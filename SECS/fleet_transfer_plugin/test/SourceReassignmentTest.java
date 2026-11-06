package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Replay the captured 6923 tentative-vehicle change; no Fleet connection. */
public final class SourceReassignmentTest {
    private static final String ID="C03E80189DB14B19A6DF10CDB79A5183";
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}

    private static final class Fixture {
        final Map<String,Object> request=TransferService.normalize(TransferService.map("COMMANDID",ID,
            "PRIORITY",1,"REPLACE",0,"CARRIERID","SIM_CARGO_001","SOURCEPORT","PORT_97","DESTPORT","PORT_90"));
        final TransferJournal journal;
        final HostState state;
        final NativeHostEngine engine;
        final TransferService service;
        boolean carried;
        String destination="PORT_90";
        Fixture(Path file,HostState restored)throws Exception{
            journal=new TransferJournal(file);journal.streamReady();
            state=restored==null?new HostState(null):restored;
            TransferService.FleetClient client=(path,token,body)->{
                Object data;
                if("jobQuery".equals(path))data=Collections.singletonList(TransferService.map("jobCode",ID,
                    "workflowId",6923,"robotId","1","containerCode","SIM_CARGO_001","status",carried?20:30,
                    "warnFlag",0,"workflowPriority",1,"completeTime","observed","finalNodeCode",WorkPorts.node("PORT_90"),
                    "targetCellCode",WorkPorts.node("PORT_90")));
                else if("containerQuery".equals(path) || "containerQueryAll".equals(path))
                    data=Collections.singletonList(TransferService.map("containerCode","SIM_CARGO_001",
                        "nodeCode",WorkPorts.containerNode(destination),"inMapStatus",1,"isCarry",carried?1:0,"containerModelCode","SIM_TRAY_001"));
                else if("robotQuery".equals(path))data=Collections.singletonList(TransferService.map("robotId","1",
                    "status",carried?4:3,"occupyStatus",carried?1:0,"containerCode",carried?"SIM_CARGO_001":"",
                    "nodeCode",WorkPorts.node(carried?"PORT_97":destination),"liftStatus",carried?1:0,
                    "missionCode",carried?ID:"","rcsMissionCode",carried?"W-TEST-6923-1":"","rcsMissionStatus",carried?9:0));
                else throw new AssertionError("Unexpected mutation "+path);
                return TransferService.map("success",true,"code","0","data",data);
            };
            service=new TransferService(client,journal);
            engine=new NativeHostEngine(service,client,state,NativeHostTest.TOKEN,Collections.emptyMap(),Collections.emptyMap(),9000);
            if(restored==null){
                journal.reserve(request);journal.accepted(ID);
                HostState.Job job=new HostState.Job();job.request=new LinkedHashMap<>(request);job.phase="ACTIVE";job.next=1;
                state.data.jobs.put(ID,job);
            }
        }
        void observe(Map<String,Object> fact)throws Exception{
            journal.observe(fact);engine.observe(fact);engine.tick();
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        List<Map<String,Object>> facts=new ObjectMapper().readValue(Paths.get(args[0],"test","fixtures",
            "source_reassignment_20261106.json").toFile(),List.class);
        check(facts.size()==13 && "3".equals(facts.get(0).get("robotId")) && "1".equals(facts.get(1).get("robotId")),
            "Captured source arrival changes tentative vehicle 3 to actual vehicle 1");
        Path file=Files.createTempDirectory("source-reassignment-").resolve("journal.jsonl");
        Fixture live=new Fixture(file,null);
        live.observe(facts.get(0));
        check(live.state.data.jobs.get(ID).next==2,"Original assignment is reported");
        HostState previous=new HostState(null);
        HostState.Job old=new HostState.Job();old.request=new LinkedHashMap<>(live.request);
        old.phase="ACTIVE";old.next=2;old.vehicle="3";old.instance="6923";previous.data.jobs.put(ID,old);
        for(int i=1;i<facts.size();i++)live.observe(facts.get(i));
        check("COMPLETED".equals(live.state.data.jobs.get(ID).phase),"Captured reassignment reaches transport completion");
        check(live.state.data.reports.stream().anyMatch(d->d.ceid==610 && "3".equals(d.vehicle) && "PORT_97".equals(d.port)),
            "Tentative vehicle is released at the source");
        check(live.state.data.reports.stream().anyMatch(d->d.ceid==604 && "1".equals(d.vehicle)),
            "Actual pickup owner is assigned");
        Fixture replay=new Fixture(file,previous);replay.engine.tick();
        check("COMPLETED".equals(previous.data.jobs.get(ID).phase) && "1".equals(previous.data.jobs.get(ID).vehicle),
            "Older stuck host state reconciles against complete recorded reassignment evidence");

        Fixture update=new Fixture(null,null);update.carried=true;
        for(int i=0;i<=6;i++)update.observe(facts.get(i));
        AtomicInteger writes=new AtomicInteger();
        check(NativeHostTest.ack(update.engine.updateDestination(ID,1,"PORT_89",(id,node)->{
            check(ID.equals(id) && WorkPorts.node("PORT_89").equals(node),"Redirect targets the actual command and new node");
            writes.incrementAndGet();return true;
        },(id,priority)->{throw new AssertionError("Destination update wrote priority");}))==4,"Reassigned loaded owner accepts destination UPDATE");
        for(int i=6;i<facts.size();i++){
            Map<String,Object> fact=new LinkedHashMap<>(facts.get(i));fact.put("responseId","redirect-"+i);fact.put("sequence",3);
            for(String key:Arrays.asList("currentNode","nextNode","carrierLocation"))
                if(WorkPorts.node("PORT_90").equals(fact.get(key)))fact.put(key,WorkPorts.node("PORT_89"));
            if(i==facts.size()-1){update.carried=false;update.destination="PORT_89";}
            update.observe(fact);
        }
        check(writes.get()==1 && "PORT_89".equals(update.state.data.jobs.get(ID).request.get("DESTPORT")),
            "One redirect updates the destination after WCS evidence");
        check(update.state.data.reports.stream().anyMatch(d->d.ceid==212 && "PORT_89".equals(d.destination))
            && update.state.data.reports.stream().anyMatch(d->d.ceid==207 && "PORT_89".equals(d.location)),
            "Reassignment, destination confirmation and new-port completion stay correlated");
        System.out.println("PASS: "+checks+" captured source reassignment, legacy replay and destination-update checks; local only.");
    }
}
