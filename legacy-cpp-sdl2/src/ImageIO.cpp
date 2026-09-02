#include "ImageIO.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

namespace ImageIO {

bool SavePNG(const Canvas& canvas, const std::string& path, std::string& error) {
    const int width = canvas.GetWidth();
    const int height = canvas.GetHeight();
    if (width <= 0 || height <= 0) {
        error = "Canvas has no pixels to save";
        return false;
    }

    // stb_image_write wants a flat, tightly-packed RGBA8888 buffer; Color is
    // already laid out that way, so pixels() can be passed straight through.
    const std::vector<Color>& pixels = canvas.Pixels();
    int strideBytes = width * 4;
    int ok = stbi_write_png(path.c_str(), width, height, 4, pixels.data(), strideBytes);
    if (!ok) {
        error = "Failed to write PNG file: " + path;
        return false;
    }
    return true;
}

bool LoadPNG(const std::string& path, Canvas& outCanvas, std::string& error) {
    int width = 0;
    int height = 0;
    int channelsInFile = 0;
    unsigned char* data = stbi_load(path.c_str(), &width, &height, &channelsInFile, 4);
    if (!data) {
        error = std::string("Failed to load PNG file: ") + stbi_failure_reason();
        return false;
    }

    std::vector<Color> pixels(static_cast<size_t>(width) * static_cast<size_t>(height));
    for (size_t i = 0; i < pixels.size(); ++i) {
        const unsigned char* p = data + i * 4;
        pixels[i] = Color{p[0], p[1], p[2], p[3]};
    }
    stbi_image_free(data);

    outCanvas.Reset(width, height);
    outCanvas.SetAllPixels(std::move(pixels));
    return true;
}

}  // namespace ImageIO
