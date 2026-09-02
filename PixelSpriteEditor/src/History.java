import java.util.ArrayList;
import java.util.List;

/**
 * Stroke-based undo/redo. A "stroke" is one user gesture (a pencil drag from
 * mouse-down to mouse-up, a single fill click, a committed line, etc). Each
 * stroke records only the pixels it actually touched so undo/redo stays
 * cheap even on larger canvases.
 */
public class History {

    /** One pixel's before/after ARGB value within a stroke. */
    public static final class PixelChange {
        final int x, y, before, after;

        PixelChange(int x, int y, int before, int after) {
            this.x = x;
            this.y = y;
            this.before = before;
            this.after = after;
        }
    }

    private List<PixelChange> inProgress = new ArrayList<>();
    private final List<List<PixelChange>> undoStack = new ArrayList<>();
    private final List<List<PixelChange>> redoStack = new ArrayList<>();

    public void beginStroke() {
        inProgress = new ArrayList<>();
    }

    /** Call after the pixel has already been written to the canvas. */
    public void recordChange(int x, int y, int before, int after) {
        inProgress.add(new PixelChange(x, y, before, after));
    }

    public void endStroke() {
        if (inProgress.isEmpty()) return;
        undoStack.add(inProgress);
        inProgress = new ArrayList<>();
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo(PixelCanvas canvas) {
        if (undoStack.isEmpty()) return;
        List<PixelChange> stroke = undoStack.remove(undoStack.size() - 1);
        for (int i = stroke.size() - 1; i >= 0; i--) {
            PixelChange c = stroke.get(i);
            canvas.setPixel(c.x, c.y, c.before);
        }
        redoStack.add(stroke);
    }

    public void redo(PixelCanvas canvas) {
        if (redoStack.isEmpty()) return;
        List<PixelChange> stroke = redoStack.remove(redoStack.size() - 1);
        for (PixelChange c : stroke) {
            canvas.setPixel(c.x, c.y, c.after);
        }
        undoStack.add(stroke);
    }

    public void clear() {
        inProgress.clear();
        undoStack.clear();
        redoStack.clear();
    }
}
