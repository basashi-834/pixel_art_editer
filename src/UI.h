#pragma once

class App;

// All UI chrome outside the pixel canvas itself: menu bar, toolbar, the
// color panel, dialogs (New Canvas / Open / Save As) and the status bar.
// Layout is recomputed every call so Draw and the input handlers below
// always agree on where things are -- there is no separate "layout cache"
// to go stale.
namespace UI {

void DrawChrome(App& app);

// Each returns true if it consumed the event (so Input does not also treat
// it as a canvas interaction).
bool HandleMouseDown(App& app, int mx, int my, bool rightButton);
bool HandleMouseUp(App& app, int mx, int my);
bool HandleMouseMotion(App& app, int mx, int my, bool leftButtonDown);
bool HandleMouseWheel(App& app, int mx, int my, int wheelY);

// Keyboard while a text field is focused (digits/backspace/enter/escape).
// Returns true if it consumed the key (shortcuts in Input should not also
// fire while the user is typing into a field).
bool HandleKeyDown(App& app, int sdlKeycode);
void HandleTextInput(App& app, const char* text);

// True while a modal dialog is open, so Input can block canvas edits and
// panning/zooming while it is up.
bool IsModalOpen(App& app);

// True if the given screen point is over any UI chrome (menu/toolbar/
// panel/status bar/dialog) rather than the canvas viewport.
bool IsPointOverChrome(App& app, int sx, int sy);

}  // namespace UI
