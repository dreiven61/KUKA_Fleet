using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class TimelineLogsTests
{
    public static void Run(Action<bool, string> check)
    {
        string directory = Path.Combine(Path.GetTempPath(), "McsTimelineLogs_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        var time = new DateTimeOffset(2026, 11, 2, 17, 27, 24, TimeSpan.FromHours(9));
        var request = new TransferRequest("LOG_COMMAND_001", "SIM_CARGO_001", "PORT_89", "PORT_97");
        var step = new TransferJob(request, time, JobPhase.Sending, "S2F49 전송 시도. 자동 재전송 없음.");
        var rows = new List<(DateTimeOffset Time, string Direction, int Sb, SecsMessage Message)>();
        using var primary = Protocol.Transfer(request);
        using var response = new SecsMessage(2, 50, false) { SecsItem = L(B(4), L()) };
        using var report = new SecsMessage(6, 11, true) { SecsItem = L(U4(1), U2(601), L(L(U2(5), L(A("AGV002"), A(request.CommandId), L(A(request.SourcePort)), L(A(request.CarrierId)))))) };
        using var ack = new SecsMessage(6, 12, false) { SecsItem = B(0) };
        using var unrelated = Protocol.Transfer(request with { CommandId = "OTHER_JOB" });
        rows.Add((time, "TX", 1, primary)); rows.Add((time.AddMilliseconds(60), "RX", 1, response));
        rows.Add((time.AddSeconds(3), "RX", 2, report)); rows.Add((time.AddSeconds(3.01), "TX", 2, ack));
        rows.Add((time.AddSeconds(6), "RX", 3, report));
        rows.Add((time.AddSeconds(7), "TX", 1, unrelated)); rows.Add((time.AddSeconds(7.1), "RX", 1, response));
        rows.Add((time.AddSeconds(900), "RX", 3, ack)); // Expired reply cannot acquire an old command.
        string file = Path.Combine(directory, "messages_20261102.log");
        File.WriteAllText(file, string.Concat(rows.Select(r => $"[{r.Time:HH:mm:ss.fff}] {r.Direction}  {r.Message}\nSB=0x{r.Sb:X8}\n{Protocol.Sml(r.Message)}\n")));
        // A partial final block must never appear as a complete SML message.
        File.AppendAllText(file, $"[17:50:00.000] TX  incomplete\nSB=0x00000009\nS2F49 W\n<A [15] \"{request.CommandId}\">\n");
        var archived = MessageLogArchive.Read(directory, request.CommandId);
        check(archived.Count == 5, "Archive restores only complete correlated messages, excluding reused SB/expired ACK");
        check(archived.Single(e => e.Function == 50).CommandId == request.CommandId, "S2F50 without COMMANDID recovered by request SystemBytes");
        check(archived.Single(e => e.Function == 12).Ceid is null, "ACK not mislabeled as a report event");
        check(archived.Where(e => e.Function == 11).All(e => e.Ceid == 601), "CEID decoded from raw S6F11 root");
        var target = TimelineLogTarget.From(step)!;
        check(target.Find(archived)?.Function == 49, "Sending timeline resolves to actual S2F49");
        var accepted = step with { Phase = JobPhase.Acknowledged, UpdatedAt = time.AddMilliseconds(70), Detail = "S2F50 HCACK=4" };
        check(TimelineLogTarget.From(accepted)!.Find(archived)?.Function == 50, "ACK timeline resolves to actual S2F50");
        check(TimelineLogTarget.From(accepted with { Detail = "S2F50=0 (현재 Fleet의 선행 응답)" })!.Find(archived)?.Function == 50, "Legacy S2F50 history syntax supported");
        var eventStep = step with { Phase = JobPhase.Transferring, UpdatedAt = time.AddSeconds(6.1), LastCeid = 601, Detail = "도착지 도착 · CEID 601" };
        check(TimelineLogTarget.From(eventStep)!.Find(archived)?.SystemBytes == 3, "Repeated 601 events distinguished by event time");
        check(TimelineLogTarget.From(eventStep with { UpdatedAt = time.AddSeconds(60) })!.Find(archived) is null, "Old same-CEID message not substituted for missing event");
        check(TimelineLogTarget.From(eventStep with { Detail = "CEID 601, Result 0" })!.Find(archived)?.SystemBytes == 3, "Old history detail syntax supported");
        check(TimelineLogTarget.From(step with { Phase = JobPhase.Uncertain, Detail = "프로그램 재시작", LastCeid = 601 }) is null, "Non-wire timeline state not matched to stale CEID");
        check(TimelineLogTarget.From(eventStep with { UpdatedAt = null })!.Find(archived) is null, "Unknown timestamp not guessed");
        check(MessageLogArchive.Read(directory, "LOG_COMMAND_00").Count == 0, "Command matching is exact, not substring");
        check(MessageLogArchive.Read(Path.Combine(directory, "absent"), request.CommandId).Count == 0, "Missing log folder yields no fabricated entries");
        var control = step with { Detail = "CANCEL 전송 시도 · 자동 재전송 없음", LastCeid = 208 };
        check(TimelineLogTarget.From(control) is { Stream: 2, Function: 41 }, "Control request ignores retained CEID");
        check(TimelineLogTarget.From(control with { Detail = "CANCEL · HCACK=4" }) is { Function: 42 }, "Control ACK resolves correct function");
        check(archived.Single(e => e.Function == 49).Text.Contains(Protocol.Sml(primary).Replace("\r\n", "\n").TrimEnd(), StringComparison.Ordinal), "Loaded raw SML preserved apart from line endings");
        check(archived.All(e => e.SourceFile == file), "Archived raw data includes actual source filename");
        string systemFile = Path.Combine(directory, "secs_20261102.log");
        File.Copy(file, systemFile);
        check(MessageLogArchive.Read(directory, request.CommandId).Count == 5, "Duplicate disk logs do not duplicate SECS messages");
        File.Delete(file);
        File.WriteAllText(systemFile, string.Concat(rows.Select(r => $"[{r.Time:HH:mm:ss.fff}] {r.Direction}  {r.Message}\nSB=0x{r.Sb:X8}\n{Protocol.Sml(r.Message)}\n[{r.Time:HH:mm:ss.fff}] EVENT  local status text\nCOMMANDID={request.CommandId}\n")));
        var fallback = MessageLogArchive.Read(directory, request.CommandId);
        check(fallback.Count == 5 && fallback.All(e => e.SourceFile == systemFile), "secs log fallback separates system records from complete messages");
    }
}
