# Fuzzing borealis-core

libFuzzer targets for the parsers and decision functions that take untrusted host text.

| Target | Function under test | Input layout |
| --- | --- | --- |
| `appversion_fuzz` | `borealis_host_parse_caps` | 4 bytes codec mask, then the `appversion` text |
| `launch_message_fuzz` | `borealis_launch_decide`, `borealis_pair_classify`, `borealis_launch_fps` | 4 bytes status, 1 byte verb (modulo 3), 4 bytes attempts, 1 byte flags (bit 0: NULL message), then the host message (the same status and message also feed `borealis_pair_classify`) |

`fuzz/launch_message_fuzz.dict` lists the host messages and the substrings the decision matches, in original and lower case, so the fuzzer reaches every category quickly.

Both derive their numeric arguments from the input bytes and abort when a function crashes, returns a
status that does not match the input (for example over-long text must give `BOREALIS_ERR_INPUT_TOO_LARGE`),
leaves its output unzeroed on error, or produces an enum, delay or count outside the documented range.

## Adding a fuzzer

1. Write `fuzz/<name>_fuzz.cpp` exposing `extern "C" int LLVMFuzzerTestOneInput(const uint8_t*, size_t)`.
2. Register it in `fuzz/CMakeLists.txt` after the helper definition:

   ```cmake
   borealis_add_fuzzer(appversion_fuzz appversion_fuzz.cpp)
   ```

   `borealis_add_fuzzer(name source)` builds a libFuzzer executable linked against
   `borealis::core` with `-fsanitize=fuzzer,address,undefined`.

## Building and running

Fuzzers require a clang that ships libFuzzer and are off by default:

```sh
cmake -S core -B core/build/fuzz -G Ninja -DCMAKE_CXX_COMPILER=clang++ \
      -DBOREALIS_CORE_BUILD_FUZZERS=ON -DBOREALIS_CORE_SANITIZE=ON
cmake --build core/build/fuzz
core/build/fuzz/fuzz/appversion_fuzz corpus/appversion -max_total_time=60
core/build/fuzz/fuzz/launch_message_fuzz corpus/launch -dict=core/fuzz/launch_message_fuzz.dict -max_total_time=60
```

Configuring with `BOREALIS_CORE_BUILD_FUZZERS=ON` and a non-clang compiler fails on purpose.
The Windows llvm-mingw toolchain has no libFuzzer, so on Windows build and run the targets in WSL or rely on CI:
the `Core (C++)` job runs both targets for 60 s each (same commands, empty corpus) when `core/**` changes
and on every push to `main`, and uploads crash reproducers as the `core-fuzz-artifacts` artifact.
Commit minimized seed corpora and crash reproducers next to the fuzzer once they exist.
