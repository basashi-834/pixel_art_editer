#include "Color.h"

namespace color565 {

uint8_t Quantize5(uint8_t v8) {
    uint8_t v5 = static_cast<uint8_t>((static_cast<int>(v8) * 31 + 127) / 255);
    return static_cast<uint8_t>((v5 << 3) | (v5 >> 2));
}

uint8_t Quantize6(uint8_t v8) {
    uint8_t v6 = static_cast<uint8_t>((static_cast<int>(v8) * 63 + 127) / 255);
    return static_cast<uint8_t>((v6 << 2) | (v6 >> 4));
}

Color QuantizeColor(Color c) {
    c.r = Quantize5(c.r);
    c.g = Quantize6(c.g);
    c.b = Quantize5(c.b);
    return c;
}

Raw565 ToRaw565(Color c) {
    Raw565 raw;
    raw.r5 = static_cast<uint8_t>((static_cast<int>(c.r) * 31 + 127) / 255);
    raw.g6 = static_cast<uint8_t>((static_cast<int>(c.g) * 63 + 127) / 255);
    raw.b5 = static_cast<uint8_t>((static_cast<int>(c.b) * 31 + 127) / 255);
    return raw;
}

uint16_t PackRGB565(Color c) {
    Raw565 raw = ToRaw565(c);
    return static_cast<uint16_t>((raw.r5 << 11) | (raw.g6 << 5) | raw.b5);
}

}  // namespace color565
