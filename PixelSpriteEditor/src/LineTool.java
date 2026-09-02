/**
 * Straight line from the press point to wherever the drag currently is.
 * Nothing is written to the canvas until release -- while dragging, the
 * candidate line is only held as a preview (EditorState.setPreview) that
 * CanvasPanel draws on top of the real pixels, so moving the mouse around
 * doesn't leave a trail of committed pixels behind it.
 */
public class LineTool implements Tool {
    private int startX, startY;
    private boolean active;

    @Override
    public void onPress(EditorState state, int x, int y) {
        startX = x;
        startY = y;
        active = true;
        state.setPreview(startX, startY, x, y);
    }

    @Override
    public void onDrag(EditorState state, int x, int y) {
        if (!active) return;
        state.setPreview(startX, startY, x, y);
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
        if (!active) return;
        active = false;
        state.clearPreview();

        int argb = state.getCurrentColorArgb();
        state.beginStroke();
        LineUtil.forEachLinePixel(startX, startY, x, y, (px, py) -> state.paintPixel(px, py, argb));
        state.endStroke();
    }
}
