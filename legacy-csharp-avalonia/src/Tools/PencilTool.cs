namespace PixelSpriteEditor.Tools;

/// <summary>
/// Freehand drawing. A drag is treated as one continuous stroke (one undo
/// step); gaps between fast pointer movements are filled with a line so no
/// pixels are skipped.
/// </summary>
public sealed class PencilTool : ITool
{
    private int _lastX, _lastY;
    private bool _hasLast;

    public void OnPointerDown(IEditorContext ctx, int x, int y)
    {
        ctx.BeginStroke();
        ctx.PaintPixel(x, y, ctx.CurrentColor);
        _lastX = x;
        _lastY = y;
        _hasLast = true;
    }

    public void OnPointerMove(IEditorContext ctx, int x, int y)
    {
        if (!_hasLast) { _lastX = x; _lastY = y; _hasLast = true; }
        var color = ctx.CurrentColor;
        LineUtil.ForEachLinePixel(_lastX, _lastY, x, y, (px, py) => ctx.PaintPixel(px, py, color));
        _lastX = x;
        _lastY = y;
    }

    public void OnPointerUp(IEditorContext ctx, int x, int y)
    {
        ctx.EndStroke();
        _hasLast = false;
    }
}
