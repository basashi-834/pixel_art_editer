#pragma once

#include "Tool.h"

// Flood fill (4-directional) starting from the clicked pixel. The whole
// fill is a single undo step; dragging does not repeat it.
class FillTool : public Tool {
public:
    void OnMouseDown(App& app, int x, int y) override;
    void OnMouseDrag(App& app, int x, int y) override;
    void OnMouseUp(App& app, int x, int y) override;
};
