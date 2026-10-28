package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Records only this plugin's reserved commands. No tokens, sends, or robot actions. */
public final class TransferJournal {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String,Entry> entries = new LinkedHashMap<>();
    private final Path file;
    private boolean streamReady, storageHealthy = true;
    static final class Entry {
        final Map<String,Object> request;
        boolean accepted, uncertain, pickup, pickupCompleted, dropStarted, drop, completed, canceled;
        boolean assigned, sourceArrived, sourceDeparted, destArrived;
        String robotId="", instance="", pickupTime="", completeTime="", lastStatus="";
        final Set<String> responses = new HashSet<>();
        Entry(Map<String,Object> request) { this.request = new LinkedHashMap<>(request); }
    }
    public TransferJournal(Path file) throws IOException {
        this.file=file;
        if(file!=null) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if(Files.exists(file)) {
                if(Files.size(file)>32*1024*1024) throw new IOException("Journal requires archival/review");
                // Read only complete valid records; a torn record fails startup.
                for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                    if(!line.trim().isEmpty()) apply(json.readValue(line,Map.class));
                }
                // A stream gap across restart can hide pickup/drop/cancel events.
                // Preserve IDs and block incomplete entries; never replay submissions.
                for(Entry e:entries.values()) if(!e.completed) e.uncertain=true;
            }
        }
    }
    public synchronized void streamReady() { streamReady=true; }
    public synchronized void streamLost() {
        streamReady=false;
        Map<String,Object> record=TransferService.map("kind","GAP");
        try { append(record); } catch(IOException ex) { storageHealthy=false; }
        apply(record);
    }
    public synchronized boolean available() { return streamReady && storageHealthy; }
    public synchronized void reserve(Map<String,Object> request) throws IOException {
        String id=(String)request.get("COMMANDID");
        if(entries.containsKey(id)) throw new IllegalArgumentException("COMMANDID already attempted. Query its job; do not replay it.");
        if(!available()) throw new IllegalStateException("Status stream/journal unavailable; no submission performed.");
        if(entries.size()>=1000) throw new IllegalStateException("Journal capacity reached; review required.");
        record(TransferService.map("kind","RESERVE","request",new LinkedHashMap<>(request)));
    }
    public synchronized void accepted(String id) throws IOException {
        record(TransferService.map("kind","ACCEPTED","commandId",id));
    }
    public synchronized void observe(Map<String,Object> event) {
        if(!entries.containsKey(event.get("commandId"))) return;
        try { record(TransferService.map("kind","EVENT","event",event)); }
        catch(IOException ex) { storageHealthy=false; streamReady=false; }
    }
    public synchronized Map<String,Object> state(Map<String,Object> request, boolean completion) {
        return stage(request,completion ? "completed" : "pickup-started");
    }
    public synchronized Map<String,Object> stage(Map<String,Object> request, String stage) {
        Entry e=entries.get(request.get("COMMANDID"));
        if(e==null) return result(false,"UNKNOWN_COMMAND",null);
        if(!e.request.equals(request)) return result(false,"COMMAND_CONTEXT_MISMATCH",null);
        if(!storageHealthy || e.uncertain) return result(false,"STATUS_UNCERTAIN",e);
        if(e.canceled) return result(false,"CANCELED",e);
        boolean observed;
        switch(stage) {
            case "assigned": observed=e.assigned; break;
            case "arrived-source": observed=e.assigned && e.sourceArrived; break;
            case "departed-source": observed=e.assigned && e.sourceArrived && e.pickupCompleted && e.sourceDeparted; break;
            case "arrived-dest": observed=e.assigned && e.sourceArrived && e.sourceDeparted && e.destArrived; break;
            case "unassigned": observed=e.assigned && e.sourceArrived && e.sourceDeparted && e.destArrived && e.completed && e.drop && e.pickup; break;
            case "pickup-started": observed=e.pickup; break;
            case "pickup-completed": observed=e.pickup && e.pickupCompleted; break;
            case "drop-started": observed=e.pickup && e.pickupCompleted && e.dropStarted; break;
            case "completed": observed=e.completed && e.drop && e.pickup; break;
            default: throw new IllegalArgumentException("Unknown reporting stage");
        }
        boolean ready=e.accepted && observed;
        return result(ready,ready?"0":"WAITING",e);
    }
    private Map<String,Object> result(boolean ready,String code,Entry e) {
        return TransferService.map("success",ready,"code",code,
            "observedTime",e==null?"":e.completeTime,
            "pickupTime",e==null?"":e.pickupTime,
            "instanceId",e==null?"":e.instance,
            "robotId",e==null?"":e.robotId,
            // ECS serializes mapped string fields with JSON quotes; expose a numeric ID too.
            "vehicleId",e==null || e.robotId.isEmpty()?0:Integer.parseInt(e.robotId),
            "lastStatus",e==null?"":e.lastStatus);
    }
    private void record(Map<String,Object> record) throws IOException { append(record); apply(record); }
    private void append(Map<String,Object> record) throws IOException {
        if(file==null) return;
        if(Files.exists(file) && Files.size(file)>32*1024*1024) throw new IOException("Journal capacity");
        byte[] bytes=(json.writeValueAsString(record)+"\n").getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
            ByteBuffer buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
    @SuppressWarnings("unchecked")
    private void apply(Map<String,Object> record) {
        String kind=(String)record.get("kind");
        if("RESERVE".equals(kind)) {
            Map<String,Object> req=(Map<String,Object>)record.get("request");
            entries.put((String)req.get("COMMANDID"),new Entry(req)); return;
        }
        if("GAP".equals(kind)) { for(Entry e:entries.values()) if(!e.completed) e.uncertain=true; return; }
        if("ACCEPTED".equals(kind)) { Entry e=entries.get(record.get("commandId")); if(e!=null)e.accepted=true; return; }
        if(!"EVENT".equals(kind)) throw new IllegalArgumentException("Unknown journal record");
        Map<String,Object> v=(Map<String,Object>)record.get("event");
        Entry e=entries.get(v.get("commandId")); if(e==null)return;
        String response=text(v,"responseId");
        if(response.isEmpty() || !e.responses.add(response)) return;
        if(e.responses.size()>10000) {e.uncertain=true;return;}
        if(!e.request.get("CARRIERID").equals(v.get("carrierId"))) return;
        String vehicle=text(v,"robotId");
        if(!Arrays.asList("1","2","3").contains(vehicle)) return;
        String instance=text(v,"instanceId");
        if(instance.isEmpty()) return;
        if(e.instance.isEmpty()) e.instance=instance;
        if(!e.instance.equals(instance)) {e.uncertain=true;return;}
        String status=text(v,"status"); e.lastStatus=status;
        if(e.completed || e.canceled) return;
        if(!Boolean.TRUE.equals(v.get("success")) || !"0".equals(text(v,"resultCode"))) return;
        if(((Number)v.get("manualCompleteFlag")).intValue()!=0) {e.uncertain=true;return;}
        if("CANCEL_COMPLETED".equals(status)) {e.canceled=true;return;}
        if(e.robotId.isEmpty()) e.robotId=vehicle;
        else if(!e.robotId.equals(vehicle)) {e.uncertain=true;return;}
        String source=node((String)e.request.get("SOURCEPORT"));
        String dest=node((String)e.request.get("DESTPORT"));
        int sequence=((Number)v.get("sequence")).intValue();
        String current=text(v,"currentNode"), action=text(v,"actionType");
        // No vehicle is pinned in submitMission. A
        // successful, correlated WCS move toward the source carries the actual robot.
        if("MOVE_BEGIN".equals(status) && sequence==1 && source.equals(text(v,"nextNode"))) e.assigned=true;
        if("MOVE_END".equals(status) && sequence==1 && source.equals(current)) {
            if(e.assigned) e.sourceArrived=true; else e.uncertain=true;
        }
        if("MOVE_BEGIN".equals(status) && sequence==2 && source.equals(current) && dest.equals(text(v,"nextNode"))) {
            if(e.assigned && e.sourceArrived && e.pickupCompleted) e.sourceDeparted=true; else e.uncertain=true;
        }
        if("MOVE_END".equals(status) && sequence==2 && dest.equals(current)) {
            if(e.sourceDeparted) e.destArrived=true; else e.uncertain=true;
        }
        if("ACTION_BEGIN".equals(status) && "UP_CONTAINER".equals(action) && sequence==1 && source.equals(current)) {
            if(!e.pickup) e.pickupTime=text(v,"actionTime");
            e.pickup=true;
        }
        if("ACTION_END".equals(status) && "UP_CONTAINER".equals(action) && sequence==1 && source.equals(current)) {
            if(e.pickup) e.pickupCompleted=true;
            else e.uncertain=true;
        }
        if("ACTION_BEGIN".equals(status) && "DOWN_CONTAINER".equals(action) && sequence==2 && dest.equals(current)) {
            if(e.pickup && e.pickupCompleted) e.dropStarted=true;
            else e.uncertain=true;
        }
        if("ACTION_END".equals(status) && "DOWN_CONTAINER".equals(action) && sequence==2 && dest.equals(current)) e.drop=true;
        if("TASK_COMPLETED".equals(status) && sequence==2 && dest.equals(current)
                && dest.equals(text(v,"carrierLocation")) && !text(v,"endTime").isEmpty()) {
            // Prior pickup and drop must have been observed in this uninterrupted subscription.
            if(e.pickup && e.drop) {e.completed=true;e.completeTime=text(v,"endTime");}
            else e.uncertain=true;
        }
    }
    static String node(String port) { return WorkPorts.node(port); }
    private static String text(Map<String,Object> m,String key) { Object v=m.get(key);return v==null?"":v.toString(); }
}
