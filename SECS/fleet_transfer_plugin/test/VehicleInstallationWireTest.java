package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Poll Fleet-shaped read-only data through the normal service entry point and ACK real HSMS frames. */
public final class VehicleInstallationWireTest {
    private static final String TOKEN=NativeHostTest.TOKEN;
    private static int checks;

    private static void check(boolean ok,String description){
        if(!ok)throw new AssertionError(description);
        checks++;
    }

    private static Map<String,Object> snapshot(int first,int second,int third){
        return TransferService.map("success",true,"code",0,"data",Arrays.asList(
            TransferService.map("robotId","1","status",first),TransferService.map("robotId","2","status",second),
            TransferService.map("robotId","3","status",third)));
    }

    private static Map<?,?> state(NativeHostService server){return (Map<?,?>)server.status(TOKEN).get("state");}
    private static List<?> reports(NativeHostService server){return (List<?>)state(server).get("reports");}
    private static Map<?,?> installed(NativeHostService server){return (Map<?,?>)state(server).get("vehicleInstallations");}

    public static void main(String[] args)throws Exception {
        AtomicReference<Map<String,Object>> source=new AtomicReference<>(snapshot(3,3,1));
        TransferService.FleetClient fleet=(path,token,body)->{
            if(!"robotQuery".equals(path) || !TOKEN.equals(token))throw new AssertionError("Unexpected Fleet request");
            return source.get();
        };
        Path temp=Files.createTempDirectory("vehicle-install-wire-");
        Path cfg=temp.resolve("config.json"),journal=temp.resolve("state.json"),names=temp.resolve("names.xml");
        Files.write(names,("<RobotNames version=\"1\"><Robot fleetId=\"1\" vehicleId=\"AGV001\"/>"
            +"<Robot fleetId=\"2\" vehicleId=\"AGV002\"/><Robot fleetId=\"3\" vehicleId=\"AGV003\"/></RobotNames>")
            .getBytes(StandardCharsets.UTF_8));
        String previous=System.getProperty("servostar.secs.config");
        System.setProperty("servostar.secs.config",cfg.toString());
        int port=freePort();
        writeConfig(cfg,journal,names,port);
        NativeHostService first=server(fleet);
        List<Long> pendingIds=new ArrayList<>();
        try{
            first.start();
            NativeHostTest.waitFor(()->installed(first)!=null,"Record the first Fleet snapshot");
            check(Boolean.FALSE.equals(installed(first).get("3")) && reports(first).isEmpty(),"Existing Departure becomes baseline without synthetic events");
            source.set(snapshot(1,1,1));
            NativeHostTest.waitFor(()->reports(first).size()==2,"Two removals queued without MCS");
            for(Object entry:reports(first)){
                Map<?,?> report=(Map<?,?>)entry;
                check(Integer.valueOf(609).equals(report.get("ceid")),"Confirmed Departure queues VehicleRemoved");
                pendingIds.add(((Number)report.get("dataId")).longValue());
            }
        }finally{first.close();}

        port=freePort();
        writeConfig(cfg,journal,names,port);
        NativeHostService second=server(fleet);
        HsmsSsCommunicator host=null;
        BlockingQueue<Secs2> received=new LinkedBlockingQueue<>();
        try{
            second.start();
            check(reports(second).size()==2,"Persisted outbox survives service restart");
            host=connect(port,received);
            for(int i=0;i<2;i++){
                Secs2 body=expect(received,609,"AGV00"+(i+1));
                check(body.get(0).getLong(0)==pendingIds.get(i),"Restart sends same DATAID");
            }
            NativeHostTest.waitFor(()->reports(second).isEmpty(),"S6F12 clears the outbox");
            source.set(snapshot(3,2,7));
            for(int i=1;i<=3;i++)expect(received,608,"AGV00"+i);
            source.set(snapshot(7,3,2));
            check(received.poll(3,TimeUnit.SECONDS)==null,"Health changes between installed states do not report Install/Remove");
            source.set(TransferService.map("success",false));
            check(received.poll(3,TimeUnit.SECONDS)==null,"Unavailable read does not report Remove");
            source.set(TransferService.map("success",true,"data",Collections.emptyList()));
            check(received.poll(3,TimeUnit.SECONDS)==null && Boolean.TRUE.equals(installed(second).get("1")),"Missing rows preserve the prior installation state");
            source.set(snapshot(1,3,3));
            expect(received,609,"AGV001");
            NativeHostTest.waitFor(()->reports(second).isEmpty(),"Live remove ACK accepted");
        }finally{
            if(host!=null)host.close();
            second.close();
            if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);
        }
        System.out.println("PASS: "+checks+" Fleet vehicle map installation/Departure, durable outbox, wire identity and ACK checks.");
    }

    private static NativeHostService server(TransferService.FleetClient fleet)throws Exception {
        TransferJournal history=new TransferJournal(null);
        return new NativeHostService(new TransferService(fleet,history),fleet,new WcsStatusMonitor(history));
    }

    private static int freePort()throws Exception {
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}
    }

    private static void writeConfig(Path cfg,Path journal,Path names,int port)throws Exception {
        new ObjectMapper().writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,
            "sessionId",3000,"t3Seconds",3,"authorization",TOKEN,"journal",journal.toString(),"robotNamesFile",names.toString()));
    }

    private static HsmsSsCommunicator connect(int port,BlockingQueue<Secs2> received)throws Exception {
        HsmsSsCommunicatorConfig cfg=new HsmsSsCommunicatorConfig();
        cfg.isEquip(false);
        cfg.sessionId(3000);
        cfg.protocol(HsmsSsProtocol.ACTIVE);
        cfg.socketAddress(new InetSocketAddress("127.0.0.1",port));
        cfg.timeout().t3(3);
        HsmsSsCommunicator host=HsmsSsCommunicator.newInstance(cfg);
        host.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(m,c)->{
            if(m.getStream()!=6 || m.getFunction()!=11)return;
            received.add(m.secs2());
            try{c.send(m,6,12,false,binary((byte)0));}
            catch(Exception failure){throw new IllegalStateException(failure);}
        });
        try{
            host.open();
            NativeHostTest.waitFor(host::isCommunicatable,"HSMS selected");
            check(host.send(1,13,true,list()).get().secs2().get(0).getByte(0)==0,"Establish communications");
            return host;
        }catch(Exception | AssertionError failure){host.close();throw failure;}
    }

    private static Secs2 expect(BlockingQueue<Secs2> received,int ceid,String vehicle)throws Exception {
        Secs2 body=received.poll(6,TimeUnit.SECONDS);
        check(body!=null && body.get(1).getInt(0)==ceid,"Expected S6F11 CEID "+ceid);
        Secs2 report=body.get(2).get(0);
        Secs2 values=report.get(1);
        check(report.get(0).getInt(0)==5 && values.size()==4 && vehicle.equals(values.get(0).getAscii())
            && values.get(1).getAscii().isEmpty() && values.get(2).size()==0 && values.get(3).size()==0,
            "ACS RPTID5 format and mapped VehicleID "+vehicle);
        return body;
    }
}
