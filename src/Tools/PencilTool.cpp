#include "PencilTool.h"

#include "App.h"
#include "LineUtil.h"

void PencilTool::OnMouseDown(App& app, int x, int y) {
    app.BeginStroke();
    app.PaintPixel(x, y, app.GetCurrentColor());
    lastX_ = x;
    lastY_ = y;
    hasLast_ = true;
}

void PencilTool::OnMouseDrag(App& app, int x, int y) {
    if (!hasLast_) {
        lastX_ = x;
        lastY_ = y;
        hasLast_ = true;
    }
    Color c = app.GetCurrentColor();
    ForEachLinePixel(lastX_, lastY_, x, y, [&](int px, int py) { app.PaintPixel(px, py, c); });
    lastX_ = x;
    lastY_ = y;
}

void PencilTool::OnMouseUp(App& app, int /*x*/, int /*y*/) {
    app.EndStroke();
    hasLast_ = false;
}
