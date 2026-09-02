#pragma once

#include <cstdint>

// A pixel color. RGB are always stored already-quantized to the RGB565
// range (5 bits red, 6 bits green, 5 bits blue) but expanded back out to
// 8-bit values so the rest of the app can treat colors as plain RGBA8888.
// Alpha is kept at full 8-bit precision (16-bit color formats only define
// RGB; transparency is handled separately).
struct Color {
    uint8_t r = 0;
    uint8_t g = 0;
    uint8_t b = 0;
    uint8_t a = 255;

    bool operator==(const Color& o) const {
        return r == o.r && g == o.g && b == o.b && a == o.a;
    }
    bool operator!=(const Color& o) const { return !(*this == o); }
};

namespace color565 {

// Reduce an 8-bit channel (0-255) down to the given bit depth and expand it
// back to 0-255 using bit replication, which is how real RGB565 hardware /
// framebuffers reconstruct the value. This is the "what it would actually
// look like in 16-bit color" preview requested by the spec.
uint8_t Quantize5(uint8_t v8);  // 5-bit red/blue channel -> 0-255
uint8_t Quantize6(uint8_t v8);  // 6-bit green channel -> 0-255

// Quantize a full color's RGB to RGB565 precision (alpha untouched).
Color QuantizeColor(Color c);

// Raw component values as they would be stored in a 16-bit RGB565 word.
struct Raw565 {
    uint8_t r5;  // 0-31
    uint8_t g6;  // 0-63
    uint8_t b5;  // 0-31
};
Raw565 ToRaw565(Color c);
uint16_t PackRGB565(Color c);

}  // namespace color565
