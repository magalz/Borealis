#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <gtest/gtest.h>
#include <initializer_list>
#include <string>
#include <string_view>

namespace {

  constexpr std::string_view kPending = "Another client launch is already pending; retry the connection.";
  constexpr std::string_view kRestoring = "The previous stream is still restoring the display; retry the connection.";
  constexpr std::string_view kGpu = "GPU/display stack is not ready; retry after recovery or reboot the host.";
  constexpr std::string_view kCapture = "Failed to initialize video capture/encoding. Is a display connected and turned on?";
  constexpr std::string_view kPrepare = "Failed to prepare the client virtual display.";
  constexpr std::string_view kNoApp = "No running app to resume";

  constexpr int32_t kVerbs[] = {BOREALIS_LAUNCH_VERB_LAUNCH, BOREALIS_LAUNCH_VERB_RESUME};

  borealis_launch_decision poisoned() {
    return borealis_launch_decision {99, 99, 99, 99};
  }

  borealis_launch_decision decide(int32_t verb, int32_t status, std::string_view msg, uint32_t attempts) {
    borealis_launch_decision out = poisoned();
    EXPECT_EQ(borealis_launch_decide(verb, status, msg.data(), msg.size(), attempts, &out), BOREALIS_OK);
    return out;
  }

  borealis_launch_decision decide_null_msg(int32_t verb, int32_t status, uint32_t attempts) {
    borealis_launch_decision out = poisoned();
    EXPECT_EQ(borealis_launch_decide(verb, status, nullptr, 0, attempts, &out), BOREALIS_OK);
    return out;
  }

  void expect_retries(int32_t verb, int32_t status, std::string_view msg, int32_t category, std::initializer_list<int32_t> delays) {
    int32_t attempt = 0;
    for (const int32_t delay : delays) {
      const auto out = decide(verb, status, msg, static_cast<uint32_t>(attempt));
      EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_RETRY) << "attempt " << attempt;
      EXPECT_EQ(out.delay_ms, delay) << "attempt " << attempt;
      EXPECT_EQ(out.category, category) << "attempt " << attempt;
      EXPECT_EQ(out.max_attempts, static_cast<int32_t>(delays.size())) << "attempt " << attempt;
      ++attempt;
    }
    for (const uint32_t exhausted : {static_cast<uint32_t>(delays.size()), static_cast<uint32_t>(delays.size()) + 1U, 1000U, UINT32_MAX}) {
      const auto out = decide(verb, status, msg, exhausted);
      EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_FAIL) << "attempts " << exhausted;
      EXPECT_EQ(out.delay_ms, 0) << "attempts " << exhausted;
      EXPECT_EQ(out.category, category) << "attempts " << exhausted;
      EXPECT_EQ(out.max_attempts, static_cast<int32_t>(delays.size())) << "attempts " << exhausted;
    }
  }

  void expect_permanent(int32_t verb, int32_t status, std::string_view msg) {
    for (const uint32_t attempts : {0U, 1U, 5U}) {
      const auto out = decide(verb, status, msg, attempts);
      EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_FAIL) << status;
      EXPECT_EQ(out.delay_ms, 0) << status;
      EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_PERMANENT) << status;
      EXPECT_EQ(out.max_attempts, 0) << status;
    }
  }

}  // namespace

TEST(LaunchDecidePending, RetriesWithBackoffThenFails) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 409, kPending, BOREALIS_LAUNCH_CATEGORY_PENDING, {1000, 2000, 3000, 5000});
  }
}

TEST(LaunchDecidePending, AnyOther409IsPendingToo) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 409, "Something else entirely", BOREALIS_LAUNCH_CATEGORY_PENDING, {1000, 2000, 3000, 5000});
    expect_retries(verb, 409, "", BOREALIS_LAUNCH_CATEGORY_PENDING, {1000, 2000, 3000, 5000});
  }
}

TEST(LaunchDecidePending, MessageLessIsPending) {
  for (const int32_t verb : kVerbs) {
    const auto out = decide_null_msg(verb, 409, 0);
    EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_RETRY);
    EXPECT_EQ(out.delay_ms, 1000);
    EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_PENDING);
    EXPECT_EQ(decide_null_msg(verb, 409, 4).action, BOREALIS_LAUNCH_ACTION_FAIL);
  }
}

