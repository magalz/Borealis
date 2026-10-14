#include "borealis/core.h"
#include "borealis/version_config.h"

namespace borealis::core {

  // Static storage duration: the pointer stays valid for the process lifetime.
  inline constexpr const char *kVersion = BOREALIS_CORE_VERSION_STRING;

}  // namespace borealis::core

extern "C" const char *borealis_core_version(void) {
  return borealis::core::kVersion;
}
