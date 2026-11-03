package servostar.fleet.transfer;

import java.util.*;

/** Loaded abort disposition. Only explicit configured wait nodes cause motion.
 * Every move has durable intent and identity; a lost reply never replays it. */
final class LoadedRecovery {
    private final HostState state;
    private final TransferService.FleetClient client;
    private final String token;
    private final Map<String,String> nodes=new LinkedHashMap<>();
    LoadedRecovery(HostState state,TransferService.FleetClient client,String token){this.state=state;this.client=client;this.token=token;}
    void configure(Map<String,String> configured){
        if(configured==null)throw new IllegalArgumentException("loadedAbortWaitNodes must be an object");
        Set<String> unique=new HashSet<>();
        for(Map.Entry<String,String> e:configured.entrySet()){
            if(!WorkPorts.identifier(e.getKey(),32) || !WorkPorts.identifier(e.getValue(),64) || !unique.add(e.getValue())
                || WorkPorts.ready() && !WorkPorts.waitNode(e.getValue()))
                throw new IllegalArgumentException("Unique transit/wait node required per AMR; charging/work ports forbidden");
        }
        nodes.clear();nodes.putAll(configured);
    }
    boolean enabled(String vehicle){return nodes.containsKey(vehicle);}
    Map<String,String> configuredNodes(){return new LinkedHashMap<>(nodes);}
    /** Explicit adoption of one old, fully reported ABORT. No mission POST here.
     * The ordinary ticker rechecks all live preconditions before its one send. */
    Map<String,Object> review(String id,String carrier,String vehicle,String source,String wait,boolean apply)throws Exception {
        HostState.Job j=state.data.jobs.get(id);
        if(j==null)throw new CargoService.Rejected("ABORT_JOB_NOT_FOUND");
        if(!"ABORTED".equals(j.phase) || !j.control.isEmpty() || !j.controlState.isEmpty()
            || !"CANCEL_COMPLETED_RELEASED_AND_REPORTED".equals(j.controlEvidence)
            || !j.instance.matches("[1-9][0-9]*"))throw new CargoService.Rejected("ABORT_COMPLETION_UNCONFIRMED");
        if(!carrier.equals(j.request.get("CARRIERID")) || !vehicle.equals(j.vehicle))throw new CargoService.Rejected("ABORT_IDENTITY_MISMATCH");
        if(j.recoveryArmed || !j.recoveryState.isEmpty() || !j.recoveryMission.isEmpty()
            || !j.recoveryCommand.isEmpty())throw new CargoService.Rejected("RECOVERY_ALREADY_INITIALIZED_QUERY_STATUS");
        if(!"AUTO".equals(state.data.tsc))throw new CargoService.Rejected("TSC_NOT_AUTO");
        if(!wait.equals(nodes.get(vehicle)))throw new CargoService.Rejected("WAIT_NODE_CONFIG_MISMATCH");
        if("FAILURE".equals(j.request.get("_abortedHandlingOutcome")))throw new CargoService.Rejected("HANDLING_FAILED_REQUIRES_REVIEW");
        boolean abortAck=false,releaseAck=false,after=false;
        for(HostState.Delivery d:state.data.reports)if(id.equals(d.command)){
            if(!"ACKED".equals(d.state))throw new CargoService.Rejected("ABORT_REPORT_NOT_ACKED");
            if("TRANSFER".equals(d.kind) && d.ceid==201)abortAck=true;
            if("VEHICLE".equals(d.kind) && d.ceid==610)releaseAck=true;
        }
        if(!abortAck || !releaseAck)throw new CargoService.Rejected("ABORT_REPORT_EVIDENCE_MISSING");
        for(HostState.Job other:state.data.jobs.values()){
            if(other==j){after=true;continue;}
            if(after && carrier.equals(other.request.get("CARRIERID")) && !"REJECTED".equals(other.phase))
                throw new CargoService.Rejected("NEWER_CARRIER_JOB_EXISTS");
            if(!NativeHostEngine.terminal(other.phase) && (carrier.equals(other.request.get("CARRIERID")) || vehicle.equals(other.vehicle)))
                throw new CargoService.Rejected("OTHER_ACTIVE_OR_UNCERTAIN_JOB");
        }
        Map<String,Object> robot=carriedRobot(client,token,vehicle,carrier);
        if(!source.equals(robot.get("nodeCode")))throw new CargoService.Rejected("SOURCE_NODE_CHANGED");
        prepare(j,wait);
        if(apply){
            j.recoveryReviewSource=source;j.recoveryReviewNode=wait;
            j.recoveryArmed=true;j.recoveryDetail="OPERATOR_REVIEW_ARMED";state.save();
        }
        return TransferService.map("success",true,"code","0","armed",apply,"completed",false,"data",
            TransferService.map("commandId",id,"carrierId",carrier,"vehicleId",vehicle,"sourceNode",source,"waitNode",wait));
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> carriedRobot(TransferService.FleetClient client,String token,String vehicle,String carrier)throws Exception {
        Map<String,Object> r=client.post("robotQuery",token,Collections.emptyMap());
        if(!NativeHostEngine.ok(r) || !(r.get("data") instanceof List))throw new IllegalStateException("AMR query unavailable");
        Map<String,Object> owner=null;int owners=0,matches=0;
        for(Object o:(List<?>)r.get("data")){
            if(!(o instanceof Map))throw new IllegalStateException("Invalid AMR snapshot");
            Map<String,Object> x=(Map<String,Object>)o;
            if(carrier.equals(x.get("containerCode")))owners++;
            if(vehicle.equals(CargoService.str(x,"robotId"))){matches++;owner=x;}
        }
        if(matches!=1 || owners!=1 || !carrier.equals(owner.get("containerCode")) || !"1".equals(CargoService.str(owner,"liftStatus")))
            throw new CargoService.Rejected("CARRIER_NOT_ON_SOURCE_AMR");
        if(!"3".equals(CargoService.str(owner,"status")) || !"0".equals(CargoService.str(owner,"occupyStatus"))
            || !CargoService.str(owner,"missionCode").isEmpty() || !CargoService.str(owner,"rcsMissionCode").isEmpty())
            throw new CargoService.Rejected("SOURCE_AMR_NOT_IDLE");
        if(!WorkPorts.knownNode(CargoService.str(owner,"nodeCode")))throw new CargoService.Rejected("SOURCE_AMR_NODE_UNCONFIRMED");
        return owner;
    }
    HostState.Job ready(Map<String,Object> request){
        String vehicle=WorkPorts.vehicle((String)request.get("SOURCEPORT"));
        if(vehicle.isEmpty())return null;
        HostState.Job found=null;
        for(HostState.Job j:state.data.jobs.values())if(vehicle.equals(j.vehicle)
            && request.get("CARRIERID").equals(j.request.get("CARRIERID")) && "READY".equals(j.recoveryState)){
            if(found!=null)throw new CargoService.Rejected("AMBIGUOUS_RECOVERY");found=j;
        }
        if(found==null)throw new CargoService.Rejected("LOADED_RECOVERY_NOT_READY");
        for(HostState.Delivery d:state.data.reports)if("RECOVERY".equals(d.kind)
            && found.request.get("COMMANDID").equals(d.command) && !"ACKED".equals(d.state))throw new CargoService.Rejected("RECOVERY_REPORT_NOT_ACKED");
        return found;
    }
    void verifyReady(HostState.Job old,Map<String,Object> req)throws Exception {
        Map<String,Object> robot=carriedRobot(client,token,old.vehicle,(String)req.get("CARRIERID"));
        if(!old.recoveryNode.equals(robot.get("nodeCode")))throw new CargoService.Rejected("AMR_NOT_AT_RECOVERY_WAIT_NODE");
    }
    void tick()throws Exception {
        if(nodes.isEmpty())return;
        for(HostState.Job j:state.data.jobs.values()){
            if("CLAIMED".equals(j.recoveryState)){
                HostState.Job next=state.data.jobs.get(j.recoveryCommand);
                if(next!=null && "COMPLETED".equals(next.phase))change(j,"RETURNED","DELIVERED_BY_RETURN_COMMAND");
                continue;
            }
            if(!j.recoveryArmed || !"ABORTED".equals(j.phase) || !j.control.isEmpty())continue;
            try{
                if(j.recoveryState.isEmpty()){
                    if(!"AUTO".equals(state.data.tsc))continue;
                    if("FAILURE".equals(j.request.get("_abortedHandlingOutcome"))){change(j,"BLOCKED","HANDLING_FAILED_REQUIRES_REVIEW");continue;}
                    // Only a freshly verified attached carrier gets an automatic wait move.
                    Map<String,Object> robots=client.post("robotQuery",token,TransferService.map("robotId",j.vehicle));
                    if(!NativeHostEngine.ok(robots) || !(robots.get("data") instanceof List))continue;
                    Map<?,?> row=null;
                    for(Object o:(List<?>)robots.get("data"))if(o instanceof Map && j.vehicle.equals(String.valueOf(((Map<?,?>)o).get("robotId"))))row=(Map<?,?>)o;
                    if(row==null)continue;
                    if(!j.request.get("CARRIERID").equals(row.get("containerCode"))){
                        if((row.get("containerCode")==null || "".equals(row.get("containerCode"))) && "0".equals(String.valueOf(row.get("liftStatus")))){
                            Map<String,Object> placed=CargoService.find(new CargoService(client).query(token),(String)j.request.get("CARRIERID"));
                            if(placed!=null && "0".equals(CargoService.str(placed,"isCarry")) && !CargoService.port(placed).isEmpty()){
                                j.recoveryState="NOT_LOADED";state.save();
                            }
                        }
                        else change(j,"BLOCKED","UNEXPECTED_CARRIER");
                        continue;
                    }
                    j.recoveryNode=nodes.getOrDefault(j.vehicle,"");
                    if(j.recoveryNode.isEmpty()){change(j,"BLOCKED","NO_WAIT_NODE_CONFIGURED");continue;}
                    if(!j.recoveryReviewNode.isEmpty() && !j.recoveryNode.equals(j.recoveryReviewNode))throw new CargoService.Rejected("REVIEWED_WAIT_NODE_CHANGED");
                    Prepared prepared=prepare(j,j.recoveryNode);
                    Map<String,Object> robot=prepared.robot;
                    if(!j.recoveryReviewSource.isEmpty() && !j.recoveryReviewSource.equals(robot.get("nodeCode")))throw new CargoService.Rejected("REVIEWED_SOURCE_NODE_CHANGED");
                    j.recoveryModel=prepared.model;
                    if(j.recoveryNode.equals(robot.get("nodeCode"))){change(j,"READY","LOADED_AT_WAIT_NODE");continue;}
                    j.recoveryMission="AS"+UUID.randomUUID().toString().replace("-","").substring(0,30);
                    j.recoveryState="SENDING";state.save();
                    // Keep the root carrier context and WCS lifecycle. As in
                    // the verified carried-drop probe, first UP on an already
                    // raised owner preserves attachment; destination has NO
                    // DOWN action. Direct robotMove bypasses WCS job tracking.
                    Map<String,Object> reply=client.post("submitMission",token,waitMission(j,robot));
                    if(reply==null || !(reply.get("success") instanceof Boolean) || !reply.containsKey("code")){change(j,"UNCERTAIN","WAIT_MOVE_REPLY_UNCERTAIN_NO_REPLAY");continue;}
                    change(j,NativeHostEngine.ok(reply)?"MOVING":"BLOCKED",NativeHostEngine.ok(reply)?"WAIT_MOVE_ACCEPTED":"WAIT_MOVE_REJECTED");
                }else if("MOVING".equals(j.recoveryState)){
                    Map<String,Object> jobs=client.post("jobQuery",token,TransferService.map("jobCode",j.recoveryMission,"limit",2));
                    if(!NativeHostEngine.ok(jobs) || !(jobs.get("data") instanceof List))continue;
                    List<?> rows=(List<?>)jobs.get("data");if(rows.size()!=1 || !(rows.get(0) instanceof Map))continue;
                    Map<?,?> job=(Map<?,?>)rows.get(0);
                    if(!j.recoveryMission.equals(job.get("jobCode")) || !j.vehicle.equals(String.valueOf(job.get("robotId"))))continue;
                    if(Arrays.asList("31","35","50","60").contains(String.valueOf(job.get("status")))){change(j,"BLOCKED","WAIT_MOVE_FAILED_OR_SUSPENDED");continue;}
                    if(!"30".equals(String.valueOf(job.get("status"))) || !"0".equals(String.valueOf(job.get("warnFlag")))
                        || !j.request.get("CARRIERID").equals(job.get("containerCode")) || !j.recoveryNode.equals(job.get("finalNodeCode"))
                        || job.get("completeTime")==null || "".equals(job.get("completeTime")))continue;
                    String instance=String.valueOf(job.get("workflowId"));
                    if(instance.equals("null"))continue;
                    if(!j.recoveryInstance.isEmpty() && !instance.equals(j.recoveryInstance)){change(j,"UNCERTAIN","WAIT_MOVE_INSTANCE_CONFLICT");continue;}
                    j.recoveryInstance=instance;
                    Map<String,Object> robot=carriedRobot(client,token,j.vehicle,(String)j.request.get("CARRIERID"));
                    if(j.recoveryNode.equals(robot.get("nodeCode"))){
                        new CargoService(client).assertVehicleFree(j.vehicle,(String)j.request.get("CARRIERID"),token);
                        change(j,"READY","LOADED_AT_WAIT_NODE");
                    }
                }
            }catch(CargoService.Rejected ex){
                // Projection lag during the move is not a new failed move.
                if(!"MOVING".equals(j.recoveryState))change(j,"BLOCKED",ex.getMessage());
            }catch(Exception ex){
                if("SENDING".equals(j.recoveryState))change(j,"UNCERTAIN","WAIT_MOVE_REPLY_LOST_NO_REPLAY");
                else throw ex;
            }
        }
    }
    private static final class Prepared {
        final Map<String,Object> robot;final String model;
        Prepared(Map<String,Object> robot,String model){this.robot=robot;this.model=model;}
    }
    private Prepared prepare(HostState.Job j,String wait)throws Exception {
        if(!WorkPorts.waitNode(wait))throw new CargoService.Rejected("WAIT_NODE_NOT_VALID_IN_FLEET");
        if(state.data.reports.size()>19995)throw new CargoService.Rejected("REPORT_CAPACITY_REQUIRES_REVIEW");
        Map<String,Object> robot=carriedRobot(client,token,j.vehicle,(String)j.request.get("CARRIERID"));
        new CargoService(client).assertVehicleFree(j.vehicle,(String)j.request.get("CARRIERID"),token);
        List<Map<String,Object>> inventory=new CargoService(client).query(token);
        Map<String,Object> attached=CargoService.find(inventory,(String)j.request.get("CARRIERID"));
        if(attached==null || !"1".equals(CargoService.str(attached,"isCarry")) || !"1".equals(CargoService.str(attached,"inMapStatus")))throw new CargoService.Rejected("CARRIED_INVENTORY_UNCONFIRMED");
        String model=CargoService.identifier(CargoService.str(attached,"containerModelCode"),"MODEL");
        for(Map<String,Object> cargo:inventory)if("1".equals(CargoService.str(cargo,"inMapStatus"))
            && "0".equals(CargoService.str(cargo,"isCarry")) && wait.equals(cargo.get("nodeCode")))throw new CargoService.Rejected("WAIT_NODE_OCCUPIED_BY_CARRIER");
        for(HostState.Job other:state.data.jobs.values())if(other!=j && (wait.equals(other.recoveryNode)
            || other.recoveryArmed && wait.equals(nodes.get(other.vehicle)))
            && (other.recoveryArmed && other.recoveryState.isEmpty() || Arrays.asList("SENDING","MOVING","READY","UNCERTAIN","BLOCKED","CLAIMED").contains(other.recoveryState)))
            throw new CargoService.Rejected("WAIT_NODE_RESERVED");
        Map<String,Object> all=client.post("robotQuery",token,Collections.emptyMap());
        if(!NativeHostEngine.ok(all) || !(all.get("data") instanceof List))throw new IllegalStateException("Wait occupancy unknown");
        for(Object o:(List<?>)all.get("data"))if(!(o instanceof Map))throw new IllegalStateException("Invalid wait occupancy");
        else if(!j.vehicle.equals(String.valueOf(((Map<?,?>)o).get("robotId"))) && wait.equals(((Map<?,?>)o).get("nodeCode")))throw new CargoService.Rejected("WAIT_NODE_OCCUPIED");
        return new Prepared(robot,model);
    }
    static Map<String,Object> waitMission(HostState.Job j,Map<String,Object> robot){
        return TransferService.map("orgId","UNIVERSAL","requestId",j.recoveryMission,"missionCode",j.recoveryMission,
            "missionType","RACK_MOVE","robotType","LIFT","robotIds",Collections.singletonList(j.vehicle),
            "robotModels",Collections.singletonList(WorkPorts.robotModel(j.vehicle)),"priority",1,
            "containerCode",j.request.get("CARRIERID"),"containerModelCode",j.recoveryModel,"lockRobotAfterFinish",false,
            "missionData",Arrays.asList(moveStep(CargoService.str(robot,"nodeCode"),"UP_CONTAINER",1),moveStep(j.recoveryNode,"",2)));
    }
    private static Map<String,Object> moveStep(String node,String action,int sequence){
        return TransferService.map("sequence",sequence,"position",node,"type","NODE_POINT","passStrategy","AUTO",
            "actionType",action,"waitingMillis",0,"actionConfirm",false,"actionInform",false);
    }
    private void change(HostState.Job j,String phase,String detail){
        j.recoveryState=phase;j.recoveryDetail=detail;
        HostState.Delivery d=new HostState.Delivery();d.kind="RECOVERY";d.ceid=9004;
        d.command=(String)j.request.get("COMMANDID");d.carrier=(String)j.request.get("CARRIERID");d.vehicle=j.vehicle;
        d.port=j.recoveryNode;d.outcome=phase;d.detail=detail;d.key="recovery:"+d.command+":"+phase;
        state.add(d);state.save();
    }
}
