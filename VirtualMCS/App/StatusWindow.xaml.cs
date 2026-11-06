using System.Collections.ObjectModel;
using System.Data;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using KukaMcs.Core;

namespace KukaMcs.App;

public partial class StatusWindow : Window
{
    private readonly McsClient _client;
    private readonly string _logDirectory;
    private readonly ObservableCollection<StatusQueryResult> _rows = [];
    private readonly List<double> _latencies = [];
    private int _failures;
    private CancellationTokenSource? _run;
    private bool _closed;
    private bool _sendingUpdate;
    private string? _updateCommandId;
    public StatusWindow(McsClient client, string logDirectory)
    {
        InitializeComponent(); _client = client; _logDirectory = logDirectory;
        ResultsGrid.ItemsSource = _rows;
        foreach (var result in client.RecentStatusResults) Apply(result);
        client.StatusReceived += Receive; client.StateChanged += ConnectionChanged; client.UpdateReported += ReceiveUpdate; client.CommandRejected += ReceiveRejection;
        Closed += (_, _) => { _closed = true; _run?.Cancel(); client.StatusReceived -= Receive; client.StateChanged -= ConnectionChanged; client.UpdateReported -= ReceiveUpdate; client.CommandRejected -= ReceiveRejection; };
        Refresh();
    }
    private void OnUi(Action action)
    { if (!_closed && !Dispatcher.HasShutdownStarted) { if (Dispatcher.CheckAccess()) action(); else _ = Dispatcher.BeginInvoke(action); } }
    private void Receive(StatusQueryResult result) => OnUi(() => Apply(result));
    private void ConnectionChanged(string state) => OnUi(() =>
    {
        if (!_client.Communicating) { _run?.Cancel(); Invalidate("연결 미확립 · 이전 목록 사용 금지"); }
        Refresh();
    });
    private void Invalidate(string text)
    {
        foreach (var label in new[] { AgvTime, PortTime, TransferTime, CarrierTime }) label.Text = text;
        foreach (var grid in new[] { AgvGrid, PortGrid, TransferGrid, CarrierGrid }) grid.ItemsSource = null;
    }
    private void Apply(StatusQueryResult result)
    {
        _rows.Insert(0, result); while (_rows.Count > 500) _rows.RemoveAt(_rows.Count - 1);
        if (result.Success) _latencies.Add(result.ElapsedMs); else { _failures++; Invalidate("조회 실패 · 상태 미확인 · 이전 목록 사용 금지"); }
        foreach (var table in result.Tables)
        {
            if (table.Svid == 58)
            {
                string destination = UpdateDestinationText.Text;
                UpdateDestinationText.ItemsSource = table.Rows.Select(row => row[0]).ToArray();
                UpdateDestinationText.Text = destination;
            }
            (DataGrid? grid, TextBlock? label) = table.Svid switch { 25 => (AgvGrid, AgvTime), 58 => (PortGrid, PortTime), 23 => (TransferGrid, TransferTime), 21 => (CarrierGrid, CarrierTime), _ => (null, null) };
            if (grid is null || label is null) continue;
            var data = new DataTable(); foreach (string column in table.Columns) data.Columns.Add(column);
            foreach (string[] row in table.Rows) data.Rows.Add(row.Cast<object>().ToArray());
            string? selectedId = table.Svid == 23 ? SelectedTransfer?[0] as string : null;
            grid.ItemsSource = data.DefaultView;
            if (selectedId is not null) grid.SelectedItem = data.DefaultView.Cast<DataRowView>().FirstOrDefault(r => (string)r[0] == selectedId);
            if (table.Svid == 23 && grid.SelectedItem is null && data.Rows.Count == 1) grid.SelectedItem = data.DefaultView[0];
            label.Text = $"SV{table.Svid} {SelectedStatusProtocol.VariableName(table.Svid)} · 수신 {result.TimeText} · {table.EntryCount}건 · {result.ElapsedText} · 현재 연결의 조회 시점 상태";
            if (table.Svid == 23 && table.EntryCount == 0) label.Text += " · 현재 운송 없음 (CommandID를 생성하지 않음)";
        }
        if (!_client.Communicating) Invalidate("연결 미확립 · 이전 목록 사용 금지");
        ResultsGrid.SelectedItem = result; ResultsGrid.ScrollIntoView(result);
        double[] sorted = _latencies.Order().ToArray();
        StatsText.Text = $"정상 {_latencies.Count} / 실패 {_failures}" + (sorted.Length == 0 ? "" : $" · P95 {sorted[(int)(.95 * (sorted.Length - 1))]:F1} ms / 최대 {sorted[^1]:F1} ms");
        if (_run is null) ProgressText.Text = $"{result.ResultText} · SVID {result.RequestIds} · {result.Summary}";
        Refresh();
    }
    private void Refresh()
    {
        StateText.Text = _client.Communicating ? "COMMUNICATING" : "DISCONNECTED / 통신 미확립";
        bool ready = _client.Communicating && _run is null && !_sendingUpdate;
        foreach (var button in new[] { AgvButton, PortButton, TransferButton, CarrierButton, CombinedButton, AllButton, StartupButton, BurstButton, DestinationPortsButton }) button.IsEnabled = ready;
        RepeatText.IsEnabled = _run is null; StopButton.IsEnabled = _run is not null;
        RefreshUpdate();
    }

