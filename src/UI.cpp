#include "UI.h"

#include <SDL.h>

#include <algorithm>
#include <cstdio>
#include <string>
#include <vector>

#include "App.h"
#include "Color.h"
#include "Font.h"

namespace {

// ---- palette of small helper colors for chrome ----
constexpr Color kBarBg{50, 50, 55, 255};
constexpr Color kPanelBg{45, 45, 50, 255};
constexpr Color kButtonBg{70, 70, 78, 255};
constexpr Color kButtonHover{90, 90, 100, 255};
constexpr Color kButtonActive{90, 130, 200, 255};
constexpr Color kButtonDisabled{55, 55, 60, 255};
constexpr Color kBorder{20, 20, 22, 255};
constexpr Color kText{235, 235, 240, 255};
constexpr Color kTextDim{160, 160, 168, 255};
constexpr Color kFieldBg{25, 25, 28, 255};
constexpr Color kFieldActiveBg{35, 45, 60, 255};

struct ButtonItem {
    SDL_Rect rect{};
    std::string id;
    std::string label;
    bool active = false;
    bool enabled = true;
    bool checkbox = false;
    bool checked = false;
    std::string shortcut;  // menu items only: right-aligned shortcut hint
};

struct Layout {
    std::vector<ButtonItem> buttons;
    // Dropdown menu items (File/Edit/View), drawn and hit-tested in a
    // separate pass so they always render on top of the toolbar/panel
    // instead of being painted over by them.
    std::vector<ButtonItem> dropdownButtons;

    bool hasDialog = false;
    SDL_Rect dialogBox{};

    bool hasPanel = false;
    SDL_Rect panelRect{};
    SDL_Rect sliderRect[4]{};
    SDL_Rect numRect[4]{};

    SDL_Rect dlgField1{};
    SDL_Rect dlgField2{};
    SDL_Rect dlgPathField{};
    bool hasField1 = false;
    bool hasField2 = false;
    bool hasPathField = false;
    FieldId field1Id = FieldId::None;
    FieldId field2Id = FieldId::None;

    std::vector<std::string> openFiles;
};

int s_draggingSlider = -1;  // 0=R,1=G,2=B,3=A while a slider drag is active

const char* ToolLabel(ToolType t) {
    switch (t) {
        case ToolType::Pencil: return "Pencil (B)";
        case ToolType::Eraser: return "Eraser (E)";
        case ToolType::Fill: return "Fill (F)";
        case ToolType::Eyedropper: return "Picker (I)";
    }
    return "?";
}

bool PointIn(const SDL_Rect& r, int x, int y) {
    return x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h;
}

void FillRect(SDL_Renderer* r, const SDL_Rect& rect, Color c) {
    SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
    SDL_RenderFillRect(r, &rect);
}

void DrawRectOutline(SDL_Renderer* r, const SDL_Rect& rect, Color c) {
    SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
    SDL_RenderDrawRect(r, &rect);
}

void DrawCenteredText(SDL_Renderer* r, const SDL_Rect& rect, const std::string& text, Color c, int scale = 2) {
    int tw = Font::TextWidth(text, scale);
    int th = Font::LineHeight(scale);
    int x = rect.x + (rect.w - tw) / 2;
    int y = rect.y + (rect.h - th) / 2;
    Font::DrawText(r, x, y, text, c, scale);
}

void DrawButton(SDL_Renderer* r, const ButtonItem& b, int mx, int my) {
    bool hovered = b.enabled && PointIn(b.rect, mx, my);
    Color bg = !b.enabled ? kButtonDisabled : (b.active ? kButtonActive : (hovered ? kButtonHover : kButtonBg));
    FillRect(r, b.rect, bg);
    DrawRectOutline(r, b.rect, kBorder);
    std::string label = b.label;
    if (b.checkbox) label = (b.checked ? std::string("[x] ") : std::string("[ ] ")) + b.label;
    Color textColor = b.enabled ? kText : kTextDim;
    DrawCenteredText(r, b.rect, label, textColor);
}

// Menu dropdown row: label left-aligned, shortcut hint right-aligned, so
// long combinations never need manual padding and never overflow the box
// (the box itself is sized to fit both in BuildLayout).
void DrawMenuItem(SDL_Renderer* r, const ButtonItem& b, int mx, int my) {
    bool hovered = PointIn(b.rect, mx, my);
    FillRect(r, b.rect, hovered ? kButtonHover : kPanelBg);
    DrawRectOutline(r, b.rect, kBorder);

    std::string label = b.checkbox ? (b.checked ? std::string("[x] ") : std::string("[ ] ")) + b.label : b.label;
    int ty = b.rect.y + (b.rect.h - Font::LineHeight(2)) / 2;
    Font::DrawText(r, b.rect.x + 8, ty, label, kText, 2);

    if (!b.shortcut.empty()) {
        int sw = Font::TextWidth(b.shortcut, 1);
        int sy = b.rect.y + (b.rect.h - Font::LineHeight(1)) / 2;
        Font::DrawText(r, b.rect.x + b.rect.w - sw - 8, sy, b.shortcut, kTextDim, 1);
    }
}

// Draws a checker-backed swatch (so alpha is visible) with `c` composited
// over it, matching how the same color looks on the canvas.
void DrawColorSwatch(SDL_Renderer* r, const SDL_Rect& rect, Color c) {
    for (int y = 0; y < rect.h; y += 6) {
        for (int x = 0; x < rect.w; x += 6) {
            bool light = ((x / 6) + (y / 6)) % 2 == 0;
            Color cc = light ? Color{200, 200, 200, 255} : Color{150, 150, 150, 255};
            SDL_Rect cell{rect.x + x, rect.y + y, std::min(6, rect.w - x), std::min(6, rect.h - y)};
            FillRect(r, cell, cc);
        }
    }
    SDL_SetRenderDrawColor(r, c.r, c.g, c.b, c.a);
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
    SDL_RenderFillRect(r, &rect);
    SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);
    DrawRectOutline(r, rect, kBorder);
}

