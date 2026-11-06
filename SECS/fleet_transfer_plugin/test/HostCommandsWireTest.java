package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.BoolValue;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.wcs.grpc.mission.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Exercise the actual Passive HSMS receiver and both device adapters on loopback. */
public final class HostCommandsWireTest {
    private static final String TOKEN=NativeHostTest.TOKEN;
    private static final String ID="TR_TEST_001";
    private static int checks;

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
        checks++;
    }

    private static Secs2 command(String name,Secs2... fields){return list(ascii(name),list(fields));}
    private static Secs2 pair(String name,Secs2 value){return list(ascii(name),value);}
    private static Secs2 update(String priorityField,int priority,String destination){
        return command("UPDATE",pair("COMMANDID",ascii(ID)),pair(priorityField,uint2(priority)),pair("DESTPORT",ascii(destination)));
    }
    private static int ack(HsmsSsCommunicator peer,Secs2 command)throws Exception {
        SecsMessage reply=peer.send(2,41,true,command).get();
        if(reply.getFunction()!=42)throw new AssertionError("Expected S2F42");
        return NativeHostTest.ack(reply.secs2());
    }
    private static int controlState(HsmsSsCommunicator peer)throws Exception {
        return peer.send(1,3,true,list(uint2(17))).get().secs2().get(0).getInt(0);
    }

    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        NativeHostTest.Harness fixture=new NativeHostTest.Harness(root,null);
        AtomicInteger actualPriority=new AtomicInteger(50),rpcCalls=new AtomicInteger();
        AtomicInteger fleetStatus=new AtomicInteger(20);
        AtomicReference<String> fleetVehicle=new AtomicReference<>();
        AtomicBoolean delayPriorityReadback=new AtomicBoolean();
        AtomicInteger pendingFleetPriority=new AtomicInteger();
        AtomicBoolean travelling=new AtomicBoolean();
        AtomicReference<WcsRedirectMissionRequest> redirect=new AtomicReference<>();
        TransferService.FleetClient client=(path,token,body)->{
            if(!"jobQuery".equals(path) || !ID.equals(body.get("jobCode")))return fixture.post(path,token,body);
            if(travelling.get())return fixture.post(path,token,body);
            return TransferService.map("success",true,"data",Collections.singletonList(TransferService.map(
                "jobCode",ID,"workflowId",6837,"containerCode","SIM_CARGO_001","status",fleetStatus.get(),
                "robotId",fleetVehicle.get(),"workflowPriority",actualPriority.get())));
        };
        Server grpc=ServerBuilder.forPort(0).addService(new WcsMissionServiceGrpc.WcsMissionServiceImplBase(){
            @Override public void subscribeMissionStatus(EmptyParameterRequest ignored,StreamObserver<WcsMissionStatusResponse> observer){}
            @Override public void updatePriorityOfMission(WcsUpdatePriorityOfMissionRequest req,StreamObserver<WcsMissionGrpcResponse> observer){
                rpcCalls.incrementAndGet();
                if(!ID.equals(req.getTaskCode()))throw new AssertionError("Wrong WCS task code");
                if(delayPriorityReadback.get())pendingFleetPriority.set(req.getPriority());
                else actualPriority.set(req.getPriority());
                observer.onNext(WcsMissionGrpcResponse.newBuilder().setSuccess(true).setResultCode("0").build());
                observer.onCompleted();
            }
            @Override public void redirectMission(WcsRedirectMissionRequest req,StreamObserver<WcsMissionGrpcResponse> observer){
                redirect.set(req);
                observer.onNext(WcsMissionGrpcResponse.newBuilder().setSuccess(true).setResultCode("0").build());
                observer.onCompleted();
            }
        }).build().start();
        ExecutorService plc=Executors.newSingleThreadExecutor();
        try(ServerSocket modbus=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            Future<byte[]> plcWrite=plc.submit(()->{
                try(Socket connection=modbus.accept()){
                    connection.setSoTimeout(2000);
                    byte[] frame=new byte[12];
                    new DataInputStream(connection.getInputStream()).readFully(frame);
                    connection.getOutputStream().write(frame);
                    return frame;
                }
            });
            int port;
            try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
            Path dir=Files.createTempDirectory("host-commands-wire-"),cfg=dir.resolve("config.json"),file=dir.resolve("state.json");
            Path routeFile=dir.resolve("wcs-route.jsonl");
            TransferJournal routeJournal=new TransferJournal(routeFile);
            HostState saved=new HostState(file);
            HostState.Job job=new HostState.Job();
            job.request=TransferService.normalize(HostProtocol.transfer(NativeHostTest.transferBody(ID)));
            job.phase="ACTIVE";
            job.next=1;
            saved.data.jobs.put(ID,job);
            saved.save();
            Map<String,Object> buzzer=TransferService.map("nodeCode","lay01-dist1-89","host","127.0.0.1","port",modbus.getLocalPort(),"unitId",1,"register",256,"value",3);
            new ObjectMapper().writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,
                "sessionId",3000,"t3Seconds",3,"authorization",TOKEN,"journal",file.toString(),
                "portBuzzers",TransferService.map("PORT_89",buzzer),"alarmMappings",TransferService.map(
                    "PP_NoPath",TransferService.map("alid",900001,"category",6,"text","FLEET_PATH_NOT_FOUND"))));
            String previous=System.getProperty("servostar.secs.config");
            System.setProperty("servostar.secs.config",cfg.toString());
            WcsStatusMonitor monitor=new WcsStatusMonitor(routeJournal,"127.0.0.1",grpc.getPort());
            NativeHostService server=new NativeHostService(new TransferService(client,routeJournal),client,monitor);
            ConcurrentMap<Integer,AtomicInteger> events=new ConcurrentHashMap<>();
            HsmsSsCommunicator peer=null;
            try{
                monitor.start();
                server.start();
                peer=NativeHostTest.peer(port,events,ConcurrentHashMap.newKeySet(),new AtomicBoolean(),new AtomicBoolean());
                peer.send(1,13,true,list()).get();
                check(controlState(peer)==5,"Old journal remains online on first handshake");
                SecsMessage offline=peer.send(1,15,true,empty()).get();
                check(offline.getFunction()==16 && offline.secs2().getByte(0)==0,"S1F16 confirms offline");
                Path snapshot=Files.copy(file,dir.resolve("offline-copy.json"));
                check(controlState(peer)==3 && new HostState(snapshot).hostOffline,"Offline state is observable and durable");
                check(ack(peer,update("PRIORITY",90,""))==2 && rpcCalls.get()==0,"Offline blocks a mutation");
                check(peer.send(2,49,true,NativeHostTest.transferBody("OFFLINE_TEST")).get().secs2().get(0).getByte(0)==2
                    && fixture.submits==0,"Offline blocks TRANSFER");
                NativeHostTest.waitFor(()->events.containsKey(1),"Offline transition reported and acknowledged");
                SecsMessage allAlarms=peer.send(5,5,true,list()).get();
                check(allAlarms.getFunction()==6 && allAlarms.secs2().size()==1 && allAlarms.secs2().get(0).get(0).getByte(0)==6,
                    "S5F6 returns configured cleared alarms while offline");
                check(peer.send(5,5,true,list(uint4(555))).get().secs2().size()==0,"Unknown ALID omitted");
                check(peer.send(5,5,true,list(uint2(1))).get().getFunction()==0,"Invalid ALID format aborts query");
                SecsMessage online=peer.send(1,17,true,empty()).get();
                check(online.getFunction()==18 && online.secs2().getByte(0)==0 && controlState(peer)==5,"S1F18 restores online");
                NativeHostTest.waitFor(()->events.containsKey(3),"Online transition reported and acknowledged");
                Map<String,Object> warning=fixture.event("WARNING",false);
                warning.put("commandId","ALARM_TEST");
                warning.put("warnCode","PP_NoPath");
                java.lang.reflect.Field field=NativeHostService.class.getDeclaredField("engine");
                field.setAccessible(true);
                NativeHostEngine engine=(NativeHostEngine)field.get(server);
                engine.observe(warning);
                Secs2 row=peer.send(5,5,true,list(uint4(900001))).get().secs2().get(0);
                check((row.get(0).getByte(0)&255)==134 && row.get(1).getLong(0)==900001,"S5F6 returns active ALCD set bit");

                check(ack(peer,update("PRIORTY",90,""))==0 && actualPriority.get()==10 && rpcCalls.get()==1,
                    "Live queue profile: status 20 without a vehicle permits priority update and WCS readback");
                fleetVehicle.set("2");
                check(ack(peer,update("PRIORITY",80,""))==2 && rpcCalls.get()==1 && actualPriority.get()==10,
                    "Status 20 with an assigned vehicle rejects the update without a WCS write");
                fleetVehicle.set("");
                fleetStatus.set(10);
                check(ack(peer,update("PRIORITY",90,""))==0 && rpcCalls.get()==1,"Same effective priority does not mutate WCS again");
                delayPriorityReadback.set(true);
                check(ack(peer,update("PRIORITY",80,""))==4 && rpcCalls.get()==2,"Unconfirmed mutation returns acceptance only");
                check(ack(peer,update("PRIORITY",70,""))==5 && rpcCalls.get()==2,"Unconfirmed update prevents a conflicting write");
                actualPriority.set(pendingFleetPriority.get());
                NativeHostTest.waitFor(()->events.getOrDefault(212,new AtomicInteger()).get()>=3,"Confirmed update report received");
                check(ack(peer,update("PRIORITY",80,""))==0 && rpcCalls.get()==2,"Readback settles the pending update without replay");
                NativeHostTest.waitFor(()->events.get(212).get()>=4,"All priority confirmations received before destination test");
                check(ack(peer,update("PRIORITY",80,"PORT_84"))==2 && redirect.get()==null,"An unassigned job cannot be redirected");
                NativeHostTest.waitFor(()->events.containsKey(213),"Destination update failure report received");
                synchronized(engine.state){
                    check(engine.state.data.reports.stream().anyMatch(d->"COMMAND_REJECT".equals(d.kind)
                        && "UPDATE".equals(d.operation) && "JOB_NOT_IN_LOADED_TRAVEL".equals(d.detail)),
                        "UPDATE rejection reports its exact reason separately from CEID 213");
                }
                travelling.set(true);
                fixture.activeControlJob=true;
                fixture.carried=true;
                fixture.robotNode="lay01-dist1-89";
                fixture.rcsState=9;
                routeJournal.reserve(job.request);
                routeJournal.accepted(ID);
                for(Map<String,Object> fact:fixture.trace){
                    routeJournal.observe(fact);
                    engine.observe(fact);
                    if("MOVE_BEGIN".equals(fact.get("status")) && Integer.valueOf(2).equals(fact.get("sequence")))break;
                }
                fixture.rcsCode="C-TEST-6837-1";
                check(ack(peer,update("PRIORITY",80,"PORT_84"))==2 && redirect.get()==null,"A handling step cannot be redirected");
                fixture.rcsCode="W-TEST-6837-1";
                int baseline=events.get(212).get();
                check(ack(peer,update("PRIORITY",80,"PORT_84"))==4,"Redirect RPC acceptance is only HCACK4");
                WcsRedirectMissionRequest rpc=redirect.get();
                check(ID.equals(rpc.getTaskCode()) && rpc.getRedirectNodeCount()==1
                    && WorkPorts.node("PORT_84").equals(rpc.getRedirectNode(0).getCode())
                    && rpc.getRedirectNode(0).getActionOnArrived(0).getType()==ActionTypeGto.DOWN_CONTAINER
                    && BoolValue.parseFrom(rpc.getUnknownFields().getField(3).getLengthDelimitedList().get(0)).getValue(),
                    "WCS receives the exact task, work node, drop action and active redirect option");
                check(ack(peer,update("PRIORITY",80,"PORT_90"))==5 && redirect.get()==rpc && events.get(212).get()==baseline,
                    "Pending redirect blocks another RPC and does not produce a success report");
                for(Map<String,Object> fact:fixture.trace){
                    if(!Integer.valueOf(2).equals(fact.get("sequence")))continue;
                    Map<String,Object> redirected=new java.util.LinkedHashMap<>(fact);
                    redirected.put("responseId",fact.get("responseId")+"-redirect");
                    redirected.put("sequence",3);
                    for(String key:Arrays.asList("currentNode","nextNode","carrierLocation"))
                        if(WorkPorts.node("PORT_97").equals(redirected.get(key)))redirected.put(key,WorkPorts.node("PORT_84"));
                    routeJournal.observe(redirected);
                    engine.observe(redirected);
                    if("MOVE_BEGIN".equals(redirected.get("status"))){
                        NativeHostTest.waitFor(()->events.get(212).get()>baseline,"WCS redirect callback produced CEID 212");
                        Secs2 current=peer.send(1,3,true,list(uint2(23))).get().secs2().get(0).get(0);
                        check("PORT_84".equals(current.get(2).get(0).get(2).getAscii()),"SV23 reports the new destination");
                    }
                }
                fixture.activeControlJob=false;
                fixture.carried=false;
                fixture.robotNode=WorkPorts.node("PORT_84");
                fixture.cargoNode=WorkPorts.containerNode("PORT_84");
                NativeHostTest.waitFor(()->events.get(212).get()>baseline && events.containsKey(207),"New route confirmed and completed over HSMS");
                synchronized(engine.state){
                    HostState.Delivery completed=engine.state.data.reports.stream().filter(d->d.ceid==207).findFirst().get();
                    check("PORT_84".equals(completed.destination) && "PORT_84".equals(completed.location)
                        && "PORT_84".equals(engine.state.data.jobs.get(ID).request.get("DESTPORT")),
                        "Transport completion and host state retain the new destination");
                    Map<String,Object> restored=TransferService.normalize(engine.state.data.jobs.get(ID).request);
                    check(Boolean.TRUE.equals(new TransferJournal(routeFile).stage(restored,"completed").get("success")),
                        "Journal replay retains the updated route and delivery evidence");
                }
                check(ack(peer,command("BUZZER",pair("PORTID",ascii("PORT_999"))))==6,"Unknown buzzer port HCACK6");
                check(ack(peer,command("BUZZER",pair("PORTID",ascii("PORT_97"))))==2,"Unconfigured buzzer port HCACK2");
                check(ack(peer,command("BUZZER",pair("PORTID",ascii("PORT_89"))))==0,"Confirmed Modbus FC06 echo acknowledges buzzer command");
                check(Arrays.equals(plcWrite.get(2,TimeUnit.SECONDS),new byte[]{0,1,0,0,0,6,1,6,1,0,0,3}),"One Modbus write for the configured output");
            }finally{
                if(peer!=null)peer.close();
                server.close();
                monitor.close();
                if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);
            }
        }finally{
            plc.shutdownNow();
            grpc.shutdownNow();
        }
        System.out.println("PASS: "+checks+" host mode, alarm list, priority and destination updates and port buzzer checks over loopback HSMS, gRPC and Modbus TCP.");
    }
}
