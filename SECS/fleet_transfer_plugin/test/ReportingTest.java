package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.TextFormat;
import com.kuka.wcs.grpc.mission.WcsMissionStatusResponse;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Replay captured WCS events through the same production protobuf adapter. No server connection. */
public final class ReportingTest {
    private static int checks;
    private static void check(boolean pass,String name) {checks++;if(!pass)throw new AssertionError(name);}
    private static Map<String,Object> request() {
        return TransferService.map("COMMANDID","TR_TEST_001","PRIORITY",1,"REPLACE",0,"CARRIERID","SIM_CARGO_001","SOURCEPORT","PORT_89","DESTPORT","PORT_97");
    }
    static List<Map<String,Object>> events(Path log) throws Exception {
        String text=new String(Files.readAllBytes(log),StandardCharsets.UTF_8).replace("\r","");
        List<Map<String,Object>> result=new ArrayList<>();
        for(String block:text.split("(?m)^2026-10-27 ")) {
            int start=block.indexOf("missionStatusResponse onNext:");
            if(start<0)continue;
            String body=block.substring(start+"missionStatusResponse onNext:".length()).trim();
            WcsMissionStatusResponse.Builder builder=WcsMissionStatusResponse.newBuilder();
            TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build().merge(body,builder);
            result.add(WcsStatusMonitor.event(builder.build()));
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);
        List<Map<String,Object>> trace=events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        check(trace.size()==13,"13 captured callbacks decoded including repeated action starts");
        Map<String,Object> req=request(), normalized=TransferService.normalize(req);
        TransferJournal journal=new TransferJournal(null);
        try {journal.reserve(normalized);throw new AssertionError("Disconnected dispatch allowed");}catch(IllegalStateException expected){checks++;}
        journal.streamReady();journal.reserve(normalized);
        check(!Boolean.TRUE.equals(journal.state(normalized,false).get("success")),"Acceptance is not pickup");
        journal.accepted("TR_TEST_001");
        int starts=0;
        boolean pickupEnded=false,dropBegun=false;
        for(Map<String,Object> e:trace) {
            journal.observe(e);
            boolean begun="ACTION_BEGIN".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType"));
            if(begun)starts++;
            if("ACTION_END".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))pickupEnded=true;
            if("ACTION_BEGIN".equals(e.get("status")) && "DOWN_CONTAINER".equals(e.get("actionType")))dropBegun=true;
            check(Boolean.TRUE.equals(journal.stage(normalized,"pickup-completed").get("success"))==pickupEnded,"603 gate follows captured UP end only");
            check(Boolean.TRUE.equals(journal.stage(normalized,"drop-started").get("success"))==dropBegun,"606 gate follows captured DOWN start only");
            if(starts==0)check(!Boolean.TRUE.equals(journal.state(normalized,false).get("success")),"No early211");
            if(!"TASK_COMPLETED".equals(e.get("status")))check(!Boolean.TRUE.equals(journal.state(normalized,true).get("success")),"No early207");
        }
        check(Boolean.TRUE.equals(journal.state(normalized,false).get("success")),"Observed pickup enables211");
        check(Boolean.TRUE.equals(journal.state(normalized,true).get("success")),"Observed terminal and drop enable completion candidate");
        check("6837".equals(journal.state(normalized,true).get("instanceId")),"Instance correlation");
        journal.observe(trace.get(trace.size()-1));
        check(Boolean.TRUE.equals(journal.state(normalized,true).get("success")),"Duplicate terminal idempotent");
        Map<String,Object> wrong=new LinkedHashMap<>(normalized);wrong.put("DESTPORT","PORT_89");
        check("COMMAND_CONTEXT_MISMATCH".equals(journal.state(wrong,true).get("code")),"ID cannot be reused with different ports");