std::string FieldValueOrBuffer(App& app, FieldId id, const std::string& committed) {
    if (app.activeField == id) return app.fieldBuffer + "_";
    return committed;
}

std::string IntStr(int v) { return std::to_string(v); }

const std::vector<Color>& PresetPalette() {
    static const std::vector<Color> kPalette = [] {
        std::vector<Color> raw = {
            {0, 0, 0, 0},        // transparent
            {0, 0, 0, 255},      // black
            {255, 255, 255, 255},// white
            {128, 128, 128, 255},// gray
            {255, 0, 0, 255},    // red
            {255, 128, 0, 255},  // orange
            {255, 255, 0, 255},  // yellow
            {0, 200, 0, 255},    // green
            {0, 200, 200, 255},  // cyan
            {0, 90, 220, 255},   // blue
            {130, 0, 200, 255},  // purple
            {255, 0, 200, 255},  // magenta
            {120, 70, 20, 255},  // brown
            {255, 200, 160, 255},// skin
            {130, 0, 0, 255},    // dark red
            {0, 0, 100, 255},    // dark blue
        };
        for (auto& c : raw) c = color565::QuantizeColor(c);
        return raw;
    }();
    return kPalette;
}

Layout BuildLayout(App& app) {
    Layout L;
    const int W = app.windowWidth_;
    const int H = app.windowHeight_;

    // ---- Menu bar ----
    {
        int x = 4;
        const char* names[] = {"File", "Edit", "View"};
        for (const char* name : names) {
            int w = Font::TextWidth(name) + 20;
            SDL_Rect r{x, 0, w, layout::kMenuBarHeight};
            ButtonItem b;
            b.rect = r;
            b.id = std::string("menu:") + name;
            b.label = name;
            b.active = (app.openMenu == name);
            L.buttons.push_back(b);
            x += w + 2;
        }

        // Dropdown items. Each entry is {id, label, shortcut, checkbox, checked}.
        // Label and shortcut are drawn separately (left/right-aligned) and the
        // box auto-sizes to fit them, so nothing needs manual space-padding
        // and nothing overflows the box.
        struct MenuEntry {
            std::string id;
            std::string label;
            std::string shortcut;
            bool checkbox = false;
            bool checked = false;
        };

        auto addDropdownFor = [&](const char* menuName, const std::vector<MenuEntry>& items) {
            if (app.openMenu != menuName) return;
            // find the menu button x to align dropdown under it
            int mx = 4;
            for (const char* n : names) {
                int w = Font::TextWidth(n) + 20;
                if (std::string(n) == menuName) break;
                mx += w + 2;
            }

            int dw = 160;
            for (const auto& it : items) {
                std::string prefixed = it.checkbox ? std::string("[x] ") + it.label : it.label;
                int labelW = Font::TextWidth(prefixed, 2);
                int shortcutW = it.shortcut.empty() ? 0 : Font::TextWidth(it.shortcut, 1);
                int gap = it.shortcut.empty() ? 0 : 20;
                dw = std::max(dw, labelW + gap + shortcutW + 16);
            }
            mx = std::min(mx, W - dw - 4);

            int y = layout::kMenuBarHeight;
            for (const auto& it : items) {
                ButtonItem b;
                b.rect = SDL_Rect{mx, y, dw, 24};
                b.id = it.id;
                b.label = it.label;
                b.shortcut = it.shortcut;
                b.checkbox = it.checkbox;
                b.checked = it.checked;
                L.dropdownButtons.push_back(b);
                y += 24;
            }
        };

        addDropdownFor("File", {
                                    {"menuitem:File:New", "New", "Ctrl+N"},
                                    {"menuitem:File:Open", "Open", "Ctrl+O"},
                                    {"menuitem:File:Save", "Save", "Ctrl+S"},
                                    {"menuitem:File:SaveAs", "Save As", "Ctrl+Shift+S"},
                                    {"menuitem:File:Quit", "Quit", ""},
                                });

        addDropdownFor("Edit", {
                                    {"menuitem:Edit:Undo", "Undo", "Ctrl+Z"},
                                    {"menuitem:Edit:Redo", "Redo", "Ctrl+Y"},
                                });

        addDropdownFor("View", {
                                    {"menuitem:View:PixelGrid", "Pixel Grid", "G", true, app.showPixelGrid},
                                    {"menuitem:View:Guide16", "16x16 Guide", "Shift+G", true, app.show16Guide},
                                    {"menuitem:View:Transparency", "Transparency Grid", "", true,
                                     app.showTransparencyGrid},
                                    {"menuitem:View:ColorPanel", "Color Panel", "", true, app.colorPanelOpen_},
                                });
    }

    // ---- Toolbar ----
    {
        int x = 8;
        int y = layout::kMenuBarHeight + 4;
        int h = layout::kToolBarHeight - 8;

        ToolType tools[] = {ToolType::Pencil, ToolType::Eraser, ToolType::Fill, ToolType::Eyedropper};
        const char* toolIds[] = {"tool:Pencil", "tool:Eraser", "tool:Fill", "tool:Eyedropper"};
        for (int i = 0; i < 4; ++i) {
            std::string label = ToolLabel(tools[i]);
            int w = Font::TextWidth(label) + 16;
            SDL_Rect r{x, y, w, h};
            ButtonItem b;
            b.rect = r;
            b.id = toolIds[i];
            b.label = label;
            b.active = (app.GetCurrentTool() == tools[i]);
            L.buttons.push_back(b);
            x += w + 4;
        }
        x += 12;

        {
            SDL_Rect r{x, y, Font::TextWidth("Undo") + 16, h};
            ButtonItem b{r, "action:Undo", "Undo", false, app.GetHistory().CanUndo()};
            L.buttons.push_back(b);
            x += r.w + 4;
        }
        {
            SDL_Rect r{x, y, Font::TextWidth("Redo") + 16, h};
            ButtonItem b{r, "action:Redo", "Redo", false, app.GetHistory().CanRedo()};
            L.buttons.push_back(b);
            x += r.w + 12;
        }

        // Color swatch (toggles panel) -- drawn specially, not via DrawButton
        SDL_Rect swatchRect{x, y, 60, h};
        ButtonItem swatchBtn{swatchRect, "colorswatch", "", app.colorPanelOpen_, true};
        L.buttons.push_back(swatchBtn);
        x += 60 + 12;

        {
            SDL_Rect r{x, y, 24, h};
            L.buttons.push_back(ButtonItem{r, "zoom:out", "-", false, true});
            x += r.w + 4;
        }
        x += Font::TextWidth("999x") + 8;  // reserve space for the (non-clickable) zoom label
        {
            SDL_Rect r{x, y, 24, h};
            L.buttons.push_back(ButtonItem{r, "zoom:in", "+", false, true});
            x += r.w + 12;
        }

        // Recent colors
        const auto& recent = app.GetRecentColors();
        int swSize = h;
        for (size_t i = 0; i < recent.size() && i < 8; ++i) {
            SDL_Rect r{x, y, swSize, swSize};
            ButtonItem b{r, "recent:" + std::to_string(i), "", false, true};
            L.buttons.push_back(b);
            x += swSize + 3;
        }
    }

    // ---- Color panel ----
    L.hasPanel = app.colorPanelOpen_;
    if (L.hasPanel) {
        int px = W - layout::kColorPanelWidth;
        int py = layout::kMenuBarHeight + layout::kToolBarHeight;
        int ph = H - layout::kStatusBarHeight - py;
        L.panelRect = SDL_Rect{px, py, layout::kColorPanelWidth, ph};

        int fx = px + 15;
        int fy = py + 70;
        const char* rowLabels[4] = {"R", "G", "B", "A"};
        for (int i = 0; i < 4; ++i) {
            L.sliderRect[i] = SDL_Rect{fx + 20, fy, 130, 16};
            L.numRect[i] = SDL_Rect{fx + 160, fy - 2, 34, 20};
            fy += 30;
        }
        (void)rowLabels;

        // Recent colors grid
        int gx = fx;
        int gy = fy + 40;
        const auto& recent = app.GetRecentColors();
        for (size_t i = 0; i < recent.size(); ++i) {
            int col = static_cast<int>(i % 8);
            int row = static_cast<int>(i / 8);
            SDL_Rect r{gx + col * 22, gy + row * 22, 20, 20};
            ButtonItem b{r, "recentpanel:" + std::to_string(i), "", false, true};
            L.buttons.push_back(b);
        }
        int recentRows = static_cast<int>((recent.size() + 7) / 8);
        if (recentRows == 0) recentRows = 1;

        // Preset palette grid
        int py2 = gy + recentRows * 22 + 26;
        const auto& palette = PresetPalette();
        for (size_t i = 0; i < palette.size(); ++i) {
            int col = static_cast<int>(i % 8);
            int row = static_cast<int>(i / 8);
            SDL_Rect r{gx + col * 22, py2 + row * 22, 20, 20};
            ButtonItem b{r, "palette:" + std::to_string(i), "", false, true};
            L.buttons.push_back(b);
        }
    }

    // ---- Dialogs ----
    if (app.dialogMode != DialogMode::None) {
        L.hasDialog = true;
        if (app.dialogMode == DialogMode::NewCanvas) {
            SDL_Rect box{W / 2 - 190, H / 2 - 130, 380, 260};
            L.dialogBox = box;

            SDL_Rect modePixel{box.x + 20, box.y + 40, 160, 28};
            SDL_Rect modeTiles{box.x + 200, box.y + 40, 160, 28};
            L.buttons.push_back(ButtonItem{modePixel, "dialog:ModePixel", "Pixel Size",
                                            app.newCanvasSizeMode == NewCanvasSizeMode::Pixels, true});
            L.buttons.push_back(ButtonItem{modeTiles, "dialog:ModeTiles", "Tile Size (16px)",
                                            app.newCanvasSizeMode == NewCanvasSizeMode::Tiles, true});

            L.hasField1 = true;
            L.hasField2 = true;
            L.dlgField1 = SDL_Rect{box.x + 140, box.y + 100, 80, 26};
            L.dlgField2 = SDL_Rect{box.x + 140, box.y + 140, 80, 26};
            if (app.newCanvasSizeMode == NewCanvasSizeMode::Pixels) {
                L.field1Id = FieldId::NewWidth;
                L.field2Id = FieldId::NewHeight;
            } else {
                L.field1Id = FieldId::NewTilesX;
                L.field2Id = FieldId::NewTilesY;
            }

            SDL_Rect ok{box.x + 60, box.y + 210, 100, 32};
            SDL_Rect cancel{box.x + 220, box.y + 210, 100, 32};
            L.buttons.push_back(ButtonItem{ok, "dialog:OK", "Create", false, true});
            L.buttons.push_back(ButtonItem{cancel, "dialog:Cancel", "Cancel", false, true});
        } else if (app.dialogMode == DialogMode::Open) {
            SDL_Rect box{W / 2 - 220, H / 2 - 190, 440, 380};
            L.dialogBox = box;

            L.hasPathField = true;
            L.dlgPathField = SDL_Rect{box.x + 90, box.y + 40, 330, 26};

            L.openFiles = app.ListPngFilesInCwd();
            int ly = box.y + 80;
            for (size_t i = 0; i < L.openFiles.size() && i < 10; ++i) {
                SDL_Rect r{box.x + 20, ly, 400, 22};
                L.buttons.push_back(ButtonItem{r, "openfile:" + std::to_string(i), L.openFiles[i], false, true});
                ly += 24;
            }

            SDL_Rect ok{box.x + 100, box.y + 340 - 10, 100, 32};
            SDL_Rect cancel{box.x + 240, box.y + 340 - 10, 100, 32};
            L.buttons.push_back(ButtonItem{ok, "dialog:OK", "Open", false, true});
            L.buttons.push_back(ButtonItem{cancel, "dialog:Cancel", "Cancel", false, true});
        } else if (app.dialogMode == DialogMode::SaveAs) {
            SDL_Rect box{W / 2 - 220, H / 2 - 80, 440, 160};
            L.dialogBox = box;

            L.hasPathField = true;
            L.dlgPathField = SDL_Rect{box.x + 100, box.y + 50, 320, 26};

            SDL_Rect ok{box.x + 100, box.y + 100, 100, 32};
            SDL_Rect cancel{box.x + 240, box.y + 100, 100, 32};
            L.buttons.push_back(ButtonItem{ok, "dialog:OK", "Save", false, true});
            L.buttons.push_back(ButtonItem{cancel, "dialog:Cancel", "Cancel", false, true});
        }
    }

    return L;
}

