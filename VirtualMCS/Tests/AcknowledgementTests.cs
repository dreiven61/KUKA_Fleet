using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class AcknowledgementTests
{
    public static void Run()
    {
        int checks = 0;
        void Check(bool value, string why) { if (!value) throw new Exception(why); checks++; }
        foreach (byte code in new byte[] { 0, 1, 2, 3, 4, 5, 6, 99 })
        {
            using var message = new SecsMessage(2, 50, false) { SecsItem = L(B(code), code == 3 ? L(L(A("PRIORITY"), B(2))) : L()) };
            var ack = CommandAcknowledgement.Parse(message, 50);
            Check(ack.Code == code && ack.Summary.Contains($"HCACK={code}"), "Ack code and meaning");
            Check(ack.Accepted == (code is 0 or 4), "Rejected ACK cannot be accepted");
            Check(!ack.Summary.Contains("완료 아님"), "No generic completion text for rejection");
        }
        using (var message = new SecsMessage(2, 50, false) { SecsItem = L(B(3), L(L(A("TRANSFERINFO"), L(L(A("SOURCEPORT"), B(2)))))) })
            Check(CommandAcknowledgement.Parse(message, 50).Summary.Contains("TRANSFERINFO.SOURCEPORT: CPACK=2"), "Nested parameter path and error decoded");
        foreach (Item body in new[] { L(B(2), L(L(A("STATE"), B(2)))), L(B(3), L()), L(U2(2), L()), L(), L(B(4), L(), L()) })
        {
            using var malformed = new SecsMessage(2, 50, false) { SecsItem = body };
            try { CommandAcknowledgement.Parse(malformed, 50); throw new Exception("Invalid ACK accepted"); } catch (FormatException) { checks++; }
        }
        string file = Path.Combine(Path.GetTempPath(), "ack-tests-" + Guid.NewGuid().ToString("N"), "jobs.jsonl");
        var store = new JobStore(file);
        var req = new TransferRequest("REJECTION_TEST", "SIM_CARGO_001", "PORT_97", "PORT_89");
        store.Reserve(req);
        var rejected = new CommandRejection(20, req.CommandId, "TRANSFER", 2, "TSC_NOT_AUTO", "PAUSING", "OLD_JOB");
        Check(store.Rejection(rejected) && store.Snapshot()[0].Phase == JobPhase.Rejected, "Early diagnostic correlated before S2F50");
        store.Acknowledge(req.CommandId, 2);
        Check(store.Snapshot()[0].Detail.Contains("PAUSING") && store.Snapshot()[0].Detail.Contains("OLD_JOB"), "Late ACK cannot erase exact reason");
        Check(new JobStore(file).Snapshot()[0].Detail.Contains("TSC_NOT_AUTO"), "Rejection survives PC restart");
        Check((rejected with { Reason = "LIFT_OPERATION_ACTIVE:UP_CONTAINER" }).Summary.Contains("리프트 이적재 동작 중 CANCEL·PAUSE 금지"), "Lift interlock has an actionable Korean reason");
        Check((rejected with { Reason = "HANDLING_STATE_UNCONFIRMED" }).Summary.Contains("이적재 동작 상태 확인 불가"), "Unknown handling state has a distinct reason");
        var active = req with { CommandId = "ACTIVE_JOB" }; store.Reserve(active); store.Acknowledge(active.CommandId, 4);
        Check(!store.Rejection(rejected with { CommandId = active.CommandId, Hcack = 5 }) && store.Snapshot().Single(j => j.Request.CommandId == active.CommandId).Phase == JobPhase.Acknowledged, "Rejected duplicate retry never invalidates accepted work");
        using (var wire = Message(req.CommandId))
        {
            var parsed = CommandRejection.Parse(wire)!;
            Check(parsed.CommandId == req.CommandId && parsed.DataId == 20 && parsed.Hcack == 2 && parsed.Summary.Contains("AUTO"), "Diagnostic wire parsed");
        }
        Console.WriteLine($"PASS: {checks} HCACK/CPACK and rejection diagnostic checks.");
    }
    public static SecsMessage Message(string id) => new(6, 11, true) { SecsItem = L(U4(20), U2(9003), L(L(U2(92), L(A(id), A("TRANSFER"), B(2), A("TSC_NOT_AUTO"), A("PAUSING"), A("OLD_JOB"))))) };
}
