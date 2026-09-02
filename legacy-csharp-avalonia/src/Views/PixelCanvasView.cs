using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using PixelSpriteEditor.Models;
using PixelSpriteEditor.Services;

namespace PixelSpriteEditor.Views;

/// <summary>
/// Renders the sprite at the current zoom with nearest-neighbor scaling, an
/// optional checkerboard behind transparent pixels, an optional 1px pixel
/// grid, and an optional 16x16 block guide -- none of which are ever part of
/// the saved PNG, only this view. Also owns pointer input: painting
/// (delegated to the active tool), panning (middle-drag or Space+drag), and
/// wheel-zoom anchored at the cursor.
/// </summary>
public sealed class PixelCanvasView : Control
{
    public static readonly StyledProperty<EditorState?> EditorProperty =
        AvaloniaProperty.Register<PixelCanvasView, EditorState?>(nameof(Editor));

    public EditorState? Editor
    {
        get => GetValue(EditorProperty);
        set => SetValue(EditorProperty, value);
    }

    /// <summary>Raised on every pointer move over the view, for the status bar (x,y,isInsideCanvas).</summary>
    public event Action<int, int, bool>? PixelHovered;

    private WriteableBitmap? _bitmap;
    private bool _panning;
    private Point _panLastPos;
    private bool _painting;
    private bool _spaceHeld;
    private int _hoverX, _hoverY;
    private bool _hoverVisible;

    private static readonly IBrush CheckerBrush = CreateCheckerBrush();

    public PixelCanvasView()
    {
        Focusable = true;
        ClipToBounds = true;
        RenderOptions.SetBitmapInterpolationMode(this, Avalonia.Media.Imaging.BitmapInterpolationMode.None);
    }

    static PixelCanvasView()
    {
        EditorProperty.Changed.AddClassHandler<PixelCanvasView>((view, e) => view.OnEditorPropertyChanged(e));
    }

    private void OnEditorPropertyChanged(AvaloniaPropertyChangedEventArgs e)
    {
        if (e.OldValue is EditorState oldState) oldState.Changed -= OnEditorChanged;
        if (e.NewValue is EditorState newState) newState.Changed += OnEditorChanged;
        RebuildBitmap();
        InvalidateVisual();
    }

    private static IBrush CreateCheckerBrush()
    {
        const int size = 16;
        var bmp = new WriteableBitmap(new PixelSize(size, size), new Vector(96, 96),
            Avalonia.Platform.PixelFormat.Rgba8888, Avalonia.Platform.AlphaFormat.Opaque);
        using (var fb = bmp.Lock())
        {
            unsafe
            {
                const byte light = 200, dark = 150;
                var ptr = (byte*)fb.Address;
                for (int y = 0; y < size; y++)
                {
                    for (int x = 0; x < size; x++)
                    {
                        bool isLight = ((x / 8) + (y / 8)) % 2 == 0;
                        byte v = isLight ? light : dark;
                        int o = y * fb.RowBytes + x * 4;
                        ptr[o] = v; ptr[o + 1] = v; ptr[o + 2] = v; ptr[o + 3] = 255;
                    }
                }
            }
        }
        return new ImageBrush(bmp)
        {
            TileMode = TileMode.Tile,
            DestinationRect = new RelativeRect(0, 0, size, size, RelativeUnit.Absolute),
            Stretch = Stretch.None,
        };
    }

    private void OnEditorChanged()
    {
        RebuildBitmap();
        InvalidateVisual();
    }

    private void RebuildBitmap()
    {
        _bitmap?.Dispose();
        _bitmap = Editor != null ? ImageIO.ToWriteableBitmap(Editor.Canvas) : null;
    }

    /// <summary>Centers the sprite in the current viewport. Call after the view has a real size (Loaded) and after New/Open.</summary>
    public void CenterCamera()
    {
        Editor?.CenterCamera(Bounds.Width, Bounds.Height, 0);
        InvalidateVisual();
    }

    public override void Render(DrawingContext context)
    {
        base.Render(context);
        var e = Editor;
        if (e == null || _bitmap == null) return;

        var rect = new Rect(e.CamOffsetX, e.CamOffsetY, e.Canvas.Width * e.Zoom, e.Canvas.Height * e.Zoom);

        if (e.ShowTransparencyGrid)
            context.FillRectangle(CheckerBrush, rect);

        context.DrawImage(_bitmap, new Rect(0, 0, e.Canvas.Width, e.Canvas.Height), rect);

        if (e.ShowPixelGrid && e.Zoom >= 4)
        {
            var pen = new Pen(new SolidColorBrush(Color.FromArgb(90, 128, 128, 128)), 1);
            DrawGrid(context, e, pen, 1);
        }

        if (e.Show16Guide)
        {
            var pen = new Pen(new SolidColorBrush(Color.FromArgb(210, 255, 70, 70)), 1);
            DrawGrid(context, e, pen, 16);
        }

        if (_hoverVisible)
        {
            double x0 = e.CamOffsetX + _hoverX * e.Zoom;
            double y0 = e.CamOffsetY + _hoverY * e.Zoom;
            double size = e.Zoom;
            // Black-then-white double outline so the cursor pixel stays visible against any color underneath.
            var outer = new Rect(x0 + 0.5, y0 + 0.5, Math.Max(0, size - 1), Math.Max(0, size - 1));
            context.DrawRectangle(null, new Pen(Brushes.Black, 1), outer);
            if (size >= 6)
            {
                var inner = new Rect(x0 + 1.5, y0 + 1.5, Math.Max(0, size - 3), Math.Max(0, size - 3));
                context.DrawRectangle(null, new Pen(Brushes.White, 1), inner);
            }
        }
    }

