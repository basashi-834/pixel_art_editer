import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The app's central mutable state: frames (each a stack of layers), the
 * active frame/layer, current tool/color, selection/clipboard, view flags,
 * camera. Owns no Swing components -- EditorWindow/CanvasPanel read from
 * and react to it, so the model stays testable and reusable independent of
 * the UI.
 *
 * Undo/redo is per-layer (each Layer has its own History): painting always
 * targets the active layer's canvas, so switching layers or frames doesn't
 * mix up what Ctrl+Z affects. Structural edits -- adding/removing a frame
 * or layer -- are NOT undoable; only pixel edits are (which does cover
 * Flip and Paste, since both go through beginStroke/paintPixel/endStroke
 * like any other tool).
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

    public enum ToolType { PENCIL, ERASER, FILL, EYEDROPPER, LINE, SELECT }

    public static final double[] ZOOM_STEPS = {0.0625, 0.125, 0.25, 0.5, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64};

    private static final int[] DEFAULT_PALETTE_COLORS = {
        0x00000000, // transparent
        0xFF000000, // black
        0xFF555555, // dark gray
        0xFFAAAAAA, // light gray
        0xFFFFFFFF, // white
        0xFFE63946, // red
        0xFFF4A261, // orange
        0xFFFFD166, // yellow
        0xFF2A9D8F, // teal green
        0xFF1B4332, // dark green
        0xFF4CC9F0, // cyan
        0xFF1D3557, // navy blue
        0xFF7B2CBF, // purple
        0xFFF72585, // pink
        0xFF6F4518, // brown
        0xFFE0AC69, // skin tone
    };

    private final List<Frame> frames = new ArrayList<>();
    private int activeFrameIndex;

    private int currentColorArgb = 0xFF000000; // opaque black
    private final List<Integer> recentColors = new ArrayList<>();
    private final List<ColorPalette> palettes = new ArrayList<>();

    private ToolType currentToolType = ToolType.PENCIL;
    private final Map<ToolType, Tool> tools = new EnumMap<>(ToolType.class);

    private boolean showPixelGrid = true;
    private boolean show16Guide = true;
    private boolean showTransparencyChecker = true;
    private boolean onionSkinEnabled = false;

    private double zoom = 8;
    private double camOffsetX;
    private double camOffsetY;

    private String currentFilePath;    // last PNG (single-frame) export/import path
    private String currentProjectPath; // last .pxproj project file path

    private boolean previewActive;
    private int previewX0, previewY0, previewX1, previewY1;

    private Rectangle selection; // pixel-space, active frame/layer; null = none

    private BufferedImage clipboardImage;
    private boolean pasteModeActive;
    private int pasteX, pasteY;

    private final List<Runnable> listeners = new ArrayList<>();

    public EditorState(int width, int height) {
        frames.add(new Frame(width, height));

        tools.put(ToolType.PENCIL, new PencilTool());
        tools.put(ToolType.ERASER, new EraserTool());
        tools.put(ToolType.FILL, new FillTool());
        tools.put(ToolType.EYEDROPPER, new EyedropperTool());
        tools.put(ToolType.LINE, new LineTool());
        tools.put(ToolType.SELECT, new SelectionTool());

        recentColors.add(0xFF000000);
        recentColors.add(0xFFFFFFFF);

        List<Integer> defaultColors = new ArrayList<>();
        for (int c : DEFAULT_PALETTE_COLORS) defaultColors.add(c);
        palettes.add(new ColorPalette("デフォルト", true, defaultColors));
    }

    // ---- listeners ----------------------------------------------------

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void fireChanged() {
        for (Runnable r : listeners) r.run();
    }

    // ---- frames -----------------------------------------------------------

    public List<Frame> getFrames() {
        return frames;
    }

    public int getActiveFrameIndex() {
        return activeFrameIndex;
    }

    public Frame getActiveFrame() {
        return frames.get(activeFrameIndex);
    }

    public void setActiveFrameIndex(int index) {
        activeFrameIndex = Math.max(0, Math.min(frames.size() - 1, index));
        selection = null;
        pasteModeActive = false;
        fireChanged();
    }

    public void addFrame() {
        Frame reference = getActiveFrame();
        frames.add(activeFrameIndex + 1, new Frame(reference.getWidth(), reference.getHeight()));
        activeFrameIndex++;
        selection = null;
        fireChanged();
    }

    public void duplicateFrame() {
        frames.add(activeFrameIndex + 1, getActiveFrame().duplicate());
        activeFrameIndex++;
        selection = null;
        fireChanged();
    }

    /** No-op if this is the only frame -- there's always at least one. */
    public void removeFrame() {
        if (frames.size() <= 1) return;
        frames.remove(activeFrameIndex);
        activeFrameIndex = Math.max(0, Math.min(frames.size() - 1, activeFrameIndex));
        selection = null;
        fireChanged();
    }

    public void moveFrame(int from, int to) {
        if (to < 0 || to >= frames.size()) return;
        Frame frame = frames.remove(from);
        frames.add(to, frame);
        activeFrameIndex = to;
        fireChanged();
    }

    /** The frame just before the active one, or null if the active frame is first -- used for onion skin. */
    public Frame getPreviousFrame() {
        return activeFrameIndex > 0 ? frames.get(activeFrameIndex - 1) : null;
    }

    // ---- layers (of the active frame) --------------------------------------

    public List<Layer> getLayers() {
        return getActiveFrame().getLayers();
    }

    public int getActiveLayerIndex() {
        return getActiveFrame().getActiveLayerIndex();
    }

    public void setActiveLayerIndex(int index) {
        getActiveFrame().setActiveLayerIndex(index);
        fireChanged();
    }

    public Layer getActiveLayer() {
        return getActiveFrame().getActiveLayer();
    }

    public void addLayer() {
        getActiveFrame().addLayer();
        fireChanged();
    }

    public void duplicateLayer(int index) {
        getActiveFrame().duplicateLayer(index);
        fireChanged();
    }

    public void removeLayer(int index) {
        getActiveFrame().removeLayer(index);
        fireChanged();
    }

    public void moveLayer(int from, int to) {
        getActiveFrame().moveLayer(from, to);
        fireChanged();
    }

    public void setLayerVisible(int index, boolean visible) {
        getLayers().get(index).setVisible(visible);
        fireChanged();
    }

    public void renameLayer(int index, String name) {
        getLayers().get(index).setName(name);
        fireChanged();
    }

    /** Every visible layer of the active frame, flattened into one image -- what's actually displayed/exported. */
    public BufferedImage getCompositeImage() {
        return getActiveFrame().composite();
    }

    // ---- canvas / history (of the active layer) --------------------------

    public PixelCanvas getCanvas() {
        return getActiveLayer().getCanvas();
    }

    public History getHistory() {
        return getActiveLayer().getHistory();
    }

    public void paintPixel(int x, int y, int argb) {
        PixelCanvas canvas = getCanvas();
        int before = canvas.getPixel(x, y);
        if (canvas.setPixel(x, y, argb)) {
            getHistory().recordChange(x, y, before, argb);
        }
    }

    public void beginStroke() {
        getHistory().beginStroke();
    }

    public void endStroke() {
        getHistory().endStroke();
        fireChanged();
    }

    public void undo() {
        getHistory().undo(getCanvas());
        fireChanged();
    }

    public void redo() {
        getHistory().redo(getCanvas());
        fireChanged();
    }

    public void newCanvas(int width, int height) {
        frames.clear();
        frames.add(new Frame(width, height));
        activeFrameIndex = 0;
        currentFilePath = null;
        currentProjectPath = null;
        selection = null;
        pasteModeActive = false;
        fireChanged();
    }

    /**
     * Resizes every frame's every layer to width x height, keeping existing
     * content positioned per the anchor (0.0/0.5/1.0 on each axis = left-or-
     * top / center / right-or-bottom, same convention as GIMP's Canvas Size).
     * Content that falls outside the new bounds is cropped away.
     *
     * This is a structural change like add/removeFrame/Layer -- not undoable,
     * and it clears every layer's history, since old undo strokes are pixel
     * coordinates against the old dimensions and would be meaningless (or
     * dangerous) to replay against the resized canvas.
     */
    public void resizeCanvas(int newWidth, int newHeight, double anchorX, double anchorY) {
        for (Frame frame : frames) {
            int oldWidth = frame.getWidth();
            int oldHeight = frame.getHeight();
            int offsetX = (int) Math.round((newWidth - oldWidth) * anchorX);
            int offsetY = (int) Math.round((newHeight - oldHeight) * anchorY);

            for (Layer layer : frame.getLayers()) {
                PixelCanvas canvas = layer.getCanvas();
                int[][] oldPixels = new int[oldWidth][oldHeight];
                for (int y = 0; y < oldHeight; y++) {
                    for (int x = 0; x < oldWidth; x++) {
                        oldPixels[x][y] = canvas.getPixel(x, y);
                    }
                }
                canvas.reset(newWidth, newHeight);
                for (int y = 0; y < oldHeight; y++) {
                    for (int x = 0; x < oldWidth; x++) {
                        int nx = x + offsetX;
                        int ny = y + offsetY;
                        if (canvas.inBounds(nx, ny)) {
                            canvas.setPixel(nx, ny, oldPixels[x][y]);
                        }
                    }
                }
                layer.getHistory().clear();
            }
        }
        selection = null;
        pasteModeActive = false;
        fireChanged();
    }

    /** Imports a plain PNG as a fresh single-frame, single-layer project. */
    public void loadFrom(BufferedImage image, String path) {
        frames.clear();
        Frame frame = new Frame(image.getWidth(), image.getHeight());
        frame.getActiveLayer().getCanvas().loadFrom(image);
        frames.add(frame);
        activeFrameIndex = 0;
        currentFilePath = path;
        currentProjectPath = null;
        selection = null;
        pasteModeActive = false;
        fireChanged();
    }

    /** For ProjectIO after loading a .pxproj: replaces the whole project (frames, layers, custom palettes). */
    public void replaceProject(List<Frame> newFrames, List<ColorPalette> customPalettes, String path) {
        frames.clear();
        frames.addAll(newFrames);
        activeFrameIndex = 0;
        palettes.removeIf(p -> !p.isBuiltIn());
        palettes.addAll(customPalettes);
        currentProjectPath = path;
        currentFilePath = null;
        selection = null;
        pasteModeActive = false;
        fireChanged();
    }

    public void setSavedPath(String path) {
        currentFilePath = path;
        fireChanged();
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public String getCurrentProjectPath() {
        return currentProjectPath;
    }

    public void setCurrentProjectPath(String path) {
        currentProjectPath = path;
        fireChanged();
    }

    // ---- selection --------------------------------------------------------

    public void setSelection(Rectangle rect) {
        selection = rect;
        fireChanged();
    }

    public Rectangle getSelection() {
        return selection;
    }

    public boolean hasSelection() {
        return selection != null;
    }

    public void clearSelection() {
        selection = null;
        fireChanged();
    }

    public void selectAll() {
        PixelCanvas canvas = getCanvas();
        setSelection(new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight()));
    }

    /** Fills the selection with fully transparent pixels, as one undo step. No-op with no selection. */
    public void deleteSelection() {
        if (selection == null) return;
        beginStroke();
        for (int dy = 0; dy < selection.height; dy++) {
            for (int dx = 0; dx < selection.width; dx++) {
                paintPixel(selection.x + dx, selection.y + dy, 0);
            }
        }
        endStroke();
    }

    /** Fills the selection with the current color, as one undo step. No-op with no selection. */
    public void fillSelection() {
        if (selection == null) return;
        int argb = currentColorArgb;
        beginStroke();
        for (int dy = 0; dy < selection.height; dy++) {
            for (int dx = 0; dx < selection.width; dx++) {
                paintPixel(selection.x + dx, selection.y + dy, argb);
            }
        }
        endStroke();
    }

    // ---- clipboard / paste --------------------------------------------------

    /** Copies the selected pixels from the active layer into the clipboard. No-op with no selection. */
    public void copySelection() {
        if (selection == null) return;
        PixelCanvas canvas = getCanvas();
        BufferedImage copy = new BufferedImage(selection.width, selection.height, BufferedImage.TYPE_INT_ARGB);
        for (int dy = 0; dy < selection.height; dy++) {
            for (int dx = 0; dx < selection.width; dx++) {
                copy.setRGB(dx, dy, canvas.getPixel(selection.x + dx, selection.y + dy));
            }
        }
        clipboardImage = copy;
        fireChanged();
    }

    public void cutSelection() {
        copySelection();
        deleteSelection();
    }

    public boolean hasClipboard() {
        return clipboardImage != null;
    }

    public BufferedImage getClipboardImage() {
        return clipboardImage;
    }

    /** Enters "floating paste" mode: the clipboard image follows the cursor until a click commits it (or Esc cancels). */
    public void beginPaste() {
        if (clipboardImage == null) return;
        pasteModeActive = true;
        pasteX = selection != null ? selection.x : 0;
        pasteY = selection != null ? selection.y : 0;
        fireChanged();
    }

    public boolean isPasteModeActive() {
        return pasteModeActive;
    }

    /** Moves the floating paste without firing listeners -- CanvasPanel repaints itself directly while dragging. */
    public void updatePastePosition(int x, int y) {
        pasteX = x;
        pasteY = y;
    }

    public int getPasteX() {
        return pasteX;
    }

    public int getPasteY() {
        return pasteY;
    }

    /** Stamps the clipboard onto the active layer at the current paste position, as one undo step. */
    public void commitPaste() {
        if (!pasteModeActive || clipboardImage == null) return;
        beginStroke();
        int w = clipboardImage.getWidth();
        int h = clipboardImage.getHeight();
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                paintPixel(pasteX + dx, pasteY + dy, clipboardImage.getRGB(dx, dy));
            }
        }
        endStroke();
        pasteModeActive = false;
        fireChanged();
    }

    public void cancelPaste() {
        if (!pasteModeActive) return;
        pasteModeActive = false;
        fireChanged();
    }

    // ---- flip (selection if present, else the whole active layer) -----------

    public void flipHorizontal() {
        flip(true);
    }

    public void flipVertical() {
        flip(false);
    }

    private void flip(boolean horizontal) {
        Rectangle r = selection != null ? selection : new Rectangle(0, 0, getCanvas().getWidth(), getCanvas().getHeight());
        PixelCanvas canvas = getCanvas();
        int[][] snapshot = new int[r.width][r.height];
        for (int dx = 0; dx < r.width; dx++) {
            for (int dy = 0; dy < r.height; dy++) {
                snapshot[dx][dy] = canvas.getPixel(r.x + dx, r.y + dy);
            }
        }
        beginStroke();
        for (int dx = 0; dx < r.width; dx++) {
            for (int dy = 0; dy < r.height; dy++) {
                int destDx = horizontal ? (r.width - 1 - dx) : dx;
                int destDy = horizontal ? dy : (r.height - 1 - dy);
                paintPixel(r.x + destDx, r.y + destDy, snapshot[dx][dy]);
            }
        }
        endStroke();
    }

    // ---- onion skin ---------------------------------------------------------

    public boolean isOnionSkinEnabled() {
        return onionSkinEnabled;
    }

    public void setOnionSkinEnabled(boolean v) {
        onionSkinEnabled = v;
        fireChanged();
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

    // ---- palettes -----------------------------------------------------------

    public List<ColorPalette> getPalettes() {
        return palettes;
    }

    public void addPalette(ColorPalette palette) {
        palettes.add(palette);
        fireChanged();
    }

    public void removePalette(ColorPalette palette) {
        if (palette.isBuiltIn()) return;
        palettes.remove(palette);
        fireChanged();
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

    public double getZoom() {
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

    public void setZoom(double newZoom, double anchorX, double anchorY) {
        double clamped = Math.max(ZOOM_STEPS[0], Math.min(ZOOM_STEPS[ZOOM_STEPS.length - 1], newZoom));
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
        int idx = indexOfZoomStep(zoom);
        if (idx >= 0) {
            idx = Math.max(0, Math.min(ZOOM_STEPS.length - 1, idx + direction));
        } else {
            // Current zoom (e.g. from fitting a just-loaded image to the
            // viewport) doesn't sit exactly on a step -- move to the nearest
            // step in the requested direction instead of snapping to an end.
            idx = nearestStepIndex(direction);
        }
        setZoom(ZOOM_STEPS[idx], anchorX, anchorY);
    }

    private int indexOfZoomStep(double z) {
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            if (ZOOM_STEPS[i] == z) return i;
        }
        return -1;
    }

    private int nearestStepIndex(int direction) {
        if (direction > 0) {
            for (int i = 0; i < ZOOM_STEPS.length; i++) {
                if (ZOOM_STEPS[i] > zoom) return i;
            }
            return ZOOM_STEPS.length - 1;
        } else {
            for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
                if (ZOOM_STEPS[i] < zoom) return i;
            }
            return 0;
        }
    }

    /**
     * Centers the canvas within a viewport of the given size (0x0 = just
     * reset to origin), first fitting the zoom to the largest step at which
     * the whole image fits inside the viewport -- so opening or creating a
     * large image is never left showing only a corner of it.
     */
    public void centerCamera(double viewportWidth, double viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            camOffsetX = 0;
            camOffsetY = 0;
            return;
        }
        fitZoomToViewport(viewportWidth, viewportHeight);
        camOffsetX = (viewportWidth - getCanvas().getWidth() * zoom) / 2.0;
        camOffsetY = (viewportHeight - getCanvas().getHeight() * zoom) / 2.0;
    }

    /** Sets zoom to the largest step that fits the active canvas entirely inside the given viewport. */
    private void fitZoomToViewport(double viewportWidth, double viewportHeight) {
        PixelCanvas canvas = getCanvas();
        double rawFit = Math.min(viewportWidth / canvas.getWidth(), viewportHeight / canvas.getHeight());
        double best = ZOOM_STEPS[0];
        for (double step : ZOOM_STEPS) {
            if (step <= rawFit) best = step;
        }
        zoom = best;
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
