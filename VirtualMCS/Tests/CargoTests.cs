using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class CargoTests
{
    public static void Run()
    {
        int checks = 0;
        void Check(bool valid, string message) { if (!valid) throw new Exception("Cargo: " + message); checks++; }
        Check(new ConnectionSettings().Port == 5556,"Existing Fleet5556 endpoint remains the default");
        Check(new ConnectionSettings(Port:5557).UseCurrentFleetEndpoint().Port == 5556,"Old5557 profile migrates for this Fleet only");
        Check(new ConnectionSettings("127.0.0.1",5557).UseCurrentFleetEndpoint().Port == 5557,"Other server settings preserved");
        Check(new ConnectionSettings(Port:6000).UseCurrentFleetEndpoint().Port == 6000,"Custom port preserved");
        void Reject(Action action, string message) { try { action(); } catch (Exception ex) when (ex is ArgumentException or FormatException or InvalidOperationException) { checks++; return; } throw new Exception("Cargo accepted: " + message); }
        var req = new CargoRequest("IN_TEST", "CARRIER_IN", "SIM_CARGO_004", "PORT_84", "SIM_TRAY_001", "SIM_CARGO_004");
        using (var message = CargoProtocol.Request(req))
        {
            Check(message.S == 2 && message.F == 41 && message.ReplyExpected, "S2F41 W");
            Check(message.SecsItem![1].Items.Single(x => x[0].GetString() == "SCANOK")[1].Format == SecsFormat.U2, "SCANOK typed U2");
            Check(message.SecsItem[1].Items.Single(x => x[0].GetString() == "SCANNEDID")[1].GetString() == req.ScannedId, "Actual scan independent from expected ID");
        }
        foreach (string id in new[] { "SIM_CARGO_002", "SCANNED-54321", "LOT:01.A" }) new TransferRequest("TR_2", id, "PORT_84", "PORT_86").Validate();
        Check(true, "Multiple carrier identities");
        foreach (string id in new[] { "", "bad id", "한글", new string('A', 65) }) Reject(() => (req with { CarrierId = id }).Validate(), "invalid ID");
        Reject(() => (req with { Port = "PORT_62" }).Validate(), "charge/transit port");
        Reject(() => (req with { Command = "DELETE_ALL" }).Validate(), "unknown RCMD");
        string folder = Path.Combine(Path.GetTempPath(), "CargoUnit_" + Guid.NewGuid().ToString("N"));
        var store = new CargoStore(Path.Combine(folder, "cargo.jsonl"));
        store.Reserve(req); store.Ack(req.RequestId, 4);
        Check(store.Snapshot()[0].Outcome == "ACKNOWLEDGED", "HCACK4 not completion");
        Check(store.BlocksTransfer(new("TR_BLOCK",req.CarrierId,"PORT_84","PORT_86")),"Pending inventory blocks transport");
        Reject(() => store.Reserve(req with { RequestId = "BYPASS" }), "new ID cannot bypass pending mutation");
        var restored = new CargoStore(Path.Combine(folder, "cargo.jsonl"));
        Check(restored.Snapshot()[0].Outcome == "UNCERTAIN", "Restart retains uncertainty");
        using var result = new SecsMessage(6, 11, true) { SecsItem = L(U4(1), U2(9002), L(L(U2(91), L(A("IN_TEST"), A("CARRIER_IN"), A("CONFIRMED"), A("SIM_CARGO_004"), A("PORT_84"), A("INVENTORY_VERIFIED"))))) };
        Check(restored.Result(CargoProtocol.Result(result)!), "Correlated final result");
        Check(!restored.BlocksTransfer(new("TR_ALLOW",req.CarrierId,"PORT_84","PORT_86")),"Confirmed inventory clears transport gate");
        Check(restored.Result(CargoProtocol.Result(result)!), "Repeat report idempotent");
        Reject(() => restored.Result(new("IN_TEST", "CARRIER_IN", "REJECTED", "SIM_CARGO_004", "PORT_84", "CONFLICT")), "conflicting result");
        Check(!restored.Result(new("IN_TEST", "CARRIER_IN", "CONFIRMED", "WRONG", "PORT_84", "X")), "unrelated cargo not applied");
        foreach (ushort status in new ushort[] { 0, 1, 2, 3 })
        {
            using var scan = new SecsMessage(6, 11, true) { SecsItem = L(U4(2), U2(406), L(L(U2(14), L(A("SCAN"), A("PORT_84"), U2(status))))) };
            Check(CargoProtocol.IdRead(scan)?.Status == status, "406 RPT14 L3 " + status);
        }
        using var badScan = new SecsMessage(6, 11, true) { SecsItem = L(U4(2), U2(406), L(L(U2(14), L(A("SCAN"), A("PORT_84"), U2(0), A("EXTRA"))))) };
        Reject(() => CargoProtocol.IdRead(badScan), "struck-out old fields not accepted");
        using var snapshot = new SecsMessage(6, 11, true) { SecsItem = L(U4(3), U2(9001), L(L(U2(90), L(A("QUERY"), L(L(A("CARGO_2"), A("PORT_84"), A("lay01-dist1-84"), A("SIM_TRAY_001"), U2(1), U2(0))))))) };
        Check(CargoProtocol.Snapshot(snapshot)!.Items[0].State == "포트 대기", "Typed inventory snapshot");
        Reject(() => store.Reserve(req), "request ID cannot replay");
        using (var review = CargoProtocol.Request(new("VERIFY_TEST", "CARRIER_VERIFY", TargetRequestId:"IN_TEST"))) Check(review.SecsItem![1][1][1].GetString()=="IN_TEST","Read-only review targets original request");
        var jobs = new JobStore(Path.Combine(folder,"jobs.jsonl"));
        jobs.Reserve(new("TR_FIRST","SIM_CARGO_001","PORT_89","PORT_97"));
        jobs.Reserve(new("TR_SECOND","SIM_CARGO_002","PORT_84","PORT_86"));
        Check(jobs.Snapshot().Count(x=>x.IsPending)==2,"Different carriers can have independent pending jobs");
        Check(!jobs.Report(new(207,"TR_SECOND","SIM_CARGO_001","PORT_84","PORT_86","PORT_86",0)),"Wrong carrier cannot complete another job");
        Check(jobs.Report(new(207,"TR_SECOND","SIM_CARGO_002","PORT_84","PORT_86","PORT_86",0)) && jobs.Snapshot().Single(x=>x.Request.CommandId=="TR_SECOND").Phase==JobPhase.Completed,"Second carrier completion correlated");
        Console.WriteLine($"PASS: {checks} PC cargo/scan/history checks; no Fleet connection.");
    }
}
