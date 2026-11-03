using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Data;

namespace KukaMcs.App;

public partial class JobHistoryWindow : Window
{
    private readonly Action _showLogs;
    private readonly MainWindow _main;
    internal JobHistoryWindow(MainWindow main, Action showLogs)
    {
        InitializeComponent(); _showLogs = showLogs; _main = main;
        // Share the exact rows and selection. No second JobStore or transport connection.
        HistoryGrid.SetBinding(ItemsControl.ItemsSourceProperty, new Binding("ItemsSource") { Source = main.JobsGrid });
        HistoryGrid.SetBinding(Selector.SelectedItemProperty, new Binding("SelectedItem") { Source = main.JobsGrid, Mode = BindingMode.TwoWay });
        PendingText.SetBinding(TextBlock.TextProperty, new Binding("Text") { Source = main.PendingText });
        DetailsText.SetBinding(TextBox.TextProperty, new Binding("Text") { Source = main.JobDetailsText, Mode = BindingMode.OneWay });
        HistoryTimelineGrid.SetBinding(ItemsControl.ItemsSourceProperty, new Binding("ItemsSource") { Source = main.TimelineGrid });
        Closed += (_, _) =>
        {
            // Detach selection first: removing ItemsSource while its two-way
            // binding is live would clear the main window's selection too.
            BindingOperations.ClearBinding(HistoryGrid, Selector.SelectedItemProperty);
            foreach (var control in new DependencyObject[] { HistoryGrid, PendingText, DetailsText, HistoryTimelineGrid })
                BindingOperations.ClearAllBindings(control);
            HistoryGrid.ItemsSource = HistoryTimelineGrid.ItemsSource = null;
        };
    }
    private void CopyCommand_Click(object sender, RoutedEventArgs e)
    { if (HistoryGrid.SelectedItem is MainWindow.JobRow row) Clipboard.SetText(row.CommandId); }
    private void Logs_Click(object sender, RoutedEventArgs e) => _showLogs();
    private async void Timeline_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ChangedButton == System.Windows.Input.MouseButton.Left && e.OriginalSource is DependencyObject element
            && ItemsControl.ContainerFromElement(HistoryTimelineGrid, element) is DataGridRow { Item: MainWindow.TimelineRow row })
            await _main.NavigateTimelineAsync(row);
    }
}
