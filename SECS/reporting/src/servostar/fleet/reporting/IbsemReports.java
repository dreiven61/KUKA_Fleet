package servostar.fleet.reporting;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** IB-SEM MessageSpec p21-24 body encoder. No sockets, polling or equipment actions.
 * The caller must supply an observed event and actual carrier location. Encoding a
 * body is not evidence that the event occurred or that a host acknowledged it.
 */
public final class IbsemReports {
    private IbsemReports() { }
    private static final Set<Integer> TRANSFER = new HashSet<Integer>(
        Arrays.asList(207, 208, 209, 210, 211));
    private static final Set<Integer> VEHICLE = new HashSet<Integer>(
        Arrays.asList(601, 602, 603, 604, 605, 606, 607, 610));

    /** RPTID 2. Use 207 only after verified terminal completion.
     * No default ResultCode or inferred carrier location is supplied.
     */
    public static Secs2 transfer(long dataId, int ceid, String commandId,
            String carrierId, String sourcePort, String destPort,
            String carrierLocation, int resultCode) {
        if (!TRANSFER.contains(ceid)) throw new IllegalArgumentException("Unsupported transfer CEID");
        // This profile only defines these result codes. Do not convert arbitrary
        // Fleet warning strings to interlock/mismatch/validation failure codes.
        if (resultCode != 0 && resultCode != 5 && resultCode != 6
                && resultCode != 7 && resultCode != 8)
            throw new IllegalArgumentException("Unagreed ResultCode");
        if (ceid != 207 && resultCode != 0)
            throw new IllegalArgumentException("Nonterminal event cannot carry terminal failure result");
        return event(dataId, ceid, 2, Secs2.list(
            ascii(commandId, 64), Secs2.list(Secs2.list(
                Secs2.list(ascii(carrierId, 64), ascii(sourcePort, 64), ascii(destPort, 64)),
                ascii(carrierLocation, 64))), Secs2.uint2(resultCode)));
    }

    /** RPTID 5. TransferPort is the port of this actual vehicle transition. */
    public static Secs2 vehicle(long dataId, int ceid, String vehicleId,
            String commandId, String transferPort, String carrierId) {
        if (!VEHICLE.contains(ceid)) throw new IllegalArgumentException("Unsupported vehicle CEID");
        return event(dataId, ceid, 5, Secs2.list(ascii(vehicleId, 32), ascii(commandId, 64),
            Secs2.list(ascii(transferPort, 64)), Secs2.list(ascii(carrierId, 64))));
    }

    /** RPTID 3. 301 after confirmed installation; 302 after confirmed removal. */
    public static Secs2 carrier(long dataId, int ceid, String vehicleId,
            String carrierId, String actualLocation, String commandId) {
        if (ceid != 301 && ceid != 302) throw new IllegalArgumentException("Unsupported carrier CEID");
        return event(dataId, ceid, 3, Secs2.list(ascii(vehicleId, 32), ascii(carrierId, 64),
            ascii(actualLocation, 64), ascii(commandId, 64)));
    }

    /** S5F1. Alarm IDs/categories must be assigned by the host profile. */
    public static Secs2 alarm(boolean set, int category, long alarmId, String alarmText) {
        if (category < 0 || category > 127) throw new IllegalArgumentException("ALCD category range");
        uint4(alarmId);
        return Secs2.list(Secs2.binary((byte)(category | (set ? 0x80 : 0))),
            Secs2.uint4(alarmId), alarmText(alarmText));
    }

    /** S6F11 RPTID 6; global aggregate alarm events 101/102 need a separate profile. */
    public static Secs2 unitAlarm(long dataId, boolean set, String unitId,
            long alarmId, String alarmText) {
        uint4(alarmId);
        return event(dataId, set ? 702 : 701, 6,
            Secs2.list(ascii(unitId, 64), Secs2.uint4(alarmId), alarmText(alarmText)));
    }

    /** S6F12 or S5F2 body. Transaction header must come from the primary request. */
    public static Secs2 acknowledge() { return Secs2.binary((byte)0); }

    private static Secs2 event(long dataId, int ceid, int rptid, Secs2 values) {
        uint4(dataId);
        return Secs2.list(Secs2.uint4(dataId), Secs2.uint2(ceid),
            Secs2.list(Secs2.list(Secs2.uint2(rptid), values)));
    }
    private static void uint4(long value) {
        if (value < 0 || value > 0xffffffffL) throw new IllegalArgumentException("U4 range");
    }
    private static Secs2 ascii(String value, int max) {
        if (value == null || value.length() < 1 || value.length() > max)
            throw new IllegalArgumentException("ASCII length 1.." + max);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c > 126) throw new IllegalArgumentException("Printable ASCII required");
        }
        return Secs2.ascii(value);
    }
    private static Secs2 alarmText(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_ ]{1,80}"))
            throw new IllegalArgumentException("AlarmText: 1..80 letters, digits, underscore or space");
        return ascii(value, 80);
    }
}
