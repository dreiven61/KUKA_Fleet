using System.ComponentModel;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using KukaMcs.Core;

namespace KukaMcs.App;

public partial class MainWindow : Window
{
    private readonly string _dataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ServoStar", "KukaVirtualMCS");
    private readonly McsClient _client;
    private readonly object _logLock = new();
    private bool _ready, _busy, _connected, _closing, _closed;
    private string _nextId = TransferRequest.NewCommandId();
    private readonly HashSet<string> _swapped = new();
    private string? _diskLogError;
    private bool _refreshingJobs;
    private readonly ObservableCollection<MessageRow> _messages = [];
    private readonly ObservableCollection<MessageRow> _workMessages = [];
    public MainWindow(string? dataDirectory = null)
    {
        _dataDirectory = dataDirectory ?? _dataDirectory;
        InitializeComponent();
        SourceCombo.ItemsSource = DestCombo.ItemsSource = WorkPorts.All;
        SourceCombo.SelectedItem = "PORT_97"; DestCombo.SelectedItem = "PORT_89";
        CargoPortCombo.ItemsSource = WorkPorts.All; CargoPortCombo.SelectedItem = "PORT_97";
        Directory.CreateDirectory(_dataDirectory); Directory.CreateDirectory(Path.Combine(_dataDirectory, "logs"));
        _client = new(new JobStore(Path.Combine(_dataDirectory, "jobs.jsonl")), new CargoStore(Path.Combine(_dataDirectory, "cargo-operations.jsonl")));
        _client.Cargo!.Changed += () => Ui(() => { CargoOperationsGrid.ItemsSource = _client.Cargo.Snapshot().Take(100).ToArray(); RefreshControls(); });
        _client.InventoryReceived += inventory => Ui(() => {
            InventoryGrid.ItemsSource = inventory.Items;
            InventoryTimeText.Text = $"목록 수신 {DateTimeOffset.Now:HH:mm:ss} · {inventory.Items.Count}건 · 자동 갱신 아님 / 실제 스캐너 미연결";
        });
        CargoOperationsGrid.ItemsSource = _client.Cargo.Snapshot().Take(100).ToArray();
        AllMessagesGrid.ItemsSource = _messages; WorkMessagesGrid.ItemsSource = _workMessages;
        CollectionViewSource.GetDefaultView(_messages).Filter = MatchesSelectedJob;
        CollectionViewSource.GetDefaultView(_workMessages).Filter = MatchesSelectedJob;
        _client.Logged += OnLog;
        _client.StateChanged += state => Ui(() => { ConnectionStateText.Text = state; RefreshControls(); });
        _client.Jobs.Changed += job => Ui(() =>
        {
            if (job.Phase == JobPhase.Completed && job.Request.CarrierId == CarrierText.Text && _swapped.Add(job.Request.CommandId) && AutoSwapCheck.IsChecked == true)
            {
                SourceCombo.SelectedItem = job.Request.DestPort;
                DestCombo.SelectedItem = job.Request.SourcePort;
            }
            RefreshJobs(job.Phase == JobPhase.Sending ? job.Request.CommandId : null); RefreshControls();
            HintText.Text = $"{job.PhaseText} · {job.Request.SourcePort} → {job.Request.DestPort} · {job.UpdatedAt:HH:mm:ss.fff}";
        });
        LoadSettings(); _ready = true; RefreshJobs(); RefreshPreview(); RefreshControls();
        _client.Log("INFO", "실행 준비. 자동 접속/운송은 하지 않습니다. 데이터 폴더: " + _dataDirectory);
    }
    private void Ui(Action action)
    {
        if (_closed || Dispatcher.HasShutdownStarted) return;
        if (Dispatcher.CheckAccess()) action(); else _ = Dispatcher.BeginInvoke(action);
    }
    private void OnLog(LogEntry entry)
    {
        string text = $"[{entry.Time:HH:mm:ss.fff}] {entry.Direction}  {entry.Summary}\r\n{entry.Text}\r\n";
        lock (_logLock)
        {
            try
            {
                File.AppendAllText(Path.Combine(_dataDirectory, "logs", $"secs_{entry.Time:yyyyMMdd}.log"), text, Encoding.UTF8);
                if (entry.IsMessage) File.AppendAllText(Path.Combine(_dataDirectory, "logs", $"messages_{entry.Time:yyyyMMdd}.log"), text, Encoding.UTF8);
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { _diskLogError = ex.Message; }
        }
        Ui(() =>
        {
            if (entry.IsMessage)
            {
                var row = new MessageRow(entry); _messages.Insert(0, row);
                if (entry.IsWorkMessage) _workMessages.Insert(0, row);
                if (_messages.Count > 2000) { var old = _messages[^1]; _messages.RemoveAt(_messages.Count - 1); _workMessages.Remove(old); }
                if (FollowMessagesCheck.IsChecked == true && MatchesSelectedJob(row))
                {
                    AllMessagesGrid.SelectedItem = row; AllMessagesGrid.ScrollIntoView(row);
                    if (entry.IsWorkMessage) { WorkMessagesGrid.SelectedItem = row; WorkMessagesGrid.ScrollIntoView(row); }
                }
                MessageCountText.Text = $"작업 {_workMessages.Count} / 전체 {_messages.Count}건";
            }
            else
            {
                if (LogText.Text.Length > 180000) LogText.Text = LogText.Text[^90000..];
                LogText.AppendText(text); LogText.ScrollToEnd();
            }
            if (_diskLogError is not null) FooterText.Text = "로그 파일 저장 실패: " + _diskLogError;
        });
    }
    private ConnectionSettings Settings() => new(IpText.Text.Trim(), int.Parse(PortText.Text), ushort.Parse(DeviceText.Text), ModeCombo.SelectedIndex == 0, int.Parse(TimeoutText.Text));
    private string SettingsPath => Path.Combine(_dataDirectory, "settings.json");
    private void LoadSettings()
    {
        if (!File.Exists(SettingsPath)) return;
        try
        {
            var s = JsonSerializer.Deserialize<ConnectionSettings>(File.ReadAllText(SettingsPath)) ?? new();
            var previous = s;
            s = s.UseCurrentFleetEndpoint();
            if (s != previous) _client.Log("INFO", "현재 Fleet 연결 포트를 5557에서 기존 5556으로 맞췄습니다. 자동 접속하지 않으며 서버 수신부 전환 후 연결하세요.");
            IpText.Text = s.IpAddress; PortText.Text = s.Port.ToString(); DeviceText.Text = s.DeviceId.ToString(); ModeCombo.SelectedIndex = s.IsActive ? 0 : 1; TimeoutText.Text = s.T3Seconds.ToString();
        }
        catch (Exception ex) { _client.Log("WARN", "연결 설정을 읽지 못해 기본값을 사용합니다: " + ex.Message); }
    }
    private async Task RunAsync(Func<Task> action)
    {
        if (_busy) return;
        _busy = true; RefreshControls();
        try { await action(); }
        catch (Exception ex) { _client.Log("ERROR", ex.Message); HintText.Text = ex.Message; }
        finally { _busy = false; RefreshControls(); }
    }
    private async void Connect_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        ConnectionSettings settings = Settings();
        await _client.ConnectAsync(settings);
        _connected = true;
        string temp = SettingsPath + ".tmp"; File.WriteAllText(temp, JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true })); File.Move(temp, SettingsPath, true);
        HintText.Text = "HSMS Selected 및 COMMUNICATING을 기다리세요. 전송 전 화물의 실제 출발 포트를 확인하세요.";
    });
    private async void Disconnect_Click(object sender, RoutedEventArgs e) => await RunAsync(async () => { await _client.DisconnectAsync(); _connected = false; });
    private async void Establish_Click(object sender, RoutedEventArgs e) => await RunAsync(() => _client.EstablishAsync());
    private async void Online_Click(object sender, RoutedEventArgs e) => await RunAsync(() => _client.OnlineAsync());
    private async void Cancel_Click(object sender, RoutedEventArgs e) => await ControlSelected("CANCEL");
    private async void Abort_Click(object sender, RoutedEventArgs e) => await ControlSelected("ABORT");
    private async Task ControlSelected(string command) => await RunAsync(async () =>
    {
        if (JobsGrid.SelectedItem is not JobRow row) throw new InvalidOperationException("작업 이력에서 대상 작업을 선택하세요.");
        await _client.SendControlAsync(command, row.CommandId);
        HintText.Text = command + " 전송. 접수 응답과 실제 결과 보고를 확인하세요.";
    });
    private async void Pause_Click(object sender, RoutedEventArgs e) => await RunAsync(() => _client.SendControlAsync("PAUSE"));
    private async void Resume_Click(object sender, RoutedEventArgs e) => await RunAsync(() => _client.SendControlAsync("RESUME"));
    private async void CargoQuery_Click(object sender, RoutedEventArgs e) => await SendCargo("CARRIER_QUERY");
    private async void CargoIn_Click(object sender, RoutedEventArgs e) => await SendCargo("CARRIER_IN");
    private async void CargoOut_Click(object sender, RoutedEventArgs e) => await SendCargo("CARRIER_OUT");
    private async void CargoScan_Click(object sender, RoutedEventArgs e) => await SendCargo("CARRIER_SCAN_TEST");
    private async void CargoVerify_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        if (PhysicalCargoCheck.IsChecked != true || CargoOperationsGrid.SelectedItem is not CargoOperation { Outcome: "UNCERTAIN" } op || op.Request.Command is not ("CARRIER_IN" or "CARRIER_OUT"))
            throw new InvalidOperationException("실제 화물 상태를 확인하고 UNCERTAIN 입출고 이력을 선택하세요.");
        await _client.SendCargoAsync(new(TransferRequest.NewCommandId(), "CARRIER_VERIFY", TargetRequestId: op.Request.RequestId));
        PhysicalCargoCheck.IsChecked = false;
        HintText.Text = "읽기 전용 재확인 요청. 원래 입출고를 재전송하지 않습니다. Fleet 현재 상태가 요청 결과와 일치해야 CONFIRMED로 전환됩니다.";
    });
    private Task SendCargo(string command) => RunAsync(async () =>
    {
        bool mutation = command is "CARRIER_IN" or "CARRIER_OUT";
        if (mutation && PhysicalCargoCheck.IsChecked != true) throw new InvalidOperationException("실제 반입·반출과 포트 위치 확인을 먼저 체크하세요.");
        if (command == "CARRIER_OUT" && DeleteCargoCheck.IsChecked == true && MessageBox.Show($"{CarrierText.Text} 등록을 반출과 함께 삭제합니다. 실제 반출을 확인했습니까?", "화물 등록 삭제", MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
        var r = new CargoRequest(TransferRequest.NewCommandId(), command, command == "CARRIER_QUERY" ? "" : CarrierText.Text.Trim(),
            command == "CARRIER_QUERY" ? "" : CargoPortCombo.SelectedItem as string ?? "", CargoModelText.Text.Trim(), ScannedIdText.Text.Trim(), ReadFailedCheck.IsChecked != true, DeleteCargoCheck.IsChecked == true);
        await _client.SendCargoAsync(r);
        if (mutation) PhysicalCargoCheck.IsChecked = false;
        HintText.Text = command == "CARRIER_SCAN_TEST" ? "수동 판독 시험 전송. 406은 실제 스캐너 판독 증거가 아닙니다. 입고·운송은 실행하지 않습니다."
            : "화물 명령 응답 수신. 이력의 CONFIRMED/REJECTED/UNCERTAIN 결과를 확인하고 목록을 다시 조회하세요.";
    });
    private void Inventory_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_ready || InventoryGrid.SelectedItem is not CargoItem row) return;
        CarrierText.Text = row.CarrierId; CargoModelText.Text = row.Model;
        if (WorkPorts.Contains(row.Port)) { SourceCombo.SelectedItem = row.Port; CargoPortCombo.SelectedItem = row.Port; }
        ScannedIdText.Clear(); PhysicalCargoCheck.IsChecked = false;
    }
    private void LogTab_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_ready || e.Source != LogTabs) return;
        bool cargo = LogTabs.SelectedItem == CargoTab;
        JobHistoryPanel.Visibility = cargo ? Visibility.Collapsed : Visibility.Visible;
        JobHistoryRow.Height = cargo ? new GridLength(0) : new GridLength(1.5, GridUnitType.Star);
        RefreshControls();
    }
    private async void Transfer_Click(object sender, RoutedEventArgs e) => await SendSelectedAsync();
    private async void Forward_Click(object sender, RoutedEventArgs e) { SourceCombo.SelectedItem = "PORT_89"; DestCombo.SelectedItem = "PORT_97"; await SendSelectedAsync(); }
    private async void Reverse_Click(object sender, RoutedEventArgs e) { SourceCombo.SelectedItem = "PORT_97"; DestCombo.SelectedItem = "PORT_89"; await SendSelectedAsync(); }
    private async Task SendSelectedAsync() => await RunAsync(async () =>
    {
        var request = Request();
        try { await _client.SendTransferAsync(request); HintText.Text = "S2F50 응답 수신. 실제 시작·진행·완료 보고를 기다립니다."; }
        finally { _nextId = TransferRequest.NewCommandId(); RefreshPreview(); RefreshJobs(); }
    });
    private TransferRequest Request() => new(_nextId, CarrierText.Text.Trim(), SourceCombo.SelectedItem as string ?? "", DestCombo.SelectedItem as string ?? "", ushort.Parse(PriorityText.Text));
    private void RefreshPreview()
    {
        if (!_ready) return;
        CommandIdText.Text = _nextId;
        try { using var message = Protocol.Transfer(Request()); PreviewText.Text = Protocol.Sml(message); }
        catch (Exception ex) { PreviewText.Text = ex.Message; }
    }
    private void Route_Changed(object sender, RoutedEventArgs e) { if (_ready) { RefreshPreview(); RefreshControls(); } }
    private void Swap_Click(object sender, RoutedEventArgs e) { (SourceCombo.SelectedIndex, DestCombo.SelectedIndex) = (DestCombo.SelectedIndex, SourceCombo.SelectedIndex); }
    private void RefreshControls()
    {
        if (!_ready) return;
        ConnectButton.IsEnabled = !_busy && !_connected;
        DisconnectButton.IsEnabled = !_busy && _connected;
        EstablishButton.IsEnabled = !_busy && _client.Selected;
        OnlineButton.IsEnabled = !_busy && _client.Communicating;
        bool canSend = !_busy && _client.Selected && _client.Communicating && !_client.Jobs.Snapshot().Any(j => j.IsPending && j.Request.CarrierId == CarrierText.Text.Trim());
        if (canSend) try { canSend = _client.Cargo?.BlocksTransfer(Request()) != true; } catch (Exception) { canSend = false; }
        TransferButton.IsEnabled = ForwardButton.IsEnabled = ReverseButton.IsEnabled = canSend;
        IpText.IsEnabled = PortText.IsEnabled = DeviceText.IsEnabled = ModeCombo.IsEnabled = TimeoutText.IsEnabled = !_connected && !_busy;
        ReleaseButton.IsEnabled = !_busy && _client.Jobs.Snapshot().Any(j => j.IsPending);
        bool controlReady = !_busy && _client.Selected && _client.Communicating;
        var selected = JobsGrid.SelectedItem as JobRow;
        var selectedJob = _client.Jobs.Snapshot().FirstOrDefault(j => j.Request.CommandId == selected?.CommandId);
        CancelButton.IsEnabled = AbortButton.IsEnabled = controlReady && LogTabs.SelectedItem != CargoTab && selectedJob is { IsPending: true, PendingControl.Length: 0 } && selectedJob.Phase != JobPhase.Uncertain;
        PauseButton.IsEnabled = ResumeButton.IsEnabled = controlReady;
        CargoQueryButton.IsEnabled = CargoInButton.IsEnabled = CargoOutButton.IsEnabled = CargoScanButton.IsEnabled = CargoVerifyButton.IsEnabled = controlReady;
    }
    private sealed record JobRow(string Time, string CommandId, string Route, string State, string Ceid, string Result, string Detail, string VehicleId, string CarrierId);
    private void RefreshJobs(string? preferred = null)
    {
        string? selected = preferred ?? (JobsGrid.SelectedItem as JobRow)?.CommandId;
        var snapshot = _client.Jobs.Snapshot();
        _refreshingJobs = true;
        JobsGrid.ItemsSource = snapshot.Take(200).Select(j => new JobRow(j.CreatedAt.ToString("HH:mm:ss"), j.Request.CommandId, $"{j.Request.SourcePort} → {j.Request.DestPort}", j.PhaseText, j.LastCeid?.ToString() ?? "—", j.ResultCode?.ToString() ?? "—", j.Detail, j.VehicleId ?? "미할당", j.Request.CarrierId)).ToArray();
        JobsGrid.SelectedItem = JobsGrid.Items.Cast<JobRow>().FirstOrDefault(r => r.CommandId == selected) ?? JobsGrid.Items.Cast<JobRow>().FirstOrDefault();
        _refreshingJobs = false;
        PendingText.Text = $"미확인 작업 {snapshot.Count(j => j.IsPending)}건";
        UpdateJobDetails(); RefreshMessageFilters();
    }
    private sealed record MessageRow(LogEntry Entry)
    {
        public string Time => Entry.Time.ToString("HH:mm:ss.fff");
        public string Direction => Entry.Direction;
        public string MessageType => Entry.MessageType;
        public string Ceid => Entry.Ceid?.ToString() ?? "";
        public string Summary => Entry.Summary ?? Entry.MessageType;
        public string CommandId => Entry.CommandId ?? "";
        public string SystemBytes => Entry.SystemBytes is int id ? $"0x{id:X8}" : "";
    }
    private bool MatchesSelectedJob(object item) => SelectedJobOnlyCheck.IsChecked != true ||
        item is MessageRow row && JobsGrid.SelectedItem is JobRow job && row.CommandId == job.CommandId;
    private void RefreshMessageFilters()
    {
        CollectionViewSource.GetDefaultView(_messages).Refresh();
        CollectionViewSource.GetDefaultView(_workMessages).Refresh();
    }
    private void Filter_Changed(object sender, RoutedEventArgs e) { if (_ready) RefreshMessageFilters(); }
    private void Message_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (sender is not DataGrid grid) return;
        TextBox box = ReferenceEquals(grid, WorkMessagesGrid) ? WorkMessageBody : AllMessageBody;
        if (grid.SelectedItem is not MessageRow row) { box.Text = "메시지 행을 선택하세요."; return; }
        box.Text = $"{row.Entry.Time:yyyy-MM-dd HH:mm:ss.fff}  {row.Direction}  {row.MessageType}  {row.SystemBytes}\r\n{row.Summary}\r\nCOMMANDID: {row.CommandId}\r\n\r\n{row.Entry.Text}";
    }
    private sealed record TimelineRow(string Time, string State, string Ceid, string Detail);
    private void UpdateJobDetails()
    {
        if (JobsGrid.SelectedItem is not JobRow row) return;
        var job = _client.Jobs.Snapshot().First(j => j.Request.CommandId == row.CommandId);
        var history = _client.Jobs.History(row.CommandId);
        string ReportTime(uint ceid)
        {
            var hit = history.FirstOrDefault(h => h.LastCeid == ceid && (ceid == 207 ? h.Phase is JobPhase.Completed or JobPhase.AbnormalEnded : h.Phase == (ceid == 208 ? JobPhase.Initiated : JobPhase.Transferring)));
            return hit is null ? "미수신" : hit.UpdatedAt is { } time ? time.ToString("HH:mm:ss.fff") : "수신됨 · 이전 버전 시각 없음";
        }
        JobDetailsText.Text = $"COMMANDID\r\n{job.Request.CommandId}\r\n\r\n화물: {job.Request.CarrierId}\r\n경로: {job.Request.SourcePort} → {job.Request.DestPort}\r\n배차 AGV: {job.VehicleId ?? "미할당"}\r\n상태: {job.PhaseText}\r\n생성: {job.CreatedAt:yyyy-MM-dd HH:mm:ss.fff}\r\n\r\n208 작업 시작: {ReportTime(208)}\r\n211 운송 진행: {ReportTime(211)}\r\n602 픽업 시작: {ReportTime(602)}\r\n603 픽업 완료: {ReportTime(603)}\r\n606 드롭 시작: {ReportTime(606)}\r\n607 드롭 완료: {ReportTime(607)}\r\n207 종료 보고: {ReportTime(207)}\r\n\r\n최근 결과: {job.ResultCode?.ToString() ?? "—"}\r\n{job.Detail}";
        JobDetailsText.Text += $"\r\n\r\n화물 보고 수신 시각\r\n301 화물 적재: {ReportTime(301)}\r\n302 화물 하차: {ReportTime(302)}";
        string VehicleTime(uint ceid, string port)
        {
            var hit=history.FirstOrDefault(h=>h.LastCeid==ceid && h.VehiclePort==port);
            return hit?.UpdatedAt?.ToString("HH:mm:ss.fff") ?? "미수신";
        }
        JobDetailsText.Text += $"\r\n\r\n차량 보고 수신 시각\r\n604 차량 할당: {VehicleTime(604,job.Request.SourcePort)}\r\n601 출발지 도착: {VehicleTime(601,job.Request.SourcePort)}\r\n605 출발지 출발: {VehicleTime(605,job.Request.SourcePort)}\r\n601 도착지 도착: {VehicleTime(601,job.Request.DestPort)}\r\n610 차량 해제: {VehicleTime(610,job.Request.DestPort)}";
        TimelineGrid.ItemsSource = history.Select(h => new TimelineRow(h.UpdatedAt?.ToString("HH:mm:ss.fff") ?? (h.Phase == JobPhase.Sending ? h.CreatedAt.ToString("HH:mm:ss.fff") : "이전 기록 · 시각 없음"), h.PhaseText, h.LastCeid?.ToString() ?? "—", h.Detail)).ToArray();
    }
    private void Jobs_SelectionChanged(object sender, SelectionChangedEventArgs e) { if (_ready && !_refreshingJobs) { UpdateJobDetails(); RefreshMessageFilters(); RefreshControls(); } }
    private void Details_Click(object sender, RoutedEventArgs e) { UpdateJobDetails(); LogTabs.SelectedItem = JobDetailsTab; }
    private void Jobs_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e) { if (JobsGrid.SelectedItem is JobRow) Details_Click(sender, e); }
    private void CopyCommandId_Click(object sender, RoutedEventArgs e) { if (JobsGrid.SelectedItem is JobRow row) Clipboard.SetText(row.CommandId); }
    private void Message_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e) { if (sender is DataGrid { SelectedItem: MessageRow row }) OpenMessage(row); }
    private void MessageWindow_Click(object sender, RoutedEventArgs e)
    {
        var row = (LogTabs.SelectedIndex == 2 ? AllMessagesGrid : WorkMessagesGrid).SelectedItem as MessageRow;
        if (row is not null) OpenMessage(row);
    }
    private void OpenMessage(MessageRow row)
    {
        string text = $"{row.Entry.Time:yyyy-MM-dd HH:mm:ss.fff}  {row.Direction}  {row.MessageType}  {row.SystemBytes}\r\n{row.Summary}\r\nCOMMANDID: {row.CommandId}\r\n\r\n{row.Entry.Text}";
        var panel = new DockPanel { Margin = new Thickness(14) };
        var copy = new Button { Content = "원문 전체 복사", HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 8, 0, 0) };
        copy.Click += (_, _) => Clipboard.SetText(text); DockPanel.SetDock(copy, Dock.Bottom); panel.Children.Add(copy);
        panel.Children.Add(new TextBox { Text = text, IsReadOnly = true, AcceptsReturn = true, VerticalContentAlignment = VerticalAlignment.Top,
            FontFamily = new System.Windows.Media.FontFamily("Consolas, 맑은 고딕"), FontSize = 14, HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
        new Window { Owner = this, Title = $"{row.Direction} {row.MessageType} · {row.Summary}", Width = 940, Height = 680, MinWidth = 600, MinHeight = 400,
            WindowStartupLocation = WindowStartupLocation.CenterOwner, Content = panel }.Show();
    }
    private void Release_Click(object sender, RoutedEventArgs e)
    {
        if (JobsGrid.SelectedItem is not JobRow row) { HintText.Text = "작업 이력에서 해제할 작업을 선택하세요."; return; }
        if (MessageBox.Show($"{row.CommandId}\n\nFleet에서 로봇·화물 위치와 기존 작업 종료를 확인했습니까?\n로컬 전송 대기만 해제하며, Fleet 작업 취소나 완료 명령은 보내지 않습니다.", "현장 확인 후 대기 해제", MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        try { _client.Jobs.Release(row.CommandId); _client.Log("INFO", "사용자 로컬 대기 해제: " + row.CommandId); }
        catch (Exception ex) { HintText.Text = ex.Message; }
    }
    private void Logs_Click(object sender, RoutedEventArgs e) => Process.Start(new ProcessStartInfo(Path.Combine(_dataDirectory, "logs")) { UseShellExecute = true });
    private void ClearLog_Click(object sender, RoutedEventArgs e)
    {
        _messages.Clear(); _workMessages.Clear(); LogText.Clear(); MessageCountText.Text = "작업 0 / 전체 0건";
    }
    internal void LoadPreviewFixture()
    {
        // Explicit offline render mode only. Uses an isolated profile; never connects.
        ConnectionStateText.Text = "OFFLINE PREVIEW";
        var request = new TransferRequest("PREVIEW_" + Guid.NewGuid().ToString("N")[..16], "SIM_CARGO_001", "PORT_97", "PORT_89");
        _client.Jobs.Reserve(request); _client.Jobs.Acknowledge(request.CommandId, 0);
        using var primary = Protocol.Transfer(request);
        OnLog(new(DateTimeOffset.Now, "TX", Protocol.Sml(primary), 2, 49, 1, request.CommandId, null, "운송 요청"));
        foreach (var (ceid,arrivalPort) in new (uint,string?)[] {(208,null),(604,null),(601,request.SourcePort),(211,null),(602,null),(301,null),(603,null),
            (605,null),(601,request.DestPort),(606,null),(302,null),(607,null),(610,null),(207,null)})
        {
            if (ceid is 301 or 302)
            {
                string carrierLocation = ceid == 301 ? "2" : request.DestPort;
                var carrier = new CarrierReport(ceid, "2", request.CarrierId, carrierLocation, request.CommandId);
                _client.Jobs.ReportCarrier(carrier);
                using var carrierMessage = new Secs4Net.SecsMessage(6, 11, true) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.U4(0), Secs4Net.Item.U2((ushort)ceid),
                    Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.U2(3), Secs4Net.Item.L(Secs4Net.Item.A("2"), Secs4Net.Item.A(request.CarrierId), Secs4Net.Item.A(carrierLocation), Secs4Net.Item.A(request.CommandId))))) };
                OnLog(new(DateTimeOffset.Now, "RX", Protocol.Sml(carrierMessage), 6, 11, (int)ceid, request.CommandId, ceid, carrier.EventText + " 보고"));
                continue;
            }
            if (ceid >= 600)
            {
                string port = arrivalPort ?? (ceid < 606 ? request.SourcePort : request.DestPort);
                var vehicle = new VehicleReport(ceid, "2", request.CommandId, port, request.CarrierId);
                _client.Jobs.ReportVehicle(vehicle);
                using var stage = new Secs4Net.SecsMessage(6, 11, true) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.U4(0), Secs4Net.Item.U2((ushort)ceid),
                    Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.U2(5), Secs4Net.Item.L(Secs4Net.Item.A("2"), Secs4Net.Item.A(request.CommandId), Secs4Net.Item.L(Secs4Net.Item.A(port)), Secs4Net.Item.L(Secs4Net.Item.A(request.CarrierId)))))) };
                OnLog(new(DateTimeOffset.Now, "RX", Protocol.Sml(stage), 6, 11, (int)ceid, request.CommandId, ceid, vehicle.EventText + " 보고"));
                continue;
            }
            string location = ceid == 207 ? request.DestPort : request.SourcePort;
            _client.Jobs.Report(new(ceid, request.CommandId, request.CarrierId, request.SourcePort, request.DestPort, location, 0));
            using var report = new Secs4Net.SecsMessage(6, 11, true) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.U4(1), Secs4Net.Item.U2((ushort)ceid), Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.U2(2), Secs4Net.Item.L(Secs4Net.Item.A(request.CommandId), Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.A(request.CarrierId), Secs4Net.Item.A(request.SourcePort), Secs4Net.Item.A(request.DestPort)), Secs4Net.Item.A(location))), Secs4Net.Item.U2(0))))) };
            OnLog(new(DateTimeOffset.Now, "RX", Protocol.Sml(report), 6, 11, (int)ceid, request.CommandId, ceid,
                ceid == 208 ? "작업 시작 보고" : ceid == 211 ? "운송 진행 보고" : "정상 완료 보고"));
        }
        RefreshJobs(request.CommandId);
        InventoryGrid.ItemsSource = new[] { new CargoItem("SIM_CARGO_001", "PORT_97", "SECS_ST_97", "SIM_TRAY_001", 1, 0),
            new CargoItem("SIM_CARGO_002", "PORT_84", "lay01-dist1-84", "SIM_TRAY_001", 1, 0), new CargoItem("SCAN_NEW_004", "", "", "SIM_TRAY_001", 0, 0) };
        var incoming = new CargoRequest("PREVIEW_IN_" + Guid.NewGuid().ToString("N")[..16], "CARRIER_IN", "SCAN_NEW_004", "PORT_94", "SIM_TRAY_001", "SCAN_NEW_004");
        _client.Cargo!.Reserve(incoming); _client.Cargo.Result(new(incoming.RequestId, incoming.Command, "CONFIRMED", incoming.CarrierId, incoming.Port, "INVENTORY_VERIFIED (OFFLINE FIXTURE)"));
        InventoryTimeText.Text = "오프라인 예시 · 실제 Fleet 조회/스캐너 입력 아님";
        LogTabs.SelectedItem = CargoTab;
        HintText.Text = "화면 미리보기용 예시입니다. 실제 연결·운송은 실행하지 않았습니다.";
    }
    protected override async void OnClosing(CancelEventArgs e)
    {
        if (_closed) { base.OnClosing(e); return; }
        e.Cancel = true; if (_closing) return; _closing = true; IsEnabled = false;
        try { await _client.DisposeAsync(); } catch (Exception ex) { OnLog(new(DateTimeOffset.Now, "ERROR", ex.Message)); }
        _closed = true; Close();
    }
}
