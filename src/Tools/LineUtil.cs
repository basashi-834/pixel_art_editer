namespace PixelSpriteEditor.Tools;

/// <summary>
/// Bresenham line walk shared by Pencil/Eraser so a fast drag doesn't leave
/// gaps between sampled pointer positions -- every pixel between the last
/// and current position gets visited exactly once.
/// </summary>
public static class LineUtil
{
    public static void ForEachLinePixel(int x0, int y0, int x1, int y1, Action<int, int> visit)
    {
        int dx = Math.Abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.Abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int x = x0, y = y0;
        while (true)
        {
            visit(x, y);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }
}
