/**
 * Common interface for every drawing tool. Coordinates are already
 * converted to canvas pixel space by CanvasPanel before a tool sees them;
 * they may be outside the canvas (a fast drag can leave the sprite's
 * bounds) -- PixelCanvas.setPixel/getPixel already ignore out-of-bounds
 * coordinates safely, so tools don't need to bounds-check themselves except
 * where noted.
 */
public interface Tool {
    void onPress(EditorState state, int x, int y);
    void onDrag(EditorState state, int x, int y);
    void onRelease(EditorState state, int x, int y);
}
