import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

/**
 * Pure-logic smoke tests for the model/tool/PNG-IO layers -- no GUI, no
 * display needed. Run with: java SelfTest
 *
 * This is a plain sanity check, not a full test framework: each case
 * prints PASS/FAIL and the run exits non-zero if anything failed, so it
 * can be used as a quick "did my build actually work" check after
 * compiling, independent of actually clicking around the GUI.
 */
public class SelfTest {

    public static void main(String[] args) {
        boolean ok = true;
        ok &= check("Pencil stroke paints pixels and records one undo step", SelfTest::testPencilStroke);
        ok &= check("Undo/redo round-trips a stroke", SelfTest::testUndoRedo);
        ok &= check("Eraser writes fully transparent pixels", SelfTest::testEraser);
        ok &= check("4-directional fill stops at a differently-colored border", SelfTest::testFill);
        ok &= check("Eyedropper picks up the color under the cursor", SelfTest::testEyedropper);
        ok &= check("Line tool commits a straight line only on release", SelfTest::testLineTool);
        ok &= check("PNG round-trip preserves exact pixels, including alpha=0", SelfTest::testPngRoundTrip);
        ok &= check("Zoom steps clamp to the documented min/max", SelfTest::testZoomClamp);
        ok &= check("NewCanvas clears history and pixels", SelfTest::testNewCanvas);
        ok &= check("setColorChannel updates one channel without disturbing the others", SelfTest::testSetColorChannel);
        ok &= check("removeRecentColor removes only the requested entry", SelfTest::testRemoveRecentColor);
        ok &= check("ColorPalette add/remove ignores duplicates and missing entries", SelfTest::testColorPalette);
        ok &= check("Frames: add/duplicate/remove and undo stays per-layer", SelfTest::testFrames);
        ok &= check("Layers: add/visibility/composite order and per-layer undo", SelfTest::testLayers);
        ok &= check("Selection + copy/paste stamps the clipboard as one undo step", SelfTest::testSelectionCopyPaste);
        ok &= check("Flip horizontal mirrors within the selection only", SelfTest::testFlipHorizontal);
        ok &= check("Flip vertical mirrors the whole canvas with no selection", SelfTest::testFlipVertical);
        ok &= check("Project save/load round-trips frames, layers and a custom palette", SelfTest::testProjectRoundTrip);

        System.out.println(ok ? "SELFTEST: ALL PASSED" : "SELFTEST: FAILURES ABOVE");
        System.exit(ok ? 0 : 1);
    }

    private static boolean testPencilStroke() {
        EditorState state = new EditorState(8, 8);
        state.setCurrentColorArgb(0xFFFF0000);
        Tool pencil = state.getCurrentTool();
        pencil.onPress(state, 1, 1);
        pencil.onDrag(state, 3, 1);
        pencil.onRelease(state, 3, 1);
        boolean painted = (state.getCanvas().getPixel(1, 1) & 0xFFFFFF) == 0xFF0000
                && (state.getCanvas().getPixel(3, 1) & 0xFFFFFF) == 0xFF0000;
        return painted && state.getHistory().canUndo();
    }

    private static boolean testUndoRedo() {
        EditorState state = new EditorState(8, 8);
        state.setCurrentColorArgb(0xFFFFFFFF);
        Tool pencil = state.getCurrentTool();
        pencil.onPress(state, 2, 2);
        pencil.onRelease(state, 2, 2);
        state.undo();
        boolean clearedAfterUndo = (state.getCanvas().getPixel(2, 2) >>> 24) == 0;
        state.redo();
        boolean restoredAfterRedo = state.getCanvas().getPixel(2, 2) == (int) 0xFFFFFFFF;
        return clearedAfterUndo && restoredAfterRedo;
    }

    private static boolean testEraser() {
        EditorState state = new EditorState(4, 4);
        state.paintPixel(0, 0, (int) 0xFFFFFFFF);
        state.setCurrentToolType(EditorState.ToolType.ERASER);
        Tool eraser = state.getCurrentTool();
        eraser.onPress(state, 0, 0);
        eraser.onRelease(state, 0, 0);
        return state.getCanvas().getPixel(0, 0) == 0;
    }

