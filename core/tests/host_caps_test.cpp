#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <gtest/gtest.h>
#include <string>
#include <string_view>

namespace {

  borealis_host_caps parse_ok(std::string_view appversion, uint32_t mask) {
    borealis_host_caps caps {};
    EXPECT_EQ(borealis_host_parse_caps(appversion.data(), appversion.size(), mask, &caps), BOREALIS_OK)
      << "appversion '" << appversion << "'";
    return caps;
  }

  void expect_zeroed(const borealis_host_caps &caps) {
    EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_UNKNOWN);
    for (const int32_t part : caps.version) {
      EXPECT_EQ(part, 0);
    }
    EXPECT_EQ(caps.codec_flags, 0U);
    EXPECT_EQ(caps.pyrowave_profiles, 0U);
  }

  // Poisoned output, to prove that a call overwrites or zeroes every field.
  borealis_host_caps poisoned() {
    return borealis_host_caps {BOREALIS_HOST_FAMILY_SUNSHINE, {1, 2, 3, 4}, 0xFFFFFFFFU, 0xFFFFFFFFU};
  }

}  // namespace

TEST(HostCapsFamily, NegativeFourthComponentIsSunshine) {
  const auto caps = parse_ok("7.1.431.-1", 0);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_SUNSHINE);
  EXPECT_EQ(caps.version[0], 7);
  EXPECT_EQ(caps.version[1], 1);
  EXPECT_EQ(caps.version[2], 431);
  EXPECT_EQ(caps.version[3], -1);
}

TEST(HostCapsFamily, NonNegativeFourthComponentIsGfe) {
  const auto caps = parse_ok("3.23.0.74", 0);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_GFE);
  EXPECT_EQ(caps.version[0], 3);
  EXPECT_EQ(caps.version[1], 23);
  EXPECT_EQ(caps.version[2], 0);
  EXPECT_EQ(caps.version[3], 74);
}

TEST(HostCapsFamily, ZeroFourthComponentIsGfe) {
  EXPECT_EQ(parse_ok("7.1.431.0", 0).family, BOREALIS_HOST_FAMILY_GFE);
}

TEST(HostCapsFamily, MinusZeroIsGfeWithZeroComponent) {
  const auto caps = parse_ok("7.1.431.-0", 0);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_GFE);
  EXPECT_EQ(caps.version[3], 0);
}

TEST(HostCapsFamily, Int32LimitsAreAccepted) {
  const auto top = parse_ok("2147483647.0.0.0", 0);
  EXPECT_EQ(top.family, BOREALIS_HOST_FAMILY_GFE);
  EXPECT_EQ(top.version[0], INT32_MAX);
  const auto bottom = parse_ok("0.0.0.-2147483647", 0);
  EXPECT_EQ(bottom.family, BOREALIS_HOST_FAMILY_SUNSHINE);
  EXPECT_EQ(bottom.version[3], -INT32_MAX);
}

TEST(HostCapsFamily, OverflowIsUnknown) {
  EXPECT_EQ(parse_ok("2147483648.0.0.0", 0).family, BOREALIS_HOST_FAMILY_UNKNOWN);
  EXPECT_EQ(parse_ok("0.0.0.-2147483648", 0).family, BOREALIS_HOST_FAMILY_UNKNOWN);
  EXPECT_EQ(parse_ok("99999999999999999999.0.0.0", 0).family, BOREALIS_HOST_FAMILY_UNKNOWN);
}

TEST(HostCapsFamily, UnparseableIsUnknownWithZeroVersion) {
  for (const std::string_view text : {
         "",
         "abc",
         "7",
         "7.1",
         "7.1.431",
         "7.1.431.-1.0",
         "7.1.431.",
         "7.1.431.-",
         "7..431.1",
         ".1.431.1",
         "7.1.-431.1",
         "7.-1.431.1",
         "7.1.431.+1",
         "7.1.431.-1x",
         "7.1.431.1 ",
         " 7.1.431.-1",
         "7.1.431.--1",
         "7.1.4 1.-1",
       }) {
    const auto caps = parse_ok(text, 0);
    EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_UNKNOWN) << "'" << text << "'";
    for (const int32_t part : caps.version) {
      EXPECT_EQ(part, 0) << "'" << text << "'";
    }
  }
}

