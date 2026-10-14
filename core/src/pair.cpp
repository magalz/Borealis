#include "borealis/host.h"

#include <cstddef>
#include <cstdint>

extern "C" int borealis_pair_classify(int32_t status, const char *msg, size_t len, borealis_pair_decision *out) noexcept {
  static_cast<void>(status);
  static_cast<void>(msg);
  static_cast<void>(len);
  if (out == nullptr) {
    return BOREALIS_ERR_INVALID_ARGUMENT;
  }
  *out = borealis_pair_decision {};
  return BOREALIS_OK;
}
