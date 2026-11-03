using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using KukaMcs.Core;

namespace KukaMcs.App;

public partial class LogWindow : Window
{
    private readonly ObservableCollection<LogEntry> _sourceEntries;
    private readonly ObservableCollection<LogEntry> _entries;
    private readonly HashSet<string> _archiveKeys = [];
    private readonly ICollectionView _view;
    private readonly string _logDirectory;
    private bool _ready;
    private bool _settingLinkedCommand;
    private string? _selectedCommand;
    internal int NavigationRevision { get; private set; }
    public LogWindow(ObservableCollection<LogEntry> entries, string logDirectory, int mode = 0, string commandId = "")
    {
        InitializeComponent(); _sourceEntries = entries; _entries = new(entries); _logDirectory = logDirectory;
        // A separate view keeps this window's filter/selection independent of the main window.
        _view = new CollectionViewSource { Source = _entries }.View;
        _view.Filter = Matches;
        ModeCombo.SelectedIndex = mode; CommandFilterText.Text = commandId;
        MessagesGrid.ItemsSource = _view;
        _sourceEntries.CollectionChanged += EntriesChanged;
        Closed += (_, _) => { _ready = false; _sourceEntries.CollectionChanged -= EntriesChanged; _view.Filter = null; MessagesGrid.ItemsSource = null; };
        _ready = true; Refresh();
    }
    private bool Matches(object item)
    {
        if (item is not LogEntry entry) return false;
        if (LinkSelectionCheck.IsChecked == true && string.IsNullOrEmpty(_selectedCommand)) return false;
        bool category = ModeCombo.SelectedIndex switch { 0 => entry.IsWorkMessage, 1 => entry.IsMessage, 2 => !entry.IsMessage, _ => true };
        return category && (entry.CommandId ?? "").Contains(CommandFilterText.Text.Trim(), StringComparison.OrdinalIgnoreCase);
    }
    private void Refresh()
    {
        _view.Refresh();
        FollowNewest(); UpdateCount();
    }
    private void FollowNewest()
    {
        if (FollowCheck.IsChecked != true || _view.Cast<LogEntry>().FirstOrDefault() is not LogEntry entry) return;
        MessagesGrid.SelectedItem = entry; MessagesGrid.ScrollIntoView(entry);
    }
    private void UpdateCount() => CountText.Text = $"표시 {_view.Cast<LogEntry>().Count()} / 보관 {_entries.Count}건 · 최근 실시간 3000건 + 선택 작업 저장 로그 · 창을 닫아도 MCS 통신은 유지됩니다.";
    private void EntriesChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (!_ready) return;
        if (e.Action == NotifyCollectionChangedAction.Reset) { _entries.Clear(); _archiveKeys.Clear(); }
        if (e.OldItems is not null) foreach (LogEntry old in e.OldItems)
        {
            var match = _entries.FirstOrDefault(entry => Key(entry) == Key(old));
            if (match is not null && !_archiveKeys.Contains(Key(old))) _entries.Remove(match);
        }
        if (e.NewItems is not null) foreach (LogEntry added in e.NewItems)
        { if (!_entries.Any(entry => Key(entry) == Key(added))) _entries.Insert(0, added); }
        FollowNewest(); UpdateCount();
    }
    private void Filter_Changed(object sender, RoutedEventArgs e)
    {
        if (!_ready) return;
        if (!_settingLinkedCommand) NavigationRevision++;
        if (ReferenceEquals(sender, CommandFilterText) && !_settingLinkedCommand) LinkSelectionCheck.IsChecked = false;
        Refresh();
    }
    internal void SetSelectedJob(string? commandId, bool enableLink = false)
    {
        if (_selectedCommand != commandId && LinkSelectionCheck.IsChecked == true) NavigationRevision++;
        _selectedCommand = commandId;
        if (enableLink) LinkSelectionCheck.IsChecked = true;
        if (LinkSelectionCheck.IsChecked != true) return;
        _settingLinkedCommand = true;
        try { CommandFilterText.Text = commandId ?? ""; }
        finally { _settingLinkedCommand = false; }
        Refresh();
    }
    private void LinkSelection_Changed(object sender, RoutedEventArgs e)
    { if (_ready) { if (LinkSelectionCheck.IsChecked == true) SetSelectedJob(_selectedCommand); else Refresh(); } }
    private void Message_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        MessageBody.Text = MessagesGrid.SelectedItem is LogEntry entry ? Format(entry) : "메시지를 선택하세요.";
    }
    private static string Format(LogEntry entry)
    {
        string systemBytes = entry.SystemBytes is int id ? $"0x{id:X8}" : "";
        string source = entry.SourceFile is null ? "현재 실행의 송수신 기록" : entry.SourceFile;
        return $"{entry.Time:yyyy-MM-dd HH:mm:ss.fff}  {entry.Direction}  {entry.MessageType}  {systemBytes}\r\n{entry.Summary}\r\nCOMMANDID: {entry.CommandId}\r\n원문 출처: {source}\r\n\r\n{entry.Text}";
    }
    private void Wrap_Changed(object sender, RoutedEventArgs e)
    { if (MessageBody is not null) MessageBody.TextWrapping = WrapCheck.IsChecked == true ? TextWrapping.Wrap : TextWrapping.NoWrap; }
    private void Copy_Click(object sender, RoutedEventArgs e)
    { if (MessagesGrid.SelectedItem is LogEntry entry) Clipboard.SetText(Format(entry)); }
    private void Folder_Click(object sender, RoutedEventArgs e) => Process.Start(new ProcessStartInfo(_logDirectory) { UseShellExecute = true });
    private static string Key(LogEntry entry) => $"{entry.Time:yyyyMMddHHmmssfff}|{entry.Direction}|{entry.SystemBytes}|{entry.Text.Replace("\r\n", "\n").TrimEnd()}";
    internal void LoadArchivedMessages(IReadOnlyList<LogEntry> archived)
    {
        var sourceKeys = _sourceEntries.Select(Key).ToHashSet(StringComparer.Ordinal);
        foreach (var old in _entries.Where(e => _archiveKeys.Contains(Key(e)) && !sourceKeys.Contains(Key(e))).ToArray()) _entries.Remove(old);
        _archiveKeys.Clear();
        var keys = _entries.Select(Key).ToHashSet(StringComparer.Ordinal);
        foreach (var entry in archived)
        {
            string key = Key(entry); _archiveKeys.Add(key);
            if (!keys.Add(key)) continue;
            int index = 0;
            while (index < _entries.Count && _entries[index].Time.DateTime >= entry.Time.DateTime) index++;
            _entries.Insert(index, entry);
        }
        UpdateCount();
    }
    internal int BeginTimelineNavigation(string command, string label)
    {
        FollowCheck.IsChecked = false;
        ModeCombo.SelectedIndex = 1;
        SetSelectedJob(command, true);
        MessagesGrid.SelectedItem = null;
        MessageBody.Text = "선택한 타임라인의 SECS 원문을 찾고 있습니다.";
        NavigationText.Text = label + " · COMMANDID " + command;
        return ++NavigationRevision;
    }
    internal void SelectTimelineMessage(LogEntry entry)
    {
        var match = _view.Cast<LogEntry>().FirstOrDefault(e => Key(e) == Key(entry));
        if (match is null) { ShowNavigationMissing("일치하는 원문을 표시할 수 없습니다."); return; }
        MessagesGrid.SelectedItem = match; MessagesGrid.ScrollIntoView(match);
        MessageBody.Text = Format(match);
        NavigationText.Text += " · 원문 선택 완료 (새 메시지 따라가기 해제)";
    }
    internal void ShowNavigationMissing(string reason)
    { MessagesGrid.SelectedItem = null; MessageBody.Text = reason; NavigationText.Text += " · 원문 미확인"; }
    internal void ShowWorkMessages() { ModeCombo.SelectedIndex = 0; CommandFilterText.Clear(); Refresh(); }
    internal void VerifyPreview(Action<LogEntry> append)
    {
        ModeCombo.SelectedIndex = 0; CommandFilterText.Text = "LOG_WINDOW_PREVIEW";
        if (MessagesGrid.Items.Count != 0) throw new InvalidOperationException("Command filter did not isolate the log window");
        var entry = new LogEntry(DateTimeOffset.Now, "RX", "S6F11 W\r\n<L [3] ...>\r\n.\r\n(오프라인 창 검증)", 6, 11, 1234, "LOG_WINDOW_PREVIEW", 207, "오프라인 검증 · 실시간 메시지 추가");
        append(entry);
        if (MessagesGrid.Items.Count != 1 || MessagesGrid.SelectedItem is not LogEntry selected || selected.CommandId != entry.CommandId
            || !MessageBody.Text.Contains(entry.Text)) throw new InvalidOperationException("New messages/raw body not synchronized with detached window");
        FollowCheck.IsChecked = false;
        append(entry with { SystemBytes = 1235 });
        if (MessagesGrid.Items.Count != 2 || !ReferenceEquals(MessagesGrid.SelectedItem, selected)) throw new InvalidOperationException("Disabling follow did not preserve the selected message");
        ModeCombo.SelectedIndex = 2;
        if (MessagesGrid.Items.Count != 0) throw new InvalidOperationException("SECS messages leaked into system filter");
        append(new(DateTimeOffset.Now, "INFO", "오프라인 시스템 로그", CommandId: entry.CommandId));
        if (MessagesGrid.Items.Count != 1) throw new InvalidOperationException("System logs not received by detached window");
        FollowCheck.IsChecked = true; ShowWorkMessages();
    }
}
