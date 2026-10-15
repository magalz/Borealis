#include "borealis/host.h"
#include "match.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <iterator>
#include <span>
#include <string_view>

namespace borealis::core {

  namespace {

    // Retry delays per category (docs/host-contract/luminalshine.md, section 7.5);
    // the number of delays is the number of retries the category allows.
    constexpr std::array<int32_t, 4> kPendingDelays {1000, 2000, 3000, 5000};
    constexpr std::array<int32_t, 3> kRestoringDelays {2000, 3000, 5000};
    constexpr std::array<int32_t, 2> kGpuDelays {5000, 5000};
    constexpr std::array<int32_t, 1> kCaptureDelays {3000};

    struct Classification {
      int32_t category;
      std::span<const int32_t> delays;
    };

    Classification classify(bool is_resume, int32_t status, std::string_view msg) {
      if (status == 400 && !is_resume && contains_ci(msg, "an app is already running")) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_APP_RUNNING, .delays = {}};
      }
      if (status == 409) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_PENDING, .delays = kPendingDelays};
      }
      if (status != 503) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_PERMANENT, .delays = {}};
      }
      if (is_resume && contains_ci(msg, "no running app to resume")) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP, .delays = {}};
      }
      if (contains_ci(msg, "restoring the display")) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY, .delays = kRestoringDelays};
      }
      if (contains_ci(msg, "gpu/display stack is not ready")) {
        return {.category = BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY, .delays = kGpuDelays};
      }
      return {.category = BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, .delays = kCaptureDelays};
    }

  }  // namespace

}  // namespace borealis::core

// NOLINTBEGIN(bugprone-easily-swappable-parameters): the C ABI order is fixed.
extern "C" int borealis_launch_decide(
  int32_t verb,
  int32_t status,
  const char *msg,
  size_t len,
  uint32_t attempts_in_category,
  borealis_launch_decision *out
) noexcept {
  using namespace borealis::core;

  if (out == nullptr) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  *out = borealis_launch_decision {};
  if ((verb != BOREALIS_LAUNCH_VERB_LAUNCH && verb != BOREALIS_LAUNCH_VERB_RESUME) || (msg == nullptr && len > 0)) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  if (len > BOREALIS_HOST_MESSAGE_MAX_LEN) {
    return BOREALIS_ERR_INPUT_TOO_LARGE;
  }

  const Classification found = classify(verb == BOREALIS_LAUNCH_VERB_RESUME, status, std::string_view(msg != nullptr ? msg : "", len));
  out->category = found.category;
  out->max_attempts = static_cast<int32_t>(found.delays.size());
  if (found.category == BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP) {
    out->action = BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH;
  } else if (found.category == BOREALIS_LAUNCH_CATEGORY_APP_RUNNING) {
    out->action = BOREALIS_LAUNCH_ACTION_RESOLVE_CONFLICT;
  } else if (attempts_in_category < found.delays.size()) {
    out->action = BOREALIS_LAUNCH_ACTION_RETRY;
    out->delay_ms = *std::next(found.delays.begin(), static_cast<std::ptrdiff_t>(attempts_in_category));
  }
  return BOREALIS_OK;
}

// NOLINTEND(bugprone-easily-swappable-parameters)

// NOLINTNEXTLINE(bugprone-easily-swappable-parameters): the C ABI order is fixed.
extern "C" int32_t borealis_launch_fps(int32_t is_gfe, int32_t launch_fps, int32_t stream_fps) noexcept {
  if (is_gfe == 0 && stream_fps > 0) {
    return stream_fps;
  }
  // Upstream rule: GFE falls back to 720p60 when the mode asks for more than 60 fps.
  return launch_fps > 60 || launch_fps < 0 ? 0 : launch_fps;
}
