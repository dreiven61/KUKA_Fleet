package servostar.fleet.transfer;
import java.util.*;

/** Old site values exist only in test code and are never packaged. */
public final class FixtureRunner {
    static Map<String,Object> catalog(){
        List<Map<String,Object>> robots=new ArrayList<>(),nodes=new ArrayList<>(),ports=new ArrayList<>();
        for(String id:Arrays.asList("1","2","3"))robots.add(TransferService.map("robotId",id,"robotType","KMP1500I"));
        for(int i=1;i<=107;i++)nodes.add(TransferService.map("nodeCode","lay01-dist1-"+i,"nodeLabel",String.valueOf(i),"functions",
            Arrays.asList(62,87).contains(i)?Arrays.asList(10):Arrays.asList(64,88).contains(i)?Arrays.asList(4):Collections.emptyList()));
        for(int i:new int[]{84,86,89,90,93,94,95,96,97,98})ports.add(TransferService.map("portId","PORT_"+i,"nodeCode","lay01-dist1-"+i,
            "externalCode",i==89 || i==97?"SECS_ST_"+i:"","robotTypeCode",Arrays.asList("KMP1500I"),"containerModelCode",Collections.emptyList()));
        return TransferService.map("robots",robots,"maps",Arrays.asList(TransferService.map("mapCode","lay01","floorNumber","dist1","nodes",nodes)),
            "ports",ports,"models",Arrays.asList("SIM_TRAY_001","ANOTHER_LIFT_TRAY","TRAY_NEW_01","DYNAMIC_MODEL","OTHER_MODEL","SIM_TRAY_002"));
    }
    public static void main(String[] args)throws Exception{
        io.grpc.NameResolverRegistry.getDefaultRegistry().register(new io.grpc.internal.DnsNameResolverProvider());
        WorkPorts.install(catalog());
        try{Class.forName(args[0]).getMethod("main",String[].class).invoke(null,(Object)Arrays.copyOfRange(args,1,args.length));}
        catch(java.lang.reflect.InvocationTargetException ex){Throwable cause=ex.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw ex;}
    }
}
