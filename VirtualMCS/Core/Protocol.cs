using System.Globalization;
using System.Text;
using Secs4Net;
using static Secs4Net.Item;

namespace KukaMcs.Core;

public sealed record TransferRequest(string CommandId, string CarrierId, string SourcePort, string DestPort, ushort Priority = 1)
{
    public static string NewCommandId() => Guid.NewGuid().ToString("N").ToUpperInvariant();
    public void Validate()
    {
        // Fleet's WCS accepts longer IDs, but its downstream RCS mission job_code
        // is varchar(50). Use a 32-character profile, including validation of
        // externally constructed requests, rather than relying on the WCS ACK.
        if (!System.Text.RegularExpressions.Regex.IsMatch(CommandId, "^[A-Za-z0-9_-]{1,32}$")) throw new ArgumentException("COMMANDID는 1~32자의 영문·숫자·_·-만 사용할 수 있습니다.");
        CargoRequest.Identifier(CarrierId, "CARRIERID");
        if (!WorkPorts.Contains(SourcePort) || !WorkPorts.Contains(DestPort) || SourcePort == DestPort) throw new ArgumentException("출발/도착은 작업존의 서로 다른 작업 포트를 선택해야 합니다. 허용 포트: " + string.Join(", ", WorkPorts.All));
        if (Priority is < 1 or > 99) throw new ArgumentException("우선순위는 1~99입니다.");
    }
}

public sealed record TransferReport(uint Ceid, string CommandId, string CarrierId, string SourcePort, string DestPort, string CarrierLocation, uint ResultCode);
public sealed record VehicleReport(uint Ceid, string VehicleId, string CommandId, string TransferPort, string CarrierId)
{
    public string EventText => Ceid switch { 601 => "차량 도착", 604 => "차량 할당", 605 => "차량 출발", 610 => "차량 해제",
        602 => "픽업 시작", 603 => "픽업 완료", 606 => "드롭 시작", 607 => "드롭 완료", _ => "차량 이벤트" };
}
public sealed record CarrierReport(uint Ceid, string VehicleId, string CarrierId, string CarrierLocation, string CommandId)
{
    public string EventText => Ceid == 301 ? "화물 적재" : "화물 하차";
}
public sealed record UnitAlarmReport(uint Ceid, string UnitId, uint AlarmId, string AlarmText);

