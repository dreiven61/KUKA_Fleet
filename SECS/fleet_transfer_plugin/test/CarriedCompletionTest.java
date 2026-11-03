package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Replays actual WCS job6894, captured by the operator. Never sends Fleet operations. */
public final class CarriedCompletionTest {
    static final String ID="4BC594E5FF7F4BCBAADC8E4FBB2389A1", ORIGINAL="053D6C1EA6694DA9B3A98E179A9A5830";
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class Snapshot implements TransferService.FleetClient {
        int mutations;String cargoNode="SECS_ST_89",status="30",robotCargo="",binding="";
        public Map<String,Object> post(String path,String token,Map<String,Object> body){
            Object data;
            if(path.equals("jobQuery"))data=body.containsKey("status")?Collections.emptyList():Arrays.asList(TransferService.map(
                "jobCode",ID,"workflowId",6894,"containerCode","SIM_CARGO_001","robotId","2","status",status,
                "targetCellCode","lay01-dist1-89","beginCellCode","lay01-dist1-105","finalNodeCode","lay01-dist1-89",
                "warnFlag",0,"completeTime","2026-10-24 12:31:57"));
            else if(path.equals("containerQuery") || path.equals("containerQueryAll"))data=Arrays.asList(TransferService.map(
                "containerCode","SIM_CARGO_001","nodeCode",cargoNode,"isCarry",0,"inMapStatus",1,"containerModelCode","SIM_TRAY_001"));
            else if(path.equals("robotQuery"))data=Arrays.asList(TransferService.map("robotId","2","status",3,"occupyStatus",0,
                "containerCode",robotCargo,"liftStatus",robotCargo.isEmpty()?0:1,"nodeCode","lay01-dist1-101","missionCode",binding));
            else {mutations++;throw new AssertionError("Unexpected robot operation: "+path);}
            return TransferService.map("success",true,"code","0","data",data);
        }
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> read(Path path)throws Exception {
        List<Map<String,Object>> r=new ArrayList<>();ObjectMapper json=new ObjectMapper();
        for(String line:Files.readAllLines(path,StandardCharsets.UTF_8))if(!line.trim().isEmpty())r.add(json.readValue(line,Map.class));
        return r;
    }
    static List<Map<String,Object>> copy(List<Map<String,Object>> records)throws Exception {
        List<Map<String,Object>> out=new ArrayList<>();ObjectMapper json=new ObjectMapper();
        for(Map<String,Object> r:records)out.add(json.readValue(json.writeValueAsString(r),Map.class));return out;
    }
    @SuppressWarnings("unchecked")
    static TransferJournal replay(List<Map<String,Object>> records)throws Exception {
        Path file=Files.createTempDirectory("carried-completion-").resolve("journal.jsonl");ObjectMapper json=new ObjectMapper();StringBuilder text=new StringBuilder();
        for(Map<String,Object> r:records)text.append(json.writeValueAsString(r)).append('\n');Files.write(file,text.toString().getBytes(StandardCharsets.UTF_8));
        return new TransferJournal(file);
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> request(List<Map<String,Object>> records){return new LinkedHashMap<>((Map<String,Object>)records.get(0).get("request"));}
    static boolean ready(TransferJournal j,Map<String,Object> req,String stage){return Boolean.TRUE.equals(j.stage(req,stage).get("success"));}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        List<Map<String,Object>> actual=read(Paths.get(args[0]).resolve("test/fixtures/return_6894_journal.jsonl"));
        Map<String,Object> req=request(actual);TransferJournal j=replay(actual);
        check(actual.stream().noneMatch(r->r.get("event") instanceof Map && "ACTION_BEGIN".equals(((Map<?,?>)r.get("event")).get("status"))
            && "UP_CONTAINER".equals(((Map<?,?>)r.get("event")).get("actionType"))),"Capture has no UP begin");
        for(String stage:new String[]{"assigned","transfer-started","departed-source","arrived-dest","drop-started","unassigned","completed"})
            check(ready(j,req,stage),"Actual carried-source capture proves "+stage);
        check(!ready(j,req,"pickup-started") && !ready(j,req,"pickup-completed"),"Never invent another pickup for a carried source");
        check("".equals(j.stage(req,"handling").get("handlingAction")),"Completed return is not active handling");
        TransferJournal live=new TransferJournal(null);live.streamReady();live.reserve(req,"lay01-dist1-105","2");live.accepted(ID);
        for(Map<String,Object> r:actual)if(r.get("event") instanceof Map){Map<String,Object> e=(Map<String,Object>)r.get("event");live.observe(e);live.observe(e);}
        check(ready(live,req,"completed"),"Live event path and duplicate callbacks agree with replay");
        Snapshot f=new Snapshot();TransferService service=new TransferService(f,j);
        check(Boolean.TRUE.equals(service.gate(req,"fixture",false).get("success")),"Public progress gate handles already carried source");
        check(Boolean.TRUE.equals(service.stage(req,"fixture","completed").get("success")),"Actual job/cargo snapshot confirms delivery");
        List<Map<String,Object>> stopped=copy(actual);stopped.add(TransferService.map("kind","GAP"));
        check(ready(replay(stopped),req,"completed"),"Shutdown after recorded terminal completion does not invalidate history");
        for(String defect:new String[]{"missingBeginMove","missingSourceArrival","missingUpEnd","missingDeparture","missingDestinationArrival","missingDropEnd","missingTaskEnd","gap","wrongOwner","wrongCarrier","wrongInstance","badManual","failedUpEnd","normalPort","unverifiedSource"}){
            List<Map<String,Object>> bad=copy(actual);
            if(defect.equals("normalPort"))((Map<String,Object>)bad.get(0).get("request")).put("SOURCEPORT","PORT_97");
            if(defect.equals("unverifiedSource"))bad.get(0).put("sourceVehicle","");
            for(Iterator<Map<String,Object>> it=bad.iterator();it.hasNext();){
                Map<String,Object> r=it.next();if(!(r.get("event") instanceof Map))continue;
                Map<String,Object> e=(Map<String,Object>)r.get("event");String status=(String)e.get("status"),action=(String)e.get("actionType");int sequence=((Number)e.get("sequence")).intValue();
                if(defect.equals("missingBeginMove") && status.equals("MOVE_BEGIN") && sequence==1
                    || defect.equals("missingSourceArrival") && status.equals("MOVE_END") && sequence==1
                    || defect.equals("missingUpEnd") && status.equals("ACTION_END") && action.equals("UP_CONTAINER")
                    || defect.equals("missingDeparture") && status.equals("MOVE_BEGIN") && sequence==2
                    || defect.equals("missingDestinationArrival") && status.equals("MOVE_END") && sequence==2
                    || defect.equals("missingDropEnd") && status.equals("ACTION_END") && action.equals("DOWN_CONTAINER")
                    || defect.equals("missingTaskEnd") && status.equals("TASK_COMPLETED")){it.remove();continue;}
                if(defect.equals("wrongOwner"))e.put("robotId","1");
                if(defect.equals("wrongCarrier"))e.put("carrierId","OTHER");
                if(defect.equals("wrongInstance") && sequence==2)e.put("instanceId","9999");
                if(defect.equals("badManual"))e.put("manualCompleteFlag",1);
                if(defect.equals("failedUpEnd") && status.equals("ACTION_END") && action.equals("UP_CONTAINER"))e.put("success",false);
            }
            if(defect.equals("gap"))bad.add(4,TransferService.map("kind","GAP"));
            Map<String,Object> changed=request(bad);TransferJournal invalid=replay(bad);
            check(!ready(invalid,changed,"unassigned"),"No completed release with "+defect);
        }
        hostCatchUp(j,req);
        for(String defect:new String[]{"cargo","job","ownerLoaded","ownerBound"}){
            Snapshot invalid=new Snapshot();if(defect.equals("cargo"))invalid.cargoNode="SECS_ST_90";
            if(defect.equals("job"))invalid.status="20";if(defect.equals("ownerLoaded"))invalid.robotCargo="SIM_CARGO_001";
            if(defect.equals("ownerBound"))invalid.binding="OTHER";
            NativeHostEngine engine=engine(j,req,invalid,new HostState(null));engine.tick();
            check(engine.state.data.reports.stream().noneMatch(d->d.ceid==207),"Retained callbacks alone cannot override conflicting final "+defect);
            check(invalid.mutations==0,"Final-state checks never resend transport: "+defect);
        }
        System.out.println("PASS: "+checks+" real carried-return callback, completion catch-up, identity and no-replay checks; captured replay and fixtures only.");
    }
    static NativeHostEngine engine(TransferJournal journal,Map<String,Object> req,Snapshot f,HostState state){
        HostState.Job old=new HostState.Job();old.phase="ABORTED";old.vehicle="2";old.request.put("COMMANDID",ORIGINAL);old.request.put("CARRIERID","SIM_CARGO_001");
        old.recoveryArmed=true;old.recoveryState="CLAIMED";old.recoveryNode="lay01-dist1-105";old.recoveryCommand=ID;state.data.jobs.put(ORIGINAL,old);
        HostState.Job active=new HostState.Job();active.request=new LinkedHashMap<>(req);active.phase="ACTIVE";active.vehicle="2";active.instance="6894";active.next=3;
        state.data.jobs.put(ID,active);state.save();
        NativeHostEngine engine=new NativeHostEngine(new TransferService(f,journal),f,state,"fixture",Collections.emptyMap(),Collections.emptyMap(),9000);
        engine.configureRecovery(Collections.singletonMap("2","lay01-dist1-105"));return engine;
    }
    static void hostCatchUp(TransferJournal journal,Map<String,Object> req)throws Exception {
        Snapshot fleet=new Snapshot();Path disk=Files.createTempDirectory("return-completion-host-").resolve("state.json");HostState state=new HostState(disk);
        NativeHostEngine host=engine(journal,req,fleet,state);host.tick();
        check(state.data.jobs.get(ID).phase.equals("COMPLETED"),"Current stuck ACTIVE/next3 becomes completed from retained facts");
        for(int ceid:new int[]{211,605,601,606,302,607,610,207})check(state.data.reports.stream().filter(d->d.command.equals(ID) && d.ceid==ceid).count()==1,"Exactly one missing report "+ceid);
        check(state.data.reports.stream().noneMatch(d->d.ceid==301 || d.ceid==602 || d.ceid==603 || d.ceid==604 || d.ceid==208),"No fabricated pickup or duplicate already sent start/assignment");
        check(state.data.reports.stream().filter(d->d.ceid==211).allMatch(d->d.location.equals("2")),"Transfer progress location is actual carrying vehicle");
        check(state.data.reports.stream().filter(d->d.ceid==601).allMatch(d->d.port.equals("PORT_89")),"Only destination arrival reported");
        check(state.data.jobs.get(ORIGINAL).recoveryState.equals("RETURNED") && state.data.reports.stream().anyMatch(d->d.ceid==9004 && d.outcome.equals("RETURNED")),"Original ABORT recovery closes after actual delivery");
        int count=state.data.reports.size();host.tick();
        HostState restarted=new HostState(disk);NativeHostEngine next=new NativeHostEngine(new TransferService(fleet,journal),fleet,restarted,"fixture",Collections.emptyMap(),Collections.emptyMap(),9000);
        next.configureRecovery(Collections.singletonMap("2","lay01-dist1-105"));next.tick();
        check(restarted.data.reports.size()==count,"Restart and repeated ticks do not duplicate terminal reports");
        check(fleet.mutations==0,"Completion catch-up sends no robot commands");
    }
}
