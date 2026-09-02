#pragma once

#include <SDL.h>

#include <memory>
#include <string>
#include <vector>

#include "Canvas.h"
#include "Color.h"
#include "History.h"
#include "Tools/Tool.h"

enum class ToolType { Pencil, Eraser, Fill, Eyedropper };

enum class DialogMode { None, NewCanvas, Open, SaveAs };

// What kind of value is currently being typed into the single shared text
// field used by all of the app's tiny modal dialogs / color inputs.
enum class FieldId {
    None,
    ColorR,
    ColorG,
    ColorB,
    ColorA,
    NewWidth,
    NewHeight,
    NewTilesX,
    NewTilesY,
    PathField,
};

enum class NewCanvasSizeMode { Pixels, Tiles };

// Fixed UI chrome sizes, shared by App/UI/Renderer/Input so hit-testing and
// drawing always agree on where things are.
namespace layout {
constexpr int kMenuBarHeight = 24;
constexpr int kToolBarHeight = 40;
constexpr int kStatusBarHeight = 24;
constexpr int kColorPanelWidth = 210;
}  // namespace layout

// Top-level application object: owns the window, the document (Canvas +
// History), current tool/color/view state, and runs the main loop. Drawing
// is delegated to Renderer (canvas) and UI (menus/toolbar/panels/dialogs);
// event handling is delegated to Input. Tools call back into the small
// public API below (PaintPixel/BeginStroke/EndStroke/current color) so they
// stay decoupled from the rest of the app.
class App {
public:
    App();
    ~App();

    bool Init();
    void Run();
    void Shutdown();

    // ---- State accessors used by Renderer / UI / Input / Tools ----
    Canvas& GetCanvas() { return canvas_; }
    const Canvas& GetCanvas() const { return canvas_; }
    History& GetHistory() { return history_; }

    SDL_Window* GetWindow() const { return window_; }
    SDL_Renderer* GetSDLRenderer() const { return sdlRenderer_; }

    Color GetCurrentColor() const { return currentColor_; }
    void SetCurrentColor(Color c, bool addToRecent = true);
    // Sets one raw 0-255 channel (0=R,1=G,2=B,3=A) of the current color
    // without touching the recent-colors list; used while a slider/field
    // edit is still in progress. RGB channels are immediately quantized to
    // RGB565 precision so the preview always shows the real result.
    void SetColorChannel(int channelIndex, uint8_t value8);
    void PushCurrentColorToRecent();
    const std::vector<Color>& GetRecentColors() const { return recentColors_; }

    ToolType GetCurrentTool() const { return currentTool_; }
    void SetCurrentTool(ToolType t) { currentTool_ = t; }
    Tool* GetActiveToolImpl();

    // Paint helper used by tools: writes the pixel and records the change
    // in the currently-open history stroke (call between BeginStroke/EndStroke).
    void PaintPixel(int x, int y, Color c);
    void BeginStroke() { history_.BeginStroke(); }
    void EndStroke() { history_.EndStroke(); }

    void Undo();
    void Redo();

    // View toggles
    bool showPixelGrid = true;
    bool show16Guide = true;
    bool showTransparencyGrid = true;

    // Camera / zoom
    static constexpr int kZoomSteps[] = {1, 2, 4, 8, 16, 24, 32};
    int zoom = 8;
    float camOffsetX = 0.0f;  // screen-space position of canvas pixel (0,0)
    float camOffsetY = 0.0f;
    void ZoomIn(int screenMouseX, int screenMouseY);
    void ZoomOut(int screenMouseX, int screenMouseY);
    void SetZoom(int newZoom, int screenMouseX, int screenMouseY);
    void CenterCanvas();

    // Screen <-> pixel coordinate mapping
    int CanvasAreaTop() const { return layout::kMenuBarHeight + layout::kToolBarHeight; }
    int CanvasAreaBottom() const { return windowHeight_ - layout::kStatusBarHeight; }
    int CanvasAreaRight() const { return windowWidth_ - (colorPanelOpen_ ? layout::kColorPanelWidth : 0); }
    bool ScreenToPixel(int sx, int sy, int& px, int& py) const;

    int windowWidth_ = 1100;
    int windowHeight_ = 720;

    // New canvas / open / save-as dialog state
    DialogMode dialogMode = DialogMode::None;
    NewCanvasSizeMode newCanvasSizeMode = NewCanvasSizeMode::Tiles;
    int newWidth = 80;
    int newHeight = 96;
    int newTilesX = 5;
    int newTilesY = 6;
    std::string pathFieldValue;
    void OpenNewCanvasDialog();
    void OpenSaveAsDialog();
    void OpenOpenDialog();
    void CloseDialog();
    void ConfirmDialog();
    std::vector<std::string> ListPngFilesInBaseDir() const;

    // The folder Open/Save resolve relative filenames against, and where
    // the Open dialog's file list is read from. Fixed to the executable's
    // own directory (not the OS's ambient "current directory", which
    // varies by how the exe was launched and isn't visible to the user) so
    // there's always one predictable, displayable answer to "where am I
    // relative to?".
    const std::string& GetAppBaseDir() const { return appBaseDir_; }
    // Trims whitespace and a surrounding pair of quotes (as produced by
    // Windows Explorer's "Copy as path"), then resolves relative paths
    // against GetAppBaseDir(). Absolute paths are returned unchanged.
    std::string ResolvePath(const std::string& input) const;

    // Shared tiny text-edit field used by dialogs + color RGBA inputs
    FieldId activeField = FieldId::None;
    std::string fieldBuffer;
    void BeginEditField(FieldId id, const std::string& initial);
    void CommitActiveField();
    void CancelActiveField();
    void FieldTextInput(const char* text);
    void FieldBackspace();

    bool colorPanelOpen_ = true;
    void ToggleColorPanel() { colorPanelOpen_ = !colorPanelOpen_; }

    std::string currentFilePath;  // empty if never saved
    void NewCanvas(int width, int height);
    void SaveToPath(const std::string& path);
    void LoadFromPath(const std::string& path);

    void SetStatusMessage(const std::string& msg);
    std::string GetStatusMessage() const;

    // Which top menu (File/Edit/View) is currently dropped down, if any.
    std::string openMenu;

    bool quitRequested = false;

private:
    SDL_Window* window_ = nullptr;
    SDL_Renderer* sdlRenderer_ = nullptr;

    Canvas canvas_;
    History history_;

    Color currentColor_{0, 0, 0, 255};
    std::vector<Color> recentColors_;

    ToolType currentTool_ = ToolType::Pencil;
    std::unique_ptr<Tool> pencilTool_;
    std::unique_ptr<Tool> eraserTool_;
    std::unique_ptr<Tool> fillTool_;
    std::unique_ptr<Tool> eyedropperTool_;

    std::string statusMessage_;
    Uint32 statusMessageExpireTicks_ = 0;

    bool sdlInitialized_ = false;

    std::string appBaseDir_;  // set once in Init(); see GetAppBaseDir()
};
