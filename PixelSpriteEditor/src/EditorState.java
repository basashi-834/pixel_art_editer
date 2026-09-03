import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The app's central mutable state: canvas, history, current tool/color,
 * view flags, camera. Owns no Swing components -- EditorWindow/CanvasPanel
 * read from and react to it, so the model stays testable and reusable
 * independent of the UI.
 *
 * Two different update paths are used on purpose:
 *  - fireChanged() (via endStroke/undo/redo/newCanvas/etc) notifies
 *    listeners for things that are fine to refresh occasionally (toolbar
 *    button states, status bar, undo/redo enabled-ness).
 *  - paintPixel() during an in-progress stroke does NOT fire listeners --
 *    CanvasPanel repaints itself directly after every dragged pixel instead,
 *    so a fast drag doesn't trigger a full UI refresh per pixel.
 */
public class EditorState {

    public enum ToolType { PENCIL, ERASER, FILL, EYEDROPPER, LINE }

    public static final int[] ZOOM_STEPS = {1, 2, 3, 4, 6, 8, 12, 16, 24, 32};

    private PixelCanvas canvas;
    private final History history = new History();

    private int currentColorArgb = 0xFF000000; // opaque black
    private final List<Integer> recentColors = new ArrayList<>();

    private ToolType currentToolType = ToolType.PENCIL;
    private final Map<ToolType, Tool> tools = new EnumMap<>(ToolType.class);

    private boolean showPixelGrid = true;
    private boolean show16Guide = true;
    private boolean showTransparencyChecker = true;

    private int zoom = 8;
    private double camOffsetX;
    private double camOffsetY;

    private String currentFilePath;

    private boolean previewActive;
    private int previewX0, previewY0, previewX1, previewY1;

    private final List<Runnable> listeners = new ArrayList<>();

    public EditorState(int width, int height) {
        canvas = new PixelCanvas(width, height);
        tools.put(ToolType.PENCIL, new PencilTool());
        tools.put(ToolType.ERASER, new EraserTool());
        tools.put(ToolType.FILL, new FillTool());
        tools.put(ToolType.EYEDROPPER, new EyedropperTool());
        tools.put(ToolType.LINE, new LineTool());
        recentColors.add(0xFF000000);
        recentColors.add(0xFFFFFFFF);
    }

    // ---- listeners ----------------------------------------------------

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void fireChanged() {
        for (Runnable r : listeners) r.run();
    }

    // ---- canvas / history ----------------------------------------------

    public PixelCanvas getCanvas() {
        return canvas;
    }

    public History getHistory() {
        return history;
    }

    public void paintPixel(int x, int y, int argb) {
        int before = canvas.getPixel(x, y);
        if (canvas.setPixel(x, y, argb)) {
            history.recordChange(x, y, before, argb);
        }
    }

    public void beginStroke() {
        history.beginStroke();
    }

    public void endStroke() {
        history.endStroke();
        fireChanged();
    }

    public void undo() {
        history.undo(canvas);
        fireChanged();
    }

    public void redo() {
        history.redo(canvas);
        fireChanged();
    }

    public void newCanvas(int width, int height) {
        canvas.reset(width, height);
        history.clear();
        currentFilePath = null;
        fireChanged();
    }

    public void loadFrom(BufferedImage image, String path) {
        canvas.loadFrom(image);
        history.clear();
        currentFilePath = path;
        fireChanged();
    }

