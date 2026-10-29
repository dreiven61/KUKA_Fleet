package servostar.fleet.transfer;

import java.util.*;

/** Read-only confirmation of an accepted control, not an API acceptance ACK.
 * Values verified in Standard API 2.15.5 RcsMissionStatus: PAUSED=12,
 * EXECUTING=9. SUSPENDED=2 is an exception, not a successful PAUSE. */
final class ControlStateVerifier {
    private ControlStateVerifier() {}

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
            && rcs.startsWith("W-") && rcs.contains("-"+expected.instance+"-")
            && (pause?"12":"9").equals(text(robot,"rcsMissionStatus"));
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