        ObjectMapper json=new ObjectMapper();
        Map<String,Object> job=json.readValue(root.resolve("test/fixtures/TR_TEST_001_jobQuery.json").toFile(),Map.class);
        Map<String,Object> cargo=json.readValue(root.resolve("test/fixtures/TR_TEST_001_containerQuery.json").toFile(),Map.class);
        AtomicInteger submits=new AtomicInteger();
        TransferService service=new TransferService((path,token,body)->{
            if(path.equals("robotQuery"))return TransferService.map("success",true,"code","0");
            if(path.equals("jobQuery"))return job;
            if(path.equals("containerQuery"))return cargo;
            submits.incrementAndGet();throw new AssertionError("Gate must never submit");
        },journal);
        check(Boolean.TRUE.equals(service.gate(req,"test-token",true).get("success")),"Recorded terminal plus real query fixtures pass207");
        Map<String,Object> c=(Map<String,Object>)((List<?>)cargo.get("data")).get(0);
        c.put("isCarry",1);
        check(!Boolean.TRUE.equals(service.gate(req,"test-token",true).get("success")),"Still loaded blocks207"); c.put("isCarry",0);
        Map<String,Object> j=(Map<String,Object>)((List<?>)job.get("data")).get(0);
        j.put("status",20);check(!Boolean.TRUE.equals(service.gate(req,"test-token",true).get("success")),"Running job blocks207");j.put("status",30);
        j.put("workflowId",999);check(!Boolean.TRUE.equals(service.gate(req,"test-token",true).get("success")),"Different job instance blocks207");j.put("workflowId",6837);
        j.put("completeTime",null);check(!Boolean.TRUE.equals(service.gate(req,"test-token",true).get("success")),"Missing completion time blocks207");
        check(submits.get()==0,"All gates read only");
        check(Boolean.TRUE.equals(service.stage(req,"test-token","pickup-completed").get("success")),"603 read-only service route");
        check(Boolean.TRUE.equals(service.stage(req,"test-token","drop-started").get("success")),"606 read-only service route");
        TransferController controller=new TransferController(service);
        check(controller.pickupCompleted(null,req).getStatusCodeValue()==401,"603 requires authentication");
        check(controller.dropStarted(null,req).getStatusCodeValue()==401,"606 requires authentication");
        for(String stage:Arrays.asList("pickup-completed","drop-started")) {
            for(String invalid:Arrays.asList("wrongRobot","wrongContainer","wrongSequence","failedAction","missingPickup","gap")) {
                TransferJournal bad=new TransferJournal(null);bad.streamReady();bad.reserve(normalized);bad.accepted("TR_TEST_001");
                if(invalid.equals("gap"))bad.streamLost();
                for(Map<String,Object> original:trace) {
                    Map<String,Object> e=new LinkedHashMap<>(original);
                    if(invalid.equals("missingPickup") && "ACTION_BEGIN".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))continue;
                    if(invalid.equals("wrongRobot") && !"MOVE_BEGIN".equals(e.get("status")))e.put("robotId","2");
                    if(invalid.equals("wrongContainer"))e.put("carrierId","OTHER");
                    if(invalid.equals("wrongSequence"))e.put("sequence",3);
                    if(invalid.equals("failedAction"))e.put("success",false);
                    bad.observe(e);
                }
                check(!Boolean.TRUE.equals(bad.stage(normalized,stage).get("success")),stage+" blocks "+invalid);
            }
        }

        for(String invalid:new String[]{"gap","wrongRobot","wrongContainer","wrongInstance","canceled","missingPickup","missingDrop","manualComplete","failedResult","wrongFinalLocation"}) {
            TransferJournal bad=new TransferJournal(null);bad.streamReady();bad.reserve(normalized);bad.accepted("TR_TEST_001");
            for(Map<String,Object> original:trace) {
                Map<String,Object> e=new LinkedHashMap<>(original);
                if(invalid.equals("wrongRobot") && !"MOVE_BEGIN".equals(e.get("status")))e.put("robotId","2");
                if(invalid.equals("wrongContainer"))e.put("carrierId","SIM_CARGO_002");
                if(invalid.equals("missingPickup") && "ACTION_BEGIN".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))continue;
                if(invalid.equals("missingDrop") && "ACTION_END".equals(e.get("status")) && "DOWN_CONTAINER".equals(e.get("actionType")))continue;
                if("TASK_COMPLETED".equals(e.get("status"))) {
                    if(invalid.equals("gap"))bad.streamLost();
                    if(invalid.equals("wrongInstance"))e.put("instanceId","OTHER");
                    if(invalid.equals("canceled"))e.put("status","CANCEL_COMPLETED");
                    if(invalid.equals("manualComplete"))e.put("manualCompleteFlag",1);
                    if(invalid.equals("failedResult"))e.put("resultCode","ERROR");
                    if(invalid.equals("wrongFinalLocation"))e.put("carrierLocation","lay01-dist1-89");
                }
                bad.observe(e);
            }
            check(!Boolean.TRUE.equals(bad.state(normalized,true).get("success")),"Invalid completion blocked: "+invalid);
        }
        Path disk=Files.createTempFile("servostar-report-test-",".jsonl");
        try {
            TransferJournal before=new TransferJournal(disk);before.streamReady();before.reserve(normalized);before.accepted("TR_TEST_001");before.observe(trace.get(2));
            TransferJournal after=new TransferJournal(disk);after.streamReady();
            check("STATUS_UNCERTAIN".equals(after.state(normalized,false).get("code")),"Restart blocks incomplete history");
            try {after.reserve(normalized);throw new AssertionError("Restart replay allowed");}catch(IllegalArgumentException expected){checks++;}
            String stored=new String(Files.readAllBytes(disk),StandardCharsets.UTF_8);
            check(!stored.contains("test-token") && !stored.contains("Authorization"),"Journal contains no credentials");
            Files.write(disk,"BROKEN".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
            try {new TransferJournal(disk);throw new AssertionError("Torn journal accepted");}catch(java.io.IOException expected){checks++;}
        } finally {Files.deleteIfExists(disk);}
        System.out.println("PASS: "+checks+" reporting checks; captured WCS replay, final-state fixtures, duplicates, gaps, cancellation and restart. No live calls.");
    }
}
