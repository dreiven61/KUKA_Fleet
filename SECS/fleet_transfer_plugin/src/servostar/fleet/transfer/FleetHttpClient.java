package servostar.fleet.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.*;

/** Fixed same-container destination; no token persistence, redirects, or POST replay. */
public final class FleetHttpClient implements TransferService.FleetClient {
    private final ObjectMapper json = new ObjectMapper();
    private final String base;
    private volatile String metadataTarget=System.getProperty("servostar.catalog.grpcTarget","krcs-basic-data:7999");
    void metadataTarget(String value){if(value==null || !value.matches("[A-Za-z0-9_.-]+:[0-9]{1,5}"))throw new IllegalArgumentException("Invalid metadata gRPC target");metadataTarget=value;}
    @SuppressWarnings("unchecked")
    private List<Object> metadataRows(String path,String token,Map<String,Object> body,boolean get)throws Exception{
        Map<String,Object> reply=request(path,token,body,750,3000,get);
        if(!NativeHostEngine.ok(reply) || !(reply.get("data") instanceof List))throw new IllegalStateException("Fleet metadata unavailable: "+path);
        List<Object> rows=(List<Object>)reply.get("data");if(rows.size()>=10000)throw new IllegalStateException("Fleet metadata possibly truncated: "+path);return rows;
    }
    @Override @SuppressWarnings("unchecked")
    public Map<String,Object> facilitySnapshot(String token)throws Exception{
        List<Object> robots=metadataRows("robotQuery",token,Collections.emptyMap(),false);
        List<Map<String,Object>> maps=new FleetMapReader(metadataTarget).read();
        List<Object> models=metadataRows("queryAllContainerModelCode",token,Collections.emptyMap(),true);
        List<Map<String,Object>> ports=new ArrayList<>();
        for(Map<String,Object> floor:maps){
            String map=String.valueOf(floor.get("mapCode")),district=String.valueOf(floor.get("floorNumber"));
            Map<String,Map<String,Object>> nodes=new HashMap<>();for(Map<String,Object> node:(List<Map<String,Object>>)floor.get("nodes"))nodes.put(String.valueOf(node.get("nodeCode")),node);
            for(Object value:metadataRows("queryFunctionNode",token,TransferService.map("functionType",1,"mapCode",map,"floorNumber",district),false)){
                if(!(value instanceof Map))throw new IllegalStateException("Invalid Fleet function node");
                Map<String,Object> p=new LinkedHashMap<>((Map<String,Object>)value);String code=String.valueOf(p.get("nodeCode"));
                Map<String,Object> node=nodes.get(code);if(node==null)throw new IllegalStateException("Fleet map/function-node scan disagrees");
                String external=p.get("externalCode")==null?"":String.valueOf(p.get("externalCode"));p.put("externalCode",external);
                p.put("portId",external.isEmpty()?code:external);p.put("mapCode",map);p.put("floorNumber",district);
                // Compatibility alias is derived from Fleet's node label, not
                // a built-in node list. Ambiguous labels are removed by catalog.
                String label=String.valueOf(node.get("nodeLabel"));
                if(label.matches("[0-9]+"))p.put("legacyAlias","PORT_"+label);
                ports.add(p);
            }
        }
        return TransferService.map("robots",robots,"maps",maps,"models",models,"ports",ports);
    }
    public FleetHttpClient() { this("http://127.0.0.1:10870/interfaces/api/amr/"); }
    // Package-private override is solely for loopback mock tests, never HTTP input.
    FleetHttpClient(String base) { this.base = base; }

    public Map<String,Object> post(String path, String token, Map<String,Object> body) throws Exception {
        return request(path,token,body,3000,15000);
    }
    @Override public Map<String,Object> readStatus(String path,String token,Map<String,Object> body)throws Exception{
        if(!"robotQuery".equals(path) && !"containerQueryAll".equals(path) && !"jobQuery".equals(path))throw new IllegalArgumentException("Read-only status API required");
        return request(path,token,body,750,1000);
    }
    private Map<String,Object> request(String path,String token,Map<String,Object> body,int connectMillis,int readMillis)throws Exception{
        return request(path,token,body,connectMillis,readMillis,false);
    }
    private Map<String,Object> request(String path,String token,Map<String,Object> body,int connectMillis,int readMillis,boolean get)throws Exception{
        if (!"robotQuery".equals(path) && !"submitMission".equals(path)
                && !"jobQuery".equals(path) && !"containerQuery".equals(path)
                && !"missionCancel".equals(path) && !"pauseMission".equals(path) && !"recoverMission".equals(path)
                && !"containerIn".equals(path) && !"containerOut".equals(path) && !"containerQueryAll".equals(path)
                && !"queryFunctionNode".equals(path) && !"queryAllContainerModelCode".equals(path)) throw new IllegalArgumentException("Unsupported API.");
        if(get && !"queryAllContainerModelCode".equals(path))throw new IllegalArgumentException("Read-only metadata GET required");
        HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectMillis);
        connection.setReadTimeout(readMillis);
        connection.setRequestMethod(get?"GET":"POST");
        connection.setDoOutput(!get);
        connection.setRequestProperty("Authorization", token);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        byte[] encoded = json.writeValueAsBytes(body);
        // Streaming mode prevents HttpURLConnection from automatically replaying a POST.
        if(!get)connection.setFixedLengthStreamingMode(encoded.length);
        try {
            if(!get)try (OutputStream out = connection.getOutputStream()) { out.write(encoded); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new SecurityException("Fleet rejected Authorization.");
            if (status < 200 || status >= 300) throw new IOException("Fleet HTTP status " + status + ". Query COMMANDID if submission was attempted.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1048576) throw new IOException("Fleet response exceeds 1 MiB.");
                    output.write(buffer, 0, count);
                }
                return json.readValue(new String(output.toByteArray(), StandardCharsets.UTF_8), new TypeReference<Map<String,Object>>() {});
            }
        } finally { connection.disconnect(); }
    }
}
