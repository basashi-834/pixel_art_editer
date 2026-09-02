#pragma once

#include <cstdint>
#include <vector>

#include "Color.h"

// The real, low-resolution pixel data for the sprite being edited.
// This is the only "true" image — everything the Renderer draws at 8x/16x
// zoom is a scaled *view* of this buffer, never the other way around.
class Canvas {
public:
    Canvas() : Canvas(16, 16) {}
    Canvas(int width, int height);

    void Reset(int width, int height);  // New canvas, fully transparent

    int GetWidth() const { return width_; }
    int GetHeight() const { return height_; }

    bool InBounds(int x, int y) const {
        return x >= 0 && y >= 0 && x < width_ && y < height_;
    }

    Color GetPixel(int x, int y) const;
    // Returns true if the pixel actually changed.
    bool SetPixel(int x, int y, Color c);

    const std::vector<Color>& Pixels() const { return pixels_; }
    std::vector<Color>& Pixels() { return pixels_; }

    // Replace the whole buffer (used by ImageIO on load). Sizes must match
    // width/height already set on this canvas.
    void SetAllPixels(std::vector<Color> pixels);

private:
    int width_ = 16;
    int height_ = 16;
    std::vector<Color> pixels_;  // row-major, size width_*height_
};
