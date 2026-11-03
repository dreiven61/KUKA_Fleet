using System.Diagnostics;
using Secs4Net;
using static Secs4Net.Item;

namespace KukaMcs.Core;

public sealed record StatusTable(ushort Svid, string Name, string[] Columns, IReadOnlyList<string[]> Rows, int EntryCount);
public sealed record StatusQueryResult(DateTimeOffset Time, string RequestIds, double ElapsedMs, bool Success,
    string Summary, string Raw, IReadOnlyList<StatusTable> Tables)
{
    public string TimeText => Time.ToString("HH:mm:ss.fff");
    public string ResultText => Success ? "정상" : "실패 · 상태 미확인";
    public string ElapsedText => $"{ElapsedMs:F1} ms";
}

public static class SelectedStatusProtocol
{
    public static readonly ushort[] All = [3, 17, 21, 23, 25, 46, 58];
    public static string VariableName(ushort id) => SkiSmlNames.VariableName(id);
    internal static ushort[]? RequestedIds(SecsMessage message) => message.SecsItem is { Format: SecsFormat.List } body
        && body.Count <= 64 && body.Items.All(i => i.Format == SecsFormat.U2 && i.Count == 1)
        ? body.Items.Select(i => i.FirstValue<ushort>()).ToArray() : null;
    public static SecsMessage Request(params ushort[] ids)
    {
        if (ids.Length > 64) throw new ArgumentException("SVID는 최대 64개입니다.");
        return new(1, 3, true) { Name = "SelectedStatus", SecsItem = L(ids.Select(i => U2(i)).ToArray()) };
    }
    private static Item List(Item? i, int? count = null)
    {
        if (i is null || i.Format != SecsFormat.List || count.HasValue && i.Count != count.Value)
            throw new FormatException("S1F4 목록 구조가 요청과 맞지 않습니다.");
        return i;
    }
    private static string Text(Item i) => i.Format == SecsFormat.ASCII ? i.GetString() : throw new FormatException("ASCII 필드가 아닙니다.");
    private static string Number(Item i, SecsFormat format = SecsFormat.U2)
    {
        if (i.Format != format || i.Count != 1) throw new FormatException($"{format} 단일 값이 아닙니다.");
        return Protocol.Number(i).ToString(System.Globalization.CultureInfo.InvariantCulture);
    }
    public static IReadOnlyList<StatusTable> Parse(SecsMessage reply, IReadOnlyList<ushort> requested)
    {
        if (reply.S != 1 || reply.F != 4 || reply.ReplyExpected)
            throw new FormatException($"S1F4 응답 필요 · 수신 S{reply.S}F{reply.F}. 빈 작업으로 판단하지 마세요.");
        ushort[] ids = requested.Count == 0 ? All.ToArray() : requested.ToArray();
        Item root = List(reply.SecsItem, ids.Length);
        var tables = new List<StatusTable>();
        for (int n = 0; n < ids.Length; n++)
        {
            ushort id = ids[n]; Item value = root[n]; var rows = new List<string[]>();
            string name; string[] columns;
            switch (id)
            {
                case 23:
                    name = "EnhancedTransfers · 현재 운송"; columns = ["CommandID", "Priority · 우선순위", "TransferState · 운송 상태", "CarrierID", "SourcePort · 출발", "DestPort · 도착"];
                    foreach (Item row in List(value).Items)
                    {
                        List(row, 3); List(row[0], 2); List(row[2]);
                        string command = Text(row[0][0]), priority = Number(row[0][1]), state = Number(row[1]);
                        if (row[2].Count == 0) rows.Add([command, priority, TransferState(state), "", "", ""]);
                        foreach (Item carrier in row[2].Items)
                        { List(carrier, 3); rows.Add([command, priority, TransferState(state), Text(carrier[0]), Text(carrier[1]), Text(carrier[2])]); }
                    }
                    break;
                case 25:
                    name = "EnhancedVehicles · AGV"; columns = ["VehicleID · AGV 식별자", "VehicleState · 차량 상태", "VehicleStatus · 가동 상태"];
                    foreach (Item row in List(value).Items)
                    {
                        List(row, 3); string state = Number(row[1]), health = Number(row[2]);
                        rows.Add([Text(row[0]), state switch { "1" => "1 · 제거", "2" => "2 · 미할당", "3" => "3 · 이동", "4" => "4 · 대기", "5" => "5 · 픽업", "6" => "6 · 드롭", _ => state }, health switch { "1" => "1 · 정상", "2" => "2 · Down", _ => health }]);
                    }
                    break;
                case 58:
                    name = "EnhancedPorts · 관리 포트"; columns = ["PortID", "PortTransferState · 이송 상태", "PortState · 서비스 상태", "CarrierID", "ProductEmpty · 화물 구분"];
                    foreach (Item row in List(value).Items)
                    {
                        List(row, 5); string state = Number(row[1]), service = Number(row[2]), full = Number(row[4]);
                        rows.Add([Text(row[0]), state switch { "1" => "1 · 차단", "2" => "2 · 반입 가능", "3" => "3 · 반출 가능", _ => state }, service switch { "1" => "1 · 서비스 외", "2" => "2 · 서비스 중", _ => service }, Text(row[3]), full switch { "0" => "0 · 없음", "1" => "1 · Full", "2" => "2 · Empty", _ => full }]);
                    }
                    break;
                case 21:
                    name = "EnhancedCarriers · 화물"; columns = ["CarrierID", "VehicleID", "CarrierLoc · 위치", "InstallTime · 설치 시각"];
                    foreach (Item row in List(value).Items) { List(row, 4); rows.Add([Text(row[0]), Text(row[1]), Text(row[2]), Text(row[3]) is { Length: > 0 } time ? time : "미상 (A0)"]); }
                    break;
                case 3:
                    name = "UnitAlarmStatList · 차량 알람"; columns = ["UnitID", "AlarmID", "AlarmText"];
                    foreach (Item row in List(value).Items) { List(row, 3); rows.Add([Text(row[0]), Number(row[1], SecsFormat.U4), Text(row[2])]); }
                    break;
                case 17: name = "제어 상태"; columns = ["값"]; rows.Add([Number(value)]); break;
                case 46: name = "TSC 상태"; columns = ["값"]; rows.Add([Number(value)]); break;
                default: name = "미지원 SVID"; columns = ["값"]; List(value, 0); break;
            }
            tables.Add(new(id, name, columns, rows, id is 17 or 46 ? 1 : value.Count));
        }
        return tables;
    }
    private static string TransferState(string s) => s switch { "1" => "1 · 대기열", "2" => "2 · 운송 중", "3" => "3 · 일시중지", "4" => "4 · 취소 중", "5" => "5 · 중단 중", "6" => "6 · 확인 대기", _ => s };
}

