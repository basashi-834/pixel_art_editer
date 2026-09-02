namespace PixelSpriteEditor.Services;

public enum Lang { En, Ja }

/// <summary>
/// Minimal UI localization: every user-facing string is written as
/// Loc.T("English", "日本語") at its call site. With only two languages and
/// a modest amount of UI text, this is easier to keep in sync than a
/// separate resource table that can silently drift out of date. Raising
/// <see cref="Changed"/> lets the UI refresh its text without a full
/// data-binding/resource-dictionary localization system.
/// </summary>
public static class Loc
{
    public static Lang Current { get; private set; } = Lang.Ja;

    public static event Action? Changed;

    public static string T(string en, string ja) => Current == Lang.Ja ? ja : en;

    public static void Toggle()
    {
        Current = Current == Lang.Ja ? Lang.En : Lang.Ja;
        Changed?.Invoke();
    }
}
