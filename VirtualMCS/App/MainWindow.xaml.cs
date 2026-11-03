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
    private readonly bool _offlinePreview;
    private readonly object _logLock = new();
    private bool _ready, _busy, _connected, _closing, _closed;
    private string _nextId = TransferRequest.NewCommandId();
    private readonly HashSet<string> _swapped = new();
    private string? _diskLogError;
    private bool _refreshingJobs;
    private readonly ObservableCollection<MessageRow> _messages = [];
    private readonly ObservableCollection<MessageRow> _workMessages = [];
    private StatusWindow? _statusWindow;
    private LogWindow? _logWindow;
    private JobHistoryWindow? _jobHistoryWindow;
    private readonly ObservableCollection<LogEntry> _logEntries = [];
    private int _timelineNavigation;
    private bool _routeInitialized;

    public MainWindow(string? dataDirectory = null)
    {
        _dataDirectory = dataDirectory ?? _dataDirectory;
        _offlinePreview = dataDirectory is not null;
        InitializeComponent();
        VersionText.Text = $"v{typeof(MainWindow).Assembly.GetName().Version?.ToString(3)}  ·  Secs4Net 3.1.0";
        WorkPorts.Reset();
        RefreshCatalogControls();
        Directory.CreateDirectory(_dataDirectory); Directory.CreateDirectory(Path.Combine(_dataDirectory, "logs"));
        _client = new(new JobStore(Path.Combine(_dataDirectory, "jobs.jsonl")), new CargoStore(Path.Combine(_dataDirectory, "cargo-operations.jsonl")));
        _client.AutoStatusOnConnect = AutoStatusCheck.IsChecked == true;
        _client.Cargo!.Changed += () => Ui(() => { CargoOperationsGrid.ItemsSource = _client.Cargo.Snapshot().Take(100).ToArray(); RefreshControls(); });
        _client.InventoryReceived += inventory => Ui(() => {
            InventoryGrid.ItemsSource = inventory.Items;
            InventoryTimeText.Text = $"목록 수신 {DateTimeOffset.Now:HH:mm:ss} · {inventory.Items.Count}건 · 자동 갱신 아님 / 실제 스캐너 미연결";
        });
        CargoOperationsGrid.ItemsSource = _client.Cargo.Snapshot().Take(100).ToArray();
        _client.RecoveryReceived += recovery => Ui(() => {
            RecoveryStateText.Text = recovery.Summary;
            HintText.Text = recovery.State == "READY"
                ? $"화물 {recovery.CarrierId}: 출발지 {recovery.VehicleId}와 회수반송 도착 포트를 선택해 전송하세요."
                : recovery.Summary;
        });
        AllMessagesGrid.ItemsSource = _messages; WorkMessagesGrid.ItemsSource = _workMessages;
        CollectionViewSource.GetDefaultView(_messages).Filter = MatchesSelectedJob;
        CollectionViewSource.GetDefaultView(_workMessages).Filter = MatchesSelectedJob;
        _client.Logged += OnLog;
        _client.StateChanged += state => Ui(() => {
            ConnectionStateText.Text = state;
            if (!_client.Communicating) { WorkPorts.Reset(); _routeInitialized = false; RefreshCatalogControls(); }
            RefreshControls();
        });
        _client.StatusReceived += result => Ui(() => {
            if (result.Success && !result.Tables.Any(t => t.Svid is 25 or 58)) return;
            try { WorkPorts.Apply(result); }
            catch (FormatException ex) { WorkPorts.Reset(); _client.Log("ERROR", ex.Message); }
            RefreshCatalogControls(); RefreshPreview(); RefreshControls();
        });
        _client.Jobs.Changed += job => Ui(() =>
        {
            if (job.Phase == JobPhase.Completed && WorkPorts.Contains(job.Request.SourcePort) && job.Request.CarrierId == CarrierText.Text && _swapped.Add(job.Request.CommandId) && AutoSwapCheck.IsChecked == true)
            {
                SourceCombo.SelectedItem = job.Request.DestPort;
                DestCombo.SelectedItem = job.Request.SourcePort;
            }
            RefreshJobs(job.Phase == JobPhase.Sending ? job.Request.CommandId : null); RefreshControls();
            HintText.Text = $"{job.PhaseText} · {job.Request.SourcePort} → {job.Request.DestPort} · {job.Detail}";
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
            _logEntries.Insert(0, entry with { Summary = entry.Summary ?? entry.Text.Split('\n')[0].TrimEnd('\r') });
            while (_logEntries.Count > 3000) _logEntries.RemoveAt(_logEntries.Count - 1);
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
    private void AutoStatus_Changed(object sender, RoutedEventArgs e) { if (_ready) _client.AutoStatusOnConnect = AutoStatusCheck.IsChecked == true; }
    private void StatusWindow_Click(object sender, RoutedEventArgs e)
    {
        if (_statusWindow is null)
        {
            _statusWindow = new StatusWindow(_client, Path.Combine(_dataDirectory, "logs")) { Owner = this };
            _statusWindow.Closed += (_, _) => _statusWindow = null;
            _statusWindow.Show();
        }
        else { _statusWindow.Show(); _statusWindow.Activate(); }
    }
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
    private async void Forward_Click(object sender, RoutedEventArgs e) => await SendSelectedAsync();
    private async void Reverse_Click(object sender, RoutedEventArgs e) { Swap_Click(sender, e); if (WorkPorts.Contains(SourceCombo.Text)) await SendSelectedAsync(); }
    private async Task SendSelectedAsync() => await RunAsync(async () =>
    {
        await _client.QuerySelectedStatusAsync([25, 58]);
        var request = Request();
        try { await _client.SendTransferAsync(request); HintText.Text = _client.Jobs.Snapshot().Single(j => j.Request.CommandId == request.CommandId).Detail; }
        finally { _nextId = TransferRequest.NewCommandId(); RefreshPreview(); RefreshJobs(); }
    });
    private TransferRequest Request()
    {
        WorkPorts.RequireReady();
        return new(_nextId, CarrierText.Text.Trim(), SourceCombo.SelectedItem as string ?? "", DestCombo.SelectedItem as string ?? "", ushort.Parse(PriorityText.Text));
    }
    private void RefreshCatalogControls()
    {
        string? source = SourceCombo.SelectedItem as string, dest = DestCombo.SelectedItem as string, cargo = CargoPortCombo.SelectedItem as string;
        SourceCombo.ItemsSource = WorkPorts.Sources; DestCombo.ItemsSource = WorkPorts.All; CargoPortCombo.ItemsSource = WorkPorts.All;
        SourceCombo.SelectedItem = source is not null && WorkPorts.IsSource(source) ? source : !_routeInitialized && WorkPorts.Ready ? WorkPorts.All.FirstOrDefault() : null;
        DestCombo.SelectedItem = dest is not null && WorkPorts.Contains(dest) ? dest : !_routeInitialized && WorkPorts.Ready ? WorkPorts.All.FirstOrDefault(p => p != SourceCombo.SelectedItem as string) : null;
        CargoPortCombo.SelectedItem = cargo is not null && WorkPorts.Contains(cargo) ? cargo : !_routeInitialized && WorkPorts.Ready ? WorkPorts.All.FirstOrDefault() : null;
        _routeInitialized |= WorkPorts.Ready;
        CatalogText.Text = WorkPorts.Ready
            ? $"서버 식별자 사용 · Port {WorkPorts.All.Count}개 / Vehicle {WorkPorts.Vehicles.Count}대 · 표시값 그대로 전송"
            : "AGV(SV25)·포트(SV58) 조회 전 또는 실패 · 운송 전송 차단";
    }
    private async void CatalogRefresh_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        await _client.QuerySelectedStatusAsync([25, 58]);
        HintText.Text = "서버의 VehicleID·PortID로 선택 목록을 갱신했습니다.";
    });
    private void RefreshPreview()
    {
        if (!_ready) return;
        CommandIdText.Text = _nextId;
        try { using var message = Protocol.Transfer(Request()); PreviewText.Text = Protocol.Sml(message); }
        catch (Exception ex) { PreviewText.Text = ex.Message; }
    }
    private void Route_Changed(object sender, RoutedEventArgs e) { if (_ready) { RefreshPreview(); RefreshControls(); } }
    private void Swap_Click(object sender, RoutedEventArgs e)
    {
        string source = SourceCombo.Text.Trim();
        if (!WorkPorts.Contains(source)) { HintText.Text = "AMR 출발 회수 명령은 From/To를 교환할 수 없습니다."; return; }
        var destination = DestCombo.SelectedItem; SourceCombo.SelectedItem = destination; DestCombo.SelectedItem = source;
    }
    private void RefreshControls()
    {
        if (!_ready) return;
        ConnectButton.IsEnabled = !_busy && !_connected;
        CatalogRefreshButton.IsEnabled = !_busy && _client.Communicating;
        DisconnectButton.IsEnabled = !_busy && _connected;
        EstablishButton.IsEnabled = !_busy && _client.Selected;
        OnlineButton.IsEnabled = !_busy && _client.Communicating;
        bool canSend = WorkPorts.Ready && !_busy && _client.Selected && _client.Communicating && !_client.Jobs.Snapshot().Any(j => j.IsPending && j.Request.CarrierId == CarrierText.Text.Trim());
        if (canSend) try { var request = Request(); request.Validate(); canSend = _client.Cargo?.BlocksTransfer(request) != true; } catch (Exception) { canSend = false; }
        TransferButton.IsEnabled = ForwardButton.IsEnabled = ReverseButton.IsEnabled = canSend;
        IpText.IsEnabled = PortText.IsEnabled = DeviceText.IsEnabled = ModeCombo.IsEnabled = TimeoutText.IsEnabled = !_connected && !_busy;
        ReleaseButton.IsEnabled = !_busy && _client.Jobs.Snapshot().Any(j => j.IsPending);
        bool controlReady = !_busy && _client.Selected && _client.Communicating;
        var selected = JobsGrid.SelectedItem as JobRow;
        var selectedJob = _client.Jobs.Snapshot().FirstOrDefault(j => j.Request.CommandId == selected?.CommandId);
        CancelButton.IsEnabled = AbortButton.IsEnabled = controlReady && LogTabs.SelectedItem != CargoTab && selectedJob is { IsPending: true, PendingControl.Length: 0 } && selectedJob.Phase != JobPhase.Uncertain;
        PauseButton.IsEnabled = ResumeButton.IsEnabled = controlReady;
        CargoQueryButton.IsEnabled = CargoVerifyButton.IsEnabled = controlReady;
        CargoInButton.IsEnabled = CargoOutButton.IsEnabled = CargoScanButton.IsEnabled = controlReady && WorkPorts.Ready;
    }
    internal sealed record JobRow(string Time, string CommandId, string Route, string State, string Ceid, string Result, string Detail, string VehicleId, string CarrierId);
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
        _logWindow?.SetSelectedJob((JobsGrid.SelectedItem as JobRow)?.CommandId);
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
    internal sealed record TimelineRow(string Time, string State, string Ceid, string Detail, TransferJob Step);
    private void UpdateJobDetails()
    {
        if (JobsGrid.SelectedItem is not JobRow row) { JobDetailsText.Text = "작업 이력에서 행을 선택하세요."; TimelineGrid.ItemsSource = null; return; }
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
        TimelineGrid.ItemsSource = history.Select(h => new TimelineRow(h.UpdatedAt?.ToString("HH:mm:ss.fff") ?? (h.Phase == JobPhase.Sending ? h.CreatedAt.ToString("HH:mm:ss.fff") : "이전 기록 · 시각 없음"), h.PhaseText, h.LastCeid?.ToString() ?? "—", h.Detail, h)).ToArray();
    }
    private async void Timeline_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ChangedButton == System.Windows.Input.MouseButton.Left && sender is DataGrid grid && e.OriginalSource is DependencyObject element
            && ItemsControl.ContainerFromElement(grid, element) is DataGridRow { Item: TimelineRow row })
            await NavigateTimelineAsync(row);
    }
    internal async Task NavigateTimelineAsync(TimelineRow row, bool preview = false)
    {
        preview |= _offlinePreview;
        int navigation = ++_timelineNavigation;
        var logs = OpenLogWindow(preview);
        string command = row.Step.Request.CommandId;
        int revision = logs.BeginTimelineNavigation(command, row.Time + " · " + row.State);
        var target = TimelineLogTarget.From(row.Step);
        if (target is null || target.Time is null)
        { logs.ShowNavigationMissing("이 상태 변경에 연결된 SECS 원문이나 기록 시각이 없습니다. 다른 메시지를 대신 표시하지 않습니다."); return; }
        var message = target.Find(_logEntries);
        if (message is null)
        {
            try
            {
                var saved = await Task.Run(() => MessageLogArchive.Read(Path.Combine(_dataDirectory, "logs"), command));
                if (navigation != _timelineNavigation || logs.NavigationRevision != revision || !ReferenceEquals(_logWindow, logs) || _closed) return;
                logs.LoadArchivedMessages(saved);
                message = target.Find(_logEntries.Concat(saved));
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                if (navigation == _timelineNavigation && logs.NavigationRevision == revision && ReferenceEquals(_logWindow, logs)) logs.ShowNavigationMissing("저장 로그를 읽지 못했습니다: " + ex.Message);
                return;
            }
        }
        if (navigation != _timelineNavigation || logs.NavigationRevision != revision || !ReferenceEquals(_logWindow, logs)) return;
        if (message is null) logs.ShowNavigationMissing("선택한 시각·COMMANDID·메시지 종류에 일치하는 원문이 저장 로그에 없습니다. 작업 이력만으로 원문을 복원하지 않습니다.");
        else logs.SelectTimelineMessage(message);
    }
    private void Jobs_SelectionChanged(object sender, SelectionChangedEventArgs e) { if (_ready && !_refreshingJobs) { UpdateJobDetails(); RefreshMessageFilters(); RefreshControls(); _logWindow?.SetSelectedJob((JobsGrid.SelectedItem as JobRow)?.CommandId); } }
    private void JobHistoryWindow_Click(object sender, RoutedEventArgs e) => OpenJobHistoryWindow();
    internal JobHistoryWindow OpenJobHistoryWindow(bool preview = false)
    {
        if (_jobHistoryWindow is null)
        {
            _jobHistoryWindow = new JobHistoryWindow(this, () => ShowSelectedJobLogs()) { Owner = this };
            _jobHistoryWindow.Closed += (_, _) => _jobHistoryWindow = null;
            if (preview) { _jobHistoryWindow.ShowActivated = false; _jobHistoryWindow.ShowInTaskbar = false; _jobHistoryWindow.Left = -10000; _jobHistoryWindow.WindowStartupLocation = WindowStartupLocation.Manual; }
            _jobHistoryWindow.Show();
        }
        else { if (_jobHistoryWindow.WindowState == WindowState.Minimized) _jobHistoryWindow.WindowState = WindowState.Normal; _jobHistoryWindow.Show(); if (!preview) _jobHistoryWindow.Activate(); }
        return _jobHistoryWindow;
    }
    private void ShowSelectedJobLogs(bool preview = false)
    {
        if (JobsGrid.SelectedItem is not JobRow row) { HintText.Text = "작업 이력에서 로그를 볼 작업을 선택하세요."; return; }
        var logs = OpenLogWindow(preview);
        int navigation = ++_timelineNavigation;
        int revision = logs.BeginTimelineNavigation(row.CommandId, "선택 작업 원문 조회");
        _ = LoadJobMessagesAsync(logs, row.CommandId, navigation, revision);
    }
    private async Task LoadJobMessagesAsync(LogWindow logs, string command, int navigation, int revision)
    {
        try
        {
            var saved = await Task.Run(() => MessageLogArchive.Read(Path.Combine(_dataDirectory, "logs"), command));
            if (navigation != _timelineNavigation || logs.NavigationRevision != revision || !ReferenceEquals(_logWindow, logs) || _closed) return;
            logs.LoadArchivedMessages(saved);
            var entries = _logEntries.Concat(saved).Where(e => e.CommandId == command && e.IsMessage).OrderBy(e => e.Time).ToArray();
            var first = entries.FirstOrDefault(e => e.Stream == 2 && e.Function == 49 && e.Direction == "TX") ?? entries.FirstOrDefault();
            if (first is null) logs.ShowNavigationMissing("작업 상태 이력은 있지만 현재 메모리와 저장 파일에 해당 COMMANDID의 SECS 원문이 없습니다. 조회 폴더: " + Path.Combine(_dataDirectory, "logs"));
            else logs.SelectTimelineMessage(first);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            if (navigation == _timelineNavigation && logs.NavigationRevision == revision && ReferenceEquals(_logWindow, logs))
                logs.ShowNavigationMissing("저장 로그를 읽지 못했습니다: " + ex.Message);
        }
    }
    private void Details_Click(object sender, RoutedEventArgs e) { UpdateJobDetails(); LogTabs.SelectedItem = JobDetailsTab; }
    private void Jobs_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e) { if (JobsGrid.SelectedItem is JobRow) Details_Click(sender, e); }
    private void CopyCommandId_Click(object sender, RoutedEventArgs e) { if (JobsGrid.SelectedItem is JobRow row) Clipboard.SetText(row.CommandId); }
    private void Message_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e) { if (sender is DataGrid { SelectedItem: MessageRow row }) OpenMessage(row); }
    private void MessageWindow_Click(object sender, RoutedEventArgs e)
    {
        var row = (LogTabs.SelectedIndex == 2 ? AllMessagesGrid : WorkMessagesGrid).SelectedItem as MessageRow;
        if (row is not null) OpenMessage(row);
    }
    private void LogWindow_Click(object sender, RoutedEventArgs e) => OpenLogWindow();
    internal LogWindow OpenLogWindow(bool preview = false)
    {
        if (_logWindow is null)
        {
            int mode = LogTabs.SelectedIndex switch { 1 => 0, 4 => 2, _ => 1 };
            string command = SelectedJobOnlyCheck.IsChecked == true && JobsGrid.SelectedItem is JobRow job ? job.CommandId : "";
            _logWindow = new LogWindow(_logEntries, Path.Combine(_dataDirectory, "logs"), mode, command) { Owner = this };
            _logWindow.SetSelectedJob((JobsGrid.SelectedItem as JobRow)?.CommandId);
            _logWindow.Closed += (_, _) => _logWindow = null;
            if (preview) { _logWindow.ShowActivated = false; _logWindow.ShowInTaskbar = false; _logWindow.Left = -10000; _logWindow.WindowStartupLocation = WindowStartupLocation.Manual; }
            _logWindow.Show();
        }
        else { if (_logWindow.WindowState == WindowState.Minimized) _logWindow.WindowState = WindowState.Normal; _logWindow.Show(); if (!preview) _logWindow.Activate(); }
        return _logWindow;
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
        _messages.Clear(); _workMessages.Clear(); _logEntries.Clear(); LogText.Clear(); MessageCountText.Text = "작업 0 / 전체 0건";
    }
    internal LogWindow VerifyLogWindowPreview()
    {
        var first = OpenLogWindow(true);
        if (!ReferenceEquals(first, OpenLogWindow(true))) throw new InvalidOperationException("Log window button created a duplicate window");
        first.VerifyPreview(OnLog);
        int count = _messages.Count;
        first.Close();
        OnLog(new(DateTimeOffset.Now, "INFO", "오프라인 검증: 로그 창을 닫은 뒤에도 메인 로그 수신 유지"));
        if (_messages.Count != count) throw new InvalidOperationException("System log changed the SECS message list");
        var reopened = OpenLogWindow(true);
        if (ReferenceEquals(first, reopened)) throw new InvalidOperationException("Closed log window was reused");
        reopened.ShowWorkMessages();
        return reopened;
    }
    internal JobHistoryWindow VerifyJobHistoryPreview()
    {
        var history = OpenJobHistoryWindow(true);
        if (!ReferenceEquals(history, OpenJobHistoryWindow(true)) || !ReferenceEquals(history.HistoryGrid.ItemsSource, JobsGrid.ItemsSource))
            throw new InvalidOperationException("History window did not share main rows or reused incorrectly");
        var original = (JobRow)JobsGrid.SelectedItem;
        var request = new TransferRequest("HISTORY_" + Guid.NewGuid().ToString("N")[..16], "SIM_CARGO_002", "lay01-dist1-90", "SECS_ST_97");
        _client.Jobs.Reserve(request); _client.Jobs.Acknowledge(request.CommandId, 4);
        history.HistoryGrid.SelectedItem = history.HistoryGrid.Items.Cast<JobRow>().Single(r => r.CommandId == original.CommandId);
        if (JobsGrid.SelectedItem is not JobRow chosen || chosen.CommandId != original.CommandId)
            throw new InvalidOperationException("Detached selection did not update main history");
        JobsGrid.SelectedItem = JobsGrid.Items.Cast<JobRow>().Single(r => r.CommandId == request.CommandId);
        if (history.HistoryGrid.SelectedItem is not JobRow linked || linked.CommandId != request.CommandId)
            throw new InvalidOperationException("Main selection did not update detached history");
        ShowSelectedJobLogs(true);
        if (_logWindow!.CommandFilterText.Text != request.CommandId || _logWindow.LinkSelectionCheck.IsChecked != true)
            throw new InvalidOperationException("Job-to-log link missing");
        history.HistoryGrid.SelectedItem = history.HistoryGrid.Items.Cast<JobRow>().Single(r => r.CommandId == original.CommandId);
        if (_logWindow.CommandFilterText.Text != original.CommandId || _logWindow.MessagesGrid.Items.Count == 0
            || _logWindow.MessagesGrid.Items.Cast<LogEntry>().Any(e => e.CommandId != original.CommandId))
            throw new InvalidOperationException("Log rows/filter did not follow detached selection");
        _logWindow.CommandFilterText.Text = "MANUAL_FILTER";
        JobsGrid.SelectedItem = JobsGrid.Items.Cast<JobRow>().Single(r => r.CommandId == request.CommandId);
        if (_logWindow.CommandFilterText.Text != "MANUAL_FILTER" || _logWindow.LinkSelectionCheck.IsChecked == true)
            throw new InvalidOperationException("Manual log filter was overwritten by selection synchronization");
        ShowSelectedJobLogs(true);
        _client.Jobs.Report(new(208, request.CommandId, request.CarrierId, request.SourcePort, request.DestPort, request.SourcePort, 0));
        _client.Jobs.ReportVehicle(new(604, "AGV002", request.CommandId, request.SourcePort, request.CarrierId));
        _client.Jobs.Report(new(207, request.CommandId, request.CarrierId, request.SourcePort, request.DestPort, request.DestPort, 0));
        if (history.HistoryGrid.SelectedItem is not JobRow completed || completed.CommandId != request.CommandId || completed.Ceid != "207" || completed.VehicleId != "AGV002"
            || history.DetailsText.Text != JobDetailsText.Text || !history.DetailsText.Text.Contains(request.CommandId)
            || !ReferenceEquals(history.HistoryTimelineGrid.ItemsSource, TimelineGrid.ItemsSource) || history.PendingText.Text != PendingText.Text
            || _logWindow.CommandFilterText.Text != request.CommandId)
            throw new InvalidOperationException("Status refresh lost synchronized selection/details/timeline/log filter");
        history.Close();
        if (JobsGrid.SelectedItem is not JobRow retained || retained.CommandId != request.CommandId)
            throw new InvalidOperationException("Closing detached history cleared main selection");
        var reopened = OpenJobHistoryWindow(true);
        if (ReferenceEquals(reopened, history) || reopened.HistoryGrid.SelectedItem is not JobRow restored || restored.CommandId != request.CommandId
            || !ReferenceEquals(reopened.HistoryGrid.SelectedItem, JobsGrid.SelectedItem) || !reopened.DetailsText.Text.Contains(request.CommandId))
            throw new InvalidOperationException("Reopened history lost selection");
        HintText.Text = "오프라인 화면 검증 · 실제 Fleet 연결/운송 없음";
        return reopened;
    }
    internal async Task<LogWindow> VerifyTimelineLogsPreviewAsync()
    {
        var request = new TransferRequest("TIMELINE_" + Guid.NewGuid().ToString("N")[..16], "SIM_CARGO_003", "SECS_ST_89", "SECS_ST_97");
        _client.Jobs.Reserve(request);
        var sent = _client.Jobs.History(request.CommandId).Single();
        using var primary = Protocol.Transfer(request);
        OnLog(new(sent.CreatedAt, "TX", "SB=0x00001234\n" + Protocol.Sml(primary), 2, 49, 0x1234, request.CommandId, Summary: "운송 요청 · 오프라인 클릭 검증"));
        _client.Jobs.Acknowledge(request.CommandId, 4);
        var accepted = _client.Jobs.History(request.CommandId).Last();
        using var ack = new Secs4Net.SecsMessage(2, 50, false) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.B(4), Secs4Net.Item.L()) };
        OnLog(new(accepted.UpdatedAt!.Value, "RX", "SB=0x00001234\n" + Protocol.Sml(ack), 2, 50, 0x1234, request.CommandId, Summary: "운송 응답 · 오프라인 클릭 검증"));
        _client.Jobs.Report(new(207, request.CommandId, request.CarrierId, request.SourcePort, request.DestPort, request.DestPort, 0));
        var row = TimelineGrid.Items.Cast<TimelineRow>().First(r => r.Step.Request.CommandId == request.CommandId);
        await NavigateTimelineAsync(row, true);
        if (_logWindow!.MessagesGrid.SelectedItem is not LogEntry { Function: 49 } || _logWindow.FollowCheck.IsChecked != false)
            throw new InvalidOperationException("Timeline click did not select/freeze the original request");
        _logEntries.Clear(); // Simulate an application restart: only saved disk logs remain.
        ShowSelectedJobLogs(true);
        for (int i = 0; i < 100 && _logWindow.MessagesGrid.SelectedItem is not LogEntry { Function: 49 }; i++) await Task.Delay(20);
        if (_logWindow.MessagesGrid.SelectedItem is not LogEntry { Function: 49, SourceFile: not null }
            || !_logWindow.MessageBody.Text.Contains("원문 출처:"))
            throw new InvalidOperationException("Selected-job log button did not load archived raw data/source filename");
        _logEntries.Clear();
        var history = OpenJobHistoryWindow(true);
        var responseRow = history.HistoryTimelineGrid.Items.Cast<TimelineRow>().Single(r => r.Step.Phase == JobPhase.Acknowledged);
        // Exercise the actual row mouse handler used by the user, not just the navigation helper.
        history.HistoryTimelineGrid.ScrollIntoView(responseRow); history.HistoryTimelineGrid.UpdateLayout();
        if (history.HistoryTimelineGrid.ItemContainerGenerator.ContainerFromItem(responseRow) is not DataGridRow visualRow)
            throw new InvalidOperationException("Timeline preview row not realized");
        visualRow.RaiseEvent(new System.Windows.Input.MouseButtonEventArgs(System.Windows.Input.Mouse.PrimaryDevice, Environment.TickCount, System.Windows.Input.MouseButton.Left)
        { RoutedEvent = System.Windows.Input.Mouse.MouseUpEvent });
        for (int i = 0; i < 100 && _logWindow.MessagesGrid.SelectedItem is not LogEntry { Function: 50 }; i++) await Task.Delay(20);
        if (_logWindow.MessagesGrid.SelectedItem is not LogEntry response || response.Function != 50 || response.CommandId != request.CommandId
            || !_logWindow.MessageBody.Text.Contains("S2F50") || !_logWindow.NavigationText.Text.Contains("원문 선택 완료"))
            throw new InvalidOperationException("Clicked history row did not reload/select archived S2F50 after restart: " + _logWindow.NavigationText.Text + " / " + _logWindow.MessageBody.Text);
        var missing = responseRow with { Step = responseRow.Step with { UpdatedAt = accepted.UpdatedAt.Value.AddHours(1) } };
        await NavigateTimelineAsync(missing, true);
        if (_logWindow.MessagesGrid.SelectedItem is not null || !_logWindow.MessageBody.Text.Contains("원문이 저장 로그에 없습니다"))
            throw new InvalidOperationException("Missing timeline raw data displayed an unrelated message");
        await NavigateTimelineAsync(row, true);
        if (_logWindow.MessagesGrid.SelectedItem is not LogEntry { Function: 49 } || !_logWindow.MessageBody.Text.Contains(request.CommandId))
            throw new InvalidOperationException("Re-selecting archived request did not recover its original SML");
        return _logWindow;
    }
    internal void VerifyCatalogPreview()
    {
        if (!WorkPorts.Ready || !SourceCombo.Items.Cast<string>().SequenceEqual(WorkPorts.Sources)
            || !DestCombo.Items.Cast<string>().SequenceEqual(WorkPorts.All)) throw new InvalidOperationException("SECS catalog dropdown mismatch");
        string? source = SourceCombo.SelectedItem as string;
        SourceCombo.SelectedItem = "AGV002";
        if (Request().SourcePort != "AGV002" || !PreviewText.Text.Contains("AGV002") || PreviewText.Text.Contains("AMR_AGV002"))
            throw new InvalidOperationException("VehicleID changed on recovery request wire");
        SourceCombo.SelectedItem = source;
    }
    internal void LoadPreviewFixture()
    {
        // Explicit offline render mode only. Uses an isolated profile; never connects.
        WorkPorts.Install(["lay01-dist1-84", "lay01-dist1-86", "SECS_ST_89", "lay01-dist1-90", "lay01-dist1-93", "lay01-dist1-94", "lay01-dist1-95", "lay01-dist1-96", "SECS_ST_97", "lay01-dist1-98"], ["AGV001", "AGV002", "AGV003"]);
        RefreshCatalogControls();
        ConnectionStateText.Text = "OFFLINE PREVIEW";
        var request = new TransferRequest("PREVIEW_" + Guid.NewGuid().ToString("N")[..16], "SIM_CARGO_001", "SECS_ST_97", "SECS_ST_89");
        _client.Jobs.Reserve(request); _client.Jobs.Acknowledge(request.CommandId, 0);
        using var primary = Protocol.Transfer(request);
        OnLog(new(DateTimeOffset.Now, "TX", Protocol.Sml(primary), 2, 49, 1, request.CommandId, null, "운송 요청"));
        foreach (var (ceid,arrivalPort) in new (uint,string?)[] {(208,null),(604,null),(601,request.SourcePort),(211,null),(602,null),(301,null),(603,null),
            (605,null),(601,request.DestPort),(606,null),(302,null),(607,null),(610,null),(207,null)})
        {
            if (ceid is 301 or 302)
            {
                string carrierLocation = ceid == 301 ? "AGV002" : request.DestPort;
                var carrier = new CarrierReport(ceid, "AGV002", request.CarrierId, carrierLocation, request.CommandId);
                _client.Jobs.ReportCarrier(carrier);
                using var carrierMessage = new Secs4Net.SecsMessage(6, 11, true) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.U4(0), Secs4Net.Item.U2((ushort)ceid),
                    Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.U2(3), Secs4Net.Item.L(Secs4Net.Item.A("AGV002"), Secs4Net.Item.A(request.CarrierId), Secs4Net.Item.A(carrierLocation), Secs4Net.Item.A(request.CommandId))))) };
                OnLog(new(DateTimeOffset.Now, "RX", Protocol.Sml(carrierMessage), 6, 11, (int)ceid, request.CommandId, ceid, carrier.EventText + " 보고"));
                continue;
            }
            if (ceid >= 600)
            {
                string port = arrivalPort ?? (ceid < 606 ? request.SourcePort : request.DestPort);
                var vehicle = new VehicleReport(ceid, "AGV002", request.CommandId, port, request.CarrierId);
                _client.Jobs.ReportVehicle(vehicle);
                using var stage = new Secs4Net.SecsMessage(6, 11, true) { SecsItem = Secs4Net.Item.L(Secs4Net.Item.U4(0), Secs4Net.Item.U2((ushort)ceid),
                    Secs4Net.Item.L(Secs4Net.Item.L(Secs4Net.Item.U2(5), Secs4Net.Item.L(Secs4Net.Item.A("AGV002"), Secs4Net.Item.A(request.CommandId), Secs4Net.Item.L(Secs4Net.Item.A(port)), Secs4Net.Item.L(Secs4Net.Item.A(request.CarrierId)))))) };
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
        InventoryGrid.ItemsSource = new[] { new CargoItem("SIM_CARGO_001", "SECS_ST_97", "SECS_ST_97", "SIM_TRAY_001", 1, 0),
            new CargoItem("SIM_CARGO_002", "lay01-dist1-84", "lay01-dist1-84", "SIM_TRAY_001", 1, 0), new CargoItem("SCAN_NEW_004", "", "", "SIM_TRAY_001", 0, 0) };
        var incoming = new CargoRequest("PREVIEW_IN_" + Guid.NewGuid().ToString("N")[..16], "CARRIER_IN", "SCAN_NEW_004", "lay01-dist1-94", "SIM_TRAY_001", "SCAN_NEW_004");
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
