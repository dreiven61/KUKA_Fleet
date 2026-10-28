package servostar.fleet.transfer;

import java.util.*;
import java.util.logging.Logger;
import com.kuka.ecs.common.transport.secs.secs2.*;

/** Native host business boundary. HTTP acknowledgement never means robot completion.
 * All mutations are serialized; received WCS facts and outbound reports are durable. */
public final class NativeHostEngine {
    static final class UncertainCommand extends RuntimeException {UncertainCommand(){super("Command outcome uncertain; no rejection or automatic replay");}}
    private static final Logger LOG=Logger.getLogger(NativeHostEngine.class.getName());
    private final TransferService service;
    private final TransferService.FleetClient client;
    final HostState state;
    private final String token;
    private final Map<String,Map<String,Object>> alarmMap;
    private final Map<String,Integer> failures;
    private final int genericFailure;
    private static final int[] CEIDS={208,604,601,211,602,301,603,605,601,606,302,607,610,207};
    private static final String[] STAGES={"","assigned","arrived-source","pickup-started","pickup-started","pickup-completed","pickup-completed","departed-source","arrived-dest","drop-started","completed","completed","unassigned","completed"};
    public NativeHostEngine(TransferService service,TransferService.FleetClient client,HostState state,String token,
            Map<String,Map<String,Object>> alarms,Map<String,Integer> failures,int genericFailure){
        this.service=service;this.client=client;this.state=state;this.token=token;this.alarmMap=alarms;this.failures=failures;this.genericFailure=genericFailure;
    }
    static boolean ok(Map<String,Object> r){return r!=null && Boolean.TRUE.equals(r.get("success")) && (r.get("code")==null || "0".equals(String.valueOf(r.get("code"))));}
    private void authenticate()throws Exception {if(!ok(client.post("robotQuery",token,Collections.<String,Object>emptyMap())))throw new SecurityException("Fleet authentication unavailable");}
    /** Custom inventory commands. Never auto-register a carrier on TRANSFER. */
    public synchronized Secs2 cargo(String command,Map<String,String> p)throws Exception {
        synchronized(state){
            state.requireHealthy();authenticate();String id=p.get("REQUESTID");
            if(state.data.cargoOperations.containsKey(id))return HostProtocol.ack(5);
            if(state.data.cargoOperations.size()>=1000 || state.data.reports.size()>19995)return HostProtocol.ack(2);
            HostState.CargoOperation op=new HostState.CargoOperation();op.command=command;op.request=new LinkedHashMap<>(p);
            state.data.cargoOperations.put(id,op);state.save();
            CargoService cargo=new CargoService(client);
            try {
                if("CARRIER_VERIFY".equals(command)){
                    HostState.CargoOperation target=state.data.cargoOperations.get(p.get("TARGETID"));
                    if(target==null || !Arrays.asList("UNCERTAIN","CONFIRMED").contains(target.phase) || !Arrays.asList("CARRIER_IN","CARRIER_OUT").contains(target.command))throw new CargoService.Rejected("UNCERTAIN_MUTATION_NOT_FOUND");
                    for(HostState.Job job:state.data.jobs.values())if(!terminal(job.phase) && target.request.get("CARRIERID").equals(job.request.get("CARRIERID")))throw new CargoService.Rejected("ACTIVE_OR_UNCERTAIN_TRANSFER");
                    // Operator has checked the physical placement. Only confirm the
                    // requested current state; never repeat the original HTTP POST.
                    cargo.assertNoFleetWork(target.request.get("CARRIERID"),target.request.get("PORT"),token);
                    if(!cargo.confirmed(target.command,target.request,token))throw new CargoService.Rejected("REQUESTED_STATE_NOT_CONFIRMED");
                    target.phase="CONFIRMED";
                    cargoResult(target,"CONFIRMED","MANUAL_STATE_VERIFIED","cargo-verified:"+p.get("TARGETID")+":"+id);
                    op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","READ_ONLY_REVIEW_COMPLETED");state.save();return HostProtocol.ack(4);
                }
                if("CARRIER_QUERY".equals(command)){
                    HostState.Delivery d=new HostState.Delivery();d.kind="INVENTORY";d.command=id;d.key="inventory:"+id;
                    d.inventory=cargo.query(token);state.add(d);op.phase="CONFIRMED";state.save();return HostProtocol.ack(4);
                }
                if("CARRIER_SCAN_TEST".equals(command)){
                    List<Map<String,Object>> rows=cargo.query(token);String scanned=p.get("SCANNEDID");int result;
                    if(!"1".equals(p.get("SCANOK")) || scanned.isEmpty())result=1;
                    else if(!scanned.equals(p.get("CARRIERID")))result=3;
                    else {
                        Map<String,Object> existing=CargoService.find(rows,scanned);
                        result=existing!=null && "1".equals(CargoService.str(existing,"inMapStatus")) && !p.get("PORT").equals(CargoService.port(existing))?2:0;
                    }
                    HostState.Delivery d=new HostState.Delivery();d.kind="ID_READ_TEST";d.command=id;d.key="scan-test:"+id;
                    d.carrier=scanned;d.location=p.get("PORT");d.result=result;state.add(d);
                    op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","SIMULATED_ID_READ_"+result);state.save();return HostProtocol.ack(4);
                }
                ensureCargoFree(p);
                Map<String,Object> body=cargo.prepare(command,p,token);
                op.phase="MUTATING";state.save(); // durable before an irreversible HTTP request
                Map<String,Object> reply=client.post("CARRIER_IN".equals(command)?"containerIn":"containerOut",token,body);
                if(reply==null || !(reply.get("success") instanceof Boolean) || !reply.containsKey("code"))throw new IllegalStateException("Unrecognized mutation response");
                if(!ok(reply)){op.phase="REJECTED";cargoResult(op,"REJECTED","FLEET_REJECTED_"+String.valueOf(reply.get("code")).replaceAll("[^A-Za-z0-9_-]","_"));}
                else if(cargo.confirmed(command,p,token)){op.phase="CONFIRMED";cargoResult(op,"CONFIRMED","INVENTORY_VERIFIED");}
                else {op.phase="UNCERTAIN";cargoResult(op,"UNCERTAIN","VERIFY_INVENTORY_NO_REPLAY");}
            }catch(CargoService.Rejected ex){
                op.phase="MUTATING".equals(op.phase)?"UNCERTAIN":"REJECTED";
                cargoResult(op,op.phase,"UNCERTAIN".equals(op.phase)?"VERIFY_INVENTORY_NO_REPLAY":ex.getMessage());
            }
            catch(Exception ex){
                boolean attempted="MUTATING".equals(op.phase);op.phase=attempted?"UNCERTAIN":"REJECTED";
                cargoResult(op,op.phase,attempted?"VERIFY_INVENTORY_NO_REPLAY":"QUERY_UNAVAILABLE_NO_MUTATION");
            }
            state.save();return HostProtocol.ack(4); // outcome is the correlated 9002 report, not this ACK
        }
    }
    private void ensureCargoFree(Map<String,String> p){
        for(HostState.Job job:state.data.jobs.values())if(!terminal(job.phase) &&
            (p.get("CARRIERID").equals(job.request.get("CARRIERID")) || p.get("PORT").equals(job.request.get("SOURCEPORT")) || p.get("PORT").equals(job.request.get("DESTPORT"))))
            throw new CargoService.Rejected("ACTIVE_OR_UNCERTAIN_TRANSFER");
        for(HostState.CargoOperation other:state.data.cargoOperations.values())if(!p.get("REQUESTID").equals(other.request.get("REQUESTID")) &&
            "UNCERTAIN".equals(other.phase) && (p.get("CARRIERID").equals(other.request.get("CARRIERID")) || p.get("PORT").equals(other.request.get("PORT"))))
            throw new CargoService.Rejected("UNCERTAIN_INVENTORY_OPERATION");
    }
    private HostState.Delivery cargoResult(HostState.CargoOperation op,String outcome,String detail){
        return cargoResult(op,outcome,detail,"cargo-result:"+op.request.get("REQUESTID"));
    }
    private HostState.Delivery cargoResult(HostState.CargoOperation op,String outcome,String detail,String key){
        HostState.Delivery d=new HostState.Delivery();d.kind="CARGO_RESULT";d.ceid=9002;d.command=op.request.get("REQUESTID");
        d.operation=op.command;d.outcome=outcome;d.carrier=op.request.getOrDefault("CARRIERID","");d.port=op.request.getOrDefault("PORT","");
        d.detail=detail;d.key=key;state.add(d);return d;
    }
    public synchronized Secs2 transfer(Map<String,Object> request){
        synchronized(state){
            state.requireHealthy();String id=(String)request.get("COMMANDID");
            if(state.data.jobs.containsKey(id))return HostProtocol.ack(5);
            if(!"AUTO".equals(state.data.tsc) || state.data.jobs.size()>=1000)return HostProtocol.ack(2);
            for(HostState.CargoOperation op:state.data.cargoOperations.values())if("UNCERTAIN".equals(op.phase) &&
                (request.get("CARRIERID").equals(op.request.get("CARRIERID")) || request.get("SOURCEPORT").equals(op.request.get("PORT")) || request.get("DESTPORT").equals(op.request.get("PORT"))))return HostProtocol.ack(2);
            for(HostState.Job j:state.data.jobs.values())if(j.request.get("CARRIERID").equals(request.get("CARRIERID")) && !terminal(j.phase))return HostProtocol.ack(2);
            HostState.Job job=new HostState.Job();job.request=TransferService.normalize(request);state.data.jobs.put(id,job);state.save();
            try {
                Map<String,Object> reply=service.submit(request,token);
                job.phase=ok(reply)?"ACTIVE":"REJECTED";
                if(ok(reply)){queue(job,"TRANSFER",208,(String)job.request.get("SOURCEPORT"),0,"normal:0");job.next=1;}
                state.save();
                return HostProtocol.ack(ok(reply)?4:2);
            }catch(TransferService.PreSubmissionException | SecurityException | IllegalArgumentException ex){job.phase="REJECTED";state.save();return HostProtocol.ack(2);}
            catch(Exception ex){job.phase="UNCERTAIN";state.save();throw new UncertainCommand();}
        }
    }
    static boolean terminal(String phase){return Arrays.asList("COMPLETED","CANCELED","ABORTED","FAILED","REJECTED").contains(phase);}
    public synchronized Secs2 control(String command,String id)throws Exception {
        state.requireHealthy();authenticate();
        if("PAUSE".equals(command) || "RESUME".equals(command))return systemControl(command);
        if(!"CANCEL".equals(command) && !"ABORT".equals(command))return HostProtocol.ack(1);
        synchronized(state){
            HostState.Job job=state.data.jobs.get(id);
            if(job==null)return HostProtocol.ack(6);
            if(!job.control.isEmpty())return HostProtocol.ack(5);
            if(!"ACTIVE".equals(job.phase))return HostProtocol.ack(2);
            // SKI CANCEL is limited to pre-pickup; ABORT is a running-transfer interruption.
            Map<String,Object> pickup=service.stage(request(job),token,"pickup-started");
            if(!"0".equals(String.valueOf(pickup.get("code"))) && !"WAITING".equals(pickup.get("code")))return HostProtocol.ack(2);
            boolean active=Boolean.TRUE.equals(pickup.get("success"));
            if("CANCEL".equals(command)?active:!active)return HostProtocol.ack(2);
            // Obtain and persist actual cargo location BEFORE accepting a control request.
            job.location=location(job);job.control=command;job.controlState="SENDING";state.save();
            Map<String,Object> reply;
            try {reply=client.post("missionCancel",token,TransferService.map("requestId",UUID.randomUUID().toString(),"missionCode",id,
                "cancelMode","CANCEL".equals(command)?"NORMAL":"FORCE","reason","SECS "+command));}
            catch(Exception ex){job.controlState="UNCERTAIN";state.save();throw new UncertainCommand();}
            if(!ok(reply)){job.control="";job.controlState="";state.save();return HostProtocol.ack(2);}
            job.controlState="ACCEPTED";queue(job,"TRANSFER","CANCEL".equals(command)?206:203,job.location,0,"control-start:"+job.responses.size());state.save();
            return HostProtocol.ack(4);
        }
    }
    private Secs2 systemControl(String command)throws Exception {
        synchronized(state){
            boolean pause="PAUSE".equals(command);
            if(pause && !"AUTO".equals(state.data.tsc) || !pause && !"PAUSED".equals(state.data.tsc))return HostProtocol.ack(2);
            List<HostState.Job> jobs=new ArrayList<>();
            for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase)){
                if(!j.control.isEmpty())return HostProtocol.ack(2);
                jobs.add(j);
            }
            state.data.tsc=pause?"PAUSING":"RESUMING";
            for(HostState.Job j:jobs){j.control=command;j.controlState="SENDING";}
            state.save();
            int accepted=0;
            for(HostState.Job j:jobs){
                try{
                    Map<String,Object> r=client.post(pause?"pauseMission":"recoverMission",token,TransferService.map("missionCode",j.request.get("COMMANDID")));
                    if(!ok(r)){
                        if(accepted==0){for(HostState.Job reset:jobs){reset.control="";reset.controlState="";}state.data.tsc=pause?"AUTO":"PAUSED";}
                        else{j.controlState="REJECTED";state.data.tsc="UNCERTAIN";state.save();throw new UncertainCommand();}
                        state.save();return HostProtocol.ack(2);
                    }
                    j.controlState="ACCEPTED";accepted++;
                }catch(Exception ex){j.controlState="UNCERTAIN";state.data.tsc="UNCERTAIN";state.save();throw new UncertainCommand();}
            }
            if(pause)tsc(107);
            if(jobs.isEmpty()){state.data.tsc=pause?"PAUSED":"AUTO";tsc(pause?105:103);}
            state.save();return HostProtocol.ack(4);
        }
    }
    /** Callback facts are buffered until the corresponding control API accepts.
     * WCS warnings do not imply abnormal terminal completion. */
    public synchronized void observe(Map<String,Object> event){
        synchronized(state){
            state.requireHealthy();String status=text(event,"status"), id=text(event,"commandId"), vehicle=text(event,"robotId");
            if(!Arrays.asList("1","2","3").contains(vehicle))return;
            if("WARNING".equals(status) || "WARN_CLEAR".equals(status))alarm(event,"WARNING".equals(status));
            HostState.Job j=state.data.jobs.get(id);if(j==null || terminal(j.phase))return;
            if(!j.request.get("CARRIERID").equals(event.get("carrierId")))return;
            String instance=text(event,"instanceId");if(instance.isEmpty())return;
            if(!j.instance.isEmpty() && !j.instance.equals(instance) || !j.vehicle.isEmpty() && !j.vehicle.equals(vehicle))return;
            String response=text(event,"responseId");if(response.isEmpty() || !j.responses.add(response))return;
            if(j.responses.size()>10000)throw new IllegalStateException("Callback review capacity");
            j.instance=instance;j.vehicle=vehicle;
            if(!"0".equals(text(event,"manualCompleteFlag"))){j.phase="UNCERTAIN";state.save();return;}
            if("CANCEL_COMPLETED".equals(status) && !j.control.isEmpty()) {
                // Preserve a terminal fact even if the callback races the REST response.
                j.request.put("_controlObserved",Boolean.TRUE.equals(event.get("success")) && "0".equals(text(event,"resultCode"))?"SUCCESS":"FAILURE");
            }
            if(Boolean.TRUE.equals(event.get("success")) && "0".equals(text(event,"resultCode"))){
                if("PAUSE".equals(j.control) && "TASK_SUSPENDED".equals(status))j.request.put("_pauseObserved","true");
                if("RESUME".equals(j.control) && ("MOVE_BEGIN".equals(status) || "ACTION_BEGIN".equals(status)))j.request.put("_resumeObserved","true");
            }
            if("TASK_COMPLETED".equals(status) && (!Boolean.TRUE.equals(event.get("success")) || !"0".equals(text(event,"resultCode"))))
                j.request.put("_terminalFailure",text(event,"resultCode"));
            state.save();
        }
    }
    /** Strip our durable observation fields before passing the six-field API contract. */
    private Map<String,Object> request(HostState.Job j){
        Map<String,Object> r=new LinkedHashMap<>();for(String k:Arrays.asList("COMMANDID","PRIORITY","REPLACE","CARRIERID","SOURCEPORT","DESTPORT"))r.put(k,j.request.get(k));return r;
    }
    public synchronized void tick()throws Exception {
        synchronized(state){
            state.requireHealthy();
            for(HostState.Job j:state.data.jobs.values()){
                if(!"ACTIVE".equals(j.phase))continue;
                if(j.request.containsKey("_terminalFailure")){
                    int result=failures.getOrDefault((String)j.request.get("_terminalFailure"),genericFailure);
                    if(result!=0){j.location=location(j);queue(j,"TRANSFER",207,j.location,result,"abnormal");j.phase="FAILED";state.save();}continue;
                }
                if(!j.control.isEmpty()) {advanceControl(j);continue;}
                if(j.paused)continue;
                if(j.next>=CEIDS.length)continue;
                Map<String,Object> status=j.next==0?TransferService.map("success",true):service.stage(request(j),token,STAGES[j.next]);
                if(!Boolean.TRUE.equals(status.get("success")))continue;
                if(status.get("robotId")!=null && !status.get("robotId").toString().isEmpty())j.vehicle=status.get("robotId").toString();
                int ceid=CEIDS[j.next];String source=(String)j.request.get("SOURCEPORT"),dest=(String)j.request.get("DESTPORT");
                String port=j.next<8?source:dest;
                String kind=ceid==301||ceid==302?"CARRIER":ceid>=600?"VEHICLE":"TRANSFER";
                String loc=ceid==301?j.vehicle:j.next<8?source:dest;
                HostState.Delivery d=queue(j,kind,ceid,loc,0,"normal:"+j.next);d.port=port;
                j.next++;if(ceid==207)j.phase="COMPLETED";state.save();
            }
            if("PAUSING".equals(state.data.tsc) || "RESUMING".equals(state.data.tsc)){
                boolean waiting=false;
                for(HostState.Job j:state.data.jobs.values())if("ACTIVE".equals(j.phase) && !j.control.isEmpty())waiting=true;
                if(!waiting){boolean paused="PAUSING".equals(state.data.tsc);state.data.tsc=paused?"PAUSED":"AUTO";tsc(paused?105:103);state.save();}
            }
        }
    }
    private void advanceControl(HostState.Job j)throws Exception {
        if(!"ACCEPTED".equals(j.controlState))return;
        String control=j.control;
        if("CANCEL".equals(control) || "ABORT".equals(control)){
            Object observed=j.request.get("_controlObserved");if(observed==null)return;
            String loc=location(j);boolean success="SUCCESS".equals(observed);
            queue(j,"TRANSFER","CANCEL".equals(control)?success?204:205:success?201:202,loc,0,"control-result:"+j.responses.size());
            if(success)j.phase="CANCEL".equals(control)?"CANCELED":"ABORTED";
            j.request.remove("_controlObserved");j.control="";j.controlState="";state.save();
        }else {
            boolean pause="PAUSE".equals(control);String key=pause?"_pauseObserved":"_resumeObserved";
            if(!j.request.containsKey(key))return;
            String loc=location(j);queue(j,"TRANSFER",pause?209:210,loc,0,"pause:"+j.responses.size());
            j.paused=pause;j.request.remove(key);j.control="";j.controlState="";state.save();
        }
    }
    @SuppressWarnings("unchecked")
    private String location(HostState.Job j)throws Exception {
        Map<String,Object> r=client.post("containerQuery",token,TransferService.map("containerCode",j.request.get("CARRIERID")));
        if(!ok(r) || !(r.get("data") instanceof List) || ((List<?>)r.get("data")).size()!=1)throw new IllegalStateException("Cargo location unavailable");
        Map<String,Object> c=(Map<String,Object>)((List<?>)r.get("data")).get(0);
        if(!j.request.get("CARRIERID").equals(c.get("containerCode")) || !"1".equals(String.valueOf(c.get("inMapStatus"))))throw new IllegalStateException("Cargo context mismatch");
        if("1".equals(String.valueOf(c.get("isCarry")))) {
            if(j.vehicle.isEmpty())throw new IllegalStateException("Carrier vehicle unknown");
            Map<String,Object> robots=client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
            if(!ok(robots) || !(robots.get("data") instanceof List) || ((List<?>)robots.get("data")).size()!=1)throw new IllegalStateException("Carrier vehicle unavailable");
            Map<?,?> robot=(Map<?,?>)((List<?>)robots.get("data")).get(0);
            if(!j.vehicle.equals(String.valueOf(robot.get("robotId"))) || !j.request.get("CARRIERID").equals(robot.get("containerCode")))throw new IllegalStateException("Cargo vehicle mismatch");
            return j.vehicle;
        }
        if(!"0".equals(String.valueOf(c.get("isCarry"))))throw new IllegalStateException("Unknown cargo carry state");
        for(String port:WorkPorts.ALL)if(WorkPorts.containerNode(port).equals(c.get("nodeCode")))return port;
        throw new IllegalStateException("Cargo not at a recognized port or confirmed vehicle");
    }
    private HostState.Delivery queue(HostState.Job j,String kind,int ceid,String location,int result,String suffix){
        HostState.Delivery d=new HostState.Delivery();d.command=(String)j.request.get("COMMANDID");d.vehicle=j.vehicle;d.kind=kind;d.ceid=ceid;d.location=location;d.result=result;d.key=d.command+":"+suffix;state.add(d);return d;
    }
    private void tsc(int ceid){HostState.Delivery d=new HostState.Delivery();d.key="tsc:"+state.data.sequence;d.kind="TSC";d.ceid=ceid;state.add(d);}
    private void alarm(Map<String,Object> e,boolean set){
        String unit=text(e,"robotId"),code=text(e,"warnCode"),command=text(e,"commandId");
        if(!set && code.isEmpty()){
            for(String key:new ArrayList<>(state.data.alarms.keySet()))if(key.startsWith(unit+":"+command+":"))clearAlarm(key);
            return;
        }
        String key=unit+":"+command+":"+code;
        if(!set){clearAlarm(key);return;}
        Map<String,Object> mapping=alarmMap.get(code);
        if(mapping==null){LOG.warning("Unmapped Fleet warning code: "+code);return;}
        if(state.data.alarms.containsKey(key))return;
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key="alarm:"+state.data.sequence;d.vehicle=unit;d.command=command;d.set=true;
        d.alarmId=((Number)mapping.get("alid")).longValue();d.category=((Number)mapping.get("category")).intValue();d.text=(String)mapping.get("text");
        state.data.alarms.put(key,d);state.add(d);unitAlarm(d,true);state.save();
    }
    private void clearAlarm(String key){
        HostState.Delivery previous=state.data.alarms.get(key);if(previous==null)return;
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key="alarm:"+state.data.sequence;d.vehicle=previous.vehicle;d.command=previous.command;
        d.alarmId=previous.alarmId;d.category=previous.category;d.text=previous.text;d.set=false;
        state.add(d);unitAlarm(d,false);state.data.alarms.remove(key);state.save();
    }
    private void unitAlarm(HostState.Delivery source,boolean set){
        HostState.Delivery d=new HostState.Delivery();d.key="unit:"+state.data.sequence;d.kind="UNIT";d.ceid=set?702:701;d.vehicle=source.vehicle;
        d.alarmId=source.alarmId;d.text=source.text;state.add(d);
    }
    public synchronized Secs2 reportBody(HostState.Delivery d){synchronized(state){return HostProtocol.body(d,state.data.jobs.get(d.command));}}
    static String text(Map<String,Object> x,String k){Object v=x.get(k);return v==null?"":v.toString();}
}
