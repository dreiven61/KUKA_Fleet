package servostar.fleet.transfer;

import com.kuka.ecs.common.transport.secs.secs2.*;
import java.nio.file.*;
import java.util.*;

/** Rejection classification, correlation and journal recovery; no live requests. */
public final class RejectionTest {
    static int checks;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static HostState.Delivery last(NativeHostTest.Harness h){return h.state.data.reports.get(h.state.data.reports.size()-1);}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        Map<String,Object> request=HostProtocol.transfer(NativeHostTest.transferBody("TR_TEST_001"));
        for(String tsc:Arrays.asList("PAUSING","PAUSED","RESUMING","UNCERTAIN")){
            Path file=Files.createTempDirectory("reject-test-").resolve("state.json");
            NativeHostTest.Harness h=new NativeHostTest.Harness(root,file);
            HostState.Job old=new HostState.Job();old.request.putAll(request);old.request.put("COMMANDID","OLD_JOB");old.phase="ACTIVE";old.control="PAUSE";old.controlState="ACCEPTED";
            h.state.data.jobs.put("OLD_JOB",old);h.state.data.tsc=tsc;
            check(NativeHostTest.ack(h.engine.transfer(request))==2 && h.submits==0,"TSC guard never submits "+tsc);
            HostState.Delivery d=last(h);
            check(d.detail.equals("TSC_NOT_AUTO") && d.command.equals("TR_TEST_001") && d.outcome.equals(tsc) && d.port.equals("OLD_JOB"),"Exact state and blocker "+tsc);
            Secs2 body=h.engine.reportBody(d),v=body.get(2).get(0).get(1);
            check(body.get(1).getInt(0)==9003 && body.get(2).get(0).get(0).getInt(0)==92 && v.size()==6,"Separate CEID9003 RPT92");
            check(v.get(2).secs2Item()==Secs2Item.BINARY && v.get(2).getByte(0)==2 && v.get(4).getAscii().equals(tsc),"Wire HCACK reason state");
            HostState recovered=new HostState(file);
            check(recovered.pending().id==d.id && recovered.pending().detail.equals(d.detail),"Same diagnostic DATAID survives restart");
            check(h.count(208)==0 && h.count(207)==0,"Rejection never fakes lifecycle");
        }
        CargoTest.Harness missing=new CargoTest.Harness(root);
        Map<String,Object> r=new LinkedHashMap<>(request);r.put("CARRIERID","UNKNOWN");
        check(NativeHostTest.ack(missing.engine.transfer(r))==6 && missing.submits==0,"Unregistered carrier is missing object HCACK6");
        check(last(missing).detail.equals("CARRIER_NOT_REGISTERED"),"Missing carrier reason");
        CargoTest.Harness wrong=new CargoTest.Harness(root);wrong.containers.put("SIM_CARGO_001",CargoTest.Harness.row("SIM_CARGO_001","PORT_84",1));
        Secs2 ack=wrong.engine.transfer(request);
        check(NativeHostTest.ack(ack)==3 && ack.get(1).get(0).get(0).getAscii().equals("SOURCEPORT") && ack.get(1).get(0).get(1).getByte(0)==2,"Wrong source HCACK3 SOURCEPORT CPACK2");
        check(wrong.submits==0 && last(wrong).detail.equals("CARRIER_NOT_AT_PORT_OR_CARRIED"),"Wrong source no submit");
        CargoTest.Harness occupied=new CargoTest.Harness(root);occupied.containers.put("SIM_CARGO_001",CargoTest.Harness.row("SIM_CARGO_001","PORT_89",1));occupied.containers.put("OTHER",CargoTest.Harness.row("OTHER","PORT_97",1));
        check(NativeHostTest.ack(occupied.engine.transfer(request))==2 && occupied.submits==0 && last(occupied).detail.equals("DESTINATION_OCCUPIED"),"Occupied destination is unavailable state, no invented CPACK");
        NativeHostTest.Harness duplicate=new NativeHostTest.Harness(root,null);duplicate.engine.transfer(request);
        check(NativeHostTest.ack(duplicate.engine.transfer(request))==5 && duplicate.submits==1 && last(duplicate).detail.equals("DUPLICATE_COMMAND_ID"),"Duplicate5 no replay");
        check(NativeHostTest.ack(duplicate.engine.control("ABORT","TR_TEST_001"))==2 && last(duplicate).detail.equals("ABORT_BEFORE_PICKUP_USE_CANCEL"),"Prepickup ABORT reason");
        duplicate.loaded();
        check(NativeHostTest.ack(duplicate.engine.control("CANCEL","TR_TEST_001"))==2 && last(duplicate).detail.equals("CANCEL_AFTER_PICKUP_USE_ABORT"),"Postpickup CANCEL reason");
        check(NativeHostTest.ack(duplicate.engine.control("CANCEL","MISSING"))==6 && last(duplicate).detail.equals("COMMAND_NOT_FOUND"),"Missing control object6");
        duplicate.state.data.tsc="PAUSING";
        check(NativeHostTest.ack(duplicate.engine.control("RESUME",""))==2 && last(duplicate).detail.equals("RESUME_REQUIRES_PAUSED"),"Invalid RESUME reason");
        NativeHostTest.Harness lost=new NativeHostTest.Harness(root,null);lost.lost=true;
        try{lost.engine.transfer(request);throw new AssertionError("Unknown submission reported as rejected");}catch(NativeHostEngine.UncertainCommand expected){checks++;}
        check(lost.count(9003)==0 && lost.state.data.jobs.get("TR_TEST_001").phase.equals("UNCERTAIN"),"Lost mutation does not send false rejection");
        System.out.println("PASS: "+checks+" HCACK/rejection reason, no-mutation and durable report checks.");
    }
}
