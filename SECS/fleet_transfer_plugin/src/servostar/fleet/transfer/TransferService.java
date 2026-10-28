package servostar.fleet.transfer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Scalar SECS parameters -> the locally verified Fleet submitMission schema. */
public final class TransferService {
    public static final class PreSubmissionException extends Exception {
        PreSubmissionException(Throwable cause) { super("Status monitor/journal unavailable; no transport submitted.",cause); }
    }
    public interface FleetClient {
        Map<String,Object> post(String path, String authorization, Map<String,Object> body) throws Exception;
    }
    private final FleetClient client;
    private final TransferJournal journal;
    private final Set<String> submitted = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    public TransferService(FleetClient client) { this(client,null); }
    public TransferService(FleetClient client, TransferJournal journal) { this.client=client; this.journal=journal; }

    public Map<String,Object> preview(Map<String,Object> request, String token) throws Exception {
        Map<String,Object> mission = buildMission(request);
        authenticate(token);
        mission.put("containerModelCode",new CargoService(client).transferModel(request,token));
        return map("success", true, "code", "0", "submitted", false,
            "statusMonitorReady", journal!=null && journal.available(), "data", mission);
    }

    public Map<String,Object> submit(Map<String,Object> request, String token) throws Exception {
        Map<String,Object> mission = buildMission(request);
        authenticate(token);
        String commandId = (String) mission.get("missionCode");
        // Queries fail before any mission POST. Never invent a container from
        // an upstream expectation, or use a stale hardcoded model for new IDs.
        try {mission.put("containerModelCode",new CargoService(client).transferModel(request,token));}
        catch(CargoService.Rejected ex){throw ex;}
        catch(Exception ex){throw new PreSubmissionException(ex);}
        if(journal!=null) {
            try { journal.reserve(normalize(request)); }
            catch(java.io.IOException | IllegalStateException blocked) { throw new PreSubmissionException(blocked); }
        }
        if (!submitted.add(commandId)) {
            throw new IllegalArgumentException("COMMANDID already attempted. Query its job; do not replay it.");
        }
        // Reserve the ID before sending. Keep it reserved even if the response is lost.
        Map<String,Object> reply = client.post("submitMission", token, mission);
        if (reply == null || !(reply.get("success") instanceof Boolean) || !reply.containsKey("code")) {
            throw new IllegalStateException("Unrecognized Fleet response. Query COMMANDID before any further action.");
        }
        Map<String,Object> result = new LinkedHashMap<String,Object>(reply);
        if(journal!=null && Boolean.TRUE.equals(reply.get("success")) && "0".equals(String.valueOf(reply.get("code"))))
            journal.accepted(commandId);
        result.put("commandId", commandId);
        result.put("completed", false);
        return result;
    }

