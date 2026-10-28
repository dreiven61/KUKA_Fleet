package servostar.fleet.transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
/** Automatic Fleet dispatch and actual vehicle correlation; fixtures only. */
public final class DynamicDispatchTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);ObjectMapper json=new ObjectMapper();
        Map<String,Object> req=VehicleLifecycleTest.request(),norm=TransferService.normalize(req);
        List<Map<String,Object>> trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        check(((List<?>)TransferService.buildMission(req).get("robotIds")).equals(Arrays.asList("1","2","3")),"Three eligible robots, not a single pinned vehicle");
        for(String robot:Arrays.asList("1","2","3")) {
            TransferJournal journal=VehicleLifecycleTest.newJournal(norm);
            check("".equals(journal.stage(norm,"assigned").get("robotId")),"No invented assignment before callback");
            for(Map<String,Object> original:trace) {Map<String,Object> e=new LinkedHashMap<>(original);e.put("robotId",robot);journal.observe(e);}
            for(String stage:Arrays.asList("assigned","arrived-source","pickup-started","pickup-completed","departed-source","arrived-dest","drop-started","completed","unassigned")) {
                Map<String,Object> value=journal.stage(norm,stage);
                check(Boolean.TRUE.equals(value.get("success")) && robot.equals(value.get("robotId")),"Actual vehicle in every stage: "+robot+" "+stage);
            }
            Map<String,Object> jobs=json.readValue(root.resolve("test/fixtures/TR_TEST_001_jobQuery.json").toFile(),Map.class);
            Map<String,Object> cargo=json.readValue(root.resolve("test/fixtures/TR_TEST_001_containerQuery.json").toFile(),Map.class);
            Map<String,Object> job=(Map<String,Object>)((List<?>)jobs.get("data")).get(0);job.put("robotId",robot);
            Map<String,Object> vehicle=TransferService.map("robotId",robot,"status",3,"occupyStatus",0,"containerCode","","nodeCode","lay01-dist1-97");
            List<Map<String,Object>> seen=new ArrayList<>();
            TransferService service=new TransferService((path,token,body)->{
                if(path.equals("jobQuery"))return jobs;
                if(path.equals("containerQuery"))return cargo;
                if(path.equals("robotQuery")){seen.add(body);return TransferService.map("success",true,"code","0","data",Arrays.asList(vehicle));}
                throw new AssertionError("No actions allowed");
            },journal);
            check(Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"Actual vehicle final/release queries: "+robot);
            check(seen.get(0).isEmpty() && robot.equals(seen.get(1).get("robotId")),"Auth is not pinned; release queries actual robot");
            job.put("robotId","99");check(!Boolean.TRUE.equals(service.stage(req,"test-token","completed").get("success")),"Mismatched final job vehicle rejected");job.put("robotId",robot);
            vehicle.put("robotId","99");check(!Boolean.TRUE.equals(service.stage(req,"test-token","unassigned").get("success")),"Other idle robot cannot prove release");
            Path disk=Files.createTempFile("dynamic-vehicle-",".jsonl");
            try {
                TransferJournal saved=new TransferJournal(disk);saved.streamReady();saved.reserve(norm);saved.accepted("TR_TEST_001");
                for(Map<String,Object> original:trace){Map<String,Object> e=new LinkedHashMap<>(original);e.put("robotId",robot);saved.observe(e);}
                check(robot.equals(new TransferJournal(disk).stage(norm,"completed").get("robotId")),"Replayed vehicle identity retained");
            }finally{Files.deleteIfExists(disk);}
        }
        TransferJournal mixed=VehicleLifecycleTest.newJournal(norm);
        for(int i=0;i<trace.size();i++){Map<String,Object> e=new LinkedHashMap<>(trace.get(i));e.put("robotId",i==0?"2":"3");mixed.observe(e);}
        check("STATUS_UNCERTAIN".equals(mixed.stage(norm,"completed").get("code")),"Vehicle changes require review, never combine callbacks");
        System.out.println("PASS: "+checks+" automatic dispatch checks; robots 1/2/3, correlation, release and restart. No live calls.");
    }
}
