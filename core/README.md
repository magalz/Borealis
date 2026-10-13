# borealis-core

`borealis-core` is the C++20 library shared by all Borealis clients. It will hold the pure,
deterministic LuminalShine host rules: capability parsing, launch retry policy and display-mode
rules. Today it is a walking skeleton that exposes a single function, `borealis_core_version()`.

## Scope and non-goals

In scope: pure logic with no I/O and no global mutable state, so it is trivially unit-testable,
fuzzable and identical on every platform.

Not in scope: HTTP/networking, UI, video/audio decoding, input handling, persistence. Those live
in the platform clients (`apps/android` today, a Qt desktop client later).

## C ABI rules

The only thing other languages see is `include/borealis/core.h`:

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

```
core/
  CMakeLists.txt        library target borealis_core (alias borealis::core)
  CMakePresets.json     dev / asan / release presets
  include/borealis/     public C header(s)
  src/                  implementation; version_config.h.in is generated into the build dir
  tests/                GoogleTest unit tests (fetched with a pinned URL + SHA256)
  fuzz/                 libFuzzer scaffolding (no targets yet, see fuzz/README.md)
  .clang-format         copied from LuminalShine
  .clang-tidy
```

## Build and test

Requires CMake 3.25+, Ninja and a C++20 compiler (clang, gcc or MSVC).

```sh
cd core
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

Presets: `dev` (Debug), `asan` (Debug + ASan/UBSan), `release`. Build trees go to `core/build/<preset>`.
On Windows without a Visual Studio environment, put the compiler and `ninja` on `PATH` (and set
`CXX`), or add a git-ignored `CMakeUserPresets.json`.

CMake options:

| Option                              | Default                          | Meaning                                           |
| ----------------------------------- | -------------------------------- | ------------------------------------------------- |
| `BOREALIS_CORE_BUILD_TESTS`         | ON only if core is top-level     | Build the GoogleTest suite (fetches GoogleTest)   |
| `BOREALIS_CORE_WARNINGS_AS_ERRORS`  | OFF (CI turns it ON)             | `-Werror` / `/WX`                                 |
| `BOREALIS_CORE_SANITIZE`            | OFF                              | `-fsanitize=address,undefined` for core and tests |
| `BOREALIS_CORE_BUILD_FUZZERS`       | OFF                              | libFuzzer helper and targets (clang only)         |

`BUILD_SHARED_LIBS` is respected; the default is a static, position-independent library. Warnings
and sanitizer flags are applied per target and never leak to a parent project.

## Formatting and static analysis

```sh
clang-format --dry-run --Werror include/borealis/*.h src/*.cpp tests/*.cpp
clang-tidy -p build/dev src/*.cpp
```

The dev preset exports `compile_commands.json` for clang-tidy.

## Sanitizers and fuzzing

- ASan/UBSan: `cmake --preset asan && cmake --build --preset asan && ctest --preset asan`. CI runs
  this on Linux. A consumer that wants sanitized binaries must also link its own targets with
  `-fsanitize=address,undefined`.
- Fuzzing: parsers get libFuzzer targets starting in Phase 1; see `fuzz/README.md`. libFuzzer needs
  a clang target that supports it (Linux CI), not the Windows MinGW target.

## Consuming borealis-core

Android (via the wrapper module) and the future Qt desktop client both consume core from source:

```cmake
add_subdirectory(${REPO_ROOT}/core core)        # no tests, no fetches by default
target_link_libraries(my_target PRIVATE borealis::core)
```

The library is compiled with `-fPIC`, so it can be linked into Android shared objects. The public
include directory (`core/include`) and the C++20 requirement propagate through `borealis::core`.
