#include "EyedropperTool.h"

#include "App.h"

void EyedropperTool::OnMouseDown(App& app, int x, int y) {
    if (!app.GetCanvas().InBounds(x, y)) return;
    app.SetCurrentColor(app.GetCanvas().GetPixel(x, y));
}

void EyedropperTool::OnMouseDrag(App& app, int x, int y) { OnMouseDown(app, x, y); }

void EyedropperTool::OnMouseUp(App&, int, int) {}