void HandleButtonClick(App& app, const std::string& id) {
    if (id.rfind("menu:", 0) == 0) {
        std::string name = id.substr(5);
        app.openMenu = (app.openMenu == name) ? "" : name;
        return;
    }
    if (id == "menuitem:File:New") { app.openMenu = ""; app.OpenNewCanvasDialog(); return; }
    if (id == "menuitem:File:Open") { app.openMenu = ""; app.OpenOpenDialog(); return; }
    if (id == "menuitem:File:Save") {
        app.openMenu = "";
        if (app.currentFilePath.empty()) app.OpenSaveAsDialog();
        else app.SaveToPath(app.currentFilePath);
        return;
    }
    if (id == "menuitem:File:SaveAs") { app.openMenu = ""; app.OpenSaveAsDialog(); return; }
    if (id == "menuitem:File:Quit") { app.quitRequested = true; return; }
    if (id == "menuitem:Edit:Undo") { app.openMenu = ""; app.Undo(); return; }
    if (id == "menuitem:Edit:Redo") { app.openMenu = ""; app.Redo(); return; }
    if (id == "menuitem:View:PixelGrid") { app.showPixelGrid = !app.showPixelGrid; return; }
    if (id == "menuitem:View:Guide16") { app.show16Guide = !app.show16Guide; return; }
    if (id == "menuitem:View:Transparency") { app.showTransparencyGrid = !app.showTransparencyGrid; return; }
    if (id == "menuitem:View:ColorPanel") { app.ToggleColorPanel(); return; }

    if (id == "tool:Pencil") { app.SetCurrentTool(ToolType::Pencil); return; }
    if (id == "tool:Eraser") { app.SetCurrentTool(ToolType::Eraser); return; }
    if (id == "tool:Fill") { app.SetCurrentTool(ToolType::Fill); return; }
    if (id == "tool:Eyedropper") { app.SetCurrentTool(ToolType::Eyedropper); return; }

    if (id == "action:Undo") { app.Undo(); return; }
    if (id == "action:Redo") { app.Redo(); return; }

    if (id == "colorswatch") { app.ToggleColorPanel(); return; }

    if (id == "zoom:out") { app.ZoomOut(app.windowWidth_ / 2, app.CanvasAreaTop()); return; }
    if (id == "zoom:in") { app.ZoomIn(app.windowWidth_ / 2, app.CanvasAreaTop()); return; }

    if (id.rfind("recent:", 0) == 0 || id.rfind("recentpanel:", 0) == 0) {
        size_t idx = std::stoul(id.substr(id.find(':') + 1));
        const auto& recent = app.GetRecentColors();
        if (idx < recent.size()) app.SetCurrentColor(recent[idx]);
        return;
    }
    if (id.rfind("palette:", 0) == 0) {
        size_t idx = std::stoul(id.substr(8));
        const auto& palette = PresetPalette();
        if (idx < palette.size()) app.SetCurrentColor(palette[idx]);
        return;
    }

    if (id == "dialog:ModePixel") { app.newCanvasSizeMode = NewCanvasSizeMode::Pixels; return; }
    if (id == "dialog:ModeTiles") { app.newCanvasSizeMode = NewCanvasSizeMode::Tiles; return; }
    if (id == "dialog:OK") { app.ConfirmDialog(); return; }
    if (id == "dialog:Cancel") { app.CloseDialog(); return; }

    if (id.rfind("openfile:", 0) == 0) {
        size_t idx = std::stoul(id.substr(9));
        auto files = app.ListPngFilesInCwd();
        if (idx < files.size()) {
            app.LoadFromPath(files[idx]);
            app.CloseDialog();
        }
        return;
    }
}

}  // namespace

