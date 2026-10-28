import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingsboard.common.util.CalculateUtil;
import org.thingsboard.common.util.DynamicParamUtil;
import org.thingsboard.server.common.data.rule.*;
import org.thingsboard.rule.engine.util.HttpApiCallHelper;

class CheckSecsTemplateMapping {
    static boolean validateTransfer(String source, String dest, String replace) {
        var metadata=Map.of("SOURCEPORT",source,"DESTPORT",dest,"REPLACE",replace);
        var parameters=new ConcurrentHashMap<String,String>();
        String[] names={"SOURCEPORT","DESTPORT","REPLACE"};
        String[] expressions={
            "'${SOURCEPORT}' == 'PORT_89' || '${SOURCEPORT}' == 'PORT_97'",
            "('${SOURCEPORT}' == 'PORT_89' && '${DESTPORT}' == 'PORT_97') || ('${SOURCEPORT}' == 'PORT_97' && '${DESTPORT}' == 'PORT_89')",
            "'${REPLACE}' == '0'"
        };
        for(int i=0;i<names.length;i++) {
            parameters.put(names[i],metadata.get(names[i]));
            if(!"true".equals(CalculateUtil.replaceParamAndCalculateFriendly(expressions[i],parameters)))return false;
        }
        return true;
    }
    static String mapping(String key) {
        return "${(" + key + " == 'PORT_89')?string('lay01-dist1-89',(" + key + " == 'PORT_97')?string('lay01-dist1-97','INVALID_PORT'))}";
    }
    static boolean accepted(String key, String value) {
        String expression = key.equals("REPLACE")
            ? "'${REPLACE}' == '0'"
            : "'${" + key + "}' == 'PORT_89' || '${" + key + "}' == 'PORT_97'";
        var parameters = new ConcurrentHashMap<String,String>();
        parameters.put(key,value);
        return "true".equals(CalculateUtil.replaceParamAndCalculateFriendly(expression,parameters));
    }
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper();
        for(String source : List.of("PORT_89","PORT_97","PORT_UNKNOWN")) {
            for(String dest : List.of("PORT_89","PORT_97","PORT_UNKNOWN")) {
                var metadata=new HashMap<String,String>();
                metadata.put("SOURCEPORT",source); metadata.put("DESTPORT",dest);
                var root=new JsonAttribute(); root.setName("root"); root.setType(JsonDataType.OBJECT);
                var children=new ArrayList<JsonAttribute>(); root.setChildren(children);
                for(String key:List.of("SOURCEPORT","DESTPORT")) {
                    var field=new JsonAttribute(); field.setName(key); field.setExternalName(key);
                    field.setType(JsonDataType.STRING);field.setValueType(AttributeType.DYNAMIC);
                    field.setValue(mapping(key));field.setChildren(List.of());children.add(field);
                }
                var body=mapper.createObjectNode();
                HttpApiCallHelper.assembleJson(root,body,metadata);
                String rendered=DynamicParamUtil.replaceDynamicParamsByStringValueMap(body.toString(),metadata);
                var parsed=mapper.readTree(rendered);
                for(String key:List.of("SOURCEPORT","DESTPORT")) {
                    String port=metadata.get(key);
                    String expected=port.equals("PORT_89")?"lay01-dist1-89":port.equals("PORT_97")?"lay01-dist1-97":"INVALID_PORT";
                    if(!expected.equals(parsed.get(key).asText())) throw new AssertionError(rendered);
                    if(accepted(key,port)==port.equals("PORT_UNKNOWN")) throw new AssertionError("guard: "+port);
                }
                System.out.println(source+" / "+dest+" -> "+rendered);
            }
        }
        if(!accepted("REPLACE","0") || accepted("REPLACE","1"))throw new AssertionError("REPLACE guard");
        int guardCases=0;
        for(String source:List.of("PORT_89","PORT_97","PORT_UNKNOWN"))
            for(String dest:List.of("PORT_89","PORT_97","PORT_UNKNOWN"))
                for(String replace:List.of("0","1")) {
                    boolean expected=replace.equals("0") && ((source.equals("PORT_89")&&dest.equals("PORT_97")) || (source.equals("PORT_97")&&dest.equals("PORT_89")));
                    if(expected!=validateTransfer(source,dest,replace))throw new AssertionError("guard sequence");
                    guardCases++;
                }
        System.out.println("GUARD_SEQUENCE_CASES="+guardCases);
        for(String key:List.of("SOURCEPORT","DESTPORT")) {
            for(String port:List.of("PORT_89","PORT_97")) {
                String expr="${"+key+"?replace('PORT_','lay01-dist1-')}";
                String template="{\"node\":\""+expr+"\"}";
                String rendered=DynamicParamUtil.replaceDynamicParamsByStringValueMap(template,Map.of(key,port));
                String expected="lay01-dist1-"+port.substring(5);
                if(!expected.equals(mapper.readTree(rendered).get("node").asText()))throw new AssertionError(rendered);
                System.out.println("WHITELISTED_TEMPLATE "+key+" "+port+" -> "+rendered);
            }
        }
        System.out.println("PASS: INSTALLED_HELPERS; 9_MAPPING_PAIRS; PORT_AND_REPLACE_GUARDS; NO_REQUEST_SENT");
    }
}
