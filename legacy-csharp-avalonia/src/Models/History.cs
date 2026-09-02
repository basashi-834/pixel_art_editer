namespace PixelSpriteEditor.Models;

/// <summary>
/// Stroke-based undo/redo. A "stroke" is one user gesture (a pencil drag
/// from mouse-down to mouse-up, a single fill click, etc). Each stroke
/// records only the pixels it actually touched so undo/redo stays cheap
/// even on large canvases.
/// </summary>
public sealed class History
{
    public readonly record struct PixelChange(int X, int Y, PixelColor Before, PixelColor After);

    private List<PixelChange> _inProgress = new();
    private readonly List<List<PixelChange>> _undoStack = new();
    private readonly List<List<PixelChange>> _redoStack = new();

    public void BeginStroke() => _inProgress = new List<PixelChange>();

    /// <summary>Call after the pixel has already been written to the canvas.</summary>
    public void RecordChange(int x, int y, PixelColor before, PixelColor after) =>
        _inProgress.Add(new PixelChange(x, y, before, after));

    public void EndStroke()
    {
        if (_inProgress.Count == 0) return;
        _undoStack.Add(_inProgress);
        _inProgress = new List<PixelChange>();
        _redoStack.Clear();
    }

    public bool CanUndo => _undoStack.Count > 0;
    public bool CanRedo => _redoStack.Count > 0;

    public void Undo(PixelCanvas canvas)
    {
        if (_undoStack.Count == 0) return;
        var stroke = _undoStack[^1];
        _undoStack.RemoveAt(_undoStack.Count - 1);
        for (int i = stroke.Count - 1; i >= 0; i--) canvas.SetPixel(stroke[i].X, stroke[i].Y, stroke[i].Before);
        _redoStack.Add(stroke);
    }

    public void Redo(PixelCanvas canvas)
    {
        if (_redoStack.Count == 0) return;
        var stroke = _redoStack[^1];
        _redoStack.RemoveAt(_redoStack.Count - 1);
        foreach (var change in stroke) canvas.SetPixel(change.X, change.Y, change.After);
        _undoStack.Add(stroke);
    }

    public void Clear()
    {
        _inProgress.Clear();
        _undoStack.Clear();
        _redoStack.Clear();
    }
}
