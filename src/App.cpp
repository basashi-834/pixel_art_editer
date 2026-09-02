#include "App.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <filesystem>
#include <iostream>
#include <iterator>

#include "ImageIO.h"
#include "Input.h"
#include "Renderer.h"
#include "Tools/EraserTool.h"
#include "Tools/EyedropperTool.h"
#include "Tools/FillTool.h"
#include "Tools/PencilTool.h"
#include "UI.h"

namespace {
int ClampedIntFromString(const std::string& s, int lo, int hi, int fallback) {
    if (s.empty()) return fallback;
    try {
        int v = std::stoi(s);
        return std::clamp(v, lo, hi);
    } catch (...) {
        return fallback;
    }
}
}  // namespace

App::App() = default;

App::~App() { Shutdown(); }

bool App::Init() {
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        std::cerr << "SDL_Init failed: " << SDL_GetError() << std::endl;
        return false;
    }
    sdlInitialized_ = true;

    // Nearest-neighbor everywhere, always -- this is a pixel art tool, not
    // a general image viewer, so blurring/interpolation is never wanted.
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "0");

    window_ = SDL_CreateWindow("Pixel Sprite Editor", SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, windowWidth_,
                                windowHeight_, SDL_WINDOW_RESIZABLE);
    if (!window_) {
        std::cerr << "SDL_CreateWindow failed: " << SDL_GetError() << std::endl;
        return false;
    }

    sdlRenderer_ = SDL_CreateRenderer(window_, -1, SDL_RENDERER_ACCELERATED);
    if (!sdlRenderer_) {
        sdlRenderer_ = SDL_CreateRenderer(window_, -1, SDL_RENDERER_SOFTWARE);
    }
    if (!sdlRenderer_) {
        std::cerr << "SDL_CreateRenderer failed: " << SDL_GetError() << std::endl;
        return false;
    }

    SDL_StartTextInput();

    pencilTool_ = std::make_unique<PencilTool>();
    eraserTool_ = std::make_unique<EraserTool>();
    fillTool_ = std::make_unique<FillTool>();
    eyedropperTool_ = std::make_unique<EyedropperTool>();

    // Default demo canvas: 5x6 blocks of 16x16 -> 80x96px, matching the
    // spec's own running example of a multi-block character sprite.
    canvas_.Reset(newWidth, newHeight);
    CenterCanvas();

    recentColors_ = {Color{0, 0, 0, 255}, Color{255, 255, 255, 255}};

    return true;
}

void App::Run() {
    Renderer renderer(sdlRenderer_);

    while (!quitRequested) {
        Input::PollEvents(*this);

        SDL_SetRenderDrawColor(sdlRenderer_, 30, 30, 34, 255);
        SDL_RenderClear(sdlRenderer_);

        renderer.RenderCanvasArea(*this);
        UI::DrawChrome(*this);

        SDL_RenderPresent(sdlRenderer_);
        SDL_Delay(8);
    }
}

void App::Shutdown() {
    if (sdlRenderer_) {
        SDL_DestroyRenderer(sdlRenderer_);
        sdlRenderer_ = nullptr;
    }
    if (window_) {
        SDL_DestroyWindow(window_);
        window_ = nullptr;
    }
    if (sdlInitialized_) {
        SDL_Quit();
        sdlInitialized_ = false;
    }
}

void App::SetCurrentColor(Color c, bool addToRecent) {
    c = color565::QuantizeColor(c);
    currentColor_ = c;
    if (addToRecent) PushCurrentColorToRecent();
}

void App::SetColorChannel(int channelIndex, uint8_t value8) {
    Color c = currentColor_;
    switch (channelIndex) {
        case 0: c.r = color565::Quantize5(value8); break;
        case 1: c.g = color565::Quantize6(value8); break;
        case 2: c.b = color565::Quantize5(value8); break;
        case 3: c.a = value8; break;
        default: break;
    }
    currentColor_ = c;
}

void App::PushCurrentColorToRecent() {
    auto it = std::find(recentColors_.begin(), recentColors_.end(), currentColor_);
    if (it != recentColors_.end()) recentColors_.erase(it);
    recentColors_.insert(recentColors_.begin(), currentColor_);
    if (recentColors_.size() > 16) recentColors_.resize(16);
}

