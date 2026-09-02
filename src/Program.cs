using Avalonia;
using Avalonia.Headless;
using Avalonia.Input;
using Avalonia.Threading;
using PixelSpriteEditor.Services;

namespace PixelSpriteEditor;

internal static class Program
{
    // Entry point. Normally starts the real desktop app; "--headless-verify
    // <dir>" instead boots Avalonia's headless backend, drives MainWindow
    // through a short scripted interaction (draw, undo via the real Ctrl+Z
    // shortcut, switch language) and saves a screenshot after each step --
    // used for automated UI verification in environments (like CI or this
    // sandbox) with no real display. "--selftest" runs pure-logic checks
    // (no window) and prints PASS/FAIL.
    [STAThread]
    public static void Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--headless-verify")
        {
            RunHeadlessVerify(args.Length > 1 ? args[1] : ".");
            return;
        }
        if (args.Length > 0 && args[0] == "--selftest")
        {
            Environment.Exit(SelfTest.Run() ? 0 : 1);
        }
        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    private static void RunHeadlessVerify(string outDir)
    {
        Directory.CreateDirectory(outDir);

        AppBuilder.Configure<App>()
            .UseSkia()
            .UseHeadless(new AvaloniaHeadlessPlatformOptions { UseHeadlessDrawing = false })
            .SetupWithoutStarting();

        var window = new Views.MainWindow();
        window.Show();
        Dispatcher.UIThread.RunJobs();
        Save(window, outDir, "1-initial-ja.png");

        // Draw a short pencil stroke directly on the canvas via real pointer events.
        var editor = window.DebugEditor;
        var canvasView = window.DebugCanvasView;
        var origin = canvasView.TranslatePoint(new Point(0, 0), window) ?? new Point();
        double sx = origin.X + editor.CamOffsetX + 4 * editor.Zoom + editor.Zoom / 2.0;
        double sy = origin.Y + editor.CamOffsetY + 4 * editor.Zoom + editor.Zoom / 2.0;
        window.MouseDown(new Point(sx, sy), MouseButton.Left);
        for (int i = 0; i < 6; i++)
        {
            window.MouseMove(new Point(sx + i * editor.Zoom, sy));
            Dispatcher.UIThread.RunJobs();
        }
        window.MouseUp(new Point(sx + 5 * editor.Zoom, sy), MouseButton.Left);
        Dispatcher.UIThread.RunJobs();
        Save(window, outDir, "2-drawn-stroke.png");

        // Undo through the real Ctrl+Z shortcut (verifies HotKey wiring, not just the model).
        window.KeyPressQwerty(PhysicalKey.Z, RawInputModifiers.Control);
        window.KeyReleaseQwerty(PhysicalKey.Z, RawInputModifiers.Control);
        Dispatcher.UIThread.RunJobs();
        Save(window, outDir, "3-after-undo.png");

        // Switch to English (same action MenuLang.Click performs) and confirm the whole UI relabels.
        Loc.Toggle();
        Dispatcher.UIThread.RunJobs();
        Save(window, outDir, "4-english.png");

        Console.WriteLine($"Headless verification screenshots written to {Path.GetFullPath(outDir)}");
    }

    private static void Save(Views.MainWindow window, string outDir, string name)
    {
        using var bmp = window.CaptureRenderedFrame();
        bmp?.Save(Path.Combine(outDir, name));
    }

    // No bundled font (.WithInterFont() etc.) -- deliberately relies on
    // whatever the OS already provides (Segoe UI + its Japanese fallback
    // on Windows). Shipping a font file was a real source of trouble in
    // the previous C++ build (a large embedded blob got flagged by
    // Windows security heuristics), so this version just doesn't carry one.
    public static AppBuilder BuildAvaloniaApp() => AppBuilder.Configure<App>()
        .UsePlatformDetect()
        .LogToTrace();
}
