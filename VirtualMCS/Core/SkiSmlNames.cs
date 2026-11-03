using Secs4Net;

namespace KukaMcs.Core;

// SKI BasicSpec pp31-35, MessageSpec pp7,13-24. These names
// annotate the log only; they are never added to a SECS Item or wire payload.
internal sealed record SmlField(string Name, Func<Item, int, SmlField?>? Child = null);

internal static class SkiSmlNames
{
    private static SmlField Leaf(string name) => new(name);
    private static SmlField Fields(string name, params SmlField[] fields) =>
        new(name, (_, index) => index < fields.Length ? fields[index] : null);
    private static SmlField Fields(string name, params string[] fields) => Fields(name, fields.Select(Leaf).ToArray());
    private static SmlField Rows(string name, string rowName, params SmlField[] fields) =>
        new(name, (_, index) => Fields(rowName + (index + 1), fields));
    private static SmlField Rows(string name, string rowName, params string[] fields) => Rows(name, rowName, fields.Select(Leaf).ToArray());
    private static SmlField Values(string name, string field) => new(name, (_, _) => Leaf(field));
    private static SmlField TransferInfo => Fields("TransferInfo", "CarrierID", "SourcePort", "DestPort");
    public static string VariableName(ushort id) => id switch
    {
        3 => "UnitAlarmStatList", 17 => "ControlState", 21 => "EnhancedCarriers", 23 => "EnhancedTransfers",
        25 => "EnhancedVehicles", 46 => "TSCState", 58 => "EnhancedPorts", _ => $"UnsupportedSVID={id}"
    };
    private static SmlField Status(ushort id) => id switch
    {
        3 => Rows(VariableName(id), "UnitAlarmStati", "UnitID", "AlarmID", "AlarmText"),
        21 => Rows(VariableName(id), "EnhancedCarrierInfoi", "CarrierID", "VehicleID", "CarrierLoc", "InstallTime"),
        23 => Rows(VariableName(id), "EnhancedTransferCommandi", Fields("CommandInfo", "CommandID", "Priority"),
            Leaf("TransferState"), Rows("TransferInfoList", "TransferInfoi", "CarrierID", "SourcePort", "DestPort")),
        25 => Rows(VariableName(id), "EnhancedVehicleInfoi", "VehicleID", "VehicleState", "VehicleStatus"),
        58 => Rows(VariableName(id), "EnhancedPortInfoi", "PortID", "PortTransferState", "PortState", "CarrierID", "ProductEmpty"),
        _ => Leaf(VariableName(id))
    };
    private static string ParameterName(Item pair)
    {
        if (pair.Format != SecsFormat.List || pair.Count == 0 || pair[0].Format != SecsFormat.ASCII) return "CPVAL";
        return pair[0].GetString() switch
        {
            "COMMANDID" => "CommandID", "PRIORITY" => "Priority", "REPLACE" => "Replace",
            "CARRIERID" => "CarrierID", "SOURCEPORT" => "SourcePort", "DESTPORT" => "DestPort",
            "REQUESTID" => "RequestID", "TARGETID" => "TargetID", "PORT" => "PortID", _ => "CPVAL"
        };
    }
    private static SmlField Parameters => new("CommandParameters", (list, index) =>
    {
        Item pair = list[index];
        return Fields("CommandParameter" + (index + 1), Leaf("CPNAME"), Leaf(ParameterName(pair)));
    });
    private static SmlField CommandGroups => new("CommandGroups", (list, index) =>
        list[index].Format == SecsFormat.List && list[index].Count == 2
            ? Fields("CommandGroup" + (index + 1), Leaf("CPNAME"), Parameters) : Leaf("ProjectExtension"));
    private static SmlField ReportValues(Item report)
    {
        if (report.Format != SecsFormat.List || report.Count == 0 || report[0].Count != 1 || report[0].Format != SecsFormat.U2)
            return Leaf("DVVAL");
        return report[0].FirstValue<ushort>() switch
        {
            1 => Fields("ReportData", Leaf("CommandID"), Fields("VehicleInfo", "VehicleID", "VehicleState")),
            2 => Fields("ReportData", Leaf("CommandID"), Rows("TransferCompleteInfo", "TransferCompleteInfoi", TransferInfo, Leaf("CarrierLoc")), Leaf("ResultCode")),
            3 => Fields("ReportData", "VehicleID", "CarrierID", "CarrierLoc", "CommandID"),
            4 => Fields("ReportData", "PortID"),
            5 => Fields("ReportData", Leaf("VehicleID"), Leaf("CommandID"), Values("TransferPortList", "TransferPort"), Values("CarrierIDList", "CarrierID")),
            6 => Fields("ReportData", "UnitID", "AlarmID", "AlarmText"),
            7 => Fields("ReportData", "VehicleID", "VehicleStatus"),
            14 => Fields("ReportData", "CarrierID", "CarrierLoc", "IDReadStatus"),
            // 90..93 are the explicitly documented project extensions.
            90 => Fields("CustomInventory", Leaf("RequestID"), Rows("InventoryList", "Inventoryi", "CarrierID", "PortID", "NodeCode", "ModelCode", "InMapStatus", "IsCarry")),
            91 => Fields("CustomCargoResult", "RequestID", "Command", "Outcome", "CarrierID", "PortID", "Detail"),
            92 => Fields("CustomCommandRejection", "CommandID", "RCMD", "HCACK", "Reason", "TSCState", "BlockingCommandID"),
            93 => Fields("CustomRecovery", "CommandID", "CarrierID", "VehicleID", "WaitPort", "Outcome", "Detail"),
            _ => Leaf("DVVAL")
        };
    }
    public static SmlField? Schema(SecsMessage message, IReadOnlyList<ushort>? statusIds)
    {
        if (message.S == 1 && message.F == 3) return Values("SVCOUNT", "SVID");
        if (message.S == 1 && message.F == 4)
        {
            if (statusIds is null) return Leaf("S1F4: SVID context unavailable");
            ushort[] ids = statusIds.Count == 0 ? SelectedStatusProtocol.All : statusIds.ToArray();
            return new("SelectedEquipmentStatusData", (_, index) => index < ids.Length ? Status(ids[index]) : null);
        }
        if (message.S == 6 && message.F == 11)
            return Fields("EventReport", Leaf("DATAID"), Leaf("CEID"), new("Reports", (list, index) =>
                Fields("Report" + (index + 1), Leaf("RPTID"), ReportValues(list[index]))));
        if (message.S == 2 && message.F == 49) return Fields("EnhancedRemoteCommand", Leaf("DATAID"), Leaf("OBJSPEC"), Leaf("RCMD"), CommandGroups);
        if (message.S == 2 && message.F == 41) return Fields("RemoteCommand", Leaf("RCMD"), Parameters);
        if (message.S == 2 && message.F is 42 or 50)
            return message.SecsItem?.Format == SecsFormat.List
                ? Fields("CommandAck", Leaf("HCACK"), Rows("ParameterAcks", "ParameterAck", "CPNAME", "CPACK")) : Leaf("HCACK");
        return (message.S, message.F) switch
        {
            (1, 2) => Fields("EquipmentIdentification", "MDLN", "SOFTREV"),
            (1, 14) => Fields("EstablishCommunicationsAck", Leaf("COMMACK"), Fields("EquipmentIdentification", "MDLN", "SOFTREV")),
            (1, 16) => Leaf("OFLACK"), (1, 18) => Leaf("ONLACK"),
            (5, 1) => Fields("AlarmReport", "ALCD", "ALID", "ALTX"), (5, 2) => Leaf("ACKC5"),
            (6, 12) => Leaf("ACKC6"), (2, 31) => Leaf("TIME"), (2, 32) => Leaf("TIACK"), _ => null
        };
    }
}
