import java.util.ArrayDeque;
import java.util.Deque;

/** Flood fill (4-directional) from the clicked pixel. The whole fill is one undo step. */
public class FillTool implements Tool {

    @Override
    public void onPress(EditorState state, int x, int y) {
        PixelCanvas canvas = state.getCanvas();
        if (!canvas.inBounds(x, y)) return;

        int target = canvas.getPixel(x, y);
        int fillColor = state.getCurrentColorArgb();
        if (target == fillColor) return;

        state.beginStroke();

        int width = canvas.getWidth();
        boolean[] visited = new boolean[width * canvas.getHeight()];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {x, y});
        visited[y * width + x] = true;

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int cx = p[0], cy = p[1];
            state.paintPixel(cx, cy, fillColor);

            for (int i = 0; i < 4; i++) {
                int px = cx + dx[i];
                int py = cy + dy[i];
                if (!canvas.inBounds(px, py)) continue;
                int idx = py * width + px;
                if (visited[idx]) continue;
                if (canvas.getPixel(px, py) != target) continue;
                visited[idx] = true;
                stack.push(new int[] {px, py});
            }
        }

        state.endStroke();
    }

    @Override
    public void onDrag(EditorState state, int x, int y) {
    }

    @Override
    public void onRelease(EditorState state, int x, int y) {
    }
}
