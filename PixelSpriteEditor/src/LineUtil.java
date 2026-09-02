/**
 * Bresenham line walk shared by Pencil/Eraser (so a fast drag doesn't leave
 * gaps between sampled pointer positions) and LineTool (to commit its
 * preview as actual pixels). Every pixel between the two endpoints is
 * visited exactly once, including both endpoints.
 */
public final class LineUtil {

    /** Small callback interface instead of BiConsumer&lt;Integer,Integer&gt; to avoid boxing every pixel. */
    public interface PixelVisitor {
        void visit(int x, int y);
    }

    private LineUtil() {
    }

    public static void forEachLinePixel(int x0, int y0, int x1, int y1, PixelVisitor visitor) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int x = x0, y = y0;
        while (true) {
            visitor.visit(x, y);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
