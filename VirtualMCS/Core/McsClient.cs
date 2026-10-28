using Microsoft.Extensions.Options;
using Secs4Net;
using static Secs4Net.Item;

namespace KukaMcs.Core;

public sealed record ConnectionSettings(string IpAddress = "192.168.0.45", int Port = 5556, ushort DeviceId = 3000, bool IsActive = true, int T3Seconds = 120)
{
    // User chose to keep the existing Fleet endpoint. Do not rewrite unrelated
    // hosts or custom ports, and do not connect/save merely by opening the UI.
    public ConnectionSettings UseCurrentFleetEndpoint() => IpAddress == "192.168.0.45" && Port == 5557 ? this with { Port = 5556 } : this;
}
public sealed record LogEntry(DateTimeOffset Time, string Direction, string Text, byte? Stream = null, byte? Function = null,
    int? SystemBytes = null, string? CommandId = null, uint? Ceid = null, string? Summary = null)
{
    public bool IsMessage => Stream.HasValue;
    public string MessageType => IsMessage ? $"S{Stream}F{Function}" : Direction;
    public bool IsWorkMessage => Stream is 2 or 5 or 6 or 9;
}

public sealed class McsClient : IAsyncDisposable
{
    private HsmsConnection? _connection;
    private SecsGem? _gem;
    private CancellationTokenSource? _lifetime;
    private Task? _receiver;
    private readonly SemaphoreSlim _lifecycle = new(1, 1);
    private readonly SemaphoreSlim _transfer = new(1, 1);
    public JobStore Jobs { get; }
    public CargoStore? Cargo { get; }
    public event Action<CargoSnapshot>? InventoryReceived;
    public bool Selected => _connection?.State == ConnectionState.Selected;
    public bool Communicating { get; private set; }
    public event Action<LogEntry>? Logged;
    public event Action<string>? StateChanged;
    public McsClient(JobStore jobs, CargoStore? cargo = null) { Jobs = jobs; Cargo = cargo; }
    public void Log(string direction, string text) => Logged?.Invoke(new(DateTimeOffset.Now, direction, text));
    public async Task ConnectAsync(ConnectionSettings settings)
    {
        if (!System.Net.IPAddress.TryParse(settings.IpAddress, out var ip) || ip.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork) throw new ArgumentException("IPv4 주소를 입력하세요.");
        if (settings.Port is < 1 or > 65535 || settings.DeviceId > 32767 || settings.T3Seconds is < 1 or > 600) throw new ArgumentException("포트/Session ID/T3 범위를 확인하세요.");
        await _lifecycle.WaitAsync().ConfigureAwait(false);
        try
        {
            await StopAsync().ConfigureAwait(false);
            var options = Options.Create(new SecsGemOptions { DeviceId = settings.DeviceId, IsActive = settings.IsActive, IpAddress = settings.IpAddress, Port = settings.Port,
                T3 = checked(settings.T3Seconds * 1000), T5 = 10000, T6 = 5000, T7 = 10000, T8 = 5000, LinkTestInterval = 60000 });
            var logger = new ClientLogger(this);
            _lifetime = new();
            var token = _lifetime.Token;
            var connection = new HsmsConnection(options, logger) { LinkTestEnabled = true };
            var gem = new SecsGem(options, connection, logger);
            _connection = connection; _gem = gem;
            connection.ConnectionChanged += (_, state) =>
            {
                if (!ReferenceEquals(_connection, connection)) return;
                Communicating = false;
                if (state != ConnectionState.Selected)
                {
                    try { Jobs.Disconnected(); Cargo?.Disconnected(); } catch (Exception ex) { Log("ERROR", "작업 기록 실패: " + ex.Message); }
                }
                StateChanged?.Invoke(state.ToString());
                if (state == ConnectionState.Selected) _ = Task.Run(async () =>
                {
                    try { await EstablishCoreAsync(gem, token).ConfigureAwait(false); }
                    catch (Exception ex) { Log("ERROR", "자동 S1F13 실패: " + ex.Message); }
                });
            };
            _receiver = Task.Run(() => ReceiveAsync(gem, token));
            connection.Start(token);
            Log("INFO", $"{(settings.IsActive ? "Active 접속" : "Passive 대기")}: {settings.IpAddress}:{settings.Port} / Session {settings.DeviceId}");
        }
        finally { _lifecycle.Release(); }
    }
    private (SecsGem Gem, CancellationToken Token) Session(bool requireCommunication)
    {
        if (_gem is null || _lifetime is null || !Selected) throw new InvalidOperationException("HSMS Selected 연결이 필요합니다.");
        if (requireCommunication && !Communicating) throw new InvalidOperationException("S1F13 통신 확립부터 확인하세요.");
        return (_gem, _lifetime.Token);
    }
    public Task EstablishAsync() { var (gem, token) = Session(false); return EstablishCoreAsync(gem, token); }
    private async Task EstablishCoreAsync(SecsGem gem, CancellationToken token)
    {
        using var primary = Protocol.Establish();
        using var reply = await gem.SendAsync(primary, token).ConfigureAwait(false);
        uint ack = Protocol.Ack(reply, 1, 14);
        if (ReferenceEquals(gem, _gem) && Selected) Communicating = ack == 0;
        Log(ack == 0 ? "INFO" : "ERROR", $"S1F14 COMMACK={ack}");
        StateChanged?.Invoke(Communicating ? "COMMUNICATING" : "Selected · 통신 확립 실패");
    }
    public async Task OnlineAsync()
    {
        var (gem, token) = Session(true);
        using var primary = Protocol.Online();
        using var reply = await gem.SendAsync(primary, token).ConfigureAwait(false);
        Log("INFO", $"S1F18 ONLACK={Protocol.Ack(reply, 1, 18)}");
    }
    public async Task SendTransferAsync(TransferRequest request)
    {
        if (!await _transfer.WaitAsync(0).ConfigureAwait(false)) throw new InvalidOperationException("이미 전송을 처리 중입니다.");
        try
        {
            var (gem, token) = Session(true);
            using var message = Protocol.Transfer(request);
            if (Cargo?.BlocksTransfer(request) == true) throw new InvalidOperationException("이 화물/포트의 입출고 결과가 미확인입니다. 실제 상태 재확인이 필요합니다.");
            Jobs.Reserve(request); // Fails closed on journal error, before any network send.
            try
            {
                using var reply = await gem.SendAsync(message, token).ConfigureAwait(false);
                Jobs.Acknowledge(request.CommandId, Protocol.Ack(reply, 2, 50));
            }
            catch (Exception ex)
            {
                Jobs.Uncertain(request.CommandId, "응답/기록 미확인: " + ex.Message + " / 새 명령으로 재전송하지 말고 Fleet에서 확인하세요.");
                throw;
            }
        }
        finally { _transfer.Release(); }
    }
    public async Task SendCargoAsync(CargoRequest request)
    {
        if (!await _transfer.WaitAsync(0).ConfigureAwait(false)) throw new InvalidOperationException("다른 명령을 전송 중입니다.");
        try
        {
            var store = Cargo ?? throw new InvalidOperationException("화물 기록 저장소가 필요합니다.");
            var (gem, token) = Session(true);
            using var message = CargoProtocol.Request(request);
            if (request.Command is "CARRIER_IN" or "CARRIER_OUT" && Jobs.Snapshot().Any(j => j.IsPending && (j.Request.CarrierId == request.CarrierId || j.Request.SourcePort == request.Port || j.Request.DestPort == request.Port)))
                throw new InvalidOperationException("화물/포트에 미확인 운송이 있습니다.");
            store.Reserve(request);
            try
            {
                using var reply = await gem.SendAsync(message, token).ConfigureAwait(false);
                store.Ack(request.RequestId, Protocol.Ack(reply, 2, 42));
            }
            catch { store.Uncertain(request.RequestId); throw; }
        }
        finally { _transfer.Release(); }
    }
    public async Task SendControlAsync(string command, string? commandId = null)
    {
        if (!await _transfer.WaitAsync(0).ConfigureAwait(false)) throw new InvalidOperationException("다른 명령을 전송 중입니다.");
        try
        {
            var (gem, token) = Session(true);
            using var message = Protocol.Control(command, commandId);
            var targets = commandId is not null ? Jobs.Snapshot().Where(j => j.Request.CommandId == commandId).ToArray() : Jobs.Snapshot().Where(j => j.IsPending).ToArray();
            if (commandId is not null && targets.Length != 1) throw new InvalidOperationException("선택 작업을 찾을 수 없습니다.");
            if (targets.Any(j => !j.IsPending || j.Phase == JobPhase.Uncertain || j.PendingControl.Length != 0)) throw new InvalidOperationException("기존 명령의 결과 확인이 필요합니다.");
            foreach (var job in targets) Jobs.BeginControl(job.Request.CommandId, command);
            try
            {
                using var reply = await gem.SendAsync(message, token).ConfigureAwait(false);
                uint ack = Protocol.Ack(reply, 2, 42);
                foreach (var job in targets) Jobs.ControlAcknowledge(job.Request.CommandId, command, ack);
                Log("INFO", $"{command} HCACK={ack}. 결과는 별도 이벤트 보고로 확인합니다.");
            }
            catch (Exception ex)
            {
                foreach (var job in targets) Jobs.Uncertain(job.Request.CommandId, $"{command} 결과 미확인 · 자동 재전송 없음: {ex.Message}");
                throw;
            }
        }
        finally { _transfer.Release(); }
    }
    private async Task ReceiveAsync(SecsGem gem, CancellationToken token)
    {
        try
        {
            await foreach (var incoming in gem.GetPrimaryMessageAsync(token).ConfigureAwait(false))
            {
                using var message = incoming.PrimaryMessage;
                try
                {
                    SecsMessage? reply;
                    if (message.S == 6 && message.F == 11)
                    {
                        try
                        {
                            TransferReport? report = Protocol.ParseReport(message);
                            if (report is not null)
                            {
                                bool matched = Jobs.Report(report);
                                Log("EVENT", $"CEID={report.Ceid}, COMMANDID={report.CommandId}, Result={report.ResultCode}, Location={report.CarrierLocation}" + (matched ? "" : " (로컬 작업과 불일치/미등록)"));
                            }
                            else if (Protocol.ParseVehicleReport(message) is { } vehicle)
                            {
                                bool matched = Jobs.ReportVehicle(vehicle);
                                Log("EVENT", $"{vehicle.EventText}, CEID={vehicle.Ceid}, COMMANDID={vehicle.CommandId}, AMR={vehicle.VehicleId}, Port={vehicle.TransferPort}" + (matched ? "" : " (로컬 작업과 불일치/미등록)"));
                            }
                            else if (Protocol.ParseCarrierReport(message) is { } carrier)
                            {
                                bool matched = Jobs.ReportCarrier(carrier);
                                Log("EVENT", $"{carrier.EventText}, CEID={carrier.Ceid}, COMMANDID={carrier.CommandId}, AMR={carrier.VehicleId}, Location={carrier.CarrierLocation}" + (matched ? "" : " (로컬 작업과 불일치/미등록)"));
                            }
                            else if (CargoProtocol.Result(message) is { } result)
                            {
                                bool matched = Cargo?.Result(result) == true;
                                Log("CARGO", $"{result.Command} · {result.Outcome} · {result.CarrierId} · {result.Port} · {result.Detail}" + (matched ? "" : " (미등록 REQUESTID)"));
                            }
                            else if (CargoProtocol.Snapshot(message) is { } inventory)
                            {
                                if (Cargo?.AcceptSnapshot(inventory.RequestId) == true) InventoryReceived?.Invoke(inventory);
                                Log("CARGO", $"화물 조회 · {inventory.Items.Count}건 · REQUESTID {inventory.RequestId}");
                            }
                            else if (CargoProtocol.IdRead(message) is { } scan) Log("SCAN", $"CarrierIDRead 406 · {scan.ResultText}({scan.Status}) · {scan.CarrierId} · {scan.Location} · 실제/시험 출처는 송신 측 확인 필요");
                            else if (Protocol.ParseUnitAlarm(message) is { } alarm) Log("ALARM", $"알람 {(alarm.Ceid == 702 ? "발생" : "해제")} · AMR {alarm.UnitId} · ALID {alarm.AlarmId} · {alarm.AlarmText}");
                            else Log("EVENT", "S6F11 수신: 시스템/기타 이벤트. 본문을 기록하며 작업 완료로 처리하지 않습니다.");
                            reply = new(6, 12, false) { SecsItem = B(0) };
                        }
                        catch (Exception ex) when (ex is FormatException or NotSupportedException or IndexOutOfRangeException or OverflowException)
                        {
                            Log("ERROR", "S6F11 형식 오류: " + ex.Message);
                            reply = new(6, 12, false) { SecsItem = B(1) };
                        }
                    }
                    else if (message.S == 5 && message.F == 1)
                    {
                        var body = message.SecsItem;
                        if (body?.Format == SecsFormat.List && body.Count == 3 && body[0].Format == SecsFormat.Binary && body[0].Count == 1 && body[1].Format == SecsFormat.U4 && body[1].Count == 1 && body[2].Format == SecsFormat.ASCII)
                        {
                            Log("ALARM", $"알람 {( (Protocol.Number(body[0]) & 128) != 0 ? "발생" : "해제")} · ALCD={Protocol.Number(body[0])}, ALID={Protocol.Number(body[1])}, {body[2].GetString()}");
                            reply = new(5, 2, false) { SecsItem = B(0) };
                        }
                        else reply = new(5, 2, false) { SecsItem = B(1) };
                    }
                    else if (message.S == 1 && message.F == 13) reply = new(1, 14, false) { SecsItem = L(B(0), L()) };
                    else if (message.S == 1 && message.F == 1) reply = new(1, 2, false) { SecsItem = L() };
                    else reply = null;
                    using (reply) if (message.ReplyExpected) await incoming.TryReplyAsync(reply, token).ConfigureAwait(false);
                }
                catch (Exception ex) { Log("ERROR", "수신 처리/ACK 실패: " + ex.Message); }
            }
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { }
        catch (Exception ex)
        {
            Communicating = false;
            try { Jobs.Disconnected(); Cargo?.Disconnected(); } catch (Exception disk) { Log("ERROR", disk.Message); }
            Log("ERROR", "수신 루프 중단: " + ex.Message); StateChanged?.Invoke("수신 중단 · 재연결 필요");
        }
    }
    public async Task DisconnectAsync()
    {
        await _lifecycle.WaitAsync().ConfigureAwait(false);
        try { await StopAsync().ConfigureAwait(false); } finally { _lifecycle.Release(); }
    }
    private async Task StopAsync()
    {
        Communicating = false;
        var connection = _connection; var gem = _gem; var lifetime = _lifetime; var receiver = _receiver;
        _connection = null; _gem = null; _lifetime = null; _receiver = null;
        if (lifetime is not null) await lifetime.CancelAsync().ConfigureAwait(false);
        gem?.Dispose();
        if (connection is not null) await connection.DisposeAsync().ConfigureAwait(false);
        if (receiver is not null) await receiver.ConfigureAwait(false);
        lifetime?.Dispose();
        Jobs.Disconnected(); Cargo?.Disconnected(); StateChanged?.Invoke("DISCONNECTED");
    }
    public async ValueTask DisposeAsync() { await DisconnectAsync().ConfigureAwait(false); }
    private sealed class ClientLogger(McsClient owner) : ISecsGemLogger
    {
        private readonly System.Collections.Concurrent.ConcurrentDictionary<int, string> _outgoing = new();
        private readonly System.Collections.Concurrent.ConcurrentDictionary<int, string> _incoming = new();
        public void MessageIn(SecsMessage message, int id) => Message(message, id, true);
        public void MessageOut(SecsMessage message, int id) => Message(message, id, false);
        private void Message(SecsMessage message, int id, bool received)
        {
            string? command = null; uint? ceid = null;
            string summary = $"S{message.S}F{message.F}";
            try
            {
                if (message.S == 2 && message.F == 49)
                {
                    command = message.SecsItem![3][0][1][0][1].GetString(); summary = "운송 요청";
                }
                else if (message.S == 2 && message.F == 50) summary = $"운송 응답 · HCACK {Protocol.Ack(message, 2, 50)} (완료 아님)";
                else if (message.S == 6 && message.F == 11)
                {
                    var report = Protocol.ParseReport(message);
                    if (report is not null)
                    {
                        command = report.CommandId; ceid = report.Ceid;
                        string phase = ceid switch { 208 => "작업 시작 보고", 211 => "운송 진행 보고", 207 when report.ResultCode == 0 => "정상 완료 보고", 207 => "비정상 종료 보고", _ => Protocol.EventText(ceid.Value) + " 보고" };
                        summary = $"{phase} · Result {report.ResultCode} · {report.CarrierLocation}";
                    }
                    else if (Protocol.ParseVehicleReport(message) is { } vehicle)
                    {
                        command = vehicle.CommandId; ceid = vehicle.Ceid;
                        summary = $"{vehicle.EventText} 보고 · AMR {vehicle.VehicleId} · {vehicle.TransferPort}";
                    }
                    else if (Protocol.ParseCarrierReport(message) is { } carrier)
                    {
                        command = carrier.CommandId; ceid = carrier.Ceid;
                        summary = $"{carrier.EventText} 보고 · {carrier.CarrierId} · CarrierLoc {carrier.CarrierLocation}";
                    }
                    else if (CargoProtocol.Result(message) is { } result) { command = result.RequestId; ceid = 9002; summary = $"화물 {result.Command} · {result.Outcome} · {result.CarrierId} · {result.Detail}"; }
                    else if (CargoProtocol.Snapshot(message) is { } inventory) { command = inventory.RequestId; ceid = 9001; summary = $"Fleet 화물 조회 · {inventory.Items.Count}건"; }
                    else if (CargoProtocol.IdRead(message) is { } scan) { ceid = 406; summary = $"ID 판독 · {scan.ResultText} · {scan.CarrierId} · {scan.Location}"; }
                    else if (Protocol.ParseUnitAlarm(message) is { } alarm) { ceid = alarm.Ceid; summary = $"알람 {(alarm.Ceid == 702 ? "발생" : "해제")} · AMR {alarm.UnitId} · ALID {alarm.AlarmId}"; }
                    else { ceid = Protocol.Number(message.SecsItem![1]); summary = Protocol.EventText(ceid.Value) + " 보고"; }
                }
                else if (message.S == 2 && message.F == 41) {summary = message.SecsItem![0].GetString() + " 요청"; foreach (var pair in message.SecsItem[1].Items) if (pair[0].GetString() is "COMMANDID" or "REQUESTID") command = pair[1].GetString();}
                else if (message.S == 2 && message.F == 42) summary = $"제어 응답 · HCACK {Protocol.Ack(message, 2, 42)}";
                else if (message.S == 6 && message.F == 12) summary = $"이벤트 ACK · ACKC6 {Protocol.Number(message.SecsItem!)}";
                else if (message.S == 5 && message.F == 1) summary = "알람 보고";
                else if (message.S == 5 && message.F == 2) summary = "알람 ACK";
            }
            catch (Exception ex) when (ex is FormatException or NotSupportedException or IndexOutOfRangeException or NullReferenceException or OverflowException)
            { summary += " · 본문 형식 확인 필요"; }
            var requests = received ? _incoming : _outgoing;
            var replies = received ? _outgoing : _incoming;
            if (message.F % 2 == 0 && replies.TryRemove(id, out var related)) command ??= related;
            if (command is not null && message.F % 2 == 1 && message.ReplyExpected)
            {
                if (requests.Count > 1024) requests.Clear();
                requests[id] = command;
            }
            owner.Logged?.Invoke(new(DateTimeOffset.Now, received ? "RX" : "TX", $"SB=0x{id:X8}\n{Protocol.Sml(message)}",
                message.S, message.F, id, command, ceid, summary));
        }
        public void Debug(string message) { }
        public void Info(string message) => owner.Log("INFO", message);
        public void Warning(string message) => owner.Log("WARN", message);
        public void Error(string message) => owner.Log("ERROR", message);
        public void Error(string message, Exception ex) => owner.Log("ERROR", message + " " + ex.Message);
        public void Error(string message, SecsMessage? secsMessage, Exception? ex) => owner.Log("ERROR", message + " " + ex?.Message);
    }
}
