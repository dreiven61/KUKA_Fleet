package servostar.fleet.transfer;

import java.util.*;
import java.io.File;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Replays saved API responses against deployed 1.3.0 code. No network. */
public final class CapturedStatusReplay {
    static final ObjectMapper JSON=new ObjectMapper();
    static final class CapturedFleet implements TransferService.FleetClient {
        final List<Map<String,Object>> records; final int first; int reads;
        CapturedFleet(List<Map<String,Object>> source,int firstId){records=source;first=firstId;}
        @SuppressWarnings("unchecked")
        public Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception{
            for(Map<String,Object> row:records){
                int id=((Number)row.get("id")).intValue();
                if(id<first || id>=first+8 || !String.valueOf(row.get("url")).endsWith("/"+path))continue;
                if(path.equals("jobQuery")){
                    List<Map<String,Object>> request=JSON.readValue(String.valueOf(row.get("body")),List.class);
                    if(!String.valueOf(request.get(0).get("status")).equals(String.valueOf(body.get("status"))))continue;
                }
                reads++;return JSON.readValue(String.valueOf(row.get("response")),Map.class);
            }
            throw new AssertionError("Missing captured response: "+path+" "+body+" first="+first);
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        List<Map<String,Object>> records=JSON.readValue(new File(args[0]),List.class);
        for(int first:new int[]{72190,72198})for(int svid:new int[]{25,58,23}){
            CapturedFleet fleet=new CapturedFleet(records,first);
            // An empty retained ledger lets the captured active Fleet job be
            // represented as external; no historical Native state is invented.
            SelectedStatusService service=new SelectedStatusService(fleet,new HostState(null),"not-used-no-network");
            try{
                Secs2 result=service.read(list(uint2(svid)));
                if(first==72190)throw new AssertionError("Inconsistent capture unexpectedly succeeded");
                System.out.println("CAPTURE "+first+".."+(first+7)+" SV"+svid+" OK entries="+result.get(0).size()+" reads="+fleet.reads);
            }catch(IllegalStateException expected){
                if(first!=72190 || !expected.getMessage().equals("Carrier snapshots disagree; requery before dispatch"))throw expected;
                System.out.println("CAPTURE "+first+".."+(first+7)+" SV"+svid+" FAIL "+expected.getMessage()+" reads="+fleet.reads);
                if(svid==25)expected.printStackTrace(System.out);
            }
        }
        System.out.println("PASS: 6 captured-response replay checks against deployed JAR; no live Fleet calls; retained ledger not reconstructed.");
    }
}
