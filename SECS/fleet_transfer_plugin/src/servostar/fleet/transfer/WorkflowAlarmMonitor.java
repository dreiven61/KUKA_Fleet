package servostar.fleet.transfer;

import java.util.*;
import java.util.logging.Logger;

/** Read-only exact-job warning polling. Fleet does not broadcast every WCS
 * warning through subscribeMissionStatus (captured workflow6899/6900).
 * Numeric fallback ALIDs are a project TEST profile, not official SKI IDs. */
final class WorkflowAlarmMonitor {
    private static final Logger LOG=Logger.getLogger(WorkflowAlarmMonitor.class.getName());
    private static final String PREFIX="workflow-warning:";
    private final HostState state;
    private final TransferService.FleetClient client;
    private final String token;
    private final Map<String,Map<String,Object>> mappings;
    WorkflowAlarmMonitor(HostState state,TransferService.FleetClient client,String token,Map<String,Map<String,Object>> mappings){
        this.state=state;this.client=client;this.token=token;this.mappings=mappings;
    }
    void poll(){
        List<String> targets=new ArrayList<>();
        synchronized(state){
            state.requireHealthy();
            for(Map.Entry<String,HostState.Job> e:state.data.jobs.entrySet()){
                boolean retained=false;
                for(HostState.Delivery a:state.data.alarms.values())if("WORKFLOW_WARNING".equals(a.operation) && e.getKey().equals(a.command)){retained=true;break;}
                if(!NativeHostEngine.terminal(e.getValue().phase) || retained)targets.add(e.getKey());
            }
        }
        // Network reads do not hold the host state lock or delay control/tick.
        for(String id:targets){
            try{
                Map<String,Object> reply=client.post("jobQuery",token,TransferService.map("jobCode",id,"limit",5));
                if(!NativeHostEngine.ok(reply) || !(reply.get("data") instanceof List))continue;
                List<?> rows=(List<?>)reply.get("data");
                // Missing/deleted jobs and ambiguous responses are NOT clears.
                if(rows.size()!=1 || !(rows.get(0) instanceof Map))continue;
                @SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)rows.get(0);
                synchronized(state){apply(id,row);}
            }catch(Exception ex){LOG.warning("Workflow alarm query unconfirmed for "+id+": "+ex.getClass().getSimpleName());}
        }
    }
    private void apply(String id,Map<String,Object> row){
        state.requireHealthy();HostState.Job job=state.data.jobs.get(id);if(job==null)return;
        String workflow=text(row,"workflowId"),flag=text(row,"warnFlag"),vehicle=text(row,"robotId");
        if(!id.equals(row.get("jobCode")) || !job.request.get("CARRIERID").equals(row.get("containerCode"))
            || !workflow.matches("[1-9][0-9]*") || !job.instance.isEmpty() && !job.instance.equals(workflow)
            || !vehicle.isEmpty() && (!WorkPorts.identifier(vehicle,32) || !job.vehicle.isEmpty() && !job.vehicle.equals(vehicle))
            || !Arrays.asList("10","20","25","28","30","31","35","50","60").contains(text(row,"status"))
            || !Arrays.asList("0","1").contains(flag))return;
        String code=text(row,"warnCode");
        if("1".equals(flag) && !code.matches("[A-Za-z0-9_.:-]{1,64}"))return;
        List<String> old=new ArrayList<>();
        for(Map.Entry<String,HostState.Delivery> e:state.data.alarms.entrySet()){
            HostState.Delivery a=e.getValue();
            if(e.getKey().startsWith(PREFIX) && id.equals(a.command)){
                if(!workflow.equals(a.detail))return; // never clear another workflow instance
                if("0".equals(flag) || !code.equals(a.port))old.add(e.getKey());
            }
        }
        for(String key:old)clear(key);
        if("0".equals(flag))return;
        String key=PREFIX+id+":"+code;if(state.data.alarms.containsKey(key))return;
        Map<String,Object> mapping=mappings.get(code);
        if(mapping==null && code.matches("[1-9][0-9]{0,5}"))mapping=TransferService.map(
            "alid",1000000L+Long.parseLong(code),"category",6,"text","TEST_FLEET_WORKFLOW_WARNING_"+code);
        if(mapping==null){LOG.warning("Unmapped workflow warning "+code+" for "+id);return;}
        capacity();
        HostState.Delivery a=new HostState.Delivery();a.kind="ALARM";a.command=id;a.vehicle=vehicle;
        a.operation="WORKFLOW_WARNING";a.port=code;a.detail=workflow;a.set=true;
        a.alarmId=((Number)mapping.get("alid")).longValue();a.category=((Number)mapping.get("category")).intValue();a.text=(String)mapping.get("text");
        // S5F1 identifies an ALID, not a workflow. Aggregate overlapping jobs:
        // first occurrence sets, last confirmed recovery clears that ALID.
        if(!active(a.alarmId,null))queueAlarm(a,true);
        if(!vehicle.isEmpty() && !active(a.alarmId,vehicle))queueUnit(a,true);
        state.data.alarms.put(key,a);state.save();
    }
    private boolean active(long alid,String vehicle){
        for(HostState.Delivery a:state.data.alarms.values())if(a.alarmId==alid && (vehicle==null || vehicle.equals(a.vehicle)))return true;
        return false;
    }
    private void clear(String key){
        capacity();
        HostState.Delivery a=state.data.alarms.remove(key);if(a==null)return;
        if(!active(a.alarmId,null))queueAlarm(a,false);
        // Unassigned workflow warnings have no invented AMR/unit reports.
        if(!a.vehicle.isEmpty() && !active(a.alarmId,a.vehicle))queueUnit(a,false);
        state.save();
    }
    private void capacity(){if(state.data.reports.size()>19998)throw new IllegalStateException("Workflow alarm report capacity; retain current alarms for review");}
    private void queueAlarm(HostState.Delivery a,boolean set){
        HostState.Delivery d=new HostState.Delivery();d.kind="ALARM";d.key="alarm:"+state.data.sequence;
        d.command=a.command;d.vehicle=a.vehicle;d.alarmId=a.alarmId;d.category=a.category;d.text=a.text;d.set=set;state.add(d);
    }
    private void queueUnit(HostState.Delivery a,boolean set){
        HostState.Delivery d=new HostState.Delivery();d.kind="UNIT";d.key="unit:"+state.data.sequence;
        d.ceid=set?702:701;d.vehicle=a.vehicle;d.command=a.command;d.alarmId=a.alarmId;d.text=a.text;state.add(d);
    }
    private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":value.toString();}
}
