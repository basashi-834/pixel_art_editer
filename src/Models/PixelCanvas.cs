namespace PixelSpriteEditor.Models;

/// <summary>
/// The real, low-resolution pixel data for the sprite being edited. This is
/// the only "true" image -- anything the UI draws at 8x/16x zoom is a
/// scaled *view* of this buffer, never the other way around. Named
/// PixelCanvas (not Canvas) to avoid clashing with Avalonia.Controls.Canvas.
/// </summary>
public sealed class PixelCanvas
{
    private PixelColor[] _pixels;

    public int Width { get; private set; }
    public int Height { get; private set; }

    public PixelCanvas(int width, int height)
    {
        Width = width;
        Height = height;
        _pixels = new PixelColor[width * height];
    }

    public void Reset(int width, int height)
    {
        Width = width;
        Height = height;
        _pixels = new PixelColor[width * height];
    }

    public bool InBounds(int x, int y) => x >= 0 && y >= 0 && x < Width && y < Height;

    public PixelColor GetPixel(int x, int y) => InBounds(x, y) ? _pixels[y * Width + x] : PixelColor.Transparent;

    /// <summary>Returns true if the pixel actually changed.</summary>
    public bool SetPixel(int x, int y, PixelColor c)
    {
        if (!InBounds(x, y)) return false;
        int i = y * Width + x;
        if (_pixels[i] == c) return false;
        _pixels[i] = c;
        return true;
    }

    public ReadOnlySpan<PixelColor> Pixels => _pixels;

    /// <summary>Used by ImageIO after loading a PNG: width/height must already match.</summary>
    public void ReplacePixels(PixelColor[] pixels) => _pixels = pixels;
}
