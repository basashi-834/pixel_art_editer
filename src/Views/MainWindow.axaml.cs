using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using PixelSpriteEditor.Models;
using PixelSpriteEditor.Services;

namespace PixelSpriteEditor.Views;

public partial class MainWindow : Window
{
    private readonly EditorState Editor;
    private bool _updatingColorUi;

    public MainWindow()
    {
        InitializeComponent();

        Editor = new EditorState(64, 64);
        CanvasView.Editor = Editor;
        Editor.Changed += OnEditorChanged;
        Loc.Changed += RefreshTexts;
        Closed += (_, _) => Loc.Changed -= RefreshTexts;

        WireToolbar();
        WireMenu();
        WireColorPanel();
        CanvasView.PixelHovered += OnPixelHovered;

        Loaded += (_, _) => CanvasView.CenterCamera();

        RefreshColorUi();
        RefreshTexts();
    }

    // ---- toolbar ----------------------------------------------------

    private void WireToolbar()
    {
        ToolPencilBtn.Click += (_, _) => SetTool(ToolType.Pencil);
        ToolEraserBtn.Click += (_, _) => SetTool(ToolType.Eraser);
        ToolFillBtn.Click += (_, _) => SetTool(ToolType.Fill);
        ToolEyedropperBtn.Click += (_, _) => SetTool(ToolType.Eyedropper);

        UndoBtn.Click += (_, _) => Editor.Undo();
        RedoBtn.Click += (_, _) => Editor.Redo();

        ZoomInBtn.Click += (_, _) => ZoomAtCenter(1);
        ZoomOutBtn.Click += (_, _) => ZoomAtCenter(-1);
    }

    private void SetTool(ToolType tool)
    {
        Editor.CurrentToolType = tool;
        Editor.RaiseChanged();
    }

    private void ZoomAtCenter(int direction)
    {
        var mid = new Point(CanvasView.Bounds.Width / 2, CanvasView.Bounds.Height / 2);
        if (direction > 0) Editor.ZoomIn(mid.X, mid.Y);
        else Editor.ZoomOut(mid.X, mid.Y);
    }

    // ---- menu ---------------------------------------------------------

    private void WireMenu()
    {
        MenuNew.Click += async (_, _) => await NewCanvasAsync();
        MenuOpen.Click += async (_, _) => await OpenAsync();
        MenuSave.Click += async (_, _) => await SaveAsync();
        MenuSaveAs.Click += async (_, _) => await SaveAsAsync();
        MenuExit.Click += (_, _) => Close();

        MenuUndo.Click += (_, _) => Editor.Undo();
        MenuRedo.Click += (_, _) => Editor.Redo();

        MenuTogglePixelGrid.Click += (_, _) => { Editor.ShowPixelGrid = !Editor.ShowPixelGrid; Editor.RaiseChanged(); };
        MenuToggle16Guide.Click += (_, _) => { Editor.Show16Guide = !Editor.Show16Guide; Editor.RaiseChanged(); };
        MenuToggleChecker.Click += (_, _) => { Editor.ShowTransparencyGrid = !Editor.ShowTransparencyGrid; Editor.RaiseChanged(); };

        MenuZoomIn.Click += (_, _) => ZoomAtCenter(1);
        MenuZoomOut.Click += (_, _) => ZoomAtCenter(-1);

        MenuLang.Click += (_, _) => Loc.Toggle();
    }

    private async System.Threading.Tasks.Task NewCanvasAsync()
    {
        var dlg = new NewCanvasDialog();
        await dlg.ShowDialog(this);
        if (dlg.Result is { } size)
        {
            Editor.NewCanvas(size.Width, size.Height);
            CanvasView.CenterCamera();
        }
    }

