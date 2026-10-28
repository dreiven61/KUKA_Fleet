package servostar.fleet.transfer;

import java.nio.file.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Loopback-only equipment fixture for the actual .NET Secs4Net client.
 * Replays recorded WCS facts into an in-memory mock; never opens a Fleet URL. */
public final class NativeInteropFixture {
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]),ready=Paths.get(args[1]);
        int port;try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        CargoTest.Harness h=new CargoTest.Harness(root);
        Path folder=Files.createTempDirectory("native-interop-"),config=folder.resolve("config.json");
        new ObjectMapper().writeValue(config.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",3,"authorization",NativeHostTest.TOKEN,"journal",folder.resolve("state.json").toString()));
        System.setProperty("servostar.secs.config",config.toString());
        NativeHostService server=new NativeHostService(h.service,h.client,new WcsStatusMonitor(h.journal));
        try{
            server.start();Files.write(ready,Integer.toString(port).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            NativeHostTest.waitFor(()->h.submits==1,".NET sent transfer once");
            for(Map<String,Object> event:h.trace)h.journal.observe(event);
            h.cargoNode="SECS_ST_97";
            h.containers.get("SIM_CARGO_001").put("nodeCode","SECS_ST_97");
            long deadline=System.currentTimeMillis()+35000;
            while(System.currentTimeMillis()<deadline){
                Map<?,?> state=(Map<?,?>)server.status(NativeHostTest.TOKEN).get("state");
                if(((List<?>)state.get("reports")).isEmpty() && Files.readAllBytes(folder.resolve("state.json")).length>2000){
                    String journal=new String(Files.readAllBytes(folder.resolve("state.json")),java.nio.charset.StandardCharsets.UTF_8);
                    if(journal.contains("\"phase\":\"COMPLETED\"")){System.out.println("PASS: Java equipment -> Secs4Net: 14 acknowledged reports, one submit.");return;}
                }
                Thread.sleep(100);
            }
            throw new AssertionError(".NET did not acknowledge the final report");
        }finally{server.close();}
    }
}
