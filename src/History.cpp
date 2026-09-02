#include "History.h"

void History::BeginStroke() { inProgress_.clear(); }

void History::RecordChange(int x, int y, Color before, Color after) {
    inProgress_.push_back(PixelChange{x, y, before, after});
}

void History::EndStroke() {
    if (inProgress_.empty()) return;
    undoStack_.push_back(std::move(inProgress_));
    inProgress_.clear();
    redoStack_.clear();
}

void History::Undo(Canvas& canvas) {
    if (undoStack_.empty()) return;
    Stroke stroke = std::move(undoStack_.back());
    undoStack_.pop_back();
    for (auto it = stroke.rbegin(); it != stroke.rend(); ++it) {
        canvas.SetPixel(it->x, it->y, it->before);
    }
    redoStack_.push_back(std::move(stroke));
}

void History::Redo(Canvas& canvas) {
    if (redoStack_.empty()) return;
    Stroke stroke = std::move(redoStack_.back());
    redoStack_.pop_back();
    for (const auto& change : stroke) {
        canvas.SetPixel(change.x, change.y, change.after);
    }
    undoStack_.push_back(std::move(stroke));
}

void History::Clear() {
    inProgress_.clear();
    undoStack_.clear();
    redoStack_.clear();
}
