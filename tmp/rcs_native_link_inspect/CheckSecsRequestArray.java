import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingsboard.server.common.data.rule.*;
import org.thingsboard.rule.engine.util.HttpApiCallHelper;

class CheckSecsRequestArray {
    static JsonAttribute field(String name, JsonDataType type, String value, JsonAttribute... children) {
        var f=new JsonAttribute(); f.setName(name); f.setExternalName(name);
        f.setType(type); f.setValue(value); f.setValueType(AttributeType.DYNAMIC);
        f.setChildren(Arrays.asList(children)); return f;
    }
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper();
        var node=field("item",JsonDataType.OBJECT,"",
            field("sequence",JsonDataType.INTEGER,"1"),
            field("position",JsonDataType.STRING,"lay01-dist1-89"));
        for(var type:List.of(JsonDataType.ARRAY,JsonDataType.STRING)) {
            var root=field("root",JsonDataType.OBJECT,"",
                field("missionCode",JsonDataType.STRING,"OFFLINE_ONLY"),
                field("missionData",type,"[{\"sequence\":1,\"position\":\"lay01-dist1-89\"}]",node));
            var body=mapper.createObjectNode();
            HttpApiCallHelper.assembleJson(root,body,Map.of());
            if(type==JsonDataType.ARRAY && body.has("missionData")) throw new AssertionError(body);
            if(type==JsonDataType.STRING && !body.get("missionData").isTextual()) throw new AssertionError(body);
            System.out.println(type+" => "+body);
        }
        String array="[{\"sequence\":1,\"position\":\"${SOURCEPORT?replace('PORT_','lay01-dist1-')}\",\"actionType\":\"UP_CONTAINER\"},"
            +"{\"sequence\":2,\"position\":\"${DESTPORT?replace('PORT_','lay01-dist1-')}\",\"actionType\":\"DOWN_CONTAINER\"}]";
        for(String kind:List.of("missing","objects","integers")) {
            var metadata=new HashMap<String,String>();
            metadata.put("SOURCEPORT","PORT_89"); metadata.put("DESTPORT","PORT_97");
            if(kind.equals("objects")) metadata.put("missionData",array);
            if(kind.equals("integers")) metadata.put("missionData","[1,2]");
            var root=field("root",JsonDataType.OBJECT,"",
                field("missionData",JsonDataType.INTEGER,"${missionData}"));
            var body=mapper.createObjectNode();
            try {
                HttpApiCallHelper.assembleJson(root,body,metadata);
                if(kind.equals("objects")) throw new AssertionError("Object array unexpectedly accepted");
            } catch(com.google.gson.JsonSyntaxException e) {
                if(!kind.equals("objects") || !e.getMessage().contains("Expected an int but was BEGIN_OBJECT")) throw e;
                System.out.println("INTEGER_WITH_OBJECT_ARRAY_METADATA => REJECTED_EXPECTED_INT");
                continue;
            }
            var parsed=mapper.readTree(body.toString());
            if(kind.equals("integers")) {
                if(!parsed.get("missionData").isArray() || parsed.get("missionData").size()!=2)
                    throw new AssertionError(body);
                if(parsed.at("/missionData/0").asInt()!=1 || parsed.at("/missionData/1").asInt()!=2)
                    throw new AssertionError(body);
                System.out.println("INTEGER_WITH_INTEGER_ARRAY_METADATA => "+body);
            } else {
                if(!parsed.get("missionData").isTextual()) throw new AssertionError(body);
                System.out.println("INTEGER_WITH_SECS_FIELDS_ONLY => "+body);
            }
        }
        System.out.println("PASS: 5 cases. ARRAY omitted; STRING remains string; INTEGER forwards List<Integer> only and rejects object arrays. NO NETWORK REQUEST SENT.");
    }
}
