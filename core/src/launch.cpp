#include "borealis/host.h"

extern "C" int borealis_launch_decide(int32_t, int32_t, const char *, size_t, uint32_t, borealis_launch_decision *) {
  return BOREALIS_OK;
}

extern "C" int32_t borealis_launch_fps(int32_t, int32_t, int32_t) {
  return 0;
}
