#pragma once

#include "Tool.h"

// Freehand drawing. A drag is treated as one continuous stroke (one undo
// step) and gaps between fast mouse movements are filled with a line so no
// pixels are skipped.
class PencilTool : public Tool {
public:
    void OnMouseDown(App& app, int x, int y) override;
    void OnMouseDrag(App& app, int x, int y) override;
    void OnMouseUp(App& app, int x, int y) override;

private:
    int lastX_ = 0;
    int lastY_ = 0;
    bool hasLast_ = false;
};