TEST(HostCapsFamily, EmbeddedNulIsNotATerminator) {
  const std::string text("7.1.431.-1\0x", 12);
  EXPECT_EQ(parse_ok(text, 0).family, BOREALIS_HOST_FAMILY_UNKNOWN);
}

TEST(HostCapsFamily, NullAppversionWithZeroLengthIsUnknownNotAnError) {
  borealis_host_caps caps = poisoned();
  EXPECT_EQ(borealis_host_parse_caps(nullptr, 0, 0x100, &caps), BOREALIS_OK);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_UNKNOWN);
  EXPECT_EQ(caps.version[0], 0);
  EXPECT_EQ(caps.codec_flags, BOREALIS_CODEC_HEVC);
}

TEST(HostCapsFamily, UnknownOverwritesEveryField) {
  borealis_host_caps caps = poisoned();
  const std::string_view text = "abc";
  ASSERT_EQ(borealis_host_parse_caps(text.data(), text.size(), 0, &caps), BOREALIS_OK);
  expect_zeroed(caps);
}

TEST(HostCapsMask, Luminalshine26_09_1FixtureMask) {
  // 0x001C0301: H.264, HEVC, HEVC Main10, H.264 4:4:4, HEVC 4:4:4 (8 and 10 bit).
  const auto caps = parse_ok("7.1.431.-1", 0x001C0301U);
  EXPECT_EQ(caps.codec_flags, BOREALIS_CODEC_HEVC | BOREALIS_CODEC_HEVC_MAIN10 | BOREALIS_CODEC_YUV444);
  EXPECT_EQ(caps.pyrowave_profiles, 0U);
}

TEST(HostCapsMask, EmptyMask) {
  const auto caps = parse_ok("7.1.431.-1", 0);
  EXPECT_EQ(caps.codec_flags, 0U);
  EXPECT_EQ(caps.pyrowave_profiles, 0U);
}

TEST(HostCapsMask, AllBitsSet) {
  const auto caps = parse_ok("7.1.431.-1", 0xFFFFFFFFU);
  EXPECT_EQ(
    caps.codec_flags,
    BOREALIS_CODEC_HEVC | BOREALIS_CODEC_HEVC_MAIN10 | BOREALIS_CODEC_AV1 | BOREALIS_CODEC_AV1_MAIN10 | BOREALIS_CODEC_YUV444
  );
  EXPECT_EQ(
    caps.pyrowave_profiles,
    BOREALIS_PYROWAVE_SDR_420 | BOREALIS_PYROWAVE_SDR_444 | BOREALIS_PYROWAVE_HDR_420 | BOREALIS_PYROWAVE_HDR_444
  );
}

TEST(HostCapsMask, HighestBitAndUnassignedBitsAreIgnored) {
  // 0x80000000 and 0x08000000 are above the PyroWave profile bits; 0x2 and 0x4
  // are unassigned low bits; 0x1 is H.264, which has no Borealis flag.
  for (const uint32_t mask : {0x80000000U, 0x08000000U, 0x00000001U, 0x00000002U, 0x00000004U, 0x00000400U, 0x7800000AU}) {
    const auto caps = parse_ok("7.1.431.-1", mask);
    EXPECT_EQ(caps.codec_flags, 0U) << std::hex << mask;
    EXPECT_EQ(caps.pyrowave_profiles, 0U) << std::hex << mask;
  }
}

