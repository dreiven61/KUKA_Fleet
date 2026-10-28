import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.fasterxml.jackson.databind.*;
import com.kuka.ecs.common.transport.secs.data.common.SecsErsEvent;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import org.thingsboard.rule.engine.action.secs.*;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.server.common.data.SecsAttribute;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.rule.SecsDataType;
import org.thingsboard.server.common.msg.*;

/** Calls installed serializers on the local machine only. Never calls process/onMsg or transport. */
class CheckEcsReportSupport {
    static SecsAttribute field(SecsDataType type, String value, SecsAttribute... children) {
        SecsAttribute field=new SecsAttribute(); field.setType(type); field.setValue(value);
        field.setChildren(Arrays.asList(children)); return field;
    }
    static Secs2 fixture(JsonNode node) {
        JsonNode value=node.get("value");
        switch(node.get("type").asText()) {
            case "L":
                List<Secs2> children=new ArrayList<>();
                value.forEach(c->children.add(fixture(c))); return Secs2.list(children);
            case "A": return Secs2.ascii(value.asText());
            case "U2": return Secs2.uint2(value.asInt());
            case "U4": return Secs2.uint4(value.asLong());
            case "B": return Secs2.binary((byte)value.asInt());
            default: throw new AssertionError("Unexpected fixture type");
        }
    }
    static SecsAttribute configuredBody(JsonNode node) {
        String type=node.get("type").asText();
        if("L".equals(type)) {
            List<SecsAttribute> children=new ArrayList<>();
            node.get("value").forEach(child->children.add(configuredBody(child)));
            return field(SecsDataType.L,"",children.toArray(new SecsAttribute[0]));
        }
        return field(SecsDataType.valueOf(type),node.get("value").asText());
    }
    public static void main(String[] args) throws Exception {
        ObjectMapper json=new ObjectMapper();
        int fixtures=0;
        try(var files=Files.list(Paths.get(args[0],"reference"))) {
            for(Path path:(Iterable<Path>)files.filter(p->p.toString().endsWith(".json"))::iterator) {
                JsonNode doc=json.readTree(path.toFile());
                Secs2 body=fixture(doc.get("body"));
                if(body==null || body.toJson()==null) throw new AssertionError(path);
                fixtures++;
            }
        }
        if(fixtures!=14) throw new AssertionError("Expected 14 fixtures");
        System.out.println("PASS: all 14 reference trees constructed with installed Secs2 typed factories.");
        JsonNode candidate=json.readTree(Paths.get(args[0],"ecs_candidate","SecsTransferReports02_IMPORT_CANDIDATE.json").toFile());
        int parsed=0;
        for(JsonNode exported:candidate.get("metadata").get(0).get("nodes")) {
            String type=exported.get("type").asText();
            Class<?> configType=null;
            if(type.endsWith("TbGrpcApiCallNode")) configType=org.thingsboard.rule.engine.grpc.TbGrpcApiCallNodeConfiguration.class;
            if(type.endsWith("TbEventReportSendNode")) configType=TbEventReportSendConfiguration.class;
            if(configType!=null) {
                org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(
                    new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),configType);
                parsed++;
            }
        }
        if(parsed!=6) throw new AssertionError("Expected three HTTP and three report configurations");
        System.out.println("PASS: six candidate HTTP/report configurations parsed by installed TbNodeUtils.");
        TbEventReportSendNode node=new TbEventReportSendNode();
        TbEventReportSendConfiguration config=new TbEventReportSendConfiguration();
        config.setRequestBody(field(SecsDataType.L,"",
            field(SecsDataType.U4,"1"), field(SecsDataType.U2,"208"), field(SecsDataType.L,"")));
        Field cf=TbEventReportSendNode.class.getDeclaredField("config"); cf.setAccessible(true); cf.set(node,config);
        Method method=TbEventReportSendNode.class.getDeclaredMethod("createSecs2",TbContext.class,TbMsg.class,SecsErsEvent.class);
        method.setAccessible(true);
        TbMsg msg=TbMsg.newMsg("OFFLINE_ONLY",new DeviceId(UUID.randomUUID()),new TbMsgMetaData(),"{}");
        int supported=0,empty=0;
        for(SecsErsEvent event:SecsErsEvent.values()) {
            Object result=method.invoke(node,null,msg,event);
            System.out.println(event.name()+" ("+event.getName()+") => "+(result==null?"NULL_BODY":"BODY_CREATED"));
            if(event==SecsErsEvent.TRANSFER_INITIATED) {
                if(result==null) throw new AssertionError("Initiated missing"); supported++;
            } else {
                if(result!=null) throw new AssertionError("Behavior changed; reassess "+event); empty++;
            }
        }
        // Distinguish the UI event selector bug from the on-wire CEID. This
        // tests a compatibility route without changing any server or executing
        // process/onMsg: the implemented branch serializes the configured CEID.
        for(String name:Arrays.asList("S6F11_211_Transferring","S6F11_207_TransferCompleted_OK")) {
            JsonNode body=json.readTree(Paths.get(args[0],"reference",name+".json").toFile()).get("body");
            config.setRequestBody(configuredBody(body));
            Secs2 encoded=(Secs2)method.invoke(node,null,msg,SecsErsEvent.TRANSFER_INITIATED);
            if(encoded==null || !fixture(body).toJson().equals(encoded.toJson()))
                throw new AssertionError("Configured payload was changed: "+name);
            System.out.println("COMPATIBILITY PROBE: TransferInitiated branch preserves full "+name+" body; not a live send.");
        }
        JsonNode stages=json.readTree(Paths.get(args[0],"ecs_candidate","SecsTransferReports03_IMPORT_CANDIDATE.json").toFile());
        TbMsgMetaData stageMeta=new TbMsgMetaData();
        stageMeta.putValue("COMMANDID","STAGE_TEST_001");
        stageMeta.putValue("CARRIERID","SIM_CARGO_001");
        stageMeta.putValue("SOURCEPORT","PORT_89");
        stageMeta.putValue("DESTPORT","PORT_97");
        TbMsg stageMsg=TbMsg.newMsg("OFFLINE_ONLY",new DeviceId(UUID.randomUUID()),stageMeta,
            "{\"COMMANDID\":\"STAGE_TEST_001\",\"CARRIERID\":\"SIM_CARGO_001\",\"SOURCEPORT\":\"PORT_89\",\"DESTPORT\":\"PORT_97\"}");
        int stageReports=0,stageHttp=0;
        for(JsonNode exported:stages.get("metadata").get(0).get("nodes")) {
            String type=exported.get("type").asText();
            if(type.endsWith("TbGrpcApiCallNode")) {
                org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),
                    org.thingsboard.rule.engine.grpc.TbGrpcApiCallNodeConfiguration.class);
                stageHttp++;
            }
            if(!exported.get("name").asText().matches("Report(Acquire|Deposit).*"))continue;
            TbEventReportSendConfiguration stageConfig=org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(
                new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),TbEventReportSendConfiguration.class);
            cf.set(node,stageConfig);
            Secs2 actual=(Secs2)method.invoke(node,null,stageMsg,SecsErsEvent.TRANSFER_INITIATED);
            int ceid=exported.get("configuration").get("requestBody").get("children").get(1).get("value").asInt();
            String port=ceid<606?"PORT_89":"PORT_97";
            Secs2 expected=Secs2.list(Secs2.uint4(0),Secs2.uint2(ceid),Secs2.list(Secs2.list(Secs2.uint2(5),
                Secs2.list(Secs2.ascii("1"),Secs2.ascii("STAGE_TEST_001"),Secs2.list(Secs2.ascii(port)),Secs2.list(Secs2.ascii("SIM_CARGO_001"))))));
            if(!expected.toJson().equals(actual.toJson()))throw new AssertionError("RPTID5 serializer/substitution mismatch "+ceid+": "+actual.toJson());
            stageReports++;
        }
        if(stageReports!=4 || stageHttp!=5)throw new AssertionError("Reports03 config counts");
        System.out.println("PASS: Reports03 five HTTP configs and four RPTID5 serializers including actual dynamic parameter substitution; no send.");
        JsonNode carriers=json.readTree(Paths.get(args[0],"ecs_candidate","SecsTransferReports04_IMPORT_CANDIDATE.json").toFile());
        int carrierReports=0;
        for(JsonNode exported:carriers.get("metadata").get(0).get("nodes")) {
            if(!exported.get("name").asText().matches("ReportCarrier(Installed301|Removed302)"))continue;
            TbEventReportSendConfiguration carrierConfig=org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(
                new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),TbEventReportSendConfiguration.class);
            cf.set(node,carrierConfig);
            Secs2 actual=(Secs2)method.invoke(node,null,stageMsg,SecsErsEvent.TRANSFER_INITIATED);
            int ceid=exported.get("configuration").get("requestBody").get("children").get(1).get("value").asInt();
            String location=ceid==301?"1":"PORT_97";
            Secs2 expected=Secs2.list(Secs2.uint4(0),Secs2.uint2(ceid),Secs2.list(Secs2.list(Secs2.uint2(3),
                Secs2.list(Secs2.ascii("1"),Secs2.ascii("SIM_CARGO_001"),Secs2.ascii(location),Secs2.ascii("STAGE_TEST_001")))));
            if(!expected.toJson().equals(actual.toJson()))throw new AssertionError("RPTID3 mismatch "+ceid);
            carrierReports++;
        }
        if(carrierReports!=2)throw new AssertionError("Expected two carrier reports");
        System.out.println("PASS: Reports04 RPTID3 301/302 typed body order and dynamic command/cargo/location substitution; no send.");
        JsonNode vehicles=json.readTree(Paths.get(args[0],"ecs_candidate","SecsTransferReports05_IMPORT_CANDIDATE.json").toFile());
        int vehicleReports=0,vehicleHttp=0;
        for(JsonNode exported:vehicles.get("metadata").get(0).get("nodes")) {
            if(exported.get("type").asText().endsWith("TbGrpcApiCallNode")) {
                org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),
                    org.thingsboard.rule.engine.grpc.TbGrpcApiCallNodeConfiguration.class);vehicleHttp++;
            }
            String name=exported.get("name").asText();
            if(!Arrays.asList("ReportVehicleAssigned604","ReportSourceArrived601","ReportSourceDeparted605","ReportDestinationArrived601","ReportVehicleUnassigned610").contains(name))continue;
            TbEventReportSendConfiguration vehicleConfig=org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(
                new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),TbEventReportSendConfiguration.class);
            cf.set(node,vehicleConfig);
            Secs2 actual=(Secs2)method.invoke(node,null,stageMsg,SecsErsEvent.TRANSFER_INITIATED);
            int ceid=exported.get("configuration").get("requestBody").get("children").get(1).get("value").asInt();
            String port=name.contains("Destination") || ceid==610?"PORT_97":"PORT_89";
            Secs2 expected=Secs2.list(Secs2.uint4(0),Secs2.uint2(ceid),Secs2.list(Secs2.list(Secs2.uint2(5),
                Secs2.list(Secs2.ascii("1"),Secs2.ascii("STAGE_TEST_001"),Secs2.list(Secs2.ascii(port)),Secs2.list(Secs2.ascii("SIM_CARGO_001"))))));
            if(!expected.toJson().equals(actual.toJson()))throw new AssertionError("Vehicle lifecycle serializer mismatch: "+name);
            vehicleReports++;
        }
        if(vehicleReports!=5 || vehicleHttp!=10)throw new AssertionError("Reports05 expected 5 new reports and 10 HTTP nodes");
        System.out.println("PASS: Reports05 ten HTTP configs and five lifecycle RPTID5 payloads including source/dest601 and dynamic substitution; no send.");
        JsonNode automatic=json.readTree(Paths.get(args[0],"ecs_candidate","SecsTransferReports06_Auto_IMPORT_CANDIDATE.json").toFile());
        org.thingsboard.rule.engine.grpc.TbGrpcApiCallNodeConfiguration assignment=null;
        int automaticHttp=0;
        for(JsonNode exported:automatic.get("metadata").get(0).get("nodes")) if(exported.get("type").asText().endsWith("TbGrpcApiCallNode")) {
            var parsedConfig=org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),
                org.thingsboard.rule.engine.grpc.TbGrpcApiCallNodeConfiguration.class);
            automaticHttp++;if(exported.get("name").asText().equals("WaitVehicleAssigned"))assignment=parsedConfig;
        }
        if(automaticHttp!=10 || assignment==null)throw new AssertionError("Reports06 API config count");
        var expectations=new HashMap<String,Object>();
        org.thingsboard.rule.engine.util.HttpApiCallHelper.generateJsonPathValueMap(assignment.getResponseBody(),"$",expectations);
        var vehicleField=assignment.getResponseBody().getChildren().stream().filter(f->"robotId".equals(f.getName())).findFirst().get();
        Method mapResponse=org.thingsboard.rule.engine.grpc.NodeCallHttpHandler.class.getDeclaredMethod("lambda$callExternalSystem$8",TbMsg.class,
            org.springframework.http.ResponseEntity.class,org.thingsboard.server.common.data.rule.JsonAttribute.class);mapResponse.setAccessible(true);
        for(int robot:new int[]{1,2,3}) {
            Map<String,Object> reply=new HashMap<>();reply.put("success",true);reply.put("code","0");reply.put("vehicleId",robot);
            if(org.thingsboard.rule.engine.util.HttpApiCallHelper.compare(expectations,json.writeValueAsString(reply))!=0)throw new AssertionError("Mapped response rejects valid vehicle "+robot);
            mapResponse.invoke(null,stageMsg,org.springframework.http.ResponseEntity.ok(reply),vehicleField);
            if(!String.valueOf(robot).equals(stageMsg.getMetaData().getValue("robotId")))throw new AssertionError("ECS vehicle metadata has JSON quotes or wrong ID");
            int dynamicReports=0;
            for(JsonNode exported:automatic.get("metadata").get(0).get("nodes")) {
                if(!exported.get("type").asText().endsWith("TbEventReportSendNode"))continue;
                var cfg=org.thingsboard.rule.engine.api.util.TbNodeUtils.convert(new org.thingsboard.rule.engine.api.TbNodeConfiguration(exported.get("configuration")),TbEventReportSendConfiguration.class);
                int ceid=exported.get("configuration").get("requestBody").get("children").get(1).get("value").asInt();
                if(ceid<600 && ceid!=301 && ceid!=302)continue;
                cf.set(node,cfg);Secs2 actual=(Secs2)method.invoke(node,null,stageMsg,SecsErsEvent.TRANSFER_INITIATED);
                Secs2 expected;
                if(ceid>=600) {
                    String name=exported.get("name").asText();String port=name.contains("Destination") || ceid==606 || ceid==607 || ceid==610?"PORT_97":"PORT_89";
                    expected=Secs2.list(Secs2.uint4(0),Secs2.uint2(ceid),Secs2.list(Secs2.list(Secs2.uint2(5),
                        Secs2.list(Secs2.ascii(String.valueOf(robot)),Secs2.ascii("STAGE_TEST_001"),Secs2.list(Secs2.ascii(port)),Secs2.list(Secs2.ascii("SIM_CARGO_001"))))));
                } else expected=Secs2.list(Secs2.uint4(0),Secs2.uint2(ceid),Secs2.list(Secs2.list(Secs2.uint2(3),
                    Secs2.list(Secs2.ascii(String.valueOf(robot)),Secs2.ascii("SIM_CARGO_001"),Secs2.ascii(ceid==301?String.valueOf(robot):"PORT_97"),Secs2.ascii("STAGE_TEST_001")))));
                if(!expected.toJson().equals(actual.toJson()))throw new AssertionError("Actual ECS response-to-SECS vehicle mapping mismatch: "+robot+" CEID "+ceid);
                dynamicReports++;
            }
            if(dynamicReports!=11)throw new AssertionError("Reports06 expected 11 vehicle-bearing reports");
        }
        if(org.thingsboard.rule.engine.util.HttpApiCallHelper.compare(expectations,"{\"success\":true,\"code\":\"0\",\"vehicleId\":0}")==0)throw new AssertionError("No assignment must not produce604");
        System.out.println("PASS: Reports06 actual ECS response mapper -> metadata -> 33 SECS vehicle bodies for AMR1/2/3; ten HTTP configs; no sends.");
        cf.set(node,config);
        config.setRequestBody(field(SecsDataType.U2,"${RESULTCODE}"));
        try {
            method.invoke(node,null,msg,SecsErsEvent.TRANSFER_INITIATED);
            throw new AssertionError("Numeric dynamic value unexpectedly supported");
        } catch(InvocationTargetException expected) {
            if(!(expected.getCause() instanceof org.thingsboard.server.common.data.exception.KecsException)
                || !expected.getCause().getMessage().contains("${RESULTCODE}")) throw expected;
            System.out.println("CONFIRMED: U2 ${RESULTCODE} is rejected by numeric parsing (KecsException wrapper).");
        }
        System.out.println("PASS: installed EventReportSend switch: "+supported+" body-producing event, "+empty+" null-body events. NO NETWORK CALLS.");
    }
}
