#pragma once

class App;

// Common interface for every drawing tool. Coordinates are already
// converted to canvas pixel space by Input before a tool sees them, and
// are guaranteed to be in-bounds.
class Tool {
public:
    virtual ~Tool() = default;

    virtual void OnMouseDown(App& app, int x, int y) = 0;
    virtual void OnMouseDrag(App& app, int x, int y) = 0;
    virtual void OnMouseUp(App& app, int x, int y) = 0;
};