    private static void DrawGrid(DrawingContext context, EditorState e, Pen pen, int step)
    {
        var rect = new Rect(e.CamOffsetX, e.CamOffsetY, e.Canvas.Width * e.Zoom, e.Canvas.Height * e.Zoom);
        for (int x = 0; x <= e.Canvas.Width; x += step)
        {
            double sx = e.CamOffsetX + x * e.Zoom;
            context.DrawLine(pen, new Point(sx, rect.Top), new Point(sx, rect.Bottom));
        }
        for (int y = 0; y <= e.Canvas.Height; y += step)
        {
            double sy = e.CamOffsetY + y * e.Zoom;
            context.DrawLine(pen, new Point(rect.Left, sy), new Point(rect.Right, sy));
        }
    }

    private (int x, int y) ToPixel(Point p)
    {
        var e = Editor;
        if (e == null) return (0, 0);
        int px = (int)Math.Floor((p.X - e.CamOffsetX) / e.Zoom);
        int py = (int)Math.Floor((p.Y - e.CamOffsetY) / e.Zoom);
        return (px, py);
    }

    public void SetSpaceHeld(bool held) => _spaceHeld = held;

    protected override void OnPointerPressed(PointerPressedEventArgs e)
    {
        base.OnPointerPressed(e);
        var editor = Editor;
        if (editor == null) return;
        Focus();
        var point = e.GetCurrentPoint(this);
        var pos = point.Position;

        if (point.Properties.IsMiddleButtonPressed || (_spaceHeld && point.Properties.IsLeftButtonPressed))
        {
            _panning = true;
            _panLastPos = pos;
            e.Pointer.Capture(this);
            return;
        }

        if (point.Properties.IsLeftButtonPressed)
        {
            _painting = true;
            var (px, py) = ToPixel(pos);
            editor.CurrentTool.OnPointerDown(editor, px, py);
            e.Pointer.Capture(this);
            RebuildBitmap();
            InvalidateVisual();
        }
    }

    protected override void OnPointerMoved(PointerEventArgs e)
    {
        base.OnPointerMoved(e);
        var editor = Editor;
        if (editor == null) return;
        var pos = e.GetCurrentPoint(this).Position;

        if (_panning)
        {
            var delta = pos - _panLastPos;
            editor.CamOffsetX += delta.X;
            editor.CamOffsetY += delta.Y;
            _panLastPos = pos;
            InvalidateVisual();
            return;
        }

        var (px, py) = ToPixel(pos);
        bool inBounds = editor.Canvas.InBounds(px, py);
        PixelHovered?.Invoke(px, py, inBounds);

        if (_hoverX != px || _hoverY != py || _hoverVisible != inBounds)
        {
            _hoverX = px;
            _hoverY = py;
            _hoverVisible = inBounds;
            InvalidateVisual();
        }

        if (_painting)
        {
            editor.CurrentTool.OnPointerMove(editor, px, py);
            RebuildBitmap();
            InvalidateVisual();
        }
    }

    protected override void OnPointerExited(PointerEventArgs e)
    {
        base.OnPointerExited(e);
        if (_hoverVisible)
        {
            _hoverVisible = false;
            InvalidateVisual();
        }
    }

    protected override void OnPointerReleased(PointerReleasedEventArgs e)
    {
        base.OnPointerReleased(e);
        var editor = Editor;
        if (editor == null) return;

        if (_panning)
        {
            _panning = false;
            e.Pointer.Capture(null);
            return;
        }

        if (_painting)
        {
            var pos = e.GetCurrentPoint(this).Position;
            var (px, py) = ToPixel(pos);
            editor.CurrentTool.OnPointerUp(editor, px, py);
            _painting = false;
            e.Pointer.Capture(null);
            InvalidateVisual();
        }
    }

    protected override void OnPointerWheelChanged(PointerWheelEventArgs e)
    {
        base.OnPointerWheelChanged(e);
        var editor = Editor;
        if (editor == null) return;
        var pos = e.GetCurrentPoint(this).Position;
        if (e.Delta.Y > 0) editor.ZoomIn(pos.X, pos.Y);
        else if (e.Delta.Y < 0) editor.ZoomOut(pos.X, pos.Y);
        e.Handled = true;
    }
}
