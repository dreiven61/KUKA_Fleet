using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class LoadedRecoveryTests
{
    public static void Run()
    {
        int checks = 0;
        void Check(bool valid, string message) { if (!valid) throw new Exception(message); checks++; }
        foreach (string source in new[] { "1", "2", "3", "AGV001", "001", "100001" })
        {
            using var request = Protocol.Transfer(new("RETURN_001", "SIM_CARGO_001", source, "PORT_89"));
            Check(request.SecsItem![3][1][1][1][1].GetString() == source, "AMR source on S2F49 wire");
        }
        foreach (string bad in new[] { "AMR_2", "AMR_AGV001", "AMR_", "AMR_bad name", "AMR_한글", "amr_2", "AMR_123456789012345678901234567890123" })
        {
            try { new TransferRequest("BAD", "SIM_CARGO_001", bad, "PORT_89").Validate(); throw new Exception("Bad AMR accepted"); }
            catch (ArgumentException) { checks++; }
        }
        try { new TransferRequest("BAD_DEST", "SIM_CARGO_001", "PORT_89", "AMR_2").Validate(); throw new Exception("AMR destination accepted"); }
        catch (ArgumentException) { checks++; }
        using var ready = new SecsMessage(6, 11, true) { SecsItem = L(U4(30), U2(9004), L(L(U2(93), L(A("OLD_001"), A("SIM_CARGO_001"), A("2"), A("lay01-dist1-105"), A("READY"), A("LOADED_AT_WAIT_NODE"))))) };
        var report = CargoProtocol.Recovery(ready)!;
        Check(report.VehicleId == "2" && report.State == "READY" && report.WaitNode == "lay01-dist1-105", "Recovery event parses separately from completed207");
        string file = Path.Combine(Path.GetTempPath(), "RecoveryMcs_" + Guid.NewGuid().ToString("N"), "jobs.jsonl");
        var jobs = new JobStore(file);var r = new TransferRequest("RETURN_001", "SIM_CARGO_001", "2", "PORT_89");jobs.Reserve(r);jobs.Acknowledge(r.CommandId, 4);
        Check(!jobs.ReportVehicle(new(604, "1", r.CommandId, r.SourcePort, r.CarrierId)), "Other vehicle cannot bind return");
        Check(jobs.ReportVehicle(new(604, "2", r.CommandId, r.SourcePort, r.CarrierId)), "Exact carrying owner binds");
        Check(jobs.Report(new(207, r.CommandId, r.CarrierId, r.SourcePort, r.DestPort, r.DestPort, 0)), "Return completed207 preserves original AMR source context");
        Check(jobs.Snapshot().Single().Phase == JobPhase.Completed, "Return completion recorded");
        foreach (string name in new[] { "AGV001", "001" })
        {
            string mappedId = "NAMED_" + name;
            var mapped = new TransferRequest(mappedId, "SIM_CARGO_001", name, "PORT_89");jobs.Reserve(mapped);jobs.Acknowledge(mappedId, 4);
            Check(!jobs.ReportVehicle(new(604, "1", mappedId, mapped.SourcePort, mapped.CarrierId)), "Numeric Fleet ID cannot replace mapped MCS owner");
            Check(jobs.ReportVehicle(new(604, name, mappedId, mapped.SourcePort, mapped.CarrierId)), "Exact mapped VehicleID binds loaded return, preserving zeros");
            Check(jobs.Report(new(207, mappedId, mapped.CarrierId, mapped.SourcePort, mapped.DestPort, mapped.DestPort, 0)), "Mapped return completes with same request context");
        }
        Console.WriteLine($"PASS: {checks} loaded recovery request, ownership and event checks; local only.");
    }
}