namespace UI {

void DrawChrome(App& app) {
    SDL_Renderer* r = app.GetSDLRenderer();
    int mx = 0, my = 0;
    SDL_GetMouseState(&mx, &my);

    Layout L = BuildLayout(app);

    // Menu bar + toolbar backgrounds
    FillRect(r, SDL_Rect{0, 0, app.windowWidth_, layout::kMenuBarHeight}, kBarBg);
    FillRect(r, SDL_Rect{0, layout::kMenuBarHeight, app.windowWidth_, layout::kToolBarHeight}, kBarBg);
    FillRect(r, SDL_Rect{0, app.windowHeight_ - layout::kStatusBarHeight, app.windowWidth_, layout::kStatusBarHeight},
             kBarBg);

    if (L.hasPanel) {
        FillRect(r, L.panelRect, kPanelBg);
        DrawRectOutline(r, L.panelRect, kBorder);
    }

    // Draw ordinary buttons (skip the special color swatch, drawn separately below)
    for (const auto& b : L.buttons) {
        if (b.id == "colorswatch") continue;
        DrawButton(r, b, mx, my);
    }

    // Color swatch button (special: shows the actual color, not a label)
    for (const auto& b : L.buttons) {
        if (b.id != "colorswatch") continue;
        DrawColorSwatch(r, b.rect, app.GetCurrentColor());
    }

    // Zoom label between the -/+ buttons
    {
        char buf[16];
        std::snprintf(buf, sizeof(buf), "%dx", app.zoom);
        SDL_Rect zoomOutRect{};
        for (const auto& b : L.buttons)
            if (b.id == "zoom:out") zoomOutRect = b.rect;
        SDL_Rect labelRect{zoomOutRect.x + zoomOutRect.w + 4, zoomOutRect.y, Font::TextWidth("999x") + 8,
                            zoomOutRect.h};
        DrawCenteredText(r, labelRect, buf, kText);
    }

    // Color panel contents
    if (L.hasPanel) {
        int px = L.panelRect.x;
        int py = L.panelRect.y;
        Font::DrawText(r, px + 15, py + 10, "Color (RGB565)", kText, 2);

        SDL_Rect preview{px + 15, py + 30, 60, 32};
        DrawColorSwatch(r, preview, app.GetCurrentColor());

        Color c = app.GetCurrentColor();
        color565::Raw565 raw = color565::ToRaw565(c);
        char hexbuf[32];
        std::snprintf(hexbuf, sizeof(hexbuf), "0x%04X", color565::PackRGB565(c));
        Font::DrawText(r, px + 85, py + 30, hexbuf, kText, 2);
        char rawbuf[48];
        std::snprintf(rawbuf, sizeof(rawbuf), "R%d G%d B%d", raw.r5, raw.g6, raw.b5);
        Font::DrawText(r, px + 85, py + 48, rawbuf, kTextDim, 1);

        const char* rowLabels[4] = {"R", "G", "B", "A"};
        uint8_t values[4] = {c.r, c.g, c.b, c.a};
        Color tint[4] = {{160, 60, 60, 255}, {60, 160, 60, 255}, {60, 60, 200, 255}, {140, 140, 140, 255}};
        FieldId fieldIds[4] = {FieldId::ColorR, FieldId::ColorG, FieldId::ColorB, FieldId::ColorA};
        for (int i = 0; i < 4; ++i) {
            const SDL_Rect& sr = L.sliderRect[i];
            Font::DrawText(r, sr.x - 18, sr.y - 1, rowLabels[i], kText, 2);
            FillRect(r, sr, Color{25, 25, 28, 255});
            DrawRectOutline(r, sr, kBorder);
            int fillW = static_cast<int>(sr.w * (values[i] / 255.0f));
            SDL_Rect fill{sr.x, sr.y, fillW, sr.h};
            FillRect(r, fill, tint[i]);
            DrawRectOutline(r, sr, kBorder);

            const SDL_Rect& nr = L.numRect[i];
            bool editing = app.activeField == fieldIds[i];
            FillRect(r, nr, editing ? kFieldActiveBg : kFieldBg);
            DrawRectOutline(r, nr, kBorder);
            std::string val = FieldValueOrBuffer(app, fieldIds[i], IntStr(values[i]));
            DrawCenteredText(r, nr, val, kText, 1);
        }

        int helpY = L.sliderRect[3].y + 30;
        Font::DrawText(r, px + 15, helpY, "16bit=65536 colors", kTextDim, 1);
        Font::DrawText(r, px + 15, helpY + 12, "R:5bit G:6bit B:5bit", kTextDim, 1);
        Font::DrawText(r, px + 15, helpY + 24, "0-255 input is rounded", kTextDim, 1);
        Font::DrawText(r, px + 15, helpY + 36, "to the nearest 16bit value", kTextDim, 1);

        int recentLabelY = helpY + 56;
        Font::DrawText(r, px + 15, recentLabelY, "Recent", kTextDim, 1);
        int paletteLabelY = recentLabelY + 22 * static_cast<int>((app.GetRecentColors().size() + 7) / 8 == 0
                                                                       ? 1
                                                                       : (app.GetRecentColors().size() + 7) / 8) +
                             10;
        Font::DrawText(r, px + 15, paletteLabelY, "Palette", kTextDim, 1);
    }

    for (const auto& b : L.buttons) {
        if (b.id.rfind("recent:", 0) == 0 || b.id.rfind("recentpanel:", 0) == 0) {
            size_t idx = std::stoul(b.id.substr(b.id.find(':') + 1));
            const auto& recent = app.GetRecentColors();
            if (idx < recent.size()) DrawColorSwatch(r, b.rect, recent[idx]);
        } else if (b.id.rfind("palette:", 0) == 0) {
            size_t idx = std::stoul(b.id.substr(8));
            const auto& palette = PresetPalette();
            if (idx < palette.size()) DrawColorSwatch(r, b.rect, palette[idx]);
        }
    }

    // Status bar
    {
        int y = app.windowHeight_ - layout::kStatusBarHeight;
        int px, py;
        bool over = app.ScreenToPixel(mx, my, px, py);
        char buf[256];
        std::string toolName = ToolLabel(app.GetCurrentTool());
        if (over) {
            std::snprintf(buf, sizeof(buf), "Image: %dx%d   Zoom: %dx   X:%d Y:%d   Tool: %s",
                          app.GetCanvas().GetWidth(), app.GetCanvas().GetHeight(), app.zoom, px, py,
                          toolName.c_str());
        } else {
            std::snprintf(buf, sizeof(buf), "Image: %dx%d   Zoom: %dx   X:- Y:-   Tool: %s",
                          app.GetCanvas().GetWidth(), app.GetCanvas().GetHeight(), app.zoom, toolName.c_str());
        }
        Font::DrawText(r, 8, y + 4, buf, kText, 2);

        std::string msg = app.GetStatusMessage();
        if (!msg.empty()) {
            int mw = Font::TextWidth(msg);
            Font::DrawText(r, app.windowWidth_ - mw - 12, y + 4, msg, Color{130, 220, 130, 255}, 2);
        }
    }

    // Menu dropdowns (File/Edit/View) -- drawn after everything else in the
    // chrome so they always sit on top of the toolbar/panel instead of
    // being painted over by it.
    for (const auto& b : L.dropdownButtons) {
        DrawMenuItem(r, b, mx, my);
    }

    // Dialogs (drawn last, on top, with a dim overlay)
    if (L.hasDialog) {
        SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_BLEND);
        FillRect(r, SDL_Rect{0, 0, app.windowWidth_, app.windowHeight_}, Color{0, 0, 0, 150});
        SDL_SetRenderDrawBlendMode(r, SDL_BLENDMODE_NONE);

        FillRect(r, L.dialogBox, kPanelBg);
        DrawRectOutline(r, L.dialogBox, kBorder);

        std::string title = app.dialogMode == DialogMode::NewCanvas ? "New Canvas"
                             : app.dialogMode == DialogMode::Open    ? "Open PNG"
                                                                      : "Save As";
        Font::DrawText(r, L.dialogBox.x + 20, L.dialogBox.y + 12, title, kText, 2);

        for (const auto& b : L.buttons) {
            bool belongsToDialog = b.id.rfind("dialog:", 0) == 0 || b.id.rfind("openfile:", 0) == 0;
            if (belongsToDialog) DrawButton(r, b, mx, my);
        }

        if (app.dialogMode == DialogMode::NewCanvas) {
            const char* l1 = app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? "Width:" : "Tiles X:";
            const char* l2 = app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? "Height:" : "Tiles Y:";
            Font::DrawText(r, L.dlgField1.x - 90, L.dlgField1.y + 4, l1, kText, 2);
            Font::DrawText(r, L.dlgField2.x - 90, L.dlgField2.y + 4, l2, kText, 2);

            std::string v1 = FieldValueOrBuffer(
                app, L.field1Id,
                IntStr(app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? app.newWidth : app.newTilesX));
            std::string v2 = FieldValueOrBuffer(
                app, L.field2Id,
                IntStr(app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? app.newHeight : app.newTilesY));

            bool editing1 = app.activeField == L.field1Id;
            bool editing2 = app.activeField == L.field2Id;
            FillRect(r, L.dlgField1, editing1 ? kFieldActiveBg : kFieldBg);
            DrawRectOutline(r, L.dlgField1, kBorder);
            DrawCenteredText(r, L.dlgField1, v1, kText);
            FillRect(r, L.dlgField2, editing2 ? kFieldActiveBg : kFieldBg);
            DrawRectOutline(r, L.dlgField2, kBorder);
            DrawCenteredText(r, L.dlgField2, v2, kText);

            if (app.newCanvasSizeMode == NewCanvasSizeMode::Tiles) {
                char buf[64];
                std::snprintf(buf, sizeof(buf), "= %d x %d px", app.newTilesX * 16, app.newTilesY * 16);
                Font::DrawText(r, L.dlgField1.x, L.dlgField2.y + 40, buf, kTextDim, 2);
            }
        } else if (app.dialogMode == DialogMode::Open || app.dialogMode == DialogMode::SaveAs) {
            const char* lbl = app.dialogMode == DialogMode::Open ? "Path:" : "Filename:";
            Font::DrawText(r, L.dlgPathField.x - Font::TextWidth(lbl) - 10, L.dlgPathField.y + 4, lbl, kText, 2);
            bool editing = app.activeField == FieldId::PathField;
            FillRect(r, L.dlgPathField, editing ? kFieldActiveBg : kFieldBg);
            DrawRectOutline(r, L.dlgPathField, kBorder);
            std::string v = FieldValueOrBuffer(app, FieldId::PathField, app.pathFieldValue);
            Font::DrawText(r, L.dlgPathField.x + 4, L.dlgPathField.y + 5, v, kText, 1);

            if (app.dialogMode == DialogMode::Open && L.openFiles.empty()) {
                Font::DrawText(r, L.dialogBox.x + 20, L.dlgPathField.y + 40, "(no .png files found here)", kTextDim,
                                1);
            }
        }
    }
}

