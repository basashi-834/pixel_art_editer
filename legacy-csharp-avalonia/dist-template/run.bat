@echo off
cd /d "%~dp0"

where dotnet >nul 2>nul
if errorlevel 1 (
    echo =====================================================================
    echo  .NET 8 Desktop Runtime was not found.
    echo  Please download and install it from:
    echo    https://dotnet.microsoft.com/download/dotnet/8.0
    echo  ^(choose ".NET Desktop Runtime 8.0.x" for Windows x64^)
    echo =====================================================================
    pause
    exit /b 1
)

dotnet PixelSpriteEditor.dll
