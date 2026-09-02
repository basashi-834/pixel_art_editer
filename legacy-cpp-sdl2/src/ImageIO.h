#pragma once

#include <string>

#include "Canvas.h"

// PNG import/export at the sprite's true resolution. No scaling, no grid
// overlays baked in -- just the raw RGBA pixel data.
namespace ImageIO {

// Writes `canvas` to `path` as an RGBA8888 PNG at canvas.GetWidth() x
// canvas.GetHeight() (the on-screen zoom level has no effect on this).
// Returns true on success; on failure `error` is set to a message.
bool SavePNG(const Canvas& canvas, const std::string& path, std::string& error);

// Loads `path` into `outCanvas`, resizing it to the PNG's own dimensions
// (the image is never resampled). Returns true on success; on failure
// `error` is set to a message and outCanvas is left untouched.
bool LoadPNG(const std::string& path, Canvas& outCanvas, std::string& error);

}  // namespace ImageIO
