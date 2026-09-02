cmake_minimum_required(VERSION 3.16)
project(PixelSpriteEditor CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(SDL2 REQUIRED)
find_package(SDL2_ttf REQUIRED)

add_executable(PixelSpriteEditor
    src/main.cpp
    src/App.cpp
    src/Canvas.cpp
    src/Color.cpp
    src/Renderer.cpp
    src/Input.cpp
    src/UI.cpp
    src/Font.cpp
    src/I18n.cpp
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
    ${SDL2_INCLUDE_DIRS}
)

target_link_libraries(PixelSpriteEditor PRIVATE ${SDL2_LIBRARIES} SDL2_ttf::SDL2_ttf)
target_compile_options(PixelSpriteEditor PRIVATE ${SDL2_CFLAGS_OTHER})

if(MSVC)
    target_compile_options(PixelSpriteEditor PRIVATE /W4)
else()
    target_compile_options(PixelSpriteEditor PRIVATE -Wall -Wextra)
endif()

# The UI font ships as a plain .ttf next to the built binary (Font::Init
# looks there) rather than being embedded in the executable, so copy it
# into the build output automatically -- otherwise `cmake --build` would
# produce a binary that can't find its font until the file is copied by
# hand.
add_custom_command(TARGET PixelSpriteEditor POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_SOURCE_DIR}/third_party/pixelmplus/PixelMplus10-Regular.ttf"
        "$<TARGET_FILE_DIR:PixelSpriteEditor>/PixelMplus10-Regular.ttf"
)
