/**
 * Same drag/interpolation behavior as PencilTool, but always writes fully
 * transparent (ARGB 0) instead of the current color.
 */
public class EraserTool implements Tool {
    private static final int TRANSPARENT = 0;

    private int lastX, lastY;
    private boolean hasLast;

    @Override
    public void onPress(EditorState state, int x, int y) {
        state.beginStroke();
        state.paintPixel(x, y, TRANSPARENT);
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
        LineUtil.forEachLinePixel(lastX, lastY, x, y, (px, py) -> state.paintPixel(px, py, TRANSPARENT));
        lastX = x;
        lastY = y;
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
        state.endStroke();
        hasLast = false;
    }
}