    private async System.Threading.Tasks.Task OpenAsync()
    {
        var files = await StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = Loc.T("Open PNG", "PNGを開く"),
            AllowMultiple = false,
            FileTypeFilter = new[] { new FilePickerFileType("PNG") { Patterns = new[] { "*.png" } } },
        });
        if (files.Count == 0) return;

        try
        {
            var path = files[0].Path.LocalPath;
            var canvas = ImageIO.LoadPng(path);
            Editor.LoadFrom(canvas, path);
            CanvasView.CenterCamera();
        }
        catch (Exception ex)
        {
            await ShowErrorAsync(Loc.T("Failed to open file", "ファイルを開けませんでした") + $":\n{ex.Message}");
        }
    }

    private async System.Threading.Tasks.Task SaveAsync()
    {
        if (Editor.CurrentFilePath == null)
        {
            await SaveAsAsync();
            return;
        }
        try
        {
            ImageIO.SavePng(Editor.Canvas, Editor.CurrentFilePath);
        }
        catch (Exception ex)
        {
            await ShowErrorAsync(Loc.T("Failed to save file", "保存に失敗しました") + $":\n{ex.Message}");
        }
    }

    private async System.Threading.Tasks.Task SaveAsAsync()
    {
        var file = await StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = Loc.T("Save PNG", "PNGを保存"),
            SuggestedFileName = "sprite.png",
            DefaultExtension = "png",
            FileTypeChoices = new[] { new FilePickerFileType("PNG") { Patterns = new[] { "*.png" } } },
        });
        if (file == null) return;

        try
        {
            var path = file.Path.LocalPath;
            ImageIO.SavePng(Editor.Canvas, path);
            Editor.SetSavedPath(path);
        }
        catch (Exception ex)
        {
            await ShowErrorAsync(Loc.T("Failed to save file", "保存に失敗しました") + $":\n{ex.Message}");
        }
    }

    private async System.Threading.Tasks.Task ShowErrorAsync(string message)
    {
        var okButton = new Button
        {
            Content = "OK",
            HorizontalAlignment = HorizontalAlignment.Right,
            MinWidth = 72,
        };
        var dlg = new Window
        {
            Width = 380,
            Height = 160,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Title = Loc.T("Error", "エラー"),
            Content = new StackPanel
            {
                Margin = new Thickness(16),
                Spacing = 12,
                Children =
                {
                    new TextBlock { Text = message, TextWrapping = Avalonia.Media.TextWrapping.Wrap },
                    okButton,
                },
            },
        };
        okButton.Click += (_, _) => dlg.Close();
        await dlg.ShowDialog(this);
    }

    // ---- color panel ----------------------------------------------------

    private void WireColorPanel()
    {
        SliderR.ValueChanged += (_, e) => { if (!_updatingColorUi) Editor.SetColorChannel(0, (byte)e.NewValue); };
        SliderG.ValueChanged += (_, e) => { if (!_updatingColorUi) Editor.SetColorChannel(1, (byte)e.NewValue); };
        SliderB.ValueChanged += (_, e) => { if (!_updatingColorUi) Editor.SetColorChannel(2, (byte)e.NewValue); };
        SliderA.ValueChanged += (_, e) => { if (!_updatingColorUi) Editor.SetColorChannel(3, (byte)e.NewValue); };

        SliderR.PointerReleased += (_, _) => Editor.PushCurrentColorToRecent();
        SliderG.PointerReleased += (_, _) => Editor.PushCurrentColorToRecent();
        SliderB.PointerReleased += (_, _) => Editor.PushCurrentColorToRecent();
        SliderA.PointerReleased += (_, _) => Editor.PushCurrentColorToRecent();
    }

    private void RefreshColorUi()
    {
        _updatingColorUi = true;
        var c = Editor.CurrentColor;
        SliderR.Value = c.R; ValR.Text = c.R.ToString();
        SliderG.Value = c.G; ValG.Text = c.G.ToString();
        SliderB.Value = c.B; ValB.Text = c.B.ToString();
        SliderA.Value = c.A; ValA.Text = c.A.ToString();
        _updatingColorUi = false;

        Rgb565Label.Text = $"RGB565: 0x{Rgb565.Pack(c):X4}";
        ColorSwatch.Background = new SolidColorBrush(Color.FromArgb(c.A, c.R, c.G, c.B));

        RecentPanel.Children.Clear();
        foreach (var recent in Editor.RecentColors)
        {
            var swatch = new Border
            {
                Width = 22,
                Height = 22,
                Margin = new Thickness(2),
                BorderThickness = new Thickness(1),
                BorderBrush = Brushes.Gray,
                Background = new SolidColorBrush(Color.FromArgb(recent.A, recent.R, recent.G, recent.B)),
            };
            var captured = recent;
            swatch.PointerPressed += (_, _) => Editor.SetCurrentColor(captured);
            RecentPanel.Children.Add(swatch);
        }
    }

    // ---- status bar / canvas hookup --------------------------------------

    private void OnPixelHovered(int x, int y, bool inBounds) =>
        StatusLeft.Text = inBounds ? $"({x}, {y})" : Loc.T("outside canvas", "キャンバス外");

    // ---- refresh --------------------------------------------------------

    private void OnEditorChanged()
    {
        RefreshColorUi();
        RefreshChrome();
    }

    private void RefreshChrome()
    {
        ZoomLabel.Text = $"{Editor.Zoom * 100}%";

        UndoBtn.IsEnabled = Editor.History.CanUndo;
        RedoBtn.IsEnabled = Editor.History.CanRedo;
        MenuUndo.IsEnabled = UndoBtn.IsEnabled;
        MenuRedo.IsEnabled = RedoBtn.IsEnabled;

        ToolPencilBtn.IsChecked = Editor.CurrentToolType == ToolType.Pencil;
        ToolEraserBtn.IsChecked = Editor.CurrentToolType == ToolType.Eraser;
        ToolFillBtn.IsChecked = Editor.CurrentToolType == ToolType.Fill;
        ToolEyedropperBtn.IsChecked = Editor.CurrentToolType == ToolType.Eyedropper;

        MenuTogglePixelGrid.Header = (Editor.ShowPixelGrid ? "✓ " : "") + Loc.T("Pixel Grid", "ピクセルグリッド");
        MenuToggle16Guide.Header = (Editor.Show16Guide ? "✓ " : "") + Loc.T("16x16 Guide", "16x16ガイド");
        MenuToggleChecker.Header = (Editor.ShowTransparencyGrid ? "✓ " : "") + Loc.T("Transparency Checkerboard", "透明チェッカーボード");

        var fileName = Editor.CurrentFilePath != null
            ? System.IO.Path.GetFileName(Editor.CurrentFilePath)
            : Loc.T("Untitled", "無題");
        Title = $"{fileName} - Pixel Sprite Editor";

        var pathText = Editor.CurrentFilePath ?? Loc.T("(unsaved)", "(未保存)");
        StatusRight.Text = $"{Editor.Canvas.Width}x{Editor.Canvas.Height}   {Editor.Zoom * 100}%   {pathText}";
    }

    private void RefreshTexts()
    {
        MenuFile.Header = Loc.T("File", "ファイル");
        MenuNew.Header = Loc.T("New", "新規") + "        Ctrl+N";
        MenuOpen.Header = Loc.T("Open...", "開く...") + "     Ctrl+O";
        MenuSave.Header = Loc.T("Save", "保存") + "       Ctrl+S";
        MenuSaveAs.Header = Loc.T("Save As...", "名前を付けて保存...") + "  Ctrl+Shift+S";
        MenuExit.Header = Loc.T("Exit", "終了");

        MenuEdit.Header = Loc.T("Edit", "編集");
        MenuUndo.Header = Loc.T("Undo", "元に戻す") + "     Ctrl+Z";
        MenuRedo.Header = Loc.T("Redo", "やり直し") + "    Ctrl+Y";

        MenuView.Header = Loc.T("View", "表示");
        MenuZoomIn.Header = Loc.T("Zoom In", "ズームイン") + "   Ctrl++";
        MenuZoomOut.Header = Loc.T("Zoom Out", "ズームアウト") + "  Ctrl+-";

        MenuLang.Header = Loc.Current == Lang.Ja ? "Language: 日本語 ▾" : "Language: English ▾";

        ToolPencilBtn.Content = Loc.T("Pencil", "鉛筆");
        ToolEraserBtn.Content = Loc.T("Eraser", "消しゴム");
        ToolFillBtn.Content = Loc.T("Fill", "塗りつぶし");
        ToolEyedropperBtn.Content = Loc.T("Eyedropper", "スポイト");
        UndoBtn.Content = Loc.T("Undo", "元に戻す");
        RedoBtn.Content = Loc.T("Redo", "やり直し");

        ColorPanelTitle.Text = Loc.T("Color", "カラー");
        RecentLabel.Text = Loc.T("Recent", "最近使った色");

        RefreshChrome();
    }

    // ---- headless test harness hooks (Program.cs --headless-screenshot) -
    // Internal, not public: only ever read by code in this same assembly.
    internal EditorState DebugEditor => Editor;
    internal PixelCanvasView DebugCanvasView => CanvasView;

    // ---- keyboard shortcuts: space-to-pan, ctrl+wheel zoom keys ----------

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (e.Key == Key.Space)
        {
            CanvasView.SetSpaceHeld(true);
            e.Handled = true;
            return;
        }

        bool ctrl = e.KeyModifiers.HasFlag(KeyModifiers.Control);
        if (ctrl && (e.Key == Key.OemPlus || e.Key == Key.Add))
        {
            ZoomAtCenter(1);
            e.Handled = true;
        }
        else if (ctrl && (e.Key == Key.OemMinus || e.Key == Key.Subtract))
        {
            ZoomAtCenter(-1);
            e.Handled = true;
        }
    }

    protected override void OnKeyUp(KeyEventArgs e)
    {
        base.OnKeyUp(e);
        if (e.Key == Key.Space)
        {
            CanvasView.SetSpaceHeld(false);
            e.Handled = true;
        }
    }
}
