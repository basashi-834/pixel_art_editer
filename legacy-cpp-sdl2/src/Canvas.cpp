#include "Canvas.h"

#include <utility>

Canvas::Canvas(int width, int height) { Reset(width, height); }

void Canvas::Reset(int width, int height) {
    width_ = width;
    height_ = height;
    pixels_.assign(static_cast<size_t>(width_) * static_cast<size_t>(height_), Color{0, 0, 0, 0});
}

Color Canvas::GetPixel(int x, int y) const {
    if (!InBounds(x, y)) return Color{0, 0, 0, 0};
    return pixels_[static_cast<size_t>(y) * width_ + x];
}

bool Canvas::SetPixel(int x, int y, Color c) {
    if (!InBounds(x, y)) return false;
    Color& slot = pixels_[static_cast<size_t>(y) * width_ + x];
    if (slot == c) return false;
    slot = c;
    return true;
}

void Canvas::SetAllPixels(std::vector<Color> pixels) {
    pixels_ = std::move(pixels);
}
