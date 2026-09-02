#pragma once

#include <SDL.h>

#include <string>

#include "Color.h"

// Tiny built-in bitmap font used for all UI chrome text (menus, toolbar
// labels, status bar, dialogs). Deliberately not a dependency on
// SDL2_ttf or any font file on disk -- the glyph data is compiled in, so
// the editor never has to hunt for a system font. See
// third_party/font5x7 for the glyph source and its (BSD) license.
namespace Font {

constexpr int kGlyphWidth = 5;
constexpr int kGlyphHeight = 7;

// Draws `text` with the top-left of the first glyph at (x, y), each pixel
// of the glyph drawn as a `scale`x`scale` filled square.
void DrawText(SDL_Renderer* renderer, int x, int y, const std::string& text, Color color, int scale = 2);

// Width in screen pixels that DrawText(text, scale) would occupy.
int TextWidth(const std::string& text, int scale = 2);
int LineHeight(int scale = 2);

}  // namespace Font
