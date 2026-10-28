package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Replay captured WCS transitions with all 90 distinct workstation pairs.
 * Queries are mock fixtures only; this does not prove a live route is available. */
public final class WorkPortsTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]); ObjectMapper json=new ObjectMapper();
        List<Map<String,Object>> trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        check(WorkPorts.ALL.size()==10,"Ten workstations");
        int routes=0;
        for(String source:WorkPorts.ALL)for(String dest:WorkPorts.ALL) {
            Map<String,Object> req=VehicleLifecycleTest.request();req.put("SOURCEPORT",source);req.put("DESTPORT",dest);
            if(source.equals(dest)){TransferTest.rejects(req);continue;}
            routes++;
            Map<String,Object> mission=TransferService.buildMission(req);
            List<?> steps=(List<?>)mission.get("missionData");
            check(WorkPorts.node(source).equals(((Map<?,?>)steps.get(0)).get("position")),"Pick route "+source);
            check(WorkPorts.node(dest).equals(((Map<?,?>)steps.get(1)).get("position")),"Drop route "+dest);
            TransferJournal journal=VehicleLifecycleTest.newJournal(TransferService.normalize(req));
            check(!Boolean.TRUE.equals(journal.stage(req,"completed").get("success")),"No early completion");
            for(Map<String,Object> original:trace){
                Map<String,Object> event=new LinkedHashMap<>(original);
                for(String key:Arrays.asList("currentNode","nextNode","carrierLocation")) {
                    Object v=event.get(key);
                    if("lay01-dist1-89".equals(v))event.put(key,WorkPorts.node(source));
                    else if("lay01-dist1-97".equals(v))event.put(key,WorkPorts.node(dest));
                }
                journal.observe(event);
            }
            Map<String,Object> jobs=json.readValue(root.resolve("test/fixtures/TR_TEST_001_jobQuery.json").toFile(),Map.class);
            Map<String,Object> cargo=json.readValue(root.resolve("test/fixtures/TR_TEST_001_containerQuery.json").toFile(),Map.class);
            Map<String,Object> job=(Map<String,Object>)((List<?>)jobs.get("data")).get(0);
            job.put("finalNodeCode",WorkPorts.node(dest));job.put("targetCellCode",WorkPorts.node(dest));
            Map<String,Object> c=(Map<String,Object>)((List<?>)cargo.get("data")).get(0);
            c.put("nodeCode",WorkPorts.containerNode(dest));
            Map<String,Object> robot=TransferService.map("robotId","1","status",3,"occupyStatus",0,"containerCode","","nodeCode",WorkPorts.node(dest));
            TransferService service=new TransferService((path,token,body)->{
                if(path.equals("robotQuery"))return TransferService.map("success",true,"code","0","data",Arrays.asList(robot));
                if(path.equals("jobQuery"))return jobs;
                if(path.equals("containerQuery"))return cargo;
                throw new AssertionError("Read-only gates must not submit");
            },journal);
            for(String stage:Arrays.asList("assigned","arrived-source","pickup-started","pickup-completed","departed-source","arrived-dest","drop-started","completed","unassigned"))
                check(Boolean.TRUE.equals(service.stage(req,"test-token",stage).get("success")),source+" -> "+dest+" "+stage);
            c.put("nodeCode",WorkPorts.containerNode(source));
            check(!Boolean.TRUE.equals(service.stage(req,"test-token","completed").get("success")),"Wrong cargo port cannot complete");
        }
        check(routes==90,"All distinct work-port pairs covered");
        for(String invalid:Arrays.asList("PORT_87","PORT_88","PORT_99","PORT_100","PORT_62","PORT_999","PORT_084","")) {
            Map<String,Object> req=VehicleLifecycleTest.request();req.put("SOURCEPORT",invalid);TransferTest.rejects(req);
            req=VehicleLifecycleTest.request();req.put("DESTPORT",invalid);TransferTest.rejects(req);
        }
        System.out.println("PASS: "+checks+" work-port checks across 90 routes; callback replay and mock final-state checks, no live actions.");
    }
}
