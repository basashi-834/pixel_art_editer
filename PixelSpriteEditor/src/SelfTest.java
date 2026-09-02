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