    private DataRowView? SelectedTransfer => TransferGrid.SelectedItem as DataRowView;
    private void TransferSelection_Changed(object s, SelectionChangedEventArgs e) => RefreshUpdate();
    private void RefreshUpdate()
    {
        if (UpdateButton is null || DestinationButton is null || TransferGrid is null || _client is null) return;
        var row = SelectedTransfer;
        string? id = row?[0] as string;
        bool pending = id is not null && _client.IsUpdatePending(id);
        bool ready = _client.Communicating && _run is null && !_sendingUpdate && !pending && !string.IsNullOrEmpty(id);
        UpdateButton.IsEnabled = ready && (row?[2] as string) == "1 · 대기열";
        DestinationButton.IsEnabled = ready && (row?[2] as string) is "1 · 대기열" or "2 · 운송 중";
        UpdatePriorityText.IsEnabled = !_sendingUpdate;
        UpdateDestinationText.IsEnabled = !_sendingUpdate;
        UpdateTargetText.Text = id is null ? "위 작업 목록에서 수정할 행을 선택하세요." : $"{id} · {row![2]} · {row[4]} → {row[5]} · 우선순위 {row[1]}" + (pending ? " · 결과 대기" : "");
    }

    private async void PriorityUpdate_Click(object s, RoutedEventArgs e) => await SendUpdateAsync(false);
    private async void DestinationUpdate_Click(object s, RoutedEventArgs e) => await SendUpdateAsync(true);
    private async void DestinationPorts_Click(object s, RoutedEventArgs e) => await Query(23, 58);

    private async Task SendUpdateAsync(bool changeDestination)
    {
        if (!(changeDestination ? DestinationButton.IsEnabled : UpdateButton.IsEnabled) || SelectedTransfer is not { } row) return;
        string destination = UpdateDestinationText.Text.Trim();
        ushort priority = 0;
        if (!changeDestination && (!ushort.TryParse(UpdatePriorityText.Text, out priority) || priority is < 1 or > 99))
        {
            UpdateAckText.Text = "새 우선순위를 1~99로 입력하세요.";
            return;
        }

        string id = (string)row[0];
        _sendingUpdate = true;
        Refresh();
        try
        {
            var status = await _client.QuerySelectedStatusAsync(changeDestination ? [23, 58] : [23]);
            var current = status.Tables.Single(t => t.Svid == 23).Rows.FirstOrDefault(r => r[0] == id);
            if (current is null || current[2] != (changeDestination ? "2 · 운송 중" : "1 · 대기열"))
            {
                UpdateAckText.Text = changeDestination
                    ? current is null ? "선택한 작업이 현재 목록에 없습니다. 완료 여부를 확인하세요."
                        : $"목적지 UPDATE 전송 보류 · 현재 {current[2]}. 적재 완료 후 주행 중에 다시 요청하세요."
                    : "선택한 작업이 현재 배차 대기열에 없습니다.";
                return;
            }
            if (changeDestination)
            {
                var ports = status.Tables.Single(t => t.Svid == 58).Rows;
                if (destination == current[4] || destination == current[5] || !ports.Any(p => p[0] == destination))
                {
                    UpdateAckText.Text = "출발지와 현재 목적지를 제외한 SV58 PortID를 입력하세요.";
                    return;
                }
                if (!ushort.TryParse(current[1], out priority) || priority is < 1 or > 99)
                {
                    UpdateAckText.Text = "현재 작업의 우선순위를 확인할 수 없습니다.";
                    return;
                }
            }
            else if (ushort.TryParse(current[1], out ushort oldPriority) && oldPriority == priority)
            {
                UpdateAckText.Text = "현재 우선순위와 다른 값을 입력하세요.";
                return;
            }
            if (_closed) return;
            _updateCommandId = id;
            UpdateReportText.Text = "최종 보고 대기 · CEID 212 또는 213";
            string change = changeDestination ? $"목적지 {current[5]} → {destination}" : $"우선순위 {current[1]} → {priority}";
            UpdateAckText.Text = $"{id} · {change} · S2F41 전송";
            var ack = changeDestination ? await _client.SendDestinationUpdateAsync(id, priority, destination)
                : await _client.SendPriorityUpdateAsync(id, priority);
            UpdateAckText.Text = ack.Accepted ? $"{id} · {change} · S2F42 HCACK={ack.Code} · 최종 결과는 212/213에서 확인" : $"{id} · {change} · S2F42 · {ack.Summary}";
            if (!_client.IsUpdatePending(id) && !ack.Accepted && UpdateReportText.Text.StartsWith("최종 보고 대기", StringComparison.Ordinal))
                UpdateReportText.Text = "UPDATE 거부 응답 수신";
        }
        catch (Exception ex)
        {
            UpdateAckText.Text = (_client.IsUpdatePending(id) ? "UPDATE 응답 미확인 · 결과 확인 후 진행하세요. " : "UPDATE 시험 중단 · ") + ex.Message;
        }
        finally { _sendingUpdate = false; if (!_closed) Refresh(); }
    }

