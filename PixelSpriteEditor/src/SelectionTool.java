import java.awt.Rectangle;

/**
 * Drags out a rectangular selection (clamped to the canvas) used by
 * Copy/Cut/Paste/Delete and by Flip (which flips only the selection when
 * one exists, or the whole active layer otherwise).
 */
public class SelectionTool implements Tool {
    private int startX, startY;

    @Override
    public void onPress(EditorState state, int x, int y) {
        startX = x;
        startY = y;
        state.setSelection(clamp(state, rectOf(x, y, x, y)));
    }

    @Override
    public void onDrag(EditorState state, int x, int y) {
        state.setSelection(clamp(state, rectOf(startX, startY, x, y)));
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
        state.setSelection(clamp(state, rectOf(startX, startY, x, y)));
    }

    private static Rectangle rectOf(int x0, int y0, int x1, int y1) {
        int minX = Math.min(x0, x1);
        int minY = Math.min(y0, y1);
        int w = Math.abs(x1 - x0) + 1;
        int h = Math.abs(y1 - y0) + 1;
        return new Rectangle(minX, minY, w, h);
    }

    private static Rectangle clamp(EditorState state, Rectangle r) {
        Rectangle bounds = new Rectangle(0, 0, state.getCanvas().getWidth(), state.getCanvas().getHeight());
        Rectangle clamped = r.intersection(bounds);
        return clamped.isEmpty() ? null : clamped;
    }
}
