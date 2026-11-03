using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class WireCatalogTests
{
    public static void Fixture() => WorkPorts.Install(["PORT_84", "PORT_86", "PORT_89", "PORT_90", "PORT_93", "PORT_94", "PORT_95", "PORT_96", "PORT_97", "PORT_98"], ["1", "2", "3", "AGV001", "AGV002", "001", "100001"]);
    public static void Run(Action<bool,string> check)
    {
        WorkPorts.Reset();
        check(!WorkPorts.Ready && WorkPorts.All.Count == 0, "No built-in production site identifiers");
        using var ports = new SecsMessage(1,4,false) { SecsItem = L(L(L(A("SECS_ST_89"),U2(2),U2(2),A(""),U2(0)),L(A("lay01-dist1-98"),U2(2),U2(2),A(""),U2(0)))) };
        using var vehicles = new SecsMessage(1,4,false) { SecsItem = L(L(L(A("AGV001"),U2(2),U2(1)),L(A("AGV002"),U2(2),U2(1)))) };
        StatusQueryResult Result(SecsMessage wire, ushort id) => new(DateTimeOffset.Now, id.ToString(), 1, true,"",Protocol.Sml(wire),SelectedStatusProtocol.Parse(wire,[id]));
        WorkPorts.Apply(Result(ports,58)); check(!WorkPorts.Ready, "Port query alone cannot enable transfer");
        WorkPorts.Apply(Result(vehicles,25)); check(WorkPorts.Ready, "Same-session port/vehicle status enables exact-ID inputs");
        check(WorkPorts.All.SequenceEqual(["SECS_ST_89","lay01-dist1-98"]) && WorkPorts.Vehicles.SequenceEqual(["AGV001","AGV002"]), "Status identifiers retained byte-for-byte");
        check(!WorkPorts.IsSource("AMR_AGV001") && !WorkPorts.IsSource("PORT_89") && !WorkPorts.IsSource("1"), "MCS never invents aliases for wire identities");
        var request = new TransferRequest("CANONICAL_JOB","CARRIER_001","AGV002","SECS_ST_89");
        using var command = Protocol.Transfer(request);
        check(command.SecsItem![3][1][1][1][1].GetString() == "AGV002" && command.SecsItem[3][1][1][2][1].GetString() == "SECS_ST_89", "Recovery SOURCEPORT equals SV25 VehicleID and destination equals SV58 PortID");
        var portRequest = request with { SourcePort="lay01-dist1-98" };
        using var portCommand=Protocol.Transfer(portRequest);
        check(portCommand.SecsItem![3][1][1][1][1].GetString()=="lay01-dist1-98", "Unnamed external port retains reported Fleet node ID");
        string file = Path.Combine(Path.GetTempPath(),"McsCanonical_"+Guid.NewGuid().ToString("N"),"jobs.jsonl");
        var store=new JobStore(file); store.Reserve(request); store.Acknowledge(request.CommandId,4);
        WorkPorts.Reset();
        check(!store.ReportVehicle(new(604,"AGV001",request.CommandId,request.SourcePort,request.CarrierId)), "Persisted recovery owner cannot change after disconnect/catalog invalidation");
        check(store.ReportVehicle(new(604,"AGV002",request.CommandId,request.SourcePort,request.CarrierId)), "Same wire VehicleID correlates after disconnect");
        check(store.Report(new(207,request.CommandId,request.CarrierId,request.SourcePort,request.DestPort,request.DestPort,0)), "Request and completion use identical source/destination IDs");
        check(new JobStore(file).Snapshot().Single().VehicleId=="AGV002", "Assigned VehicleID persists without local remapping");
        WorkPorts.Apply(Result(ports,58));WorkPorts.Apply(Result(vehicles,25));
        WorkPorts.Apply(new(DateTimeOffset.Now,"58",1,false,"query failed","",[]));
        check(!WorkPorts.Ready,"Failed status blocks new jobs without creating an empty success snapshot");
        Fixture();
    }
}
