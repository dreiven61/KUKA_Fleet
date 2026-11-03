package servostar.fleet.transfer;

import java.nio.file.*;
import java.util.*;

/** Physical handling admission and confirmed abort cleanup. Local fixtures;
 * never sends a command to the configured Fleet or a real vehicle. */
public final class HandlingControlTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static final class Fixture extends NativeHostTest.Harness {
        boolean brokenCargo,malformedRobot;
        Fixture(Path root,Path disk)throws Exception {super(root,disk);activeControlJob=true;rcsState=9;}
        @Override Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if("containerQuery".equals(path) && brokenCargo)return TransferService.map("success",false,"code","100001","data",null);
            if("robotQuery".equals(path) && body.containsKey("robotId") && malformedRobot)
                return TransferService.map("success",true,"code","0","data",Collections.emptyList());
            return super.post(path,token,body);
        }
        void at(String action,String status)throws Exception {
            PauseResumeTest.assign(this);
            for(Map<String,Object> e:trace){
                observe(e);
                if("UP_CONTAINER".equals(e.get("actionType")) && "ACTION_END".equals(e.get("status")))carried=true;
                if(action.equals(e.get("actionType")) && status.equals(e.get("status")))return;
            }
            throw new AssertionError("Missing trace stage "+action+status);
        }
        void release(){missionCode="";rcsCode="";rcsState=null;}
        void ackReports(){for(HostState.Delivery d:state.data.reports)d.state="ACKED";state.save();}
        HostState.Job job(){return state.data.jobs.get("TR_TEST_001");}
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        for(String action:Arrays.asList("UP_CONTAINER","DOWN_CONTAINER")){
            for(String command:Arrays.asList("CANCEL","PAUSE")){
                Fixture h=new Fixture(root,null);h.at(action,"ACTION_BEGIN");
                check(NativeHostTest.ack(h.engine.control(command,"TR_TEST_001"))==2,"Reject "+command+" during "+action);
                check(h.controls==0 && "AUTO".equals(h.state.data.tsc) && h.job().control.isEmpty(),"No mutation on lift rejection");
                HostState.Delivery reason=h.state.data.reports.stream().filter(d->d.ceid==9003).reduce((a,b)->b).get();
                check(reason.detail.equals("LIFT_OPERATION_ACTIVE:"+action),"Exact rejection reason "+action);
                check(h.count(105)==0 && h.count(209)==0,"No fake stopped report for rejected pause");
            }
        }
        Fixture travel=new Fixture(root,null);travel.at("UP_CONTAINER","ACTION_END");travel.brokenCargo=true;
        check(NativeHostTest.ack(travel.engine.control("PAUSE",""))==4,"Loaded travel PAUSE allowed");
        travel.rcsState=12;travel.engine.tick();
        check(travel.count(209)==1 && "PAUSED".equals(travel.state.data.tsc),"Loaded travel proves actual stop");
        travel.engine.control("RESUME","");travel.rcsState=9;travel.engine.tick();
        check(travel.count(210)==1 && "AUTO".equals(travel.state.data.tsc),"Loaded travel resumes");
        Fixture carry=new Fixture(root,null);PauseResumeTest.assign(carry);
        carry.rcsCode="C-TEST-6837-1";carry.robotNode="lay01-dist1-89";
        check(NativeHostTest.ack(carry.engine.control("PAUSE",""))==2 && carry.controls==0,"C-step closes callback-lag window");
        Fixture unknown=new Fixture(root,null);PauseResumeTest.assign(unknown);unknown.malformedRobot=true;
        check(NativeHostTest.ack(unknown.engine.control("PAUSE",""))==2 && unknown.controls==0,"Malformed current AMR fails closed");
        check(NativeHostTest.ack(unknown.engine.control("CANCEL","TR_TEST_001"))==2 && unknown.controls==0,"Targeted control fails closed without AMR evidence");
        Fixture gap=new Fixture(root,null);PauseResumeTest.assign(gap);gap.journal.streamLost();
        check(NativeHostTest.ack(gap.engine.control("PAUSE",""))==2 && gap.controls==0,"Unknown callback interval blocks pause");

        for(String action:Arrays.asList("UP_CONTAINER","DOWN_CONTAINER")){
            Path disk=Files.createTempDirectory("handling-abort-").resolve("state.json");
            Fixture h=new Fixture(root,disk);h.at(action,"ACTION_BEGIN");h.brokenCargo=true;
            check(NativeHostTest.ack(h.engine.control("ABORT","TR_TEST_001"))==4 && h.lastMode.equals("FORCE"),"Lift ABORT sent despite unstable cargo node "+action);
            h.observe(h.event("TASK_COMPLETED",false));h.engine.tick();
            check(h.count(201)==0 && h.count(207)==0 && h.state.data.alarms.isEmpty(),"Failed task is not abort completion");
            Map<String,Object> failedAction=h.event("ACTION_END",false);failedAction.put("actionType",action);h.observe(failedAction);
            h.observe(h.event("CANCEL_COMPLETED",true));
            // Simulate confirmed physical attachment after lift interruption.
            h.carried=true;h.engine.tick();
            check(h.count(201)==1 && h.count(610)==0 && "ACTIVE".equals(h.job().phase),"Await actual vehicle release");
            HostState.Delivery alarm=h.state.data.reports.stream().filter(d->d.kind.equals("ALARM")).findFirst().get();
            check(alarm.alarmId==900002 && alarm.set && alarm.command.equals("TR_TEST_001"),"Test ALID bound to aborted handling");
            check(h.state.data.reports.indexOf(alarm)<index(h,201),"Handling failure precedes abort completion");
            check((h.engine.reportBody(alarm).get(0).getByte(0)&255)==0x86,"S5F1 set category6");
            h.release();h.engine.tick();
            check(h.count(610)==1 && "WAIT_REPORT_ACK".equals(h.job().controlState),"Release observed; retain job until report ACK");
            HostState.Delivery released=h.state.data.reports.stream().filter(d->d.ceid==610).findFirst().get();
            check(released.port.equals(action.equals("UP_CONTAINER")?"PORT_89":"PORT_97"),"Unassigned transfer port reflects handling end");
            check(index(h,201)<index(h,610) && h.count(207)==0 && h.count(603)==0 && h.count(607)==0,"No fabricated transport/handling completion");
            // Crash/restart in the final report phase must not replay FORCE or
            // lose the latched alarm, history, report identity or carrier.
            Fixture restored=new Fixture(root,disk);restored.release();restored.carried=true;restored.brokenCargo=true;
            restored.engine.tick();check(restored.controls==0 && "ACTIVE".equals(restored.job().phase),"Restart waits for pending reports, no replay");
            restored.ackReports();restored.engine.tick();
            check("ABORTED".equals(restored.job().phase) && restored.job().control.isEmpty(),"Clear active job only after failure/result/release ACK");
            check(restored.state.data.jobs.containsKey("TR_TEST_001") && restored.carried && restored.state.data.alarms.size()==1,"Preserve history, carried cargo and unresolved handling alarm");
            for(int i=0;i<3;i++)restored.engine.tick();
            check(restored.count(201)==1 && restored.count(610)==1,"Abort reports are not duplicated after restart");
            checkAlarmRecovery(restored,action);
        }
        Fixture fail=new Fixture(root,null);fail.at("UP_CONTAINER","ACTION_BEGIN");
        fail.engine.control("ABORT","TR_TEST_001");fail.observe(fail.event("CANCEL_COMPLETED",false));fail.engine.tick();
        check(fail.count(202)==1 && fail.count(201)==0 && fail.state.data.alarms.isEmpty() && fail.job().phase.equals("ACTIVE"),"Failed ABORT is202, not a made-up handling completion");
        Fixture completedLift=new Fixture(root,null);completedLift.at("UP_CONTAINER","ACTION_BEGIN");
        completedLift.engine.control("ABORT","TR_TEST_001");
        Map<String,Object> successfulAction=completedLift.event("ACTION_END",true);successfulAction.put("actionType","UP_CONTAINER");completedLift.observe(successfulAction);
        completedLift.carried=true;completedLift.observe(completedLift.event("CANCEL_COMPLETED",true));completedLift.engine.tick();
        check(completedLift.count(201)==1 && completedLift.state.data.alarms.isEmpty(),"Lift finishing normally before ABORT never raises handling failure");
        Fixture batch=new Fixture(root,null);PauseResumeTest.assign(batch);
        HostState.Job second=new HostState.Job();second.phase="ACTIVE";
        second.request=new LinkedHashMap<>(batch.job().request);second.request.put("COMMANDID","SECOND");second.request.put("CARRIERID","OTHER_CARGO");
        batch.state.data.jobs.put("SECOND",second);batch.journal.reserve(second.request);batch.journal.accepted("SECOND");
        for(Map<String,Object> original:batch.trace){
            Map<String,Object> e=new LinkedHashMap<>(original);e.put("commandId","SECOND");e.put("carrierId","OTHER_CARGO");e.put("robotId","2");e.put("instanceId","6838");
            batch.journal.observe(e);batch.engine.observe(e);
            if("ACTION_BEGIN".equals(e.get("status")) && "UP_CONTAINER".equals(e.get("actionType")))break;
        }
        check(NativeHostTest.ack(batch.engine.control("PAUSE",""))==2 && batch.controls==0 && batch.job().control.isEmpty(),"One lifting job prevents partial global PAUSE mutation");
        System.out.println("PASS: "+checks+" lift admission, loaded travel and confirmed abort/report/release/restart checks; local fixtures only.");
    }
    static int index(Fixture h,int ceid){for(int i=0;i<h.state.data.reports.size();i++)if(h.state.data.reports.get(i).ceid==ceid)return i;return -1;}
    static void checkAlarmRecovery(Fixture h,String action)throws Exception {
        // New correlated physical action, not job deletion, resolves the latch.
        HostState.Job next=new HostState.Job();next.phase="ACTIVE";next.request=new LinkedHashMap<>(h.job().request);
        next.request.put("COMMANDID","RECOVERY");next.vehicle="1";next.instance="6838";
        h.state.data.jobs.put("RECOVERY",next);
        Map<String,Object> end=h.event("ACTION_END",false);end.put("commandId","RECOVERY");end.put("instanceId","6838");end.put("actionType",action);
        h.engine.observe(end);check(h.state.data.alarms.size()==1,"Failed action does not clear handling alarm");
        end.put("responseId",UUID.randomUUID().toString());end.put("success",true);end.put("resultCode","0");end.put("actionType",action.equals("UP_CONTAINER")?"DOWN_CONTAINER":"UP_CONTAINER");
        h.engine.observe(end);check(h.state.data.alarms.size()==1,"Different action does not clear handling alarm");
        end.put("responseId",UUID.randomUUID().toString());end.put("actionType",action);end.put("sequence",action.equals("UP_CONTAINER")?1:2);
        end.put("currentNode","lay01-dist1-999");h.engine.observe(end);
        check(h.state.data.alarms.size()==1,"Wrong handling station cannot clear alarm");
        end.put("responseId",UUID.randomUUID().toString());end.put("currentNode",action.equals("UP_CONTAINER")?"lay01-dist1-89":"lay01-dist1-97");h.engine.observe(end);
        check(h.state.data.alarms.isEmpty() && h.count(701)==1,"Verified subsequent same lift action clears alarm once");
        HostState.Delivery clear=h.state.data.reports.stream().filter(d->d.kind.equals("ALARM") && !d.set).findFirst().get();
        check((h.engine.reportBody(clear).get(0).getByte(0)&255)==6 && clear.alarmId==900002,"Clear uses same ALID and category");
    }
}
