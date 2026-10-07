/**
 * One drawable layer within a Frame: its own pixel data and its own
 * undo/redo history (so switching layers doesn't mix up what Ctrl+Z
 * affects), a name, a visibility flag, and an opacity (0.0-1.0) used when
 * compositing a Frame for display/export.
 */
public class Layer {
    private String name;
    private boolean visible = true;
    private float opacity = 1f;
    private final PixelCanvas canvas;
    private final History history = new History();

    public Layer(String name, int width, int height) {
        this.name = name;
        this.canvas = new PixelCanvas(width, height);
    }

    /** For ProjectIO rebuilding a layer loaded from disk (same package, no need for public access). */
    Layer(String name, boolean visible, float opacity, PixelCanvas canvas) {
        this.name = name;
        this.visible = visible;
        this.opacity = opacity;
        this.canvas = canvas;
    }

    /** Convenience for callers that don't care about opacity (defaults to fully opaque). */
    Layer(String name, boolean visible, PixelCanvas canvas) {
        this(name, visible, 1f, canvas);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public PixelCanvas getCanvas() {
        return canvas;
    }

    public History getHistory() {
        return history;
    }

    public Layer duplicate() {
        PixelCanvas copy = new PixelCanvas(canvas.getWidth(), canvas.getHeight());
        copy.loadFrom(canvas.getImage());
        return new Layer(name + " コピー", visible, opacity, copy);
    }
}
