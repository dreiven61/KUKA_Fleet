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
    private static final Logger LOG=Logger.getLogger(NativeHostService.class.getName());
    private final TransferService service;
    private final TransferService.FleetClient client;
    private final WcsStatusMonitor monitor;
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"servostar-native-state"));
    private final ExecutorService commands=Executors.newSingleThreadExecutor(r->daemon(r,"servostar-native-commands"));
    private final ExecutorService deliveries=Executors.newSingleThreadExecutor(r->daemon(r,"servostar-native-reports"));
    private HsmsSsCommunicator equipment;
    private NativeHostEngine engine;
    private volatile boolean communicating,closed;
    // Admission reports must not overtake their S2F50/S2F42 response attempt.
    private volatile boolean replying;
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
        int port=((Number)cfg.getOrDefault("port",DEFAULT_PORT)).intValue(), session=((Number)cfg.getOrDefault("sessionId",3000)).intValue();
        if(port<1 || port>65535 || session<0 || session>32767)throw new IllegalArgumentException("Invalid native SECS port/session");
        int t3=((Number)cfg.getOrDefault("t3Seconds",120)).intValue();if(t3<1 || t3>600)throw new IllegalArgumentException("T3 range");
        Map<String,Map<String,Object>> alarms=(Map<String,Map<String,Object>>)cfg.getOrDefault("alarmMappings",Collections.emptyMap());
        Set<Long> alarmIds=new HashSet<>();
        for(Map<String,Object> a:alarms.values()){
            long id=((Number)a.get("alid")).longValue();int category=((Number)a.get("category")).intValue();
            if(id<1 || id>0xffffffffL || !alarmIds.add(id) || category<0 || category>127 || !String.valueOf(a.get("text")).matches("[A-Za-z0-9_ ]{1,80}"))throw new IllegalArgumentException("Invalid or duplicate alarm mapping");
        }
        Map<String,Integer> failures=new LinkedHashMap<>();
        for(Map.Entry<String,Object> e:((Map<String,Object>)cfg.getOrDefault("terminalResultMappings",Collections.emptyMap())).entrySet()){
            int value=((Number)e.getValue()).intValue();if(value<1 || value>65535)throw new IllegalArgumentException("Terminal result range");failures.put(e.getKey(),value);
        }
        int fallback=((Number)cfg.getOrDefault("genericTerminalResult",9000)).intValue();if(fallback<1 || fallback>65535)throw new IllegalArgumentException("Generic terminal result range");
        Path journal=Paths.get(String.valueOf(cfg.getOrDefault("journal","/opt/nav/robot/bin/Log/interface/servostar-secs-state.json")));
        engine=new NativeHostEngine(service,client,new HostState(journal),token,alarms,failures,fallback);
        monitor.setObserver(engine::observe);
        HsmsSsCommunicatorConfig config=new HsmsSsCommunicatorConfig();config.isEquip(true);config.sessionId(session);
        config.protocol(HsmsSsProtocol.PASSIVE);config.socketAddress(new InetSocketAddress(String.valueOf(cfg.getOrDefault("bind","0.0.0.0")),port));
        config.timeout().t3(t3);config.timeout().t5(10);config.timeout().t6(5);config.timeout().t7(10);config.timeout().t8(5);
        equipment=new PassiveEquipment(config);
        equipment.addSecsCommunicatableStateChangeListener((SecsCommunicatableStateChangeBiListener)(ready,c)->{if(!ready)communicating=false;});
        equipment.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(message,comm)->{
            if(!closed)commands.execute(()->receive(message,comm));
        });
        equipment.open();
        ticker.scheduleWithFixedDelay(()->{
            if(closed)return;
            if(!equipment.isCommunicatable())communicating=false;
            try{engine.tick();}catch(Exception ex){LOG.warning("Native SECS state awaits confirmation: "+ex.getClass().getSimpleName());}
        },1,1,TimeUnit.SECONDS);
        deliveries.execute(()->deliver());
        LOG.info("Native SECS passive listener started on "+port+", session "+session+". No automatic transport submission.");
    }
    private void receive(SecsMessage m,SecsCommunicator comm){
        replying=true;
        try{
            if(m.sessionId()!=equipment.sessionId())return;
            if(m.getStream()==2 && (m.getFunction()==41 || m.getFunction()==49) && !m.wbit())return;
            int s=m.getStream(),f=m.getFunction();Secs2 reply=null;int replyFunction=f+1;
            if(s==1 && f==13){HostProtocol.listOf(m.secs2(),0,"BODY");reply=list(binary((byte)0),list(ascii("FLEET"),ascii("1.1.3")));}
            else if(s==1 && f==1)reply=list(ascii("FLEET"),ascii("1.1.3"));
            else if(s==1 && f==17)reply=binary((byte)0);
            else if(s==2 && f==49){
                if(!communicating)reply=HostProtocol.ack(2);
                else try{reply=engine.transfer(HostProtocol.transfer(m.secs2()));}
                catch(HostProtocol.ParameterError ex){reply=HostProtocol.ack(ex);}
            }else if(s==2 && f==41){
                if(!communicating)reply=HostProtocol.ack(2);
                else try{
                    HostProtocol.listOf(m.secs2(),2,"BODY");String command=HostProtocol.asciiField(m.secs2().get(0),"RCMD");
                    Map<String,Secs2> p=HostProtocol.pairs(m.secs2().get(1),"PARAMETERS");String id="";
                    if(HostProtocol.cargoCommand(command)){
                        reply=engine.cargo(command,HostProtocol.cargoParameters(command,p));
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
            if(s==1 && f==13)communicating=true;
        }catch(Exception ex){LOG.warning("Native SECS request failed; no command retry: "+ex.getClass().getSimpleName());}
        finally{replying=false;}
    }
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
    public Map<String,Object> status(String supplied){auth(supplied);return TransferService.map("enabled",engine!=null,"communicating",communicating,"state",engine==null?Collections.emptyMap():engine.state.status());}
    public Map<String,Object> retry(String supplied,long id){auth(supplied);if(engine==null)throw new IllegalStateException("Native SECS disabled");engine.state.retry(id);return TransferService.map("success",true,"dataId",id);}
    public void close(){
        closed=true;communicating=false;monitor.setObserver(null);ticker.shutdownNow();commands.shutdownNow();deliveries.shutdownNow();
        if(equipment!=null)try{equipment.close();}catch(Exception ex){LOG.warning("SECS close: "+ex.getClass().getSimpleName());}
    }
}