Tool* App::GetActiveToolImpl() {
    switch (currentTool_) {
        case ToolType::Pencil: return pencilTool_.get();
        case ToolType::Eraser: return eraserTool_.get();
        case ToolType::Fill: return fillTool_.get();
        case ToolType::Eyedropper: return eyedropperTool_.get();
    }
    return nullptr;
}

void App::PaintPixel(int x, int y, Color c) {
    Color before = canvas_.GetPixel(x, y);
    if (canvas_.SetPixel(x, y, c)) {
        history_.RecordChange(x, y, before, c);
    }
}

void App::Undo() { history_.Undo(canvas_); }
void App::Redo() { history_.Redo(canvas_); }

void App::SetZoom(int newZoom, int screenMouseX, int screenMouseY) {
    newZoom = std::clamp(newZoom, kZoomSteps[0], kZoomSteps[std::size(kZoomSteps) - 1]);
    float pxf = (screenMouseX - camOffsetX) / static_cast<float>(zoom);
    float pyf = (screenMouseY - camOffsetY) / static_cast<float>(zoom);
    zoom = newZoom;
    camOffsetX = screenMouseX - pxf * zoom;
    camOffsetY = screenMouseY - pyf * zoom;
}

void App::ZoomIn(int screenMouseX, int screenMouseY) {
    int n = static_cast<int>(std::size(kZoomSteps));
    int idx = 0;
    for (int i = 0; i < n; ++i)
        if (kZoomSteps[i] == zoom) idx = i;
    idx = std::min(idx + 1, n - 1);
    SetZoom(kZoomSteps[idx], screenMouseX, screenMouseY);
}

void App::ZoomOut(int screenMouseX, int screenMouseY) {
    int n = static_cast<int>(std::size(kZoomSteps));
    int idx = 0;
    for (int i = 0; i < n; ++i)
        if (kZoomSteps[i] == zoom) idx = i;
    idx = std::max(idx - 1, 0);
    SetZoom(kZoomSteps[idx], screenMouseX, screenMouseY);
}

void App::CenterCanvas() {
    int viewW = CanvasAreaRight();
    int viewH = CanvasAreaBottom() - CanvasAreaTop();
    camOffsetX = (viewW - canvas_.GetWidth() * zoom) / 2.0f;
    camOffsetY = CanvasAreaTop() + (viewH - canvas_.GetHeight() * zoom) / 2.0f;
}

bool App::ScreenToPixel(int sx, int sy, int& px, int& py) const {
    if (sy < CanvasAreaTop() || sy >= CanvasAreaBottom() || sx < 0 || sx >= CanvasAreaRight()) return false;
    float fx = (sx - camOffsetX) / static_cast<float>(zoom);
    float fy = (sy - camOffsetY) / static_cast<float>(zoom);
    px = static_cast<int>(std::floor(fx));
    py = static_cast<int>(std::floor(fy));
    return canvas_.InBounds(px, py);
}

void App::OpenNewCanvasDialog() {
    dialogMode = DialogMode::NewCanvas;
    activeField = FieldId::None;
}

void App::OpenSaveAsDialog() {
    dialogMode = DialogMode::SaveAs;
    pathFieldValue = currentFilePath.empty() ? "sprite.png" : currentFilePath;
    activeField = FieldId::None;
}

void App::OpenOpenDialog() {
    dialogMode = DialogMode::Open;
    pathFieldValue.clear();
    activeField = FieldId::None;
}

void App::CloseDialog() {
    dialogMode = DialogMode::None;
    activeField = FieldId::None;
}

void App::ConfirmDialog() {
    if (dialogMode == DialogMode::NewCanvas) {
        int w, h;
        if (newCanvasSizeMode == NewCanvasSizeMode::Pixels) {
            w = newWidth;
            h = newHeight;
        } else {
            w = newTilesX * 16;
            h = newTilesY * 16;
        }
        w = std::clamp(w, 1, 2048);
        h = std::clamp(h, 1, 2048);
        NewCanvas(w, h);
    } else if (dialogMode == DialogMode::Open) {
        if (!pathFieldValue.empty()) LoadFromPath(pathFieldValue);
    } else if (dialogMode == DialogMode::SaveAs) {
        if (!pathFieldValue.empty()) SaveToPath(pathFieldValue);
    }
    dialogMode = DialogMode::None;
    activeField = FieldId::None;
}

