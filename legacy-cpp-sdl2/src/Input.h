#pragma once

class App;

// Pumps the SDL event queue once and turns it into app state changes: tool
// strokes (delegated to the active Tool, converting screen -> pixel
// coordinates first), panning, zooming, keyboard shortcuts, and UI/dialog
// interaction (delegated to UI::Handle*).
namespace Input {
void PollEvents(App& app);
}
