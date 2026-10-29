package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Fixtures, loopback HTTP/HSMS only. Never contacts the configured Fleet. */
public final class NativeHostTest {
    static int checks;
    static final String TOKEN=String.join("",Collections.nCopies(64,"a"));
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    interface Condition{boolean ready()throws Exception;}
    static void waitFor(Condition c,String label)throws Exception{long end=System.currentTimeMillis()+18000;while(!c.ready() && System.currentTimeMillis()<end)Thread.sleep(50);check(c.ready(),label);}
    static int ack(Secs2 body)throws Exception{return body.get(0).getByte(0)&255;}
    static Secs2 transferBody(String id){return list(uint4(0),ascii(""),ascii("TRANSFER"),list(
        list(ascii("COMMANDINFO"),list(list(ascii("COMMANDID"),ascii(id)),list(ascii("PRIORITY"),uint2(1)),list(ascii("REPLACE"),uint2(0)))),
        list(ascii("TRANSFERINFO"),list(list(ascii("CARRIERID"),ascii("SIM_CARGO_001")),list(ascii("SOURCEPORT"),ascii("PORT_89")),list(ascii("DESTPORT"),ascii("PORT_97"))))));}
    static class Harness {
        final TransferJournal journal=new TransferJournal(null);
        final HostState state;
        final TransferService service;
        final NativeHostEngine engine;
        final TransferService.FleetClient client=this::post;
        final List<Map<String,Object>> trace;
        volatile int submits,controls;volatile boolean carried,negative,lost,controlNegative,controlLost;
        volatile Integer rcsState;volatile boolean activeControlJob;
        volatile String cargoNode="SECS_ST_89",lastMode="";
        Harness(Path root,Path disk)throws Exception {
            journal.streamReady();state=new HostState(disk);service=new TransferService(client,journal);
            engine=new NativeHostEngine(service,client,state,TOKEN,Collections.singletonMap("PP_NoPath",TransferService.map("alid",900001,"category",6,"text","FLEET_PATH_NOT_FOUND")),Collections.singletonMap("MOCK_SOURCE_INTERLOCK",5),9000);
            trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        }
        Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if(!TOKEN.equals(token))throw new SecurityException();
            if(path.equals("robotQuery"))return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("robotId","1","status",3,"occupyStatus",0,"containerCode",carried?"SIM_CARGO_001":"","nodeCode","lay01-dist1-97",
                "missionCode","TR_TEST_001","rcsMissionCode","W-TEST-6837-1","rcsMissionStatus",rcsState)));
            if(path.equals("submitMission")){submits++;if(lost)throw new java.io.IOException("response lost");return TransferService.map("success",!negative,"code",negative?"FAIL":"0");}
            if(path.equals("containerQuery") || path.equals("containerQueryAll"))return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("containerCode","SIM_CARGO_001","nodeCode",cargoNode,"isCarry",carried?1:0,"inMapStatus",1,"containerModelCode","SIM_TRAY_001")));
            if(path.equals("jobQuery"))return TransferService.map("success",true,"data",Arrays.asList(TransferService.map("jobCode","TR_TEST_001","workflowId",6837,"robotId","1","containerCode","SIM_CARGO_001","status",activeControlJob?20:30,"warnFlag",0,"completeTime","observed-time","finalNodeCode","lay01-dist1-97","targetCellCode","lay01-dist1-97")));
            if(Arrays.asList("missionCancel","pauseMission","recoverMission").contains(path)){controls++;lastMode=String.valueOf(body.get("cancelMode"));if(controlLost)throw new java.io.IOException("Control response lost");return TransferService.map("success",!controlNegative,"code",controlNegative?"FAIL":"0");}
            throw new AssertionError("Unexpected API "+path);
        }
        void observe(Map<String,Object> e){journal.observe(e);engine.observe(e);}
        Map<String,Object> event(String status,boolean success){
            Map<String,Object> e=new LinkedHashMap<>(trace.get(0));e.put("responseId",UUID.randomUUID().toString());e.put("status",status);e.put("success",success);e.put("resultCode",success?"0":"FAIL");return e;
        }
        int count(int ceid){return (int)state.data.reports.stream().filter(d->d.ceid==ceid).count();}
        void loaded() {
            for(Map<String,Object> e:trace){observe(e);if("ACTION_END".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))break;}
            carried=true;
        }
    }
    public static void main(String[] args)throws Exception {
        check(NativeHostService.DEFAULT_PORT==5556,"Existing Fleet endpoint default5556");
        Path root=Paths.get(args[0]);ObjectMapper json=new ObjectMapper();
        Map<String,Object> request=HostProtocol.transfer(transferBody("TR_TEST_001"));
        Harness normal=new Harness(root,null);
        check(ack(normal.engine.transfer(request))==4,"Actual API acceptance -> HCACK4");
        check(ack(normal.engine.transfer(request))==5 && normal.submits==1,"Duplicate -> HCACK5, one submit");
        check(normal.count(207)==0,"No early completion");
        for(Map<String,Object> e:normal.trace)normal.observe(e);normal.cargoNode="SECS_ST_97";
        for(int i=0;i<14;i++)normal.engine.tick();
        check(normal.state.data.reports.size()==14,"All fourteen normal reports");
        check("COMPLETED".equals(normal.state.data.jobs.get("TR_TEST_001").phase),"Normal completion verified");
        check(normal.state.data.reports.get(13).ceid==207 && normal.state.data.reports.get(13).location.equals("PORT_97"),"207 uses confirmed cargo port");
        Harness rejected=new Harness(root,null);rejected.negative=true;check(ack(rejected.engine.transfer(request))==2 && rejected.state.data.reports.isEmpty(),"API rejection produces HCACK2 and no fake start");
        Harness uncertain=new Harness(root,null);uncertain.lost=true;
        try{uncertain.engine.transfer(request);throw new AssertionError("Lost submit misreported as rejection");}catch(NativeHostEngine.UncertainCommand expected){checks++;}
        check(ack(uncertain.engine.transfer(request))==5 && uncertain.submits==1,"Response loss never replays command");
        Secs2 invalid=transferBody("TR_TEST_001");
        try{HostProtocol.transfer(list(uint4(0),ascii(""),ascii("TRANSFER"),list()));throw new AssertionError();}catch(HostProtocol.ParameterError e){check(ack(HostProtocol.ack(e))==3,"Invalid groups -> HCACK3/CPACK");}
        for(String cmd:Arrays.asList("CANCEL","ABORT")){
            Harness h=new Harness(root,null);h.engine.transfer(request);
            if(cmd.equals("ABORT")){check(ack(h.engine.control(cmd,"TR_TEST_001"))==2 && h.controls==0,"ABORT cannot target waiting command");h.loaded();}
            check(ack(h.engine.control(cmd,"TR_TEST_001"))==4,"Control API accepted "+cmd);
            check(h.lastMode.equals("FORCE"),"Interrupt current Fleet move without reaching target "+cmd);
            check(ack(h.engine.control(cmd,"TR_TEST_001"))==5 && h.controls==1,"No repeated control "+cmd);
            h.engine.tick();check(h.count(cmd.equals("CANCEL")?204:201)==0,"No result before callback "+cmd);
            h.observe(h.event("CANCEL_COMPLETED",true));h.engine.tick();
            check(h.count(cmd.equals("CANCEL")?204:201)==1,"Observed control terminal "+cmd);
            check(h.count(207)==0,"Cancel/abort is not successful207 "+cmd);
            if(cmd.equals("ABORT"))check(h.state.data.reports.get(h.state.data.reports.size()-1).location.equals("1"),"Carried cargo stays on actual vehicle");
        }
        Harness afterPickup=new Harness(root,null);afterPickup.engine.transfer(request);afterPickup.loaded();
        check(ack(afterPickup.engine.control("CANCEL","TR_TEST_001"))==2 && afterPickup.controls==0,
            "FORCE mode does not allow CANCEL after pickup has started");
        Harness cancelFail=new Harness(root,null);cancelFail.engine.transfer(request);cancelFail.engine.control("CANCEL","TR_TEST_001");cancelFail.observe(cancelFail.event("CANCEL_COMPLETED",false));cancelFail.engine.tick();
        check(cancelFail.count(205)==1 && cancelFail.state.data.jobs.get("TR_TEST_001").phase.equals("ACTIVE"),"Cancel failure leaves transport active");
        Harness controlRejected=new Harness(root,null);controlRejected.engine.transfer(request);controlRejected.controlNegative=true;
        check(ack(controlRejected.engine.control("CANCEL","TR_TEST_001"))==2 && controlRejected.state.data.jobs.get("TR_TEST_001").control.isEmpty(),"Explicit control rejection restores prior state");
        check(ack(controlRejected.engine.control("PAUSE",""))==2 && controlRejected.state.data.tsc.equals("AUTO"),"Rejected first pause does not strand TSC");
        Harness controlUncertain=new Harness(root,null);controlUncertain.engine.transfer(request);controlUncertain.controlLost=true;
        try{controlUncertain.engine.control("CANCEL","TR_TEST_001");throw new AssertionError("Control uncertainty misreported");}catch(NativeHostEngine.UncertainCommand expected){checks++;}
        check(ack(controlUncertain.engine.control("CANCEL","TR_TEST_001"))==5 && controlUncertain.controls==1,"Uncertain control never repeats mutation");
        Harness pause=new Harness(root,null);pause.engine.transfer(request);pause.loaded();pause.engine.control("PAUSE","");
        check(pause.count(107)==1 && pause.count(105)==0,"PAUSE acceptance only starts pausing");
        Map<String,Object> another=new LinkedHashMap<>(request);another.put("COMMANDID","ANOTHER");check(ack(pause.engine.transfer(another))==2,"Pause gates new dispatch");
        pause.observe(pause.event("TASK_SUSPENDED",true));pause.engine.tick();check(pause.count(209)==0,"Suspension callback alone does not prove PAUSED");
        pause.activeControlJob=true;pause.rcsState=12;pause.engine.tick();check(pause.count(209)==1 && pause.count(105)==1,"Confirmed RCS PAUSED ->209 and105");
        pause.engine.control("RESUME","");pause.engine.tick();check(pause.count(210)==0,"No fake resume before execution");
        pause.observe(pause.event("MOVE_BEGIN",true));pause.engine.tick();check(pause.count(210)==0,"Stale execution callback alone does not prove resume");
        pause.rcsState=9;pause.engine.tick();check(pause.count(210)==1 && pause.count(103)==1,"Confirmed RCS execution ->210 and103");
        Harness alarm=new Harness(root,null);alarm.engine.transfer(request);
        Map<String,Object> warning=alarm.event("WARNING",false);warning.put("warnCode","PP_NoPath");alarm.observe(warning);alarm.observe(warning);
        check(alarm.count(702)==1 && alarm.count(207)==0,"Duplicate warning coalesced; no fake terminal failure");
        check((alarm.engine.reportBody(alarm.state.data.reports.stream().filter(d->d.kind.equals("ALARM") && d.set).findFirst().get()).get(0).getByte(0)&255)==0x86,"Alarm set bit and category");
        Map<String,Object> clear=alarm.event("WARN_CLEAR",true);clear.put("warnCode","PP_NoPath");alarm.observe(clear);alarm.observe(clear);
        check(alarm.count(701)==1 && alarm.state.data.alarms.isEmpty(),"Clear transition once, same ALID");
        check((alarm.engine.reportBody(alarm.state.data.reports.stream().filter(d->d.kind.equals("ALARM") && !d.set).findFirst().get()).get(0).getByte(0)&255)==6,"Alarm clear bit");
        Harness failed=new Harness(root,null);failed.engine.transfer(request);Map<String,Object> e=failed.event("TASK_COMPLETED",false);e.put("resultCode","MOCK_SOURCE_INTERLOCK");failed.observe(e);failed.engine.tick();
        check(failed.count(207)==1 && failed.state.data.reports.stream().filter(d->d.ceid==207).findFirst().get().result==5,"Configured terminal failure mapping only after terminal fact");
        Harness generic=new Harness(root,null);generic.engine.transfer(request);generic.observe(generic.event("TASK_COMPLETED",false));generic.engine.tick();
        check(generic.state.data.reports.stream().filter(d->d.ceid==207).findFirst().get().result==9000,"Unmapped terminal failure uses documented test-profile code, never interlock5/6");
        Path disk=Files.createTempDirectory("native-secs-state-").resolve("state.json");Harness durable=new Harness(root,disk);durable.engine.transfer(request);durable.engine.tick();
        HostState reread=new HostState(disk);check(reread.data.reports.size()==1 && reread.pending().id==durable.state.pending().id,"Pending DATAID survives restart");
        reread.pending().state="BLOCKED";reread.save();reread.retry(reread.pending().id);check(reread.pending().state.equals("PENDING"),"Explicit report-only retry unblocks journal");
        Harness after=new Harness(root,disk);check(ack(after.engine.transfer(request))==5 && after.submits==0,"Duplicate persists across restart without submit replay");
        after.state.data.jobs.get("TR_TEST_001").control="CANCEL";after.state.data.jobs.get("TR_TEST_001").controlState="SENDING";after.state.save();
        check(new HostState(disk).data.jobs.get("TR_TEST_001").controlState.equals("UNCERTAIN"),"Restart preserves uncertain in-flight control without replay");
        wire(root,json);
        System.out.println("PASS: "+checks+" native SECS acceptance/control/alarm/restart/ACK checks; local fixtures and loopback sockets only.");
    }
    @SuppressWarnings("unchecked")
    static void wire(Path root,ObjectMapper json)throws Exception {
        Harness h=new Harness(root,null);int port;
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=s.getLocalPort();}
        Path dir=Files.createTempDirectory("native-hsms-"),cfg=dir.resolve("config.json");
        json.writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",1,"authorization",TOKEN,"journal",dir.resolve("state.json").toString(),"alarmMappings",Collections.singletonMap("PP_NoPath",TransferService.map("alid",900001,"category",6,"text","FLEET_PATH_NOT_FOUND"))));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",cfg.toString());
        NativeHostService server=new NativeHostService(h.service,h.client,new WcsStatusMonitor(h.journal));
        HsmsSsCommunicator host=null;
        ConcurrentMap<Integer,AtomicInteger> counts=new ConcurrentHashMap<>();Set<Long> initiatedIds=ConcurrentHashMap.newKeySet();AtomicBoolean reject=new AtomicBoolean(true),drop=new AtomicBoolean(true);
        try{
            server.start();host=peer(port,counts,initiatedIds,reject,drop);
            check(host.send(1,13,true,list()).get().secs2().get(0).getByte(0)==0,"Real HSMS S1F13 handshake");
            check(ack(host.send(2,49,true,transferBody("TR_TEST_001")).get().secs2())==4,"Real S2F50 L2 HCACK4");
            check(ack(host.send(2,49,true,transferBody("TR_TEST_001")).get().secs2())==5 && h.submits==1,"Wire duplicate response without second AMR command");
            waitFor(()->server.status(TOKEN).toString().contains("BLOCKED"),"Negative host ACK blocks outbox");
            Map<String,Object> state=(Map<String,Object>)server.status(TOKEN).get("state");long id=((Number)((Map<?,?>)((List<?>)state.get("reports")).get(0)).get("dataId")).longValue();
            server.retry(TOKEN,id);waitFor(()->counts.getOrDefault(208,new AtomicInteger()).get()>=2 && !server.status(TOKEN).toString().contains("BLOCKED"),"Explicit retry reuses DATAID after ACK rejection");
            check(initiatedIds.size()==1,"Business DATAID stable across retransmission");
            h.journal.observe(h.trace.get(0));waitFor(()->counts.getOrDefault(604,new AtomicInteger()).get()>=1,"Assignment report on wire");
            host.close();host=null;Thread.sleep(1200);host=peer(port,counts,initiatedIds,reject,drop);host.send(1,13,true,list());
            waitFor(()->counts.getOrDefault(604,new AtomicInteger()).get()>=2,"Pending report retransmitted after real disconnect/reconnect");
            check(h.submits==1,"Reconnect never resubmits transfer");
            check(ack(host.send(2,41,true,list(ascii("UNKNOWN"),list())).get().secs2())==1,"Wire unknown command HCACK1");
            check(ack(host.send(2,41,true,list(ascii("CANCEL"),list())).get().secs2())==3,"Wire missing COMMANDID HCACK3");
            check(ack(host.send(2,41,true,list(ascii("CANCEL"),list(list(ascii("COMMANDID"),ascii("NOT_FOUND"))))).get().secs2())==6,"Wire unknown object HCACK6");
            Secs2 cancel=list(ascii("CANCEL"),list(list(ascii("COMMANDID"),ascii("TR_TEST_001"))));
            check(ack(host.send(2,41,true,cancel).get().secs2())==4 && h.controls==1,"Wire CANCEL actual API acceptance HCACK4");
            check(ack(host.send(2,41,true,cancel).get().secs2())==5 && h.controls==1,"Wire CANCEL duplicate never repeats mutation");
            waitFor(()->counts.getOrDefault(206,new AtomicInteger()).get()==1,"Wire cancel initiated report");
            java.lang.reflect.Field field=NativeHostService.class.getDeclaredField("engine");field.setAccessible(true);
            NativeHostEngine live=(NativeHostEngine)field.get(server);
            Map<String,Object> warning=h.event("WARNING",false);warning.put("warnCode","PP_NoPath");
            live.observe(warning);waitFor(()->counts.getOrDefault(702,new AtomicInteger()).get()==1,"Wire S5F1/S5F2 set followed by unit702");
            warning=h.event("WARN_CLEAR",true);warning.put("warnCode","PP_NoPath");live.observe(warning);
            waitFor(()->counts.getOrDefault(701,new AtomicInteger()).get()==1,"Wire S5F1/S5F2 clear followed by unit701");
            check(counts.getOrDefault(5001,new AtomicInteger()).get()==2,"Two real S5F1 reports with U4 ALID900001");
        }finally{if(host!=null)host.close();server.close();if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);}
    }
    static HsmsSsCommunicator peer(int port,ConcurrentMap<Integer,AtomicInteger> counts,Set<Long> ids,AtomicBoolean reject,AtomicBoolean drop)throws Exception {
        HsmsSsCommunicatorConfig cfg=new HsmsSsCommunicatorConfig();cfg.isEquip(false);cfg.sessionId(3000);cfg.protocol(HsmsSsProtocol.ACTIVE);cfg.socketAddress(new InetSocketAddress("127.0.0.1",port));cfg.timeout().t3(2);cfg.timeout().t5(1);
        HsmsSsCommunicator host=HsmsSsCommunicator.newInstance(cfg);
        host.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(m,c)->{
            try{
                if(m.getStream()==5 && m.getFunction()==1){
                    if(m.secs2().get(1).secs2Item()!=Secs2Item.UINT4 || m.secs2().get(1).getLong(0)!=900001)throw new AssertionError("Wire ALID");
                    counts.computeIfAbsent(5001,k->new AtomicInteger()).incrementAndGet();c.send(m,5,2,false,binary((byte)0));return;
                }
                if(m.getStream()==6 && m.getFunction()==11){
                    int ceid=m.secs2().get(1).getInt(0);counts.computeIfAbsent(ceid,k->new AtomicInteger()).incrementAndGet();if(ceid==208)ids.add(m.secs2().get(0).getLong(0));
                    if(ceid==604 && drop.compareAndSet(true,false))return;
                    c.send(m,6,12,false,binary((byte)(ceid==208 && reject.compareAndSet(true,false)?1:0)));
                }
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
        // Installed library's timed property wait never starts its FutureTask.
        // Production uses open() + state callbacks; mirror that here.
        host.open();waitFor(host::isCommunicatable,"Peer HSMS selected");return host;
    }
}
