package servostar.fleet.transfer;

import java.nio.file.*;
import java.util.*;

/** Reproduces live1.1.5: completed job disappears from jobQuery, nullable
 * mission fields omitted, cargo delivered and AMR idle at parking105. */
public final class RetainedCompletionTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static final class Fixture extends NativeHostTest.Harness {
        boolean missing,omitBindings,queryFailure,conflictingRow;
        int competingStatus=-1;
        Fixture(Path root)throws Exception {super(root,null);activeControlJob=true;rcsState=9;}
        @Override Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if(path.equals("jobQuery") && missing){
                if(queryFailure)return TransferService.map("success",false,"code","UNAVAILABLE","data",Collections.emptyList());
                if(body.containsKey("status") && String.valueOf(competingStatus).equals(String.valueOf(body.get("status"))))
                    return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("jobCode","OTHER_WORK")));
                if(conflictingRow && body.containsKey("jobCode"))
                    return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("jobCode","WRONG","status",30)));
                return TransferService.map("success",true,"code",null,"data",Collections.emptyList());
            }
            Map<String,Object> response=super.post(path,token,body);
            if(path.equals("robotQuery") && omitBindings){
                Map<?,?> row=(Map<?,?>)((List<?>)response.get("data")).get(0);
                row.remove("missionCode");row.remove("rcsMissionCode");row.remove("rcsMissionStatus");
            }
            return response;
        }
    }
    static Fixture pendingResume(Path root,boolean complete)throws Exception {
        Fixture f=new Fixture(root);PauseResumeTest.assign(f);
        f.engine.control("PAUSE","");f.rcsState=12;f.engine.tick();f.engine.control("RESUME","");
        if(complete)for(Map<String,Object> e:f.trace)f.observe(e);
        f.missing=true;f.omitBindings=true;f.robotNode="lay01-dist1-105";f.cargoNode="SECS_ST_97";
        return f;
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        Fixture actual=pendingResume(root,true);actual.engine.tick();
        HostState.Job done=actual.state.data.jobs.get("TR_TEST_001");
        check(done.phase.equals("COMPLETED") && actual.state.data.tsc.equals("AUTO"),"Missing completed row recovered from retained WCS and actual release");
        check(actual.count(210)==1 && actual.count(103)==1 && actual.count(610)==1 && actual.count(207)==1,"Resume/AUTO/release/completion reports each once");
        check(done.controlEvidence.equals("COMPLETED_RETAINED_WCS_RELEASED"),"Recovery source visible in status");
        check(actual.controls==2 && actual.submits==1,"No replay of transfer, pause or resume");
        actual.engine.tick();check(actual.count(207)==1,"Durable report keys prevent duplicate completion");
        Fixture noHistory=pendingResume(root,false);noHistory.engine.tick();
        check(noHistory.count(207)==0 && noHistory.count(210)==0,"Empty job rows plus free robot and cargo alone cannot manufacture completion");
        Fixture queryFailed=pendingResume(root,true);queryFailed.queryFailure=true;queryFailed.engine.tick();
        check(queryFailed.count(210)==0,"Query failure is not an archived/missing job");
        Fixture wrong=pendingResume(root,true);wrong.conflictingRow=true;wrong.engine.tick();
        check(wrong.count(210)==0,"Conflicting job row cannot fall through to missing-record recovery");
        Fixture wrongCargo=pendingResume(root,true);wrongCargo.cargoNode="SECS_ST_89";wrongCargo.engine.tick();
        check(wrongCargo.count(207)==0 && wrongCargo.count(210)==0,"Real cargo must be at destination");
        Fixture carried=pendingResume(root,true);carried.carried=true;carried.engine.tick();
        check(carried.count(210)==0,"Loaded AMR/cargo cannot pass release confirmation");
        Fixture bound=pendingResume(root,true);bound.omitBindings=false;bound.engine.tick();
        check(bound.count(210)==0,"A still-bound AMR cannot pass even if idle is reported");
        for(int status:new int[]{10,20,25,28,50,60}){
            Fixture competing=pendingResume(root,true);competing.competingStatus=status;competing.engine.tick();
            check(competing.count(210)==0,"Active/warning Fleet work blocks recovery status="+status);
        }
        Map<String,Object> req=HostProtocol.transfer(NativeHostTest.transferBody("TR_TEST_001"));
        check(!ControlStateVerifier.missingJob(TransferService.map("success",false,"data",Collections.emptyList())),"Failed empty query rejected");
        check(!ControlStateVerifier.missingJob(TransferService.map("success",true,"data","[]")),"Malformed data is not an empty list");
        check(!ControlStateVerifier.missingJob(TransferService.map("success",true)),"Missing data is not an empty list");
        Fixture gap=pendingResume(root,false);gap.journal.streamLost();gap.journal.streamReady();for(Map<String,Object> e:gap.trace)gap.observe(e);gap.engine.tick();
        check(gap.count(210)==0 && gap.count(207)==0,"A retained stream gap never becomes normal completion");
        check(Boolean.TRUE.equals(actual.service.stage(req,NativeHostTest.TOKEN,"completed").get("success")),"Public completion gate uses the same authenticated retained-history checks");
        System.out.println("PASS: "+checks+" retained completion, omitted-null API, active-work, context and no-replay checks; no live Fleet calls.");
    }
}
