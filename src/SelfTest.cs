using PixelSpriteEditor.Models;
using PixelSpriteEditor.Services;
using PixelSpriteEditor.Tools;

namespace PixelSpriteEditor;

/// <summary>
/// Pure-logic smoke tests for the model/tool/IO layers -- no window, no
/// rendering. Run via "dotnet run -- --selftest". Complements the
/// screenshot-based UI verification in Program.RunHeadlessVerify, which
/// covers the parts a logic test can't (layout, actual pointer routing,
/// real keyboard-shortcut wiring).
/// </summary>
internal static class SelfTest
{
    public static bool Run()
    {
        bool ok = true;
        ok &= Check("RGB565 quantizes and expands 0/255 exactly", () =>
        {
            var c = Rgb565.Quantize(new PixelColor(255, 255, 255, 255));
            return c is { R: 255, G: 255, B: 255 };
        });
        ok &= Check("RGB565 quantizes a mid value to a representable 16-bit step", () =>
        {
            var raw = Rgb565.ToRaw(new PixelColor(128, 128, 128, 255));
            return raw.R5 == 16 && raw.G6 == 32; // 128*31/255≈15.5→16, 128*63/255≈31.6→32
        });
        ok &= Check("Pencil stroke paints pixels and records one undo step", () =>
        {
            var editor = new EditorState(8, 8);
            editor.SetCurrentColor(new PixelColor(255, 0, 0, 255));
            editor.CurrentTool.OnPointerDown(editor, 1, 1);
            editor.CurrentTool.OnPointerMove(editor, 3, 1);
            editor.CurrentTool.OnPointerUp(editor, 3, 1);
            bool painted = editor.Canvas.GetPixel(1, 1).R == 255 && editor.Canvas.GetPixel(3, 1).R == 255;
            bool canUndo = editor.History.CanUndo;
            return painted && canUndo;
        });
        ok &= Check("Undo/redo round-trips a stroke", () =>
        {
            var editor = new EditorState(8, 8);
            editor.SetCurrentColor(PixelColor.White);
            editor.CurrentTool.OnPointerDown(editor, 2, 2);
            editor.CurrentTool.OnPointerUp(editor, 2, 2);
            editor.Undo();
            bool clearedAfterUndo = editor.Canvas.GetPixel(2, 2).A == 0;
            editor.Redo();
            bool restoredAfterRedo = editor.Canvas.GetPixel(2, 2).R == 255 && editor.Canvas.GetPixel(2, 2).A == 255;
            return clearedAfterUndo && restoredAfterRedo;
        });
        ok &= Check("Eraser writes fully transparent pixels", () =>
        {
            var editor = new EditorState(4, 4);
            editor.PaintPixel(0, 0, PixelColor.White);
            editor.CurrentToolType = ToolType.Eraser;
            editor.CurrentTool.OnPointerDown(editor, 0, 0);
            editor.CurrentTool.OnPointerUp(editor, 0, 0);
            return editor.Canvas.GetPixel(0, 0) == PixelColor.Transparent;
        });
        ok &= Check("4-directional fill stops at a differently-colored border", () =>
        {
            var editor = new EditorState(5, 5);
            for (int x = 0; x < 5; x++) { editor.PaintPixel(x, 2, PixelColor.Black); editor.PaintPixel(2, x, PixelColor.Black); }
            editor.SetCurrentColor(new PixelColor(0, 255, 0, 255));
            editor.CurrentToolType = ToolType.Fill;
            editor.CurrentTool.OnPointerDown(editor, 0, 0);
            bool insideFilled = editor.Canvas.GetPixel(0, 0).G == 255;
            bool outsideUntouched = editor.Canvas.GetPixel(4, 4) == PixelColor.Transparent;
            return insideFilled && outsideUntouched;
        });
        ok &= Check("Eyedropper picks up the color under the cursor", () =>
        {
            var editor = new EditorState(4, 4);
            var target = new PixelColor(10, 20, 30, 255);
            editor.PaintPixel(1, 1, Rgb565.Quantize(target));
            editor.CurrentToolType = ToolType.Eyedropper;
            editor.CurrentTool.OnPointerDown(editor, 1, 1);
            return editor.CurrentColor == editor.Canvas.GetPixel(1, 1);
        });
        ok &= Check("PNG round-trip preserves exact pixels, including alpha=0", () =>
        {
            var editor = new EditorState(3, 2);
            editor.PaintPixel(0, 0, new PixelColor(200, 50, 10, 255));
            editor.PaintPixel(1, 0, PixelColor.Transparent); // alpha=0 must stay alpha=0, not become checkerboard/opaque
            editor.PaintPixel(2, 1, new PixelColor(0, 0, 0, 128));

            string tmp = Path.Combine(Path.GetTempPath(), $"pse-selftest-{Guid.NewGuid():N}.png");
            try
            {
                ImageIO.SavePng(editor.Canvas, tmp);
                var loaded = ImageIO.LoadPng(tmp);
                bool sizeMatches = loaded.Width == 3 && loaded.Height == 2;
                bool pixelsMatch = true;
                for (int y = 0; y < 2 && pixelsMatch; y++)
                    for (int x = 0; x < 3 && pixelsMatch; x++)
                        pixelsMatch &= loaded.GetPixel(x, y) == editor.Canvas.GetPixel(x, y);
                return sizeMatches && pixelsMatch;
            }
            finally
            {
                if (File.Exists(tmp)) File.Delete(tmp);
            }
        });
        ok &= Check("Zoom steps clamp to the documented min/max", () =>
        {
            var editor = new EditorState(8, 8);
            for (int i = 0; i < 20; i++) editor.ZoomOut(0, 0);
            bool minOk = editor.Zoom == EditorState.ZoomSteps[0];
            for (int i = 0; i < 20; i++) editor.ZoomIn(0, 0);
            bool maxOk = editor.Zoom == EditorState.ZoomSteps[^1];
            return minOk && maxOk;
        });
        ok &= Check("NewCanvas clears history and pixels", () =>
        {
            var editor = new EditorState(4, 4);
            editor.PaintPixel(0, 0, PixelColor.White);
            editor.BeginStroke();
            editor.PaintPixel(0, 0, PixelColor.White);
            editor.EndStroke();
            editor.NewCanvas(6, 6);
            return editor.Canvas.Width == 6 && editor.Canvas.Height == 6
                && !editor.History.CanUndo && !editor.History.CanRedo
                && editor.Canvas.GetPixel(0, 0) == PixelColor.Transparent;
        });

        Console.WriteLine(ok ? "SELFTEST: ALL PASSED" : "SELFTEST: FAILURES ABOVE");
        return ok;
    }

    private static bool Check(string name, Func<bool> test)
    {
        bool pass;
        try { pass = test(); }
        catch (Exception ex) { Console.WriteLine($"[FAIL] {name} -- threw {ex.GetType().Name}: {ex.Message}"); return false; }
        Console.WriteLine(pass ? $"[PASS] {name}" : $"[FAIL] {name}");
        return pass;
    }
}
