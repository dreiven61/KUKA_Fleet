package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.interfaces.plugin.common.RcsMissionStatus;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Reproduces the live failure: Fleet PAUSED, no TASK_SUSPENDED callback.
 * Only in-memory Fleet fixtures and loopback HSMS; never a production request. */
public final class PauseResumeTest {
    static int checks;
    static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    static NativeHostTest.Harness fixture(Path root,Path disk)throws Exception {
        NativeHostTest.Harness h=new NativeHostTest.Harness(root,disk);
        h.activeControlJob=true;h.rcsState=9;return h;
    }
    static void assign(NativeHostTest.Harness h)throws Exception {
        h.engine.transfer(HostProtocol.transfer(NativeHostTest.transferBody("TR_TEST_001")));
        h.observe(h.trace.get(0));
    }
    static Map<String,Object> reply(Map<String,Object> row){return TransferService.map("success",true,"code","0","data",Arrays.asList(row));}
    static Map<String,Object> job(){return TransferService.map("jobCode","TR_TEST_001","workflowId",6837,"robotId","1","containerCode","SIM_CARGO_001","status",20,"warnFlag",0);}
    static Map<String,Object> robot(){return TransferService.map("robotId","1","missionCode","TR_TEST_001","rcsMissionCode","W-TEST-6837-1","rcsMissionStatus",12);}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        check(RcsMissionStatus.PAUSED.getValue()==12 && RcsMissionStatus.EXECUTING.getValue()==9
            && RcsMissionStatus.SUSPENDED.getValue()==2,"Installed SDK enum mapping");
        NativeHostTest.Harness h=fixture(root,null);assign(h);
        check(NativeHostTest.ack(h.engine.control("PAUSE",""))==4,"Pause API accepted");
        h.rcsState=11;h.engine.tick();
        check(h.count(107)==1 && h.count(209)==0 && h.count(105)==0,"PAUSING is not PAUSED");
        h.observe(h.event("TASK_SUSPENDED",true));h.rcsState=2;h.engine.tick();
        check(h.count(209)==0 && h.state.data.tsc.equals("PAUSING"),"Exception callback/state must not complete PAUSE");
        h.rcsState=12;h.engine.tick();
        check(h.count(209)==1 && h.count(105)==1 && h.state.data.tsc.equals("PAUSED"),"No suspension callback required for actual PAUSED");
        check(h.state.data.jobs.get("TR_TEST_001").control.isEmpty(),"Pending PAUSE cleared only after actual state confirmation");
        for(int i=0;i<3;i++)h.engine.tick();
        check(h.count(209)==1 && h.count(105)==1,"Polling does not duplicate pause reports");
        check(NativeHostTest.ack(h.engine.control("RESUME",""))==4,"RESUME allowed after confirmed pause");
        h.rcsState=13;h.engine.tick();h.observe(h.event("MOVE_BEGIN",true));h.engine.tick();
        check(h.count(210)==0 && h.count(103)==0,"RESUMING and stale movement callback are not execution proof");
        h.rcsState=9;h.engine.tick();
        check(h.count(210)==1 && h.count(103)==1 && h.state.data.tsc.equals("AUTO"),"Actual execution without MOVE_BEGIN produces210/103");
        check(h.controls==2 && h.count(207)==0,"Two user controls; no replay or fake transport completion");
        // No callbacks between repeated cycles: report identity must not depend on callback count.
        h.engine.control("PAUSE","");h.rcsState=12;h.engine.tick();
        h.engine.control("RESUME","");h.rcsState=9;h.engine.tick();
        check(h.count(209)==2 && h.count(210)==2 && h.count(105)==2 && h.count(103)==2,"Repeated cycles retain distinct durable report IDs");

        HostState.Job expected=h.state.data.jobs.get("TR_TEST_001");
        check(ControlStateVerifier.confirmed(true,expected,reply(job()),reply(robot())),"Exact correlated snapshot accepted");
        for(String key:Arrays.asList("jobCode","workflowId","robotId","containerCode","status","warnFlag")){
            Map<String,Object> bad=job();bad.put(key,"WRONG");
            check(!ControlStateVerifier.confirmed(true,expected,reply(bad),reply(robot())),"Reject mismatched job "+key);
            bad.remove(key);check(!ControlStateVerifier.confirmed(true,expected,reply(bad),reply(robot())),"Reject missing job "+key);
        }
        for(String key:Arrays.asList("robotId","missionCode","rcsMissionCode","rcsMissionStatus")){
            Map<String,Object> bad=robot();bad.put(key,"WRONG");
            check(!ControlStateVerifier.confirmed(true,expected,reply(job()),reply(bad)),"Reject mismatched AMR "+key);
            bad.remove(key);check(!ControlStateVerifier.confirmed(true,expected,reply(job()),reply(bad)),"Reject missing AMR "+key);
        }
        Map<String,Object> wrongInstance=robot();wrongInstance.put("rcsMissionCode","W-TEST-68370-1");
        check(!ControlStateVerifier.confirmed(true,expected,reply(job()),reply(wrongInstance)),"Exact workflow token, not substring match");
        Map<String,Object> duplicate=TransferService.map("success",true,"data",Arrays.asList(robot(),robot()));
        check(!ControlStateVerifier.confirmed(true,expected,reply(job()),duplicate),"Duplicate robot rows fail closed");
        duplicate.put("data",Arrays.asList(job(),job()));
        check(!ControlStateVerifier.confirmed(true,expected,duplicate,reply(robot())),"Duplicate job rows fail closed");
        for(Map<String,Object> bad:Arrays.asList(TransferService.map("success",false),TransferService.map("success",true,"code","ERROR","data",Arrays.asList(robot())),TransferService.map("success",true,"data","bad"),TransferService.map("success",true,"data",Collections.emptyList()))){
            check(!ControlStateVerifier.confirmed(true,expected,reply(job()),bad),"Query error/malformed/empty is not success");
        }