    public void setSavedPath(String path) {
        currentFilePath = path;
        fireChanged();
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    // ---- color ----------------------------------------------------------

    public int getCurrentColorArgb() {
        return currentColorArgb;
    }

    public void setCurrentColorArgb(int argb) {
        currentColorArgb = argb;
        pushCurrentColorToRecent();
        fireChanged();
    }

    /** Sets one 0-255 channel (0=R,1=G,2=B,3=A) without touching recent colors, for a slider drag in progress. */
    public void setColorChannel(int channelIndex, int value8) {
        int a = (currentColorArgb >>> 24) & 0xFF;
        int r = (currentColorArgb >>> 16) & 0xFF;
        int g = (currentColorArgb >>> 8) & 0xFF;
        int b = currentColorArgb & 0xFF;
        switch (channelIndex) {
            case 0:
                r = value8;
                break;
            case 1:
                g = value8;
                break;
            case 2:
                b = value8;
                break;
            case 3:
                a = value8;
                break;
            default:
                throw new IllegalArgumentException("channelIndex must be 0-3");
        }
        currentColorArgb = (a << 24) | (r << 16) | (g << 8) | b;
        fireChanged();
    }

    public void pushCurrentColorToRecent() {
        recentColors.remove((Integer) currentColorArgb);
        recentColors.add(0, currentColorArgb);
        while (recentColors.size() > 16) {
            recentColors.remove(recentColors.size() - 1);
        }
        fireChanged();
    }

    public List<Integer> getRecentColors() {
        return recentColors;
    }

    public void removeRecentColor(int argb) {
        if (recentColors.remove((Integer) argb)) {
            fireChanged();
        }
    }

    // ---- tool -------------------------------------------------------------

    public ToolType getCurrentToolType() {
        return currentToolType;
    }

    public void setCurrentToolType(ToolType type) {
        currentToolType = type;
        fireChanged();
    }

    public Tool getCurrentTool() {
        return tools.get(currentToolType);
    }

    // ---- view flags -------------------------------------------------------

    public boolean isShowPixelGrid() {
        return showPixelGrid;
    }

    public void setShowPixelGrid(boolean v) {
        showPixelGrid = v;
        fireChanged();
    }

    public boolean isShow16Guide() {
        return show16Guide;
    }

    public void setShow16Guide(boolean v) {
        show16Guide = v;
        fireChanged();
    }

    public boolean isShowTransparencyChecker() {
        return showTransparencyChecker;
    }

    public void setShowTransparencyChecker(boolean v) {
        showTransparencyChecker = v;
        fireChanged();
    }

    // ---- zoom / camera ------------------------------------------------------

    public int getZoom() {
        return zoom;
    }

    public double getCamOffsetX() {
        return camOffsetX;
    }

    public double getCamOffsetY() {
        return camOffsetY;
    }

    /** Pans without firing listeners -- CanvasPanel repaints itself directly while dragging. */
    public void panBy(double dx, double dy) {
        camOffsetX += dx;
        camOffsetY += dy;
    }

    public void setZoom(int newZoom, double anchorX, double anchorY) {
        int clamped = Math.max(ZOOM_STEPS[0], Math.min(ZOOM_STEPS[ZOOM_STEPS.length - 1], newZoom));
        double pxf = (anchorX - camOffsetX) / zoom;
        double pyf = (anchorY - camOffsetY) / zoom;
        zoom = clamped;
        camOffsetX = anchorX - pxf * zoom;
        camOffsetY = anchorY - pyf * zoom;
        fireChanged();
    }

    public void zoomIn(double anchorX, double anchorY) {
        stepZoom(1, anchorX, anchorY);
    }

    public void zoomOut(double anchorX, double anchorY) {
        stepZoom(-1, anchorX, anchorY);
    }

    private void stepZoom(int direction, double anchorX, double anchorY) {
        int idx = -1;
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            if (ZOOM_STEPS[i] == zoom) {
                idx = i;
                break;
            }
        }
        if (idx < 0) idx = 0;
        idx = Math.max(0, Math.min(ZOOM_STEPS.length - 1, idx + direction));
        setZoom(ZOOM_STEPS[idx], anchorX, anchorY);
    }

    /** Centers the canvas within a viewport of the given size (0x0 = just reset to origin). */
    public void centerCamera(double viewportWidth, double viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            camOffsetX = 0;
            camOffsetY = 0;
            return;
        }
        camOffsetX = (viewportWidth - canvas.getWidth() * zoom) / 2.0;
        camOffsetY = (viewportHeight - canvas.getHeight() * zoom) / 2.0;
    }

    // ---- line-tool preview (drawn by CanvasPanel, never touches the canvas) ---

    public void setPreview(int x0, int y0, int x1, int y1) {
        previewActive = true;
        previewX0 = x0;
        previewY0 = y0;
        previewX1 = x1;
        previewY1 = y1;
    }

    public void clearPreview() {
        previewActive = false;
    }

    public boolean hasPreview() {
        return previewActive;
    }

    public int getPreviewX0() {
        return previewX0;
    }

    public int getPreviewY0() {
        return previewY0;
    }

    public int getPreviewX1() {
        return previewX1;
    }

    public int getPreviewY1() {
        return previewY1;
    }
}
