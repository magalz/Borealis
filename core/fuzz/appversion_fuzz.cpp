// Fuzzes borealis_host_parse_caps: the first four input bytes are the codec mask,
// the rest is the appversion text. The parser must never crash and its output must
// stay inside the documented ranges.
#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>

namespace {

  void check(bool condition) {
    if (!condition) {
      std::abort();
    }
  }

}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  uint32_t mask = 0;
  const std::size_t header = size < sizeof(mask) ? size : sizeof(mask);
  std::memcpy(&mask, data, header);
  const char *text = reinterpret_cast<const char *>(data) + header;
  const std::size_t len = size - header;

  borealis_host_caps caps;
  std::memset(&caps, 0xA5, sizeof(caps));
  const int rc = borealis_host_parse_caps(len > 0 ? text : nullptr, len, mask, &caps);

  if (len > BOREALIS_HOST_APPVERSION_MAX_LEN) {
    check(rc == BOREALIS_ERR_INPUT_TOO_LARGE);
    check(caps.family == BOREALIS_HOST_FAMILY_UNKNOWN && caps.codec_flags == 0U && caps.pyrowave_profiles == 0U);
    return 0;
  }
  check(rc == BOREALIS_OK);
  check(caps.family >= BOREALIS_HOST_FAMILY_UNKNOWN && caps.family <= BOREALIS_HOST_FAMILY_SUNSHINE);
  if (caps.family == BOREALIS_HOST_FAMILY_UNKNOWN) {
    for (const int32_t part : caps.version) {
      check(part == 0);
    }
  } else {
    check(caps.version[0] >= 0 && caps.version[1] >= 0 && caps.version[2] >= 0);
    check((caps.family == BOREALIS_HOST_FAMILY_SUNSHINE) == (caps.version[3] < 0));
  }
  check((caps.codec_flags & ~0x1FU) == 0U);
  check((caps.pyrowave_profiles & ~0xFU) == 0U);
  return 0;
}
