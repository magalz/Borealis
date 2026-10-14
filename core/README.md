# borealis-core

`borealis-core` is the C++20 library shared by all Borealis clients. It will hold the pure,
deterministic LuminalShine host rules: capability parsing, launch retry policy and display-mode
rules. It exposes `borealis_core_version()` and, in `include/borealis/host.h`, the LuminalShine host
rules described below.

## Scope and non-goals

In scope: pure logic with no I/O and no global mutable state, so it is trivially unit-testable,
fuzzable and identical on every platform.

Not in scope: HTTP/networking, UI, video/audio decoding, input handling, persistence. Those live
in the platform clients (`apps/android` today, a Qt desktop client later).

## C ABI rules

The only things other languages see are `include/borealis/core.h` and `include/borealis/host.h`:

- Plain C declarations inside `extern "C"`; the header must compile as C and C++.
- Every exported function is marked `BOREALIS_CORE_API` (default visibility on clang/gcc,
  `dllexport`/`dllimport` for Windows shared builds). The library is compiled with hidden
  visibility, so anything not marked is not exported.
- No exceptions and no STL types cross the ABI. Use C types, caller-provided buffers with explicit
  sizes, or opaque handles with matching create/destroy functions.
- Every function documents who owns returned memory. For example, `borealis_core_version()` returns
  a static, null-terminated string that is never NULL and must not be freed.
- Internals live in the `borealis::core` namespace and in `src/`; they are not part of the ABI.

## Layout

```text
core/
  CMakeLists.txt        library target borealis_core (alias borealis::core)
  CMakePresets.json     dev / asan / release / coverage presets
  scripts/              coverage.sh (llvm-cov lcov report)
  include/borealis/     public C headers (core.h, host.h)
  src/                  implementation; version_config.h.in is generated into the build dir
  tests/                GoogleTest unit tests (fetched with a pinned URL + SHA256)
  tests/fixtures/       host responses and golden vectors (see fixtures/README.md)
  fuzz/                 libFuzzer targets (see fuzz/README.md)
  .clang-format         copied from LuminalShine
  .clang-tidy
```

## Host rules API (`borealis/host.h`)

Pure and stateless: no I/O, no allocation, no global state, thread-safe. Strings are `(pointer, length)`
pairs, are not NUL-terminated by contract and are never retained after the call; the caller owns every
buffer and every output struct. A NULL pointer is valid only with length 0. Every function that returns
`int` yields `BOREALIS_OK` (0), `BOREALIS_ERR_INVALID_ARGUMENT` (NULL `out`, NULL pointer with a
non-zero length, unknown verb) or `BOREALIS_ERR_INPUT_TOO_LARGE`, and on any error zeroes `out`
(all-zero is the safe value of every struct). Structs use fixed-width integers and `#define` constants so
the JNI layer can map them to an `IntArray`.

| Function | Purpose |
| --- | --- |
| `borealis_host_parse_caps(appversion, len, codec_mask, out)` | Classifies the host (`UNKNOWN`, `GFE`, `SUNSHINE`) from `appversion`, parses its four version parts, and derives `codec_flags` (HEVC, HEVC Main10, AV1, AV1 Main10, any 4:4:4) and the four PyroWave `pyrowave_profiles` bits from `ServerCodecModeSupport`. `appversion` is at most `BOREALIS_HOST_APPVERSION_MAX_LEN` (64) bytes. |
| `borealis_launch_decide(verb, status, msg, len, attempts_in_category, out)` | Decides `FAIL`, `RETRY` (with `delay_ms`), `FALLBACK_TO_LAUNCH` or `RESOLVE_CONFLICT` after a non-200 `/launch` or `/resume`, from the status and an ASCII case-insensitive substring of the host message (at most `BOREALIS_HOST_MESSAGE_MAX_LEN`, 256, bytes). |
| `borealis_pair_classify(status, msg, len, out)` | Classifies a `/pair` error from the status and an ASCII case-insensitive substring of the host message: `RESTART_ONCE` (the host dropped its session), `WAIT_HOST_SESSION` (the host holds another session) or `FAIL`, with a `category` that selects the user message. A known message only counts with the status the host sends it with. |
| `borealis_launch_fps(is_gfe, launch_fps, stream_fps)` | The fps for the `/launch` `mode=` parameter: the upstream GFE rule, or the RTSP `maxFPS` for any other host. Cannot fail. |

`appversion` is exactly `a.b.c.d` (decimal, no whitespace, only the fourth part may be negative):
a negative fourth part is `SUNSHINE` (LuminalShine sends `7.1.431.-1`), a non-negative one `GFE`, and
anything else is `UNKNOWN` with a zero version, which is not an error. Mask bits that Borealis does not
know are ignored.

`borealis_launch_decide` is stateless. The caller counts the retries it already made in the returned
`category` and passes the count back as `attempts_in_category`; core answers `RETRY` while the count is
below `max_attempts` and `FAIL` afterwards. The caller also owns the overall time budget.

| Situation | Category | Retries (`delay_ms`) |
| --- | --- | --- |
| 409, with any message | `PENDING` | 4 (1000, 2000, 3000, 5000) |
| 503 "restoring the display" | `RESTORING_DISPLAY` | 3 (2000, 3000, 5000) |
| 503 "GPU/display stack is not ready" | `GPU_NOT_READY` | 2 (5000, 5000) |
| any other or message-less 503 (capture, encoder, virtual display) | `CAPTURE_OR_DISPLAY` | 1 (3000) |
| `RESUME` with 503 "No running app to resume" | `NO_RUNNING_APP` | `FALLBACK_TO_LAUNCH` |
| `LAUNCH` with 400 "An app is already running on this host" | `APP_RUNNING` | `RESOLVE_CONFLICT`: the caller asks the user (join, quit and relaunch, cancel) and never cancels the other app unasked |
| every other status | `PERMANENT` | none, `FAIL` |

