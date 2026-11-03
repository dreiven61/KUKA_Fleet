package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Real HSMS on loopback, fake Fleet only: a lift PAUSE is rejected, ABORT
 * produces a negatively-ACKed/retried failure alarm before201/610 and cleanup. */
public final class HandlingWireTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);HandlingControlTest.Fixture h=new HandlingControlTest.Fixture(root,null);
        int port;try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        Path dir=Files.createTempDirectory("handling-hsms-"),cfg=dir.resolve("config.json");
        new ObjectMapper().writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,
            "sessionId",3000,"t3Seconds",1,"authorization",NativeHostTest.TOKEN,"journal",dir.resolve("state.json").toString()));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",cfg.toString());
        NativeHostService server=new NativeHostService(h.service,h.client,new WcsStatusMonitor(h.journal));
        HsmsSsCommunicator host=null;
        List<Integer> received=new CopyOnWriteArrayList<>();
        try{
            server.start();HsmsSsCommunicatorConfig pc=new HsmsSsCommunicatorConfig();pc.isEquip(false);pc.sessionId(3000);
            pc.protocol(HsmsSsProtocol.ACTIVE);pc.socketAddress(new InetSocketAddress("127.0.0.1",port));pc.timeout().t3(3);pc.timeout().t5(1);
            host=HsmsSsCommunicator.newInstance(pc);
            host.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(m,c)->{
                try{
                    if(m.getStream()==5 && m.getFunction()==1){
                        if(m.secs2().get(1).getLong(0)!=900002 || m.secs2().get(0).getByte(0)!=(byte)0x86
                            || !m.secs2().get(2).getAscii().contains("TR_TEST_001"))throw new AssertionError("Unexpected handling alarm");
                        received.add(900002);c.send(m,5,2,false,binary((byte)(Collections.frequency(received,900002)==1?1:0)));
                    }else if(m.getStream()==6 && m.getFunction()==11){
                        received.add(m.secs2().get(1).getInt(0));c.send(m,6,12,false,binary((byte)0));
                    }
                }catch(Exception ex){throw new RuntimeException(ex);}
            });
            host.open();NativeHostTest.waitFor(host::isCommunicatable,"Loopback HSMS connected");
            host.send(1,13,true,list());
            check(NativeHostTest.ack(host.send(2,49,true,NativeHostTest.transferBody("TR_TEST_001")).get().secs2())==4,"Wire transport accepted");
            java.lang.reflect.Field f=NativeHostService.class.getDeclaredField("engine");f.setAccessible(true);
            NativeHostEngine live=(NativeHostEngine)f.get(server);
            for(Map<String,Object> e:h.trace){h.journal.observe(e);live.observe(e);if("ACTION_BEGIN".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))break;}
            check(NativeHostTest.ack(host.send(2,41,true,list(ascii("PAUSE"),list())).get().secs2())==2 && h.controls==0,"Wire PAUSE blocked before Fleet POST");
            check(NativeHostTest.ack(host.send(2,41,true,list(ascii("ABORT"),list(list(ascii("COMMANDID"),ascii("TR_TEST_001"))))).get().secs2())==4,"Wire handling ABORT accepted");
            Map<String,Object> failed=h.event("TASK_COMPLETED",false);h.journal.observe(failed);live.observe(failed);
            check(!received.contains(201) && !received.contains(207),"No terminal report from failed task callback alone");
            Map<String,Object> failedLift=h.event("ACTION_END",false);failedLift.put("actionType","UP_CONTAINER");h.journal.observe(failedLift);live.observe(failedLift);
            h.carried=true;Map<String,Object> canceled=h.event("CANCEL_COMPLETED",true);h.journal.observe(canceled);live.observe(canceled);
            NativeHostTest.waitFor(()->received.contains(900002),"S5F1 failure reaches host");
            check(!received.contains(201) && !received.contains(610),"Rejected alarm ACK blocks later terminal messages");
            HostState.Delivery alarm;
            synchronized(live.state){alarm=live.state.data.reports.stream().filter(d->d.kind.equals("ALARM")).findFirst().get();}
            NativeHostTest.waitFor(()->"BLOCKED".equals(alarm.state),"Alarm negative ACK preserved");
            server.retry(NativeHostTest.TOKEN,alarm.id);
            NativeHostTest.waitFor(()->received.contains(201),"Same alarm retry then abort completion");
            check(Collections.frequency(received,900002)==2 && received.indexOf(900002)<received.indexOf(201),"Failure precedes201; retry does not repeat FORCE");
            check(!received.contains(610),"Vehicle still bound: no fabricated610");
            h.release();
            NativeHostTest.waitFor(()->{synchronized(live.state){return live.state.data.jobs.get("TR_TEST_001").phase.equals("ABORTED");}},"Released and ACKed job cleared");
            check(received.indexOf(201)<received.indexOf(610) && h.controls==1 && !received.contains(207),"Wire201 before610, one FORCE, no fake207");
        }finally{
            if(host!=null)host.close();server.close();
            if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);
        }
        System.out.println("PASS: "+checks+" HSMS lift rejection, abort alarm ACK/retry, report ordering and release checks; loopback only.");
    }
}
