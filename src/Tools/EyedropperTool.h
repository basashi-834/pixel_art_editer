#pragma once

#include "Tool.h"

// Picks the color under the cursor and makes it the current color.
class EyedropperTool : public Tool {
public:
    void OnMouseDown(App& app, int x, int y) override;
    void OnMouseDrag(App& app, int x, int y) override;
    void OnMouseUp(App& app, int x, int y) override;
};