`borealis_pair_classify` is stateless too; the caller owns the single restart:

| Status and host message | Category | Action |
| --- | --- | --- |
| 400 "Out of order call to getservercert", 400 "Invalid uniqueid" | `STALE_SESSION` | `RESTART_ONCE` |
| 400 "Pairing exchange invalidated" | `EXCHANGE_INVALIDATED` | `RESTART_ONCE` |
| 410 "Host pairing state was reset" | `HOST_RESET` | `RESTART_ONCE` |
| 409 "Pairing exchange already in progress" | `IN_PROGRESS` | `WAIT_HOST_SESSION` |
| 409 "Pairing request identity mismatch" (another device is pairing) | `IDENTITY_MISMATCH` | `WAIT_HOST_SESSION` |
| 503 "Too many pending pairing requests" | `TOO_MANY_PENDING` | `WAIT_HOST_SESSION` |
| 403 "Pairing request peer mismatch" | `PEER_MISMATCH` | `FAIL` |
| 409 "Pairing request superseded by retry" | `SUPERSEDED` | `FAIL` |
| anything else | `UNKNOWN` | `FAIL` |

The rules come from `docs/host-contract/luminalshine.md` (sections 5, 6, 7.4 and 7.5) and
`docs/host-contract/pyrowave.md` (section 3.1). Golden vectors for the three decision functions live in
`tests/fixtures/vectors/` and are replayed by the unit tests; the Android JNI instrumented test (task T4) will replay the same files.

## Build and test

Requires CMake 3.25+, Ninja and a C++20 compiler (clang, gcc or MSVC).

```sh
cd core
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

Presets: `dev` (Debug), `asan` (Debug + ASan/UBSan), `release`, `coverage` (Debug + llvm-cov, clang). Build trees go to `core/build/<preset>`.
On Windows without a Visual Studio environment, put the compiler and `ninja` on `PATH` (and set
`CXX`), or add a git-ignored `CMakeUserPresets.json`.

CMake options:

| Option                              | Default                          | Meaning                                           |
| ----------------------------------- | -------------------------------- | ------------------------------------------------- |
| `BOREALIS_CORE_BUILD_TESTS`         | ON only if core is top-level     | Build the GoogleTest suite (fetches GoogleTest)   |
| `BOREALIS_CORE_WARNINGS_AS_ERRORS`  | OFF (CI turns it ON)             | `-Werror` / `/WX`                                 |
| `BOREALIS_CORE_SANITIZE`            | OFF                              | `-fsanitize=address,undefined` for core and tests |
| `BOREALIS_CORE_BUILD_FUZZERS`       | OFF                              | libFuzzer helper and targets (clang only)         |
| `BOREALIS_CORE_COVERAGE`            | OFF                              | llvm-cov instrumentation (clang only)             |

`BUILD_SHARED_LIBS` is respected; the default is a static, position-independent library. Warnings
and sanitizer flags are applied per target and never leak to a parent project.

## Coverage

Source-based coverage with clang and llvm-cov. `BOREALIS_CORE_COVERAGE=ON` adds
`-fprofile-instr-generate -fcoverage-mapping` to core and its tests only (per target, never to a
parent project) and fails configuration on a non-clang compiler. The `coverage` preset turns it on
and makes `ctest` write profiles to `core/build/coverage/profiles/`.

```sh
cd core
sh scripts/coverage.sh
```

The script configures, builds and tests the `coverage` preset, merges the profiles with
`llvm-profdata merge -sparse`, prints an `llvm-cov report` summary and writes
`core/build/coverage/coverage.lcov`. The report covers `include/` and `src/` only (no tests,
GoogleTest or generated files) and `SF:` paths are relative to the repository root
(`core/src/version.cpp`), which is what Codecov expects.

Environment variables: `CXX` (clang++), `LLVM_SUFFIX` (version suffix of the LLVM tools, for
example `-18` on Ubuntu), `LLVM_PROFDATA` and `LLVM_COV` (explicit tool paths). The compiler and
the LLVM tools must come from the same LLVM version. On Ubuntu: `sudo apt-get install clang llvm`.
On Windows the script runs under Git Bash with llvm-mingw (clang, `llvm-profdata`, `llvm-cov` on
`PATH`, plus `ninja`).

## Formatting and static analysis

```sh
clang-format --dry-run --Werror include/borealis/*.h src/*.cpp tests/*.cpp fuzz/*.cpp
clang-tidy -p build/dev src/*.cpp
```

The dev preset exports `compile_commands.json` for clang-tidy.

## Sanitizers and fuzzing

- ASan/UBSan: `cmake --preset asan && cmake --build --preset asan && ctest --preset asan`. CI runs
  this on Linux. A consumer that wants sanitized binaries must also link its own targets with
  `-fsanitize=address,undefined`.
- Fuzzing: `appversion_fuzz` and `launch_message_fuzz`, see `fuzz/README.md`. libFuzzer needs a clang
  target that supports it (Linux CI), not the Windows MinGW target.

## Consuming borealis-core

Android (via the wrapper module) and the future Qt desktop client both consume core from source:

```cmake
add_subdirectory(${REPO_ROOT}/core core)        # no tests, no fetches by default
target_link_libraries(my_target PRIVATE borealis::core)
```

The library is compiled with `-fPIC`, so it can be linked into Android shared objects. The public
include directory (`core/include`) and the C++20 requirement propagate through `borealis::core`.
