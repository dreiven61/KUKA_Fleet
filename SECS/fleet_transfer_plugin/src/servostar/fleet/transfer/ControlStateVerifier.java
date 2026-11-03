package servostar.fleet.transfer;

import java.util.*;

/** Read-only confirmation of an accepted control, not an API acceptance ACK.
 * Values verified in Standard API 2.15.5 RcsMissionStatus: PAUSED=12,
 * EXECUTING=9. SUSPENDED=2 is an exception, not a successful PAUSE. */
final class ControlStateVerifier {
    private ControlStateVerifier() {}

    static boolean missingJob(Map<String,Object> jobs){
        return NativeHostEngine.ok(jobs) && jobs.get("data") instanceof List && ((List<?>)jobs.get("data")).isEmpty();
    }

    static boolean completedCandidate(HostState.Job expected,Map<String,Object> jobs){
        Map<?,?> job=single(jobs);
        return job!=null && !expected.instance.isEmpty() && !expected.vehicle.isEmpty()
            && expected.request.get("COMMANDID").equals(job.get("jobCode"))
            && expected.instance.equals(text(job,"workflowId"))
            && expected.vehicle.equals(text(job,"robotId"))
            && expected.request.get("CARRIERID").equals(job.get("containerCode"))
            && "30".equals(text(job,"status")) && "0".equals(text(job,"warnFlag"));
    }

    static boolean confirmed(boolean pause,HostState.Job expected,
            Map<String,Object> jobs,Map<String,Object> robots) {
        if(expected.instance.isEmpty() || expected.vehicle.isEmpty())return false;
        Map<?,?> job=single(jobs),robot=single(robots);
        if(job==null || robot==null)return false;
        String command=String.valueOf(expected.request.get("COMMANDID"));
        if(!command.equals(job.get("jobCode"))
                || !expected.instance.equals(text(job,"workflowId"))
                || !expected.vehicle.equals(text(job,"robotId"))
                || !expected.request.get("CARRIERID").equals(job.get("containerCode"))
                || !"20".equals(text(job,"status"))
                || !"0".equals(text(job,"warnFlag")))return false;
        // Do not confirm a different job/step that now owns the same AMR.
        String rcs=text(robot,"rcsMissionCode");
        return expected.vehicle.equals(text(robot,"robotId"))
            && command.equals(robot.get("missionCode"))
            && (rcs.startsWith("W-") || rcs.startsWith("C-")) && rcs.contains("-"+expected.instance+"-")
            && (pause?"12":"9").equals(text(robot,"rcsMissionStatus"));
    }

    /** A loaded AMR is the carrier location. Fleet containerQuery can fail
     * while the container is attached; never require its stale node mapping.
     * Bind the live carrier to this exact command, workflow and vehicle. */
    static String carriedLocation(HostState.Job expected,Map<String,Object> robots) {
        Map<?,?> robot=single(robots);
        if(robot==null || expected.vehicle.isEmpty() || expected.instance.isEmpty())return "";
        String rcs=text(robot,"rcsMissionCode");
        return expected.vehicle.equals(text(robot,"robotId"))
            && expected.request.get("COMMANDID").equals(robot.get("missionCode"))
            && expected.request.get("CARRIERID").equals(robot.get("containerCode"))
            && "1".equals(text(robot,"liftStatus"))
            && (rcs.startsWith("W-") || rcs.startsWith("C-"))
            && rcs.contains("-"+expected.instance+"-")?expected.vehicle:"";
    }

    static boolean handlingStep(HostState.Job expected,Map<String,Object> robots) {
        Map<?,?> robot=single(robots);
        if(robot==null || !expected.vehicle.equals(text(robot,"robotId")))
            throw new IllegalStateException("Handling vehicle state unavailable");
        String mission=text(robot,"missionCode");
        if(!mission.isEmpty() && !expected.request.get("COMMANDID").equals(mission))
            throw new IllegalStateException("Handling vehicle belongs to another command");
        if(expected.instance.isEmpty())return false;
        String rcs=text(robot,"rcsMissionCode");
        if(!rcs.isEmpty() && (!mission.equals(expected.request.get("COMMANDID"))
            || !rcs.contains("-"+expected.instance+"-")))
            throw new IllegalStateException("Handling step does not match workflow");
        return expected.vehicle.equals(text(robot,"robotId"))
            && expected.request.get("COMMANDID").equals(robot.get("missionCode"))
            && rcs.startsWith("C-") && rcs.contains("-"+expected.instance+"-")
            && Arrays.asList("9","11","12","13").contains(text(robot,"rcsMissionStatus"));
    }

    /** Cancellation callback proves the old command ended; this independent
     * snapshot proves Fleet released the AMR. Carrying cargo is allowed: ABORT
     * must not erase an attached carrier or fabricate a drop. */
    static boolean released(HostState.Job expected,Map<String,Object> robots) {
        Map<?,?> robot=single(robots);
        return robot!=null && !expected.vehicle.isEmpty()
            && expected.vehicle.equals(text(robot,"robotId"))
            && "3".equals(text(robot,"status")) && "0".equals(text(robot,"occupyStatus"))
            && text(robot,"missionCode").isEmpty() && text(robot,"rcsMissionCode").isEmpty()
            && (text(robot,"containerCode").isEmpty() || expected.request.get("CARRIERID").equals(robot.get("containerCode")));
    }

    static String releasedCarrierLocation(HostState.Job expected,Map<String,Object> robots) {
        Map<?,?> robot=single(robots);
        return released(expected,robots) && expected.request.get("CARRIERID").equals(robot.get("containerCode"))
            && "1".equals(text(robot,"liftStatus"))?expected.vehicle:"";
    }

    private static Map<?,?> single(Map<String,Object> response) {
        if(!NativeHostEngine.ok(response) || !(response.get("data") instanceof List))return null;
        List<?> rows=(List<?>)response.get("data");
        return rows.size()==1 && rows.get(0) instanceof Map?(Map<?,?>)rows.get(0):null;
    }
    private static String text(Map<?,?> row,String key) {
        Object value=row.get(key);return value==null?"":value.toString();
    }
}
