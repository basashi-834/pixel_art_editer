# Legacy C#/Avalonia implementation

This was the second implementation of the pixel sprite editor: C# + Avalonia
UI, published framework-dependent (no self-contained native `.exe`) so the
process that actually launches is Microsoft's own signed `dotnet.exe`
rather than an unrecognized binary. It worked well and fixed the Smart App
Control blocking that killed the first (C++/SDL2, see
`../legacy-cpp-sdl2/NOTE.md`) implementation.

The project moved on to a third implementation -- pure Java/Swing, JDK
standard library only, no build tool, no third-party dependencies -- at the
user's request, for a stronger guarantee than "framework-dependent": the
user receives plain `.java` source and compiles it themselves with `javac`
on their own machine, so nothing they run was ever a binary they didn't
build. See `../PixelSpriteEditor/README.md` for the current implementation.

This folder is kept for reference only and is not built or maintained
going forward.
