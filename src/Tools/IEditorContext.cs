using PixelSpriteEditor.Models;

namespace PixelSpriteEditor.Tools;

/// <summary>
/// The small surface tools need from the app -- keeps tools decoupled from
/// MainWindow/the rest of the UI so new tools can be added without touching it.
/// </summary>
public interface IEditorContext
{
    PixelCanvas Canvas { get; }
    PixelColor CurrentColor { get; }
    void SetCurrentColor(PixelColor color);

    /// <summary>Writes the pixel and records the change in the open history stroke.</summary>
    void PaintPixel(int x, int y, PixelColor c);
    void BeginStroke();
    void EndStroke();
}
