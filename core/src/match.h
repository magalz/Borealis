// Internal helpers shared by the decision functions; not part of the C ABI.
#ifndef BOREALIS_CORE_MATCH_H
#define BOREALIS_CORE_MATCH_H

#include <algorithm>
#include <string_view>

namespace borealis::core {

  inline char ascii_lower(char c) {
    return c >= 'A' && c <= 'Z' ? static_cast<char>(c - 'A' + 'a') : c;
  }

  // ASCII case-insensitive substring test; `needle` must already be lower case.
  inline bool contains_ci(std::string_view text, std::string_view needle) {
    const auto match = std::ranges::search(text, needle, [](char a, char b) {
      return ascii_lower(a) == b;
    });
    return !match.empty();
  }

}  // namespace borealis::core

#endif  // BOREALIS_CORE_MATCH_H