TEST(HostCapsMask, EachCodecBitMapsToItsFlag) {
  struct Row {
    uint32_t mask;
    uint32_t flags;
  };

  for (const Row row : {
         Row {0x00000100U, BOREALIS_CODEC_HEVC},
         Row {0x00000200U, BOREALIS_CODEC_HEVC_MAIN10},
         Row {0x00010000U, BOREALIS_CODEC_AV1},
         Row {0x00020000U, BOREALIS_CODEC_AV1_MAIN10},
         Row {0x00040000U, BOREALIS_CODEC_YUV444},
         Row {0x00080000U, BOREALIS_CODEC_YUV444},
         Row {0x00100000U, BOREALIS_CODEC_YUV444},
         Row {0x00200000U, BOREALIS_CODEC_YUV444},
         Row {0x00400000U, BOREALIS_CODEC_YUV444},
       }) {
    EXPECT_EQ(parse_ok("7.1.431.-1", row.mask).codec_flags, row.flags) << std::hex << row.mask;
  }
}

TEST(HostCapsMask, EachPyrowaveBitMapsToItsProfile) {
  struct Row {
    uint32_t mask;
    uint32_t profiles;
  };

  for (const Row row : {
         Row {0x00800000U, BOREALIS_PYROWAVE_SDR_420},
         Row {0x01000000U, BOREALIS_PYROWAVE_SDR_444},
         Row {0x02000000U, BOREALIS_PYROWAVE_HDR_420},
         Row {0x04000000U, BOREALIS_PYROWAVE_HDR_444},
         Row {0x04800000U, BOREALIS_PYROWAVE_SDR_420 | BOREALIS_PYROWAVE_HDR_444},
       }) {
    const auto caps = parse_ok("7.1.431.-1", row.mask);
    EXPECT_EQ(caps.pyrowave_profiles, row.profiles) << std::hex << row.mask;
    EXPECT_EQ(caps.codec_flags, 0U) << std::hex << row.mask;
  }
}

TEST(HostCapsMask, MaskIsDecodedForAnyFamily) {
  EXPECT_EQ(parse_ok("7.1.431.0", 0x300U).codec_flags, BOREALIS_CODEC_HEVC | BOREALIS_CODEC_HEVC_MAIN10);
  EXPECT_EQ(parse_ok("garbage", 0x300U).codec_flags, BOREALIS_CODEC_HEVC | BOREALIS_CODEC_HEVC_MAIN10);
}

TEST(HostCapsLimits, LongestAcceptedAppversionIsParsed) {
  // Exactly 64 bytes: leading zeros pad the last component without overflowing it.
  std::string text = "1.2.3.";
  text.append(BOREALIS_HOST_APPVERSION_MAX_LEN - text.size() - 1, '0');
  text += '5';
  ASSERT_EQ(text.size(), static_cast<std::size_t>(BOREALIS_HOST_APPVERSION_MAX_LEN));
  const auto caps = parse_ok(text, 0);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_GFE);
  EXPECT_EQ(caps.version[3], 5);
}

TEST(HostCapsLimits, OverLongAppversionFailsAndZeroesOutput) {
  const std::string text(BOREALIS_HOST_APPVERSION_MAX_LEN + 1, '1');
  borealis_host_caps caps = poisoned();
  EXPECT_EQ(borealis_host_parse_caps(text.data(), text.size(), 0xFFFFFFFFU, &caps), BOREALIS_ERR_INPUT_TOO_LARGE);
  expect_zeroed(caps);
}

TEST(HostCapsLimits, NullPointerWithLengthFailsAndZeroesOutput) {
  borealis_host_caps caps = poisoned();
  EXPECT_EQ(borealis_host_parse_caps(nullptr, 5, 0xFFFFFFFFU, &caps), BOREALIS_ERR_INVALID_ARGUMENT);
  expect_zeroed(caps);
}

TEST(HostCapsLimits, NullOutputFails) {
  const std::string_view text = "7.1.431.-1";
  EXPECT_EQ(borealis_host_parse_caps(text.data(), text.size(), 0, nullptr), BOREALIS_ERR_INVALID_ARGUMENT);
}

TEST(HostCapsLimits, NullOutputWithNullInputFails) {
  EXPECT_EQ(borealis_host_parse_caps(nullptr, 0, 0, nullptr), BOREALIS_ERR_INVALID_ARGUMENT);
}
