using System.Buffers;
using System.Text.Json;
using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class SmlDictionaryTests
{
    private static byte[] Encode(Item item) { var buffer = new ArrayBufferWriter<byte>(); item.EncodeTo(buffer); return buffer.WrittenSpan.ToArray(); }
    public static void Run(Action<bool,string> check)
    {
        using var source = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory,"fixtures","secom_s1f4_20240425.json")));
        var captured=source.RootElement;
        string[] vehicles=captured.GetProperty("vehicleIds").EnumerateArray().Select(e=>e.GetString()!).ToArray();
        ushort[] ids=captured.GetProperty("requestedSvids").EnumerateArray().Select(e=>e.GetUInt16()).ToArray();
        using var legacy = new SecsMessage(1,4,false){SecsItem=L(U2(3),L(),L(),L(vehicles.Select(v=>L(A(v),U2(2),U2(1))).ToArray()),L(),L())};
        byte[] expected=Convert.FromHexString(captured.GetProperty("bodyHex").GetString()!);
        check(Encode(legacy.SecsItem!).SequenceEqual(expected),"S1F4 model matches all 178 captured legacy wire bytes, without field-name strings");
        var rows=SelectedStatusProtocol.Parse(legacy,ids);
        check(rows[3].Rows.Select(r=>r[0]).SequenceEqual(vehicles),"Legacy VehicleIDs preserved exactly, including UP/DW suffixes");
        check(rows[2].EntryCount==0 && rows[2].Columns[0]=="CommandID","Legacy empty transfers show named columns without invented command");
        string sml=Protocol.Sml(legacy,ids);
        foreach(string label in new[]{"ControlState","EnhancedCarriers","EnhancedTransfers","EnhancedVehicles","VehicleID","VehicleState","VehicleStatus","UnitAlarmStatList","EnhancedPorts"})
            check(sml.Contains("["+label+"]"),"Legacy-compatible field annotation "+label);
        check(Encode(legacy.SecsItem!).SequenceEqual(expected),"Log annotation never changes captured SECS binary data");
        check(!Protocol.Sml(legacy).Contains("[VehicleID]"),"S1F4 without request context must not guess a schema from list shape");
        using var selected=SelectedStatusProtocol.Request(25,23,25,999);
        using var reply=SelectedStatusTests.Reply(selected);
        string repeated=Protocol.Sml(reply,[25,23,25,999]);
        check(repeated.Split("[EnhancedVehicles]").Length==3 && repeated.Contains("[CommandInfo]") && repeated.Contains("[CommandID]"),"Requested order, repeated SVIDs and command hierarchy retain correct field names");
        check(repeated.Contains("[TransferState]") && repeated.Contains("[TransferInfoList]") && repeated.Contains("[SourcePort]") && repeated.Contains("[DestPort]"),"SV23 hierarchy matches BasicSpec pp31-34");
        using var command=Protocol.Transfer(new("CMD2024042514220409","SIM_CARGO_001","PORT_90","PORT_89"));
        byte[] before=Encode(command.SecsItem!);string request=Protocol.Sml(command);
        check(request.Contains("\"COMMANDID\" [CPNAME]") && request.Contains("\"CMD2024042514220409\" [CommandID]"),"CPNAME COMMANDID and its actual ID value have distinct annotations");
        check(Encode(command.SecsItem!).SequenceEqual(before),"Annotated command preserves actual wire fields and values");
        using var report=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2(604),L(L(U2(5),L(A("C0VGVA01_VC05"),A("CMD2024042514220409"),L(A("PORT_90")),L(A("SIM_CARGO_001"))))))};
        string eventText=Protocol.Sml(report);
        foreach(string label in new[]{"DATAID","CEID","RPTID","VehicleID","CommandID","TransferPortList","CarrierID"})check(eventText.Contains("["+label+"]"),"RPTID5 event annotation "+label);
        using var unknown=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2(999),L(L(U2(999),L(A("opaque")))))};
        check(!Protocol.Sml(unknown).Contains("[CommandID]"),"Unknown report IDs do not receive a fabricated standard schema");
    }
}
