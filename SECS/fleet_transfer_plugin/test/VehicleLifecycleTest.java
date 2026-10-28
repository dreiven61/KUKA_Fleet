package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.rcs.bd.grpc.robot.MobileRobotStatus;
import com.kuka.rcs.bd.grpc.robot.RobotOccupyStatus;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Captured callback replay and query fixtures only. No live calls or actions. */
public final class VehicleLifecycleTest {
    private static int checks;
    static void check(boolean valid,String name) {checks++;if(!valid)throw new AssertionError(name);}
    static boolean ready(TransferJournal j,Map<String,Object> r,String stage) {return Boolean.TRUE.equals(j.stage(r,stage).get("success"));}
    static Map<String,Object> request() {return TransferService.map("COMMANDID","TR_TEST_001","PRIORITY",1,"REPLACE",0,
        "CARRIERID","SIM_CARGO_001","SOURCEPORT","PORT_89","DESTPORT","PORT_97");}
    static TransferJournal newJournal(Map<String,Object> req) throws Exception {
        TransferJournal j=new TransferJournal(null);j.streamReady();j.reserve(req);j.accepted("TR_TEST_001");return j;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);
        Map<String,Object> req=request(),norm=TransferService.normalize(req);
        List<Map<String,Object>> trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        check(MobileRobotStatus.IDLE.getNumber()==3 && RobotOccupyStatus.UNOCCUPIED_ROBOT.getNumber()==0,"SDK confirms idle/unoccupied numeric profile");
        TransferJournal journal=newJournal(norm);
        for(String stage:Arrays.asList("assigned","arrived-source","departed-source","arrived-dest","unassigned"))
            check(!ready(journal,norm,stage),"API acceptance is not "+stage);
        for(int i=0;i<trace.size();i++) {
            journal.observe(trace.get(i));
            check(ready(journal,norm,"assigned"),"604 after first correlated move, callback "+i);
            check(ready(journal,norm,"arrived-source")== (i>=1),"601 source only after MOVE_END "+i);
            check(ready(journal,norm,"departed-source")== (i>=6),"605 only after loaded MOVE_BEGIN "+i);
            check(ready(journal,norm,"arrived-dest")== (i>=7),"601 destination only after MOVE_END "+i);
            check(ready(journal,norm,"unassigned")== (i==12),"610 requires terminal candidate "+i);
        }
        for(String bad:Arrays.asList("robot","cargo","failure","nextNode","sequence","missingAssigned","missingSourceArrival","missingDeparture","missingDestArrival","gap","cancel")) {
            TransferJournal invalid=newJournal(norm);
            for(int i=0;i<trace.size();i++) {
                Map<String,Object> e=new LinkedHashMap<>(trace.get(i));
                if(bad.equals("robot") && i>0)e.put("robotId","2");
                if(bad.equals("cargo"))e.put("carrierId","OTHER");
                if(bad.equals("failure"))e.put("success",false);
                if(bad.equals("nextNode") && "MOVE_BEGIN".equals(e.get("status")))e.put("nextNode","lay01-dist1-999");
                if(bad.equals("sequence"))e.put("sequence",9);
                if(bad.equals("missingAssigned") && i==0 || bad.equals("missingSourceArrival") && i==1
                    || bad.equals("missingDeparture") && i==6 || bad.equals("missingDestArrival") && i==7)continue;
                if(bad.equals("gap") && i==6)invalid.streamLost();
                if(bad.equals("cancel") && i==12)e.put("status","CANCEL_COMPLETED");
                invalid.observe(e);
            }
            check(!ready(invalid,norm,"unassigned"),"No optimistic 610: "+bad);
        }
        ObjectMapper json=new ObjectMapper();
        Map<String,Object> jobs=json.readValue(root.resolve("test/fixtures/TR_TEST_001_jobQuery.json").toFile(),Map.class);
        Map<String,Object> cargo=json.readValue(root.resolve("test/fixtures/TR_TEST_001_containerQuery.json").toFile(),Map.class);
        Map<String,Object> robot=TransferService.map("robotId","1","status",3,"occupyStatus",0,"containerCode","","nodeCode","lay01-dist1-97");
        List<Map<String,Object>> rows=new ArrayList<>();rows.add(robot);
        Map<String,Object> robots=TransferService.map("success",true,"code","0","data",rows);
        AtomicInteger mutations=new AtomicInteger();
        TransferService service=new TransferService((path,token,body)->{
            if(!"test-token".equals(token))return TransferService.map("success",false,"code","401");
            if(path.equals("robotQuery"))return robots;
            if(path.equals("jobQuery"))return jobs;
            if(path.equals("containerQuery"))return cargo;
            mutations.incrementAndGet();throw new AssertionError("Lifecycle gates cannot mutate Fleet");
        },journal);
        check(Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"610 requires completed job, unloaded destination cargo and actual free vehicle");
        for(Object[] change:new Object[][]{{"status",4},{"occupyStatus",1},{"containerCode","SIM_CARGO_001"},{"nodeCode","lay01-dist1-89"},{"robotId","2"}}) {
            Object original=robot.put((String)change[0],change[1]);
            check(!Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"610 blocks vehicle "+change[0]);
            robot.put((String)change[0],original);
        }
        rows.add(new LinkedHashMap<>(robot));
        check(!Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"Duplicate robot rows block release");rows.remove(1);
        Map<String,Object> job=(Map<String,Object>)((List<?>)jobs.get("data")).get(0);
        job.put("status",20);check(!Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"Idle vehicle alone cannot release incomplete command");job.put("status",30);
        Map<String,Object> c=(Map<String,Object>)((List<?>)cargo.get("data")).get(0);
        c.put("isCarry",1);check(!Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"Cargo still carried blocks release");c.put("isCarry",0);
        TransferController controller=new TransferController(service);
        check(controller.assigned(null,req).getStatusCodeValue()==401,"Assigned auth");
        check(controller.arrivedSource(null,req).getStatusCodeValue()==401,"Source arrival auth");
        check(controller.departedSource(null,req).getStatusCodeValue()==401,"Departure auth");
        check(controller.arrivedDest(null,req).getStatusCodeValue()==401,"Dest arrival auth");
        check(controller.unassigned(null,req).getStatusCodeValue()==401,"Release auth");
        check(mutations.get()==0,"All five gates are read only");
        // Opposite direction must use the request's ports, not hard-coded 89 -> 97.
        Map<String,Object> reverse=new LinkedHashMap<>(norm);reverse.put("SOURCEPORT","PORT_97");reverse.put("DESTPORT","PORT_89");
        TransferJournal backward=newJournal(reverse);
        for(Map<String,Object> original:trace) {
            Map<String,Object> e=new LinkedHashMap<>(original);
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation")) if(e.get(key) instanceof String)
                e.put(key,((String)e.get(key)).replace("-89","-TEMP").replace("-97","-89").replace("-TEMP","-97"));
            backward.observe(e);
        }
        check(ready(backward,reverse,"arrived-source") && ready(backward,reverse,"arrived-dest") && ready(backward,reverse,"unassigned"),"Reverse route callbacks preserve all vehicle stages");
        System.out.println("PASS: "+checks+" vehicle lifecycle checks; actual callback replay and mock queries only.");
    }
}