TEST(LaunchDecideRestoring, RetriesThenFails) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 503, kRestoring, BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY, {2000, 3000, 5000});
  }
}

TEST(LaunchDecideGpu, RetriesTwiceThenFails) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 503, kGpu, BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY, {5000, 5000});
  }
}

TEST(LaunchDecideCapture, EncoderProbeFailureRetriesOnce) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 503, kCapture, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, {3000});
  }
}

TEST(LaunchDecideCapture, VirtualDisplayFailuresRetryOnce) {
  for (const std::string_view msg : {
         kPrepare,
         std::string_view("Windows did not activate the virtual display."),
         std::string_view("Windows did not apply the requested virtual-display mode."),
         std::string_view("The virtual display activated, but its capture ring did not begin publishing frames."),
         std::string_view("The Windows display helper was unavailable."),
         std::string_view("Display configuration failed."),
       }) {
    for (const int32_t verb : kVerbs) {
      expect_retries(verb, 503, msg, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, {3000});
    }
  }
}

TEST(LaunchDecideCapture, UnknownAndMessageLess503RetryOnce) {
  for (const int32_t verb : kVerbs) {
    expect_retries(verb, 503, "Something unexpected", BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, {3000});
    expect_retries(verb, 503, "", BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, {3000});
    const auto out = decide_null_msg(verb, 503, 0);
    EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_RETRY);
    EXPECT_EQ(out.delay_ms, 3000);
    EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY);
    EXPECT_EQ(decide_null_msg(verb, 503, 1).action, BOREALIS_LAUNCH_ACTION_FAIL);
  }
}

TEST(LaunchDecideNoRunningApp, ResumeFallsBackToLaunch) {
  for (const uint32_t attempts : {0U, 1U, 7U, UINT32_MAX}) {
    const auto out = decide(BOREALIS_LAUNCH_VERB_RESUME, 503, kNoApp, attempts);
    EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH);
    EXPECT_EQ(out.delay_ms, 0);
    EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP);
    EXPECT_EQ(out.max_attempts, 0);
  }
}

TEST(LaunchDecideNoRunningApp, OnlyResumeFallsBack) {
  // The host never sends this text for /launch; if it did, treat it as an ordinary 503.
  expect_retries(BOREALIS_LAUNCH_VERB_LAUNCH, 503, kNoApp, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY, {3000});
}

TEST(LaunchDecideNoRunningApp, OtherStatusesDoNotFallBack) {
  expect_permanent(BOREALIS_LAUNCH_VERB_RESUME, 404, kNoApp);
  expect_permanent(BOREALIS_LAUNCH_VERB_RESUME, 400, kNoApp);
}

TEST(LaunchDecideMatching, IsCaseInsensitive) {
  for (const int32_t verb : kVerbs) {
    EXPECT_EQ(
      decide(verb, 503, "THE PREVIOUS STREAM IS STILL RESTORING THE DISPLAY; RETRY THE CONNECTION.", 0).category,
      BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY
    );
    EXPECT_EQ(decide(verb, 503, "gpu/display stack is NOT READY", 0).category, BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY);
  }
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_RESUME, 503, "no RUNNING app TO resume", 0).category, BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP);
}

TEST(LaunchDecideMatching, FindsTheTextAnywhereInTheMessage) {
  EXPECT_EQ(
    decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, "prefix: restoring the display :suffix", 0).category,
    BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY
  );
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, "GPU/display stack is not ready", 0).category, BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY);
}

TEST(LaunchDecideMatching, PartialPhraseDoesNotMatch) {
  EXPECT_EQ(
    decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, "restoring the displa", 0).category,
    BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY
  );
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, "restoring", 0).category, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY);
}

TEST(LaunchDecideMatching, MessageIsLengthBoundedNotNulTerminated) {
  // The buffer continues past len with text that would match; only len bytes count.
  const std::string_view buffer = "restoring the display";
  const auto out = decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, buffer.substr(0, 10), 0);
  EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY);
}

TEST(LaunchDecideMatching, EmbeddedNulIsAnOrdinaryByte) {
  const std::string split("restoring the\0display", 21);
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, split, 0).category, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY);
  const std::string tail("\0restoring the display", 22);
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, tail, 0).category, BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY);
}