public static class Protocol
{
    public static SecsMessage Transfer(TransferRequest r)
    {
        r.Validate();
        return new(2, 49, true) { Name = "TRANSFER", SecsItem = L(U4(0), A(""), A("TRANSFER"),
            L(L(A("COMMANDINFO"), L(L(A("COMMANDID"), A(r.CommandId)), L(A("PRIORITY"), U2(r.Priority)), L(A("REPLACE"), U2(0)))),
              L(A("TRANSFERINFO"), L(L(A("CARRIERID"), A(r.CarrierId)), L(A("SOURCEPORT"), A(r.SourcePort)), L(A("DESTPORT"), A(r.DestPort)))))) };
    }
    public static SecsMessage Establish() => new(1, 13, true) { Name = "EstablishCommunications", SecsItem = L() };
    public static SecsMessage Online() => new(1, 17, true) { Name = "RequestOnline" };
    public static SecsMessage Control(string command, string? commandId = null)
    {
        if (command is not ("CANCEL" or "ABORT" or "PAUSE" or "RESUME")) throw new ArgumentException("지원하지 않는 제어 명령입니다.");
        bool targeted = command is "CANCEL" or "ABORT";
        if (targeted && (commandId is null || !System.Text.RegularExpressions.Regex.IsMatch(commandId, "^[A-Za-z0-9_-]{1,32}$"))) throw new ArgumentException("선택 작업의 COMMANDID가 필요합니다.");
        if (!targeted && commandId is not null) throw new ArgumentException("PAUSE/RESUME은 운송 시스템 단위 명령입니다.");
        return new(2, 41, true) { Name = command, SecsItem = L(A(command), targeted ? L(L(A("COMMANDID"), A(commandId!))) : L()) };
    }
    public static string EventText(uint ceid) => ceid switch
    {
        201 => "강제중단 완료", 202 => "강제중단 실패", 203 => "강제중단 시작", 204 => "취소 완료", 205 => "취소 실패", 206 => "취소 시작",
        207 => "운송 완료", 208 => "작업 시작", 209 => "운송 일시중지", 210 => "운송 재개", 211 => "운송 진행",
        103 => "시스템 재개 완료", 105 => "시스템 일시중지 완료", 107 => "시스템 일시중지 시작", _ => "이벤트"
    };
    public static UnitAlarmReport? ParseUnitAlarm(SecsMessage message)
    {
        if (message.S != 6 || message.F != 11) return null;
        Item root = message.SecsItem ?? throw new FormatException("빈 S6F11");
        if (root.Format != SecsFormat.List || root.Count != 3 || root[2].Format != SecsFormat.List) throw new FormatException("S6F11 L[3] 필요");
        uint ceid = Number(root[1]);
        if (ceid is not (701 or 702)) return null;
        if (root[2].Count != 1 || root[2][0].Count != 2 || Number(root[2][0][0]) != 6) throw new FormatException("알람 RPTID6 필요");
        Item v = root[2][0][1];
        if (v.Format != SecsFormat.List || v.Count != 3 || v[0].Format != SecsFormat.ASCII || v[1].Format != SecsFormat.U4 || v[2].Format != SecsFormat.ASCII) throw new FormatException("잘못된 RPTID6");
        return new(ceid, v[0].GetString(), Number(v[1]), v[2].GetString());
    }
    public static uint Number(Item i) => i.Format switch
    {
        SecsFormat.Binary => i.FirstValue<byte>(), SecsFormat.U1 => i.FirstValue<byte>(),
        SecsFormat.U2 => i.FirstValue<ushort>(), SecsFormat.U4 => i.FirstValue<uint>(),
        SecsFormat.I1 => checked((uint)i.FirstValue<sbyte>()), SecsFormat.I2 => checked((uint)i.FirstValue<short>()), SecsFormat.I4 => checked((uint)i.FirstValue<int>()),
        _ => throw new FormatException("지원하지 않는 SECS 숫자 형식")
    };
    public static uint Ack(SecsMessage reply, byte s, byte f)
    {
        if (reply.S != s || reply.F != f || reply.SecsItem is null) throw new FormatException($"S{s}F{f} 응답이 아닙니다.");
        Item item = reply.SecsItem.Format == SecsFormat.List ? reply.SecsItem[0] : reply.SecsItem;
        return Number(item);
    }
    public static TransferReport? ParseReport(SecsMessage message)
    {
        if (message.S != 6 || message.F != 11) return null;
        Item root = message.SecsItem ?? throw new FormatException("빈 S6F11");
        if (root.Format != SecsFormat.List || root.Count != 3 || root[2].Format != SecsFormat.List) throw new FormatException("S6F11은 L[3]이어야 합니다.");
        uint ceid = Number(root[1]);
        foreach (Item report in root[2].Items)
        {
            if (report.Format != SecsFormat.List || report.Count != 2 || Number(report[0]) != 2) continue;
            Item values = report[1];
            if (values.Format != SecsFormat.List || values.Count != 3) throw new FormatException("RPTID 2의 values는 L[3]이어야 합니다.");
            Item cargos = values[1];
            if (cargos.Format != SecsFormat.List || cargos.Count != 1 || cargos[0].Count != 2 || cargos[0][0].Count != 3) throw new FormatException("RPTID 2의 Carrier 목록 형식이 다릅니다.");
            Item cargo = cargos[0][0];
            return new(ceid, values[0].GetString(), cargo[0].GetString(), cargo[1].GetString(), cargo[2].GetString(), cargos[0][1].GetString(), Number(values[2]));
        }
        return null; // Unknown report remains in the protocol log; never infer completion.
    }
    public static VehicleReport? ParseVehicleReport(SecsMessage message)
    {
        if (message.S != 6 || message.F != 11) return null;
        Item root = message.SecsItem ?? throw new FormatException("빈 S6F11");
        if (root.Format != SecsFormat.List || root.Count != 3 || root[2].Format != SecsFormat.List) throw new FormatException("S6F11은 L[3]이어야 합니다.");
        uint ceid = Number(root[1]);
        foreach (Item report in root[2].Items)
        {
            if (report.Format != SecsFormat.List || report.Count != 2 || Number(report[0]) != 5) continue;
            Item v = report[1];
            if (ceid is not (601 or 602 or 603 or 604 or 605 or 606 or 607 or 610) || v.Format != SecsFormat.List || v.Count != 4
                || v[2].Format != SecsFormat.List || v[2].Count != 1 || v[3].Format != SecsFormat.List || v[3].Count != 1)
                throw new FormatException("RPTID 5는 CEID 601~607/610의 차량·명령·포트·화물 목록이어야 합니다.");
            return new(ceid, v[0].GetString(), v[1].GetString(), v[2][0].GetString(), v[3][0].GetString());
        }
        return null;
    }
    public static CarrierReport? ParseCarrierReport(SecsMessage message)
    {
        if (message.S != 6 || message.F != 11) return null;
        Item root = message.SecsItem ?? throw new FormatException("빈 S6F11");
        if (root.Format != SecsFormat.List || root.Count != 3 || root[2].Format != SecsFormat.List) throw new FormatException("S6F11은 L[3]이어야 합니다.");
        uint ceid = Number(root[1]);
        foreach (Item report in root[2].Items)
        {
            if (report.Format != SecsFormat.List || report.Count != 2 || Number(report[0]) != 3) continue;
            Item v = report[1];
            if (ceid is not (301 or 302) || v.Format != SecsFormat.List || v.Count != 4
                || v.Items.Any(i => i.Format != SecsFormat.ASCII))
                throw new FormatException("RPTID 3은 CEID 301/302의 차량·화물·위치·명령 ASCII 4개여야 합니다.");
            return new(ceid, v[0].GetString(), v[1].GetString(), v[2].GetString(), v[3].GetString());
        }
        return null;
    }
    public static string Sml(SecsMessage message)
    {
        var text = new StringBuilder($"S{message.S}F{message.F}{(message.ReplyExpected ? " W" : "")} {message.Name}\n");
        if (message.SecsItem is not null) Append(text, message.SecsItem, 0);
        return text.Append('.').ToString();
    }
    private static void Append(StringBuilder b, Item i, int depth)
    {
        b.Append(' ', depth * 2);
        if (i.Format == SecsFormat.List)
        {
            b.AppendLine($"<L [{i.Count}]"); foreach (Item child in i.Items) Append(b, child, depth + 1); b.Append(' ', depth * 2).AppendLine(">"); return;
        }
        string content = i.Format switch
        {
            SecsFormat.ASCII or SecsFormat.JIS8 => "\"" + i.GetString().Replace("\"", "\\\"") + "\"",
            SecsFormat.Binary or SecsFormat.U1 => string.Join(" ", i.GetMemory<byte>().ToArray()),
            SecsFormat.U2 => string.Join(" ", i.GetMemory<ushort>().ToArray()), SecsFormat.U4 => string.Join(" ", i.GetMemory<uint>().ToArray()),
            SecsFormat.I1 => string.Join(" ", i.GetMemory<sbyte>().ToArray()), SecsFormat.I2 => string.Join(" ", i.GetMemory<short>().ToArray()),
            SecsFormat.I4 => string.Join(" ", i.GetMemory<int>().ToArray()), SecsFormat.U8 => string.Join(" ", i.GetMemory<ulong>().ToArray()),
            SecsFormat.I8 => string.Join(" ", i.GetMemory<long>().ToArray()), SecsFormat.Boolean => string.Join(" ", i.GetMemory<bool>().ToArray()),
            SecsFormat.F4 => string.Join(" ", i.GetMemory<float>().ToArray().Select(v => v.ToString(CultureInfo.InvariantCulture))),
            SecsFormat.F8 => string.Join(" ", i.GetMemory<double>().ToArray().Select(v => v.ToString(CultureInfo.InvariantCulture))), _ => "?"
        };
        string type = i.Format == SecsFormat.ASCII ? "A" : i.Format == SecsFormat.Binary ? "B" : i.Format.ToString();
        b.AppendLine($"<{type} [{i.Count}] {content}>");
    }
}
