# Building

Primary development platform is Windows. Linux notes for CI are at the end.

## Prerequisites

| Tool | Version | Used for |
| --- | --- | --- |
| Git | recent | Source control, subtree and submodules. |
| JDK | 17 | Gradle and Android build. |
| Android SDK | platform `android-37.0`, build-tools `37.0.0`, platform-tools | Android client. |
| Android NDK | `29.0.14206865` | Native code (moonlight-core). |
| CMake | `4.1.2` (SDK) for Android; host `core/` needs 3.25 or newer | Native builds. |
| Ninja | any recent (the SDK's `cmake/4.1.2/bin` ships `ninja.exe`) | `core/` presets. |
| clang / LLVM | recent | `core/` host build, clang-tidy. |
| clang-format | 22.x (CI pins 22.1.8 via `CLANG_FORMAT_VERSION` in `.github/workflows/ci.yml`) | Formatting of `core/` and Borealis C/C++ in `apps/android` (Spotless calls it from PATH). A different major version can format differently; `pip install clang-format==22.1.8` gives the same binary as CI. |

## Clone

```sh
git clone --recurse-submodules https://github.com/magalz/Borealis.git
cd Borealis
git config core.hooksPath .githooks
```

If you cloned without submodules:

```sh
git submodule update --init --recursive
```

The `moonlight-common-c` submodule lives at its upstream path,
`apps/android/app/src/main/jni/moonlight-core/moonlight-common-c`, and is
required for the native build.

## Android SDK setup

Install the SDK with the new Android CLI. The old `sdkmanager` is deprecated.

```sh
android --no-metrics sdk install platform-tools platforms/android-37.0 build-tools/37.0.0 ndk/29.0.14206865 cmake/4.1.2
```

Borealis' setup installs it under `%LOCALAPPDATA%\Android\Sdk` (the location
used with `--sdk`); any location works as long as `ANDROID_HOME` and
`apps/android/local.properties` point to it. Then:

1. Set the `ANDROID_HOME` environment variable to that directory.
2. Create `apps/android/local.properties` (it is git-ignored) with the SDK
   path. Use forward slashes or escaped backslashes:

   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

3. Make sure `JAVA_HOME` points at JDK 17 (`java -version` must report 17).

## Android client

Run from `apps/android/`. Only the `nonRoot` flavor is built; the upstream
`root` flavor is disabled.

```sh
cd apps/android
./gradlew assembleNonRootDebug
```

Checks (format, static analysis, lint and JVM unit tests):

```sh
./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test
```

Fix formatting of Borealis code:

```sh
./gradlew spotlessApply
```

Spotless does not touch upstream files. If it wants to change one, stop and
report it; never reformat upstream code.

Other test levels (as they land): Robolectric and Compose UI tests run with
the unit tests; emulator tests use Gradle Managed Devices; screenshot tests
use Roborazzi.

## borealis-core (C++)

Run from `core/`. Presets: `dev`, `asan`, `release`.

```sh
cd core
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

Sanitizers (ASan/UBSan):

```sh
cmake --preset asan
cmake --build --preset asan
ctest --preset asan
```

Formatting follows LuminalShine's LLVM-based `.clang-format`. Parsers get
libFuzzer targets in addition to unit tests.

If `ninja` is not on `PATH`, add the SDK's `cmake/4.1.2/bin` directory.

## Before you push

- `git config core.hooksPath .githooks` is set; the pre-commit hook runs.
- Format, lint, detekt, tests and build pass locally for what you touched.
- The failing test was committed before the implementation
  (see [tdd.md](tdd.md)).

## Linux (CI)

CI runs on Linux runners with the same versions:

- Install JDK 17 (for example `actions/setup-java`), the Android SDK packages
  above, and CMake, Ninja and clang from the runner or the SDK.
- Use `./gradlew` from `apps/android/` and the same CMake presets from
  `core/`.
- Check out submodules (`submodules: recursive`).
- Shell scripts are POSIX `sh`/`bash`; paths use forward slashes.
- Release signing happens only in the `release` environment (see
  [release.md](release.md)); pull request builds never receive secrets.

## Troubleshooting

- *SDK not found:* check `ANDROID_HOME` and `apps/android/local.properties`.
- *Native build fails with missing headers:* the submodule is not initialised.
- *Wrong Java version:* Gradle needs JDK 17; check `JAVA_HOME`.
