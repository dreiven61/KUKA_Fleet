using System.Text.Json;
using System.Text.RegularExpressions;
using Secs4Net;
using static Secs4Net.Item;

namespace KukaMcs.Core;

public sealed record CargoRequest(string RequestId, string Command, string CarrierId = "", string Port = "", string Model = "SIM_TRAY_001", string ScannedId = "", bool ScanOk = true, bool Delete = false, string TargetRequestId = "")
{
    public static void Identifier(string value, string field)
    {
        if (!Regex.IsMatch(value, @"\A[A-Za-z0-9_.:-]{1,64}\z")) throw new ArgumentException(field + "는 1~64자의 영문·숫자·_ . : -만 사용할 수 있습니다.");
    }
    public void Validate()
    {
        if (!Regex.IsMatch(RequestId, @"\A[A-Za-z0-9_-]{1,32}\z")) throw new ArgumentException("잘못된 REQUESTID");
        if (Command is not ("CARRIER_QUERY" or "CARRIER_IN" or "CARRIER_OUT" or "CARRIER_SCAN_TEST" or "CARRIER_VERIFY")) throw new ArgumentException("지원하지 않는 화물 명령");
        if (Command == "CARRIER_QUERY") return;
        if (Command == "CARRIER_VERIFY") { if (!Regex.IsMatch(TargetRequestId, @"\A[A-Za-z0-9_-]{1,32}\z")) throw new ArgumentException("재확인 대상 REQUESTID 필요"); return; }
        Identifier(CarrierId, "CARRIERID");
        if (!WorkPorts.Contains(Port)) throw new ArgumentException("작업 포트를 선택하세요.");
        if (Command == "CARRIER_IN") Identifier(Model, "MODEL");
        if (Command is "CARRIER_IN" or "CARRIER_SCAN_TEST") { if (ScannedId.Length > 0) Identifier(ScannedId, "SCANNEDID"); }
    }
}
public sealed record CargoItem(string CarrierId, string Port, string Node, string Model, ushort InMap, ushort Carried)
{
    public string State => InMap == 0 ? "반출 상태" : Carried == 1 ? "AMR 적재 중" : "포트 대기";
}
public sealed record CargoResult(string RequestId, string Command, string Outcome, string CarrierId, string Port, string Detail);
public sealed record CargoSnapshot(string RequestId, IReadOnlyList<CargoItem> Items);
public sealed record IdReadReport(string CarrierId, string Location, ushort Status)
{
    public string ResultText => Status switch { 0 => "일치", 1 => "판독 실패", 2 => "중복", 3 => "불일치", _ => "알 수 없음" };
}
public sealed record CargoOperation(CargoRequest Request, string Outcome, string Detail, DateTimeOffset Updated);

public static class CargoProtocol
{
    public static SecsMessage Request(CargoRequest r)
    {
        r.Validate();
        var fields = new List<Item> { L(A("REQUESTID"), A(r.RequestId)) };
        if (r.Command == "CARRIER_VERIFY") fields.Add(L(A("TARGETID"), A(r.TargetRequestId)));
        else if (r.Command != "CARRIER_QUERY") fields.AddRange([L(A("CARRIERID"), A(r.CarrierId)), L(A("PORT"), A(r.Port))]);
        if (r.Command == "CARRIER_IN") fields.Add(L(A("MODEL"), A(r.Model)));
        if (r.Command is "CARRIER_IN" or "CARRIER_SCAN_TEST") fields.AddRange([L(A("SCANNEDID"), A(r.ScannedId)), L(A("SCANOK"), U2((ushort)(r.ScanOk ? 1 : 0)))]);
        if (r.Command == "CARRIER_OUT") fields.Add(L(A("DELETE"), U2((ushort)(r.Delete ? 1 : 0))));
        return new(2, 41, true) { Name = r.Command, SecsItem = L(A(r.Command), L(fields.ToArray())) };
    }
    private static Item? Values(SecsMessage m, ushort ceid, ushort rpt)
    {
        if (m.S != 6 || m.F != 11) return null;
        var root = m.SecsItem ?? throw new FormatException("빈 S6F11");
        if (root.Format != SecsFormat.List || root.Count != 3) throw new FormatException("이벤트 머리 형식 오류");
        if (Protocol.Number(root[1]) != ceid) return null;
        if (root[1].Format != SecsFormat.U2 || root[1].Count != 1) throw new FormatException("U2 CEID 필요");
        if (root[2].Format != SecsFormat.List || root[2].Count != 1 || root[2][0].Format != SecsFormat.List || root[2][0].Count != 2
            || root[2][0][0].Format != SecsFormat.U2 || root[2][0][0].Count != 1 || root[2][0][0].FirstValue<ushort>() != rpt) throw new FormatException("잘못된 화물 RPTID");
        return root[2][0][1];
    }
    private static void Count(Item v, int n) { if (v.Format != SecsFormat.List || v.Count != n) throw new FormatException("화물 보고 항목 수 오류"); }
    private static string Text(Item v) { if (v.Format != SecsFormat.ASCII || v.Count > 256) throw new FormatException("ASCII 화물 필드 오류"); return v.GetString(); }
    private static ushort Flag(Item v, ushort max = 1) { if (v.Format != SecsFormat.U2 || v.Count != 1 || v.FirstValue<ushort>() > max) throw new FormatException("U2 화물 상태 오류"); return v.FirstValue<ushort>(); }
    public static CargoResult? Result(SecsMessage m)
    {
        var v = Values(m, 9002, 91); if (v is null) return null; Count(v, 6);
        string outcome = Text(v[2]);
        if (outcome is not ("CONFIRMED" or "REJECTED" or "UNCERTAIN")) throw new FormatException("화물 결과 상태 오류");
        return new(Text(v[0]), Text(v[1]), outcome, Text(v[3]), Text(v[4]), Text(v[5]));
    }
    public static CargoSnapshot? Snapshot(SecsMessage m)
    {
        var v = Values(m, 9001, 90); if (v is null) return null; Count(v, 2);
        if (v[1].Format != SecsFormat.List || v[1].Count > 1000) throw new FormatException("화물 목록 오류");
        var rows = new List<CargoItem>();
        foreach (var row in v[1].Items) { Count(row, 6); rows.Add(new(Text(row[0]), Text(row[1]), Text(row[2]), Text(row[3]), Flag(row[4]), Flag(row[5]))); }
        if (rows.Select(x => x.CarrierId).Distinct().Count() != rows.Count) throw new FormatException("중복 화물 조회 행");
        return new(Text(v[0]), rows);
    }
    public static IdReadReport? IdRead(SecsMessage m)
    {
        var v = Values(m, 406, 14); if (v is null) return null; Count(v, 3);
        return new(Text(v[0]), Text(v[1]), Flag(v[2], 3));
    }
}

