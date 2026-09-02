cmake_minimum_required(VERSION 3.16)
project(PixelSpriteEditor CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(SDL2 REQUIRED)

add_executable(PixelSpriteEditor
    src/main.cpp
    src/App.cpp
    src/Canvas.cpp
    src/Color.cpp
    src/Renderer.cpp
    src/Input.cpp
    src/UI.cpp
    src/Font.cpp
    src/History.cpp
    src/ImageIO.cpp
    src/Tools/PencilTool.cpp
    src/Tools/EraserTool.cpp
    src/Tools/FillTool.cpp
    src/Tools/EyedropperTool.cpp
)

target_include_directories(PixelSpriteEditor PRIVATE
    src
    third_party/stb
    third_party/font5x7
    ${SDL2_INCLUDE_DIRS}
)

target_link_libraries(PixelSpriteEditor PRIVATE ${SDL2_LIBRARIES})
target_compile_options(PixelSpriteEditor PRIVATE ${SDL2_CFLAGS_OTHER})

if(MSVC)
    target_compile_options(PixelSpriteEditor PRIVATE /W4)
else()
    target_compile_options(PixelSpriteEditor PRIVATE -Wall -Wextra)
endif()
