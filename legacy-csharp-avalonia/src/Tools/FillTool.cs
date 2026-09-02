using PixelSpriteEditor.Models;

namespace PixelSpriteEditor.Tools;

/// <summary>Flood fill (4-directional) from the clicked pixel. The whole fill is one undo step.</summary>
public sealed class FillTool : ITool
{
    public void OnPointerDown(IEditorContext ctx, int x, int y)
    {
        var canvas = ctx.Canvas;
        if (!canvas.InBounds(x, y)) return;

        PixelColor target = canvas.GetPixel(x, y);
        PixelColor fillColor = ctx.CurrentColor;
        if (target == fillColor) return;

        ctx.BeginStroke();

        var visited = new bool[canvas.Width * canvas.Height];
        var stack = new Stack<(int X, int Y)>();
        stack.Push((x, y));
        visited[y * canvas.Width + x] = true;

        Span<int> dx = stackalloc int[4] { 1, -1, 0, 0 };
        Span<int> dy = stackalloc int[4] { 0, 0, 1, -1 };

        while (stack.Count > 0)
        {
            var (cx, cy) = stack.Pop();
            ctx.PaintPixel(cx, cy, fillColor);

            for (int i = 0; i < 4; i++)
            {
                int px = cx + dx[i], py = cy + dy[i];
                if (!canvas.InBounds(px, py)) continue;
                int idx = py * canvas.Width + px;
                if (visited[idx]) continue;
                if (canvas.GetPixel(px, py) != target) continue;
                visited[idx] = true;
                stack.Push((px, py));
            }
        }

        ctx.EndStroke();
    }

    public void OnPointerMove(IEditorContext ctx, int x, int y) { }
    public void OnPointerUp(IEditorContext ctx, int x, int y) { }
}
