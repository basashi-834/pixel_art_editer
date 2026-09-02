#include "EraserTool.h"

#include "App.h"
#include "LineUtil.h"

namespace {
constexpr Color kTransparent{0, 0, 0, 0};
}

void EraserTool::OnMouseDown(App& app, int x, int y) {
    app.BeginStroke();
    app.PaintPixel(x, y, kTransparent);
    lastX_ = x;
    lastY_ = y;
    hasLast_ = true;
}

void EraserTool::OnMouseDrag(App& app, int x, int y) {
    if (!hasLast_) {
        lastX_ = x;
        lastY_ = y;
        hasLast_ = true;
    }
    ForEachLinePixel(lastX_, lastY_, x, y, [&](int px, int py) { app.PaintPixel(px, py, kTransparent); });
    lastX_ = x;
    lastY_ = y;
}

void EraserTool::OnMouseUp(App& app, int /*x*/, int /*y*/) {
    app.EndStroke();
    hasLast_ = false;
}
