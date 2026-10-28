package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class TransferTest {
    private static int checks;
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    static Map<String,Object> request(String id) {
        return TransferService.map("COMMANDID",id,"PRIORITY",1,"REPLACE",0,"CARRIERID","SIM_CARGO_001","SOURCEPORT","PORT_97","DESTPORT","PORT_89");
    }
    static void rejects(Map<String,Object> request) {
        try { TransferService.buildMission(request); throw new AssertionError("Invalid request accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    static Map<String,Object> cargo(String id,String port){return TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("containerCode",id,"nodeCode",WorkPorts.containerNode(port),"inMapStatus",1,"isCarry",0,"containerModelCode","SIM_TRAY_001")));}
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        Map<String,Object> mission = TransferService.buildMission(request("TR_TEST_001"));
        List<?> steps = (List<?>)mission.get("missionData");
        check(steps.size()==2,"Two steps");
        check("lay01-dist1-97".equals(((Map<?,?>)steps.get(0)).get("position")),"From 97");
        check("UP_CONTAINER".equals(((Map<?,?>)steps.get(0)).get("actionType")),"Pick first");
        check("lay01-dist1-89".equals(((Map<?,?>)steps.get(1)).get("position")),"To 89");
        check("DOWN_CONTAINER".equals(((Map<?,?>)steps.get(1)).get("actionType")),"Drop second");
        Map<String,Object> decoded = json.readValue(json.writeValueAsBytes(mission),Map.class);
        check(decoded.get("missionData") instanceof List,"JSON object array, not text");
        check(decoded.get("robotIds") instanceof List && ((List<?>)decoded.get("robotIds")).equals(Arrays.asList("1","2","3")),"Fleet automatic dispatch among existing robots 1/2/3");
        check(((List<?>)decoded.get("robotModels")).equals(Arrays.asList("KMP1500I")),"Only eligible KMP1500I model requested");
        Map<String,Object> forward=request("TR_FORWARD"); forward.put("SOURCEPORT","PORT_89"); forward.put("DESTPORT","PORT_97");
        check("lay01-dist1-89".equals(((Map<?,?>)((List<?>)TransferService.buildMission(forward).get("missionData")).get(0)).get("position")),"Reverse mapping");
        for (String field : Arrays.asList("COMMANDID","CARRIERID","SOURCEPORT","DESTPORT","PRIORITY","REPLACE")) {
            Map<String,Object> missing=request("MISSING"); missing.remove(field); rejects(missing);
        }
        for (Object[] change : new Object[][]{{"SOURCEPORT","PORT_999"},{"DESTPORT","PORT_97"},{"CARRIERID","bad id"},{"PRIORITY",0},{"PRIORITY",100},{"PRIORITY",1.5},{"REPLACE",1},{"COMMANDID","bad id"},{"url","http://example.invalid"}}) {
            Map<String,Object> invalid=request("INVALID"); invalid.put((String)change[0],change[1]); rejects(invalid);
        }
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger queried = new AtomicInteger();
        HttpServer mock=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        mock.createContext("/interfaces/api/amr/",exchange->{
            byte[] response;
            if (!"test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                response="{}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(401,response.length);
            } else if (exchange.getRequestURI().getPath().endsWith("robotQuery")) {
                queried.incrementAndGet(); response="{\"success\":true,\"code\":\"0\",\"data\":[]}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,response.length);
            } else if (exchange.getRequestURI().getPath().endsWith("containerQueryAll")) {
                response=json.writeValueAsBytes(cargo("SIM_CARGO_001","PORT_97")); exchange.sendResponseHeaders(200,response.length);
            } else {
                Map<?,?> received=json.readValue(exchange.getRequestBody(),Map.class);
                if (!(received.get("missionData") instanceof List)) throw new AssertionError("Wire array missing");
                submitted.incrementAndGet(); response="{\"success\":true,\"code\":\"0\",\"data\":null}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,response.length);
            }
            exchange.getResponseBody().write(response); exchange.close();
        });
        mock.start();
        try {
            FleetHttpClient http=new FleetHttpClient("http://127.0.0.1:"+mock.getAddress().getPort()+"/interfaces/api/amr/");
            TransferService service=new TransferService(http);
            check(Boolean.TRUE.equals(service.preview(request("PREVIEW"),"test-token").get("success")),"Preview accepted");
            check(submitted.get()==0,"Preview never submits");
            check(Boolean.FALSE.equals(service.submit(request("ONCE"),"test-token").get("completed")),"Acceptance is not completion");
            check(submitted.get()==1,"One HTTP submission");
            try { service.submit(request("ONCE"),"test-token"); throw new AssertionError("Duplicate submitted"); } catch(IllegalArgumentException expected) { checks++; }
            check(submitted.get()==1,"No duplicate POST");
            TransferController controller=new TransferController(service);
            check(controller.submit(null,request("NO_AUTH")).getStatusCodeValue()==401,"Missing auth rejected");
            check(controller.submit("wrong",request("BAD_AUTH")).getStatusCodeValue()==401,"Wrong auth rejected");
            check(submitted.get()==1,"Auth failure never submits");
            check(queried.get()>=3,"Auth checked on each allowed request");
        } finally { mock.stop(0); }
        AtomicInteger uncertainSends=new AtomicInteger();
        TransferService uncertain=new TransferService((path,token,body)->{
            if (path.equals("robotQuery")) return TransferService.map("success",true,"code","0");
            if(path.equals("containerQuery") || path.equals("containerQueryAll"))return cargo("SIM_CARGO_001","PORT_97");
            uncertainSends.incrementAndGet(); throw new java.io.IOException("Lost response");
        });
        TransferController controller=new TransferController(uncertain);
        check(controller.submit("test-token",request("UNCERTAIN")).getStatusCodeValue()==502,"Uncertain outcome surfaced");
        check(controller.submit("test-token",request("UNCERTAIN")).getStatusCodeValue()==400,"Uncertain command cannot replay");
        check(uncertainSends.get()==1,"No replay after response loss");
        System.out.println("PASS: "+checks+" validation, wire-format, auth, preview, deduplication and uncertain-outcome checks; loopback mock only.");
    }
}
