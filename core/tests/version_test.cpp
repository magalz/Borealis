#include "borealis/core.h"

#include <cstddef>
#include <gtest/gtest.h>
#include <string>
#include <string_view>
#include <vector>

namespace {

  std::vector<std::string_view> split_dots(std::string_view text) {
    std::vector<std::string_view> parts;
    std::size_t start = 0;
    while (true) {
      const std::size_t dot = text.find('.', start);
      if (dot == std::string_view::npos) {
        parts.push_back(text.substr(start));
        return parts;
      }
      parts.push_back(text.substr(start, dot - start));
      start = dot + 1;
    }
  }

  // SemVer numeric identifier: digits only, no leading zeros (except "0").
  bool is_numeric_identifier(std::string_view part) {
    if (part.empty()) {
      return false;
    }
    if (part.size() > 1 && part.front() == '0') {
      return false;
    }
    for (const char c : part) {
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

}  // namespace

TEST(CoreVersion, ReturnsNonNull) {
  EXPECT_NE(borealis_core_version(), nullptr);
}

TEST(CoreVersion, MatchesCMakeProjectVersion) {
  ASSERT_NE(borealis_core_version(), nullptr);
  EXPECT_STREQ(borealis_core_version(), BOREALIS_CORE_EXPECTED_VERSION);
}

TEST(CoreVersion, IsValidSemVerCore) {
  ASSERT_NE(borealis_core_version(), nullptr);
  const std::string version = borealis_core_version();
  const auto parts = split_dots(version);
  ASSERT_EQ(parts.size(), 3U) << "expected MAJOR.MINOR.PATCH, got '" << version << "'";
  for (const auto part : parts) {
    EXPECT_TRUE(is_numeric_identifier(part)) << "bad identifier '" << part << "' in '" << version << "'";
  }
}

TEST(CoreVersion, ReturnsStablePointerAcrossCalls) {
  const char *first = borealis_core_version();
  const char *second = borealis_core_version();
  EXPECT_EQ(first, second);
}