        Path disk=Files.createTempDirectory("pause-recovery-").resolve("state.json");
        NativeHostTest.Harness before=fixture(root,disk);assign(before);before.engine.control("PAUSE","");
        NativeHostTest.Harness after=fixture(root,disk);after.rcsState=12;after.engine.tick();
        check(after.count(209)==1 && after.state.data.tsc.equals("PAUSED") && after.controls==0,"Restore accepted PAUSE by query without replay");
        long generation=after.state.data.jobs.get("TR_TEST_001").controlGeneration;
        after.engine.control("RESUME","");after.rcsState=9;after.engine.tick();
        check(after.count(210)==1 && after.state.data.jobs.get("TR_TEST_001").controlGeneration>generation,"Persisted report generation survives restart");
        // Backward-compatible recovery of the already stuck 1.1.2 journal.
        HostState.Job old=after.state.data.jobs.get("TR_TEST_001");old.control="PAUSE";old.controlState="ACCEPTED";old.controlGeneration=0;
        after.state.data.tsc="PAUSING";after.state.save();
        NativeHostTest.Harness legacy=fixture(root,disk);legacy.rcsState=12;legacy.engine.tick();
        check(legacy.state.data.tsc.equals("PAUSED") && legacy.controls==0,"Existing1.1.2 accepted control recovers without reset");
        wire(root);
        System.out.println("PASS: "+checks+" pause/resume identity, actual-state, restart and HSMS checks; no live Fleet calls.");
    }
    @SuppressWarnings("unchecked")
    static void wire(Path root)throws Exception {
        NativeHostTest.Harness h=fixture(root,null);int port;
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        Path dir=Files.createTempDirectory("pause-wire-"),config=dir.resolve("config.json");
        new ObjectMapper().writeValue(config.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",1,"authorization",NativeHostTest.TOKEN,"journal",dir.resolve("state.json").toString()));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",config.toString());
        NativeHostService server=new NativeHostService(h.service,h.client,new WcsStatusMonitor(h.journal));
        HsmsSsCommunicator peer=null;
        ConcurrentMap<Integer,AtomicInteger> counts=new ConcurrentHashMap<>();
        try {
            server.start();peer=NativeHostTest.peer(port,counts,ConcurrentHashMap.newKeySet(),new AtomicBoolean(false),new AtomicBoolean(false));
            check(peer.send(1,13,true,list()).get().secs2().get(1).get(1).getAscii().equals("1.1.3"),"Wire reports new version");
            check(NativeHostTest.ack(peer.send(2,49,true,NativeHostTest.transferBody("TR_TEST_001")).get().secs2())==4,"Wire transfer accepted");
            java.lang.reflect.Field f=NativeHostService.class.getDeclaredField("engine");f.setAccessible(true);
            NativeHostEngine live=(NativeHostEngine)f.get(server);h.journal.observe(h.trace.get(0));live.observe(h.trace.get(0));
            check(NativeHostTest.ack(peer.send(2,41,true,list(ascii("PAUSE"),list())).get().secs2())==4,"Wire PAUSE HCACK4");
            h.rcsState=11;live.tick();check(counts.getOrDefault(209,new AtomicInteger()).get()==0,"No wire pause completion while PAUSING");
            h.rcsState=12;live.tick();NativeHostTest.waitFor(()->counts.getOrDefault(105,new AtomicInteger()).get()==1,"Wire105");
            check(counts.getOrDefault(209,new AtomicInteger()).get()==1 && counts.getOrDefault(107,new AtomicInteger()).get()==1,"Wire209 then105, start107");
            check(NativeHostTest.ack(peer.send(2,41,true,list(ascii("RESUME"),list())).get().secs2())==4,"Wire RESUME HCACK4");
            h.rcsState=13;live.tick();check(counts.getOrDefault(210,new AtomicInteger()).get()==0,"No wire resume completion while RESUMING");
            h.rcsState=9;live.tick();NativeHostTest.waitFor(()->counts.getOrDefault(103,new AtomicInteger()).get()==1,"Wire103");
            check(counts.getOrDefault(210,new AtomicInteger()).get()==1 && h.controls==2,"Wire210/103 with ACKs; no repeated control");
        } finally {
            if(peer!=null)peer.close();server.close();
            if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);
        }
    }
}
