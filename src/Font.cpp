#include "Font.h"

#include <SDL_ttf.h>

#include <cstdio>
#include <unordered_map>

#include "PixelMplusFontData.h"

namespace {

constexpr int kSmallPointSize = 14;  // scale 1 -- big enough that dense kanji stay legible
constexpr int kLargePointSize = 20;  // scale >= 2
constexpr size_t kMaxCacheEntries = 512;

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

    SDL_RWops* rwSmall = SDL_RWFromConstMem(kPixelMplusFontData, static_cast<int>(kPixelMplusFontDataSize));
    SDL_RWops* rwLarge = SDL_RWFromConstMem(kPixelMplusFontData, static_cast<int>(kPixelMplusFontDataSize));
    if (!rwSmall || !rwLarge) return false;

    g_fontSmall = TTF_OpenFontRW(rwSmall, 1, kSmallPointSize);
    g_fontLarge = TTF_OpenFontRW(rwLarge, 1, kLargePointSize);
    if (!g_fontSmall || !g_fontLarge) return false;

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