    private void ReceiveUpdate(TransferReport report) => OnUi(() =>
    {
        if (_updateCommandId == report.CommandId)
            UpdateReportText.Text = $"{report.CommandId} · CEID {report.Ceid} · {Protocol.EventText(report.Ceid)} · DESTPORT {report.DestPort}";
        RefreshUpdate();
    });
    private void ReceiveRejection(CommandRejection report) => OnUi(() =>
    {
        if (report.Command == "UPDATE" && _updateCommandId == report.CommandId)
            UpdateReportText.Text = report.Summary;
    });
    private async Task Run(Func<CancellationToken, Task> action)
    {
        if (_run is not null) return;
        using var run = new CancellationTokenSource(); _run = run; Refresh();
        _latencies.Clear(); _failures = 0; StatsText.Text = "현재 시험: 수신 0 / 실패 0";
        try { await action(run.Token); ProgressText.Text = $"시험 완료 · 정상 {_latencies.Count} / 실패 {_failures} · 운송 발행 없음"; }
        catch (OperationCanceledException) { ProgressText.Text = "시험 중지 · 진행 중 요청의 늦은 응답은 원문 로그에 남을 수 있습니다."; }
        catch (Exception ex) { ProgressText.Text = "시험 실패 · " + ex.Message + " · 빈 작업으로 판단하지 마세요."; }
        finally { _run = null; if (!_closed) Refresh(); }
    }
    private Task Query(params ushort[] ids) => Run(token => _client.QuerySelectedStatusAsync(ids, token));
    private async void Agv_Click(object s, RoutedEventArgs e) => await Query(25);
    private async void Port_Click(object s, RoutedEventArgs e) => await Query(58);
    private async void Transfer_Click(object s, RoutedEventArgs e) { SnapshotTabs.SelectedIndex = 2; await Query(23); }
    private async void Carrier_Click(object s, RoutedEventArgs e) => await Query(21);
    private async void Combined_Click(object s, RoutedEventArgs e) => await Query(23, 25, 58, 21);
    private async void All_Click(object s, RoutedEventArgs e) => await Query();
    private async void Startup_Click(object s, RoutedEventArgs e) => await Run(token => _client.QueryStartupStatusAsync(token));
    private async void Burst_Click(object s, RoutedEventArgs e)
    {
        if (!int.TryParse(RepeatText.Text, out int count) || count is < 1 or > 1000) { ProgressText.Text = "반복 묶음 수는 1~1000입니다."; return; }
        await Run(async token => { for (int i = 0; i < count; i++) { token.ThrowIfCancellationRequested(); ProgressText.Text = $"연속 요청 시험 {i + 1}/{count} 묶음 · 최대 {count * 3}개 응답"; await _client.QueryStartupStatusAsync(token); } });
    }
    private void Stop_Click(object s, RoutedEventArgs e) => _run?.Cancel();
    private void Result_Changed(object s, SelectionChangedEventArgs e) { if (ResultsGrid.SelectedItem is StatusQueryResult result) RawText.Text = $"{result.TimeText} · SVID {result.RequestIds} · {result.ResultText} · {result.ElapsedText}\n{result.Summary}\n\n{result.Raw}"; }
    private void Logs_Click(object s, RoutedEventArgs e) => Process.Start(new ProcessStartInfo(_logDirectory) { UseShellExecute = true });
    private void Copy_Click(object s, RoutedEventArgs e) { if (RawText.Text.Length > 0) Clipboard.SetText(RawText.Text); }
}
