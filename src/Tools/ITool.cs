namespace PixelSpriteEditor.Tools;

/// <summary>
/// Common interface for every drawing tool. Coordinates are already
/// converted to canvas pixel space by the canvas view before a tool sees
/// them, and are guaranteed to be in-bounds (drag/up may be clamped to the
/// nearest edge pixel rather than in-bounds -- see PixelCanvasView).
/// </summary>
public interface ITool
{
    void OnPointerDown(IEditorContext ctx, int x, int y);
    void OnPointerMove(IEditorContext ctx, int x, int y);
    void OnPointerUp(IEditorContext ctx, int x, int y);
}
