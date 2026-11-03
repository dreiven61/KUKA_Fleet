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
    public StatusWindow(McsClient client, string logDirectory)
    {
        InitializeComponent(); _client = client; _logDirectory = logDirectory;
        ResultsGrid.ItemsSource = _rows;
        foreach (var result in client.RecentStatusResults) Apply(result);
        client.StatusReceived += Receive; client.StateChanged += ConnectionChanged;
        Closed += (_, _) => { _closed = true; _run?.Cancel(); client.StatusReceived -= Receive; client.StateChanged -= ConnectionChanged; };
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
            (DataGrid? grid, TextBlock? label) = table.Svid switch { 25 => (AgvGrid, AgvTime), 58 => (PortGrid, PortTime), 23 => (TransferGrid, TransferTime), 21 => (CarrierGrid, CarrierTime), _ => (null, null) };
            if (grid is null || label is null) continue;
            var data = new DataTable(); foreach (string column in table.Columns) data.Columns.Add(column);
            foreach (string[] row in table.Rows) data.Rows.Add(row.Cast<object>().ToArray());
            grid.ItemsSource = data.DefaultView;
            label.Text = $"SV{table.Svid} {SelectedStatusProtocol.VariableName(table.Svid)} · 수신 {result.TimeText} · {table.EntryCount}건 · {result.ElapsedText} · 현재 연결의 조회 시점 상태";
            if (table.Svid == 23 && table.EntryCount == 0) label.Text += " · 현재 운송 없음 (CommandID를 생성하지 않음)";
        }
        if (!_client.Communicating) Invalidate("연결 미확립 · 이전 목록 사용 금지");
        ResultsGrid.SelectedItem = result; ResultsGrid.ScrollIntoView(result);
        double[] sorted = _latencies.Order().ToArray();
        StatsText.Text = $"정상 {_latencies.Count} / 실패 {_failures}" + (sorted.Length == 0 ? "" : $" · P95 {sorted[(int)(.95 * (sorted.Length - 1))]:F1} ms / 최대 {sorted[^1]:F1} ms");
        if (_run is null) ProgressText.Text = $"{result.ResultText} · SVID {result.RequestIds} · {result.Summary}";
    }
    private void Refresh()
    {
        StateText.Text = _client.Communicating ? "COMMUNICATING" : "DISCONNECTED / 통신 미확립";
        bool ready = _client.Communicating && _run is null;
        foreach (var button in new[] { AgvButton, PortButton, TransferButton, CarrierButton, CombinedButton, AllButton, StartupButton, BurstButton }) button.IsEnabled = ready;
        RepeatText.IsEnabled = _run is null; StopButton.IsEnabled = _run is not null;
    }
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
    private async void Transfer_Click(object s, RoutedEventArgs e) => await Query(23);
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
