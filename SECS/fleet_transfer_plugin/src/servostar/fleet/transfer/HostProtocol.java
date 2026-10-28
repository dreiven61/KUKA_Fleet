package servostar.fleet.transfer;

import com.kuka.ecs.common.transport.secs.secs2.*;
import java.util.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** SKI MessageSpec p13-18,21-24; parse types before any Fleet call. */
final class HostProtocol {
    static final class ParameterError extends Exception {
        final String field;final int code;
        ParameterError(String field,int code){super("Invalid "+field);this.field=field;this.code=code;}
    }
    static void listOf(Secs2 x,int count,String field)throws Exception {
        if(x==null || x.secs2Item()!=Secs2Item.LIST || x.size()!=count)throw new ParameterError(field,3);
    }
    static String asciiField(Secs2 x,String name)throws Exception {
        if(x.secs2Item()!=Secs2Item.ASCII)throw new ParameterError(name,3);
        return x.getAscii();
    }
    static Map<String,Secs2> pairs(Secs2 x,String field)throws Exception {
        if(x.secs2Item()!=Secs2Item.LIST)throw new ParameterError(field,3);
        Map<String,Secs2> result=new LinkedHashMap<>();
        for(Secs2 p:x){listOf(p,2,field);String k=asciiField(p.get(0),field);if(result.put(k,p.get(1))!=null)throw new ParameterError(k,2);}
        return result;
    }
    static Map<String,Object> transfer(Secs2 root)throws Exception {
        listOf(root,4,"BODY");
        if(root.get(0).secs2Item()!=Secs2Item.UINT4 || root.get(0).size()!=1)throw new ParameterError("DATAID",3);
        if(!asciiField(root.get(1),"OBJSPEC").isEmpty())throw new ParameterError("OBJSPEC",2);
        if(!"TRANSFER".equals(asciiField(root.get(2),"RCMD")))throw new ParameterError("RCMD",1);
        Map<String,Secs2> groups=pairs(root.get(3),"GROUPS");
        if(!groups.keySet().equals(new HashSet<>(Arrays.asList("COMMANDINFO","TRANSFERINFO"))))throw new ParameterError("GROUPS",1);
        Map<String,Secs2> cmd=pairs(groups.get("COMMANDINFO"),"COMMANDINFO"), cargo=pairs(groups.get("TRANSFERINFO"),"TRANSFERINFO");
        if(!cmd.keySet().equals(new HashSet<>(Arrays.asList("COMMANDID","PRIORITY","REPLACE"))))throw new ParameterError("COMMANDINFO",1);
        if(!cargo.keySet().equals(new HashSet<>(Arrays.asList("CARRIERID","SOURCEPORT","DESTPORT"))))throw new ParameterError("TRANSFERINFO",1);
        Map<String,Object> r=new LinkedHashMap<>();
        for(String k:Arrays.asList("COMMANDID","CARRIERID","SOURCEPORT","DESTPORT"))r.put(k,asciiField((cmd.containsKey(k)?cmd:cargo).get(k),k));
        if(!((String)r.get("COMMANDID")).matches("[A-Za-z0-9_-]{1,32}"))throw new ParameterError("COMMANDID",2);
        for(String k:Arrays.asList("PRIORITY","REPLACE")){
            Secs2 x=cmd.get(k);if(x.secs2Item()!=Secs2Item.UINT2 || x.size()!=1)throw new ParameterError(k,3);r.put(k,x.getInt(0));
        }
        try{CargoService.identifier((String)r.get("CARRIERID"),"CARRIERID");}catch(IllegalArgumentException ex){throw new ParameterError("CARRIERID",2);}
        for(String k:Arrays.asList("SOURCEPORT","DESTPORT"))if(!WorkPorts.ALL.contains(r.get(k)))throw new ParameterError(k,2);
        if(r.get("SOURCEPORT").equals(r.get("DESTPORT")))throw new ParameterError("DESTPORT",2);
        if((int)r.get("PRIORITY")<1 || (int)r.get("PRIORITY")>99)throw new ParameterError("PRIORITY",2);
        if((int)r.get("REPLACE")!=0)throw new ParameterError("REPLACE",2);
        return r;
    }
    static Secs2 ack(int code){return list(binary((byte)code),list());}
    static Secs2 ack(ParameterError e){if("RCMD".equals(e.field) && e.code==1)return ack(1);return list(binary((byte)3),list(list(ascii(e.field),binary((byte)e.code))));}
    static Secs2 event(long id,int ceid,int report,Secs2 value){return list(uint4(id),uint2(ceid),report==0?list():list(list(uint2(report),value)));}
    static boolean cargoCommand(String command){return Arrays.asList("CARRIER_QUERY","CARRIER_IN","CARRIER_OUT","CARRIER_SCAN_TEST","CARRIER_VERIFY").contains(command);}
    static Map<String,String> cargoParameters(String command,Map<String,Secs2> values)throws Exception {
        Set<String> expected=new HashSet<>(Arrays.asList("REQUESTID"));
        if("CARRIER_VERIFY".equals(command))expected.add("TARGETID");
        else if(!"CARRIER_QUERY".equals(command))expected.addAll(Arrays.asList("CARRIERID","PORT"));
        if("CARRIER_IN".equals(command))expected.addAll(Arrays.asList("MODEL","SCANNEDID","SCANOK"));
        if("CARRIER_OUT".equals(command))expected.add("DELETE");
        if("CARRIER_SCAN_TEST".equals(command))expected.addAll(Arrays.asList("SCANNEDID","SCANOK"));
        if(!values.keySet().equals(expected))throw new ParameterError("PARAMETERS",1);
        Map<String,String> p=new LinkedHashMap<>();
        for(String key:expected){
            Secs2 x=values.get(key);String v;
            if("SCANOK".equals(key) || "DELETE".equals(key)){
                if(x.secs2Item()!=Secs2Item.UINT2 || x.size()!=1)throw new ParameterError(key,3);
                v=String.valueOf(x.getInt(0));if(!"0".equals(v) && !"1".equals(v))throw new ParameterError(key,2);
            }else v=asciiField(x,key);
            if(("REQUESTID".equals(key) || "TARGETID".equals(key)) && !v.matches("[A-Za-z0-9_-]{1,32}"))throw new ParameterError(key,2);
            if(Arrays.asList("CARRIERID","MODEL").contains(key))try{CargoService.identifier(v,key);}catch(IllegalArgumentException ex){throw new ParameterError(key,2);}
            if("SCANNEDID".equals(key) && !v.isEmpty())try{CargoService.identifier(v,key);}catch(IllegalArgumentException ex){throw new ParameterError(key,2);}
            if("PORT".equals(key) && !WorkPorts.ALL.contains(v))throw new ParameterError(key,2);
            p.put(key,v);
        }
        return p;
    }
    static Secs2 body(HostState.Delivery d,HostState.Job j){
        if("INVENTORY".equals(d.kind)){
            List<Secs2> rows=new ArrayList<>();
            for(Map<String,Object> c:d.inventory)rows.add(list(ascii(CargoService.str(c,"containerCode")),ascii(CargoService.port(c)),
                ascii(CargoService.str(c,"nodeCode")),ascii(CargoService.str(c,"containerModelCode")),
                uint2(Integer.parseInt(CargoService.str(c,"inMapStatus"))),uint2(Integer.parseInt(CargoService.str(c,"isCarry")))));
            return event(d.id,9001,90,list(ascii(d.command),list(rows)));
        }
        if("CARGO_RESULT".equals(d.kind))return event(d.id,9002,91,list(ascii(d.command),ascii(d.operation),ascii(d.outcome),ascii(d.carrier),ascii(d.port),ascii(d.detail)));
        if("ID_READ_TEST".equals(d.kind))return event(d.id,406,14,list(ascii(d.carrier),ascii(d.location),uint2(d.result)));
        if("ALARM".equals(d.kind))return list(binary((byte)(d.category|(d.set?128:0))),uint4(d.alarmId),ascii(d.text));
        if("UNIT".equals(d.kind))return event(d.id,d.ceid,6,list(ascii(d.vehicle),uint4(d.alarmId),ascii(d.text)));
        if("TSC".equals(d.kind))return event(d.id,d.ceid,0,list());
        Map<String,Object> r=j.request;
        if("VEHICLE".equals(d.kind))return event(d.id,d.ceid,5,list(ascii(d.vehicle),ascii(d.command),list(ascii(d.port)),list(ascii((String)r.get("CARRIERID")))));
        if("CARRIER".equals(d.kind))return event(d.id,d.ceid,3,list(ascii(d.vehicle),ascii((String)r.get("CARRIERID")),ascii(d.location),ascii(d.command)));
        return event(d.id,d.ceid,2,list(ascii(d.command),list(list(list(ascii((String)r.get("CARRIERID")),ascii((String)r.get("SOURCEPORT")),ascii((String)r.get("DESTPORT"))),ascii(d.location))),uint2(d.result)));
    }
}
