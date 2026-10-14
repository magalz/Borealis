#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <gtest/gtest.h>
#include <string>
#include <string_view>

namespace {

  struct Case {
    int32_t status;
    std::string_view message;
    int32_t action;
    int32_t category;
  };

  // The exact host texts and statuses, from luminalshine src/nvhttp.cpp at 5afdbdc6.
  constexpr Case kHostCases[] = {
    {400, "Out of order call to getservercert", BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_STALE_SESSION},
    {400, "Invalid uniqueid", BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_STALE_SESSION},
    {400, "Pairing exchange invalidated", BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_EXCHANGE_INVALIDATED},
    {410, "Host pairing state was reset", BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_HOST_RESET},
    {409, "Pairing exchange already in progress", BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_IN_PROGRESS},
    {409, "Pairing request identity mismatch", BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_IDENTITY_MISMATCH},
    {503, "Too many pending pairing requests", BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_TOO_MANY_PENDING},
    {403, "Pairing request peer mismatch", BOREALIS_PAIR_ACTION_FAIL, BOREALIS_PAIR_CATEGORY_PEER_MISMATCH},
    {409, "Pairing request superseded by retry", BOREALIS_PAIR_ACTION_FAIL, BOREALIS_PAIR_CATEGORY_SUPERSEDED},
  };

  borealis_pair_decision poisoned() {
    return borealis_pair_decision {99, 99};
  }

  borealis_pair_decision classify(int32_t status, std::string_view msg) {
    borealis_pair_decision out = poisoned();
    EXPECT_EQ(borealis_pair_classify(status, msg.data(), msg.size(), &out), BOREALIS_OK);
    return out;
  }

  void expect_unknown(int32_t status, std::string_view msg) {
    const auto out = classify(status, msg);
    EXPECT_EQ(out.action, BOREALIS_PAIR_ACTION_FAIL) << status << " " << msg;
    EXPECT_EQ(out.category, BOREALIS_PAIR_CATEGORY_UNKNOWN) << status << " " << msg;
  }

}  // namespace

TEST(PairClassify, EveryHostMessageMapsToItsCategoryAndAction) {
  for (const Case &c : kHostCases) {
    const auto out = classify(c.status, c.message);
    EXPECT_EQ(out.action, c.action) << c.message;
    EXPECT_EQ(out.category, c.category) << c.message;
  }
}

TEST(PairClassify, MatchingIsCaseInsensitiveSubstring) {
  const auto upper = classify(400, "OUT OF ORDER CALL TO GETSERVERCERT");
  EXPECT_EQ(upper.action, BOREALIS_PAIR_ACTION_RESTART_ONCE);
  EXPECT_EQ(upper.category, BOREALIS_PAIR_CATEGORY_STALE_SESSION);
  const auto embedded = classify(409, "Error: pairing exchange already in progress (retry later)");
  EXPECT_EQ(embedded.action, BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION);
  EXPECT_EQ(embedded.category, BOREALIS_PAIR_CATEGORY_IN_PROGRESS);
}

TEST(PairClassify, KnownMessageWithAnotherStatusIsUnknown) {
  for (const Case &c : kHostCases) {
    expect_unknown(c.status == 500 ? 501 : 500, c.message);
    expect_unknown(200, c.message);
  }
  expect_unknown(409, "Out of order call to getservercert");
  expect_unknown(400, "Pairing exchange already in progress");
}

TEST(PairClassify, UnknownMessagesFail) {
  for (const int32_t status : {400, 403, 409, 410, 503, 500, 0, -1, INT32_MIN, INT32_MAX}) {
    expect_unknown(status, "Whatever the host said");
    expect_unknown(status, "");
    borealis_pair_decision out = poisoned();
    EXPECT_EQ(borealis_pair_classify(status, nullptr, 0, &out), BOREALIS_OK);
    EXPECT_EQ(out.action, BOREALIS_PAIR_ACTION_FAIL);
    EXPECT_EQ(out.category, BOREALIS_PAIR_CATEGORY_UNKNOWN);
  }
}

TEST(PairClassify, PartialPhraseDoesNotMatch) {
  expect_unknown(400, "Out of order call to getserver");
  expect_unknown(409, "Pairing exchange already");
}

TEST(PairClassify, EmbeddedNulIsAnOrdinaryByte) {
  const std::string split("Out of order call\0to getservercert", 34);
  expect_unknown(400, split);
  const std::string tail("\0Out of order call to getservercert", 35);
  EXPECT_EQ(classify(400, tail).category, BOREALIS_PAIR_CATEGORY_STALE_SESSION);
}

TEST(PairClassify, NonAsciiBytesAreNotFolded) {
  // 0xE1 & 0x7F is 'a': a case fold that masks the high bit would wrongly match.
  const std::string folded =
    "Pairing request peer mism\xE1"
    "tch";
  expect_unknown(403, folded);
}

TEST(PairClassify, LongestAcceptedMessageIsClassified) {
  constexpr std::string_view needle = "Too many pending pairing requests";
  std::string msg(BOREALIS_HOST_MESSAGE_MAX_LEN - needle.size(), ' ');
  msg += needle;
  ASSERT_EQ(msg.size(), static_cast<std::size_t>(BOREALIS_HOST_MESSAGE_MAX_LEN));
  EXPECT_EQ(classify(503, msg).category, BOREALIS_PAIR_CATEGORY_TOO_MANY_PENDING);
}

TEST(PairClassify, OverLongMessageFailsAndZeroesOutput) {
  const std::string msg(BOREALIS_HOST_MESSAGE_MAX_LEN + 1, 'a');
  borealis_pair_decision out = poisoned();
  EXPECT_EQ(borealis_pair_classify(400, msg.data(), msg.size(), &out), BOREALIS_ERR_INPUT_TOO_LARGE);
  EXPECT_EQ(out.action, 0);
  EXPECT_EQ(out.category, 0);
}

TEST(PairClassify, NullMessageWithLengthFailsAndZeroesOutput) {
  borealis_pair_decision out = poisoned();
  EXPECT_EQ(borealis_pair_classify(400, nullptr, 3, &out), BOREALIS_ERR_INVALID_ARGUMENT);
  EXPECT_EQ(out.action, 0);
  EXPECT_EQ(out.category, 0);
}

TEST(PairClassify, NullOutputFails) {
  EXPECT_EQ(borealis_pair_classify(400, nullptr, 0, nullptr), BOREALIS_ERR_INVALID_ARGUMENT);
}
