// Fuzzes borealis_launch_decide and borealis_launch_fps. Input layout: 4 bytes
// status, 1 byte verb (taken modulo 3 so an unknown verb is reached too), 4 bytes
// attempts, 1 byte flags (bit 0: pass a NULL message), the rest is the host
// message. The functions must never crash and keep their outputs in range.
#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>

namespace {

  constexpr std::size_t kHeaderSize = 10;

  void check(bool condition) {
    if (!condition) {
      std::abort();
    }
  }

}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  if (size < kHeaderSize) {
    return 0;
  }
  int32_t status = 0;
  uint32_t attempts = 0;
  std::memcpy(&status, data, sizeof(status));
  const int32_t verb = data[4] % 3;
  std::memcpy(&attempts, data + 5, sizeof(attempts));
  const bool null_message = (data[9] & 1U) != 0U;
  const char *msg = reinterpret_cast<const char *>(data) + kHeaderSize;
  const std::size_t len = size - kHeaderSize;

  borealis_launch_decision out;
  std::memset(&out, 0xA5, sizeof(out));
  const int rc = borealis_launch_decide(verb, status, null_message ? nullptr : msg, null_message ? 0 : len, attempts, &out);

  const bool valid_verb = verb == BOREALIS_LAUNCH_VERB_LAUNCH || verb == BOREALIS_LAUNCH_VERB_RESUME;
  const std::size_t used_len = null_message ? 0 : len;
  if (!valid_verb) {
    check(rc == BOREALIS_ERR_INVALID_ARGUMENT);
  } else if (used_len > BOREALIS_HOST_MESSAGE_MAX_LEN) {
    check(rc == BOREALIS_ERR_INPUT_TOO_LARGE);
  } else {
    check(rc == BOREALIS_OK);
  }
  if (rc != BOREALIS_OK) {
    check(out.action == 0 && out.delay_ms == 0 && out.category == 0 && out.max_attempts == 0);
  } else {
    check(out.action >= BOREALIS_LAUNCH_ACTION_FAIL && out.action <= BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH);
    check(out.category >= BOREALIS_LAUNCH_CATEGORY_PERMANENT && out.category <= BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP);
    check(out.max_attempts >= 0 && out.max_attempts <= 4);
    check(out.delay_ms >= 0 && out.delay_ms <= 5000);
    if (out.action == BOREALIS_LAUNCH_ACTION_RETRY) {
      check(out.delay_ms > 0 && attempts < static_cast<uint32_t>(out.max_attempts));
    } else {
      check(out.delay_ms == 0);
    }
    if (out.action == BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH) {
      check(verb == BOREALIS_LAUNCH_VERB_RESUME && status == 503);
    }
    if (status != 409 && status != 503) {
      check(out.category == BOREALIS_LAUNCH_CATEGORY_PERMANENT && out.action == BOREALIS_LAUNCH_ACTION_FAIL);
    }
  }

  // The same bytes drive the frame rate: is_gfe, launch fps and stream fps.
  int32_t fps_bytes[2] = {0, 0};
  std::memcpy(fps_bytes, data, sizeof(fps_bytes));
  const int32_t fps = borealis_launch_fps(verb, fps_bytes[0], fps_bytes[1]);
  check(fps >= 0);
  return 0;
}
