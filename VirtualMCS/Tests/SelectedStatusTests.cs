using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class SelectedStatusTests
{
    public static int Mode;
    public static int Requests;
    public static SecsMessage Reply(SecsMessage primary)
    {
        Interlocked.Increment(ref Requests);
        if (Mode == 1) return new(1, 0, false);
        if (Mode == 2) return new(1, 4, false) { SecsItem = L(U2(1)) };
        ushort[] ids = primary.SecsItem!.Items.Select(i => i.FirstValue<ushort>()).ToArray();
        if (ids.Length == 0) ids = SelectedStatusProtocol.All.ToArray();
        return new(1, 4, false) { SecsItem = L(ids.Select(Value).ToArray()) };
    }
    private static Item Value(ushort id) => id switch
    {
        3 => L(L(A("2"), U4(1200103), A("WARNING"))), 17 => U2(5), 46 => U2(3),
        23 => L(L(L(A("PENDING_ACK_COMMAND"), U2(1)), U2(6), L(L(A("CARGO_1"), A("PORT_90"), A("PORT_97"))))),
        25 => L(L(A("1"), U2(2), U2(1)), L(A("2"), U2(3), U2(1)), L(A("3"), U2(2), U2(1))),
        58 => L(WorkPorts.All.Select(p => L(A(p), U2(2), U2(2), A(""), U2(0))).ToArray()),
        21 => L(L(A("CARGO_1"), A("2"), A("2"), A(""))), _ => L()
    };
    public static void ParseTests(Action<bool, string> check)
    {
        using var request = SelectedStatusProtocol.Request(58, 25, 23, 21, 25, 999);
        check(request.S == 1 && request.F == 3 && request.ReplyExpected && request.SecsItem!.Count == 6
            && request.SecsItem.Items.All(i => i.Format == SecsFormat.U2 && i.Count == 1), "S1F3 ordered scalar U2 SVIDs");
        using var reply = Reply(request);
        var rows = SelectedStatusProtocol.Parse(reply, [58, 25, 23, 21, 25, 999]);
        check(rows.Select(t => t.Svid).SequenceEqual(new ushort[] {58,25,23,21,25,999}), "Caller order, repeat and unsupported SVID slot");
        check(rows[0].EntryCount == 10 && rows[1].EntryCount == 3 && rows[5].EntryCount == 0, "Ports10 AMRs3 unknown empty");
        check(rows[2].Rows[0][0] == "PENDING_ACK_COMMAND" && rows[2].Rows[0][2].Contains("확인 대기"), "Completed unACKed command remains visible");
        check(rows[3].Rows[0][2] == "2" && rows[3].Rows[0][3].Contains("미상"), "Carried location and unknown install time");
        using var all = SelectedStatusProtocol.Request(); using var allReply = Reply(all);
        check(SelectedStatusProtocol.Parse(allReply, []).Count == 7, "L0 all supported SVIDs");
        foreach (SecsMessage invalid in new SecsMessage[] {
            new(1,0,false), new(1,4,false){SecsItem=L()}, new(1,4,false){SecsItem=L(L(L(A("1"),U4(2),U2(1))))},
            new(1,4,false){SecsItem=L(L(L(A("1"),U2(2),U2(1),A("extra"))))}, new(1,4,false){SecsItem=L(L(A("bad")))}})
        {
            using (invalid) { bool rejected = false; try { SelectedStatusProtocol.Parse(invalid, [25]); } catch (FormatException) { rejected = true; } check(rejected, "Malformed/abort must never be empty successful snapshot"); }
        }
    }
    public static async Task WireTests(McsClient client, Action<bool, string> check)
    {
        int before = Requests;
        await client.QueryStartupStatusAsync();
        check(Requests == before + 3, "Startup pattern sends exactly three read-only requests");
        var latest = client.RecentStatusResults.TakeLast(3).ToArray();
        check(latest.All(r => r.Success) && latest.Select(r => r.RequestIds).Order().SequenceEqual(new[] {"23","25","58"}), "Parallel HSMS replies correlate to SVID requests");
        check(latest.All(r => r.Raw.Contains("S1F3 W") && r.Raw.Contains("S1F4") && r.ElapsedMs >= 0), "Raw request/response and latency retained");
        for (int i=0;i<10;i++) await client.QueryStartupStatusAsync();
        check(Requests == before + 33, "Thirty additional reads without transport replay");
        Mode = 1;
        bool failed = false; try { await client.QuerySelectedStatusAsync([23]); } catch (Exception ex) when (ex is FormatException or SecsException) { failed = true; }
        check(failed && !client.RecentStatusResults.Last().Success && client.RecentStatusResults.Last().Tables.Count == 0, "S1F0 recorded as failure, no empty work snapshot");
        var abort = client.RecentStatusResults.Last();
        check(abort.Summary.Contains("상태 조회 중단 (S1F0)") && !abort.Summary.Contains("not online"), "S1F0 does not falsely diagnose equipment Offline");
        check(abort.Raw.Contains("응답") && abort.Raw.Contains("S1F0"), "Abort response SML retained alongside its request");
        Mode = 2;
        failed = false; try { await client.QuerySelectedStatusAsync([25]); } catch (FormatException) { failed = true; }
        check(failed && !client.RecentStatusResults.Last().Success, "Malformed S1F4 rejected on actual HSMS");
        Mode = 0;
        check((await client.QuerySelectedStatusAsync([23,25,58,21])).Success, "Query recovers after failed response");
    }
}
