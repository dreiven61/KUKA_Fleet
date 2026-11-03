package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;
import com.kuka.ecs.common.transport.secs.*;
import com.kuka.ecs.common.transport.secs.hsmsss.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exact captured jobQuery warning snapshots; local fixtures and HSMS only. */
public final class WorkflowAlarmTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static final class Fleet implements TransferService.FleetClient {
        final Map<String,Map<String,Object>> jobs=new LinkedHashMap<>();
        String defect="";int mutations;
        public Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception{
            if(!path.equals("jobQuery")){mutations++;throw new AssertionError("Alarm poll attempted "+path);}
            if(defect.equals("timeout"))throw new java.io.IOException("fixture timeout");
            Map<String,Object> row=jobs.get(body.get("jobCode"));
            List<Map<String,Object>> rows=row==null?new ArrayList<>():new ArrayList<>(Arrays.asList(row));
            if(defect.equals("empty"))rows.clear();if(defect.equals("duplicate") && row!=null)rows.add(row);
            return TransferService.map("success",!defect.equals("rejected"),"data",rows);
        }
    }
    static WorkflowAlarmMonitor monitor(HostState s,Fleet f){return new WorkflowAlarmMonitor(s,f,NativeHostTest.TOKEN,Collections.emptyMap());}
    static Map<String,Object> copy(Map<String,Object> row){return new LinkedHashMap<>(row);}
    static void own(HostState state,Map<String,Object> row){
        HostState.Job j=new HostState.Job();j.phase="ACTIVE";
        j.request.put("COMMANDID",row.get("jobCode"));j.request.put("CARRIERID",row.get("containerCode"));
        j.instance=String.valueOf(row.get("workflowId"));
        Object robot=row.get("robotId");j.vehicle=robot==null?"":robot.toString();
        state.data.jobs.put((String)row.get("jobCode"),j);state.save();
    }
    static long count(HostState s,String kind,boolean set){return s.data.reports.stream().filter(d->d.kind.equals(kind) && (kind.equals("UNIT")?d.ceid==(set?702:701):d.set==set)).count();}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);ObjectMapper json=new ObjectMapper();
        List<Map<String,Object>> capture=json.readValue(root.resolve("test/fixtures/workflow_warning_20261030.json").toFile(),List.class);
        Map<String,Object> unassigned=(Map<String,Object>)capture.get(0).get("job"),cleared=(Map<String,Object>)capture.get(1).get("job");
        Map<String,Object> assigned=(Map<String,Object>)capture.get(5).get("job"),finished=(Map<String,Object>)capture.get(6).get("job");
        check("200103".equals(unassigned.get("warnCode")) && unassigned.get("robotId")==null,"Real warning6899 before robot assignment");
        check("200103".equals(assigned.get("warnCode")) && assigned.get("robotId").equals("2"),"Real warning6900 after assignment");
        for(Map<String,Object> warning:Arrays.asList(unassigned,assigned)){
            boolean vehicle=warning.get("robotId")!=null;String id=(String)warning.get("jobCode");
            Fleet f=new Fleet();HostState s=new HostState(null);own(s,warning);f.jobs.put(id,warning);WorkflowAlarmMonitor m=monitor(s,f);
            m.poll();m.poll();m.poll();
            check(count(s,"ALARM",true)==1 && count(s,"ALARM",false)==0,"One set while captured warning persists");
            check(count(s,"UNIT",true)==(vehicle?1:0),"Never invent a vehicle for preassignment warning");
            HostState.Delivery set=s.data.reports.stream().filter(d->d.kind.equals("ALARM")).findFirst().get();
            check(set.alarmId==1200103 && (HostProtocol.body(set,null).get(0).getByte(0)&255)==0x86,"Explicit numeric test ALID and set bit");
            for(String defect:Arrays.asList("timeout","empty","duplicate","rejected")){
                f.defect=defect;m.poll();check(count(s,"ALARM",false)==0 && s.data.alarms.size()==1,"No false clear on "+defect);
            }
            f.defect="";
            for(String defect:Arrays.asList("identity","carrier","instance","missingFlag","invalidFlag","missingCode","vehicle","status")){
                Map<String,Object> bad=copy(warning);
                if(defect.equals("identity"))bad.put("jobCode","OTHER");if(defect.equals("carrier"))bad.put("containerCode","OTHER");
                if(defect.equals("instance"))bad.put("workflowId",999999);if(defect.equals("missingFlag"))bad.remove("warnFlag");
                if(defect.equals("invalidFlag"))bad.put("warnFlag",2);if(defect.equals("missingCode"))bad.remove("warnCode");
                if(defect.equals("vehicle"))bad.put("robotId","99");if(defect.equals("status"))bad.put("status",999);
                f.jobs.put(id,bad);m.poll();check(count(s,"ALARM",false)==0 && s.data.alarms.size()==1,"Invalid snapshot retains warning: "+defect);
            }
            Map<String,Object> clear=copy(vehicle?finished:cleared);clear.remove("warnCode");f.jobs.put(id,clear);
            s.data.jobs.get(id).phase="COMPLETED";m.poll();m.poll();
            check(count(s,"ALARM",false)==1 && s.data.alarms.isEmpty(),"Terminal host job still reconciles one confirmed clear; null-omitted code allowed");
            check(count(s,"UNIT",false)==(vehicle?1:0),"Clear paired only with actual unit set");
            HostState.Delivery end=s.data.reports.stream().filter(d->d.kind.equals("ALARM") && !d.set).findFirst().get();
            check(end.alarmId==set.alarmId && (HostProtocol.body(end,null).get(0).getByte(0)&255)==6,"Same ALID and clear bit");
            s.data.jobs.get(id).phase="ACTIVE";f.jobs.put(id,warning);m.poll();
            check(count(s,"ALARM",true)==2,"Same warning can reoccur after actual clear");
            check(f.mutations==0,"Polling never changes jobs or sends robot commands");
        }
        // Two real captured jobs share ALID1200103: clearing one must not
        // falsely tell MCS that the other job's warning has recovered.
        Fleet f=new Fleet();Path disk=Files.createTempDirectory("workflow-alarm-").resolve("state.json");HostState s=new HostState(disk);
        own(s,unassigned);own(s,assigned);String first=(String)unassigned.get("jobCode"),second=(String)assigned.get("jobCode");
        f.jobs.put(first,unassigned);f.jobs.put(second,assigned);monitor(s,f).poll();
        check(count(s,"ALARM",true)==1 && count(s,"UNIT",true)==1 && s.data.alarms.size()==2,"Overlapping warnings aggregate S5 by ALID and unit by actual AMR");
        HostState restored=new HostState(disk);monitor(restored,f).poll();
        check(count(restored,"ALARM",true)==1,"Restart retains active warning without a duplicate set");
        f.jobs.put(first,cleared);monitor(restored,f).poll();
        check(count(restored,"ALARM",false)==0 && restored.data.alarms.size()==1,"First recovery cannot clear still-active shared ALID");
        f.jobs.put(second,finished);monitor(restored,f).poll();monitor(restored,f).poll();
        check(count(restored,"ALARM",false)==1 && count(restored,"UNIT",false)==1,"Last recovery clears shared ALID once");
        check(((List<?>)restored.status().get("activeAlarms")).isEmpty(),"Authenticated status exposes active alarm reconciliation");
        Map<String,Object> mapped=TransferService.map("alid",900003,"category",6,"text","CUSTOM_WORKFLOW_WARNING");
        HostState explicit=new HostState(null);own(explicit,assigned);f.jobs.put(second,assigned);
        new WorkflowAlarmMonitor(explicit,f,NativeHostTest.TOKEN,Collections.singletonMap("200103",mapped)).poll();
        check(explicit.data.reports.get(0).alarmId==900003,"Explicit project/customer mapping overrides numeric test fallback");
        NativeHostTest.Harness mixed=new NativeHostTest.Harness(root,null);
        Map<String,Object> event=mixed.event("WARNING",false);event.put("warnCode","PP_NoPath");mixed.engine.observe(event);
        Map<String,Object> callbackRow=TransferService.map("jobCode","TR_TEST_001","workflowId",6837,"containerCode","SIM_CARGO_001",
            "robotId","1","status",50,"warnFlag",1,"warnCode","PP_NoPath");
        own(mixed.state,callbackRow);Fleet shared=new Fleet();shared.jobs.put("TR_TEST_001",callbackRow);
        WorkflowAlarmMonitor sharedMonitor=new WorkflowAlarmMonitor(mixed.state,shared,NativeHostTest.TOKEN,
            Collections.singletonMap("PP_NoPath",TransferService.map("alid",900001,"category",6,"text","FLEET_PATH_NOT_FOUND")));
        sharedMonitor.poll();mixed.engine.observe(event);
        check(count(mixed.state,"ALARM",true)==1 && count(mixed.state,"UNIT",true)==1,"Callback and poll sources cannot duplicate an active alarm");
        Map<String,Object> callbackClear=mixed.event("WARN_CLEAR",true);callbackClear.put("warnCode","PP_NoPath");mixed.engine.observe(callbackClear);
        check(count(mixed.state,"ALARM",false)==0,"One source cannot clear an alarm still active in another confirmed source");
        Map<String,Object> noWarn=copy(callbackRow);noWarn.put("warnFlag",0);noWarn.remove("warnCode");shared.jobs.put("TR_TEST_001",noWarn);sharedMonitor.poll();
        check(count(mixed.state,"ALARM",false)==1 && count(mixed.state,"UNIT",false)==1,"Last confirmed source recovery clears once");
        wire(root,json);
        System.out.println("PASS: "+checks+" captured workflow warning set/hold/clear, identity, aggregation, persistence and real loopback HSMS ACK checks; no live Fleet calls.");
    }
    static void wire(Path root,ObjectMapper json)throws Exception{
        AtomicBoolean warning=new AtomicBoolean(false);AtomicInteger mutations=new AtomicInteger();
        NativeHostTest.Harness h=new NativeHostTest.Harness(root,null){
            @Override Map<String,Object> post(String path,String token,Map<String,Object> body)throws Exception{
                if(path.equals("jobQuery") && body.containsKey("status"))return TransferService.map("success",true,"data",Collections.emptyList());
                if(path.equals("jobQuery"))return TransferService.map("success",true,"data",Arrays.asList(TransferService.map(
                    "jobCode","TR_TEST_001","workflowId",6837,"containerCode","SIM_CARGO_001","robotId","1","status",20,
                    "warnFlag",warning.get()?1:0,"warnCode",warning.get()?"200103":null)));
                if(path.equals("submitMission"))mutations.incrementAndGet();return super.post(path,token,body);
            }
        };
        int port;try(ServerSocket sock=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=sock.getLocalPort();}
        Path dir=Files.createTempDirectory("workflow-alarm-wire-"),cfg=dir.resolve("config.json");
        json.writeValue(cfg.toFile(),TransferService.map("enabled",true,"bind","127.0.0.1","port",port,"sessionId",3000,"t3Seconds",1,
            "authorization",NativeHostTest.TOKEN,"journal",dir.resolve("state.json").toString()));
        String previous=System.getProperty("servostar.secs.config");System.setProperty("servostar.secs.config",cfg.toString());
        NativeHostService server=new NativeHostService(h.service,h.client,new WcsStatusMonitor(h.journal));HsmsSsCommunicator host=null;
        List<Integer> received=new CopyOnWriteArrayList<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        try{
            server.start();HsmsSsCommunicatorConfig pc=new HsmsSsCommunicatorConfig();pc.isEquip(false);pc.sessionId(3000);
            pc.protocol(HsmsSsProtocol.ACTIVE);pc.socketAddress(new InetSocketAddress("127.0.0.1",port));pc.timeout().t3(3);pc.timeout().t5(1);
            host=HsmsSsCommunicator.newInstance(pc);
            host.addSecsMessageReceiveListener((SecsMessageReceiveBiListener)(m,c)->{try{
                if(m.getStream()==5 && m.getFunction()==1){
                    if(m.secs2().get(1).getLong(0)!=1200103)throw new AssertionError("Wrong workflow ALID on wire");
                    received.add((m.secs2().get(0).getByte(0)&128)!=0?5001:5000);c.send(m,5,2,false,binary((byte)0));
                }else if(m.getStream()==6 && m.getFunction()==11){received.add(m.secs2().get(1).getInt(0));c.send(m,6,12,false,binary((byte)0));}
            }catch(Throwable ex){failure.set(ex);}});
            host.open();NativeHostTest.waitFor(host::isCommunicatable,"Workflow wire HSMS connected");host.send(1,13,true,list());
            check(NativeHostTest.ack(host.send(2,49,true,NativeHostTest.transferBody("TR_TEST_001")).get().secs2())==4,"Workflow wire accepts sole transfer");
            warning.set(true);NativeHostTest.waitFor(()->received.contains(702),"Poll-generated S5F1 set ACK then702");
            Thread.sleep(2200);
            check(Collections.frequency(received,5001)==1 && Collections.frequency(received,702)==1,"Repeated scheduled polls hold one set on real wire");
            warning.set(false);NativeHostTest.waitFor(()->received.contains(701),"Poll-generated S5F1 clear ACK then701");Thread.sleep(1200);
            check(Collections.frequency(received,5000)==1 && Collections.frequency(received,701)==1,"One clear on real wire");
            check(received.indexOf(5001)<received.indexOf(702) && received.indexOf(5000)<received.indexOf(701),"Alarm and unit reports preserve ACKed order");
            check(mutations.get()==1 && !received.contains(207),"Alarm polling never resubmits or invents completion");
            check(failure.get()==null,"Wire payload and ACK processing succeeded");
        }finally{if(host!=null)host.close();server.close();if(previous==null)System.clearProperty("servostar.secs.config");else System.setProperty("servostar.secs.config",previous);}
    }
}