/** PC intent/outcome history. No automatic command replay on reconnect/restart. */
public sealed class CargoStore
{
    private readonly object _sync = new();
    private readonly string _path;
    private readonly Dictionary<string, CargoOperation> _ops = [];
    public event Action? Changed;
    public CargoStore(string path)
    {
        _path = path; Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        if (File.Exists(path)) foreach (string line in File.ReadLines(path)) if (line.Length > 0) { var row = JsonSerializer.Deserialize<CargoOperation>(line) ?? throw new IOException("화물 기록 손상"); _ops[row.Request.RequestId] = row; }
        foreach (var row in Snapshot().Where(x => x.Outcome is "SENDING" or "ACKNOWLEDGED")) Save(row with { Outcome = "UNCERTAIN", Detail = "재시작: Fleet 조회 필요. 자동 재전송 없음." });
    }
    public IReadOnlyList<CargoOperation> Snapshot() { lock (_sync) return _ops.Values.OrderByDescending(x => x.Updated).ToArray(); }
    public bool BlocksTransfer(TransferRequest r) => Snapshot().Any(x => (x.Request.Command is "CARRIER_IN" or "CARRIER_OUT") && (x.Outcome is "SENDING" or "ACKNOWLEDGED" or "UNCERTAIN")
        && (x.Request.CarrierId == r.CarrierId || x.Request.Port == r.SourcePort || x.Request.Port == r.DestPort));
    public void Reserve(CargoRequest request)
    {
        request.Validate(); lock (_sync)
        {
            if (_ops.ContainsKey(request.RequestId)) throw new InvalidOperationException("사용한 REQUESTID입니다.");
            if (request.Command is "CARRIER_IN" or "CARRIER_OUT" && _ops.Values.Any(x => x.Request.Command is "CARRIER_IN" or "CARRIER_OUT" && x.Outcome is "SENDING" or "ACKNOWLEDGED" or "UNCERTAIN"
                && (x.Request.CarrierId == request.CarrierId || x.Request.Port == request.Port))) throw new InvalidOperationException("동일 화물/포트의 미확인 입출고가 있습니다. Fleet 실제 상태부터 확인하세요.");
            Save(new(request, "SENDING", "전송 의도 저장 · 자동 재전송 없음", DateTimeOffset.Now));
        }
    }
    public void Ack(string id, uint ack) { lock (_sync) if (_ops.TryGetValue(id, out var old) && old.Outcome == "SENDING") Save(old with { Outcome = ack is 0 or 4 ? "ACKNOWLEDGED" : ack == 5 ? "UNCERTAIN" : "REJECTED", Detail = $"HCACK={ack} · 9001/9002 결과 별도 확인" }); }
    public bool Result(CargoResult r)
    {
        lock (_sync)
        {
            if (!_ops.TryGetValue(r.RequestId, out var old) || old.Request.Command != r.Command || old.Request.CarrierId != r.CarrierId || old.Request.Port != r.Port) return false;
            if (old.Outcome is "CONFIRMED" or "REJECTED") { if (old.Outcome != r.Outcome) throw new FormatException("동일 REQUESTID 결과 충돌"); return true; }
            Save(old with { Outcome = r.Outcome, Detail = r.Detail }); return true;
        }
    }
    public bool AcceptSnapshot(string id) { lock (_sync) { if (!_ops.TryGetValue(id, out var old) || old.Request.Command != "CARRIER_QUERY") return false; Save(old with { Outcome = "CONFIRMED", Detail = "Fleet 화물 목록 수신" }); return true; } }
    public void Uncertain(string id) { lock (_sync) if (_ops.TryGetValue(id, out var old) && old.Outcome is "SENDING" or "ACKNOWLEDGED") Save(old with { Outcome = "UNCERTAIN", Detail = "응답 미확인: 실제 상태 조회 후 확인. 자동 재전송 없음." }); }
    public void Disconnected() { foreach (var op in Snapshot()) Uncertain(op.Request.RequestId); }
    private void Save(CargoOperation row)
    {
        row = row with { Updated = DateTimeOffset.Now };
        using var file = new FileStream(_path, FileMode.Append, FileAccess.Write, FileShare.Read);
        file.Write(System.Text.Encoding.UTF8.GetBytes(JsonSerializer.Serialize(row) + "\n")); file.Flush(true);
        _ops[row.Request.RequestId] = row; Changed?.Invoke();
    }
}
