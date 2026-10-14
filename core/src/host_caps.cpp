#include "borealis/host.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <iterator>
#include <string_view>

namespace borealis::core {

  namespace {

    // ServerCodecModeSupport bits (SCM_* in moonlight-common-c Limelight.h) and the
    // PyroWave profile bits (docs/host-contract/pyrowave.md, section 3.1).
    constexpr uint32_t kScmHevc = 0x00000100U;
    constexpr uint32_t kScmHevcMain10 = 0x00000200U;
    constexpr uint32_t kScmAv1Main8 = 0x00010000U;
    constexpr uint32_t kScmAv1Main10 = 0x00020000U;
    // SCM_H264_HIGH8_444, SCM_HEVC_REXT8_444, SCM_HEVC_REXT10_444, SCM_AV1_HIGH8_444, SCM_AV1_HIGH10_444.
    constexpr uint32_t kScmMaskYuv444 = 0x007C0000U;
    constexpr uint32_t kPyrowaveSdr420 = 0x00800000U;
    constexpr uint32_t kPyrowaveSdr444 = 0x01000000U;
    constexpr uint32_t kPyrowaveHdr420 = 0x02000000U;
    constexpr uint32_t kPyrowaveHdr444 = 0x04000000U;

    constexpr std::size_t kVersionParts = 4;

    // Parses a decimal integer whose magnitude fits in an int32. A leading '-' is
    // only a sign when allow_negative is set; "+", spaces and empty digit runs are
    // rejected.
    bool parse_component(std::string_view text, bool allow_negative, int32_t &value) {
      bool negative = false;
      if (allow_negative && !text.empty() && text.front() == '-') {
        negative = true;
        text.remove_prefix(1);
      }
      if (text.empty()) {
        return false;
      }
      int64_t magnitude = 0;
      for (const char c : text) {
        if (c < '0' || c > '9') {
          return false;
        }
        magnitude = (magnitude * 10) + (c - '0');
        if (magnitude > INT32_MAX) {
          return false;
        }
      }
      value = static_cast<int32_t>(negative ? -magnitude : magnitude);
      return true;
    }

    // "a.b.c.d": exactly four components, only the last one may be negative.
    bool parse_version(std::string_view text, std::array<int32_t, kVersionParts> &version) {
      for (int32_t &slot : version) {
        const bool last = &slot == &version.back();
        const std::size_t dot = text.find('.');
        if (last != (dot == std::string_view::npos)) {
          return false;
        }
        if (!parse_component(std::string_view(text.data(), last ? text.size() : dot), last, slot)) {
          return false;
        }
        text.remove_prefix(last ? text.size() : dot + 1);
      }
      return true;
    }

    uint32_t flag_if(uint32_t mask, uint32_t bits, uint32_t flag) {
      return (mask & bits) != 0U ? flag : 0U;
    }

  }  // namespace

}  // namespace borealis::core

// NOLINTBEGIN(bugprone-easily-swappable-parameters): the C ABI order is fixed.
extern "C" int borealis_host_parse_caps(const char *appversion, size_t len, uint32_t codec_mask, borealis_host_caps *out) noexcept {
  using namespace borealis::core;

  if (out == nullptr) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  *out = borealis_host_caps {};
  if (appversion == nullptr && len > 0) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  if (len > BOREALIS_HOST_APPVERSION_MAX_LEN) {
    return BOREALIS_ERR_INPUT_TOO_LARGE;
  }

  std::array<int32_t, kVersionParts> version {};
  if (parse_version(std::string_view(appversion != nullptr ? appversion : "", len), version)) {
    out->family = version.back() < 0 ? BOREALIS_HOST_FAMILY_SUNSHINE : BOREALIS_HOST_FAMILY_GFE;
    std::ranges::copy(version, std::begin(out->version));
  }

  out->codec_flags = flag_if(codec_mask, kScmHevc, BOREALIS_CODEC_HEVC) | flag_if(codec_mask, kScmHevcMain10, BOREALIS_CODEC_HEVC_MAIN10) |
                     flag_if(codec_mask, kScmAv1Main8, BOREALIS_CODEC_AV1) | flag_if(codec_mask, kScmAv1Main10, BOREALIS_CODEC_AV1_MAIN10) |
                     flag_if(codec_mask, kScmMaskYuv444, BOREALIS_CODEC_YUV444);
  out->pyrowave_profiles = flag_if(codec_mask, kPyrowaveSdr420, BOREALIS_PYROWAVE_SDR_420) |
                           flag_if(codec_mask, kPyrowaveSdr444, BOREALIS_PYROWAVE_SDR_444) |
                           flag_if(codec_mask, kPyrowaveHdr420, BOREALIS_PYROWAVE_HDR_420) |
                           flag_if(codec_mask, kPyrowaveHdr444, BOREALIS_PYROWAVE_HDR_444);
  return BOREALIS_OK;
}

// NOLINTEND(bugprone-easily-swappable-parameters)