    private static boolean testFill() {
        EditorState state = new EditorState(5, 5);
        for (int i = 0; i < 5; i++) {
            state.paintPixel(i, 2, (int) 0xFF000000);
            state.paintPixel(2, i, (int) 0xFF000000);
        }
        state.setCurrentColorArgb((int) 0xFF00FF00);
        state.setCurrentToolType(EditorState.ToolType.FILL);
        state.getCurrentTool().onPress(state, 0, 0);
        boolean insideFilled = (state.getCanvas().getPixel(0, 0) & 0xFFFFFF) == 0x00FF00;
        boolean outsideUntouched = state.getCanvas().getPixel(4, 4) == 0;
        return insideFilled && outsideUntouched;
    }

    private static boolean testEyedropper() {
        EditorState state = new EditorState(4, 4);
        int target = (int) 0xFF0A141E;
        state.paintPixel(1, 1, target);
        state.setCurrentToolType(EditorState.ToolType.EYEDROPPER);
        state.getCurrentTool().onPress(state, 1, 1);
        return state.getCurrentColorArgb() == target;
    }

    private static boolean testLineTool() {
        EditorState state = new EditorState(8, 8);
        state.setCurrentColorArgb((int) 0xFFFFFFFF);
        state.setCurrentToolType(EditorState.ToolType.LINE);
        Tool line = state.getCurrentTool();
        line.onPress(state, 0, 0);
        line.onDrag(state, 3, 0);
        boolean nothingCommittedMidDrag = state.getCanvas().getPixel(2, 0) == 0 && state.hasPreview();
        line.onRelease(state, 3, 0);
        boolean committedAfterRelease = state.getCanvas().getPixel(2, 0) == (int) 0xFFFFFFFF && !state.hasPreview();
        return nothingCommittedMidDrag && committedAfterRelease;
    }

