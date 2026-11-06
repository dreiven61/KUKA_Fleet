package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.secs2.*;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Opt-in HSMS equipment endpoint INSIDE Fleet's custom plugin, using the
 * SECS library supplied with this Fleet installation. Legacy ECS is untouched. */
public final class NativeHostService implements AutoCloseable {
    static final int DEFAULT_PORT=5556;
    static final String VERSION="1.5.8";
    private static final Logger LOG=Logger.getLogger(NativeHostService.class.getName());
    private final TransferService service;
    private final TransferService.FleetClient client;
    private final WcsStatusMonitor monitor;
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"servostar-native-state"));
    private final ScheduledExecutorService alarmTicker=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"servostar-workflow-alarms"));
    private final ScheduledExecutorService vehicleTicker=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"servostar-vehicle-installations"));
    private final ExecutorService commands=Executors.newSingleThreadExecutor(r->daemon(r,"servostar-native-commands"));
    private final ExecutorService statusRequests=Executors.newSingleThreadExecutor(r->daemon(r,"servostar-selected-status"));
    private final ExecutorService deliveries=Executors.newSingleThreadExecutor(r->daemon(r,"servostar-native-reports"));
    private final ScheduledExecutorService catalogTicker=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"servostar-facility-catalog"));
    private HsmsSsCommunicator equipment;
    private NativeHostEngine engine;
    private SelectedStatusService selectedStatus;
    private PortBuzzer buzzer;
    private volatile boolean communicating,closed;
    // Admission reports must not overtake their S2F50/S2F42 response attempt.
    private volatile boolean replying;
    private long nextOrdinaryPoll=System.nanoTime();
    private String vehiclePollError="";
    private String token="";
    private int maxAttempts=5;
    private static Thread daemon(Runnable r,String n){Thread t=new Thread(r,n);t.setDaemon(true);return t;}
    /** Installed 2.11 passive communicator does not propagate SELECTED to its
     * public state property. Read its actual session/channel identity instead;
     * no vendor classes or wire behavior are patched. */
    private static final class PassiveEquipment extends AbstractHsmsSsPassiveCommunicator {
        private volatile AbstractHsmsAsyncSocketChannel channel;
        PassiveEquipment(HsmsSsCommunicatorConfig cfg){super(cfg);}
        @Override protected AbstractHsmsAsyncSocketChannel buildAsyncSocketChannel(java.nio.channels.AsynchronousSocketChannel socket){
            AbstractHsmsAsyncSocketChannel next=super.buildAsyncSocketChannel(socket);channel=next;return next;
        }
        @Override public boolean isCommunicatable(){AbstractHsmsAsyncSocketChannel c=channel;return c!=null && getSession().equalsAsyncSocketChannel(c);}
    }
    public NativeHostService(TransferService service,TransferService.FleetClient client,WcsStatusMonitor monitor){this.service=service;this.client=client;this.monitor=monitor;}
    @SuppressWarnings("unchecked")
    public void start()throws Exception {
        if(Boolean.getBoolean("servostar.transfer.offlineTest"))return;
        Path file=Paths.get(System.getProperty("servostar.secs.config","/opt/nav/config/servostar-secs.json"));
        if(!Files.exists(file)){LOG.info("Native SECS disabled: no configuration file");return;}
        Map<String,Object> cfg=new ObjectMapper().readValue(file.toFile(),Map.class);
        if(!Boolean.TRUE.equals(cfg.get("enabled"))){LOG.info("Native SECS disabled by configuration");return;}
        token=String.valueOf(cfg.get("authorization"));if(!token.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Native SECS requires Fleet authorization in its local configuration");
        if(client instanceof FleetHttpClient && cfg.containsKey("metadataGrpcTarget"))((FleetHttpClient)client).metadataTarget(String.valueOf(cfg.get("metadataGrpcTarget")));
        try{WorkPorts.refresh(client,token);}catch(Exception unavailable){LOG.warning("Fleet catalog not ready; new transport blocked until scan succeeds: "+unavailable.getClass().getSimpleName());}
        int port=((Number)cfg.getOrDefault("port",DEFAULT_PORT)).intValue(), session=((Number)cfg.getOrDefault("sessionId",3000)).intValue();
        if(port<1 || port>65535 || session<0 || session>32767)throw new IllegalArgumentException("Invalid native SECS port/session");
        int t3=((Number)cfg.getOrDefault("t3Seconds",120)).intValue();if(t3<1 || t3>600)throw new IllegalArgumentException("T3 range");
        Map<String,Map<String,Object>> alarms=(Map<String,Map<String,Object>>)cfg.getOrDefault("alarmMappings",Collections.emptyMap());
        Set<Long> alarmIds=new HashSet<>();
        for(Map.Entry<String,Map<String,Object>> entry:alarms.entrySet()){
            Map<String,Object> a=entry.getValue();
            long id=((Number)a.get("alid")).longValue();int category=((Number)a.get("category")).intValue();
            if(id<1 || id>0xffffffffL || id==900002 || !alarmIds.add(id) || category<0 || category>127 || !String.valueOf(a.get("text")).matches("[A-Za-z0-9_ ]{1,80}"))throw new IllegalArgumentException("Invalid or duplicate alarm mapping (900002 reserved for test handling interruption)");
            if(id>=1000001 && id<=1999999 && !entry.getKey().equals(String.valueOf(id-1000000)))throw new IllegalArgumentException("ALID1000001..1999999 reserved for matching numeric workflow warning test profile");
        }
        Map<String,Integer> failures=new LinkedHashMap<>();
        for(Map.Entry<String,Object> e:((Map<String,Object>)cfg.getOrDefault("terminalResultMappings",Collections.emptyMap())).entrySet()){
            int value=((Number)e.getValue()).intValue();if(value<1 || value>65535)throw new IllegalArgumentException("Terminal result range");failures.put(e.getKey(),value);
        }
        int fallback=((Number)cfg.getOrDefault("genericTerminalResult",9000)).intValue();if(fallback<1 || fallback>65535)throw new IllegalArgumentException("Generic terminal result range");
        Path journal=Paths.get(String.valueOf(cfg.getOrDefault("journal","/opt/nav/robot/bin/Log/interface/servostar-secs-state.json")));
        engine=new NativeHostEngine(service,client,new HostState(journal),token,alarms,failures,fallback);
        buzzer=new PortBuzzer(cfg.getOrDefault("portBuzzers",Collections.emptyMap()));
        WorkPorts.publicationGuard((next,commit)->{synchronized(engine.state){
            if(!engine.vehicleNames.preservesPortNamespace(next))throw new IllegalStateException("Fleet port aliases conflict with accepted MCS VehicleID names");
            boolean unsettled=engine.state.data.jobs.values().stream().anyMatch(j->engine.state.unsettled(j));
            if(unsettled && !WorkPorts.preservesBindings(next))throw new IllegalStateException("Fleet port bindings changed while accepted jobs are unsettled");
            commit.run();
        }});
        engine.vehicleNames.configure(Paths.get(String.valueOf(cfg.getOrDefault("robotNamesFile","/workspace/plugins/servostar-robot-names.xml"))),!cfg.containsKey("robotNamesFile"));
        engine.vehicleNames.refresh(false);
        selectedStatus=new SelectedStatusService(client,engine.state,token,engine.vehicleNames);
        Map<String,String> waitNodes=new LinkedHashMap<>();
        Object configured=cfg.getOrDefault("loadedAbortWaitNodes",Collections.emptyMap());
        if(!(configured instanceof Map))throw new IllegalArgumentException("loadedAbortWaitNodes must be an object");
        for(Map.Entry<?,?> e:((Map<?,?>)configured).entrySet()){
            if(!(e.getKey() instanceof String) || !(e.getValue() instanceof String))throw new IllegalArgumentException("Wait node IDs must be strings");
            waitNodes.put((String)e.getKey(),(String)e.getValue());
        }
        engine.configureRecovery(waitNodes);
        monitor.setObserver(engine::observe);
        HsmsSsCommunicatorConfig config=new HsmsSsCommunicatorConfig();config.isEquip(true);config.sessionId(session);
        config.protocol(HsmsSsProtocol.PASSIVE);config.socketAddress(new InetSocketAddress(String.valueOf(cfg.getOrDefault("bind","0.0.0.0")),port));
        config.timeout().t3(t3);config.timeout().t5(10);config.timeout().t6(5);config.timeout().t7(10);config.timeout().t8(5);
        equipment=new PassiveEquipment(config);
        equipment.addSecsCommunicatableStateChangeListener((SecsCommunicatableStateChangeBiListener)(ready,c)->{if(!ready)communicating=false;});
        equipment.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(message,comm)->{
            if(!closed){
                if(message.getStream()==1 && message.getFunction()==3)statusRequests.execute(()->receiveStatus(message,comm));
                else commands.execute(()->receive(message,comm));
            }
        });
        equipment.open();
        ticker.scheduleWithFixedDelay(()->{
            if(closed)return;
            if(!equipment.isCommunicatable())communicating=false;
            long now=System.nanoTime();
            if(!engine.hasPendingSystemControl() && now-nextOrdinaryPoll<0)return;
            nextOrdinaryPoll=now+TimeUnit.SECONDS.toNanos(1);
            try{engine.tick();}catch(Exception ex){LOG.warning("Native SECS state awaits confirmation: "+ex.getClass().getSimpleName());}
        },250,250,TimeUnit.MILLISECONDS);
        alarmTicker.scheduleWithFixedDelay(()->{
            if(closed)return;
            try{engine.pollWorkflowAlarms();}catch(Exception ex){LOG.warning("Workflow alarms await confirmation: "+ex.getClass().getSimpleName());}
        },1,1,TimeUnit.SECONDS);
        VehicleInstallationMonitor vehicleInstallations=new VehicleInstallationMonitor(client,token,engine.state,engine.vehicleNames);
        vehicleTicker.scheduleWithFixedDelay(()->{
            if(closed)return;
            try{
                vehicleInstallations.poll();
                vehiclePollError="";
            }catch(Exception unavailable){
                String reason=unavailable.getClass().getSimpleName();
                if(!reason.equals(vehiclePollError))LOG.warning("Vehicle installation snapshot unavailable: "+reason);
                vehiclePollError=reason;
            }
        },0,2,TimeUnit.SECONDS);
        deliveries.execute(()->deliver());
        catalogTicker.scheduleWithFixedDelay(()->{
            if(closed)return;
            try{WorkPorts.refresh(client,token);}catch(Exception unavailable){LOG.warning("Fleet catalog refresh unavailable; no static fallback: "+unavailable.getClass().getSimpleName());}
            refreshVehicleNames();
        },5,5,TimeUnit.SECONDS);
        LOG.info("Native SECS passive listener started on "+port+", session "+session+". Loaded ABORT wait policy configured for vehicles "+waitNodes.keySet()+".");
    }
    private void receiveStatus(SecsMessage m,SecsCommunicator comm){
        if(m.sessionId()!=equipment.sessionId() || !m.wbit())return;
        try{
            if(!communicating)throw new IllegalStateException("Establish communication first");
            Secs2 values=selectedStatus.read(m.secs2());comm.send(m,1,4,false,values);
        }catch(Exception ex){
            // S1F0 signals unavailable status, never a successful empty inventory
            // or transfer list that would permit duplicate upstream dispatch.
            String ids="invalid";
            try{ids=SelectedStatusService.requested(m.secs2()).toString();}catch(Exception ignored){}
            String reason=String.valueOf(ex.getMessage()).replaceAll("[\\r\\n]"," ").replaceAll("[0-9a-fA-F]{64}","[redacted]");
            if(reason.length()>240)reason=reason.substring(0,240);
            LOG.warning("S1F3 snapshot unavailable; SVID="+ids+"; "+ex.getClass().getSimpleName()+"; reason="+reason+"; no empty success response");
            try{comm.send(m,1,0,false,Secs2.empty());}catch(Exception disconnected){LOG.warning("S1F3 peer disconnected before abort reply");}
        }
    }
    private void receive(SecsMessage m,SecsCommunicator comm){
        replying=true;
        try{
            if(m.sessionId()!=equipment.sessionId())return;
            if(m.getStream()==2 && (m.getFunction()==41 || m.getFunction()==49) && !m.wbit())return;
            int s=m.getStream(),f=m.getFunction();Secs2 reply=null;int replyFunction=f+1;
            if(s==1 && f==13){HostProtocol.listOf(m.secs2(),0,"BODY");refreshVehicleNames();reply=list(binary((byte)0),list(ascii("FLEET"),ascii(VERSION)));}
            else if(s==1 && f==1)reply=list(ascii("FLEET"),ascii(VERSION));
            else if(s==1 && (f==15 || f==17)){
                if(!m.wbit())return;
                reply=binary((byte)changeHostMode(f==15,m.secs2()));
            }else if(s==5 && f==5){
                if(!m.wbit())return;
                try{
                    if(!communicating)throw new IllegalStateException("Establish communication first");
                    reply=engine.alarmList(m.secs2());
                }catch(Exception unavailable){
                    replyFunction=0;
                    reply=Secs2.empty();
                }
            }else if(s==2 && f==49){
                if(!hostCanCommand())reply=HostProtocol.ack(2);
                else if(!refreshAvailable())reply=HostProtocol.ack(2);
                else try{reply=engine.transfer(HostProtocol.transfer(m.secs2(),engine.vehicleNames));}
                catch(HostProtocol.ParameterError ex){reply=HostProtocol.ack(ex);}
            }else if(s==2 && f==41){
                if(!hostCanCommand())reply=HostProtocol.ack(2);
                else try{
                    HostProtocol.listOf(m.secs2(),2,"BODY");String command=HostProtocol.asciiField(m.secs2().get(0),"RCMD");
                    Map<String,Secs2> p=HostProtocol.pairs(m.secs2().get(1),"PARAMETERS");String id="";
                    if(HostProtocol.cargoCommand(command)){
                        reply=engine.cargo(command,HostProtocol.cargoParameters(command,p));
                    }else if("UPDATE".equals(command)){
                        reply=update(p);
                    }else if("BUZZER".equals(command)){
                        reply=buzzer(p);
                    }else {
                    if("CANCEL".equals(command) || "ABORT".equals(command)){
                        if(p.size()!=1 || !p.containsKey("COMMANDID"))throw new HostProtocol.ParameterError("COMMANDID",1);
                        id=HostProtocol.asciiField(p.get("COMMANDID"),"COMMANDID");
                        if(!id.matches("[A-Za-z0-9_-]{1,32}"))throw new HostProtocol.ParameterError("COMMANDID",2);
                    }else if(!p.isEmpty())throw new HostProtocol.ParameterError("PARAMETERS",1);
                    reply=engine.control(command,id);
                    }
                }catch(HostProtocol.ParameterError ex){reply=HostProtocol.ack(ex);}
            }else {replyFunction=0;reply=Secs2.empty();}
            if(m.wbit())comm.send(m,s,replyFunction,false,reply);
            if(s==1 && f==13)synchronized(engine.state){communicating=true;}
        }catch(Exception ex){LOG.warning("Native SECS request failed; no command retry: "+ex.getClass().getSimpleName());}
        finally{replying=false;}
    }

    private boolean hostCanCommand(){
        synchronized(engine.state){return communicating && !engine.state.hostOffline;}
    }

    private int changeHostMode(boolean offline,Secs2 body){
        if(!communicating || !body.equals(Secs2.empty()))return offline?2:1;
        synchronized(engine.state){
            if(engine.state.hostOffline==offline)return 0;
            if(engine.state.data.reports.size()>=20000)return offline?2:1;
            HostState.Delivery report=new HostState.Delivery();
            report.kind="TSC";
            report.ceid=offline?1:3;
            report.key="host-mode:"+engine.state.data.sequence;
            engine.state.add(report);
            engine.state.save();
            engine.state.hostOffline=offline;
            return 0;
        }
    }

    private Secs2 update(Map<String,Secs2> fields)throws Exception{
        String id=HostProtocol.requiredAscii(fields,"COMMANDID");
        if(!id.matches("[A-Za-z0-9_-]{1,32}"))throw new HostProtocol.ParameterError("COMMANDID",2);
        int priority=HostProtocol.updatePriority(fields);
        String destination=fields.containsKey("DESTPORT")?HostProtocol.requiredAscii(fields,"DESTPORT"):"";
        if(!destination.isEmpty() && !WorkPorts.identifier(destination,64))throw new HostProtocol.ParameterError("DESTPORT",2);
        int expected=fields.containsKey("DESTPORT")?3:2;
        if(fields.size()!=expected)throw new HostProtocol.ParameterError("PARAMETERS",1);
        return destination.isEmpty()?engine.updatePriority(id,priority,monitor::updatePriority)
            :engine.updateDestination(id,priority,destination,monitor::redirectDestination,monitor::updatePriority);
    }

    private Secs2 buzzer(Map<String,Secs2> fields)throws Exception{
        String requested=HostProtocol.requiredAscii(fields,"PORTID");
        if(fields.size()!=1)throw new HostProtocol.ParameterError("PARAMETERS",1);
        if(!WorkPorts.identifier(requested,64))throw new HostProtocol.ParameterError("PORTID",2);
        try{WorkPorts.requireFresh();}catch(IllegalStateException unavailable){return HostProtocol.ack(2);}
        synchronized(engine.state){
            engine.state.requireHealthy();
            if(!WorkPorts.contains(requested))return HostProtocol.ack(6);
            String port=WorkPorts.canonical(requested);
            try{return HostProtocol.ack(buzzer.pulse(port,WorkPorts.node(port))?0:2);}
            catch(java.io.IOException uncertain){throw new NativeHostEngine.UncertainCommand();}
        }
    }
    private boolean refreshAvailable(){
        try{WorkPorts.refresh(client,token);refreshVehicleNames();engine.vehicleNames.requireReady();return true;}
        catch(Exception unavailable){LOG.warning("Transfer admission blocked by facility scan: "+unavailable.getClass().getSimpleName());return false;}
    }
    private void refreshVehicleNames(){engine.vehicleNames.refresh(()->communicating && equipment!=null && equipment.isCommunicatable());}
    private void deliver(){
        while(!closed){
            try{
                if(replying || !communicating || equipment==null || !equipment.isCommunicatable()){Thread.sleep(500);continue;}
                HostState.Delivery d=engine.state.pending();
                if(d==null || "BLOCKED".equals(d.state)){Thread.sleep(500);continue;}
                Secs2 body=engine.reportBody(d);
                if(replying)continue;
                synchronized(engine.state){d.attempts++;engine.state.save();}
                try{
                    int stream="ALARM".equals(d.kind)?5:6;
                    Optional<SecsMessage> r=equipment.send(stream,stream==5?1:11,true,body);
                    if(!r.isPresent())throw new IllegalStateException("Missing report ACK");
                    SecsMessage ack=r.get();
                    boolean accepted=ack.getStream()==stream && ack.getFunction()==(stream==5?2:12)
                        && ack.secs2().secs2Item()==Secs2Item.BINARY && ack.secs2().size()==1 && ack.secs2().getByte(0)==0;
                    synchronized(engine.state){d.state=accepted?"ACKED":"BLOCKED";d.error=accepted?"":"Host rejected ACK or returned an invalid ACK body";engine.state.save();}
                    if(!accepted)LOG.warning("Report DATAID "+d.id+" blocked by host ACK; explicit report retry required");
                }catch(Exception ex){
                    synchronized(engine.state){
                        d.error="ACK unavailable: "+ex.getClass().getSimpleName();if(d.attempts>=maxAttempts)d.state="BLOCKED";engine.state.save();
                    }
                    Thread.sleep(5000); // retry reports only; never repeat transport/control commands
                }
            }catch(InterruptedException stop){Thread.currentThread().interrupt();return;}
            catch(Exception error){LOG.warning("SECS report journal blocked: "+error.getClass().getSimpleName());try{Thread.sleep(1000);}catch(InterruptedException stop){return;}}
        }
    }
    private void auth(String supplied){
        if(token.isEmpty() || supplied==null || !java.security.MessageDigest.isEqual(token.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new SecurityException("Native SECS authorization rejected");
    }
    public Map<String,Object> status(String supplied){auth(supplied);return TransferService.map("enabled",engine!=null,"version",VERSION,"communicating",communicating,"catalog",WorkPorts.status(),"robotNames",engine==null?Collections.emptyMap():engine.vehicleNames.status(),"loadedAbortWaitNodes",engine==null?Collections.emptyMap():engine.recoveryStatus(),"state",engine==null?Collections.emptyMap():engine.state.status());}
    public Map<String,Object> refreshCatalog(String supplied)throws Exception{auth(supplied);WorkPorts.refresh(client,token);if(engine!=null){refreshVehicleNames();engine.vehicleNames.requireReady();}return WorkPorts.status();}
    public Map<String,Object> recoverLoadedAbort(String supplied,String id,String carrier,String vehicle,String source,String wait,boolean apply)throws Exception {
        auth(supplied);
        if(engine==null)throw new IllegalStateException("Native SECS disabled");
        if(apply && !communicating)throw new CargoService.Rejected("SECS_NOT_COMMUNICATING");
        return engine.recoverLoadedAbort(id,carrier,vehicle,source,wait,apply);
    }
    public Map<String,Object> retry(String supplied,long id){auth(supplied);if(engine==null)throw new IllegalStateException("Native SECS disabled");engine.state.retry(id);return TransferService.map("success",true,"dataId",id);}
    public void close(){
        closed=true;communicating=false;monitor.setObserver(null);ticker.shutdownNow();alarmTicker.shutdownNow();vehicleTicker.shutdownNow();catalogTicker.shutdownNow();commands.shutdownNow();statusRequests.shutdownNow();deliveries.shutdownNow();WorkPorts.publicationGuard((next,commit)->commit.run());
        if(equipment!=null)try{equipment.close();}catch(Exception ex){LOG.warning("SECS close: "+ex.getClass().getSimpleName());}
    }
}
