#include "Input.h"

#include <SDL.h>

#include <algorithm>
#include <cmath>

#include "App.h"
#include "Tools/Tool.h"
#include "UI.h"

namespace {

bool s_spaceDown = false;
bool s_panning = false;
int s_panStartMouseX = 0, s_panStartMouseY = 0;
float s_panStartCamX = 0, s_panStartCamY = 0;

bool s_toolDragging = false;

// During an active stroke, keep painting even if the cursor leaves the
// canvas bounds -- clamp to the nearest edge pixel instead of stopping.
void PixelCoordsClamped(App& app, int sx, int sy, int& px, int& py) {
    float fx = (sx - app.camOffsetX) / static_cast<float>(app.zoom);
    float fy = (sy - app.camOffsetY) / static_cast<float>(app.zoom);
    px = std::clamp(static_cast<int>(std::floor(fx)), 0, app.GetCanvas().GetWidth() - 1);
    py = std::clamp(static_cast<int>(std::floor(fy)), 0, app.GetCanvas().GetHeight() - 1);
}

void HandleMouseDown(App& app, const SDL_MouseButtonEvent& e) {
    if (UI::HandleMouseDown(app, e.x, e.y, e.button == SDL_BUTTON_RIGHT)) return;

    if (e.button == SDL_BUTTON_MIDDLE || (e.button == SDL_BUTTON_LEFT && s_spaceDown)) {
        s_panning = true;
        s_panStartMouseX = e.x;
        s_panStartMouseY = e.y;
        s_panStartCamX = app.camOffsetX;
        s_panStartCamY = app.camOffsetY;
        return;
    }

    if (e.button == SDL_BUTTON_LEFT) {
        int px, py;
        if (app.ScreenToPixel(e.x, e.y, px, py)) {
            Tool* tool = app.GetActiveToolImpl();
            if (tool) {
                tool->OnMouseDown(app, px, py);
                s_toolDragging = true;
            }
        }
    }
}

void HandleMouseUp(App& app, const SDL_MouseButtonEvent& e) {
    UI::HandleMouseUp(app, e.x, e.y);

    if (s_panning) {
        s_panning = false;
    }
    if (s_toolDragging && e.button == SDL_BUTTON_LEFT) {
        int px, py;
        PixelCoordsClamped(app, e.x, e.y, px, py);
        Tool* tool = app.GetActiveToolImpl();
        if (tool) tool->OnMouseUp(app, px, py);
        s_toolDragging = false;
    }
}

void HandleMouseMotion(App& app, const SDL_MouseMotionEvent& e) {
    bool leftDown = (e.state & SDL_BUTTON_LMASK) != 0;
    if (UI::HandleMouseMotion(app, e.x, e.y, leftDown)) return;

    if (s_panning) {
        app.camOffsetX = s_panStartCamX + (e.x - s_panStartMouseX);
        app.camOffsetY = s_panStartCamY + (e.y - s_panStartMouseY);
        return;
    }

    if (s_toolDragging) {
        int px, py;
        PixelCoordsClamped(app, e.x, e.y, px, py);
        Tool* tool = app.GetActiveToolImpl();
        if (tool) tool->OnMouseDrag(app, px, py);
    }
}

void HandleMouseWheel(App& app, const SDL_MouseWheelEvent& e) {
    int mx, my;
    SDL_GetMouseState(&mx, &my);
    if (UI::HandleMouseWheel(app, mx, my, e.y)) return;
    if (e.y > 0) app.ZoomIn(mx, my);
    else if (e.y < 0) app.ZoomOut(mx, my);
}

void HandleKeyDown(App& app, const SDL_KeyboardEvent& e) {
    SDL_Keycode key = e.keysym.sym;
    Uint16 mod = e.keysym.mod;
    bool ctrl = (mod & KMOD_CTRL) != 0;
    bool shift = (mod & KMOD_SHIFT) != 0;

    if (key == SDLK_SPACE) s_spaceDown = true;

    if (UI::HandleKeyDown(app, key)) return;  // a text field consumed it

    if (key == SDLK_ESCAPE) {
        if (UI::IsModalOpen(app)) app.CloseDialog();
        return;
    }

    if (UI::IsModalOpen(app)) {
        if (key == SDLK_RETURN || key == SDLK_KP_ENTER) app.ConfirmDialog();
        return;
    }

    if (ctrl && shift && key == SDLK_s) { app.OpenSaveAsDialog(); return; }
    if (ctrl && key == SDLK_n) { app.OpenNewCanvasDialog(); return; }
    if (ctrl && key == SDLK_o) { app.OpenOpenDialog(); return; }
    if (ctrl && key == SDLK_s) {
        if (app.currentFilePath.empty()) app.OpenSaveAsDialog();
        else app.SaveToPath(app.currentFilePath);
        return;
    }
    if (ctrl && key == SDLK_z) { app.Undo(); return; }
    if (ctrl && key == SDLK_y) { app.Redo(); return; }

    if (key == SDLK_b) { app.SetCurrentTool(ToolType::Pencil); return; }
    if (key == SDLK_e) { app.SetCurrentTool(ToolType::Eraser); return; }
    if (key == SDLK_f) { app.SetCurrentTool(ToolType::Fill); return; }
    if (key == SDLK_i) { app.SetCurrentTool(ToolType::Eyedropper); return; }

    if (key == SDLK_g) {
        if (shift) app.show16Guide = !app.show16Guide;
        else app.showPixelGrid = !app.showPixelGrid;
        return;
    }

    if (key == SDLK_PLUS || key == SDLK_EQUALS || key == SDLK_KP_PLUS) {
        int mx, my;
        SDL_GetMouseState(&mx, &my);
        app.ZoomIn(mx, my);
        return;
    }
    if (key == SDLK_MINUS || key == SDLK_KP_MINUS) {
        int mx, my;
        SDL_GetMouseState(&mx, &my);
        app.ZoomOut(mx, my);
        return;
    }
}

void HandleKeyUp(App&, const SDL_KeyboardEvent& e) {
    if (e.keysym.sym == SDLK_SPACE) {
        s_spaceDown = false;
        s_panning = false;
    }
}

}  // namespace

namespace Input {

void PollEvents(App& app) {
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT: app.quitRequested = true; break;
            case SDL_WINDOWEVENT:
                if (event.window.event == SDL_WINDOWEVENT_SIZE_CHANGED ||
                    event.window.event == SDL_WINDOWEVENT_RESIZED) {
                    app.windowWidth_ = event.window.data1;
                    app.windowHeight_ = event.window.data2;
                }
                break;
            case SDL_MOUSEBUTTONDOWN: HandleMouseDown(app, event.button); break;
            case SDL_MOUSEBUTTONUP: HandleMouseUp(app, event.button); break;
            case SDL_MOUSEMOTION: HandleMouseMotion(app, event.motion); break;
            case SDL_MOUSEWHEEL: HandleMouseWheel(app, event.wheel); break;
            case SDL_KEYDOWN: HandleKeyDown(app, event.key); break;
            case SDL_KEYUP: HandleKeyUp(app, event.key); break;
            case SDL_TEXTINPUT: UI::HandleTextInput(app, event.text.text); break;
            default: break;
        }
    }
}

}  // namespace Input