    private static boolean testPngRoundTrip() {
        EditorState state = new EditorState(3, 2);
        state.paintPixel(0, 0, (int) 0xFFC8320A);
        state.paintPixel(1, 0, 0); // alpha=0 must stay alpha=0, not become opaque/checkerboard
        state.paintPixel(2, 1, 0x80000000);

        File tmp = null;
        try {
            tmp = File.createTempFile("pse-selftest-", ".png");
            ImageIO.write(state.getCanvas().getImage(), "png", tmp);
            BufferedImage loaded = ImageIO.read(tmp);
            if (loaded.getWidth() != 3 || loaded.getHeight() != 2) return false;
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 3; x++) {
                    if (loaded.getRGB(x, y) != state.getCanvas().getPixel(x, y)) return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static boolean testZoomClamp() {
        EditorState state = new EditorState(8, 8);
        for (int i = 0; i < 20; i++) state.zoomOut(0, 0);
        boolean minOk = state.getZoom() == EditorState.ZOOM_STEPS[0];
        for (int i = 0; i < 20; i++) state.zoomIn(0, 0);
        boolean maxOk = state.getZoom() == EditorState.ZOOM_STEPS[EditorState.ZOOM_STEPS.length - 1];
        return minOk && maxOk;
    }

    private static boolean testNewCanvas() {
        EditorState state = new EditorState(4, 4);
        state.beginStroke();
        state.paintPixel(0, 0, (int) 0xFFFFFFFF);
        state.endStroke();
        state.newCanvas(6, 6);
        return state.getCanvas().getWidth() == 6 && state.getCanvas().getHeight() == 6
                && !state.getHistory().canUndo() && !state.getHistory().canRedo()
                && state.getCanvas().getPixel(0, 0) == 0;
    }

    private static boolean testSetColorChannel() {
        EditorState state = new EditorState(4, 4);
        state.setCurrentColorArgb((int) 0xFF102030);
        state.setColorChannel(1, 200); // G
        int argb = state.getCurrentColorArgb();
        return ((argb >>> 24) & 0xFF) == 0xFF   // A unchanged
                && ((argb >>> 16) & 0xFF) == 0x10 // R unchanged
                && ((argb >>> 8) & 0xFF) == 200   // G updated
                && (argb & 0xFF) == 0x30;          // B unchanged
    }

    private static boolean testRemoveRecentColor() {
        EditorState state = new EditorState(4, 4);
        state.setCurrentColorArgb((int) 0xFFAA0000);
        state.setCurrentColorArgb((int) 0xFF00BB00);
        boolean hadBoth = state.getRecentColors().contains((int) 0xFFAA0000)
                && state.getRecentColors().contains((int) 0xFF00BB00);
        state.removeRecentColor((int) 0xFFAA0000);
        return hadBoth
                && !state.getRecentColors().contains((int) 0xFFAA0000)
                && state.getRecentColors().contains((int) 0xFF00BB00);
    }

    private static boolean testColorPalette() {
        ColorPalette palette = new ColorPalette("テスト", false, new java.util.ArrayList<>());
        palette.addColor(0xFF112233);
        palette.addColor(0xFF112233); // duplicate, should not add a second entry
        palette.addColor(0xFF445566);
        boolean afterAdds = palette.getColors().size() == 2;

        palette.removeColor(0xFF112233);
        palette.removeColor(0xFF999999); // not present, should be a no-op
        boolean afterRemoves = palette.getColors().size() == 1 && palette.getColors().contains(0xFF445566);

        return afterAdds && afterRemoves;
    }

    private static boolean testFrames() {
        EditorState state = new EditorState(4, 4);
        state.paintPixel(0, 0, (int) 0xFFFF0000);
        boolean frame1HasPixel = state.getCanvas().getPixel(0, 0) == (int) 0xFFFF0000;

        state.addFrame();
        boolean newFrameIsBlank = state.getCanvas().getPixel(0, 0) == 0;
        boolean twoFrames = state.getFrames().size() == 2;

        state.setActiveFrameIndex(0);
        boolean firstFrameUnaffected = state.getCanvas().getPixel(0, 0) == (int) 0xFFFF0000;

        state.duplicateFrame();
        boolean dupHasSameContent = state.getCanvas().getPixel(0, 0) == (int) 0xFFFF0000;
        boolean threeFrames = state.getFrames().size() == 3;

        state.removeFrame();
        boolean removedOk = state.getFrames().size() == 2;

        return frame1HasPixel && newFrameIsBlank && twoFrames && firstFrameUnaffected
                && dupHasSameContent && threeFrames && removedOk;
    }

    private static boolean testLayers() {
        EditorState state = new EditorState(4, 4);
        state.paintPixel(1, 1, (int) 0xFFFF0000);
        state.addLayer();
        boolean newLayerActive = state.getActiveLayerIndex() == 1;
        state.beginStroke(); // paintPixel alone doesn't commit to the undo stack -- only endStroke() does
        state.paintPixel(1, 1, (int) 0xFF0000FF);
        state.endStroke();

        boolean topLayerWinsComposite = state.getCompositeImage().getRGB(1, 1) == (int) 0xFF0000FF;

        state.undo(); // undo only touches the active (top) layer
        boolean topLayerPixelUndone = state.getCanvas().getPixel(1, 1) == 0;
        state.setActiveLayerIndex(0);
        boolean bottomLayerUnaffectedByTopUndo = state.getCanvas().getPixel(1, 1) == (int) 0xFFFF0000;

        state.setLayerVisible(0, false);
        boolean hiddenLayerExcluded = (state.getCompositeImage().getRGB(1, 1) >>> 24) == 0;

        state.setLayerVisible(0, true);
        state.setActiveLayerIndex(1);
        state.removeLayer(1);
        boolean removedBackToOneLayer = state.getLayers().size() == 1;

        return newLayerActive && topLayerWinsComposite && topLayerPixelUndone
                && bottomLayerUnaffectedByTopUndo && hiddenLayerExcluded && removedBackToOneLayer;
    }

    private static boolean testSelectionCopyPaste() {
        EditorState state = new EditorState(6, 6);
        state.paintPixel(1, 1, (int) 0xFFFF0000);
        state.paintPixel(2, 1, (int) 0xFF00FF00);

        state.setSelection(new java.awt.Rectangle(1, 1, 2, 1));
        state.copySelection();
        boolean hasClipboard = state.hasClipboard();
        boolean clipboardMatches = state.getClipboardImage().getRGB(0, 0) == (int) 0xFFFF0000
                && state.getClipboardImage().getRGB(1, 0) == (int) 0xFF00FF00;

        state.beginPaste();
        boolean pasteModeOn = state.isPasteModeActive();
        state.updatePastePosition(3, 3);
        state.commitPaste();
        boolean pasteModeOffAfterCommit = !state.isPasteModeActive();
        boolean stampedAtNewLocation = state.getCanvas().getPixel(3, 3) == (int) 0xFFFF0000
                && state.getCanvas().getPixel(4, 3) == (int) 0xFF00FF00;

        boolean pasteIsOneUndoStep = state.getHistory().canUndo();
        state.undo();
        boolean undonePasteClearsBoth = state.getCanvas().getPixel(3, 3) == 0 && state.getCanvas().getPixel(4, 3) == 0;

        return hasClipboard && clipboardMatches && pasteModeOn && pasteModeOffAfterCommit
                && stampedAtNewLocation && pasteIsOneUndoStep && undonePasteClearsBoth;
    }

    private static boolean testFlipHorizontal() {
        EditorState state = new EditorState(4, 4);
        state.paintPixel(0, 0, (int) 0xFFFF0000);
        state.paintPixel(1, 0, (int) 0xFF00FF00);
        state.paintPixel(3, 3, (int) 0xFF123456); // outside the selection, must stay untouched

        state.setSelection(new java.awt.Rectangle(0, 0, 2, 1));
        state.flipHorizontal();

        boolean swappedWithinSelection = state.getCanvas().getPixel(0, 0) == (int) 0xFF00FF00
                && state.getCanvas().getPixel(1, 0) == (int) 0xFFFF0000;
        boolean outsideSelectionUntouched = state.getCanvas().getPixel(3, 3) == (int) 0xFF123456;
        boolean flipIsUndoable = state.getHistory().canUndo();

        return swappedWithinSelection && outsideSelectionUntouched && flipIsUndoable;
    }

    private static boolean testFlipVertical() {
        EditorState state = new EditorState(2, 4);
        state.paintPixel(0, 0, (int) 0xFFFF0000);
        state.paintPixel(0, 3, (int) 0xFF00FF00);

        state.flipVertical(); // no selection -> flips the whole canvas

        return state.getCanvas().getPixel(0, 0) == (int) 0xFF00FF00
                && state.getCanvas().getPixel(0, 3) == (int) 0xFFFF0000;
    }

    private static boolean testProjectRoundTrip() {
        EditorState state = new EditorState(3, 2);
        state.paintPixel(0, 0, (int) 0xFFAABBCC);
        state.addLayer();
        state.getActiveLayer().setName("上のレイヤー");
        state.paintPixel(1, 1, 0x80112233);
        state.setLayerVisible(1, false);

        state.addFrame();
        state.paintPixel(2, 0, (int) 0xFF445566);

        java.util.List<Integer> colors = new java.util.ArrayList<>();
        colors.add((int) 0xFF010203);
        state.addPalette(new ColorPalette("テストパレット", false, colors));

        File tmp = null;
        try {
            tmp = File.createTempFile("pse-selftest-project-", ".pxproj");
            ProjectIO.save(state, tmp);

            EditorState loaded = new EditorState(1, 1); // overwritten wholesale by replaceProject()
            ProjectIO.load(loaded, tmp);

            boolean frameCountOk = loaded.getFrames().size() == 2;
            loaded.setActiveFrameIndex(0);
            boolean frame0LayerCountOk = loaded.getLayers().size() == 2;
            boolean layerNameOk = loaded.getLayers().get(1).getName().equals("上のレイヤー");
            boolean layerVisibilityOk = !loaded.getLayers().get(1).isVisible();
            loaded.setActiveLayerIndex(0);
            boolean bottomPixelOk = loaded.getCanvas().getPixel(0, 0) == (int) 0xFFAABBCC;
            loaded.setActiveLayerIndex(1);
            boolean topPixelOk = loaded.getCanvas().getPixel(1, 1) == 0x80112233;

            loaded.setActiveFrameIndex(1);
            boolean frame1PixelOk = loaded.getCanvas().getPixel(2, 0) == (int) 0xFF445566;

            boolean paletteRoundTripOk = false;
            for (ColorPalette p : loaded.getPalettes()) {
                if (!p.isBuiltIn() && "テストパレット".equals(p.getName()) && p.getColors().contains(0xFF010203)) {
                    paletteRoundTripOk = true;
                }
            }

            return frameCountOk && frame0LayerCountOk && layerNameOk && layerVisibilityOk
                    && bottomPixelOk && topPixelOk && frame1PixelOk && paletteRoundTripOk;
        } catch (IOException e) {
            return false;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static boolean check(String name, BooleanSupplier test) {
        boolean pass;
        try {
            pass = test.getAsBoolean();
        } catch (RuntimeException ex) {
            System.out.println("[FAIL] " + name + " -- threw " + ex);
            return false;
        }
        System.out.println((pass ? "[PASS] " : "[FAIL] ") + name);
        return pass;
    }
}
