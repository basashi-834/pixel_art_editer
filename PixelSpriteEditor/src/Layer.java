/**
 * One drawable layer within a Frame: its own pixel data and its own
 * undo/redo history (so switching layers doesn't mix up what Ctrl+Z
 * affects), a name, and a visibility flag used when compositing a Frame
 * for display/export.
 */
public class Layer {
    private String name;
    private boolean visible = true;
    private final PixelCanvas canvas;
    private final History history = new History();

    public Layer(String name, int width, int height) {
        this.name = name;
        this.canvas = new PixelCanvas(width, height);
    }

    /** For ProjectIO rebuilding a layer loaded from disk (same package, no need for public access). */
    Layer(String name, boolean visible, PixelCanvas canvas) {
        this.name = name;
        this.visible = visible;
        this.canvas = canvas;
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

    public PixelCanvas getCanvas() {
        return canvas;
    }

    public History getHistory() {
        return history;
    }

    public Layer duplicate() {
        PixelCanvas copy = new PixelCanvas(canvas.getWidth(), canvas.getHeight());
        copy.loadFrom(canvas.getImage());
        return new Layer(name + " コピー", visible, copy);
    }
}