TEST(LaunchDecideMatching, NonAsciiBytesAreNotFolded) {
  // 0xE1 & 0x7F is 'a': a case fold that masks the high bit would wrongly match.
  const std::string text =
    "gpu/display stack is not re\xE1"
    "dy";
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, text, 0).category, BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY);
}

TEST(LaunchDecidePermanent, EveryOtherStatusFailsWithoutRetry) {
  for (const int32_t verb : kVerbs) {
    for (const int32_t status : {400, 403, 404, -1, 418, 470, 525, 599, 200, 0, 500, 502, 504, 408, 429, INT32_MIN, INT32_MAX}) {
      expect_permanent(verb, status, "Whatever the host said");
      expect_permanent(verb, status, "");
    }
  }
}

TEST(LaunchDecidePermanent, MessagesOfRetryableCategoriesDoNotLeakAcrossStatuses) {
  for (const int32_t verb : kVerbs) {
    expect_permanent(verb, 500, kPending);
    expect_permanent(verb, 502, kRestoring);
    expect_permanent(verb, 403, kGpu);
    expect_permanent(verb, 404, kCapture);
  }
}

TEST(LaunchDecidePermanent, HostTableRows) {
  for (const int32_t verb : kVerbs) {
    expect_permanent(verb, 400, "Missing a required launch parameter");
    expect_permanent(verb, 400, "An app is already running on this host");
    expect_permanent(verb, 403, "Encryption is mandatory for this host but unsupported by the client");
    expect_permanent(verb, 404, "Failed to start the specified application");
    expect_permanent(verb, -1, "Failed to start the specified application");
  }
}

TEST(LaunchDecideLimits, LongestAcceptedMessageIsClassified) {
  std::string msg(BOREALIS_HOST_MESSAGE_MAX_LEN - kGpu.size(), ' ');
  msg += kGpu;
  ASSERT_EQ(msg.size(), static_cast<std::size_t>(BOREALIS_HOST_MESSAGE_MAX_LEN));
  EXPECT_EQ(decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, msg, 0).category, BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY);
}

TEST(LaunchDecideLimits, OverLongMessageFailsAndZeroesOutput) {
  const std::string msg(BOREALIS_HOST_MESSAGE_MAX_LEN + 1, 'a');
  borealis_launch_decision out = poisoned();
  EXPECT_EQ(
    borealis_launch_decide(BOREALIS_LAUNCH_VERB_LAUNCH, 503, msg.data(), msg.size(), 0, &out),
    BOREALIS_ERR_INPUT_TOO_LARGE
  );
  EXPECT_EQ(out.action, BOREALIS_LAUNCH_ACTION_FAIL);
  EXPECT_EQ(out.delay_ms, 0);
  EXPECT_EQ(out.category, BOREALIS_LAUNCH_CATEGORY_PERMANENT);
  EXPECT_EQ(out.max_attempts, 0);
}

TEST(LaunchDecideLimits, NullMessageWithLengthFailsAndZeroesOutput) {
  borealis_launch_decision out = poisoned();
  EXPECT_EQ(borealis_launch_decide(BOREALIS_LAUNCH_VERB_LAUNCH, 409, nullptr, 3, 0, &out), BOREALIS_ERR_INVALID_ARGUMENT);
  EXPECT_EQ(out.action, 0);
  EXPECT_EQ(out.delay_ms, 0);
  EXPECT_EQ(out.category, 0);
  EXPECT_EQ(out.max_attempts, 0);
}

TEST(LaunchDecideLimits, UnknownVerbFailsAndZeroesOutput) {
  for (const int32_t verb : {-1, 2, 100, INT32_MIN, INT32_MAX}) {
    borealis_launch_decision out = poisoned();
    EXPECT_EQ(borealis_launch_decide(verb, 409, nullptr, 0, 0, &out), BOREALIS_ERR_INVALID_ARGUMENT) << verb;
    EXPECT_EQ(out.action, 0);
    EXPECT_EQ(out.delay_ms, 0);
    EXPECT_EQ(out.category, 0);
    EXPECT_EQ(out.max_attempts, 0);
  }
}

TEST(LaunchDecideLimits, NullOutputFails) {
  EXPECT_EQ(
    borealis_launch_decide(BOREALIS_LAUNCH_VERB_LAUNCH, 409, nullptr, 0, 0, nullptr),
    BOREALIS_ERR_INVALID_ARGUMENT
  );
}
