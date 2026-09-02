/** Picks the color under the cursor and makes it the current color. */
public class EyedropperTool implements Tool {

    @Override
    public void onPress(EditorState state, int x, int y) {
        if (!state.getCanvas().inBounds(x, y)) return;
        state.setCurrentColorArgb(state.getCanvas().getPixel(x, y));
    }

    @Override
    public void onDrag(EditorState state, int x, int y) {
        onPress(state, x, y);
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
    }
}
