package servostar.fleet.transfer;

import java.util.*;

/** Fleet 2.15 manual 670-673: registration is inventory state, never a robot move.
 * No mutation retries. The native host journals intent before calling this class. */
final class CargoService {
    static final class Rejected extends IllegalArgumentException { Rejected(String why){super(why);} }
    final TransferService.FleetClient client;
    CargoService(TransferService.FleetClient client){this.client=client;}
    static String identifier(String value,String name) {
        if(value==null || !value.matches("[A-Za-z0-9_.:-]{1,64}"))throw new Rejected(name+": use 1-64 ASCII letters, digits, _ . : -");
        return value;
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> query(String token)throws Exception {
        Map<String,Object> r=client.post("containerQueryAll",token,Collections.<String,Object>emptyMap());
        if(!NativeHostEngine.ok(r) || !(r.get("data") instanceof List))throw new IllegalStateException("Inventory query unavailable");
        List<Map<String,Object>> rows=new ArrayList<>();Set<String> ids=new HashSet<>();
        if(((List<?>)r.get("data")).size()>1000)throw new IllegalStateException("Inventory exceeds profile capacity");
        for(Object o:(List<?>)r.get("data")) {
            if(!(o instanceof Map))throw new IllegalStateException("Invalid inventory row");
            Map<String,Object> row=(Map<String,Object>)o;
            String id=identifier(str(row,"containerCode"),"CARRIERID");
            if(!ids.add(id))throw new IllegalStateException("Duplicate inventory ID");
            for(String k:Arrays.asList("inMapStatus","isCarry"))if(!Arrays.asList("0","1").contains(str(row,k)))throw new IllegalStateException("Unknown inventory state");
            rows.add(row);
        }
        return rows;
    }
    static Map<String,Object> find(List<Map<String,Object>> rows,String id) {
        for(Map<String,Object> row:rows)if(id.equals(row.get("containerCode")))return row;
        return null;
    }
    static String port(Map<String,Object> row) {
        if(!"1".equals(str(row,"inMapStatus")) || !"0".equals(str(row,"isCarry")))return "";
        String node=str(row,"nodeCode");
        for(String p:WorkPorts.ALL)if(WorkPorts.containerNode(p).equals(node) || WorkPorts.node(p).equals(node))return p;
        return "";
    }
    static String str(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":v.toString();}
    static void at(Map<String,Object> row,String p) {
        if(row==null)throw new Rejected("CARRIER_NOT_REGISTERED");
        if(!p.equals(port(row)))throw new Rejected("CARRIER_NOT_AT_PORT_OR_CARRIED");
    }
    String transferModel(Map<String,Object> request,String token)throws Exception {
        List<Map<String,Object>> rows=query(token);
        Map<String,Object> row=find(rows,(String)request.get("CARRIERID"));
        at(row,(String)request.get("SOURCEPORT"));
        for(Map<String,Object> other:rows){
            if(request.get("DESTPORT").equals(port(other)))throw new Rejected("DESTINATION_OCCUPIED");
            if(request.get("SOURCEPORT").equals(port(other)) && !request.get("CARRIERID").equals(other.get("containerCode")))throw new Rejected("SOURCE_INVENTORY_CONFLICT");
        }
        return identifier(str(row,"containerModelCode"),"MODEL");
    }
    Map<String,Object> prepare(String command,Map<String,String> p,String token)throws Exception {
        String id=p.get("CARRIERID"),place=p.get("PORT");
        identifier(id,"CARRIERID");WorkPorts.node(place);
        List<Map<String,Object>> rows=query(token);Map<String,Object> row=find(rows,id);
        assertNoFleetWork(id,place,token);
        if("CARRIER_IN".equals(command)) {
            if(!"1".equals(p.get("SCANOK")) || !id.equals(p.get("SCANNEDID")))throw new Rejected("SCAN_FAILED_OR_MISMATCH");
            if(row!=null && !"0".equals(str(row,"inMapStatus")))throw new Rejected("CARRIER_ALREADY_IN_MAP");
            if(row!=null && !"0".equals(str(row,"isCarry")))throw new Rejected("CARRIER_IS_CARRIED");
            for(Map<String,Object> other:rows)if(place.equals(port(other)))throw new Rejected("PORT_OCCUPIED");
            String model=identifier(p.get("MODEL"),"MODEL");
            if(row!=null && !model.equals(str(row,"containerModelCode")))throw new Rejected("MODEL_MISMATCH");
            // Use full node UUID, not the SECS port name. Validation barcode can
            // equal containerCode for this carrier-label test profile.
            return TransferService.map("requestId",p.get("REQUESTID"),"containerCode",id,"position",WorkPorts.node(place),
                "containerModelCode",model,"enterOrientation","0","isNew",row==null,"withDefaultValidationCode",true);
        }
        at(row,place);
        return TransferService.map("requestId",p.get("REQUESTID"),"containerCode",id,"position",WorkPorts.node(place),"isDelete","1".equals(p.get("DELETE")));
    }
    /** Include jobs from ECS/manual clients, not only jobs owned by our host.
     * Filter each documented nonterminal state to avoid truncating old history. */
    void assertNoFleetWork(String carrier,String place,String token)throws Exception {
        for(String status:Arrays.asList("10","20","25","28","50","60")) {
            Map<String,Object> r=client.post("jobQuery",token,TransferService.map("status",Integer.parseInt(status),"limit",1000));
            if(!NativeHostEngine.ok(r) || !(r.get("data") instanceof List) || ((List<?>)r.get("data")).size()>=1000)throw new IllegalStateException("Active job query unavailable or truncated");
            for(Object item:(List<?>)r.get("data")) {
                if(!(item instanceof Map))throw new IllegalStateException("Invalid job response");
                Map<?,?> job=(Map<?,?>)item;
                String actual=String.valueOf(job.get("status"));
                if(Arrays.asList("30","31","35").contains(actual))continue;
                if(carrier.equals(job.get("containerCode")) || WorkPorts.node(place).equals(job.get("targetCellCode"))
                    || WorkPorts.node(place).equals(job.get("beginCellCode")))throw new Rejected("FLEET_ACTIVE_OR_WARNING_JOB");
            }
        }
    }
    boolean confirmed(String command,Map<String,String> p,String token)throws Exception {
        Map<String,Object> row=find(query(token),p.get("CARRIERID"));
        if("CARRIER_IN".equals(command))return row!=null && p.get("PORT").equals(port(row)) && p.get("MODEL").equals(str(row,"containerModelCode"));
        if("1".equals(p.get("DELETE")))return row==null;
        return row!=null && "0".equals(str(row,"inMapStatus")) && "0".equals(str(row,"isCarry"));
    }
}
