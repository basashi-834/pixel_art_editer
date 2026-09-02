#include <cstdlib>
#include <iostream>
#include <vector>

#include "App.h"
#include "ImageIO.h"
#include "Renderer.h"
#include "Tools/Tool.h"
#include "UI.h"
#include "stb_image_write.h"

namespace {

// Runs a few frames headlessly and exercises paint/undo/redo/save/load
// without opening an interactive event loop. Used to smoke-test the build
// in environments without a real display (SDL_VIDEODRIVER=dummy) -- enable
// with PIXEL_EDITOR_SELFTEST=1.
int RunSelfTest(App& app) {
    Renderer renderer(app.GetSDLRenderer());

    auto renderFrame = [&] {
        SDL_SetRenderDrawColor(app.GetSDLRenderer(), 30, 30, 34, 255);
        SDL_RenderClear(app.GetSDLRenderer());
        renderer.RenderCanvasArea(app);
        UI::DrawChrome(app);
        SDL_RenderPresent(app.GetSDLRenderer());
    };

    renderFrame();

    // Draw a small diagonal line with the pencil, exercising strokes/undo.
    app.SetCurrentTool(ToolType::Pencil);
    app.SetCurrentColor(Color{255, 0, 0, 255});
    Tool* pencil = app.GetActiveToolImpl();
    pencil->OnMouseDown(app, 2, 2);
    pencil->OnMouseDrag(app, 5, 5);
    pencil->OnMouseUp(app, 5, 5);
    renderFrame();

    if (app.GetCanvas().GetPixel(2, 2).a == 0) {
        std::cerr << "[selftest] pencil did not paint pixel (2,2)\n";
        return 1;
    }

    app.Undo();
    if (app.GetCanvas().GetPixel(2, 2).a != 0) {
        std::cerr << "[selftest] undo did not clear pixel (2,2)\n";
        return 1;
    }
    app.Redo();
    if (app.GetCanvas().GetPixel(2, 2).a == 0) {
        std::cerr << "[selftest] redo did not restore pixel (2,2)\n";
        return 1;
    }

    app.SetCurrentTool(ToolType::Fill);
    app.SetCurrentColor(Color{0, 255, 0, 255});
    app.GetActiveToolImpl()->OnMouseDown(app, 0, 0);
    renderFrame();

    app.showPixelGrid = false;
    app.show16Guide = false;
    renderFrame();
    app.showPixelGrid = true;
    app.show16Guide = true;

    const char* outPath = std::getenv("PIXEL_EDITOR_SELFTEST_OUT");
    std::string savePath = outPath ? outPath : "/tmp/pixel_editor_selftest.png";
    std::string error;
    if (!ImageIO::SavePNG(app.GetCanvas(), savePath, error)) {
        std::cerr << "[selftest] save failed: " << error << "\n";
        return 1;
    }

    Canvas loaded;
    if (!ImageIO::LoadPNG(savePath, loaded, error)) {
        std::cerr << "[selftest] load failed: " << error << "\n";
        return 1;
    }
    if (loaded.GetWidth() != app.GetCanvas().GetWidth() || loaded.GetHeight() != app.GetCanvas().GetHeight()) {
        std::cerr << "[selftest] round-tripped PNG size mismatch\n";
        return 1;
    }

    std::cout << "[selftest] OK -- saved " << savePath << " (" << loaded.GetWidth() << "x" << loaded.GetHeight()
              << ")\n";

    // Optional: dump screenshots of the full rendered UI (chrome + canvas)
    // for visual review -- not part of the app's normal behavior.
    if (const char* shotPath = std::getenv("PIXEL_EDITOR_SELFTEST_SCREENSHOT")) {
        auto screenshot = [&](const std::string& path) {
            renderFrame();
            SDL_Renderer* r = app.GetSDLRenderer();
            int w = app.windowWidth_, h = app.windowHeight_;
            std::vector<unsigned char> pixels(static_cast<size_t>(w) * h * 4);
            if (SDL_RenderReadPixels(r, nullptr, SDL_PIXELFORMAT_RGBA32, pixels.data(), w * 4) == 0) {
                stbi_write_png(path.c_str(), w, h, 4, pixels.data(), w * 4);
                std::cout << "[selftest] screenshot saved to " << path << "\n";
            } else {
                std::cerr << "[selftest] SDL_RenderReadPixels failed: " << SDL_GetError() << "\n";
            }
        };

        screenshot(shotPath);

        app.openMenu = "File";
        screenshot(std::string(shotPath) + ".menu_file.png");
        app.openMenu = "View";
        screenshot(std::string(shotPath) + ".menu_view.png");
        app.openMenu = "";

        app.SetZoom(2, app.windowWidth_ / 2, (app.CanvasAreaTop() + app.CanvasAreaBottom()) / 2);
        app.CenterCanvas();
        screenshot(std::string(shotPath) + ".zoomed_out.png");

        app.SetZoom(8, app.windowWidth_ / 2, app.CanvasAreaTop());
        app.CenterCanvas();
        app.OpenNewCanvasDialog();
        screenshot(std::string(shotPath) + ".new_dialog.png");
        app.newCanvasSizeMode = NewCanvasSizeMode::Pixels;
        screenshot(std::string(shotPath) + ".new_dialog_pixels.png");
        app.CloseDialog();
    }

    return 0;
}

}  // namespace

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;

    App app;
    if (!app.Init()) {
        std::cerr << "Failed to initialize application" << std::endl;
        return 1;
    }

    if (std::getenv("PIXEL_EDITOR_SELFTEST")) {
        int result = RunSelfTest(app);
        app.Shutdown();
        return result;
    }

    app.Run();
    app.Shutdown();
    return 0;
}
