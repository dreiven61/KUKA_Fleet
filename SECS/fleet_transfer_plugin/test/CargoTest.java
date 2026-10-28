package servostar.fleet.transfer;

import java.util.*;
import java.nio.file.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** In-memory Fleet, no live network. Business-state checks, not mirrored setters. */
public final class CargoTest {
    static int checks;
    static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    static class Harness extends NativeHostTest.Harness {
        final Map<String,Map<String,Object>> containers=new LinkedHashMap<>();
        int ins,outs;boolean lose,reject,active,queryFailure,verificationFailure;
        Map<String,Object> last,lastMission;
        Harness(Path root)throws Exception {super(root,null);containers.put("SIM_CARGO_001",row("SIM_CARGO_001","PORT_89",1));}
        static Map<String,Object> row(String id,String port,int inMap){return TransferService.map("containerCode",id,"nodeCode",WorkPorts.containerNode(port),"containerModelCode","SIM_TRAY_001","inMapStatus",inMap,"isCarry",0);}
        @Override Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if(!TOKEN.equals(token))throw new SecurityException();
            if(path.equals("submitMission")){lastMission=body;return super.post(path,token,body);}
            if(path.equals("jobQuery") && !body.containsKey("status") && lastMission!=null){
                String dest=(String)((Map<?,?>)((List<?>)lastMission.get("missionData")).get(1)).get("position");
                return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("jobCode",lastMission.get("missionCode"),"workflowId",6837,"robotId","1","containerCode",lastMission.get("containerCode"),"status",30,"completeTime","observed-time","finalNodeCode",dest,"targetCellCode",dest)));
            }
            if(path.equals("robotQuery") && lastMission!=null){
                String dest=(String)((Map<?,?>)((List<?>)lastMission.get("missionData")).get(1)).get("position");
                return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("robotId","1","status",3,"occupyStatus",0,"containerCode","","nodeCode",dest)));
            }
            if(path.equals("containerQuery") || path.equals("containerQueryAll")){
                if(queryFailure || verificationFailure && ins+outs>0)throw new java.io.IOException("Mock query unavailable");
                return TransferService.map("success",true,"code","0","data",path.equals("containerQueryAll")?new ArrayList<>(containers.values()):containers.values().stream().filter(x->"1".equals(CargoService.str(x,"inMapStatus"))).collect(java.util.stream.Collectors.toList()));
            }
            if(path.equals("jobQuery") && body.containsKey("status"))return TransferService.map("success",true,"code","0","data",active?Arrays.asList(TransferService.map("containerCode","EXTERNAL","targetCellCode","lay01-dist1-84","status",20)):Collections.emptyList());
            if(path.equals("containerIn") || path.equals("containerOut")){
                last=body;
                if(path.equals("containerIn"))ins++;else outs++;
                if(lose)throw new java.io.IOException("Mock lost mutation reply");
                if(reject)return TransferService.map("success",false,"code","MOCK_BUSY");
                String id=(String)body.get("containerCode");
                if(path.equals("containerIn")){
                    Map<String,Object> row=row(id,"PORT_84",1);row.put("nodeCode",body.get("position"));row.put("containerModelCode",body.get("containerModelCode"));containers.put(id,row);
                }else if(Boolean.TRUE.equals(body.get("isDelete")))containers.remove(id);
                else containers.get(id).put("inMapStatus",0);
                return TransferService.map("success",true,"code","0");
            }
            return super.post(path,token,body);
        }
        void run(String cmd,Map<String,String> p)throws Exception {check(NativeHostTest.ack(engine.cargo(cmd,p))==4,"Command response "+cmd);}
        String outcome(String id){return state.data.cargoOperations.get(id).phase;}
    }
    static Map<String,String> p(String id,String carrier,String port){Map<String,String> p=new LinkedHashMap<>();p.put("REQUESTID",id);p.put("CARRIERID",carrier);p.put("PORT",port);p.put("MODEL","SIM_TRAY_001");p.put("SCANNEDID",carrier);p.put("SCANOK","1");p.put("DELETE","0");return p;}
    static final String TOKEN=NativeHostTest.TOKEN;
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Harness h=new Harness(root);
        Map<String,String> p=p("IN_2","SIM_CARGO_002","PORT_84");h.run("CARRIER_IN",p);
        check(h.ins==1 && Boolean.TRUE.equals(h.last.get("isNew")) && h.outcome("IN_2").equals("CONFIRMED"),"Create + insert unknown scanned carrier");
        check("lay01-dist1-84".equals(h.last.get("position")),"Canonical map node sent to Fleet, not PORT name");
        check(NativeHostTest.ack(h.engine.cargo("CARRIER_IN",p))==5 && h.ins==1,"Same request ID never repeats mutation");
        h.run("CARRIER_IN",p("IN_ONMAP","SIM_CARGO_002","PORT_90"));check(h.outcome("IN_ONMAP").equals("REJECTED") && h.ins==1,"Already on-map carrier cannot be teleported");
        h.run("CARRIER_IN",p("OCCUPIED","NEW","PORT_84"));check(h.ins==1 && h.outcome("OCCUPIED").equals("REJECTED"),"Occupied port rejected");
        p=p("MISMATCH","NEW","PORT_90");p.put("SCANNEDID","DIFFERENT");h.run("CARRIER_IN",p);check(h.ins==1,"Mismatch never registers expected cargo");
        p=p("UNREAD","NEW","PORT_90");p.put("SCANOK","0");h.run("CARRIER_IN",p);check(h.ins==1,"Failed reader never inserts");
        p=p("OUT_2","SIM_CARGO_002","PORT_84");h.run("CARRIER_OUT",p);
        check(h.outs==1 && !Boolean.TRUE.equals(h.last.get("isDelete")) && h.outcome("OUT_2").equals("CONFIRMED"),"Default out retains off-map registry");
        h.run("CARRIER_IN",p("REENTER","SIM_CARGO_002","PORT_90"));check(Boolean.FALSE.equals(h.last.get("isNew")),"Existing off-map carrier re-enters without recreation");
        h.containers.get("SIM_CARGO_002").put("isCarry",1);h.run("CARRIER_OUT",p("CARRIED","SIM_CARGO_002","PORT_90"));check(h.outs==1,"Loaded cargo cannot be removed");h.containers.get("SIM_CARGO_002").put("isCarry",0);
        p=p("DELETE_2","SIM_CARGO_002","PORT_90");p.put("DELETE","1");h.run("CARRIER_OUT",p);check(!h.containers.containsKey("SIM_CARGO_002"),"Explicit deletion confirmed absent");
        h.active=true;h.run("CARRIER_IN",p("EXTERNAL","SIM_CARGO_003","PORT_84"));check(h.outcome("EXTERNAL").equals("REJECTED"),"External ECS/manual work guards port");h.active=false;
        h.reject=true;h.run("CARRIER_IN",p("API_REJECT","X","PORT_90"));check(h.outcome("API_REJECT").equals("REJECTED"),"Explicit API rejection is not uncertainty");h.reject=false;
        Harness lost=new Harness(root);lost.lose=true;lost.run("CARRIER_IN",p("LOST","X","PORT_84"));
        check(lost.outcome("LOST").equals("UNCERTAIN") && lost.ins==1,"Lost reply is durable uncertainty");
        lost.run("CARRIER_IN",p("NEW_ID","X","PORT_84"));check(lost.ins==1,"New ID cannot bypass uncertain carrier/port");
        Harness verify=new Harness(root);verify.verificationFailure=true;verify.run("CARRIER_IN",p("VERIFY","X","PORT_84"));check(verify.outcome("VERIFY").equals("UNCERTAIN"),"Post-mutation query failure never reports rejection/success");
        Map<String,String> review=new LinkedHashMap<>();review.put("REQUESTID","REVIEW");review.put("TARGETID","VERIFY");
        verify.verificationFailure=false;verify.run("CARRIER_VERIFY",review);
        check(verify.outcome("VERIFY").equals("CONFIRMED") && verify.ins==1,"Manual review confirms current state without replay");
        check(verify.state.data.reports.stream().anyMatch(x->x.command.equals("VERIFY") && x.outcome.equals("CONFIRMED")),"Reviewed original result is reportable to PC");
        review.put("REQUESTID","NOT_APPLIED");review.put("TARGETID","LOST");lost.run("CARRIER_VERIFY",review);
        check(lost.outcome("LOST").equals("UNCERTAIN") && lost.ins==1,"Review cannot force an unconfirmed state");
        Harness down=new Harness(root);down.queryFailure=true;down.run("CARRIER_IN",p("DOWN","X","PORT_84"));check(down.ins==0 && down.outcome("DOWN").equals("REJECTED"),"Preflight query failure never mutates");
        Harness transfer=new Harness(root);Map<String,Object> req=TransferTest.request("OTHER_CARGO");req.put("CARRIERID","SIM_CARGO_002");req.put("SOURCEPORT","PORT_84");req.put("DESTPORT","PORT_86");
        transfer.containers.put("SIM_CARGO_002",Harness.row("SIM_CARGO_002","PORT_84",1));
        transfer.containers.get("SIM_CARGO_002").put("containerModelCode","ANOTHER_LIFT_TRAY");
        Map<?,?> preview=(Map<?,?>)transfer.service.preview(req,TOKEN).get("data");check(preview.get("containerModelCode").equals("ANOTHER_LIFT_TRAY"),"Preview uses actual registered model, not hardcoded model");
        check(NativeHostTest.ack(transfer.engine.transfer(req))==4 && transfer.submits==1,"Second carrier can be dispatched");
        transfer.run("CARRIER_OUT",p("ACTIVE","SIM_CARGO_002","PORT_84"));check(transfer.outs==0,"Owned active job blocks inventory mutation");
        for(Map<String,Object> original:transfer.trace){
            Map<String,Object> e=new LinkedHashMap<>(original);e.put("commandId","OTHER_CARGO");e.put("carrierId","SIM_CARGO_002");
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation")){
                if("lay01-dist1-89".equals(e.get(key)))e.put(key,"lay01-dist1-84");
                else if("lay01-dist1-97".equals(e.get(key)))e.put(key,"lay01-dist1-86");
            }
            transfer.observe(e);
        }
        transfer.containers.get("SIM_CARGO_002").put("nodeCode","lay01-dist1-86");
        for(int i=0;i<14;i++)transfer.engine.tick();
        check(transfer.state.data.jobs.get("OTHER_CARGO").phase.equals("COMPLETED") && transfer.count(207)==1,"Second carrier observed lifecycle reaches verified completion");
        HostState.Delivery finished=transfer.state.data.reports.stream().filter(x->x.ceid==207).findFirst().get();
        check(transfer.engine.reportBody(finished).toString().contains("SIM_CARGO_002"),"Final SECS report uses actual second carrier");
        Harness bad=new Harness(root);check(NativeHostTest.ack(bad.engine.transfer(req))==2 && bad.submits==0,"Unregistered transfer does not create cargo");
        bad.containers.put("SIM_CARGO_002",Harness.row("SIM_CARGO_002","PORT_94",1));req.put("COMMANDID","WRONG_PORT");check(NativeHostTest.ack(bad.engine.transfer(req))==2 && bad.submits==0,"Wrong source never submits");
        for(int expected=0;expected<4;expected++) {
            Map<String,String> scan=p("SCAN_"+expected,"SIM_CARGO_001",expected==2?"PORT_97":"PORT_89");
            if(expected==1)scan.put("SCANOK","0");if(expected==3)scan.put("SCANNEDID","OTHER");h.run("CARRIER_SCAN_TEST",scan);
            HostState.Delivery d=h.state.data.reports.stream().filter(x->x.kind.equals("ID_READ_TEST") && x.command.equals(scan.get("REQUESTID"))).findFirst().get();
            Secs2 wire=h.engine.reportBody(d);check(wire.get(2).get(0).get(0).getInt(0)==14 && wire.get(2).get(0).get(1).size()==3 && wire.get(2).get(0).get(1).get(2).getInt(0)==expected,"406 RPT14 L3 status "+expected);
        }
        Map<String,String> query=Collections.singletonMap("REQUESTID","QUERY");h.run("CARRIER_QUERY",query);
        HostState.Delivery snapshot=h.state.data.reports.stream().filter(x->x.kind.equals("INVENTORY")).findFirst().get();
        check(h.engine.reportBody(snapshot).get(2).get(0).get(1).get(1).size()==h.containers.size(),"Inventory report includes real returned containers");
        Map<String,Secs2> pairs=new LinkedHashMap<>();pairs.put("REQUESTID",ascii("PARSE"));pairs.put("CARRIERID",ascii("NEW"));pairs.put("PORT",ascii("PORT_84"));pairs.put("MODEL",ascii("SIM_TRAY_001"));pairs.put("SCANNEDID",ascii("NEW"));pairs.put("SCANOK",uint2(1));
        check(HostProtocol.cargoParameters("CARRIER_IN",pairs).get("SCANOK").equals("1"),"Typed parameters parse");
        pairs.put("SCANOK",ascii("1"));try{HostProtocol.cargoParameters("CARRIER_IN",pairs);throw new AssertionError("Wrong scalar accepted");}catch(HostProtocol.ParameterError ex){check(ex.code==3,"Wrong scan scalar rejected");}
        Path disk=Files.createTempDirectory("cargo-restart-").resolve("state.json");HostState state=new HostState(disk);
        HostState.CargoOperation op=new HostState.CargoOperation();op.command="CARRIER_IN";op.request=p("RESTART","X","PORT_84");op.phase="MUTATING";state.data.cargoOperations.put("RESTART",op);state.save();
        HostState recovered=new HostState(disk);check(recovered.data.cargoOperations.get("RESTART").phase.equals("UNCERTAIN") && recovered.pending().outcome.equals("UNCERTAIN"),"Restart records uncertain mutation and queues outcome without replay");
        System.out.println("PASS: "+checks+" inventory/scan/multi-carrier/no-replay checks; mock Fleet only.");
    }
}
