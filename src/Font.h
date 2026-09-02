#pragma once

#include <SDL.h>

#include <string>

#include "Color.h"

// UI chrome text rendering (menus, toolbar labels, status bar, dialogs).
// Backed by SDL_ttf and a font embedded directly in the binary (see
// PixelMplusFontData.h / third_party/pixelmplus) so the app stays a single
// self-contained executable with no font file to ship or locate at
// runtime, and so it can render both English and Japanese UI text with one
// font. PixelMplus is a pixel-styled monospace font, chosen to keep the
// retro/pixel-art look even though it's rendered as an outline font (only
// the pixel *canvas* itself has the strict "no antialiasing" requirement --
// this is UI chrome, not sprite data).
namespace Font {

// Loads the embedded font at the two point sizes used throughout the UI
// (scale 1 = small/secondary text, scale >= 2 = normal/primary text).
// Must be called once after SDL_ttf's dependencies (SDL video) are
// initialized, before any DrawText/TextWidth call. Returns false on
// failure (check SDL_GetError()/TTF_GetError()).
bool Init();
void Shutdown();

// Draws `text` (UTF-8) with its top-left at (x, y).
void DrawText(SDL_Renderer* renderer, int x, int y, const std::string& text, Color color, int scale = 2);

// Width/line-height in screen pixels that DrawText(text, scale) occupies.
int TextWidth(const std::string& text, int scale = 2);
int LineHeight(int scale = 2);

}  // namespace Font
