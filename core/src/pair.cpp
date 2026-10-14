#include "borealis/host.h"
#include "match.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <string_view>

namespace borealis::core {

  namespace {

    // The pairing errors of LuminalShine src/nvhttp.cpp (docs/host-contract/luminalshine.md,
    // section 5). A message only counts with the status the host sends it with.
    struct Rule {
      int32_t status;
      std::string_view needle;  // lower case
      borealis_pair_decision decision;
    };

    constexpr std::array<Rule, 9> kRules {{
      {400, "out of order call to getservercert", {BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_STALE_SESSION}},
      {400, "invalid uniqueid", {BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_STALE_SESSION}},
      {400, "pairing exchange invalidated", {BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_EXCHANGE_INVALIDATED}},
      {410, "host pairing state was reset", {BOREALIS_PAIR_ACTION_RESTART_ONCE, BOREALIS_PAIR_CATEGORY_HOST_RESET}},
      {409, "pairing exchange already in progress", {BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_IN_PROGRESS}},
      {409, "pairing request identity mismatch", {BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_IDENTITY_MISMATCH}},
      {503, "too many pending pairing requests", {BOREALIS_PAIR_ACTION_WAIT_HOST_SESSION, BOREALIS_PAIR_CATEGORY_TOO_MANY_PENDING}},
      {403, "pairing request peer mismatch", {BOREALIS_PAIR_ACTION_FAIL, BOREALIS_PAIR_CATEGORY_PEER_MISMATCH}},
      {409, "pairing request superseded by retry", {BOREALIS_PAIR_ACTION_FAIL, BOREALIS_PAIR_CATEGORY_SUPERSEDED}},
    }};

  }  // namespace

}  // namespace borealis::core

extern "C" int borealis_pair_classify(int32_t status, const char *msg, size_t len, borealis_pair_decision *out) noexcept {
  using namespace borealis::core;

  if (out == nullptr) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  *out = borealis_pair_decision {};
  if (msg == nullptr && len > 0) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  if (len > BOREALIS_HOST_MESSAGE_MAX_LEN) {
    return BOREALIS_ERR_INPUT_TOO_LARGE;
  }

  const std::string_view text(msg != nullptr ? msg : "", len);
  for (const Rule &rule : kRules) {
    if (status == rule.status && contains_ci(text, rule.needle)) {
      *out = rule.decision;
      break;
    }
  }
  return BOREALIS_OK;
}
