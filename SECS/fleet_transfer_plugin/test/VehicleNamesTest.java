package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Config boundary, persisted identity, actual SML fields and localhost HSMS.
 * Uses explicit Fleet fixtures; never changes the live server/robots. */
public final class VehicleNamesTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static String xml(String first){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><RobotNames version=\"1\"><Robot fleetId=\"1\" vehicleId=\""+first+"\"/><Robot fleetId=\"2\" vehicleId=\"AGV002\"/><Robot fleetId=\"3\" vehicleId=\"AGV003\"/></RobotNames>";}
    static void write(Path file,String value)throws Exception{Files.write(file,value.getBytes(StandardCharsets.UTF_8));}
    static void badXml(Path file,String value)throws Exception{
        write(file,value);try{VehicleNames.read(file);throw new AssertionError("Invalid XML accepted");}catch(Exception expected){checks++;}
    }
    static Secs2 row(Secs2 report)throws Exception{return report.get(2).get(0).get(1);}
    static Secs2 transfer(String source)throws Exception{
        return list(uint4(0),ascii(""),ascii("TRANSFER"),list(
            list(ascii("COMMANDINFO"),list(list(ascii("COMMANDID"),ascii("RETURN_NAMED")),list(ascii("PRIORITY"),uint2(1)))),
            list(ascii("TRANSFERINFO"),list(list(ascii("CARRIERID"),ascii("SIM_CARGO_001")),list(ascii("SOURCEPORT"),ascii(source)),list(ascii("DESTPORT"),ascii("PORT_97"))))));
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]),dir=Files.createTempDirectory("vehicle-names-"),file=dir.resolve("names.xml"),stateFile=dir.resolve("state.json");
        HostState state=new HostState(stateFile);VehicleNames names=new VehicleNames(state);
        check(names.vehicle("1").equals("1"),"Without opt-in, actual Fleet IDs retain previous behavior");
        names.configure(dir.resolve("optional.xml"),true);names.refresh(false);names.requireReady();
        check(!Boolean.TRUE.equals(names.status().get("enabled")),"Missing default XML is optional before first activation");
        write(file,xml("001"));names.configure(file);names.refresh(false);names.requireReady();
        check(names.vehicle("1").equals("001"),"Leading zero preserved exactly as ASCII identifier");
        check(names.vehicle("").isEmpty(),"Unknown/unassigned vehicle stays empty");
        check(names.vehicle("2").equals("AGV002"),"Names come from XML, no compiled site list");
        check(names.sourceFromSecs("001").equals("1"),"Inbound VehicleID translates to actual Fleet ID");
        check(names.sourceFromSecs("AMR_001").equals("AMR_1"),"Loaded-source compatibility alias translates bidirectionally");
        check(names.sourceFromSecs("PORT_97").equals("PORT_97"),"Port inputs remain ports");
        try{names.sourceFromSecs("1");throw new AssertionError("Raw Fleet ID ambiguously accepted in mapped mode");}catch(IllegalArgumentException expected){checks++;}
        Map<String,Object> request=HostProtocol.transfer(transfer("001"),names);
        check(request.get("SOURCEPORT").equals("1"),"Host parser stores actual Fleet ID for loaded ownership");
        check(((List<?>)TransferService.buildMission(request).get("robotIds")).equals(Arrays.asList("1")),"Fleet mission payload uses real ID, never mapped name");
        LoadedRecoveryTest.Fleet dispatch=new LoadedRecoveryTest.Fleet();dispatch.node="lay01-dist1-105";
        TransferJournal dispatchJournal=new TransferJournal(null);dispatchJournal.streamReady();
        new TransferService(dispatch,dispatchJournal).submit(HostProtocol.transfer(transfer("AGV002"),names),"fixture");
        check(dispatch.mutations==1 && ((List<?>)dispatch.last.get("robotIds")).equals(Arrays.asList("2")),"Named MCS source submits once to actual Fleet robot 2 after loaded ownership preflight");
        try{HostProtocol.transfer(transfer("UNMAPPED"),names);throw new AssertionError("Unknown mapped source accepted");}catch(HostProtocol.ParameterError expected){check(expected.field.equals("SOURCEPORT"),"Reject unknown name before dispatch");}

        SelectedStatusTest.Fleet fleet=new SelectedStatusTest.Fleet();fleet.loaded=true;
        SelectedStatusService status=new SelectedStatusService(fleet,state,NativeHostTest.TOKEN,names);
        Secs2 vehicles=SelectedStatusTest.one(status,25);check(vehicles.get(0).get(0).getAscii().equals("001"),"SV25 mapped ASCII VehicleID");
        Secs2 carriers=SelectedStatusTest.one(status,21);check(carriers.get(0).get(1).getAscii().equals("AGV002") && carriers.get(0).get(2).getAscii().equals("AGV002"),"SV21 owner and carried location use same mapped name");
        HostState.Job job=SelectedStatusTest.job(state,"RETURN_NAMED","ACCEPTED");job.request=request;job.vehicle="1";
        Secs2 transfers=SelectedStatusTest.one(status,23);check(transfers.get(0).get(2).get(0).get(1).getAscii().equals("001"),"SV23 loaded SOURCEPORT reports MCS name");
        HostState.Delivery alarm=new HostState.Delivery();alarm.vehicle="1";alarm.alarmId=900001;alarm.text="PATH_BLOCKED";state.data.alarms.put("A",alarm);
        check(SelectedStatusTest.one(status,3).get(0).get(0).getAscii().equals("001"),"SV3 alarm unit reports same mapped name");
        state.data.alarms.clear();
        for(String kind:Arrays.asList("UNIT","VEHICLE","CARRIER","RECOVERY")){
            HostState.Delivery d=new HostState.Delivery();d.kind=kind;d.id=1;d.vehicle="1";d.command="RETURN_NAMED";d.location="1";d.port="1";d.ceid=702;
            Secs2 body=HostProtocol.body(d,job,names),values=row(body);
            check(values.get(kind.equals("RECOVERY")?2:0).getAscii().equals("001"),"Mapped outbound "+kind+" VehicleID");
            if(kind.equals("CARRIER"))check(values.get(2).getAscii().equals("001"),"Carrier report carries mapped location");
            check(d.vehicle.equals("1") && job.vehicle.equals("1"),"Wire conversion never changes durable Fleet correlation");
        }
        HostState.Delivery transferReport=new HostState.Delivery();transferReport.kind="TRANSFER";transferReport.command="RETURN_NAMED";transferReport.location="1";
        Secs2 transferValues=row(HostProtocol.body(transferReport,job,names));
        check(transferValues.get(1).get(0).get(0).get(1).getAscii().equals("001") && transferValues.get(1).get(0).get(1).getAscii().equals("001"),"Transfer source and location use mapped identity");
        job.phase="COMPLETED";state.save();
        HostState restarted=new HostState(stateFile);VehicleNames restored=new VehicleNames(restarted);
        check(restored.vehicle("1").equals("001"),"Accepted mapping survives plugin restart");

        write(file,xml("AGV_NEW"));names.refresh(true);
        check(!Boolean.TRUE.equals(names.status().get("ready")) && names.vehicle("1").equals("001"),"Connected host prevents identity change while retaining old report names");
        names.refresh(false);names.requireReady();check(names.vehicle("1").equals("AGV_NEW"),"Idle disconnected XML edit applies without source edit/rebuild");
        HostState.Delivery pending=new HostState.Delivery();pending.id=77;pending.kind="UNIT";pending.vehicle="1";pending.state="PENDING";state.data.reports.add(pending);state.save();
        write(file,xml("AGV_LATER"));names.refresh(false);
        check(names.vehicle("1").equals("AGV_NEW") && !Boolean.TRUE.equals(names.status().get("ready")),"UnACKed report pins original identity");
        HostState rebooted=new HostState(stateFile);VehicleNames rebootNames=new VehicleNames(rebooted);rebootNames.configure(file);rebootNames.refresh(false);
        check(rebootNames.vehicle("1").equals("AGV_NEW"),"Changed file on reboot cannot rename an unACKed report");
        pending.state="ACKED";names.refresh(false);names.requireReady();check(names.vehicle("1").equals("AGV_LATER"),"Change applies after old ACK completes");
        HostState.Delivery activeAlarm=new HostState.Delivery();activeAlarm.vehicle="1";state.data.alarms.put("active",activeAlarm);write(file,xml("AFTER_CLEAR"));names.refresh(false);
        check(names.vehicle("1").equals("AGV_LATER"),"Set/clear alarm pair pins name until clear");state.data.alarms.clear();names.refresh(false);names.requireReady();
        job.phase="ACCEPTED";write(file,xml("AFTER_WORK"));names.refresh(false);check(names.vehicle("1").equals("AFTER_CLEAR"),"Active transport pins name");job.phase="COMPLETED";
        write(file,"<RobotNames version=\"1\"><Robot fleetId=\"1\" vehicleId=\"ONE\"/></RobotNames>");names.refresh(false);
        check(!Boolean.TRUE.equals(names.status().get("ready")) && names.vehicle("1").equals("AFTER_CLEAR"),"Missing Fleet robot mapping blocks new admission without raw fallback");
        try{SelectedStatusTest.one(status,25);throw new AssertionError("Invalid mapping became successful vehicle snapshot");}catch(IllegalStateException expected){checks++;}
        write(file,xml("PORT_97"));names.refresh(false);check(!Boolean.TRUE.equals(names.status().get("ready")),"Names cannot collide with work port aliases");
        Files.delete(file);names.refresh(false);check(!Boolean.TRUE.equals(names.status().get("ready")) && names.vehicle("1").equals("AFTER_CLEAR"),"Deleted active file never silently restores numeric IDs");
        for(String bad:Arrays.asList(
            xml("AGV002"),xml("AMR_AGV002"),xml("한글"),xml("123456789012345678901234567890123"),xml("bad name"),
            "<RobotNames version=\"2\"/>","<RobotNames version=\"1\"/>",xml("001").replace("fleetId=\"2\"","fleetId=\"1\""),xml("001").replace("vehicleId=\"001\"","robotName=\"001\""),
            "<!DOCTYPE RobotNames [<!ENTITY leak SYSTEM \"file:///not-a-site-config\">]><RobotNames version=\"1\">&leak;</RobotNames>","<RobotNames"))badXml(file,bad);
        check(fleet.mutations==0,"Identity/status checks never operate robots");
        wire(root,dir.resolve("wire"));
        System.out.println("PASS: "+checks+" XML RobotID/VehicleID, leading zeros, bidirectional fields, Fleet payload IDs, durable ACK/alarm/work guards, malformed XML and real HSMS checks; fixtures/loopback only.");
    }
    @SuppressWarnings("unchecked")
    static void wire(Path root,Path dir)throws Exception{
        Files.createDirectories(dir);Path xmlFile=dir.resolve("names.xml"),config=dir.resolve("config.json");write(xmlFile,xml("001"));
        int port;try(ServerSocket reserved=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=reserved.getLocalPort();}
        SelectedStatusTest.Fleet fleet=new SelectedStatusTest.Fleet();
        new ObjectMapper().writeValue(config.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",1,"authorization",NativeHostTest.TOKEN,
            "journal",dir.resolve("state.json").toString(),"robotNamesFile",xmlFile.toString()));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",config.toString());
        NativeHostService server=new NativeHostService(new TransferService(fleet),fleet,new WcsStatusMonitor(new TransferJournal(null)));
        HsmsSsCommunicator peer=null;
        try{
            server.start();
            peer=NativeHostTest.peer(port,new ConcurrentHashMap<Integer,AtomicInteger>(),ConcurrentHashMap.<Long>newKeySet(),new AtomicBoolean(false),new AtomicBoolean(false));
            check(peer.send(1,13,true,list()).get().getFunction()==14,"Mapping host establishes SECS communication");
            check(peer.send(1,3,true,list(uint2(25))).get().secs2().get(0).get(0).get(0).getAscii().equals("001"),"Actual HSMS S1F4 preserves leading zero from XML");
            Map<String,Object> colliding=FixtureRunner.catalog();
            ((List<Map<String,Object>>)colliding.get("ports")).get(0).put("legacyAlias","001");
            try{WorkPorts.install(colliding);throw new AssertionError("Fleet port alias replaced accepted VehicleID");}catch(IllegalStateException expected){checks++;}
            write(xmlFile,xml("CONNECTED_EDIT"));
            java.lang.reflect.Field engineField=NativeHostService.class.getDeclaredField("engine");engineField.setAccessible(true);NativeHostEngine engine=(NativeHostEngine)engineField.get(server);
            try{server.refreshCatalog(NativeHostTest.TOKEN);throw new AssertionError("Connected rename applied");}catch(IllegalStateException expected){checks++;}
            check(peer.send(1,3,true,list(uint2(25))).get().getFunction()==0,"Mapping mismatch returns S1F0, never success with wrong identity");
            write(xmlFile,xml("001"));server.refreshCatalog(NativeHostTest.TOKEN);
            check(peer.send(1,3,true,list(uint2(25))).get().getFunction()==4,"Restoring accepted XML recovers query without restart");
            check(engine.vehicleNames.vehicle("1").equals("001"),"Host remains on accepted name throughout session");
            check(fleet.mutations==0,"Wire tests never send Fleet control/transport");
        }finally{if(peer!=null)peer.close();server.close();if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);}
    }
}
