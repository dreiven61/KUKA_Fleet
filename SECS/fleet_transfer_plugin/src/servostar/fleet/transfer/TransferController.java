package servostar.fleet.transfer;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/mes-transfer", "/interfaces/api/mes-transfer"})
public final class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { this.service = service; }

    @PostMapping(value="/preview", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> preview(@RequestHeader(value="Authorization", required=false) String token,
                                                     @RequestBody Map<String,Object> request) {
        return handle(request, token, false);
    }
    @PostMapping(value="/submit", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> submit(@RequestHeader(value="Authorization", required=false) String token,
                                                    @RequestBody Map<String,Object> request) {
        return handle(request, token, true);
    }
    @PostMapping(value="/progress", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> progress(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return gate(request,token,false); }
    @PostMapping(value="/completed", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> completed(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return gate(request,token,true); }
    private ResponseEntity<Map<String,Object>> gate(Map<String,Object> request,String token,boolean completed) {
        return stage(request,token,completed?"completed":"pickup-started");
    }
    @PostMapping(value="/pickup-completed", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> pickupCompleted(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"pickup-completed"); }
    @PostMapping(value="/drop-started", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> dropStarted(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"drop-started"); }
    @PostMapping(value="/assigned", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> assigned(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"assigned"); }
    @PostMapping(value="/arrived-source", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> arrivedSource(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"arrived-source"); }
    @PostMapping(value="/departed-source", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> departedSource(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"departed-source"); }
    @PostMapping(value="/arrived-dest", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> arrivedDest(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"arrived-dest"); }
    @PostMapping(value="/unassigned", consumes="application/json", produces="application/json")
    public ResponseEntity<Map<String,Object>> unassigned(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String,Object> request) { return stage(request,token,"unassigned"); }
    private ResponseEntity<Map<String,Object>> stage(Map<String,Object> request,String token,String stage) {
        try { return ResponseEntity.ok(service.stage(request,token,stage)); }
        catch(SecurityException ex) { return error(401,"AUTH_REJECTED","Fleet Authorization/preflight rejected."); }
        catch(IllegalArgumentException ex) { return error(400,"INVALID_REQUEST",ex.getMessage()); }
        catch(Exception ex) { return error(503,"STATUS_UNAVAILABLE","Read-only status unavailable; no transport submitted."); }
    }
    private ResponseEntity<Map<String,Object>> handle(Map<String,Object> request, String token, boolean submit) {
        try { return ResponseEntity.ok(submit ? service.submit(request,token) : service.preview(request,token)); }
        catch (SecurityException ex) { return error(401,"AUTH_REJECTED", "Fleet Authorization/preflight rejected."); }
        catch (IllegalArgumentException ex) { return error(400,"INVALID_OR_DUPLICATE_REQUEST", ex.getMessage()); }
        catch (TransferService.PreSubmissionException ex) {
            return ResponseEntity.status(503).body(TransferService.map("success",false,
                "code","STATUS_MONITOR_OR_JOURNAL_UNAVAILABLE","message",ex.getMessage(),
                "submitted",false,"completed",false));
        }
        catch (Exception ex) {
            // Do not expose transport exception bodies/headers or imply the operation failed to arrive.
            return error(502,"VERIFY_JOB_BEFORE_RETRY", "Fleet response unavailable. Query COMMANDID before retrying; do not generate a new ID to replay.");
        }
    }
    private ResponseEntity<Map<String,Object>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(TransferService.map("success",false,"code",code,"message",message,"completed",false));
    }
}
