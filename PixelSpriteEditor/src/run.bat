@echo off
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo =====================================================================
    echo  JDK not found ^(javac is not on PATH^).
    echo  Please install a JDK ^(JDK 11 or later^) and make sure
    echo  "javac" and "java" work from the command line.
    echo    https://adoptium.net/
    echo =====================================================================
    pause
    exit /b 1
)

echo Compiling...
javac -encoding UTF-8 *.java
if errorlevel 1 (
    echo.
    echo Compile failed. See the errors above.
    pause
    exit /b 1
)

java Main
