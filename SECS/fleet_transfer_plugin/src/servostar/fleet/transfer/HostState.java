package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.*;
import java.io.IOException;

/** Separate native-SECS journal. No tokens. Writes must finish before ACK/send.
 * Commands are never replayed. Reports use durable at-least-once delivery. */
public final class HostState {
    public static final class Job {
        public Map<String,Object> request=new LinkedHashMap<>();
        public String phase="SUBMITTING", vehicle="", instance="", control="", controlState="", location="";
        public int next;
        public long controlGeneration;
        public String controlEvidence="";
        public boolean paused;
        public boolean recoveryArmed;
        public String recoveryReviewSource="", recoveryReviewNode="";
        public String recoveryState="", recoveryMission="", recoveryNode="", recoveryInstance="", recoveryCommand="", recoveryDetail="", recoveryModel="";
        public Set<String> responses=new LinkedHashSet<>();
    }
    public static final class Delivery {
        public long id;
        public String key="", kind="", command="", vehicle="", port="", location="", text="", state="PENDING", error="";
        public int ceid, result, category, attempts;
        public long alarmId;
        public boolean set;
        public String carrier="", operation="", outcome="", detail="";
        public List<Map<String,Object>> inventory=new ArrayList<>();
    }
    public static final class CargoOperation {
        public String command="", phase="PREPARING";
        public Map<String,String> request=new LinkedHashMap<>();
    }
    public static final class Data {
        public Map<String,String> vehicleNames=new LinkedHashMap<>();
        public Map<String,Job> jobs=new LinkedHashMap<>();
        public List<Delivery> reports=new ArrayList<>();
        public Set<String> reportKeys=new LinkedHashSet<>();
        public Map<String,Delivery> alarms=new LinkedHashMap<>();
        public Map<String,CargoOperation> cargoOperations=new LinkedHashMap<>();
        public String tsc="AUTO";
        public long sequence=1;
    }
    final Data data;
    private final Path file;
    private final ObjectMapper json=new ObjectMapper();
    private boolean healthy=true;
    public HostState(Path file)throws IOException {
        this.file=file;
        if(file!=null && Files.exists(file) && Files.size(file)>32*1024*1024)throw new IOException("SECS journal exceeds review limit");
        data=file!=null && Files.exists(file)?json.readValue(file.toFile(),Data.class):new Data();
        if(data.jobs.size()>1000 || data.cargoOperations.size()>1000 || data.reports.size()>20000 || data.sequence<1)throw new IOException("Invalid SECS journal");
        for(CargoOperation op:data.cargoOperations.values()) {
            if(!"MUTATING".equals(op.phase) && !"PREPARING".equals(op.phase))continue;
            op.phase="MUTATING".equals(op.phase)?"UNCERTAIN":"REJECTED";
            Delivery d=new Delivery();d.kind="CARGO_RESULT";d.ceid=9002;d.command=op.request.get("REQUESTID");
            d.key="cargo-result:"+d.command;d.operation=op.command;d.outcome=op.phase;d.carrier=op.request.getOrDefault("CARRIERID","");d.port=op.request.getOrDefault("PORT","");
            d.detail="RESTART_REVIEW_NO_REPLAY";add(d);
        }
        for(Job j:data.jobs.values()){
            if("SENDING".equals(j.recoveryState))j.recoveryState="UNCERTAIN";
            if("SUBMITTING".equals(j.phase))j.phase="UNCERTAIN";
            if("SENDING".equals(j.controlState)){j.controlState="UNCERTAIN";if("PAUSE".equals(j.control)||"RESUME".equals(j.control))data.tsc="UNCERTAIN";}
        }
        if(file!=null)save();
    }
    synchronized void requireHealthy(){if(!healthy)throw new IllegalStateException("SECS journal unavailable; manual review required");}
    synchronized void save() {
        requireHealthy();if(file==null)return;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            byte[] bytes=json.writeValueAsBytes(data);
            if(bytes.length>32*1024*1024)throw new IOException("SECS journal capacity");
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
                ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())ch.write(b);ch.force(true);
            }
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException ex){healthy=false;throw new IllegalStateException("SECS journal write failed; no further sends permitted",ex);}
    }
    synchronized void add(Delivery d) {
        requireHealthy();
        if(data.reportKeys.contains(d.key))return;
        if(data.reports.size()>=20000)throw new IllegalStateException("SECS report capacity; manual archival required");
        d.id=data.sequence++;if(d.id>0xffffffffL)throw new IllegalStateException("DATAID exhausted");
        data.reportKeys.add(d.key);data.reports.add(d);
    }
    synchronized Delivery pending(){requireHealthy();for(Delivery d:data.reports)if(!"ACKED".equals(d.state))return d;return null;}
    synchronized boolean unsettled(Job job){
        if(!NativeHostEngine.terminal(job.phase))return true;
        for(Delivery d:data.reports)if(job.request.get("COMMANDID").equals(d.command) && !"ACKED".equals(d.state)
            && Arrays.asList("TRANSFER","VEHICLE","CARRIER").contains(d.kind))return true;
        return false;
    }
    public synchronized Map<String,Object> status(){
        List<Map<String,Object>> reports=new ArrayList<>();
        List<Map<String,Object>> jobs=new ArrayList<>();
        for(Map.Entry<String,Job> entry:data.jobs.entrySet()){
            Job j=entry.getValue();jobs.add(TransferService.map("commandId",entry.getKey(),"phase",j.phase,"control",j.control,"controlState",j.controlState,"paused",j.paused,"vehicle",j.vehicle,"instanceId",j.instance,"controlGeneration",j.controlGeneration,"controlEvidence",j.controlEvidence,"nextReportIndex",j.next,"recoveryArmed",j.recoveryArmed,"recoveryReviewSource",j.recoveryReviewSource,"recoveryReviewNode",j.recoveryReviewNode,"recoveryState",j.recoveryState,"recoveryMission",j.recoveryMission,"recoveryNode",j.recoveryNode,"recoveryCommand",j.recoveryCommand,"recoveryDetail",j.recoveryDetail));
        }
        for(Delivery d:data.reports)if(!"ACKED".equals(d.state))reports.add(TransferService.map("dataId",d.id,"kind",d.kind,"ceid",d.ceid,"commandId",d.command,"state",d.state,"attempts",d.attempts,"error",d.error));
        List<Map<String,Object>> rejected=new ArrayList<>();
        for(int i=data.reports.size()-1;i>=0 && rejected.size()<20;i--){Delivery d=data.reports.get(i);if("COMMAND_REJECT".equals(d.kind))rejected.add(TransferService.map("dataId",d.id,"commandId",d.command,"command",d.operation,"hcack",d.result,"reason",d.detail,"tsc",d.outcome,"blockingId",d.port));}
        List<Map<String,Object>> activeAlarms=new ArrayList<>();
        for(Map.Entry<String,Delivery> entry:data.alarms.entrySet()){
            Delivery a=entry.getValue();activeAlarms.add(TransferService.map("key",entry.getKey(),"alid",a.alarmId,"commandId",a.command,"vehicle",a.vehicle,"code",a.port,"source",a.operation,"text",a.text));
        }
        return TransferService.map("recentRejections",rejected,"healthy",healthy,"tsc",data.tsc,"reports",reports,"activeAlarms",activeAlarms,"jobCount",data.jobs.size(),"jobs",jobs,"cargoOperations",data.cargoOperations);
    }
    public synchronized void retry(long id){
        requireHealthy();
        for(Delivery d:data.reports)if(d.id==id && "BLOCKED".equals(d.state)){d.state="PENDING";d.attempts=0;d.error="Manually authorized report retry";save();return;}
        throw new IllegalArgumentException("Blocked DATAID not found");
    }
}
