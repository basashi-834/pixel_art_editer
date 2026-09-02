#pragma once

#include "Tool.h"

// Same drag/stroke behaviour as PencilTool but always paints fully
// transparent pixels, regardless of the current selected color.
class EraserTool : public Tool {
public:
    void OnMouseDown(App& app, int x, int y) override;
    void OnMouseDrag(App& app, int x, int y) override;
    void OnMouseUp(App& app, int x, int y) override;

private:
    int lastX_ = 0;
    int lastY_ = 0;
    bool hasLast_ = false;
};
