#pragma once

#include <SDL.h>

class App;

// Draws the pixel canvas itself: transparency checkerboard, the sprite at
// its current zoom (always nearest-neighbor, never blurred), the 1px grid
// and the 16x16 guide. None of this ever touches the actual Canvas pixel
// data -- it is purely a scaled-up *view*. UI chrome (menus/toolbar/
// panels/dialogs/status bar) is drawn separately by UI::DrawChrome.
class Renderer {
public:
    explicit Renderer(SDL_Renderer* sdlRenderer);
    ~Renderer();

    void RenderCanvasArea(App& app);

private:
    SDL_Renderer* sdlRenderer_;
    SDL_Texture* canvasTexture_ = nullptr;
    int texWidth_ = 0;
    int texHeight_ = 0;

    void EnsureTexture(int w, int h);
    void UploadCanvasPixels(App& app);
    void DrawCheckerboard(const SDL_Rect& canvasRect, const SDL_Rect& viewport);
    void DrawPixelGrid(App& app, const SDL_Rect& canvasRect);
    void Draw16Guide(App& app, const SDL_Rect& canvasRect);
};
