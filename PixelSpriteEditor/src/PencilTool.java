/**
 * Freehand drawing. A drag is treated as one continuous stroke (one undo
 * step); gaps between fast pointer movements are filled with a line so no
 * pixels are skipped.
 */
public class PencilTool implements Tool {
    private int lastX, lastY;
    private boolean hasLast;

    @Override
    public void onPress(EditorState state, int x, int y) {
        state.beginStroke();
        state.paintPixel(x, y, state.getCurrentColorArgb());
        lastX = x;
        lastY = y;
        hasLast = true;
    }

    @Override
    public void onDrag(EditorState state, int x, int y) {
        if (!hasLast) {
            lastX = x;
            lastY = y;
            hasLast = true;
        }
        int argb = state.getCurrentColorArgb();
        LineUtil.forEachLinePixel(lastX, lastY, x, y, (px, py) -> state.paintPixel(px, py, argb));
        lastX = x;
        lastY = y;
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
        state.endStroke();
        hasLast = false;
    }
}
