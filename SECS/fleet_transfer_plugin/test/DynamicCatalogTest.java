package servostar.fleet.transfer;

import com.kuka.rcs.bd.grpc.EmptyParameterRequest;
import com.kuka.rcs.bd.grpc.map.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real read-only HTTP/gRPC adapters against loopback fixtures; no Fleet calls. */
public final class DynamicCatalogTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static void rejects(Runnable action,String text){try{action.run();throw new AssertionError(text);}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    static Map<String,Object> request(){return TransferService.map("COMMANDID","CATALOG_TEST","CARRIERID","TRAY_A","PRIORITY",1,"REPLACE",0,"SOURCEPORT","LOAD_A","DESTPORT","UNLOAD_B");}
    static NodeGto node(String code,String label,String external,int function){
        return NodeGto.newBuilder().setNodeUuid(code).setNodeLabel(label).setMapCode("SITE_WEST").setFloorNumber("LEVEL_A").setForeignCode(external)
            .addFullFunctionNode(FullFunctionNodeGto.newBuilder().setMainFunction(NodeFunctionGto.newBuilder().setFunctionType(function))).build();
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        AtomicInteger reads=new AtomicInteger(),writes=new AtomicInteger(),floorReads=new AtomicInteger();
        AtomicBoolean unavailable=new AtomicBoolean();
        AtomicReference<String> robotId=new AtomicReference<>("100001");
        MapFloorGto floor=MapFloorGto.newBuilder().setMapCode("SITE_WEST").setFloorNumber("LEVEL_A")
            .addNodeList(node("site-a-load","7","LOAD_A",1)).addNodeList(node("site-a-drop","8","UNLOAD_B",1))
            .addNodeList(node("site-a-wait","9","",3)).addNodeList(node("site-a-charger","10","",10)).build();
        MapFloorGto second=MapFloorGto.newBuilder().setMapCode("SITE_EAST").setFloorNumber("LEVEL_Z")
            .addNodeList(NodeGto.newBuilder().setNodeUuid("site-z-node").setNodeLabel("7").setMapCode("SITE_EAST").setFloorNumber("LEVEL_Z")).build();
        Server rpc=ServerBuilder.forPort(0).addService(new MapServiceGrpc.MapServiceImplBase(){
            @Override public void getAllMapInfo(EmptyParameterRequest req,StreamObserver<QueryAllMapResponse> out){
                out.onNext(QueryAllMapResponse.newBuilder()
                    .addMapBuilding(MapBuildingGto.newBuilder().setMapCode("SITE_WEST").setMapName("West").setBuildingCode("BUILDING_A").addFloorList(floor))
                    .addMapBuilding(MapBuildingGto.newBuilder().setMapCode("SITE_EAST").addFloorList(second)).build());out.onCompleted();
            }
            @Override public void getMapFloor(MapFloorQueryRequest req,StreamObserver<MapFloorQueryResponse> out){
                floorReads.incrementAndGet();MapFloorGto found="SITE_WEST".equals(req.getMapCode())?floor:second;
                if(!req.getFloorNumber().equals(found.getFloorNumber())){out.onError(Status.INVALID_ARGUMENT.asRuntimeException());return;}
                out.onNext(MapFloorQueryResponse.newBuilder().setMapFloor(found).build());out.onCompleted();
            }
        }).build().start();
        ObjectMapper json=new ObjectMapper();
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/amr/",exchange->{
            Map<String,Object> response;String path=exchange.getRequestURI().getPath();reads.incrementAndGet();
            if(unavailable.get() && path.endsWith("queryAllContainerModelCode"))response=TransferService.map("success",false,"code","TEMPORARY","data",null);
            else if(path.endsWith("robotQuery"))response=TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("robotId",robotId.get(),"robotType","LIFT_NEW")));
            else if(path.endsWith("queryAllContainerModelCode")){
                if(!"GET".equals(exchange.getRequestMethod()))throw new AssertionError("Models must use documented GET");
                response=TransferService.map("success",true,"code","0","data",Arrays.asList("TRAY_NEW"));
            }else if(path.endsWith("queryFunctionNode")){
                Map<?,?> query=json.readValue(exchange.getRequestBody(),Map.class);
                List<Map<String,Object>> ports=new ArrayList<>();
                if("SITE_WEST".equals(query.get("mapCode")))for(String[] p:new String[][]{{"site-a-load","LOAD_A"},{"site-a-drop","UNLOAD_B"}})
                    ports.add(TransferService.map("nodeCode",p[0],"externalCode",p[1],"robotTypeCode",Arrays.asList("LIFT_NEW"),"containerModelCode",Arrays.asList("TRAY_NEW")));
                response=TransferService.map("success",true,"code","0","data",ports);
            }else if(path.endsWith("containerQueryAll"))response=TransferService.map("success",true,"code","0","data",Arrays.asList(TransferService.map("containerCode","TRAY_A","nodeCode","LOAD_A","containerModelCode","TRAY_NEW","inMapStatus",1,"isCarry",0)));
            else {writes.incrementAndGet();response=TransferService.map("success",true,"code","0","data",null);}
            byte[] encoded=json.writeValueAsBytes(response);exchange.sendResponseHeaders(200,encoded.length);exchange.getResponseBody().write(encoded);exchange.close();
        });http.start();
        FleetHttpClient client=new FleetHttpClient("http://127.0.0.1:"+http.getAddress().getPort()+"/amr/");client.metadataTarget("127.0.0.1:"+rpc.getPort());
        try{
            WorkPorts.refresh(client,"fixture-token");
            check(WorkPorts.ready(),"Catalog ready after scan");check(WorkPorts.robotIds().equals(Arrays.asList("100001")),"Numeric ID not restricted to 1/2/3");
            check(floorReads.get()==2,"All maps/floors queried, including map without robots");
            check(WorkPorts.knownNode("site-z-node"),"Nodes on another map discovered");
            check(WorkPorts.robotModels().equals(Arrays.asList("LIFT_NEW")),"Robot model from Fleet");
            check(WorkPorts.models().equals(Arrays.asList("TRAY_NEW")),"Container models from Fleet");
            check(WorkPorts.node("LOAD_A").equals("site-a-load"),"External port maps to actual node UUID");
            check(WorkPorts.samePort("PORT_7","LOAD_A"),"Legacy alias derived from Fleet node label");
            check(WorkPorts.waitNode("site-a-wait") && !WorkPorts.waitNode("site-a-charger") && !WorkPorts.waitNode("site-a-load"),"Recovery waiting rules use discovered functions");
            Map<String,Object> preview=new TransferService(client).preview(request(),"fixture-token");
            Map<?,?> mission=(Map<?,?>)preview.get("data");
            check(((List<?>)mission.get("robotIds")).equals(Arrays.asList("100001")),"Dynamic ID reaches actual mission payload");
            check("TRAY_NEW".equals(mission.get("containerModelCode")),"Actual cargo model reaches mission payload");
            check(writes.get()==0,"Read-only startup/preview never mutates Fleet");
            robotId.set("AGV_WEST_01");WorkPorts.refresh(client,"fixture-token");
            check(WorkPorts.robotIds().equals(Arrays.asList("AGV_WEST_01")),"String ID carried unchanged when returned by Fleet");
            check(WorkPorts.vehicle("100001").isEmpty(),"Removed robot not retained");
            check(WorkPorts.vehicle("AMR_AGV_WEST_01").equals("AGV_WEST_01"),"Loaded vehicle alias uses discovered ID");
            check(WorkPorts.eligibleRobots("LOAD_A","UNLOAD_B","TRAY_NEW").equals(Arrays.asList("AGV_WEST_01")),"New robot eligible without source changes");
            rejects(()->WorkPorts.eligibleRobots("LOAD_A","UNLOAD_B","UNKNOWN_MODEL"),"Unknown model accepted");
            Map<String,Object> changed=client.facilitySnapshot("fixture-token");((List<Map<String,Object>>)changed.get("ports")).get(0).put("nodeCode","site-a-wait");
            WorkPorts.publicationGuard((next,commit)->{if(!WorkPorts.preservesBindings(next))throw new IllegalStateException("Accepted work owns bindings");commit.run();});
            rejects(()->WorkPorts.install(changed),"In-flight port binding changed");
            check(WorkPorts.node("LOAD_A").equals("site-a-load"),"Rejected change retains accepted binding");
            WorkPorts.publicationGuard((next,commit)->commit.run());
            unavailable.set(true);
            try{new TransferService(client).submit(request(),"fixture-token");throw new AssertionError("Unavailable metadata accepted");}catch(TransferService.PreSubmissionException expected){checks++;}
            check(!WorkPorts.ready(),"Scan failure visible to admission/status");
            check(WorkPorts.node("LOAD_A").equals("site-a-load"),"Existing job keeps confirmed binding during outage");
            check(writes.get()==0,"Scan failure never sends submitMission");
            unavailable.set(false);WorkPorts.refresh(client,"fixture-token");check(WorkPorts.ready(),"Catalog recovers after fresh Fleet scan");
            check(reads.get()>10,"Tests exercised real HTTP metadata adapter");
        }finally{WorkPorts.publicationGuard((next,commit)->commit.run());http.stop(0);rpc.shutdownNow();}
        System.out.println("PASS: "+checks+" dynamic Fleet catalog, multi-map gRPC/HTTP, numeric/string IDs, changed roster, model selection, outage and no-mutation checks; loopback only.");
    }
}
