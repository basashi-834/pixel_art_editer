# Legacy C++/SDL2 implementation

This is the original C++ / SDL2 + SDL2_ttf implementation of the pixel
sprite editor. It was fully working (see its own README.md), but every
freshly-built, unsigned Windows `.exe` it produced was inconsistently
blocked by Windows Smart App Control regardless of how it was distributed
(GitHub Actions artifact vs. sent directly), with no reliable workaround
short of the user disabling Smart App Control entirely.

The project was restarted from scratch in C# with Avalonia UI (see the
repository root) specifically to change that: publishing framework-
dependent (no self-contained native `.exe`) means the actual process
launched on the user's machine is Microsoft's own signed `dotnet.exe`,
which does not get flagged as an unrecognized native binary the way the
SDL2 build did. Avalonia's native OS file dialogs also removed the need
for the custom Open/Save path UI this version had to build (and kept
having to fix) by hand.

This folder is kept for reference only and is not built or maintained
going forward.
