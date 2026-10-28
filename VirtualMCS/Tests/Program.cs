using System.Net;
using System.Net.Sockets;
using KukaMcs.Core;
using Microsoft.Extensions.Options;
using Secs4Net;
using static Secs4Net.Item;

if(args.Length==2 && args[0]=="--native-interop"){await NativeInteropTests.Run(int.Parse(args[1]));return;}
CargoTests.Run();

int checks = 0;
void Check(bool valid, string name) { if (!valid) throw new Exception("FAIL: " + name); checks++; }
void Reject(Action action, string name) { try { action(); } catch (InvalidOperationException) { checks++; return; } throw new Exception("FAIL: " + name); }
async Task Wait(Func<bool> condition, string name)
{
    for (int i = 0; i < 100 && !condition(); i++) await Task.Delay(50);
    Check(condition(), name);
}
TransferRequest Req(string id, string from = "PORT_97", string to = "PORT_89") => new(id, "SIM_CARGO_001", from, to, 1);
SecsMessage Event(uint ceid, TransferRequest r, uint result = 0, string? location = null) => new(6, 11, true)
{
    SecsItem = L(U4(0), U2((ushort)ceid), L(L(U2(2), L(A(r.CommandId), L(L(L(A(r.CarrierId), A(r.SourcePort), A(r.DestPort)), A(location ?? r.DestPort))), U2((ushort)result)))))
};
SecsMessage Vehicle(uint ceid, TransferRequest r, string robot = "1", string? port = null) => new(6, 11, true)
{
    SecsItem = L(U4(0), U2((ushort)ceid), L(L(U2(5), L(A(robot), A(r.CommandId),
        L(A(port ?? (ceid is 601 or 602 or 603 or 604 or 605 ? r.SourcePort : r.DestPort))), L(A(r.CarrierId))))))
};
SecsMessage Carrier(uint ceid, TransferRequest r, string robot = "1", string? location = null) => new(6, 11, true)
{
    SecsItem = L(U4(0),U2((ushort)ceid),L(L(U2(3),L(A(robot),A(r.CarrierId),
        A(location ?? (ceid == 301 ? robot : r.DestPort)),A(r.CommandId)))))
};
string root = Path.Combine(Path.GetTempPath(), "KukaMcsTests_" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(root);
var ids = Enumerable.Range(0, 3000).Select(_ => TransferRequest.NewCommandId()).ToArray();
Check(ids.Distinct().Count() == ids.Length && ids.All(id => id.Length == 32), "Unique 32-character automatic IDs fit downstream Fleet RCS varchar(50)");
foreach (int length in new[] {33, 50, 54, 64})
{
    try { using var invalid = Protocol.Transfer(Req(new string('A', length))); throw new Exception("Oversized ID accepted"); }
    catch (ArgumentException) { checks++; }
}
using (var boundary = Protocol.Transfer(Req(new string('B', 32)))) Check(boundary.SecsItem![3][0][1][0][1].Count == 32, "32-character profile boundary is encoded without truncation");
Check(WorkPorts.All.Count == 10 && WorkPorts.All.Distinct().Count() == 10, "All ten lay01/dist1 work ports");
foreach (string from in WorkPorts.All)
    foreach (string to in WorkPorts.All)
    {
        if (from == to)
        {
            try { Req("SAME_PORT", from, to).Validate(); throw new Exception("Same-port transfer accepted"); }
            catch (ArgumentException) { checks++; }
            continue;
        }
        using var allPortsMessage = Protocol.Transfer(Req("ALL_PORTS", from, to));
        var info = allPortsMessage.SecsItem![3][1][1];
        Check(info[1][1].GetString() == from && info[2][1].GetString() == to, "Work-port wire route " + from + " -> " + to);
    }
foreach (string invalidPort in new[] { "PORT_87", "PORT_88", "PORT_99", "PORT_100", "PORT_62", "PORT_999", "PORT_084", "" })
    foreach (bool source in new[] { true, false })
    {
        try { Req("INVALID_PORT", source ? invalidPort : "PORT_89", source ? "PORT_97" : invalidPort).Validate(); throw new Exception("Non-work port accepted"); }
        catch (ArgumentException) { checks++; }
    }
using (var msg = Protocol.Transfer(Req(ids[0])))
{
    Item b = msg.SecsItem!;
    Check(msg.S == 2 && msg.F == 49 && msg.ReplyExpected, "S2F49 W");
    Check(b.Count == 4 && b[0].Format == SecsFormat.U4 && b[0].FirstValue<uint>() == 0, "DATAID U4=0 and L4 envelope");
    Check(b[1].GetString() == "" && b[2].GetString() == "TRANSFER", "OBJSPEC and RCMD");
    Check(b[3].Count == 2 && b[3][0][0].GetString() == "COMMANDINFO" && b[3][1][0].GetString() == "TRANSFERINFO", "Named groups");
    Check(b[3][0][1].Count == 3 && b[3][1][1].Count == 3, "Each group has three name/value pairs");
    Check(b[3][0][1][0][1].GetString() == ids[0] && b[3][0][1][1][1].Format == SecsFormat.U2, "COMMANDID and U2 PRIORITY");
    Check(b[3][0][1][2][1].FirstValue<ushort>() == 0, "REPLACE=0");
    Check(b[3][1][1][1][1].GetString() == "PORT_97" && b[3][1][1][2][1].GetString() == "PORT_89", "Correct From/To values");
}
using (var ack = new SecsMessage(2, 50, false) { SecsItem = B(0) }) Check(Protocol.Ack(ack, 2, 50) == 0, "Current Fleet single-B ACK");
using (var ack = new SecsMessage(2, 50, false) { SecsItem = L(B(4), L()) }) Check(Protocol.Ack(ack, 2, 50) == 4, "Structured HCACK");
string stateFile = Path.Combine(root, "jobs.jsonl");
var store = new JobStore(stateFile); var request = Req("STATE_TEST");
store.Reserve(request); Reject(() => store.Reserve(Req("DOUBLE_CLICK")), "Prevent second pending cargo request");
store.Acknowledge(request.CommandId, 0); Check(store.Snapshot()[0].IsPending, "S2F50 zero does not complete job");
Check(new JobStore(stateFile).Snapshot()[0].Phase == JobPhase.Uncertain, "Restart blocks unresolved command");
using (var ev = Event(211, request with { CarrierId = "WRONG" })) Check(!store.Report(Protocol.ParseReport(ev)!), "Wrong cargo cannot update job");
using (var ev = Event(208, request)) store.Report(Protocol.ParseReport(ev)!);
using (var ev = Event(211, request)) store.Report(Protocol.ParseReport(ev)!);
store.Acknowledge(request.CommandId, 0); Check(store.Snapshot()[0].Phase == JobPhase.Transferring, "Late S2F50 cannot regress event state");
using (var ev = Event(207, request, location: request.SourcePort)) store.Report(Protocol.ParseReport(ev)!);
Check(store.Snapshot()[0].IsPending, "Wrong final cargo location blocks normal completion");
using (var ev = Event(207, request)) store.Report(Protocol.ParseReport(ev)!);
Check(store.Snapshot()[0].Phase == JobPhase.Completed, "Matched 207 success completes job");
Check(store.History(request.CommandId).Any(j => j.LastCeid == 208) && store.History(request.CommandId).Any(j => j.LastCeid == 211), "Timeline retains start and progress after completion");
Check(store.History(request.CommandId).All(j => j.UpdatedAt is not null), "New timeline entries have actual record timestamps");
Check(new JobStore(stateFile).History(request.CommandId).Any(j => j.Phase == JobPhase.Completed), "Timeline survives restart");
string legacyFile = Path.Combine(root, "legacy.jsonl");
File.WriteAllText(legacyFile, System.Text.Json.JsonSerializer.Serialize(new TransferJob(Req("LEGACY"), DateTimeOffset.Now.AddHours(-1), JobPhase.Completed, "Old completion", 207, 0)) + "\n");
Check(new JobStore(legacyFile).History("LEGACY")[0].UpdatedAt is null, "Legacy history never invents missing event timestamps");
store.Disconnected(); Check(store.Snapshot()[0].Phase == JobPhase.Completed, "Disconnect cannot erase completed result");
Reject(() => store.Reserve(request), "Used command ID never recycled");
var failed = Req("FAILED_TEST"); store.Reserve(failed);
using (var ev = Event(207, failed, 6, failed.SourcePort)) store.Report(Protocol.ParseReport(ev)!);
Check(store.Snapshot().Single(j => j.Request.CommandId == failed.CommandId).Phase == JobPhase.AbnormalEnded, "Nonzero result is abnormal termination");
store.Reserve(Req("RELEASE_TEST")); store.Release("RELEASE_TEST");
Check(store.Snapshot().Single(j => j.Request.CommandId == "RELEASE_TEST").Phase == JobPhase.ManuallyReleased, "Manual release is distinct from completion");
// A loopback-only HSMS equipment endpoint, never the real Fleet address.
var probe = new TcpListener(IPAddress.Loopback, 0); probe.Start(); int port = ((IPEndPoint)probe.LocalEndpoint).Port; probe.Stop();
var options = Options.Create(new SecsGemOptions { IsActive = false, IpAddress = "127.0.0.1", Port = port, DeviceId = 3000, T3 = 1000, T5 = 100, T6 = 1000, T7 = 2000 });
var silent = new SilentLogger();
await using var equipment = new HsmsConnection(options, silent);
using var eqGem = new SecsGem(options, equipment, silent);
using var cts = new CancellationTokenSource();
var received = new TaskCompletionSource<TransferRequest>(TaskCreationOptions.RunContinuationsAsynchronously);
bool dropReply = false;
var serverLoop = Task.Run(async () =>
{
    try
    {
        await foreach (var incoming in eqGem.GetPrimaryMessageAsync(cts.Token))
        {
            using var primary = incoming.PrimaryMessage;
            using var reply = primary.S == 1 && primary.F == 13 ? new SecsMessage(1, 14, false) { SecsItem = L(B(0), L(A("MOCK_FLEET"), A("1"))) }
                : primary.S == 1 && primary.F == 17 ? new SecsMessage(1, 18, false) { SecsItem = B(0) }
                : primary.S == 2 && primary.F == 41 ? new SecsMessage(2, 42, false) { SecsItem = L(B(4), L()) }
                : new SecsMessage(2, 50, false) { SecsItem = B(0) };
            if (primary.S == 2 && primary.F == 49)
            {
                Item b = primary.SecsItem!;
                received.TrySetResult(new(b[3][0][1][0][1].GetString(), b[3][1][1][0][1].GetString(), b[3][1][1][1][1].GetString(), b[3][1][1][2][1].GetString(), b[3][0][1][1][1].FirstValue<ushort>()));
                if (dropReply) continue;
            }
            await incoming.TryReplyAsync(reply, cts.Token);
        }
    }
    catch (OperationCanceledException) when (cts.IsCancellationRequested) { }
});
equipment.Start(cts.Token);
var wireStore = new JobStore(Path.Combine(root, "wire.jsonl"));
await using var client = new McsClient(wireStore);
var logs = new System.Collections.Concurrent.ConcurrentQueue<LogEntry>(); client.Logged += logs.Enqueue;
await client.ConnectAsync(new("127.0.0.1", port, 3000, true, 1));
await Wait(() => client.Communicating, "HSMS select plus automatic S1F13/S1F14 handshake");
await client.OnlineAsync(); Check(logs.Any(l => l.Text.Contains("ONLACK=0")), "Manual S1F17 round trip");
var wireRequest = Req(TransferRequest.NewCommandId()); await client.SendTransferAsync(wireRequest);
Check(await received.Task.WaitAsync(TimeSpan.FromSeconds(3)) == wireRequest, "Real HSMS wire delivers exact transfer parameters");
Check(wireStore.Snapshot()[0].Phase == JobPhase.Acknowledged, "Legacy S2F50 is only acknowledged");
Check(logs.Any(l => l.IsWorkMessage && l.MessageType == "S2F49" && l.CommandId == wireRequest.CommandId), "Transfer row has structured command correlation");
await Wait(() => logs.Any(l => l.MessageType == "S2F50" && l.CommandId == wireRequest.CommandId), "Secondary response retains request COMMANDID");
foreach (var (ceid, portName) in new (uint, string?)[] { (208,null), (604,null), (601,wireRequest.SourcePort),
    (211,null), (602,null), (301,null), (603,null), (605,null), (601,wireRequest.DestPort),
    (606,null), (302,null), (607,null), (610,null), (207,null) })
{
    using var report = ceid >= 600 ? Vehicle(ceid, wireRequest, port:portName) : ceid is 301 or 302 ? Carrier(ceid,wireRequest) : Event(ceid, wireRequest);
    using var reply = await eqGem.SendAsync(report, cts.Token);
    Check(Protocol.Ack(reply, 6, 12) == 0, $"Automatic correlated S6F12 for CEID {ceid}");
    if (ceid >= 600 || ceid is 301 or 302) Check(wireStore.Snapshot()[0].IsPending && wireStore.History(wireRequest.CommandId).Any(j => j.LastCeid == ceid), $"CEID {ceid} retained without claiming transport completion");
}
await Wait(() => wireStore.Snapshot()[0].Phase == JobPhase.Completed, "Received completion report updates job");
Check(logs.Any(l => l.Ceid == 208 && l.Summary!.Contains("작업 시작")) && logs.Any(l => l.Ceid == 207 && l.Summary!.Contains("정상 완료")), "Start/completion have readable message labels");
await Wait(() => logs.Any(l => l.MessageType == "S6F12" && l.CommandId == wireRequest.CommandId), "Event ACK row is linked to the same command");
foreach (uint ceid in new uint[] {601,602,603,604,605,606,607,610})
    Check(logs.Any(l => l.Ceid == ceid && l.CommandId == wireRequest.CommandId && l.Summary!.Contains("보고")), $"Vehicle {ceid} message has readable summary and correlation");
var vehicleRequest = Req("VEHICLE_VALIDATION");
wireStore.Reserve(vehicleRequest);
using (var wrong = Vehicle(603, vehicleRequest, robot: "0")) Check(!wireStore.ReportVehicle(Protocol.ParseVehicleReport(wrong)!), "Wrong vehicle blocked");
using (var wrong = Vehicle(606, vehicleRequest, port: vehicleRequest.SourcePort)) Check(!wireStore.ReportVehicle(Protocol.ParseVehicleReport(wrong)!), "Wrong event port blocked");
using (var wrong = Vehicle(603, vehicleRequest with { CarrierId="OTHER" })) Check(!wireStore.ReportVehicle(Protocol.ParseVehicleReport(wrong)!), "Wrong event cargo blocked");
foreach(uint ceid in new uint[]{601,604,605,610})
{
    string wrongPort=ceid==601?"PORT_999":ceid==610?vehicleRequest.SourcePort:vehicleRequest.DestPort;
    using var wrong=Vehicle(ceid,vehicleRequest,port:wrongPort);
    Check(!wireStore.ReportVehicle(Protocol.ParseVehicleReport(wrong)!),$"Vehicle {ceid} enforces port role");
}
using (var malformed = new SecsMessage(6,11,true) { SecsItem=L(U4(0),U2(603),L(L(U2(5),L(A("1"),A(vehicleRequest.CommandId),L(),L(A(vehicleRequest.CarrierId)))))) })
using (var reply = await eqGem.SendAsync(malformed,cts.Token)) Check(Protocol.Ack(reply,6,12)==1,"Malformed RPTID5 gets negative ACK and cannot advance job");
Check(wireStore.Snapshot().Single(j=>j.Request.CommandId==vehicleRequest.CommandId).Phase==JobPhase.Sending,"Rejected vehicle reports never change local phase");
wireStore.Release(vehicleRequest.CommandId);
var arrivals=wireStore.History(wireRequest.CommandId).Where(j=>j.LastCeid==601).ToArray();
Check(arrivals.Length==2 && arrivals[0].VehiclePort==wireRequest.SourcePort && arrivals[1].VehiclePort==wireRequest.DestPort,"Two CEID601 arrivals retain separate ports and timeline records");
Check(wireStore.History(wireRequest.CommandId).First(j=>j.LastCeid==604).Phase==JobPhase.Initiated,"Vehicle assignment does not pretend pickup/transport has begun");
Check(wireStore.History(wireRequest.CommandId).First(j=>j.LastCeid==610).IsPending,"Vehicle release still waits for final207");
foreach (uint ceid in new uint[] {301,302})
    Check(logs.Any(l=>l.Ceid==ceid && l.CommandId==wireRequest.CommandId && l.Summary!.Contains(ceid==301?"화물 적재":"화물 하차")), $"Carrier {ceid} has readable summary and command correlation");
var carrierRequest=Req("CARRIER_VALIDATION");wireStore.Reserve(carrierRequest);
using(var wrong=Carrier(301,carrierRequest,robot:"0"))Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(wrong)!),"Wrong carrier vehicle blocked");
using(var wrong=Carrier(301,carrierRequest,location:carrierRequest.SourcePort))Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(wrong)!),"301 must locate cargo on vehicle");
using(var wrong=Carrier(302,carrierRequest,location:carrierRequest.SourcePort))Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(wrong)!),"302 wrong final location blocked");
using(var wrong=Carrier(301,carrierRequest with {CarrierId="OTHER"}))Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(wrong)!),"Wrong carrier ID blocked");
using(var wrong=Carrier(301,carrierRequest with {CommandId="UNKNOWN"}))Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(wrong)!),"Unknown carrier command blocked");
using(var malformed=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2(301),L(L(U2(3),L(A("1"),A(carrierRequest.CarrierId),A("1")))))})
using(var reply=await eqGem.SendAsync(malformed,cts.Token))Check(Protocol.Ack(reply,6,12)==1,"Malformed RPTID3 gets negative ACK");
using(var malformed=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2(302),L(L(U2(3),L(A("1"),A(carrierRequest.CarrierId),U2(89),A(carrierRequest.CommandId)))))})
using(var reply=await eqGem.SendAsync(malformed,cts.Token))Check(Protocol.Ack(reply,6,12)==1,"Non-ASCII RPTID3 location gets negative ACK");
Check(wireStore.Snapshot().Single(j=>j.Request.CommandId==carrierRequest.CommandId).Phase==JobPhase.Sending,"Invalid carrier reports cannot advance job");
wireStore.Release(carrierRequest.CommandId);
using(var late=Carrier(301,wireRequest))wireStore.ReportCarrier(Protocol.ParseCarrierReport(late)!);
Check(wireStore.Snapshot().Single(j=>j.Request.CommandId==wireRequest.CommandId).Phase==JobPhase.Completed,"Late carrier report cannot regress completed job");
Check(new JobStore(Path.Combine(root,"wire.jsonl")).History(wireRequest.CommandId).Count(j=>j.LastCeid is 301 or 302)==2,"Carrier receipt timeline persists across restart");
// Each command can be assigned a different real robot. Persist identity and reject mixing.
foreach (string robot in new[] { "2", "3" })
{
    var r = Req("AUTO_" + robot); wireStore.Reserve(r);
    using(var first=Vehicle(604,r,robot)) Check(wireStore.ReportVehicle(Protocol.ParseVehicleReport(first)!), "Bind assigned robot " + robot);
    foreach(var (ceid,stagePort) in new (uint,string)[]{(601,r.SourcePort),(602,r.SourcePort),(603,r.SourcePort),(605,r.SourcePort),(601,r.DestPort),(606,r.DestPort),(607,r.DestPort),(610,r.DestPort)})
    {
        using var m=Vehicle(ceid,r,robot,stagePort); Check(wireStore.ReportVehicle(Protocol.ParseVehicleReport(m)!), "Dynamic vehicle stage " + ceid);
    }
    using(var mixed=Vehicle(603,r,"1")) Check(!wireStore.ReportVehicle(Protocol.ParseVehicleReport(mixed)!), "Do not mix vehicles in one command");
    using(var loaded=Carrier(301,r,robot)) Check(wireStore.ReportCarrier(Protocol.ParseCarrierReport(loaded)!), "301 location is actual assigned robot");
    using(var mixed=Carrier(302,r,"1")) Check(!wireStore.ReportCarrier(Protocol.ParseCarrierReport(mixed)!), "Carrier report must match assigned vehicle");
    using(var removed=Carrier(302,r,robot)) Check(wireStore.ReportCarrier(Protocol.ParseCarrierReport(removed)!), "Dynamic carrier removal");
    Check(wireStore.Snapshot().Single(j=>j.Request.CommandId==r.CommandId).VehicleId==robot,"Actual assigned robot retained");
    wireStore.Report(new(207,r.CommandId,r.CarrierId,r.SourcePort,r.DestPort,r.DestPort,0));
    Check(new JobStore(Path.Combine(root,"wire.jsonl")).Snapshot().Single(j=>j.Request.CommandId==r.CommandId).VehicleId==robot,"Assigned vehicle persists after reload");
}
using (var alarm = new SecsMessage(5, 1, true) { SecsItem = L(B(0x86), U4(900001), A("MOCK_ALARM")) })
using (var reply = await eqGem.SendAsync(alarm, cts.Token)) Check(Protocol.Ack(reply, 5, 2) == 0, "Automatic S5F2 alarm ACK");
Check(logs.Any(l => l.Direction == "ALARM" && l.Text.Contains("MOCK_ALARM")), "Alarm is logged separately");
await ControlTests.Run(client, eqGem, wireStore, cts.Token, Check);
dropReply = true;
var uncertain = Req(TransferRequest.NewCommandId());
try { await client.SendTransferAsync(uncertain); throw new Exception("T3 timeout expected"); } catch (SecsException) { checks++; }
Check(wireStore.Snapshot().Single(j => j.Request.CommandId == uncertain.CommandId).Phase == JobPhase.Uncertain, "Missing S2F50 becomes uncertain, never auto-retried");
try { await client.SendTransferAsync(Req(TransferRequest.NewCommandId())); throw new Exception("Pending cargo should block new ID"); } catch (InvalidOperationException) { checks++; }
await client.DisconnectAsync();
Check(!client.Selected && !client.Communicating, "Disconnect clears transport/communication readiness");
cts.Cancel(); await serverLoop.WaitAsync(TimeSpan.FromSeconds(3));
Console.WriteLine($"PASS: {checks} checks; loopback HSMS only. No actual Fleet connection or transport.");
Console.WriteLine("Test data: " + root);

sealed class SilentLogger : ISecsGemLogger
{
    public void MessageIn(SecsMessage m, int id) { }
    public void MessageOut(SecsMessage m, int id) { }
    public void Debug(string s) { }
    public void Info(string s) { }
    public void Warning(string s) { }
    public void Error(string s) { }
    public void Error(string s, Exception e) { }
    public void Error(string s, SecsMessage? m, Exception? e) { }
}
