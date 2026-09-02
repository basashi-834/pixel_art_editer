#include "Font.h"

#include "font5x7.h"  // third_party/font5x7 -- provides `static const unsigned char font[]`

namespace Font {

void DrawText(SDL_Renderer* renderer, int x, int y, const std::string& text, Color color, int scale) {
    SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);
    int penX = x;
    for (unsigned char ch : text) {
        if (ch < 32 || ch > 126) ch = '?';
        const unsigned char* glyph = &font[static_cast<size_t>(ch) * 5];
        for (int col = 0; col < kGlyphWidth; ++col) {
            unsigned char bits = glyph[col];
            for (int row = 0; row < kGlyphHeight; ++row) {
                if (bits & (1 << row)) {
                    SDL_Rect r{penX + col * scale, y + row * scale, scale, scale};
                    SDL_RenderFillRect(renderer, &r);
                }
            }
        }
        penX += (kGlyphWidth + 1) * scale;
    }
}

int TextWidth(const std::string& text, int scale) {
    if (text.empty()) return 0;
    return static_cast<int>(text.size()) * (kGlyphWidth + 1) * scale - scale;
}

int LineHeight(int scale) { return kGlyphHeight * scale; }

}  // namespace Font
