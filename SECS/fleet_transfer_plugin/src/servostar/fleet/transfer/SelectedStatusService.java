package servostar.fleet.transfer;

import java.util.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** SKI BasicSpec VID3,17,21,23,25,46,58; MessageSpec S1F3/4.
 * Fresh reads, no empty fallback on failed/incomplete queries, no mutation.
 * Managed lift ports are discovered from Fleet function-node metadata. */
final class SelectedStatusService {
    // SDK MobileRobotStatus wire values. No dependency on another plugin's
    // private robot protobuf classloader: REST provides these numeric values.
    static final int DEPARTURE=1;
    private static final int OFFLINE=2,IDLE=3,EXECUTING=4,CHARGING=5,UPDATING=6,ABNORMAL=7;
    static final int[] ALL={3,17,21,23,25,46,58};
    private static final Set<String> ACTIVE=new HashSet<>(Arrays.asList("10","20","25","28","50","60"));
    private final TransferService.FleetClient client;
    private final HostState state;
    private final String token;
    private final VehicleNames vehicleNames;
    private static final int MAX_SNAPSHOT_ATTEMPTS=3;
    private static final class SnapshotConflict extends IllegalStateException {
        SnapshotConflict(String reason){super(reason);}
    }
    SelectedStatusService(TransferService.FleetClient client,HostState state,String token){this(client,state,token,VehicleNames.identity());}
    SelectedStatusService(TransferService.FleetClient client,HostState state,String token,VehicleNames names){this.client=client;this.state=state;this.token=token;this.vehicleNames=names;}
    static List<Integer> requested(Secs2 body)throws Exception{
        if(body==null || body.secs2Item()!=Secs2Item.LIST || body.size()>64)throw new IllegalArgumentException("S1F3 requires at most64 U2 SVID items");
        List<Integer> ids=new ArrayList<>();
        for(Secs2 value:body){if(value.secs2Item()!=Secs2Item.UINT2 || value.size()!=1)throw new IllegalArgumentException("SVID must be U2 scalar");ids.add(value.getInt(0));}
        if(ids.isEmpty())for(int id:ALL)ids.add(id);return ids;
    }
    Secs2 read(Secs2 request)throws Exception{
        return WorkPorts.withCurrent(()->readSnapshot(request));
    }
    private Secs2 readSnapshot(Secs2 request)throws Exception{
        List<Integer> ids=requested(request);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1500);
        for(int attempt=1;;attempt++){
            try{return snapshot(ids,deadline);}
            catch(SnapshotConflict conflict){
                // Only re-read transient cross-source conflicts. Never retry
                // transport/control operations, unavailable APIs or bad data.
                if(attempt>=MAX_SNAPSHOT_ATTEMPTS || deadline-System.nanoTime()<=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50))throw conflict;
                Thread.sleep(50);
            }
        }
    }
    private Secs2 snapshot(List<Integer> ids,long deadline)throws Exception{
        vehicleNames.requireReady();
        if(ids.contains(21) || ids.contains(58))WorkPorts.requireFresh();
        boolean needRobots=ids.contains(21) || ids.contains(25);
        boolean needCargo=ids.contains(21) || ids.contains(58);
        boolean needJobs=ids.contains(23) || ids.contains(58);
        boolean live=needRobots || needCargo || needJobs;
        List<Map<String,Object>> robots=Collections.emptyList(),cargo=Collections.emptyList(),fleet=new ArrayList<>();
        if(needRobots)robots=rows("robotQuery",Collections.emptyMap(),1000,deadline);
        if(needCargo)cargo=rows("containerQueryAll",Collections.emptyMap(),1000,deadline);
        if(needJobs){
            // State filters prevent old completed history hiding current work.
            Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
            for(String status:ACTIVE)for(Map<String,Object> row:rows("jobQuery",TransferService.map("status",Integer.parseInt(status),"limit",1000),1000,deadline)){
                String actual=text(row,"status");
                if(Arrays.asList("30","31","35").contains(actual))continue;
                if(!ACTIVE.contains(actual))throw new IllegalStateException("Unknown active job status");
                String id=required(row,"jobCode");
                Map<String,Object> old=unique.put(id,row);
                if(old!=null && !old.equals(row))throw new SnapshotConflict("ACTIVE_JOB_TRANSITION");
            }
            fleet.addAll(unique.values());
        }
        synchronized(state){
            state.requireHealthy();
            if(live && System.nanoTime()>deadline)throw new IllegalStateException("Status snapshot too old; query again");
            Map<Integer,Secs2> values=new HashMap<>();
            values.put(3,alarms());values.put(17,uint2(state.hostOffline?3:5));values.put(46,uint2(tsc(state.data.tsc)));
            if(live){
                Map<String,Map<String,Object>> byRobot=index(robots,"robotId"),byCargo=index(cargo,"containerCode");
                if(needRobots)for(String id:byRobot.keySet())if(!WorkPorts.identifier(id,32))throw new IllegalStateException("Invalid Fleet VehicleID");
                if(needCargo)validateInventory(byCargo);
                Map<String,Map<String,Object>> transfers=needJobs?transfers(fleet):Collections.emptyMap();
                if(ids.contains(23))values.put(23,transferValues(transfers));
                if(ids.contains(25))values.put(25,vehicleValues(byRobot));
                if(ids.contains(21))values.put(21,carrierValues(byCargo,byRobot));
                if(ids.contains(58))values.put(58,portValues(byCargo,transfers));
            }
            List<Secs2> result=new ArrayList<>();for(int id:ids)result.add(values.getOrDefault(id,list()));return list(result);
        }
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> rows(String path,Map<String,Object> body,int limit,long deadline)throws Exception{
        if(System.nanoTime()>deadline)throw new IllegalStateException("Status snapshot deadline exceeded");
        Map<String,Object> reply=client.readStatus(path,token,body);
        if(System.nanoTime()>deadline)throw new IllegalStateException("Status snapshot deadline exceeded");
        if(!NativeHostEngine.ok(reply) || !(reply.get("data") instanceof List))throw new IllegalStateException("Status query unavailable: "+path);
        List<?> data=(List<?>)reply.get("data");if(data.size()>=limit)throw new IllegalStateException("Status query possibly truncated: "+path);
        List<Map<String,Object>> out=new ArrayList<>();for(Object row:data){if(!(row instanceof Map))throw new IllegalStateException("Invalid status row");out.add((Map<String,Object>)row);}return out;
    }
    private Map<String,Map<String,Object>> index(List<Map<String,Object>> rows,String key){
        Map<String,Map<String,Object>> result=new TreeMap<>();
        for(Map<String,Object> row:rows)if(result.put(required(row,key),row)!=null)throw new IllegalStateException("Duplicate status identity: "+key);
        return result;
    }
    private Map<String,Map<String,Object>> transfers(List<Map<String,Object>> fleet){
        Map<String,Map<String,Object>> result=new TreeMap<>();
        for(Map<String,Object> row:fleet){
            String id=required(row,"jobCode");HostState.Job known=state.data.jobs.get(id);
            if(known!=null){
                if(!known.request.get("CARRIERID").equals(row.get("containerCode")) || !known.instance.isEmpty() && !known.instance.equals(text(row,"workflowId")))
                    throw new IllegalStateException("Live/retained transfer identity mismatch");
                if(NativeHostEngine.terminal(known.phase))throw new SnapshotConflict("NATIVE_FLEET_PHASE_TRANSITION");
                result.put(id,transfer(known));
            }else{
                // An internal loaded-ABORT parking mission is not another MCS
                // transfer. Its actual AMR binding still prevents idle status.
                boolean internal=false;
                for(HostState.Job j:state.data.jobs.values())if(id.equals(j.recoveryMission)){internal=true;break;}
                if(internal)continue;
                String from=port(required(row,"beginCellCode")),to=port(required(row,"finalNodeCode"));
                result.put(id,TransferService.map("id",id,"priority",number(row,"workflowPriority",1,99),"state",fleetState(text(row,"status")),
                    "carrier",required(row,"containerCode"),"source",from,"dest",to));
            }
        }
        // Keep uncertain submissions and completed-but-unACKed commands visible.
        // Empty Fleet history is never authority to discard our durable intent.
        Set<String> pending=new HashSet<>();
        for(HostState.Delivery d:state.data.reports)if(!"ACKED".equals(d.state) && Arrays.asList("TRANSFER","VEHICLE","CARRIER").contains(d.kind))pending.add(d.command);
        for(HostState.Job job:state.data.jobs.values())if(!NativeHostEngine.terminal(job.phase) || pending.contains(job.request.get("COMMANDID")))result.put((String)job.request.get("COMMANDID"),transfer(job));
        return result;
    }
    private Map<String,Object> transfer(HostState.Job j){
        int transferState=6;
        if(!NativeHostEngine.terminal(j.phase) && !"UNCERTAIN".equals(j.phase) && !"SUBMITTING".equals(j.phase)){
            transferState="CANCEL".equals(j.control)?4:"ABORT".equals(j.control)?5:j.paused?3:j.next>=4?2:1;
        }
        return TransferService.map("id",j.request.get("COMMANDID"),"priority",j.request.get("PRIORITY"),"state",transferState,
            "carrier",j.request.get("CARRIERID"),"source",j.request.get("SOURCEPORT"),"dest",j.request.get("DESTPORT"));
    }
    private Secs2 transferValues(Map<String,Map<String,Object>> transfers){
        List<Secs2> values=new ArrayList<>();
        for(Map<String,Object> t:transfers.values())values.add(list(list(ascii(required(t,"id")),uint2(number(t,"priority",1,99))),
            uint2(number(t,"state",1,6)),list(list(ascii(required(t,"carrier")),ascii(vehicleNames.location(required(t,"source"))),ascii(vehicleNames.location(required(t,"dest")))))));
        return list(values);
    }
    private Secs2 vehicleValues(Map<String,Map<String,Object>> robots){
        List<Secs2> values=new ArrayList<>();
        for(Map.Entry<String,Map<String,Object>> entry:robots.entrySet()){
            String id=entry.getKey();Map<String,Object> r=entry.getValue();
            int status=robotStatus(r);
            boolean down=status==OFFLINE || status==ABNORMAL || status==DEPARTURE || status==UPDATING;
            int vehicleState=status==DEPARTURE?1:2;
            boolean busy=!text(r,"missionCode").isEmpty() || !text(r,"rcsMissionCode").isEmpty()
                || !text(r,"containerCode").isEmpty() || !"0".equals(text(r,"occupyStatus")) || status==CHARGING || status==EXECUTING;
            if(vehicleState!=1 && busy){vehicleState=status==EXECUTING?3:4;}
            for(HostState.Job j:state.data.jobs.values())if(id.equals(j.vehicle) && !NativeHostEngine.terminal(j.phase)){
                if(j.next==5)vehicleState=5;else if(j.next==10)vehicleState=6;
                else if(j.paused || Arrays.asList(3,4,6,7,9,11,12,13).contains(j.next))vehicleState=4;
                else vehicleState=3;
            }
            values.add(list(ascii(vehicleNames.vehicle(id)),uint2(vehicleState),uint2(down?2:1)));
        }
        return list(values);
    }
    private Secs2 carrierValues(Map<String,Map<String,Object>> cargo,Map<String,Map<String,Object>> robots){
        List<Secs2> values=new ArrayList<>();
        for(Map.Entry<String,Map<String,Object>> entry:cargo.entrySet()){
            Map<String,Object> c=entry.getValue();String map=text(c,"inMapStatus"),carried=text(c,"isCarry");
            if(!Arrays.asList("0","1").contains(map) || !Arrays.asList("0","1").contains(carried))throw new IllegalStateException("Unknown carrier state");
            if(map.equals("0")){if(carried.equals("1"))throw new IllegalStateException("Off-map carried inventory conflict");continue;}
            String owner="",location="";
            for(Map.Entry<String,Map<String,Object>> robot:robots.entrySet())if(entry.getKey().equals(robot.getValue().get("containerCode"))){
                if(!owner.isEmpty())throw new IllegalStateException("Duplicate carrier ownership");
                if(!"1".equals(text(robot.getValue(),"liftStatus")))throw new SnapshotConflict("CARRIER_LIFT_TRANSITION");
                owner=robot.getKey();location=owner;
            }
            if(carried.equals("1") && owner.isEmpty() || carried.equals("0") && !owner.isEmpty())throw new SnapshotConflict("CARRIER_OWNER_TRANSITION");
            if(owner.isEmpty())location=port(required(c,"nodeCode"));
            // Fleet inventory API provides no authoritative InstallTime. An
            // empty ASCII explicitly means unknown; never invent current time.
            values.add(list(ascii(entry.getKey()),ascii(vehicleNames.vehicle(owner)),ascii(owner.isEmpty()?location:vehicleNames.vehicle(owner)),ascii("")));
        }
        return list(values);
    }
    private void validateInventory(Map<String,Map<String,Object>> cargo){
        for(Map<String,Object> c:cargo.values()){
            String map=text(c,"inMapStatus"),carried=text(c,"isCarry");
            if(!Arrays.asList("0","1").contains(map) || !Arrays.asList("0","1").contains(carried))throw new IllegalStateException("Unknown carrier state");
            if(map.equals("0") && carried.equals("1"))throw new IllegalStateException("Off-map carried inventory conflict");
            if(map.equals("1") && carried.equals("0"))port(required(c,"nodeCode"));
        }
    }
    private Secs2 portValues(Map<String,Map<String,Object>> cargo,Map<String,Map<String,Object>> transfers){
        List<Secs2> values=new ArrayList<>();
        for(String p:WorkPorts.ALL){
            String carrier="";int product=0;
            for(Map.Entry<String,Map<String,Object>> c:cargo.entrySet())if(p.equals(CargoService.port(c.getValue()))){
                if(!carrier.isEmpty())throw new IllegalStateException("Conflicting port inventory");carrier=c.getKey();
                String full=text(c.getValue(),"emptyFullStatus");if(!Arrays.asList("0","1").contains(full))throw new IllegalStateException("Unknown full/empty state");product=full.equals("1")?1:2;
            }
            int ready=carrier.isEmpty()?2:3;
            for(Map<String,Object> t:transfers.values())if(WorkPorts.samePort(p,String.valueOf(t.get("source"))) || WorkPorts.samePort(p,String.valueOf(t.get("dest"))))ready=1;
            values.add(list(ascii(p),uint2(ready),uint2(2),ascii(carrier),uint2(product)));
        }
        return list(values);
    }
    private Secs2 alarms(){
        List<Secs2> values=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(HostState.Delivery a:state.data.alarms.values())if(!a.vehicle.isEmpty() && seen.add(a.vehicle+":"+a.alarmId))
            values.add(list(ascii(vehicleNames.vehicle(a.vehicle)),uint4(a.alarmId),ascii(a.text)));
        return list(values);
    }
    private static int tsc(String s){return "AUTO".equals(s)?3:"PAUSED".equals(s)?2:"PAUSING".equals(s)?4:1;}
    private static int fleetState(String s){return "10".equals(s)?1:"20".equals(s)?2:"28".equals(s)?4:6;}
    private static String port(String node){
        return WorkPorts.port(node);
    }
    private static int number(Map<String,Object> row,String key,int min,int max){
        String text=required(row,key);if(!text.matches("[0-9]+"))throw new IllegalStateException("Invalid numeric "+key);
        int n=Integer.parseInt(text);if(n<min || n>max)throw new IllegalStateException("Range "+key);return n;
    }
    static int robotStatus(Map<String,Object> row){return number(row,"status",1,7);}
    private static String required(Map<String,Object> row,String key){String s=text(row,key);if(s.isEmpty())throw new IllegalStateException("Missing "+key);return s;}
    private static String text(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":v.toString();}
}
