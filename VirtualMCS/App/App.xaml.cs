using System.Windows;

namespace KukaMcs.App;

public partial class App : Application
{
    private Mutex? _instance;
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        bool preview = e.Args.Length == 2 && e.Args[0] == "--render-preview";
        if (!preview)
        {
            _instance = new Mutex(true, @"Local\ServoStar_KukaVirtualMCS", out bool created);
            if (!created) { MessageBox.Show("KUKA Virtual MCS가 이미 실행 중입니다.\n기존 창 또는 Visual Studio 디버그 실행을 종료한 뒤 다시 실행하세요. 새 버전으로 자동 교체되지 않습니다."); Shutdown(); return; }
        }
        try
        {
            MainWindow = preview ? new MainWindow(System.IO.Path.Combine(System.IO.Path.GetDirectoryName(System.IO.Path.GetFullPath(e.Args[1]))!, "preview-profile")) : new MainWindow();
            if (preview)
            {
                MainWindow.ShowActivated = false; MainWindow.ShowInTaskbar = false; MainWindow.Left = -10000;
                MainWindow.Show();
                _ = Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.ApplicationIdle, new Action(async () =>
                {
                    try
                    {
                        var window = (MainWindow)MainWindow;
                        window.LoadPreviewFixture();
                        // Let hidden-tab DataGrid bindings/column widths settle
                        // after the cargo tab becomes visible in preview mode.
                        await Task.Delay(150);
                        if (window.SourceCombo.Items.Count != 10 || window.DestCombo.Items.Count != 10)
                            throw new InvalidOperationException("Work-port selectors must contain all ten ports");
                        if (window.TransferButton.IsEnabled || window.ForwardButton.IsEnabled || window.ReverseButton.IsEnabled) throw new InvalidOperationException("Disconnected UI permits transfer");
                        if (window.CancelButton.IsEnabled || window.AbortButton.IsEnabled || window.PauseButton.IsEnabled || window.ResumeButton.IsEnabled) throw new InvalidOperationException("Disconnected UI permits control");
                        if (window.CarrierText.IsReadOnly) throw new InvalidOperationException("Carrier ID is still fixed");
                        if (window.CargoQueryButton.IsEnabled || window.CargoInButton.IsEnabled || window.CargoOutButton.IsEnabled || window.CargoScanButton.IsEnabled || window.CargoVerifyButton.IsEnabled) throw new InvalidOperationException("Disconnected UI permits inventory command");
                        var content = (System.Windows.FrameworkElement)window.Content;
                        content.UpdateLayout();
                        var bitmap = new System.Windows.Media.Imaging.RenderTargetBitmap((int)content.ActualWidth, (int)content.ActualHeight, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
                        bitmap.Render(content);
                        var png = new System.Windows.Media.Imaging.PngBitmapEncoder(); png.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap));
                        using var output = System.IO.File.Create(System.IO.Path.GetFullPath(e.Args[1])); png.Save(output);
                        Shutdown(0);
                    }
                    catch (Exception ex) { System.IO.File.WriteAllText(e.Args[1] + ".error.txt", ex.ToString()); Shutdown(2); }
                }));
            }
            else MainWindow.Show();
        }
        catch (Exception ex) { MessageBox.Show("프로그램을 시작할 수 없습니다.\n" + ex.Message, "KUKA Virtual MCS"); Shutdown(1); }
    }
    protected override void OnExit(ExitEventArgs e) { _instance?.Dispose(); base.OnExit(e); }
}
