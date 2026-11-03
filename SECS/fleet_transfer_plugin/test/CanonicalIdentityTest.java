package servostar.fleet.transfer;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.kuka.ecs.common.transport.secs.secs2.*;
import static com.kuka.ecs.common.transport.secs.secs2.Secs2.*;

/** Live-shaped Fleet IDs: status -> command -> reports -> actual Fleet binding. No live motion. */
public final class CanonicalIdentityTest {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static Secs2 command(String source,String destination){return list(uint4(0),ascii(""),ascii("TRANSFER"),list(
        list(ascii("COMMANDINFO"),list(list(ascii("COMMANDID"),ascii("CANONICAL_TEST")),list(ascii("PRIORITY"),uint2(1)))),
        list(ascii("TRANSFERINFO"),list(list(ascii("CARRIERID"),ascii("SIM_CARGO_001")),list(ascii("SOURCEPORT"),ascii(source)),list(ascii("DESTPORT"),ascii(destination))))));}
    static Secs2 values(HostState.Delivery d,HostState.Job j,VehicleNames names)throws Exception{return HostProtocol.body(d,j,names).get(2).get(0).get(1);}
    @SuppressWarnings("unchecked")
    static void installCatalog(){
        Map<String,Object> catalog=FixtureRunner.catalog();
        for(Map<String,Object> p:(List<Map<String,Object>>)catalog.get("ports")){
            p.put("legacyAlias",p.get("portId"));
            String external=String.valueOf(p.get("externalCode"));p.put("portId",external.isEmpty()?p.get("nodeCode"):external);
        }
        WorkPorts.install(catalog);
    }
    public static void main(String[] args)throws Exception{
        installCatalog();
        HostState state=new HostState(null);
        Path xml=Files.createTempFile("canonical-robots-",".xml");
        Files.write(xml,VehicleNamesTest.xml("AGV001").getBytes(StandardCharsets.UTF_8));
        VehicleNames names=new VehicleNames(state);names.configure(xml);names.refresh(false);names.requireReady();
        check(WorkPorts.canonical("PORT_89").equals("SECS_ST_89") && WorkPorts.canonical("PORT_98").equals("lay01-dist1-98"),"One Fleet-derived canonical PortID, including no-external-name nodes");
        Map<String,Object> request=HostProtocol.transfer(command("lay01-dist1-98","SECS_ST_89"),names);
        check(request.get("SOURCEPORT").equals("lay01-dist1-98") && request.get("DESTPORT").equals("SECS_ST_89"),"Canonical S2F49 IDs remain unchanged");
        Map<String,Object> compatible=HostProtocol.transfer(command("PORT_89","PORT_97"),names);
        check(compatible.get("SOURCEPORT").equals("SECS_ST_89") && compatible.get("DESTPORT").equals("SECS_ST_97"),"Old aliases normalize before durable native intent");
        try{HostProtocol.transfer(command("PORT_89","SECS_ST_89"),names);throw new AssertionError("Same physical endpoint accepted");}
        catch(HostProtocol.ParameterError expected){check(expected.field.equals("DESTPORT"),"Alias cannot bypass same source/destination validation");}
        SelectedStatusTest.Fleet fleet=new SelectedStatusTest.Fleet();
        HostState.Job job=SelectedStatusTest.job(state,"CANONICAL_TEST","ACTIVE");job.request=request;job.vehicle="2";
        SelectedStatusService service=new SelectedStatusService(fleet,state,"fixture",names);
        Set<String> ports=new HashSet<>();for(Secs2 row:SelectedStatusTest.one(service,58))ports.add(row.get(0).getAscii());
        check(ports.contains(request.get("SOURCEPORT")) && ports.contains(request.get("DESTPORT")),"Both job endpoints occur verbatim in SV58");
        Secs2 transfer=SelectedStatusTest.one(service,23).get(0).get(2).get(0);
        check(transfer.get(1).getAscii().equals(request.get("SOURCEPORT")) && transfer.get(2).getAscii().equals(request.get("DESTPORT")),"SV23 matches exact S2F49 source and destination");
        HostState.Delivery d=new HostState.Delivery();d.kind="TRANSFER";d.command="CANONICAL_TEST";d.ceid=207;d.location="PORT_89";
        Secs2 report=values(d,job,names);
        check(report.get(1).get(0).get(0).get(1).getAscii().equals(request.get("SOURCEPORT")) && report.get(1).get(0).get(0).get(2).getAscii().equals(request.get("DESTPORT")),"S6F11 completion matches exact admitted endpoints");
        check(report.get(1).get(0).get(1).getAscii().equals("SECS_ST_89"),"CarrierLoc uses the same PortID as SV58");
        d.kind="VEHICLE";d.vehicle="2";d.port="PORT_89";d.ceid=601;
        report=values(d,job,names);check(report.get(0).getAscii().equals("AGV002") && report.get(2).get(0).getAscii().equals("SECS_ST_89"),"Vehicle report matches SV25 VehicleID and SV58 PortID");
        Map<String,Object> recovery=HostProtocol.transfer(command("AGV002","SECS_ST_89"),names);
        check(recovery.get("SOURCEPORT").equals("2"),"SV25 VehicleID resolves to actual carrying Fleet RobotID");
        job.request=recovery;
        check(SelectedStatusTest.one(service,23).get(0).get(2).get(0).get(1).getAscii().equals("AGV002"),"Recovery SV23 source equals requested/reported VehicleID without AMR prefix");
        d.kind="TRANSFER";d.ceid=208;d.location="2";
        report=values(d,job,names);
        check(report.get(1).get(0).get(0).get(1).getAscii().equals("AGV002") && report.get(1).get(0).get(1).getAscii().equals("AGV002"),"Recovery event source and CarrierLoc use exact VehicleID");
        check(fleet.mutations==0,"Identity/status tests do not dispatch missions");
        System.out.println("PASS: "+checks+" canonical PortID/VehicleID admission, status and report checks.");
    }
}
