using PixelSpriteEditor.Models;

namespace PixelSpriteEditor.Tools;

/// <summary>Same drag/stroke behaviour as PencilTool but always paints fully transparent pixels.</summary>
public sealed class EraserTool : ITool
{
    private int _lastX, _lastY;
    private bool _hasLast;

    public void OnPointerDown(IEditorContext ctx, int x, int y)
    {
        ctx.BeginStroke();
        ctx.PaintPixel(x, y, PixelColor.Transparent);
        _lastX = x;
        _lastY = y;
        _hasLast = true;
    }

    public void OnPointerMove(IEditorContext ctx, int x, int y)
    {
        if (!_hasLast) { _lastX = x; _lastY = y; _hasLast = true; }
        LineUtil.ForEachLinePixel(_lastX, _lastY, x, y, (px, py) => ctx.PaintPixel(px, py, PixelColor.Transparent));
        _lastX = x;
        _lastY = y;
    }

    public void OnPointerUp(IEditorContext ctx, int x, int y)
    {
        ctx.EndStroke();
        _hasLast = false;
    }
}
