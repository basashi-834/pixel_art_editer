using System.Runtime.InteropServices;
using Avalonia;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using PixelSpriteEditor.Models;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace PixelSpriteEditor.Services;

/// <summary>
/// PNG import/export at the sprite's true resolution. No scaling, no grid
/// overlays baked in -- just the raw RGBA pixel data.
///
/// PNG encode/decode goes through ImageSharp rather than Avalonia's own
/// Bitmap/WriteableBitmap, since Avalonia only exposes raw pixel access on
/// WriteableBitmap (a *write* target) and gives no supported way to read
/// pixels back out of a loaded (decode-only) Bitmap. ImageSharp's Rgba32 is
/// exactly R,G,B,A bytes in order, matching <see cref="PixelColor"/>.
/// </summary>
public static class ImageIO
{
    /// <summary>
    /// Writes <paramref name="canvas"/> to <paramref name="path"/> as an
    /// RGBA8888 PNG at canvas.Width x canvas.Height (the on-screen zoom
    /// level has no effect on this). Throws on failure.
    /// </summary>
    public static void SavePng(PixelCanvas canvas, string path)
    {
        using var image = new Image<Rgba32>(canvas.Width, canvas.Height);
        var srcPixels = canvas.Pixels.ToArray();
        image.ProcessPixelRows(accessor =>
        {
            for (int y = 0; y < accessor.Height; y++)
            {
                var row = accessor.GetRowSpan(y);
                int rowStart = y * canvas.Width;
                for (int x = 0; x < row.Length; x++)
                {
                    var c = srcPixels[rowStart + x];
                    row[x] = new Rgba32(c.R, c.G, c.B, c.A);
                }
            }
        });
        image.SaveAsPng(path);
    }

    /// <summary>
    /// Loads <paramref name="path"/> into a fresh PixelCanvas sized to the
    /// PNG's own dimensions (the image is never resampled). Throws on failure.
    /// </summary>
    public static PixelCanvas LoadPng(string path)
    {
        using var image = SixLabors.ImageSharp.Image.Load<Rgba32>(path);
        var canvas = new PixelCanvas(image.Width, image.Height);
        var pixels = new PixelColor[image.Width * image.Height];
        image.ProcessPixelRows(accessor =>
        {
            for (int y = 0; y < accessor.Height; y++)
            {
                var row = accessor.GetRowSpan(y);
                int rowStart = y * image.Width;
                for (int x = 0; x < row.Length; x++)
                {
                    var p = row[x];
                    pixels[rowStart + x] = new PixelColor(p.R, p.G, p.B, p.A);
                }
            }
        });
        canvas.ReplacePixels(pixels);
        return canvas;
    }

    /// <summary>
    /// Creates a WriteableBitmap holding exactly the canvas's pixels (no
    /// grid/checkerboard/etc.) for the live canvas view to draw scaled up
    /// with nearest-neighbor sampling. WriteableBitmap.Lock() is a stable,
    /// documented Avalonia API for raw pixel writes, unlike bitmap decoding.
    /// </summary>
    public static WriteableBitmap ToWriteableBitmap(PixelCanvas canvas)
    {
        var bmp = new WriteableBitmap(
            new PixelSize(canvas.Width, canvas.Height),
            new Vector(96, 96),
            PixelFormat.Rgba8888,
            AlphaFormat.Unpremul);

        using var fb = bmp.Lock();
        ReadOnlySpan<byte> src = MemoryMarshal.AsBytes(canvas.Pixels);
        int srcStride = canvas.Width * 4;
        unsafe
        {
            var dst = new Span<byte>((void*)fb.Address, fb.RowBytes * canvas.Height);
            if (fb.RowBytes == srcStride)
            {
                src.CopyTo(dst);
            }
            else
            {
                for (int y = 0; y < canvas.Height; y++)
                    src.Slice(y * srcStride, srcStride).CopyTo(dst.Slice(y * fb.RowBytes, srcStride));
            }
        }
        return bmp;
    }
}
