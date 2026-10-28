package servostar.fleet.transfer;

import java.util.*;

/** Lift workstations in the exported lay01/dist1 map (2026-10-28).
 * Do not accept arbitrary node numbers: charging and transit nodes cannot be cargo ports. */
final class WorkPorts {
    static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
        "PORT_84", "PORT_86", "PORT_89", "PORT_90", "PORT_93",
        "PORT_94", "PORT_95", "PORT_96", "PORT_97", "PORT_98"));
    static String node(String port) {
        if(!ALL.contains(port)) throw new IllegalArgumentException("Supported work ports: " + ALL);
        return "lay01-dist1-" + port.substring(5);
    }
    static String containerNode(String port) {
        String node=node(port);
        // Actual map External Code values. API containerQuery returns the external
        // code when present, otherwise the node UUID (verified for ports 84/94).
        return "PORT_89".equals(port) || "PORT_97".equals(port)
            ? "SECS_ST_" + port.substring(5) : node;
    }
}
