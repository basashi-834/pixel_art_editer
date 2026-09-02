using PixelSpriteEditor.Tools;

namespace PixelSpriteEditor.Models;

public enum ToolType { Pencil, Eraser, Fill, Eyedropper }

/// <summary>
/// The app's document + editing state: canvas, history, current tool/color,
/// view flags, camera. Analogous to the old C++ App class, but without any
/// window/dialog management -- Avalonia's Window and native file pickers
/// own that now. Raises <see cref="Changed"/> whenever something the UI
/// should reflect changes, so MainWindow can refresh in one place instead
/// of every mutation needing to know which controls depend on it.
/// </summary>
public sealed class EditorState : IEditorContext
{
    public static readonly int[] ZoomSteps = { 1, 2, 4, 8, 16, 24, 32 };

    public PixelCanvas Canvas { get; private set; }
    public History History { get; } = new();

    private PixelColor _currentColor = PixelColor.Black;
    public PixelColor CurrentColor => _currentColor;
    public List<PixelColor> RecentColors { get; } = new();

    public ToolType CurrentToolType { get; set; } = ToolType.Pencil;
    private readonly Dictionary<ToolType, ITool> _tools;
    public ITool CurrentTool => _tools[CurrentToolType];

    public bool ShowPixelGrid { get; set; } = true;
    public bool Show16Guide { get; set; } = true;
    public bool ShowTransparencyGrid { get; set; } = true;

    public int Zoom { get; private set; } = 8;
    public double CamOffsetX { get; set; }
    public double CamOffsetY { get; set; }

    public string? CurrentFilePath { get; private set; }

    public event Action? Changed;
    public void RaiseChanged() => Changed?.Invoke();

    public EditorState(int width, int height)
    {
        Canvas = new PixelCanvas(width, height);
        _tools = new Dictionary<ToolType, ITool>
        {
            [ToolType.Pencil] = new PencilTool(),
            [ToolType.Eraser] = new EraserTool(),
            [ToolType.Fill] = new FillTool(),
            [ToolType.Eyedropper] = new EyedropperTool(),
        };
        RecentColors.Add(PixelColor.Black);
        RecentColors.Add(PixelColor.White);
    }

    public void SetCurrentColor(PixelColor color)
    {
        _currentColor = Rgb565.Quantize(color);
        PushCurrentColorToRecent();
        Changed?.Invoke();
    }

    /// <summary>Sets one raw 0-255 channel (0=R,1=G,2=B,3=A) without touching
    /// the recent-colors list, for use while a slider drag is in progress.</summary>
    public void SetColorChannel(int channelIndex, byte value8)
    {
        var c = _currentColor;
        _currentColor = channelIndex switch
        {
            0 => c with { R = Rgb565.Quantize5(value8) },
            1 => c with { G = Rgb565.Quantize6(value8) },
            2 => c with { B = Rgb565.Quantize5(value8) },
            3 => c with { A = value8 },
            _ => c,
        };
        Changed?.Invoke();
    }

    public void PushCurrentColorToRecent()
    {
        RecentColors.Remove(_currentColor);
        RecentColors.Insert(0, _currentColor);
        if (RecentColors.Count > 16) RecentColors.RemoveRange(16, RecentColors.Count - 16);
        Changed?.Invoke();
    }

    public void PaintPixel(int x, int y, PixelColor c)
    {
        var before = Canvas.GetPixel(x, y);
        if (Canvas.SetPixel(x, y, c)) History.RecordChange(x, y, before, c);
    }

    public void BeginStroke() => History.BeginStroke();

    public void EndStroke()
    {
        History.EndStroke();
        Changed?.Invoke();
    }

    public void Undo() { History.Undo(Canvas); Changed?.Invoke(); }
    public void Redo() { History.Redo(Canvas); Changed?.Invoke(); }

    public void NewCanvas(int width, int height)
    {
        Canvas.Reset(width, height);
        History.Clear();
        CurrentFilePath = null;
        CenterCamera(0, 0, 0);
        Changed?.Invoke();
    }

    public void LoadFrom(PixelCanvas loaded, string path)
    {
        Canvas = loaded;
        History.Clear();
        CurrentFilePath = path;
        CenterCamera(0, 0, 0);
        Changed?.Invoke();
    }

    public void SetSavedPath(string path)
    {
        CurrentFilePath = path;
        Changed?.Invoke();
    }

    public void SetZoom(int newZoom, double anchorScreenX, double anchorScreenY)
    {
        newZoom = Math.Clamp(newZoom, ZoomSteps[0], ZoomSteps[^1]);
        double pxf = (anchorScreenX - CamOffsetX) / Zoom;
        double pyf = (anchorScreenY - CamOffsetY) / Zoom;
        Zoom = newZoom;
        CamOffsetX = anchorScreenX - pxf * Zoom;
        CamOffsetY = anchorScreenY - pyf * Zoom;
        Changed?.Invoke();
    }

    public void ZoomIn(double anchorScreenX, double anchorScreenY) => StepZoom(1, anchorScreenX, anchorScreenY);
    public void ZoomOut(double anchorScreenX, double anchorScreenY) => StepZoom(-1, anchorScreenX, anchorScreenY);

    private void StepZoom(int direction, double anchorScreenX, double anchorScreenY)
    {
        int idx = Array.IndexOf(ZoomSteps, Zoom);
        if (idx < 0) idx = 0;
        idx = Math.Clamp(idx + direction, 0, ZoomSteps.Length - 1);
        SetZoom(ZoomSteps[idx], anchorScreenX, anchorScreenY);
    }

    /// <summary>Centers the canvas within a viewport of the given size (0x0 = just reset to origin).</summary>
    public void CenterCamera(double viewportWidth, double viewportHeight, double viewportTop)
    {
        if (viewportWidth <= 0 || viewportHeight <= 0)
        {
            CamOffsetX = 0;
            CamOffsetY = viewportTop;
            return;
        }
        CamOffsetX = (viewportWidth - Canvas.Width * Zoom) / 2.0;
        CamOffsetY = viewportTop + (viewportHeight - Canvas.Height * Zoom) / 2.0;
    }
}
