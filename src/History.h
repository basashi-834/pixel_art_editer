#pragma once

#include <vector>

#include "Canvas.h"
#include "Color.h"

// Stroke-based undo/redo. A "stroke" is one user gesture (a pencil drag
// from mouse-down to mouse-up, a single fill click, etc). Each stroke
// records only the pixels it actually touched so undo/redo stays cheap
// even on large canvases.
class History {
public:
    struct PixelChange {
        int x;
        int y;
        Color before;
        Color after;
    };

    // Begin recording a new stroke. Any in-progress stroke is discarded.
    void BeginStroke();
    // Record a pixel change as part of the current stroke. Call this
    // *after* the pixel has already been written to the canvas.
    void RecordChange(int x, int y, Color before, Color after);
    // Finish the current stroke and push it onto the undo stack (a no-op
    // if the stroke ended up touching no pixels). Clears the redo stack.
    void EndStroke();

    bool CanUndo() const { return !undoStack_.empty(); }
    bool CanRedo() const { return !redoStack_.empty(); }

    void Undo(Canvas& canvas);
    void Redo(Canvas& canvas);

    void Clear();

private:
    using Stroke = std::vector<PixelChange>;

    Stroke inProgress_;
    std::vector<Stroke> undoStack_;
    std::vector<Stroke> redoStack_;
};
