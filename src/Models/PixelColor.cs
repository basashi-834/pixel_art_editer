using System.Runtime.InteropServices;

namespace PixelSpriteEditor.Models;

/// <summary>
/// A pixel color, RGBA8888. Named PixelColor (not Color) to avoid clashing
/// with Avalonia.Media.Color, which the UI layer also needs in scope.
/// RGB channels are expected to already be quantized to RGB565 precision
/// (see <see cref="Rgb565"/>) before being stored here -- Alpha stays full
/// 8-bit, since 16-bit color formats only define RGB.
///
/// Explicit sequential layout so a PixelCanvas's pixel buffer can be
/// reinterpreted as a raw RGBA8888 byte span (MemoryMarshal.AsBytes) when
/// handing it to the bitmap/PNG APIs, without a per-pixel copy loop.
/// </summary>
[StructLayout(LayoutKind.Sequential)]
public readonly record struct PixelColor(byte R, byte G, byte B, byte A)
{
    public static readonly PixelColor Transparent = new(0, 0, 0, 0);
    public static readonly PixelColor Black = new(0, 0, 0, 255);
    public static readonly PixelColor White = new(255, 255, 255, 255);
}

/// <summary>
/// RGB565 quantization: R 5 bits (32 steps), G 6 bits (64 steps, since the
/// eye is more sensitive to green), B 5 bits (32 steps) = 65,536 colors
/// total. A raw 0-255 UI input is rounded down to the nearest representable
/// 16-bit value and expanded back to 0-255 (via bit replication, the way
/// real RGB565 hardware reconstructs the value) so the app always shows
/// exactly what the 16-bit color would really look like.
/// </summary>
public static class Rgb565
{
    public static byte Quantize5(byte v8)
    {
        byte v5 = (byte)((v8 * 31 + 127) / 255);
        return (byte)((v5 << 3) | (v5 >> 2));
    }

    public static byte Quantize6(byte v8)
    {
        byte v6 = (byte)((v8 * 63 + 127) / 255);
        return (byte)((v6 << 2) | (v6 >> 4));
    }

    public static PixelColor Quantize(PixelColor c) =>
        c with { R = Quantize5(c.R), G = Quantize6(c.G), B = Quantize5(c.B) };

    public readonly record struct Raw(byte R5, byte G6, byte B5);

    public static Raw ToRaw(PixelColor c) => new(
        (byte)((c.R * 31 + 127) / 255),
        (byte)((c.G * 63 + 127) / 255),
        (byte)((c.B * 31 + 127) / 255));

    public static ushort Pack(PixelColor c)
    {
        Raw raw = ToRaw(c);
        return (ushort)((raw.R5 << 11) | (raw.G6 << 5) | raw.B5);
    }
}