public sealed partial class McsClient
{
    private readonly object _statusLock = new();
    private readonly Queue<StatusQueryResult> _statusResults = new();
    private SecsGem? _statusEstablished;
    public bool AutoStatusOnConnect { get; set; }
    public event Action<StatusQueryResult>? StatusReceived;
    public IReadOnlyList<StatusQueryResult> RecentStatusResults { get { lock (_statusLock) return _statusResults.ToArray(); } }
    private async Task QueryStatusOnConnectAsync(SecsGem gem, CancellationToken token)
    {
        if (!AutoStatusOnConnect || ReferenceEquals(_statusEstablished, gem)) return;
        _statusEstablished = gem;
        try { await QueryStartupStatusAsync(token).ConfigureAwait(false); }
        catch (Exception ex) { Log("ERROR", "접속 후 상태 조회 실패 · 운송 재발행 금지: " + ex.Message); }
    }
    public Task QueryStartupStatusAsync(CancellationToken token = default) => Task.WhenAll(
        QuerySelectedStatusAsync([25], token), QuerySelectedStatusAsync([58], token), QuerySelectedStatusAsync([23], token));
    public async Task<StatusQueryResult> QuerySelectedStatusAsync(ushort[] ids, CancellationToken token = default)
    {
        ushort[] requested = ids.ToArray();
        var (gem, lifetime) = Session(true);
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(token, lifetime);
        using var primary = SelectedStatusProtocol.Request(requested);
        string requestText = requested.Length == 0 ? "전체" : string.Join(",", requested);
        string raw = "요청\n" + Protocol.Sml(primary); var timer = Stopwatch.StartNew();
        StatusQueryResult result;
        try
        {
            using var reply = await gem.SendAsync(primary, linked.Token).ConfigureAwait(false);
            raw += "\n\n응답\n" + Protocol.Sml(reply, requested);
            var tables = SelectedStatusProtocol.Parse(reply, requested);
            result = new(DateTimeOffset.Now, requestText, timer.Elapsed.TotalMilliseconds, true,
                string.Join(" / ", tables.Select(t => $"{t.Name} {t.EntryCount}건")), raw, tables);
        }
        catch (SecsException ex) when (ex.SecsMessage is { S: 1, F: 0 })
        {
            using var abortReply = ex.SecsMessage!;
            raw += "\n\n응답\n" + Protocol.Sml(abortReply);
            const string summary = "상태 조회 중단 (S1F0) · 서버가 조회 응답을 완료하지 못했습니다. 연결 단절 여부와는 별도로 확인해야 합니다. 빈 상태로 판단하거나 운송을 재발행하지 마세요.";
            result = new(DateTimeOffset.Now, requestText, timer.Elapsed.TotalMilliseconds, false, summary, raw, []);
            PublishStatus(gem, result);
            throw new SecsException(summary);
        }
        catch (Exception ex)
        {
            result = new(DateTimeOffset.Now, requestText, timer.Elapsed.TotalMilliseconds, false,
                ex.Message + " · 빈 상태로 판단하거나 운송을 재발행하지 마세요.", raw, []);
            PublishStatus(gem, result); throw;
        }
        if (!ReferenceEquals(gem, _gem) || !Communicating || lifetime.IsCancellationRequested)
            throw new OperationCanceledException("이전 연결의 조회 결과입니다.");
        PublishStatus(gem, result); return result;
    }
    private void PublishStatus(SecsGem session, StatusQueryResult result)
    {
        if (!ReferenceEquals(session, _gem)) return;
        lock (_statusLock) { _statusResults.Enqueue(result); while (_statusResults.Count > 500) _statusResults.Dequeue(); }
        Log(result.Success ? "STATUS" : "ERROR", $"S1F3 [{result.RequestIds}] → {result.ResultText} · {result.ElapsedText} · {result.Summary}\n{result.Raw}");
        StatusReceived?.Invoke(result);
    }
}
