#include "Renderer.h"

#include <algorithm>

#include "App.h"
#include "Canvas.h"

namespace {
constexpr SDL_Color kViewportBg{40, 40, 45, 255};
constexpr SDL_Color kCheckerLight{200, 200, 200, 255};
constexpr SDL_Color kCheckerDark{150, 150, 150, 255};
constexpr SDL_Color kPixelGridColor{0, 0, 0, 90};
constexpr SDL_Color kGuideColor{255, 60, 60, 200};
constexpr SDL_Color kBorderColor{255, 255, 255, 255};
constexpr int kCheckerCellPx = 8;  // fixed screen-space size, independent of zoom
}  // namespace

Renderer::Renderer(SDL_Renderer* sdlRenderer) : sdlRenderer_(sdlRenderer) {}

Renderer::~Renderer() {
    if (canvasTexture_) SDL_DestroyTexture(canvasTexture_);
}

void Renderer::EnsureTexture(int w, int h) {
    if (canvasTexture_ && texWidth_ == w && texHeight_ == h) return;
    if (canvasTexture_) SDL_DestroyTexture(canvasTexture_);
    canvasTexture_ = SDL_CreateTexture(sdlRenderer_, SDL_PIXELFORMAT_RGBA32, SDL_TEXTUREACCESS_STREAMING, w, h);
    SDL_SetTextureBlendMode(canvasTexture_, SDL_BLENDMODE_BLEND);
    SDL_SetTextureScaleMode(canvasTexture_, SDL_ScaleModeNearest);
    texWidth_ = w;
    texHeight_ = h;
}

void Renderer::UploadCanvasPixels(App& app) {
    const Canvas& canvas = app.GetCanvas();
    EnsureTexture(canvas.GetWidth(), canvas.GetHeight());

    void* pixels = nullptr;
    int pitch = 0;
    if (SDL_LockTexture(canvasTexture_, nullptr, &pixels, &pitch) == 0) {
        const auto& src = canvas.Pixels();
        auto* dstBytes = static_cast<Uint8*>(pixels);
        for (int y = 0; y < canvas.GetHeight(); ++y) {
            Color* row = reinterpret_cast<Color*>(dstBytes + y * pitch);
            const Color* srcRow = &src[static_cast<size_t>(y) * canvas.GetWidth()];
            for (int x = 0; x < canvas.GetWidth(); ++x) row[x] = srcRow[x];
        }
        SDL_UnlockTexture(canvasTexture_);
    }
}

void Renderer::DrawCheckerboard(const SDL_Rect& canvasRect, const SDL_Rect& viewport) {
    SDL_Rect clip;
    if (!SDL_IntersectRect(&canvasRect, &viewport, &clip)) return;

    for (int y = clip.y; y < clip.y + clip.h; y += kCheckerCellPx) {
        for (int x = clip.x; x < clip.x + clip.w; x += kCheckerCellPx) {
            int cellCol = (x - canvasRect.x) / kCheckerCellPx;
            int cellRow = (y - canvasRect.y) / kCheckerCellPx;
            bool light = ((cellCol + cellRow) % 2) == 0;
            const SDL_Color& c = light ? kCheckerLight : kCheckerDark;
            SDL_SetRenderDrawColor(sdlRenderer_, c.r, c.g, c.b, c.a);
            SDL_Rect cell{x, y, std::min(kCheckerCellPx, clip.x + clip.w - x),
                          std::min(kCheckerCellPx, clip.y + clip.h - y)};
            SDL_RenderFillRect(sdlRenderer_, &cell);
        }
    }
}

void Renderer::DrawPixelGrid(App& app, const SDL_Rect& canvasRect) {
    if (app.zoom < 4) return;  // too dense to be useful/legible below this
    const Canvas& canvas = app.GetCanvas();
    SDL_SetRenderDrawColor(sdlRenderer_, kPixelGridColor.r, kPixelGridColor.g, kPixelGridColor.b, kPixelGridColor.a);
    SDL_SetRenderDrawBlendMode(sdlRenderer_, SDL_BLENDMODE_BLEND);
    for (int x = 0; x <= canvas.GetWidth(); ++x) {
        int sx = canvasRect.x + x * app.zoom;
        SDL_RenderDrawLine(sdlRenderer_, sx, canvasRect.y, sx, canvasRect.y + canvasRect.h);
    }
    for (int y = 0; y <= canvas.GetHeight(); ++y) {
        int sy = canvasRect.y + y * app.zoom;
        SDL_RenderDrawLine(sdlRenderer_, canvasRect.x, sy, canvasRect.x + canvasRect.w, sy);
    }
}

void Renderer::Draw16Guide(App& app, const SDL_Rect& canvasRect) {
    const Canvas& canvas = app.GetCanvas();
    SDL_SetRenderDrawColor(sdlRenderer_, kGuideColor.r, kGuideColor.g, kGuideColor.b, kGuideColor.a);
    SDL_SetRenderDrawBlendMode(sdlRenderer_, SDL_BLENDMODE_BLEND);
    const int kThickness = 2;
    for (int x = 0; x <= canvas.GetWidth(); x += 16) {
        int sx = canvasRect.x + x * app.zoom;
        SDL_Rect line{sx - kThickness / 2, canvasRect.y, kThickness, canvasRect.h};
        SDL_RenderFillRect(sdlRenderer_, &line);
    }
    for (int y = 0; y <= canvas.GetHeight(); y += 16) {
        int sy = canvasRect.y + y * app.zoom;
        SDL_Rect line{canvasRect.x, sy - kThickness / 2, canvasRect.w, kThickness};
        SDL_RenderFillRect(sdlRenderer_, &line);
    }
}

void Renderer::RenderCanvasArea(App& app) {
    SDL_Rect viewport{0, app.CanvasAreaTop(), app.CanvasAreaRight(), app.CanvasAreaBottom() - app.CanvasAreaTop()};

    SDL_SetRenderDrawColor(sdlRenderer_, kViewportBg.r, kViewportBg.g, kViewportBg.b, kViewportBg.a);
    SDL_RenderFillRect(sdlRenderer_, &viewport);

    SDL_RenderSetClipRect(sdlRenderer_, &viewport);

    const Canvas& canvas = app.GetCanvas();
    SDL_Rect canvasRect{static_cast<int>(app.camOffsetX), static_cast<int>(app.camOffsetY),
                         canvas.GetWidth() * app.zoom, canvas.GetHeight() * app.zoom};

    if (app.showTransparencyGrid) DrawCheckerboard(canvasRect, viewport);

    UploadCanvasPixels(app);
    SDL_RenderCopy(sdlRenderer_, canvasTexture_, nullptr, &canvasRect);

    if (app.showPixelGrid) DrawPixelGrid(app, canvasRect);
    if (app.show16Guide) Draw16Guide(app, canvasRect);

    SDL_SetRenderDrawColor(sdlRenderer_, kBorderColor.r, kBorderColor.g, kBorderColor.b, kBorderColor.a);
    SDL_RenderDrawRect(sdlRenderer_, &canvasRect);

    SDL_RenderSetClipRect(sdlRenderer_, nullptr);
}