    /** Read-only gates. Never resubmit a transport and never infer pickup from job status. */
    public Map<String,Object> gate(Map<String,Object> request,String token,boolean completion) throws Exception {
        return stage(request,token,completion?"completed":"pickup-started");
    }
    public Map<String,Object> stage(Map<String,Object> request,String token,String stage) throws Exception {
        buildMission(request);
        authenticate(token);
        if(journal==null) return map("success",false,"code","MONITOR_NOT_CONFIGURED");
        Map<String,Object> normalized=normalize(request);
        Map<String,Object> state=journal.stage(normalized,stage);
        if(!Boolean.TRUE.equals(state.get("success")) || !("completed".equals(stage) || "unassigned".equals(stage))) return state;
        String command=(String)normalized.get("COMMANDID");
        Map<String,Object> jobs=client.post("jobQuery",token,map("jobCode",command,"limit",2));
        Map<String,Object> cargo=client.post("containerQuery",token,map("containerCode",normalized.get("CARRIERID")));
        if(!confirmedComplete(jobs,cargo,normalized,state.get("instanceId"),state.get("robotId")))
            return map("success",false,"code","FINAL_STATE_NOT_CONFIRMED");
        if("unassigned".equals(stage)) {
            Map<String,Object> robots=client.post("robotQuery",token,map("robotId",state.get("robotId")));
            if(!confirmedUnassigned(robots,normalized,state.get("robotId"))) return map("success",false,"code","VEHICLE_RELEASE_NOT_CONFIRMED");
        }
        return state;
    }
    private static boolean confirmedUnassigned(Map<String,Object> robots,Map<String,Object> req,Object vehicle) {
        if(robots==null || !Boolean.TRUE.equals(robots.get("success")))return false;
        Object data=robots.get("data");
        if(!(data instanceof List))return false;
        int count=0;
        for(Object row:(List<?>)data) if(row instanceof Map) {
            Map<?,?> robot=(Map<?,?>)row;
            if(!String.valueOf(vehicle).equals(String.valueOf(robot.get("robotId"))))continue;
            count++;
            // Verified against SDK MobileRobotStatus.IDLE=3 and RobotOccupyStatus.UNOCCUPIED_ROBOT=0.
            if(!"3".equals(String.valueOf(robot.get("status"))) || !"0".equals(String.valueOf(robot.get("occupyStatus")))
                || !"".equals(robot.get("containerCode"))
                || !TransferJournal.node((String)req.get("DESTPORT")).equals(robot.get("nodeCode")))return false;
        }
        return count==1;
    }
    @SuppressWarnings("unchecked")
    private static boolean confirmedComplete(Map<String,Object> jobs,Map<String,Object> cargo,
            Map<String,Object> req,Object instance,Object vehicle) {
        if(jobs==null || cargo==null || !Boolean.TRUE.equals(jobs.get("success")) || !Boolean.TRUE.equals(cargo.get("success"))) return false;
        Object list=jobs.get("data"), containers=cargo.get("data");
        if(!(list instanceof List) || !(containers instanceof List))return false;
        List<Map<String,Object>> matching=new ArrayList<>();
        for(Object row:(List<?>)list) if(row instanceof Map && req.get("COMMANDID").equals(((Map<?,?>)row).get("jobCode"))) matching.add((Map<String,Object>)row);
        if(matching.size()!=1)return false;
        Map<String,Object> job=matching.get(0);
        String destination=TransferJournal.node((String)req.get("DESTPORT"));
        if(!"30".equals(String.valueOf(job.get("status"))) || !String.valueOf(vehicle).equals(String.valueOf(job.get("robotId")))
                || !req.get("CARRIERID").equals(job.get("containerCode"))
                || !String.valueOf(instance).equals(String.valueOf(job.get("workflowId")))
                || job.get("completeTime")==null || job.get("completeTime").toString().trim().isEmpty()
                || !destination.equals(job.get("finalNodeCode")) || !destination.equals(job.get("targetCellCode")))return false;
        int count=0;
        for(Object row:(List<?>)containers) if(row instanceof Map) {
            Map<?,?> c=(Map<?,?>)row;
            if(req.get("CARRIERID").equals(c.get("containerCode"))) {
                count++;
                String station=WorkPorts.containerNode((String)req.get("DESTPORT"));
                if(!station.equals(c.get("nodeCode")) || !"0".equals(String.valueOf(c.get("isCarry")))
                        || !"1".equals(String.valueOf(c.get("inMapStatus"))))return false;
            }
        }
        return count==1;
    }
    static Map<String,Object> normalize(Map<String,Object> request) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:Arrays.asList("COMMANDID","PRIORITY","REPLACE","CARRIERID","SOURCEPORT","DESTPORT"))
            result.put(key,request.get(key).toString());
        return result;
    }

    private void authenticate(String token) throws Exception {
        if (token == null || token.trim().isEmpty() || token.length() > 4096 || token.contains("\r") || token.contains("\n")) {
            throw new SecurityException("Authorization header is required.");
        }
        Map<String,Object> auth = client.post("robotQuery", token, Collections.<String,Object>emptyMap());
        if (auth == null || !Boolean.TRUE.equals(auth.get("success")) || !"0".equals(String.valueOf(auth.get("code")))) {
            throw new SecurityException("Fleet rejected Authorization or robotQuery preflight.");
        }
    }

    public static Map<String,Object> buildMission(Map<String,Object> request) {
        if (request == null) throw new IllegalArgumentException("Request body is required.");
        Set<String> allowed = new HashSet<String>(Arrays.asList("COMMANDID", "PRIORITY", "REPLACE", "CARRIERID", "SOURCEPORT", "DESTPORT"));
        if (!allowed.containsAll(request.keySet())) throw new IllegalArgumentException("Unknown request field.");
        String command = text(request, "COMMANDID");
        if (!command.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("COMMANDID: use 1-64 ASCII letters, numbers, _ or -.");
        String carrier = text(request, "CARRIERID");
        CargoService.identifier(carrier,"CARRIERID");
        String from = node(text(request, "SOURCEPORT"));
        String to = node(text(request, "DESTPORT"));
        if (from.equals(to)) throw new IllegalArgumentException("SOURCEPORT and DESTPORT must differ.");
        int priority = number(request, "PRIORITY");
        if (priority < 1 || priority > 99) throw new IllegalArgumentException("PRIORITY must be 1-99.");
        if (number(request, "REPLACE") != 0) throw new IllegalArgumentException("REPLACE=0 only; replacement is not implemented.");
        return map("orgId", "UNIVERSAL", "requestId", command, "missionCode", command,
            "missionType", "RACK_MOVE", "robotType", "LIFT", "robotIds", Arrays.asList("1","2","3"),
            "robotModels", Arrays.asList("KMP1500I"), "priority", priority, "containerCode", carrier,
            "containerModelCode", "SIM_TRAY_001", "lockRobotAfterFinish", false,
            "missionData", Arrays.asList(step(1, from, "UP_CONTAINER"), step(2, to, "DOWN_CONTAINER")));
    }
    private static Map<String,Object> step(int sequence, String position, String action) {
        return map("sequence", sequence, "position", position, "type", "NODE_POINT", "passStrategy", "AUTO",
            "actionType", action, "waitingMillis", 0, "actionConfirm", false, "actionInform", false);
    }
    private static String node(String port) {
        return WorkPorts.node(port);
    }
    private static String text(Map<String,Object> request, String key) {
        Object value = request.get(key);
        if (!(value instanceof String) || ((String)value).trim().isEmpty()) throw new IllegalArgumentException(key + " is required as a string.");
        return (String)value;
    }
    private static int number(Map<String,Object> request, String key) {
        Object value = request.get(key);
        String s = value == null ? "" : value.toString();
        if (!s.matches("[0-9]{1,2}")) throw new IllegalArgumentException(key + " must be an integer.");
        return Integer.parseInt(s);
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> value = new LinkedHashMap<String,Object>();
        for (int i=0; i<pairs.length; i+=2) value.put((String)pairs[i], pairs[i+1]);
        return value;
    }
}
