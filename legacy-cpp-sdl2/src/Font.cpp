#include "Font.h"

#include <SDL_ttf.h>

#include <cstdio>
#include <iostream>
#include <unordered_map>
#include <vector>

namespace {

constexpr int kSmallPointSize = 14;  // scale 1 -- big enough that dense kanji stay legible
constexpr int kLargePointSize = 20;  // scale >= 2
constexpr size_t kMaxCacheEntries = 512;
constexpr const char* kFontFileName = "PixelMplus10-Regular.ttf";

TTF_Font* g_fontSmall = nullptr;
TTF_Font* g_fontLarge = nullptr;
bool g_ttfInitialized = false;

struct CacheEntry {
    SDL_Texture* tex;
    int w;
    int h;
};
std::unordered_map<std::string, CacheEntry> g_cache;

TTF_Font* FontForScale(int scale) { return scale <= 1 ? g_fontSmall : g_fontLarge; }

void ClearCache() {
    for (auto& [key, entry] : g_cache) SDL_DestroyTexture(entry.tex);
    g_cache.clear();
}

std::string CacheKey(const std::string& text, int scale, Color c) {
    char suffix[32];
    std::snprintf(suffix, sizeof(suffix), "\x01%d\x01%u,%u,%u,%u", scale, c.r, c.g, c.b, c.a);
    return text + suffix;
}

// The font ships as a plain .ttf file next to the executable (or, for
// convenience while developing, is copied next to the build output by
// CMake) rather than being embedded in the binary as a byte array --
// bundling a large opaque binary blob inside an .exe is exactly the kind
// of pattern antivirus/SmartScreen-style heuristics flag as suspicious,
// and a `.ttf` sitting alongside the exe (the same way SDL2.dll does) is a
// completely unremarkable, recognizable file.
std::string FindFontPath() {
    std::vector<std::string> candidates;

    char* base = SDL_GetBasePath();
    if (base) {
        candidates.push_back(std::string(base) + kFontFileName);
        SDL_free(base);
    }
    // Fallbacks for running straight out of the source tree without a
    // packaged/copied font next to the binary (e.g. from an IDE).
    candidates.push_back(std::string("third_party/pixelmplus/") + kFontFileName);
    candidates.push_back(std::string("../third_party/pixelmplus/") + kFontFileName);
    candidates.push_back(kFontFileName);

    for (const auto& path : candidates) {
        SDL_RWops* probe = SDL_RWFromFile(path.c_str(), "rb");
        if (probe) {
            SDL_RWclose(probe);
            return path;
        }
    }
    return "";
}

const CacheEntry* GetOrRender(SDL_Renderer* renderer, const std::string& text, int scale, Color c) {
    if (text.empty() || !FontForScale(scale)) return nullptr;

    std::string key = CacheKey(text, scale, c);
    auto it = g_cache.find(key);
    if (it != g_cache.end()) return &it->second;

    // Dynamic strings (status bar coordinates, etc.) would otherwise grow
    // the cache without bound -- just reset it once it gets large.
    if (g_cache.size() > kMaxCacheEntries) ClearCache();

    SDL_Color sc{c.r, c.g, c.b, c.a};
    SDL_Surface* surf = TTF_RenderUTF8_Blended(FontForScale(scale), text.c_str(), sc);
    if (!surf) return nullptr;
    SDL_Texture* tex = SDL_CreateTextureFromSurface(renderer, surf);
    int w = surf->w, h = surf->h;
    SDL_FreeSurface(surf);
    if (!tex) return nullptr;

    auto [insertedIt, ok] = g_cache.emplace(std::move(key), CacheEntry{tex, w, h});
    return &insertedIt->second;
}

}  // namespace

namespace Font {

bool Init() {
    if (TTF_Init() != 0) return false;
    g_ttfInitialized = true;

    std::string path = FindFontPath();
    if (path.empty()) {
        std::cerr << "Font::Init: could not find " << kFontFileName
                  << " next to the executable (or in third_party/pixelmplus/ when running from"
                     " the source tree). It must ship alongside the built binary.\n";
        return false;
    }

    g_fontSmall = TTF_OpenFont(path.c_str(), kSmallPointSize);
    g_fontLarge = TTF_OpenFont(path.c_str(), kLargePointSize);
    if (!g_fontSmall || !g_fontLarge) {
        std::cerr << "Font::Init: TTF_OpenFont(" << path << ") failed: " << TTF_GetError() << "\n";
        return false;
    }

    TTF_SetFontKerning(g_fontSmall, 0);
    TTF_SetFontKerning(g_fontLarge, 0);
    return true;
}

void Shutdown() {
    ClearCache();
    if (g_fontSmall) {
        TTF_CloseFont(g_fontSmall);
        g_fontSmall = nullptr;
    }
    if (g_fontLarge) {
        TTF_CloseFont(g_fontLarge);
        g_fontLarge = nullptr;
    }
    if (g_ttfInitialized) {
        TTF_Quit();
        g_ttfInitialized = false;
    }
}

void DrawText(SDL_Renderer* renderer, int x, int y, const std::string& text, Color color, int scale) {
    const CacheEntry* e = GetOrRender(renderer, text, scale, color);
    if (!e) return;
    SDL_Rect dst{x, y, e->w, e->h};
    SDL_RenderCopy(renderer, e->tex, nullptr, &dst);
}

int TextWidth(const std::string& text, int scale) {
    if (text.empty() || !FontForScale(scale)) return 0;
    int w = 0, h = 0;
    TTF_SizeUTF8(FontForScale(scale), text.c_str(), &w, &h);
    return w;
}

int LineHeight(int scale) {
    TTF_Font* font = FontForScale(scale);
    return font ? TTF_FontHeight(font) : 0;
}

}  // namespace Font
