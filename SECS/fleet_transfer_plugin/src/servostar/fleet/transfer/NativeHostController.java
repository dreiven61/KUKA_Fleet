package servostar.fleet.transfer;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Authenticated administrative recovery. Motion requires explicit, exact review. */
@RestController
@RequestMapping({"/api/mes-secs","/interfaces/api/mes-secs"})
public final class NativeHostController {
    private final NativeHostService host;
    public NativeHostController(NativeHostService host){this.host=host;}
    @PostMapping("/status")
    public ResponseEntity<Map<String,Object>> status(@RequestHeader(value="Authorization",required=false)String token){
        try{return ResponseEntity.ok(host.status(token));}catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false));}
    }
    @PostMapping("/refresh-catalog")
    public ResponseEntity<Map<String,Object>> refreshCatalog(@RequestHeader(value="Authorization",required=false)String token){
        try{return ResponseEntity.ok(host.refreshCatalog(token));}
        catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false));}
        catch(Exception ex){return ResponseEntity.status(503).body(TransferService.map("success",false,"code","FLEET_CATALOG_UNAVAILABLE"));}
    }
    @PostMapping("/retry-report")
    public ResponseEntity<Map<String,Object>> retry(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object> req){
        try{
            if(req.size()!=1 || !(req.get("dataId") instanceof Number) || !req.get("dataId").toString().matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException();
            return ResponseEntity.ok(host.retry(token,((Number)req.get("dataId")).longValue()));
        }catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false));}
        catch(Exception ex){return ResponseEntity.badRequest().body(TransferService.map("success",false,"message","Blocked report DATAID required"));}
    }
    @PostMapping("/recover-loaded-abort")
    public ResponseEntity<Map<String,Object>> recoverLoadedAbort(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object> req){
        try{
            if(req==null || req.size()!=6 || !(req.get("apply") instanceof Boolean))throw new IllegalArgumentException("Exact review fields and Boolean apply required");
            for(String key:new String[]{"commandId","carrierId","vehicleId","sourceNode","waitNode"})
                if(!(req.get(key) instanceof String) || !((String)req.get(key)).matches("[A-Za-z0-9_.:-]{1,64}"))throw new IllegalArgumentException("Invalid review identity");
            return ResponseEntity.ok(host.recoverLoadedAbort(token,(String)req.get("commandId"),(String)req.get("carrierId"),
                (String)req.get("vehicleId"),(String)req.get("sourceNode"),(String)req.get("waitNode"),(Boolean)req.get("apply")));
        }catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false,"code","AUTH_REJECTED"));}
        catch(CargoService.Rejected ex){return ResponseEntity.status(409).body(TransferService.map("success",false,"code",ex.getMessage(),"completed",false));}
        catch(IllegalArgumentException ex){return ResponseEntity.badRequest().body(TransferService.map("success",false,"code","INVALID_REVIEW_REQUEST"));}
        catch(Exception ex){return ResponseEntity.status(503).body(TransferService.map("success",false,"code","REVIEW_UNAVAILABLE_QUERY_STATUS","completed",false));}
    }
}
