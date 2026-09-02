namespace PixelSpriteEditor.Tools;

/// <summary>Picks the color under the cursor and makes it the current color.</summary>
public sealed class EyedropperTool : ITool
{
    public void OnPointerDown(IEditorContext ctx, int x, int y)
    {
        if (!ctx.Canvas.InBounds(x, y)) return;
        ctx.SetCurrentColor(ctx.Canvas.GetPixel(x, y));
    }

    public void OnPointerMove(IEditorContext ctx, int x, int y) => OnPointerDown(ctx, x, y);
    public void OnPointerUp(IEditorContext ctx, int x, int y) { }
}
