import java.util.concurrent.ConcurrentHashMap;
import org.thingsboard.common.util.CalculateUtil;

class CheckPortExpressions {
    public static void main(String[] args) {
        for (String key : new String[]{"SOURCEPORT", "DESTPORT"}) {
            String expression = "'${" + key + "}' == 'PORT_89' ? 'lay01-dist1-89' : ('${" + key + "}' == 'PORT_97' ? 'lay01-dist1-97' : 'INVALID_PORT')";
            for (String port : new String[]{"PORT_89", "PORT_97", "PORT_UNKNOWN"}) {
                var map = new ConcurrentHashMap<String,String>();
                map.put(key, port);
                String actual = CalculateUtil.replaceParamAndCalculateFriendly(expression, map);
                String expected = port.equals("PORT_89") ? "lay01-dist1-89" : port.equals("PORT_97") ? "lay01-dist1-97" : "INVALID_PORT";
                if (!expected.equals(actual)) throw new AssertionError(port + " -> " + actual);
                System.out.println(key + ": " + port + " -> " + actual);
            }
        }
        System.out.println("OFFLINE_ONLY; NO_FLEET_REQUEST_SENT");
    }
}
