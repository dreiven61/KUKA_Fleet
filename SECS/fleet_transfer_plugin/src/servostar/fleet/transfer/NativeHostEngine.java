package servostar.fleet.transfer;

import java.util.*;
import java.util.logging.Logger;
import com.kuka.ecs.common.transport.secs.secs2.*;

/** Native host business boundary. HTTP acknowledgement never means robot completion.
 * All mutations are serialized; received WCS facts and outbound reports are durable. */
public final class NativeHostEngine {
    static final class UncertainCommand extends RuntimeException {UncertainCommand(){super("Command outcome uncertain; no rejection or automatic replay");}}
    private static final Logger LOG=Logger.getLogger(NativeHostEngine.class.getName());
    private final TransferService service;
    private final TransferService.FleetClient client;
    final HostState state;
    final VehicleNames vehicleNames;
    private final String token;
    private final Map<String,Map<String,Object>> alarmMap;
    private final Map<String,Integer> failures;
    private final int genericFailure;
    private final LoadedRecovery recovery;
    private final WorkflowAlarmMonitor workflowAlarms;
    private static final int[] CEIDS={208,604,601,211,602,301,603,605,601,606,302,607,610,207};
    private static final String[] STAGES={"","assigned","arrived-source","transfer-started","pickup-started","pickup-completed","pickup-completed","departed-source","arrived-dest","drop-started","completed","completed","unassigned","completed"};
    public NativeHostEngine(TransferService service,TransferService.FleetClient client,HostState state,String token,
            Map<String,Map<String,Object>> alarms,Map<String,Integer> failures,int genericFailure){
        this.service=service;this.client=client;this.state=state;this.token=token;this.alarmMap=alarms;this.failures=failures;this.genericFailure=genericFailure;
        this.vehicleNames=new VehicleNames(state);
        this.recovery=new LoadedRecovery(state,client,token);
        this.workflowAlarms=new WorkflowAlarmMonitor(state,client,token,alarms);
    }
    void pollWorkflowAlarms(){workflowAlarms.poll();}
    void configureRecovery(Map<String,String> nodes){recovery.configure(nodes);}
    synchronized Map<String,Object> recoveryStatus(){return new LinkedHashMap<String,Object>(recovery.configuredNodes());}
    public synchronized Map<String,Object> recoverLoadedAbort(String id,String carrier,String vehicle,String source,String wait,boolean apply)throws Exception {
        synchronized(state){state.requireHealthy();authenticate();return recovery.review(id,carrier,vehicle,source,wait,apply);}
    }
    static boolean ok(Map<String,Object> r){return r!=null && Boolean.TRUE.equals(r.get("success")) && (r.get("code")==null || "0".equals(String.valueOf(r.get("code"))));}
    private void authenticate()throws Exception {if(!ok(client.post("robotQuery",token,Collections.<String,Object>emptyMap())))throw new SecurityException("Fleet authentication unavailable");}
    /** Custom inventory commands. Never auto-register a carrier on TRANSFER. */
    public synchronized Secs2 cargo(String command,Map<String,String> p)throws Exception {
        synchronized(state){
            state.requireHealthy();authenticate();String id=p.get("REQUESTID");
            if(state.data.cargoOperations.containsKey(id))return HostProtocol.ack(5);
            if(state.data.cargoOperations.size()>=1000 || state.data.reports.size()>19995)return HostProtocol.ack(2);
            HostState.CargoOperation op=new HostState.CargoOperation();op.command=command;op.request=new LinkedHashMap<>(p);
            state.data.cargoOperations.put(id,op);state.save();
            CargoService cargo=new CargoService(client);
            try {
                if("CARRIER_VERIFY".equals(command)){
                    HostState.CargoOperation target=state.data.cargoOperations.get(p.get("TARGETID"));
                    if(target==null || !Arrays.asList("UNCERTAIN","CONFIRMED").contains(target.phase) || !Arrays.asList("CARRIER_IN","CARRIER_OUT").contains(target.command))throw new CargoService.Rejected("UNCERTAIN_MUTATION_NOT_FOUND");
                    for(HostState.Job job:state.data.jobs.values())if(!terminal(job.phase) && target.request.get("CARRIERID").equals(job.request.get("CARRIERID")))throw new CargoService.Rejected("ACTIVE_OR_UNCERTAIN_TRANSFER");
                    // Operator has checked the physical placement. Only confirm the
                    // requested current state; never repeat the original HTTP POST.
                    cargo.assertNoFleetWork(target.request.get("CARRIERID"),target.request.get("PORT"),token);
                    if(!cargo.confirmed(target.command,target.request,token))throw new CargoService.Rejected("REQUESTED_STATE_NOT_CONFIRMED");
                    target.phase="CONFIRMED";
                    cargoResult(target,"CONFIRMED","MANUAL_STATE_VERIFIED","cargo-verified:"+p.get("TARGETID")+":"+id);
                    op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","READ_ONLY_REVIEW_COMPLETED");state.save();return HostProtocol.ack(4);
                }
                if("CARRIER_QUERY".equals(command)){
                    HostState.Delivery d=new HostState.Delivery();d.kind="INVENTORY";d.command=id;d.key="inventory:"+id;
                    d.inventory=cargo.query(token);state.add(d);op.phase="CONFIRMED";state.save();return HostProtocol.ack(4);
                }
                if("CARRIER_SCAN_TEST".equals(command)){
                    List<Map<String,Object>> rows=cargo.query(token);String scanned=p.get("SCANNEDID");int result;
                    if(!"1".equals(p.get("SCANOK")) || scanned.isEmpty())result=1;
                    else if(!scanned.equals(p.get("CARRIERID")))result=3;
                    else {
                        Map<String,Object> existing=CargoService.find(rows,scanned);
                        result=existing!=null && "1".equals(CargoService.str(existing,"inMapStatus")) && !WorkPorts.samePort(p.get("PORT"),CargoService.port(existing))?2:0;
                    }
                    HostState.Delivery d=new HostState.Delivery();d.kind="ID_READ_TEST";d.command=id;d.key="scan-test:"+id;
                    d.carrier=scanned;d.location=p.get("PORT");d.result=result;state.add(d);
                    op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","SIMULATED_ID_READ_"+result);state.save();return HostProtocol.ack(4);
                }
                ensureCargoFree(p);
                Map<String,Object> body=cargo.prepare(command,p,token);
                op.phase="MUTATING";state.save(); // durable before an irreversible HTTP request
                Map<String,Object> reply=client.post("CARRIER_IN".equals(command)?"containerIn":"containerOut",token,body);
                if(reply==null || !(reply.get("success") instanceof Boolean) || !reply.containsKey("code"))throw new IllegalStateException("Unrecognized mutation response");
                if(!ok(reply)){op.phase="REJECTED";cargoResult(op,"REJECTED","FLEET_REJECTED_"+String.valueOf(reply.get("code")).replaceAll("[^A-Za-z0-9_-]","_"));}
                else if(cargo.confirmed(command,p,token)){op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","INVENTORY_VERIFIED");}
                else {op.phase="UNCERTAIN";cargoResult(op,"UNCERTAIN","VERIFY_INVENTORY_NO_REPLAY");}
            }catch(CargoService.Rejected ex){
                op.phase="MUTATING".equals(op.phase)?"UNCERTAIN":"REJECTED";
                cargoResult(op,op.phase,"UNCERTAIN".equals(op.phase)?"VERIFY_INVENTORY_NO_REPLAY":ex.getMessage());
            }
            catch(Exception ex){
                boolean attempted="MUTATING".equals(op.phase);op.phase=attempted?"UNCERTAIN":"REJECTED";
                cargoResult(op,op.phase,attempted?"VERIFY_INVENTORY_NO_REPLAY":"QUERY_UNAVAILABLE_NO_MUTATION");
            }
            state.save();return HostProtocol.ack(4); // outcome is the correlated 9002 report, not this ACK
        }
    }
    private void ensureCargoFree(Map<String,String> p){
        for(HostState.Job job:state.data.jobs.values())if(!terminal(job.phase) &&
            (p.get("CARRIERID").equals(job.request.get("CARRIERID")) || p.get("PORT").equals(job.request.get("SOURCEPORT")) || p.get("PORT").equals(job.request.get("DESTPORT"))))
            throw new CargoService.Rejected("ACTIVE_OR_UNCERTAIN_TRANSFER");
        for(HostState.CargoOperation other:state.data.cargoOperations.values())if(!p.get("REQUESTID").equals(other.request.get("REQUESTID")) &&
            "UNCERTAIN".equals(other.phase) && (p.get("CARRIERID").equals(other.request.get("CARRIERID")) || p.get("PORT").equals(other.request.get("PORT"))))
            throw new CargoService.Rejected("UNCERTAIN_INVENTORY_OPERATION");
    }
    private HostState.Delivery cargoResult(HostState.CargoOperation op,String outcome,String detail){
        return cargoResult(op,outcome,detail,"cargo-result:"+op.request.get("REQUESTID"));
    }
    private HostState.Delivery cargoResult(HostState.CargoOperation op,String outcome,String detail,String key){
        HostState.Delivery d=new HostState.Delivery();d.kind="CARGO_RESULT";d.ceid=9002;d.command=op.request.get("REQUESTID");
        d.operation=op.command;d.outcome=outcome;d.carrier=op.request.getOrDefault("CARRIERID","");d.port=op.request.getOrDefault("PORT","");
        d.detail=detail;d.key=key;state.add(d);return d;
    }
    /** Standard HCACK remains unchanged. Test-profile CEID9003 carries the reason
     * separately; never put a business error into a fake CPACK parameter. */
    synchronized Secs2 reject(String command,String id,int ack,String reason,String blocking){
        synchronized(state){
            state.requireHealthy();
            HostState.Delivery d=new HostState.Delivery();d.kind="COMMAND_REJECT";d.ceid=9003;
            d.command=id==null?"":id;d.operation=command;d.result=ack;d.detail=reason;
            d.outcome=state.data.tsc;d.port=blocking==null?"":blocking;
            d.key="rejection:"+state.data.sequence;
            if(state.data.reports.size()<20000){state.add(d);state.save();}
            LOG.warning("SECS reject RCMD="+command+" COMMANDID="+d.command+" HCACK="+ack+" REASON="+reason+" TSC="+d.outcome+" BLOCKING="+d.port);
            return HostProtocol.ack(ack);
        }
    }
    private String blockingControl(){
        for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase) && !j.control.isEmpty())return (String)j.request.get("COMMANDID");
        return "";
    }
    public synchronized Secs2 transfer(Map<String,Object> request){
        synchronized(state){
            state.requireHealthy();String id=(String)request.get("COMMANDID");
            if(state.data.jobs.containsKey(id))return reject("TRANSFER",id,5,"DUPLICATE_COMMAND_ID",id);
            if(!"AUTO".equals(state.data.tsc))return reject("TRANSFER",id,2,"TSC_NOT_AUTO",blockingControl());
            if(state.data.jobs.size()>=1000)return reject("TRANSFER",id,2,"JOB_CAPACITY","");
            for(HostState.CargoOperation op:state.data.cargoOperations.values())if("UNCERTAIN".equals(op.phase) &&
                (request.get("CARRIERID").equals(op.request.get("CARRIERID")) || request.get("SOURCEPORT").equals(op.request.get("PORT")) || request.get("DESTPORT").equals(op.request.get("PORT"))))return reject("TRANSFER",id,2,"UNCERTAIN_INVENTORY_OPERATION",op.request.get("REQUESTID"));
            for(HostState.Job j:state.data.jobs.values())if(j.request.get("CARRIERID").equals(request.get("CARRIERID")) && state.unsettled(j))return reject("TRANSFER",id,2,"CARRIER_HAS_PENDING_TRANSFER",(String)j.request.get("COMMANDID"));
            HostState.Job parent;
            try{parent=recovery.ready(request);if(parent!=null)recovery.verifyReady(parent,request);}
            catch(CargoService.Rejected ex){return reject("TRANSFER",id,2,safeCode(ex.getMessage()),"");}
            catch(Exception ex){return reject("TRANSFER",id,2,"RECOVERY_QUERY_UNAVAILABLE","");}
            HostState.Job job=new HostState.Job();job.request=TransferService.normalize(request);
            if(parent!=null){parent.recoveryState="CLAIMED";parent.recoveryCommand=id;job.vehicle=parent.vehicle;}
            state.data.jobs.put(id,job);state.save();
            try {
                Map<String,Object> reply=service.submit(request,token);
                job.phase=ok(reply)?"ACTIVE":"REJECTED";
                if(ok(reply)){queue(job,"TRANSFER",208,parent==null?(String)job.request.get("SOURCEPORT"):job.vehicle,0,"normal:0");job.next=1;}
                else restoreRecovery(parent);
                state.save();
                if(!ok(reply))return reject("TRANSFER",id,2,"FLEET_SUBMIT_REJECTED:"+safeCode(reply.get("code")),"");
                return HostProtocol.ack(4);
            }catch(CargoService.Rejected ex){
                restoreRecovery(parent);
                job.phase="REJECTED";state.save();String reason=ex.getMessage();
                int ack="CARRIER_NOT_REGISTERED".equals(reason)?6:"CARRIER_NOT_AT_PORT_OR_CARRIED".equals(reason)?3:2;
                Secs2 response=reject("TRANSFER",id,ack,reason.startsWith("MODEL:")?"CARRIER_MODEL_INVALID":safeCode(reason),"");
                return ack==3?HostProtocol.ack(new HostProtocol.ParameterError("SOURCEPORT",2)):response;
            }catch(TransferService.PreSubmissionException ex){restoreRecovery(parent);job.phase="REJECTED";state.save();return reject("TRANSFER",id,2,"PREFLIGHT_OR_MONITOR_UNAVAILABLE","");}
            catch(SecurityException ex){restoreRecovery(parent);job.phase="REJECTED";state.save();return reject("TRANSFER",id,2,"FLEET_AUTHENTICATION_FAILED","");}
            catch(IllegalArgumentException ex){restoreRecovery(parent);job.phase="REJECTED";state.save();return reject("TRANSFER",id,2,"REQUEST_VALIDATION_FAILED","");}
            catch(Exception ex){job.phase="UNCERTAIN";state.save();throw new UncertainCommand();}
        }
    }
    private void restoreRecovery(HostState.Job parent){if(parent!=null){parent.recoveryState="READY";parent.recoveryCommand="";}}
    private static String safeCode(Object value){String v=String.valueOf(value).replaceAll("[^A-Za-z0-9_:-]","_");return v.substring(0,Math.min(v.length(),80));}
    static boolean terminal(String phase){return Arrays.asList("COMPLETED","CANCELED","ABORTED","FAILED","REJECTED").contains(phase);}
    public synchronized Secs2 control(String command,String id)throws Exception {
        state.requireHealthy();
        try{authenticate();}catch(SecurityException ex){return reject(command,id,2,"FLEET_AUTHENTICATION_FAILED","");}
        catch(Exception ex){return reject(command,id,2,"FLEET_QUERY_UNAVAILABLE","");}
        if("PAUSE".equals(command) || "RESUME".equals(command))return systemControl(command);
        if(!"CANCEL".equals(command) && !"ABORT".equals(command))return reject(command,id,1,"UNKNOWN_COMMAND","");
        synchronized(state){
            HostState.Job job=state.data.jobs.get(id);
            if(job==null)return reject(command,id,6,"COMMAND_NOT_FOUND",id);
            if(!job.control.isEmpty())return reject(command,id,5,"CONTROL_ALREADY_PENDING",id);
            if(!"ACTIVE".equals(job.phase))return reject(command,id,2,"JOB_NOT_ACTIVE",id);
            Map<String,Object> handling;
            try{handling=service.stageForHost(request(job),token,"handling");}
            catch(Exception ex){return reject(command,id,2,"HANDLING_STATE_UNCONFIRMED",id);}
            if(!ok(handling))return reject(command,id,2,"HANDLING_STATE_UNCONFIRMED",id);
            String handlingAction;
            try{handlingAction=handlingAction(job,handling);}
            catch(Exception ex){return reject(command,id,2,"HANDLING_STATE_UNCONFIRMED",id);}
            if("CANCEL".equals(command) && !handlingAction.isEmpty())return reject(command,id,2,"LIFT_OPERATION_ACTIVE:"+handlingAction,id);
            // SKI CANCEL is limited to pre-pickup; ABORT is a running-transfer interruption.
            Map<String,Object> pickup;
            try{pickup=service.stage(request(job),token,"pickup-started");}
            catch(Exception ex){return reject(command,id,2,"PICKUP_STATE_UNCONFIRMED",id);}
            if(!"0".equals(String.valueOf(pickup.get("code"))) && !"WAITING".equals(pickup.get("code")))return reject(command,id,2,"PICKUP_STATE_UNCONFIRMED",id);
            boolean active=!WorkPorts.vehicle((String)job.request.get("SOURCEPORT")).isEmpty() || Boolean.TRUE.equals(pickup.get("success"));
            if("CANCEL".equals(command)?active:!active)return reject(command,id,2,active?"CANCEL_AFTER_PICKUP_USE_ABORT":"ABORT_BEFORE_PICKUP_USE_CANCEL",id);
            // Obtain and persist actual cargo location BEFORE accepting a control request.
            try{job.location=location(job);}catch(Exception ex){
                // A moving lift may have no stable carry/node projection yet.
                // Do not make emergency interruption depend on that endpoint.
                // Use the correlated handling station for initiation only;
                // completion still requires a fresh actual carrier location.
                if(!"ABORT".equals(command) || !Arrays.asList("UP_CONTAINER","DOWN_CONTAINER").contains(handlingAction))
                    return reject(command,id,2,"CARGO_LOCATION_UNCONFIRMED",id);
                job.location=(String)job.request.get("UP_CONTAINER".equals(handlingAction)?"SOURCEPORT":"DESTPORT");
            }
            job.control=command;job.controlState="SENDING";job.controlGeneration++;
            if("ABORT".equals(command))job.recoveryArmed=recovery.enabled(job.vehicle);
            if("ABORT".equals(command) && !handlingAction.isEmpty())job.request.put("_abortedHandling",handlingAction);
            state.save();
            Map<String,Object> reply;
            // FORCE interrupts travel, but current Fleet finishes an active
            // lift before cancellation. Do not claim instant lift interruption.
            try {reply=client.post("missionCancel",token,TransferService.map("requestId",UUID.randomUUID().toString(),"missionCode",id,
                "cancelMode","FORCE","reason","SECS "+command));}
            catch(Exception ex){job.controlState="UNCERTAIN";state.save();throw new UncertainCommand();}
            if(!ok(reply)){job.recoveryArmed=false;job.control="";job.controlState="";job.request.remove("_abortedHandling");state.save();return reject(command,id,2,"FLEET_CONTROL_REJECTED:"+safeCode(reply.get("code")),id);}
            job.controlState="ACCEPTED";
            queue(job,"TRANSFER","CANCEL".equals(command)?206:203,job.location,0,"control-start:"+job.responses.size());state.save();
            return HostProtocol.ack(4);
        }
    }
    private Secs2 systemControl(String command)throws Exception {
        synchronized(state){
            for(HostState.Job j:state.data.jobs.values())if(!j.recoveryMission.isEmpty() && Arrays.asList("MOVING","SENDING","UNCERTAIN","BLOCKED").contains(j.recoveryState))
                return reject(command,"",2,"RECOVERY_WAIT_MOVE_ACTIVE",j.recoveryMission);
            boolean pause="PAUSE".equals(command);
            if(pause && !"AUTO".equals(state.data.tsc) || !pause && !"PAUSED".equals(state.data.tsc))return reject(command,"",2,pause?"PAUSE_REQUIRES_AUTO":"RESUME_REQUIRES_PAUSED",blockingControl());
            List<HostState.Job> jobs=new ArrayList<>();
            for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase)){
                if(!j.control.isEmpty())return reject(command,"",2,"CONTROL_ALREADY_PENDING",(String)j.request.get("COMMANDID"));
                jobs.add(j);
            }
            // Validate every active job BEFORE mutating any one of them.
            // Lifting/lowering must run to completion. ABORT cancels the job
            // after that physical action, rather than pausing the lift.
            if(pause)for(HostState.Job j:jobs){
                Map<String,Object> handling;
                try{handling=service.stageForHost(request(j),token,"handling");}
                catch(Exception ex){return reject(command,"",2,"HANDLING_STATE_UNCONFIRMED",(String)j.request.get("COMMANDID"));}
                if(!ok(handling))return reject(command,"",2,"HANDLING_STATE_UNCONFIRMED",(String)j.request.get("COMMANDID"));
                String action;
                try{action=handlingAction(j,handling);}
                catch(Exception ex){return reject(command,"",2,"HANDLING_STATE_UNCONFIRMED",(String)j.request.get("COMMANDID"));}
                if(!action.isEmpty())return reject(command,"",2,"LIFT_OPERATION_ACTIVE:"+action,(String)j.request.get("COMMANDID"));
            }
            state.data.tsc=pause?"PAUSING":"RESUMING";
            for(HostState.Job j:jobs){j.control=command;j.controlState="SENDING";j.controlGeneration++;j.controlEvidence="";}
            state.save();
            int accepted=0;
            for(HostState.Job j:jobs){
                try{
                    Map<String,Object> r=client.post(pause?"pauseMission":"recoverMission",token,TransferService.map("missionCode",j.request.get("COMMANDID")));
                    if(!ok(r)){
                        if(accepted==0){for(HostState.Job reset:jobs){reset.control="";reset.controlState="";}state.data.tsc=pause?"AUTO":"PAUSED";}
                        else{j.controlState="REJECTED";state.data.tsc="UNCERTAIN";state.save();throw new UncertainCommand();}
                        state.save();return reject(command,"",2,"FLEET_CONTROL_REJECTED:"+safeCode(r.get("code")),(String)j.request.get("COMMANDID"));
                    }
                    j.controlState="ACCEPTED";accepted++;
                }catch(Exception ex){j.controlState="UNCERTAIN";state.data.tsc="UNCERTAIN";state.save();throw new UncertainCommand();}
            }
            if(pause)tsc(107);
            if(jobs.isEmpty()){state.data.tsc=pause?"PAUSED":"AUTO";tsc(pause?105:103);}
            state.save();return HostProtocol.ack(4);
        }
    }
    /** Callback facts are buffered until the corresponding control API accepts.
     * WCS warnings do not imply abnormal terminal completion. */
    public synchronized void observe(Map<String,Object> event){
        synchronized(state){
            state.requireHealthy();String status=text(event,"status"), id=text(event,"commandId"), vehicle=text(event,"robotId");
            if(!WorkPorts.identifier(vehicle,32))return;
            if("WARNING".equals(status) || "WARN_CLEAR".equals(status))alarm(event,"WARNING".equals(status));
            HostState.Job j=state.data.jobs.get(id);if(j==null || terminal(j.phase))return;
            if(!j.request.get("CARRIERID").equals(event.get("carrierId")))return;
            String instance=text(event,"instanceId");if(instance.isEmpty())return;
            if(!j.instance.isEmpty() && !j.instance.equals(instance) || !j.vehicle.isEmpty() && !j.vehicle.equals(vehicle))return;
            String response=text(event,"responseId");if(response.isEmpty() || !j.responses.add(response))return;
            if(j.responses.size()>10000)throw new IllegalStateException("Callback review capacity");
            j.instance=instance;j.vehicle=vehicle;
            if(!"0".equals(text(event,"manualCompleteFlag"))){j.phase="UNCERTAIN";state.save();return;}
            if("ACTION_END".equals(status) && Boolean.TRUE.equals(event.get("success")) && "0".equals(text(event,"resultCode")))
                clearRecoveredHandling(j,event);
            if("ACTION_END".equals(status) && !text(j.request,"_abortedHandling").isEmpty()
                && text(j.request,"_abortedHandling").equals(text(event,"actionType")))
                j.request.put("_abortedHandlingOutcome",Boolean.TRUE.equals(event.get("success")) && "0".equals(text(event,"resultCode"))?"SUCCESS":"FAILURE");
            if("CANCEL_COMPLETED".equals(status) && !j.control.isEmpty()) {
                // Preserve a terminal fact even if the callback races the REST response.
                j.request.put("_controlObserved",Boolean.TRUE.equals(event.get("success")) && "0".equals(text(event,"resultCode"))?"SUCCESS":"FAILURE");
            }
            // TASK_SUSPENDED is an exception callback, not the RCS PAUSED state.
            // PAUSE/RESUME completion is confirmed against the current job and AMR.
            if("TASK_COMPLETED".equals(status) && (!Boolean.TRUE.equals(event.get("success")) || !"0".equals(text(event,"resultCode"))))
                j.request.put("_terminalFailure",text(event,"resultCode"));
            state.save();
        }
    }
    /** Strip our durable observation fields before passing the six-field API contract. */
    private Map<String,Object> request(HostState.Job j){
        Map<String,Object> r=new LinkedHashMap<>();for(String k:Arrays.asList("COMMANDID","PRIORITY","REPLACE","CARRIERID","SOURCEPORT","DESTPORT"))r.put(k,j.request.get(k));return r;
    }
    public synchronized void tick()throws Exception {
        synchronized(state){
            state.requireHealthy();
            for(HostState.Job j:state.data.jobs.values()){
                if(!"ACTIVE".equals(j.phase))continue;
                // A failed task callback can accompany a deliberate FORCE
                // interruption. It does not replace CANCEL_COMPLETED proof or
                // convert an accepted ABORT into successful transport207.
                if(j.request.containsKey("_terminalFailure") && !("ABORT".equals(j.control) || "CANCEL".equals(j.control))){
                    int result=failures.getOrDefault((String)j.request.get("_terminalFailure"),genericFailure);
                    if(result!=0){j.location=location(j);queue(j,"TRANSFER",207,j.location,result,"abnormal");j.phase="FAILED";j.control="";j.controlState="";j.paused=false;j.controlEvidence="FAILED_LIFECYCLE";state.save();}continue;
                }
                if(!j.control.isEmpty())advanceControl(j);
            }
            // Resolve TSC before potentially slow/failed normal report gates.
            // A delivery-location query must not put a proven RESUME back into
            // RESUMING or prevent another user PAUSE after its confirmation.
            settleSystemControl();
            for(HostState.Job j:state.data.jobs.values()){
                if(!"ACTIVE".equals(j.phase) || !j.control.isEmpty())continue;
                if(j.paused)continue;
                // Drain already observed facts in order. One-second sleeps
                // between fourteen reports hide real progress during control.
                for(int ready=0;ready<CEIDS.length;ready++){
                    if(j.next>=CEIDS.length)break;
                    // FROM=AMR already owns the cargo. No new source arrival,
                    // pickup or CarrierInstalled reports are fabricated.
                    if(!WorkPorts.vehicle((String)j.request.get("SOURCEPORT")).isEmpty() && Arrays.asList(2,4,5,6).contains(j.next)){
                        j.next++;state.save();continue;
                    }
                    Map<String,Object> status=j.next==0?TransferService.map("success",true):service.stageForHost(request(j),token,STAGES[j.next]);
                    if(!Boolean.TRUE.equals(status.get("success")))break;
                    if(status.get("robotId")!=null && !status.get("robotId").toString().isEmpty())j.vehicle=status.get("robotId").toString();
                    int ceid=CEIDS[j.next];String source=(String)j.request.get("SOURCEPORT"),dest=(String)j.request.get("DESTPORT");
                    String port=j.next<8?source:dest;
                    String kind=ceid==301||ceid==302?"CARRIER":ceid>=600?"VEHICLE":"TRANSFER";
                    String loc=ceid==301 || j.next<8 && !WorkPorts.vehicle(source).isEmpty()?j.vehicle:j.next<8?source:dest;
                    HostState.Delivery d=queue(j,kind,ceid,loc,0,"normal:"+j.next);d.port=port;
                    j.next++;if(ceid==207)j.phase="COMPLETED";state.save();
                    if(terminal(j.phase))break;
                }
            }
            recovery.tick();
        }
    }
    private void settleSystemControl(){
            if("PAUSING".equals(state.data.tsc) || "RESUMING".equals(state.data.tsc)){
                boolean waiting=false;
                for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase) && !j.control.isEmpty())waiting=true;
                if(!waiting){boolean paused="PAUSING".equals(state.data.tsc);state.data.tsc=paused?"PAUSED":"AUTO";tsc(paused?105:103);state.save();}
            }
    }
    synchronized boolean hasPendingSystemControl(){
        for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase)
            && ("PAUSE".equals(j.control)||"RESUME".equals(j.control)))return true;
        return false;
    }
    private String handlingAction(HostState.Job job,Map<String,Object> handling)throws Exception {
        String action=text(handling,"handlingAction");
        if(!action.isEmpty() || job.vehicle.isEmpty())return action;
        Map<String,Object> robots=client.post("robotQuery",token,TransferService.map("robotId",job.vehicle));
        if(!ok(robots))throw new IllegalStateException("Handling vehicle state unavailable");
        if(!ControlStateVerifier.handlingStep(job,robots))return "";
        Map<?,?> robot=(Map<?,?>)((List<?>)robots.get("data")).get(0);
        for(String key:Arrays.asList("SOURCEPORT","DESTPORT"))if(WorkPorts.contains((String)job.request.get(key)) && WorkPorts.node((String)job.request.get(key)).equals(robot.get("nodeCode")))
            return "SOURCEPORT".equals(key)?"UP_CONTAINER":"DOWN_CONTAINER";
        return "CARRY_STEP";
    }
    private void advanceControl(HostState.Job j)throws Exception {
        String control=j.control;
        if("WAIT_REPORT_ACK".equals(j.controlState)){
            finishControlReports(j);return;
        }
        if("WAIT_RELEASE".equals(j.controlState)){
            finishRelease(j);return;
        }
        if(!"ACCEPTED".equals(j.controlState))return;
        if("CANCEL".equals(control) || "ABORT".equals(control)){
            Object observed=j.request.get("_controlObserved");if(observed==null)return;
            String loc=location(j);boolean success="SUCCESS".equals(observed);
            if(success && "ABORT".equals(control))handlingInterrupted(j);
            queue(j,"TRANSFER","CANCEL".equals(control)?success?204:205:success?201:202,loc,0,"control-result:"+j.controlGeneration);
            j.location=loc;j.request.remove("_controlObserved");
            if(success){
                j.request.remove("_terminalFailure");j.paused=false;j.controlState="WAIT_RELEASE";
                j.controlEvidence="CANCEL_COMPLETED_WAITING_VEHICLE_RELEASE";state.save();finishRelease(j);
            }else{j.request.remove("_abortedHandling");j.control="";j.controlState="";state.save();}
        }else {
            boolean pause="PAUSE".equals(control);String key=pause?"_pauseObserved":"_resumeObserved";
            if(j.instance.isEmpty() || j.vehicle.isEmpty())return;
            Map<String,Object> jobs=client.post("jobQuery",token,TransferService.map("jobCode",j.request.get("COMMANDID"),"limit",2));
            Map<String,Object> robots=client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
            if(!ControlStateVerifier.confirmed(pause,j,jobs,robots)){
                // EXECUTING can start and finish between API projections. A
                // complete WCS lifecycle plus matching completed Fleet job and
                // actual delivered cargo is stronger proof of ended execution.
                // Never use REST acceptance or status=30 alone as this proof.
                if(!ControlStateVerifier.completedCandidate(j,jobs) && !ControlStateVerifier.missingJob(jobs))return;
                Map<String,Object> completed=service.stageForHost(request(j),token,"completed");
                if(!ok(completed) || !j.instance.equals(text(completed,"instanceId"))
                    || !j.vehicle.equals(text(completed,"robotId")))return;
                if(pause){
                    // Task won the race against PAUSE. Do not invent209. Keep
                    // the global system paused but allow its proven207 report.
                    j.controlEvidence="COMPLETED_BEFORE_PAUSE_CONFIRMATION";
                    j.paused=false;j.control="";j.controlState="";state.save();return;
                }
                if(!j.paused)return;
                LOG.info("SECS RESUME confirmed by completed lifecycle COMMANDID="+j.request.get("COMMANDID")+" INSTANCE="+j.instance);
                j.controlEvidence=ControlStateVerifier.missingJob(jobs)?"COMPLETED_RETAINED_WCS_RELEASED":"COMPLETED_LIFECYCLE";
            }else j.controlEvidence=pause?"RCS_PAUSED":"RCS_EXECUTING";
            // Reuse the very snapshot that proved this control. An unrelated
            // container node lookup must not wedge PAUSING after actual stop.
            String loc=location(j,robots);queue(j,"TRANSFER",pause?209:210,loc,0,"system-control:"+j.controlGeneration+":"+control);
            j.paused=pause;j.request.remove(key);j.control="";j.controlState="";state.save();
        }
    }
    private void finishRelease(HostState.Job j)throws Exception {
        Map<String,Object> robots=client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
        if(!ControlStateVerifier.released(j,robots))return;
        HostState.Delivery released=queue(j,"VEHICLE",610,j.location,0,"control-release:"+j.controlGeneration);
        released.port=j.next>=8 || "DOWN_CONTAINER".equals(j.request.get("_abortedHandling"))
            ?(String)j.request.get("DESTPORT"):(String)j.request.get("SOURCEPORT");
        j.controlState="WAIT_REPORT_ACK";j.controlEvidence="VEHICLE_RELEASED_WAITING_HOST_ACK";state.save();finishControlReports(j);
    }
    private void finishControlReports(HostState.Job j){
        String command=(String)j.request.get("COMMANDID"),generation=String.valueOf(j.controlGeneration);
        for(HostState.Delivery d:state.data.reports)if(command.equals(d.command)
            && (d.key.equals(command+":control-result:"+generation) || d.key.equals(command+":control-release:"+generation)
                || d.key.startsWith("handling:")) && !"ACKED".equals(d.state))return;
        j.phase="CANCEL".equals(j.control)?"CANCELED":"ABORTED";
        j.control="";j.controlState="";j.controlEvidence="CANCEL_COMPLETED_RELEASED_AND_REPORTED";state.save();
    }
    @SuppressWarnings("unchecked")
    private String location(HostState.Job j)throws Exception {
        Map<String,Object> robots=j.vehicle.isEmpty()?Collections.emptyMap():
            client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
        return location(j,robots);
    }
    @SuppressWarnings("unchecked")
    private String location(HostState.Job j,Map<String,Object> robots)throws Exception {
        String carried=ControlStateVerifier.carriedLocation(j,robots);
        if(carried.isEmpty() && ("SUCCESS".equals(j.request.get("_controlObserved")) || "WAIT_RELEASE".equals(j.controlState)))
            carried=ControlStateVerifier.releasedCarrierLocation(j,robots);
        if(!carried.isEmpty())return carried;
        Map<String,Object> r=client.post("containerQuery",token,TransferService.map("containerCode",j.request.get("CARRIERID")));
        if(!ok(r) || !(r.get("data") instanceof List) || ((List<?>)r.get("data")).size()!=1)throw new IllegalStateException("Cargo location unavailable");
        Map<String,Object> c=(Map<String,Object>)((List<?>)r.get("data")).get(0);
        if(!j.request.get("CARRIERID").equals(c.get("containerCode")) || !"1".equals(String.valueOf(c.get("inMapStatus"))))throw new IllegalStateException("Cargo context mismatch");
        if("1".equals(String.valueOf(c.get("isCarry")))) {
            if(j.vehicle.isEmpty())throw new IllegalStateException("Carrier vehicle unknown");
            robots=client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
            if(!ok(robots) || !(robots.get("data") instanceof List) || ((List<?>)robots.get("data")).size()!=1)throw new IllegalStateException("Carrier vehicle unavailable");
            Map<?,?> robot=(Map<?,?>)((List<?>)robots.get("data")).get(0);
            if(!j.vehicle.equals(String.valueOf(robot.get("robotId"))) || !j.request.get("CARRIERID").equals(robot.get("containerCode")))throw new IllegalStateException("Cargo vehicle mismatch");
            return j.vehicle;
        }
        if(!"0".equals(String.valueOf(c.get("isCarry"))))throw new IllegalStateException("Unknown cargo carry state");
        for(String key:Arrays.asList("SOURCEPORT","DESTPORT")){String port=text(j.request,key);if(WorkPorts.sameNode(port,CargoService.str(c,"nodeCode")))return port;}
        for(String port:WorkPorts.ALL)if(WorkPorts.sameNode(port,CargoService.str(c,"nodeCode")))return port;
        throw new IllegalStateException("Cargo not at a recognized port or confirmed vehicle");
    }
    private HostState.Delivery queue(HostState.Job j,String kind,int ceid,String location,int result,String suffix){
        HostState.Delivery d=new HostState.Delivery();d.command=(String)j.request.get("COMMANDID");d.vehicle=j.vehicle;d.kind=kind;d.ceid=ceid;d.location=location;d.result=result;d.key=d.command+":"+suffix;state.add(d);return d;
    }
    private void tsc(int ceid){HostState.Delivery d=new HostState.Delivery();d.key="tsc:"+state.data.sequence;d.kind="TSC";d.ceid=ceid;state.add(d);}
    // Test-profile ALID900002, never presented as an official SKI mapping.
    // Latch until a subsequent correlated successful lift action on this AMR;
    // cancelling the job alone does not prove physical handling recovered.
    private void handlingInterrupted(HostState.Job j){
        String action=text(j.request,"_abortedHandling");if(action.isEmpty())return;
        if(!"FAILURE".equals(j.request.get("_abortedHandlingOutcome")))return; // Completed or unproven != failed lift.
        String key="handling:"+j.vehicle+":"+j.request.get("COMMANDID")+":"+action;
        if(state.data.alarms.containsKey(key))return;
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key=key;
        d.vehicle=j.vehicle;d.command=(String)j.request.get("COMMANDID");d.set=true;
        d.alarmId=900002;d.category=6;d.operation=action;
        d.text=("UP_CONTAINER".equals(action)?"PICKUP_ABORTED_":"DOWN_CONTAINER".equals(action)?"DROPOFF_ABORTED_":"HANDLING_ABORTED_")+d.command;
        if(d.text.length()>80)d.text=d.text.substring(0,80);
        state.data.alarms.put(key,d);state.add(d);unitAlarm(d,true);
    }
    private void clearRecoveredHandling(HostState.Job recovered,Map<String,Object> event){
        String action=text(event,"actionType");
        if(!Arrays.asList("UP_CONTAINER","DOWN_CONTAINER").contains(action))return;
        boolean pickup="UP_CONTAINER".equals(action);
        if(pickup && !WorkPorts.vehicle((String)recovered.request.get("SOURCEPORT")).isEmpty())return;
        if(!(pickup?"1":"2").equals(text(event,"sequence"))
            || !WorkPorts.node((String)recovered.request.get(pickup?"SOURCEPORT":"DESTPORT")).equals(event.get("currentNode")))return;
        for(String key:new ArrayList<>(state.data.alarms.keySet())){
            HostState.Delivery alarm=state.data.alarms.get(key);
            if(key.startsWith("handling:") && recovered.vehicle.equals(alarm.vehicle)
                && !recovered.request.get("COMMANDID").equals(alarm.command)
                && (action.equals(alarm.operation) || "CARRY_STEP".equals(alarm.operation)))clearAlarm(key);
        }
    }
    private void alarm(Map<String,Object> e,boolean set){
        String unit=text(e,"robotId"),code=text(e,"warnCode"),command=text(e,"commandId");
        if(!set && code.isEmpty()){
            for(String key:new ArrayList<>(state.data.alarms.keySet()))if(key.startsWith(unit+":"+command+":"))clearAlarm(key);
            return;
        }
        String key=unit+":"+command+":"+code;
        if(!set){clearAlarm(key);return;}
        Map<String,Object> mapping=alarmMap.get(code);
        if(mapping==null){LOG.warning("Unmapped Fleet warning code: "+code);return;}
        if(state.data.alarms.containsKey(key))return;
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key="alarm:"+state.data.sequence;d.vehicle=unit;d.command=command;d.set=true;
        d.alarmId=((Number)mapping.get("alid")).longValue();d.category=((Number)mapping.get("category")).intValue();d.text=(String)mapping.get("text");
        boolean firstAlarm=!activeAlarm(d.alarmId,null),firstUnit=!activeAlarm(d.alarmId,d.vehicle);
        state.data.alarms.put(key,d);if(firstAlarm)state.add(d);if(firstUnit)unitAlarm(d,true);state.save();
    }
    private void clearAlarm(String key){
        HostState.Delivery previous=state.data.alarms.get(key);if(previous==null)return;
        state.data.alarms.remove(key);
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key="alarm:"+state.data.sequence;d.vehicle=previous.vehicle;d.command=previous.command;
        d.alarmId=previous.alarmId;d.category=previous.category;d.text=previous.text;d.set=false;
        if(!activeAlarm(d.alarmId,null))state.add(d);
        if(!activeAlarm(d.alarmId,d.vehicle))unitAlarm(d,false);state.save();
    }
    private boolean activeAlarm(long alid,String vehicle){
        for(HostState.Delivery a:state.data.alarms.values())if(a.alarmId==alid && (vehicle==null || vehicle.equals(a.vehicle)))return true;
        return false;
    }
    private void unitAlarm(HostState.Delivery source,boolean set){
        HostState.Delivery d=new HostState.Delivery();d.key="unit:"+state.data.sequence;d.kind="UNIT";d.ceid=set?702:701;d.vehicle=source.vehicle;
        d.alarmId=source.alarmId;d.text=source.text;state.add(d);
    }
    public synchronized Secs2 reportBody(HostState.Delivery d){synchronized(state){return HostProtocol.body(d,state.data.jobs.get(d.command),vehicleNames);}}
    static String text(Map<String,Object> x,String k){Object v=x.get(k);return v==null?"":v.toString();}
}
