package servostar.fleet.transfer;

import java.nio.file.*;
import java.util.*;

/** Local Fleet fixtures only. Never connects to the live installation. */
public final class LoadedRecoveryTest {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class Fleet implements TransferService.FleetClient {
        String node="lay01-dist1-97",cargo="SIM_CARGO_001",mission="",otherNode="lay01-dist1-103";
        String status="3",occupy="0",model="SIM_TRAY_001";
        boolean lost,negative,done,occupied,busyJob,wrongOwner,inventoryLoaded=true;
        int mutations;Map<String,Object> last;
        final Map<String,Object> jobs=new LinkedHashMap<>();
        public Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if(path.equals("robotQuery")){
                Map<String,Object> r=TransferService.map("robotId","2","status",status,"occupyStatus",occupy,"containerCode",cargo,
                    "liftStatus",cargo.isEmpty()?0:1,"nodeCode",node,"missionCode",mission,"rcsMissionCode",mission);
                Map<String,Object> other=TransferService.map("robotId","1","status",3,"occupyStatus",0,"containerCode",wrongOwner?cargo:"","nodeCode",occupied?"lay01-dist1-105":otherNode);
                return good(Arrays.asList(r,other));
            }
            if(path.equals("containerQueryAll") || path.equals("containerQuery"))return good(Arrays.asList(TransferService.map("containerCode","SIM_CARGO_001","inMapStatus",1,"isCarry",inventoryLoaded?1:0,"nodeCode",!inventoryLoaded && node.endsWith("-89")?"SECS_ST_89":node,"containerModelCode",model)));
            if(path.equals("jobQuery")){
                if(body.containsKey("status"))return good(busyJob?Arrays.asList(TransferService.map("robotId","2","containerCode",cargo,"status",20)):Collections.emptyList());
                if(last!=null && Objects.equals(body.get("jobCode"),last.get("missionCode"))){
                    List<?> steps=(List<?>)last.get("missionData");Object dest=((Map<?,?>)steps.get(steps.size()-1)).get("position");
                    return good(Arrays.asList(TransferService.map("jobCode",last.get("missionCode"),"workflowId",6837,"robotId","2","containerCode","SIM_CARGO_001","warnFlag",0,"finalNodeCode",dest,"targetCellCode",dest,"status",done?30:20,"completeTime",done?"observed":"")));
                }
                return good(Collections.emptyList());
            }
            if(path.equals("submitMission")){mutations++;last=body;if(lost)throw new java.io.IOException("lost reply");return TransferService.map("success",!negative,"code",negative?"ERROR":"0");}
            throw new AssertionError("Unexpected API "+path);
        }
        Map<String,Object> good(Object data){return TransferService.map("success",true,"code","0","data",data);}
    }
    static HostState.Job aborted(HostState s){
        HostState.Job j=new HostState.Job();j.request=TransferService.normalize(TransferTest.request("ABORTED_001"));
        j.phase="ABORTED";j.vehicle="2";j.instance="6889";j.recoveryArmed=true;s.data.jobs.put("ABORTED_001",j);s.save();return j;
    }
    static LoadedRecovery configured(HostState s,Fleet f){LoadedRecovery r=new LoadedRecovery(s,f,"fixture");r.configure(Collections.singletonMap("2","lay01-dist1-105"));return r;}
    static Map<String,Object> request(String source){Map<String,Object> r=TransferTest.request("RETURN_001");r.put("SOURCEPORT",source);return r;}
    static void ack(HostState s){for(HostState.Delivery d:s.data.reports)d.state="ACKED";s.save();}
    public static void main(String[] args)throws Exception {
        Fleet f=new Fleet();HostState s=new HostState(null);HostState.Job j=aborted(s);LoadedRecovery recovery=configured(s,f);
        recovery.tick();check(f.mutations==1 && j.recoveryState.equals("MOVING"),"One wait move after confirmed ABORT");
        List<?> steps=(List<?>)f.last.get("missionData");
        check(((List<?>)f.last.get("robotIds")).equals(Arrays.asList("2")),"Wait move pins carrying AMR");
        check(f.last.get("containerCode").equals("SIM_CARGO_001") && ((Map<?,?>)steps.get(1)).get("actionType").equals(""),"Preserve cargo context; never lower at waiting node");
        for(int i=0;i<3;i++)recovery.tick();check(f.mutations==1,"No movement replay while running");
        f.done=true;recovery.tick();check(j.recoveryState.equals("MOVING"),"Job30 alone is not arrival proof");
        f.node="lay01-dist1-105";f.status="4";recovery.tick();check(j.recoveryState.equals("MOVING"),"Wait for actual idle release");
        f.status="3";recovery.tick();check(j.recoveryState.equals("READY"),"Loaded idle at target plus completed own job proves readiness");
        try{recovery.ready(request("AMR_2"));throw new AssertionError("Unacked ready admitted");}catch(CargoService.Rejected expected){checks++;}
        ack(s);check(recovery.ready(request("AMR_2"))==j && recovery.ready(request("2"))==j,"Canonical and actual AMR ID accepted after ACK");
        recovery.verifyReady(j,request("AMR_2"));f.node="lay01-dist1-104";
        try{recovery.verifyReady(j,request("AMR_2"));throw new AssertionError("Wrong wait node admitted");}catch(CargoService.Rejected expected){checks++;}
        f.node="lay01-dist1-105";
        TransferJournal journal=new TransferJournal(null);journal.streamReady();TransferService service=new TransferService(f,journal);
        Map<String,Object> preview=service.preview(request("AMR_2"),"fixture");
        Map<?,?> body=(Map<?,?>)preview.get("data");
        check(((List<?>)body.get("robotIds")).equals(Arrays.asList("2")),"Return cannot dispatch another vehicle");
        check(((Map<?,?>)((List<?>)body.get("missionData")).get(0)).get("position").equals(f.node),"Return starts from fresh actual vehicle node");
        check(((Map<?,?>)((List<?>)body.get("missionData")).get(1)).get("actionType").equals("DOWN_CONTAINER"),"Only return destination lowers cargo");
        int before=f.mutations;f.wrongOwner=true;
        try{service.submit(request("AMR_2"),"fixture");throw new AssertionError("Ambiguous cargo admitted");}catch(CargoService.Rejected expected){checks++;}
        check(f.mutations==before,"Identity rejection happens before transport POST");f.wrongOwner=false;
        f.busyJob=true;
        try{service.submit(request("AMR_2"),"fixture");throw new AssertionError("Foreign job admitted");}catch(CargoService.Rejected expected){checks++;}f.busyJob=false;
        Map<String,Object> wrong=request("AMR_1");
        try{recovery.ready(wrong);throw new AssertionError("Wrong vehicle admitted");}catch(CargoService.Rejected expected){checks++;}
        for(String node:Arrays.asList("lay01-dist1-62","lay01-dist1-87","lay01-dist1-89","other-105")){
            try{recovery.configure(Collections.singletonMap("2",node));throw new AssertionError("Unsafe wait config");}catch(IllegalArgumentException expected){checks++;}
        }
        Fleet lost=new Fleet();lost.lost=true;Path disk=Files.createTempDirectory("loaded-recovery-").resolve("state.json");HostState durable=new HostState(disk);HostState.Job pending=aborted(durable);
        LoadedRecovery lostRecovery=configured(durable,lost);lostRecovery.tick();check(lost.mutations==1 && pending.recoveryState.equals("UNCERTAIN"),"Lost wait reply never treated as rejection");
        for(int i=0;i<3;i++)lostRecovery.tick();HostState restarted=new HostState(disk);configured(restarted,lost).tick();
        check(lost.mutations==1 && restarted.data.jobs.get("ABORTED_001").recoveryState.equals("UNCERTAIN"),"Restart never replays uncertain wait mutation");
        pending.recoveryState="SENDING";durable.save();check(new HostState(disk).data.jobs.get("ABORTED_001").recoveryState.equals("UNCERTAIN"),"Crash in sending state needs explicit review");
        Fleet blocked=new Fleet();blocked.occupied=true;HostState bs=new HostState(null);HostState.Job bj=aborted(bs);configured(bs,blocked).tick();
        check(bj.recoveryState.equals("BLOCKED") && blocked.mutations==0,"Occupied wait node blocks before send");
        Fleet empty=new Fleet();empty.cargo="";empty.inventoryLoaded=false;HostState es=new HostState(null);HostState.Job ej=aborted(es);configured(es,empty).tick();
        check(ej.recoveryState.equals("NOT_LOADED") && empty.mutations==0,"Drop completed before abort does not send empty recovery move");
        Fleet pause=new Fleet();HostState ps=new HostState(null);HostState.Job pj=aborted(ps);ps.data.tsc="PAUSED";configured(ps,pause).tick();
        check(pause.mutations==0 && pj.recoveryState.isEmpty(),"No automatic wait move while TSC paused");
        Fleet old=new Fleet();HostState os=new HostState(null);HostState.Job oj=aborted(os);oj.recoveryArmed=false;configured(os,old).tick();
        check(old.mutations==0 && oj.recoveryState.isEmpty(),"Enabling new feature never moves historical aborted jobs");
        lifecycle(Paths.get(args[0]));
        sdkContract(j,steps);
        hostLifecycle(Paths.get(args[0]));
        System.out.println("PASS: "+checks+" loaded ABORT standby, return ownership, durability and lifecycle checks; fixtures only.");
    }
    static void sdkContract(HostState.Job j,List<?> ignored)throws Exception {
        Map<String,Object> body=LoadedRecovery.waitMission(j,TransferService.map("nodeCode","lay01-dist1-97"));
        com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
        com.kuka.interfaces.plugin.entity.AgvTaskDto dto=json.convertValue(body,com.kuka.interfaces.plugin.entity.AgvTaskDto.class);
        Object handler=new com.kuka.interfaces.plugin.handle.RackMoveNodeActionHandler();
        java.lang.reflect.Method method=handler.getClass().getDeclaredMethod("buildAction",com.kuka.interfaces.plugin.entity.AgvTaskDto.class,
            com.kuka.interfaces.plugin.entity.RackMoveDto.class,Integer.class,String.class);method.setAccessible(true);
        List<?> steps=(List<?>)body.get("missionData");
        com.kuka.interfaces.plugin.entity.RackMoveDto destination=json.convertValue(steps.get(1),com.kuka.interfaces.plugin.entity.RackMoveDto.class);
        Object actual=method.invoke(handler,dto,destination,Integer.valueOf(2),"");
        check(steps.size()==2,"Wait request preserves source and destination nodes");
        String encoded=json.writeValueAsString(actual);
        check(!encoded.contains("DOWN_CONTAINER") && encoded.contains("lay01-dist1-105"),"Installed handler destination has no implicit DOWN");
    }
    static void lifecycle(Path root)throws Exception {
        Fleet f=new Fleet();f.node="lay01-dist1-105";TransferJournal journal=new TransferJournal(null);journal.streamReady();
        Map<String,Object> req=request("AMR_2");new TransferService(f,journal).submit(req,"fixture");
        List<Map<String,Object>> trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        for(Map<String,Object> original:trace){
            Map<String,Object> e=new LinkedHashMap<>(original);e.put("commandId","RETURN_001");e.put("robotId","2");
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation"))if("lay01-dist1-89".equals(e.get(key)))e.put(key,"lay01-dist1-105");
            if("lay01-dist1-97".equals(e.get("currentNode")))e.put("currentNode","lay01-dist1-89");
            if("lay01-dist1-97".equals(e.get("nextNode")))e.put("nextNode","lay01-dist1-89");
            if("lay01-dist1-97".equals(e.get("carrierLocation")))e.put("carrierLocation","lay01-dist1-89");
            journal.observe(e);
        }
        check(Boolean.TRUE.equals(journal.stage(TransferService.normalize(req),"completed").get("success")),"Carried-source lifecycle uses captured source node and exact owner");
        // Reject a different owner even if it emits otherwise plausible callbacks.
        TransferJournal wrong=new TransferJournal(null);wrong.streamReady();wrong.reserve(TransferService.normalize(req),f.node,"2");wrong.accepted("RETURN_001");
        Map<String,Object> e=new LinkedHashMap<>(trace.get(0));e.put("commandId","RETURN_001");e.put("robotId","1");wrong.observe(e);
        check(!Boolean.TRUE.equals(wrong.stage(TransferService.normalize(req),"assigned").get("success")),"Other AMR callback cannot bind return transfer");
    }
    static void hostLifecycle(Path root)throws Exception {
        Fleet f=new Fleet();f.node="lay01-dist1-105";HostState state=new HostState(null);HostState.Job parent=aborted(state);
        parent.recoveryState="READY";parent.recoveryNode=f.node;
        TransferJournal journal=new TransferJournal(null);journal.streamReady();
        NativeHostEngine engine=new NativeHostEngine(new TransferService(f,journal),f,state,"fixture",Collections.emptyMap(),Collections.emptyMap(),9000);
        engine.configureRecovery(Collections.singletonMap("2",f.node));
        Map<String,Object> req=request("AMR_2");
        check(NativeHostTest.ack(engine.transfer(req))==4 && parent.recoveryState.equals("CLAIMED"),"SECS accepts one exact AMR recovery and claims original disposition");
        check(NativeHostTest.ack(engine.transfer(req))==5 && f.mutations==1,"Duplicate recovery never submits twice");
        List<Map<String,Object>> trace=ReportingTest.events(root.resolve("../reporting/TR_TEST_001_WCS_status_20261027.txt"));
        for(Map<String,Object> original:trace){
            Map<String,Object> e=new LinkedHashMap<>(original);e.put("commandId","RETURN_001");e.put("robotId","2");
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation")){
                Object value=e.get(key);if("lay01-dist1-89".equals(value))e.put(key,f.node);else if("lay01-dist1-97".equals(value))e.put(key,"lay01-dist1-89");
            }
            if("TASK_COMPLETED".equals(e.get("status")) || "DOWN_CONTAINER".equals(e.get("actionType")))break;
            journal.observe(e);engine.observe(e);
        }
        engine.tick();
        for(int ceid:new int[]{208,604,211,605})check(state.data.reports.stream().anyMatch(d->d.ceid==ceid),"Return emits actual transfer/vehicle stage "+ceid);
        check(state.data.reports.stream().noneMatch(d->d.ceid==301 || d.ceid==602 || d.ceid==603),"Already carried recovery never fabricates pickup/installation reports");
        check(state.data.reports.stream().filter(d->d.ceid==211).allMatch(d->d.location.equals("2")),"Recovery progress location is actual vehicle ID");
        check(state.data.reports.stream().filter(d->d.ceid==601).noneMatch(d->d.port.equals("AMR_2")),"No fake arrival at source vehicle");
        for(Map<String,Object> original:trace){
            Map<String,Object> e=new LinkedHashMap<>(original);e.put("commandId","RETURN_001");e.put("robotId","2");
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation")){
                Object value=e.get(key);if("lay01-dist1-89".equals(value))e.put(key,"lay01-dist1-105");else if("lay01-dist1-97".equals(value))e.put(key,"lay01-dist1-89");
            }
            journal.observe(e);engine.observe(e);
        }
        f.node="lay01-dist1-89";f.cargo="";f.inventoryLoaded=false;f.done=true;engine.tick();
        check(state.data.jobs.get("RETURN_001").phase.equals("COMPLETED") && parent.recoveryState.equals("RETURNED"),"Correlated return completes and closes original loaded disposition");
        check(state.data.reports.stream().anyMatch(d->d.ceid==207 && d.command.equals("RETURN_001") && d.result==0),"Only return command emits successful207 after actual delivery proof");
        Fleet lost=new Fleet();lost.node=f.node;lost.lost=true;HostState ls=new HostState(null);HostState.Job old=aborted(ls);old.recoveryState="READY";old.recoveryNode=f.node;
        TransferJournal lj=new TransferJournal(null);lj.streamReady();NativeHostEngine le=new NativeHostEngine(new TransferService(lost,lj),lost,ls,"fixture",Collections.emptyMap(),Collections.emptyMap(),9000);
        try{le.transfer(req);throw new AssertionError("Lost reply rejected as certain");}catch(NativeHostEngine.UncertainCommand expected){checks++;}
        check(old.recoveryState.equals("CLAIMED") && ls.data.jobs.get("RETURN_001").phase.equals("UNCERTAIN"),"Lost recovery response preserves parent claim and uncertain command");
    }
}
