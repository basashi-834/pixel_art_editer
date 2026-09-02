#include "FillTool.h"

#include <vector>

#include "App.h"
#include "Canvas.h"

void FillTool::OnMouseDown(App& app, int x, int y) {
    Canvas& canvas = app.GetCanvas();
    if (!canvas.InBounds(x, y)) return;

    Color target = canvas.GetPixel(x, y);
    Color fillColor = app.GetCurrentColor();
    if (target == fillColor) return;

    app.BeginStroke();

    std::vector<bool> visited(static_cast<size_t>(canvas.GetWidth()) * canvas.GetHeight(), false);
    auto index = [&](int px, int py) { return static_cast<size_t>(py) * canvas.GetWidth() + px; };

    std::vector<std::pair<int, int>> stack;
    stack.push_back({x, y});
    visited[index(x, y)] = true;

    while (!stack.empty()) {
        auto [cx, cy] = stack.back();
        stack.pop_back();

        app.PaintPixel(cx, cy, fillColor);

        const int nx[4] = {cx + 1, cx - 1, cx, cx};
        const int ny[4] = {cy, cy, cy + 1, cy - 1};
        for (int i = 0; i < 4; ++i) {
            int px = nx[i];
            int py = ny[i];
            if (!canvas.InBounds(px, py)) continue;
            size_t idx = index(px, py);
            if (visited[idx]) continue;
            if (canvas.GetPixel(px, py) != target) continue;
            visited[idx] = true;
            stack.push_back({px, py});
        }
    }

    app.EndStroke();
}

void FillTool::OnMouseDrag(App&, int, int) {
    // Fill does not repeat while dragging.
}

void FillTool::OnMouseUp(App&, int, int) {}
