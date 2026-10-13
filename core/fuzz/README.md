# Fuzzing borealis-core

This directory only contains scaffolding. There are intentionally no fuzz targets yet:
fuzzers arrive together with the first parser (LuminalShine capability parsing, Phase 1).

## Adding a fuzzer

1. Write `fuzz/<name>_fuzz.cpp` exposing `extern "C" int LLVMFuzzerTestOneInput(const uint8_t*, size_t)`.
2. Register it in `fuzz/CMakeLists.txt` after the helper definition:

   ```cmake
   borealis_add_fuzzer(capabilities_fuzz capabilities_fuzz.cpp)
   ```

   `borealis_add_fuzzer(name source)` builds a libFuzzer executable linked against
   `borealis::core` with `-fsanitize=fuzzer,address,undefined`.

## Building and running

Fuzzers require clang and are off by default:

```sh
cmake -S core -B core/build/fuzz -G Ninja -DCMAKE_CXX_COMPILER=clang++ \
      -DBOREALIS_CORE_BUILD_FUZZERS=ON -DBOREALIS_CORE_SANITIZE=ON
cmake --build core/build/fuzz
core/build/fuzz/capabilities_fuzz corpus/ -max_total_time=60
```

Configuring with `BOREALIS_CORE_BUILD_FUZZERS=ON` and a non-clang compiler fails on purpose.
Commit minimized seed corpora and crash reproducers next to the fuzzer once they exist.
