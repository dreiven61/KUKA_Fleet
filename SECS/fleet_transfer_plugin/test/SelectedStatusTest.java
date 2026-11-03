package servostar.fleet.transfer;

import java.util.*;
import java.nio.file.*;
import java.net.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** SKI ACS SVID shape, live/retained union and blocked-report query isolation.
 * Fixture data + localhost HSMS only. No live Fleet commands. */
public final class SelectedStatusTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Map<String,Object> request(String id){return TransferService.map("COMMANDID",id,"PRIORITY",1,"REPLACE",0,"CARRIERID","SIM_CARGO_001","SOURCEPORT","PORT_97","DESTPORT","PORT_90");}
    static HostState.Job job(HostState s,String id,String phase){HostState.Job j=new HostState.Job();j.request=request(id);j.phase=phase;s.data.jobs.put(id,j);return j;}
    static final class Fleet implements TransferService.FleetClient {
        volatile String defect="";volatile boolean loaded;volatile String cargoPort="PORT_90";
        final List<Map<String,Object>> active=new ArrayList<>();int mutations,reads;
        public Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception{
            reads++;
            if(!Arrays.asList("robotQuery","jobQuery","containerQueryAll").contains(path)){mutations++;throw new AssertionError("Status query mutation "+path);}
            if(defect.equals("timeout"))throw new java.io.IOException("fixture unavailable");
            if(defect.equals("failed"))return TransferService.map("success",false,"data",Collections.emptyList());
            List<Map<String,Object>> rows=new ArrayList<>();
            if(path.equals("robotQuery"))for(int i=1;i<=3;i++)rows.add(TransferService.map("robotId",String.valueOf(i),"status",3,
                "occupyStatus",loaded && i==2?1:0,"containerCode",loaded && i==2?"SIM_CARGO_001":"","liftStatus",loaded && i==2?1:0,
                "missionCode",loaded && i==2?"OWN":"","rcsMissionCode",loaded && i==2?"C-1-2":""));
            else if(path.equals("containerQueryAll"))rows.add(TransferService.map("containerCode","SIM_CARGO_001","nodeCode",WorkPorts.containerNode(cargoPort),
                "inMapStatus",1,"isCarry",loaded && !defect.equals("ownerMismatch")?1:0,"emptyFullStatus",1,"containerModelCode","SIM_TRAY_001"));
            else for(Map<String,Object> j:active)if(String.valueOf(j.get("status")).equals(String.valueOf(body.get("status"))))rows.add(new LinkedHashMap<>(j));
            if(defect.equals("duplicate") && path.equals("containerQueryAll"))rows.add(new LinkedHashMap<>(rows.get(0)));
            if(defect.equals("truncated") && path.equals("jobQuery"))for(int i=0;i<1000;i++)rows.add(Collections.emptyMap());
            return TransferService.map("success",true,"code","0","data",rows);
        }
    }
    static Secs2 one(SelectedStatusService s,int id)throws Exception{return s.read(list(uint2(id))).get(0);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);Fleet fleet=new Fleet();HostState state=new HostState(null);SelectedStatusService s=new SelectedStatusService(fleet,state,NativeHostTest.TOKEN);
        Secs2 all=s.read(list());check(all.size()==7,"Empty request returns seven supported SVIDs");
        Secs2 ordered=s.read(list(uint2(58),uint2(25),uint2(23),uint2(21),uint2(25),uint2(999)));
        check(ordered.size()==6 && ordered.get(0).size()==10 && ordered.get(1).size()==3 && ordered.get(5).size()==0,"Caller order, repeats and unknown SVID empty slot");
        check(ordered.get(1).get(1).get(0).getAscii().equals("2") && ordered.get(1).get(1).get(1).getInt(0)==2,"Actual three idle unassigned AMRs");
        check(ordered.get(3).get(0).get(0).getAscii().equals("SIM_CARGO_001") && ordered.get(3).get(0).get(2).getAscii().equals("PORT_90"),"Current carrier and port mapping");
        check(ordered.get(3).get(0).get(3).getAscii().isEmpty(),"Unknown installation timestamp is not fabricated");
        check(one(s,58).get(3).get(1).getInt(0)==3 && one(s,58).get(8).get(1).getInt(0)==2,"Occupied source ready to unload; empty station ready to load");
        HostState.Job own=job(state,"OWN","UNCERTAIN");
        Secs2 transfers=one(s,23);check(transfers.size()==1 && transfers.get(0).get(0).get(0).getAscii().equals("OWN") && transfers.get(0).get(1).getInt(0)==6,"Missing uncertain intent stays visible as WAITING");
        check(one(s,58).get(3).get(1).getInt(0)==1 && one(s,58).get(8).get(1).getInt(0)==1,"Both endpoints reserved by retained unresolved work");
        own.phase="COMPLETED";HostState.Delivery terminal=new HostState.Delivery();terminal.kind="TRANSFER";terminal.command="OWN";terminal.key="test-complete";terminal.ceid=207;state.add(terminal);
        check(one(s,23).size()==1 && one(s,23).get(0).get(1).getInt(0)==6,"Completed but unACKed transfer remains visible");
        terminal.state="ACKED";check(one(s,23).size()==0,"Only completion ACK removes settled transfer from current list");
        fleet.active.add(TransferService.map("jobCode","EXTERNAL","status",20,"workflowId",99,"workflowPriority",2,
            "containerCode","OTHER","beginCellCode","lay01-dist1-84","finalNodeCode","lay01-dist1-86"));
        check(one(s,23).size()==1 && one(s,23).get(0).get(0).get(0).getAscii().equals("EXTERNAL"),"External/manual active Fleet work is not hidden");
        fleet.active.clear();own.phase="ACTIVE";own.vehicle="2";own.next=5;fleet.loaded=true;
        check(one(s,25).get(1).get(1).getInt(0)==5,"Observed pickup stage reports ACQUIRING");
        check(one(s,21).get(0).get(1).getAscii().equals("2") && one(s,21).get(0).get(2).getAscii().equals("2"),"Actual robot owns carried carrier");
        own.paused=true;own.next=8;check(one(s,23).get(0).get(1).getInt(0)==3,"PAUSED state preserved");own.paused=false;own.control="ABORT";
        check(one(s,23).get(0).get(1).getInt(0)==5,"ABORTING state preserved");own.control="";
        for(String defect:Arrays.asList("timeout","failed","duplicate","truncated","ownerMismatch")){
            int id=defect.equals("duplicate")?58:defect.equals("ownerMismatch")?21:23;
            fleet.defect=defect;try{one(s,id);throw new AssertionError("Status returned success for "+defect);}catch(IllegalStateException | java.io.IOException expected){checks++;}
        }
        fleet.defect="ownerMismatch";
        check(one(s,25).size()==3 && one(s,23).size()==1 && one(s,58).size()==10,"Carrier ownership transition cannot fail unrelated AGV/work/port SVIDs");
        fleet.defect="";fleet.loaded=false;own.phase="COMPLETED";fleet.cargoPort="PORT_97";
        check(one(s,21).get(0).get(2).getAscii().equals("PORT_97"),"Repeated query reads changed live location, no stale cached snapshot");
        for(int i=0;i<20;i++){check(s.read(list(uint2(23),uint2(25),uint2(58),uint2(21))).size()==4,"Continuous read-only query "+i);}
        check(fleet.mutations==0,"No transfer replay from status reads");
        try{s.read(list(uint4(23)));throw new AssertionError("Wrong SVID type accepted");}catch(IllegalArgumentException expected){checks++;}
        NativeHostTest.Harness h=new NativeHostTest.Harness(root,null);HostState.Job retained=job(h.state,"OLD","COMPLETED");
        HostState.Delivery pending=new HostState.Delivery();pending.kind="TRANSFER";pending.key="old207";pending.command="OLD";pending.ceid=207;h.state.add(pending);
        check(NativeHostTest.ack(h.engine.transfer(request("NEW_ID")))==2 && h.submits==0,"New ID cannot bypass unACKed prior carrier completion");
        check(NativeHostTest.ack(h.engine.transfer(request("OLD")))==5 && h.submits==0,"Original ID remains durable duplicate");
        capturedTransition(root);
        wire();
        System.out.println("PASS: "+checks+" S1F3/4 snapshot, current-work union, pending-ACK duplicate guards and independent HSMS query checks; fixtures/loopback only.");
    }
    static final class CapturedFleet implements TransferService.FleetClient {
        final List<Map<String,Object>> records;
        final boolean settles;
        final List<String> paths=new ArrayList<>();
        int robotReads;
        CapturedFleet(List<Map<String,Object>> rows,boolean settle){records=rows;settles=settle;}
        @SuppressWarnings("unchecked")
        public Map<String,Object> post(String path,String token,Map<String,Object> body){
            check(Arrays.asList("robotQuery","containerQueryAll","jobQuery").contains(path),"Captured replay is read-only");
            paths.add(path);if(path.equals("robotQuery"))robotReads++;
            int first=settles && robotReads>1?72198:72190;
            for(Map<String,Object> record:records){
                int id=((Number)record.get("id")).intValue();
                if(id<first || id>=first+8 || !path.equals(record.get("path")))continue;
                Map<String,Object> request=(Map<String,Object>)record.get("request");
                if(path.equals("jobQuery") && !String.valueOf(body.get("status")).equals(String.valueOf(request.get("status"))))continue;
                return (Map<String,Object>)record.get("response");
            }
            throw new AssertionError("Missing captured read "+path+body);
        }
    }
    @SuppressWarnings("unchecked")
    static void capturedTransition(Path root)throws Exception{
        List<Map<String,Object>> records=new ObjectMapper().readValue(root.resolve("test/fixtures/status_transition_20261102.json").toFile(),List.class);
        for(int id:new int[]{25,23,58}){
            CapturedFleet f=new CapturedFleet(records,false);
            Secs2 value=one(new SelectedStatusService(f,new HostState(null),NativeHostTest.TOKEN),id);
            check(value.size()==(id==25?3:id==23?1:10),"Actual pickup transition still reports SV"+id);
            check(f.paths.size()==(id==25?1:id==23?6:7),"Only necessary APIs for SV"+id);
        }
        CapturedFleet permanent=new CapturedFleet(records,false);
        try{one(new SelectedStatusService(permanent,new HostState(null),NativeHostTest.TOKEN),21);throw new AssertionError("False carrier success");}
        catch(IllegalStateException expected){check(expected.getMessage().equals("CARRIER_OWNER_TRANSITION"),"Exact captured carrier mismatch remains a failure when persistent");}
        check(permanent.robotReads==3 && permanent.paths.size()==6,"Persistent conflict stops after three read-only snapshots");
        CapturedFleet transientFleet=new CapturedFleet(records,true);
        Secs2 carrier=one(new SelectedStatusService(transientFleet,new HostState(null),NativeHostTest.TOKEN),21);
        check(transientFleet.robotReads==2 && transientFleet.paths.size()==4,"Carrier transition re-reads fresh robot and cargo once");
        boolean found=false;for(Secs2 row:carrier)if(row.get(0).getAscii().equals("SIM_CARGO_001"))found=row.get(1).getAscii().equals("2") && row.get(2).getAscii().equals("2");
        check(found,"Recovered carrier uses actual AMR2 ownership, not stale port or empty inventory");
        CapturedFleet combined=new CapturedFleet(records,true);
        check(new SelectedStatusService(combined,new HostState(null),NativeHostTest.TOKEN).read(list()).size()==7 && combined.robotReads==2,"Combined all-SVID query recovers as a complete snapshot");
        // Port queries still reject malformed inventory instead of silently
        // hiding a carrier as an empty port after dependency separation.
        TransferService.FleetClient invalid=new TransferService.FleetClient(){final Fleet base=new Fleet();
        @Override public Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception{
            Map<String,Object> result=base.post(path,token,body);
            if(path.equals("containerQueryAll"))((List<Map<String,Object>>)result.get("data")).get(0).put("isCarry",9);
            return result;
        }};
        try{one(new SelectedStatusService(invalid,new HostState(null),NativeHostTest.TOKEN),58);throw new AssertionError("Invalid inventory was empty port success");}
        catch(IllegalStateException expected){check(true,"Malformed inventory remains unavailable for port query");}
    }
    static void wire()throws Exception{
        Fleet f=new Fleet();TransferJournal journal=new TransferJournal(null);journal.streamReady();TransferService service=new TransferService(f,journal);
        int port;try(ServerSocket sock=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=sock.getLocalPort();}
        Path folder=Files.createTempDirectory("status-wire-"),cfg=folder.resolve("config.json");
        new ObjectMapper().writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",1,
            "authorization",NativeHostTest.TOKEN,"journal",folder.resolve("state.json").toString()));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",cfg.toString());
        NativeHostService server=new NativeHostService(service,f,new WcsStatusMonitor(journal));HsmsSsCommunicator peer=null;AtomicInteger reports=new AtomicInteger();
        try{
            server.start();HsmsSsCommunicatorConfig pc=new HsmsSsCommunicatorConfig();pc.isEquip(false);pc.sessionId(3000);pc.protocol(HsmsSsProtocol.ACTIVE);
            pc.socketAddress(new InetSocketAddress("127.0.0.1",port));pc.timeout().t3(3);pc.timeout().t5(1);peer=HsmsSsCommunicator.newInstance(pc);
            peer.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(m,c)->{try{if(m.getStream()==6 && m.getFunction()==11){reports.incrementAndGet();c.send(m,6,12,false,binary((byte)1));}}catch(Exception ex){throw new RuntimeException(ex);}});
            peer.open();NativeHostTest.waitFor(peer::isCommunicatable,"Selected-status wire connected");peer.send(1,13,true,list());
            SecsMessage first=peer.send(1,3,true,list(uint2(23),uint2(25),uint2(58),uint2(21))).get();
            check(first.getFunction()==4 && first.secs2().size()==4 && first.secs2().get(1).size()==3,"Actual S1F4 wire structure");
            java.lang.reflect.Field field=NativeHostService.class.getDeclaredField("engine");field.setAccessible(true);NativeHostEngine engine=(NativeHostEngine)field.get(server);
            synchronized(engine.state){job(engine.state,"UNACKED","COMPLETED");HostState.Delivery d=new HostState.Delivery();d.kind="TRANSFER";d.key="wire207";d.command="UNACKED";d.ceid=207;d.location="PORT_90";engine.state.add(d);engine.state.save();}
            NativeHostTest.waitFor(()->reports.get()==1,"Host rejects retained completion ACK");
            NativeHostTest.waitFor(()->server.status(NativeHostTest.TOKEN).toString().contains("BLOCKED"),"Negative ACK persists BLOCKED");
            for(int i=0;i<5;i++)check(peer.send(1,3,true,list(uint2(23))).get().secs2().get(0).size()==1,"Blocked report cannot block S1F4 query "+i);
            f.defect="failed";check(peer.send(1,3,true,list(uint2(23))).get().getFunction()==0,"Unavailable Fleet returns S1F0, not false empty S1F4");
            f.defect="";check(peer.send(1,3,true,list(uint2(25))).get().getFunction()==4,"Query recovers after source becomes available");
            check(f.mutations==0,"Wire queries never send Fleet operations");
        }finally{if(peer!=null)peer.close();server.close();if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);}
    }
}
