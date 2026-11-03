package servostar.fleet.transfer;

import java.nio.file.*;
import java.util.*;

/** Live 6886 regression: second PAUSE is RCS PAUSED, but containerQuery
 * returns100001 (No such node in graph) while the carrier is on the AMR. */
public final class CarriedPauseResumeTest {
    static int checks;
    static void check(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    static final class Fixture extends NativeHostTest.Harness {
        boolean brokenCargo,throwCargo;
        int cargoQueries;
        String wrongField="";
        Fixture(Path root,Path disk)throws Exception {super(root,disk);activeControlJob=true;rcsState=9;}
        @SuppressWarnings("unchecked")
        @Override Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception {
            if("containerQuery".equals(path)){
                cargoQueries++;
                if(throwCargo)throw new java.io.IOException("Read timeout");
                if(brokenCargo)return TransferService.map("success",false,"code","100001","data",null,
                    "message","No such node in the graph.[-lay01-dist1-90]");
            }
            Map<String,Object> r=super.post(path,token,body);
            if("robotQuery".equals(path) && !wrongField.isEmpty()){
                Map<String,Object> robot=(Map<String,Object>)((List<?>)r.get("data")).get(0);
                robot.put(wrongField,"WRONG");
            }
            return r;
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        Fixture h=new Fixture(root,null);PauseResumeTest.assign(h);
        h.engine.control("PAUSE","");h.rcsState=12;h.engine.tick();
        check("PAUSED".equals(h.state.data.tsc) && h.count(209)==1,"First pause before pickup");
        h.engine.control("RESUME","");h.rcsState=9;h.engine.tick();
        check("AUTO".equals(h.state.data.tsc) && h.count(210)==1,"First resume before pickup");
        h.loaded();h.brokenCargo=true;
        for(int cycle=0;cycle<3;cycle++){
            h.engine.control("PAUSE","");h.rcsState=12;int reads=h.cargoQueries;h.engine.tick();
            check(h.cargoQueries==reads,"Loaded pause avoids broken cargo endpoint "+cycle);
            check("PAUSED".equals(h.state.data.tsc) && h.count(209)==cycle+2,"Repeated loaded pause settles "+cycle);
            HostState.Delivery report=h.state.data.reports.stream().filter(d->d.ceid==209).reduce((a,b)->b).get();
            check("1".equals(report.location),"209 reports confirmed carrier vehicle "+cycle);
            h.engine.control("RESUME","");h.rcsState=9;h.engine.tick();
            check("AUTO".equals(h.state.data.tsc) && h.count(210)==cycle+2,"Repeated loaded resume settles "+cycle);
            check(h.cargoQueries==reads,"Loaded resume avoids broken cargo endpoint "+cycle);
        }
        check(h.controls==8 && h.submits==1 && h.count(207)==0,"No replay or invented completion");
        // Timeout on the obsolete endpoint cannot delay either control.
        h.throwCargo=true;h.engine.control("PAUSE","");h.rcsState=12;h.engine.tick();
        h.engine.control("RESUME","");h.rcsState=9;h.engine.tick();
        check("AUTO".equals(h.state.data.tsc) && h.count(209)==5 && h.count(210)==5,"Timeout-independent loaded control");

        Path file=Files.createTempDirectory("loaded-pause-").resolve("state.json");
        Fixture before=new Fixture(root,file);PauseResumeTest.assign(before);before.loaded();
        before.engine.control("PAUSE","");before.state.data.jobs.get("TR_TEST_001").controlEvidence="RCS_PAUSED";before.state.save();
        Fixture restored=new Fixture(root,file);restored.loaded();restored.rcsState=12;restored.brokenCargo=true;
        restored.engine.tick();
        check("PAUSED".equals(restored.state.data.tsc) && restored.count(209)==1 && restored.controls==0,"Existing accepted second pause recovers by reads only");
        for(int i=0;i<3;i++)restored.engine.tick();
        check(restored.count(209)==1 && restored.count(105)==1,"Recovery reports once");

        for(String field:Arrays.asList("robotId","missionCode","rcsMissionCode","containerCode","liftStatus")){
            Fixture bad=new Fixture(root,null);PauseResumeTest.assign(bad);bad.loaded();bad.brokenCargo=true;
            bad.engine.control("PAUSE","");bad.rcsState=12;bad.wrongField=field;
            try{bad.engine.tick();}catch(IllegalStateException expected){}
            check(bad.count(209)==0 && "PAUSING".equals(bad.state.data.tsc),"Wrong carrier binding cannot confirm "+field);
        }
        // Snapshot helper also rejects duplicate rows and exact-workflow mismatch.
        HostState.Job job=h.state.data.jobs.get("TR_TEST_001");
        Map<String,Object> robot=TransferService.map("robotId","1","missionCode","TR_TEST_001",
            "rcsMissionCode","W-TEST-68370-1","containerCode","SIM_CARGO_001","liftStatus",1);
        check(ControlStateVerifier.carriedLocation(job,PauseResumeTest.reply(robot)).isEmpty(),"No partial workflow match");
        robot.put("rcsMissionCode","W-TEST-6837-1");
        check(ControlStateVerifier.carriedLocation(job,TransferService.map("success",true,"data",Arrays.asList(robot,robot))).isEmpty(),"Duplicate carrier rows rejected");
        check(ControlStateVerifier.carriedLocation(job,TransferService.map("success",false,"data",Arrays.asList(robot))).isEmpty(),"Failed robot response rejected");
        System.out.println("PASS: "+checks+" carried pause/resume query-failure, repeat-cycle and persisted recovery checks; no live Fleet commands.");
    }
}
