package servostar.fleet.transfer;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Administrative report-only recovery; never repeats an AMR operation. */
@RestController
@RequestMapping({"/api/mes-secs","/interfaces/api/mes-secs"})
public final class NativeHostController {
    private final NativeHostService host;
    public NativeHostController(NativeHostService host){this.host=host;}
    @PostMapping("/status")
    public ResponseEntity<Map<String,Object>> status(@RequestHeader(value="Authorization",required=false)String token){
        try{return ResponseEntity.ok(host.status(token));}catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false));}
    }
    @PostMapping("/retry-report")
    public ResponseEntity<Map<String,Object>> retry(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object> req){
        try{
            if(req.size()!=1 || !(req.get("dataId") instanceof Number) || !req.get("dataId").toString().matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException();
            return ResponseEntity.ok(host.retry(token,((Number)req.get("dataId")).longValue()));
        }catch(SecurityException ex){return ResponseEntity.status(401).body(TransferService.map("success",false));}
        catch(Exception ex){return ResponseEntity.badRequest().body(TransferService.map("success",false,"message","Blocked report DATAID required"));}
    }
}
