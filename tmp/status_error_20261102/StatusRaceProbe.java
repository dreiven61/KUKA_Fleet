package servostar.fleet.transfer;

import java.util.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Diagnostic fixtures only. Uses the backed-up deployed 1.3.0 classes; no network. */
public final class StatusRaceProbe {
    static final class Fleet implements TransferService.FleetClient {
        String scenario; int calls;
        Fleet(String value){ scenario=value; }
        public Map<String,Object> post(String path,String token,Map<String,Object> body){
            calls++;
            List<Map<String,Object>> rows=new ArrayList<>();
            boolean robotOwner=scenario.equals("stable-loaded") || scenario.equals("owner-before-lift");
            boolean carried=scenario.equals("stable-loaded") || scenario.equals("cargo-newer-than-robot");
            if(path.equals("robotQuery")){
                for(int id=1;id<=3;id++)rows.add(TransferService.map("robotId",String.valueOf(id),"status",id==2?4:3,
                    "occupyStatus",id==2?1:0,"containerCode",id==2&&robotOwner?"SIM_CARGO_001":"",
                    "liftStatus",id==2&&scenario.equals("stable-loaded")?1:0,"missionCode",id==2?"ACTIVE_JOB":"","rcsMissionCode",""));
            }else if(path.equals("containerQueryAll")){
                rows.add(TransferService.map("containerCode","SIM_CARGO_001","nodeCode",WorkPorts.containerNode("PORT_90"),
                    "inMapStatus",1,"isCarry",carried?1:0,"emptyFullStatus",1));
            }else if(!path.equals("jobQuery"))throw new AssertionError("Unexpected API "+path);
            return TransferService.map("success",true,"code","0","data",rows);
        }
    }
    public static void main(String[] args)throws Exception{
        int checks=0;
        for(String scenario:Arrays.asList("stable-unloaded","cargo-newer-than-robot","owner-before-lift","stable-loaded")){
            for(int svid:new int[]{25,58,23,21}){
                Fleet fixture=new Fleet(scenario);
                SelectedStatusService service=new SelectedStatusService(fixture,new HostState(null),"fixture-no-network");
                boolean stable=scenario.startsWith("stable-");
                try{
                    Secs2 reply=service.read(list(uint2(svid)));
                    if(!stable)throw new AssertionError("Unexpected success "+scenario);
                    System.out.println(scenario+" SV"+svid+" OK entries="+reply.get(0).size()+" APIreads="+fixture.calls);
                }catch(IllegalStateException expected){
                    if(stable)throw expected;
                    System.out.println(scenario+" SV"+svid+" FAIL "+expected.getClass().getSimpleName()+": "+expected.getMessage()+" APIreads="+fixture.calls);
                }
                checks++;
            }
        }
        System.out.println("PASS "+checks+" diagnostic scenarios; fixtures only, no Fleet operations.");
    }
}