bool IsPointOverChrome(App& app, int sx, int sy) {
    if (sy < app.CanvasAreaTop()) return true;
    if (sy >= app.CanvasAreaBottom()) return true;
    if (sx >= app.CanvasAreaRight()) return true;
    return false;
}

bool IsModalOpen(App& app) { return app.dialogMode != DialogMode::None; }

bool HandleMouseDown(App& app, int mx, int my, bool /*rightButton*/) {
    Layout L = BuildLayout(app);

    // Dialog open: only dialog widgets are interactive.
    if (L.hasDialog) {
        for (const auto& b : L.buttons) {
            bool belongsToDialog = b.id.rfind("dialog:", 0) == 0 || b.id.rfind("openfile:", 0) == 0;
            if (belongsToDialog && b.enabled && PointIn(b.rect, mx, my)) {
                HandleButtonClick(app, b.id);
                return true;
            }
        }
        if (L.hasField1 && PointIn(L.dlgField1, mx, my)) {
            int cur = app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? app.newWidth : app.newTilesX;
            app.BeginEditField(L.field1Id, IntStr(cur));
            return true;
        }
        if (L.hasField2 && PointIn(L.dlgField2, mx, my)) {
            int cur = app.newCanvasSizeMode == NewCanvasSizeMode::Pixels ? app.newHeight : app.newTilesY;
            app.BeginEditField(L.field2Id, IntStr(cur));
            return true;
        }
        if (L.hasPathField && PointIn(L.dlgPathField, mx, my)) {
            app.BeginEditField(FieldId::PathField, app.pathFieldValue);
            return true;
        }
        return true;  // swallow all clicks while modal (including on the dim overlay)
    }

    // Menu dropdown items take priority since they render on top of
    // everything else in the chrome.
    for (const auto& b : L.dropdownButtons) {
        if (PointIn(b.rect, mx, my)) {
            HandleButtonClick(app, b.id);
            return true;
        }
    }

    // Sliders (checked before generic buttons since they overlap nothing else)
    if (L.hasPanel) {
        FieldId fieldIds[4] = {FieldId::ColorR, FieldId::ColorG, FieldId::ColorB, FieldId::ColorA};
        for (int i = 0; i < 4; ++i) {
            if (PointIn(L.sliderRect[i], mx, my)) {
                s_draggingSlider = i;
                float t = (mx - L.sliderRect[i].x) / static_cast<float>(L.sliderRect[i].w);
                t = std::clamp(t, 0.0f, 1.0f);
                app.SetColorChannel(i, static_cast<uint8_t>(t * 255.0f + 0.5f));
                return true;
            }
            if (PointIn(L.numRect[i], mx, my)) {
                Color c = app.GetCurrentColor();
                uint8_t vals[4] = {c.r, c.g, c.b, c.a};
                app.BeginEditField(fieldIds[i], IntStr(vals[i]));
                return true;
            }
        }
    }

    for (const auto& b : L.buttons) {
        if (b.enabled && PointIn(b.rect, mx, my)) {
            HandleButtonClick(app, b.id);
            return true;
        }
    }

    // Clicking outside an open menu closes it, but still counts as
    // "consumed" only if it was actually over the chrome area.
    if (!app.openMenu.empty()) {
        app.openMenu = "";
        if (IsPointOverChrome(app, mx, my)) return true;
    }

    if (app.activeField != FieldId::None && IsPointOverChrome(app, mx, my)) {
        app.CommitActiveField();
    }

    return IsPointOverChrome(app, mx, my);
}

