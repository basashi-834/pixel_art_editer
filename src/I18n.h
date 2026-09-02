#pragma once

#include <string>

// Minimal UI localization: every user-facing string in UI.cpp is written
// as i18n::T("English", "日本語") at its call site, rather than routed
// through a separate key/lookup table -- with only two languages and a
// modest amount of UI text, keeping the translation next to its usage is
// easier to keep in sync than a table that can silently drift out of date.
namespace i18n {

enum class Lang { EN, JA };

extern Lang current;

inline void SetLang(Lang l) { current = l; }
inline void ToggleLang() { current = (current == Lang::EN) ? Lang::JA : Lang::EN; }

inline std::string T(const char* en, const char* ja) { return current == Lang::JA ? std::string(ja) : std::string(en); }

}  // namespace i18n