std::vector<std::string> App::ListPngFilesInCwd() const {
    std::vector<std::string> result;
    namespace fs = std::filesystem;
    std::error_code ec;
    for (const auto& entry : fs::directory_iterator(fs::current_path(), ec)) {
        if (ec) break;
        if (entry.is_regular_file() && entry.path().extension() == ".png") {
            result.push_back(entry.path().filename().string());
        }
    }
    std::sort(result.begin(), result.end());
    return result;
}

void App::BeginEditField(FieldId id, const std::string& initial) {
    activeField = id;
    fieldBuffer = initial;
}

void App::CommitActiveField() {
    if (activeField == FieldId::None) return;

    switch (activeField) {
        case FieldId::ColorR:
        case FieldId::ColorG:
        case FieldId::ColorB:
        case FieldId::ColorA: {
            int v = ClampedIntFromString(fieldBuffer, 0, 255, 0);
            int idx = activeField == FieldId::ColorR ? 0 : activeField == FieldId::ColorG ? 1
                                                         : activeField == FieldId::ColorB  ? 2
                                                                                            : 3;
            SetColorChannel(idx, static_cast<uint8_t>(v));
            PushCurrentColorToRecent();
            break;
        }
        case FieldId::NewWidth: newWidth = ClampedIntFromString(fieldBuffer, 1, 2048, newWidth); break;
        case FieldId::NewHeight: newHeight = ClampedIntFromString(fieldBuffer, 1, 2048, newHeight); break;
        case FieldId::NewTilesX: newTilesX = ClampedIntFromString(fieldBuffer, 1, 128, newTilesX); break;
        case FieldId::NewTilesY: newTilesY = ClampedIntFromString(fieldBuffer, 1, 128, newTilesY); break;
        case FieldId::PathField: pathFieldValue = fieldBuffer; break;
        default: break;
    }

    activeField = FieldId::None;
    fieldBuffer.clear();
}

void App::CancelActiveField() {
    activeField = FieldId::None;
    fieldBuffer.clear();
}

void App::FieldTextInput(const char* text) {
    if (activeField == FieldId::None) return;
    if (activeField == FieldId::PathField) {
        fieldBuffer += text;
        return;
    }
    for (const char* p = text; *p; ++p) {
        if (std::isdigit(static_cast<unsigned char>(*p)) && fieldBuffer.size() < 6) {
            fieldBuffer.push_back(*p);
        }
    }
}

void App::FieldBackspace() {
    if (!fieldBuffer.empty()) fieldBuffer.pop_back();
}

void App::NewCanvas(int width, int height) {
    canvas_.Reset(width, height);
    history_.Clear();
    currentFilePath.clear();
    CenterCanvas();
    SetStatusMessage("New canvas " + std::to_string(width) + "x" + std::to_string(height));
}

void App::SaveToPath(const std::string& path) {
    std::string error;
    if (ImageIO::SavePNG(canvas_, path, error)) {
        currentFilePath = path;
        SetStatusMessage("Saved: " + path);
    } else {
        SetStatusMessage("Save failed: " + error);
    }
}

void App::LoadFromPath(const std::string& path) {
    std::string error;
    Canvas temp;
    if (ImageIO::LoadPNG(path, temp, error)) {
        canvas_ = std::move(temp);
        history_.Clear();
        currentFilePath = path;
        CenterCanvas();
        SetStatusMessage("Loaded: " + path);
    } else {
        SetStatusMessage("Load failed: " + error);
    }
}

void App::SetStatusMessage(const std::string& msg) {
    statusMessage_ = msg;
    statusMessageExpireTicks_ = SDL_GetTicks() + 4000;
}

std::string App::GetStatusMessage() const {
    if (statusMessage_.empty()) return "";
    if (SDL_GetTicks() > statusMessageExpireTicks_) return "";
    return statusMessage_;
}