bool HandleMouseUp(App& app, int /*mx*/, int /*my*/) {
    if (s_draggingSlider != -1) {
        s_draggingSlider = -1;
        app.PushCurrentColorToRecent();
        return true;
    }
    return false;
}

bool HandleMouseMotion(App& app, int mx, int my, bool leftButtonDown) {
    if (s_draggingSlider != -1 && leftButtonDown) {
        Layout L = BuildLayout(app);
        const SDL_Rect& sr = L.sliderRect[s_draggingSlider];
        float t = (mx - sr.x) / static_cast<float>(sr.w);
        t = std::clamp(t, 0.0f, 1.0f);
        app.SetColorChannel(s_draggingSlider, static_cast<uint8_t>(t * 255.0f + 0.5f));
        return true;
    }
    if (!leftButtonDown) s_draggingSlider = -1;
    return s_draggingSlider != -1;
}

bool HandleMouseWheel(App&, int, int, int) { return false; }

bool HandleKeyDown(App& app, int sdlKeycode) {
    if (app.activeField == FieldId::None) return false;

    if (sdlKeycode == SDLK_RETURN || sdlKeycode == SDLK_KP_ENTER) {
        app.CommitActiveField();
        return true;
    }
    if (sdlKeycode == SDLK_ESCAPE) {
        app.CancelActiveField();
        return true;
    }
    if (sdlKeycode == SDLK_BACKSPACE) {
        app.FieldBackspace();
        return true;
    }
    return true;  // swallow all other keys while a field is focused
}

void HandleTextInput(App& app, const char* text) {
    if (app.activeField == FieldId::None) return;
    app.FieldTextInput(text);
}

}  // namespace UI
