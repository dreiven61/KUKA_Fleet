package servostar.fleet.transfer;

import java.nio.file.*;
import java.util.*;
import org.springframework.http.ResponseEntity;

/** Explicit adoption of the real failure shape: old ABORT, unarmed, cargo still on AMR.
 * All Fleet replies are fixtures; no production transport or HTTP is sent. */
public final class HistoricalRecoveryTest {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final String ID="ABORTED_001", CARGO="SIM_CARGO_001", SOURCE="lay01-dist1-97", WAIT="lay01-dist1-105";
    static final class Harness {
        final LoadedRecoveryTest.Fleet fleet=new LoadedRecoveryTest.Fleet();
        final HostState state;
        final HostState.Job job;
        final NativeHostEngine engine;
        Harness()throws Exception{this(null);}
        Harness(Path path)throws Exception{
            state=new HostState(path);job=LoadedRecoveryTest.aborted(state);job.recoveryArmed=false;
            job.controlEvidence="CANCEL_COMPLETED_RELEASED_AND_REPORTED";
            for(int ceid:new int[]{201,610}){
                HostState.Delivery d=new HostState.Delivery();d.command=ID;d.ceid=ceid;d.kind=ceid==201?"TRANSFER":"VEHICLE";
                d.key="confirmed:"+ceid;d.state="ACKED";state.add(d);
            }
            state.save();
            engine=new NativeHostEngine(new TransferService(fleet),fleet,state,"fixture",Collections.emptyMap(),Collections.emptyMap(),9000);
            engine.configureRecovery(Collections.singletonMap("2",WAIT));
        }
        Map<String,Object> review(boolean apply)throws Exception{return engine.recoverLoadedAbort(ID,CARGO,"2",SOURCE,WAIT,apply);}
    }
    interface Edit {void apply(Harness h);}
    static void blocked(Edit edit,String reason)throws Exception {
        Harness h=new Harness();edit.apply(h);
        try{h.review(true);throw new AssertionError("Admitted: "+reason);}
        catch(CargoService.Rejected expected){check(expected.getMessage().equals(reason),"Exact reason: "+reason+" vs "+expected.getMessage());}
        check(!h.job.recoveryArmed && h.fleet.mutations==0,"Rejected review neither arms nor moves: "+reason);
    }
    static Map<String,Object> body(boolean apply){return TransferService.map("commandId",ID,"carrierId",CARGO,"vehicleId","2","sourceNode",SOURCE,"waitNode",WAIT,"apply",apply);}
    static void field(Object object,String name,Object value)throws Exception {
        java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    static NativeHostService host(Harness h)throws Exception {
        NativeHostService s=new NativeHostService(new TransferService(h.fleet),h.fleet,null);
        field(s,"token","fixture");field(s,"engine",h.engine);return s;
    }
    public static void main(String[] args)throws Exception {
        Path disk=Files.createTempDirectory("historical-loaded-abort-").resolve("state.json");Harness h=new Harness(disk);
        h.engine.tick();check(h.fleet.mutations==0,"Configuration alone never adopts an old ABORT");
        byte[] before=Files.readAllBytes(disk);
        Map<String,Object> preview=h.review(false);
        check(Boolean.TRUE.equals(preview.get("success")) && Boolean.FALSE.equals(preview.get("armed")),"Read-only review succeeds with explicit non-armed result");
        check(!h.job.recoveryArmed && h.fleet.mutations==0 && Arrays.equals(before,Files.readAllBytes(disk)),"Preview leaves journal and Fleet unchanged");
        Map<String,Object> applied=h.review(true);
        check(Boolean.TRUE.equals(applied.get("armed")) && !Boolean.TRUE.equals(applied.get("completed")),"Arming never reports completed motion");
        check(h.fleet.mutations==0 && SOURCE.equals(h.job.recoveryReviewSource) && WAIT.equals(h.job.recoveryReviewNode),"Persist exact reviewed identity before any mission");
        try{h.review(true);throw new AssertionError("Duplicate adoption");}catch(CargoService.Rejected expected){check(expected.getMessage().equals("RECOVERY_ALREADY_INITIALIZED_QUERY_STATUS"),"Duplicate review queries status, never resets");}
        HostState reloaded=new HostState(disk);
        HostState.Job restored=reloaded.data.jobs.get(ID);
        check(restored.recoveryArmed && SOURCE.equals(restored.recoveryReviewSource) && WAIT.equals(restored.recoveryReviewNode),"Reviewed intent survives restart");
        LoadedRecovery r=LoadedRecoveryTest.configured(reloaded,h.fleet);r.tick();r.tick();
        check(h.fleet.mutations==1 && restored.recoveryState.equals("MOVING"),"Restart executes one wait mission, no replay");
        check(restored.phase.equals("ABORTED") && restored.instance.equals("6889"),"Original terminal job and instance preserved");
        check(reloaded.status().toString().contains("recoveryArmed=true"),"Status shows armed gate");
        blocked(x->x.job.phase="COMPLETED","ABORT_COMPLETION_UNCONFIRMED");
        blocked(x->x.job.control="ABORT","ABORT_COMPLETION_UNCONFIRMED");
        blocked(x->x.job.controlState="WAIT_REPORT_ACK","ABORT_COMPLETION_UNCONFIRMED");
        blocked(x->x.job.controlEvidence="","ABORT_COMPLETION_UNCONFIRMED");
        blocked(x->x.job.instance="","ABORT_COMPLETION_UNCONFIRMED");
        blocked(x->x.job.vehicle="1","ABORT_IDENTITY_MISMATCH");
        blocked(x->x.job.request.put("CARRIERID","OTHER"),"ABORT_IDENTITY_MISMATCH");
        blocked(x->x.state.data.tsc="PAUSED","TSC_NOT_AUTO");
        blocked(x->x.engine.configureRecovery(Collections.emptyMap()),"WAIT_NODE_CONFIG_MISMATCH");
        blocked(x->x.job.request.put("_abortedHandlingOutcome","FAILURE"),"HANDLING_FAILED_REQUIRES_REVIEW");
        blocked(x->x.state.data.reports.get(0).state="PENDING","ABORT_REPORT_NOT_ACKED");
        blocked(x->x.state.data.reports.remove(0),"ABORT_REPORT_EVIDENCE_MISSING");
        blocked(x->x.state.data.reports.remove(1),"ABORT_REPORT_EVIDENCE_MISSING");
        blocked(x->{HostState.Job other=new HostState.Job();other.phase="COMPLETED";other.request.put("CARRIERID",CARGO);x.state.data.jobs.put("NEWER",other);},"NEWER_CARRIER_JOB_EXISTS");
        blocked(x->{HostState.Job other=new HostState.Job();other.phase="UNCERTAIN";other.vehicle="2";other.request.put("CARRIERID","OTHER");x.state.data.jobs.put("FOREIGN",other);},"OTHER_ACTIVE_OR_UNCERTAIN_JOB");
        blocked(x->x.fleet.wrongOwner=true,"CARRIER_NOT_ON_SOURCE_AMR");
        blocked(x->x.fleet.status="4","SOURCE_AMR_NOT_IDLE");
        blocked(x->x.fleet.occupy="1","SOURCE_AMR_NOT_IDLE");
        blocked(x->x.fleet.mission="OTHER","SOURCE_AMR_NOT_IDLE");
        blocked(x->x.fleet.node="lay01-dist1-96","SOURCE_NODE_CHANGED");
        blocked(x->x.fleet.busyJob=true,"SOURCE_AMR_HAS_FLEET_WORK");
        blocked(x->x.fleet.inventoryLoaded=false,"CARRIED_INVENTORY_UNCONFIRMED");
        blocked(x->x.fleet.occupied=true,"WAIT_NODE_OCCUPIED");
        blocked(x->x.job.recoveryState="UNCERTAIN","RECOVERY_ALREADY_INITIALIZED_QUERY_STATUS");
        blocked(x->x.job.recoveryMission="EXISTING","RECOVERY_ALREADY_INITIALIZED_QUERY_STATUS");
        for(String changed:new String[]{"source","wait","occupied","busy"}){
            Harness x=new Harness();x.review(true);
            if(changed.equals("source"))x.fleet.node="lay01-dist1-96";
            if(changed.equals("wait"))x.engine.configureRecovery(Collections.singletonMap("2","lay01-dist1-104"));
            if(changed.equals("occupied"))x.fleet.occupied=true;
            if(changed.equals("busy"))x.fleet.busyJob=true;
            x.engine.tick();check(x.fleet.mutations==0 && x.job.recoveryState.equals("BLOCKED"),"Recheck before send blocks changed "+changed);
        }
        Harness lost=new Harness();lost.review(true);lost.fleet.lost=true;lost.engine.tick();
        check(lost.fleet.mutations==1 && lost.job.recoveryState.equals("UNCERTAIN"),"Lost wait reply preserved");
        try{lost.review(true);throw new AssertionError("Uncertain replay");}catch(CargoService.Rejected expected){checks++;}
        lost.engine.tick();check(lost.fleet.mutations==1,"Review never replays an uncertain wait move");
        Harness api=new Harness();NativeHostService service=host(api);NativeHostController controller=new NativeHostController(service);
        check(controller.recoverLoadedAbort("wrong",body(false)).getStatusCodeValue()==401,"HTTP review authenticates");
        check(controller.recoverLoadedAbort("fixture",Collections.emptyMap()).getStatusCodeValue()==400,"Incomplete review rejected");
        Map<String,Object> bad=body(true);bad.put("apply","true");check(controller.recoverLoadedAbort("fixture",bad).getStatusCodeValue()==400,"String apply cannot initiate motion");
        Map<String,Object> extra=body(true);extra.put("unexpected",true);check(controller.recoverLoadedAbort("fixture",extra).getStatusCodeValue()==400,"Unexpected fields rejected");
        check(controller.recoverLoadedAbort("fixture",body(false)).getStatusCodeValue()==200 && !api.job.recoveryArmed,"HTTP preview available without SECS connection");
        ResponseEntity<Map<String,Object>> disconnected=controller.recoverLoadedAbort("fixture",body(true));
        check(disconnected.getStatusCodeValue()==409 && disconnected.getBody().get("code").equals("SECS_NOT_COMMUNICATING") && !api.job.recoveryArmed,"Apply requires communicating host");
        field(service,"communicating",true);
        check(controller.recoverLoadedAbort("fixture",body(true)).getStatusCodeValue()==200 && api.job.recoveryArmed && api.fleet.mutations==0,"HTTP explicit review durably arms only");
        check(controller.recoverLoadedAbort("fixture",body(true)).getStatusCodeValue()==409,"HTTP duplicate never resets");
        Map<String,Object> status=service.status("fixture");
        check(status.get("version").equals("1.5.2") && ((Map<?,?>)status.get("loadedAbortWaitNodes")).get("2").equals(WAIT),"Actual runtime version and loaded mapping exposed");
        ((Map<?,?>)status.get("loadedAbortWaitNodes")).clear();
        check(service.status("fixture").toString().contains(WAIT),"Status cannot mutate live mapping");
        System.out.println("PASS: "+checks+" explicit historical ABORT recovery, auth, preview, ownership and no-replay checks; fixtures only.");
    }
}
