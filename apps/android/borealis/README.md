# Borealis Android modules

Borealis-owned Kotlin code lives here; `apps/android/app` stays the (minimally hooked) upstream Moonlight module.
New code is Kotlin under the package root `io.github.magalz.borealis`.

## Build setup

- `../build-logic` is an included build (registered with `pluginManagement { includeBuild }` in `settings.gradle`) with the convention plugins:
  - `borealis.android.application`: applied by `app/build.gradle`. Through the AGP variant API it sets the application id (`io.github.magalz.borealis`, `.debug` suffix for debug), the labels (`Borealis` / `Borealis Debug`), minSdk 28, disables the upstream `root` flavor, derives the version from git, configures release signing, the Android Lint baseline and Hilt/KSP, and applies Kover, licensee and the CycloneDX SBOM. For the native chain it also enables Prefab, passes `APP_STL=c++_shared` to ndk-build, depends on `:borealis:native` and orders that module's CMake build before the ndk-build configure step (ndk-build checks that the Prefab library file exists).
  - `borealis.android.library`: use it for every `:borealis:*` module (compileSdk 37, minSdk 28, NDK 29.0.14206865 like upstream, Java 17, Lint warnings are errors, JUnit 4 + Truth, AndroidX test runner for `androidTest`). It also applies Kover and licensee. Instrumented tests receive the borealis-core version from `core/CMakeLists.txt` as the instrumentation argument `expectedCoreVersion` (also set on `:app`).
  - `borealis.settings`: applies Spotless and detekt to Borealis-owned paths only: ktfmt (kotlinlang style) for Kotlin and clang-format with `core/.clang-format` for `borealis/native/src/main/cpp` and `app/src/main/jni/borealis`. `clang-format` must be on `PATH`; Spotless needs its exact version, so the build reads it from `clang-format --version`. Without it the C/C++ Spotless tasks fail with "clang-format 22.x not found on PATH". Upstream Java/Groovy/C is never formatted. It also applies Kover to the root project and merges the modules' coverage there.
- Versions live in `../gradle/libs.versions.toml`.
- Upstream hooks are marked `BOREALIS` in `settings.gradle`, `app/build.gradle` and `app/src/main/AndroidManifest.xml`.

### Version

`versionName`/`versionCode` come from the newest plain `android-vMAJOR.MINOR.PATCH` tag (pre-release tags such as `-rc1` are skipped; components above 999 fail the build; `versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`). Without a tag: `0.0.0-dev`, code 1 for debug builds; release builds fail with a message (fetch tags, CI: `fetch-depth: 0`). Commits after a tag show as `1.2.3-5-gabcdef1`.

### Release signing

Release stays unsigned unless all four values are present, either in `apps/android/keystore.properties` (git-ignored) or in the environment:

| keystore.properties | environment |
| --- | --- |
| `storeFile` | `BOREALIS_KEYSTORE_FILE` |
| `storePassword` | `BOREALIS_KEYSTORE_PASSWORD` |
| `keyAlias` | `BOREALIS_KEY_ALIAS` |
| `keyPassword` | `BOREALIS_KEY_PASSWORD` |

### Checks (from `apps/android`)

```sh
./gradlew spotlessCheck detekt lint testDebugUnitTest assembleNonRootDebug licensee
./gradlew :build-logic:test        # build-logic unit tests
./gradlew spotlessApply            # format Borealis-owned Kotlin
./gradlew :app:updateLintBaseline  # only when deliberately accepting upstream lint findings
```

Lint on `:app` uses `app/lint-baseline.xml` for the existing upstream findings; anything new fails the build, and Borealis modules have no baseline.
An opt-in pre-commit hook lives in `.githooks` at the repository root (`git config core.hooksPath .githooks`).

### Coverage, licences and SBOM

All three come from the convention plugins (`BorealisCoverage`, `BorealisLicenses`, `BorealisSbom` in `build-logic`); details in `docs/development/building.md`.

```sh
./gradlew koverXmlReportBorealis koverHtmlReportBorealis  # build/reports/kover/coverage.xml (JaCoCo format) and htmlBorealis/
./gradlew licensee                                        # part of check; fails on a licence outside the allowlist
./gradlew :app:cyclonedxDirectBom                         # app/build/reports/sbom/borealis-android.cdx.json
```

- Kover merges the debug unit-test coverage (`debug` in `:borealis:*`, `nonRootDebug` in `:app`) into one report at the root. Upstream `com.limelight.*` and generated Hilt/Dagger/`BuildConfig`/`R`/Compose singleton classes are excluded.
- licensee allows Apache-2.0, MIT, BSD-2/3-Clause, ISC and Zlib plus URL-only licences of Bouncy Castle and JCodec, each with a justification in `BorealisLicenses.kt`.
- `../sbom/native-components.cdx.json` is the hand-maintained SBOM of the native components in the APK; update it with every upstream sync (`docs/development/upstream-sync.md`).

## `:borealis:logging`

`BorealisLog` is the only logging entry point for Borealis code.

- Levels `VERBOSE..ERROR`, subsystem tags `LogTag` (APP, HOST, PAIR, LAUNCH, RTSP, VIDEO, PYROWAVE, INPUT, REST, CORE, UPSTREAM), structured fields: `BorealisLog.i(LogTag.HOST, "paired", "host" to name)` renders as `... INFO HOST paired host=name`.
- Before `BorealisLog.initialize(...)` (done in `BorealisApplication.onCreate`) records are dropped; sinks never throw into the caller.
- `Redactor` runs on every message, field and stack trace before any sink: labelled PINs, `Bearer` and `token=`/`apiToken=` values, PEM blocks and long hex/base64 values in `cert`/`key`/`signature` fields. IP masking is opt-in (`Redactor(maskIpAddresses = true)`).
- Sinks: `LogcatSink` (tag `Borealis.<TAG>`) and `RotatingFileSink` (`noBackupFilesDir/logs/borealis.log`, 5 files x 1 MB, one line per record, ISO-8601 UTC).
- `JulBridge.install()` forwards the upstream `LimeLog` (`java.util.logging`) to `BorealisLog` with tag `UPSTREAM`.
- `CrashHandler.install(dir, header)` writes a redacted `crash-<timestamp>.txt` next to the logs and delegates to the previous handler.

## `:borealis:native`

The Android side of the C++ core (ADR 0010, 0043). Package `io.github.magalz.borealis.nativecore`.

```text
borealis/native/
  build.gradle.kts                CMake + Prefab publishing
  src/main/cpp/CMakeLists.txt     add_subdirectory(<repo>/core), builds libborealis_jni.so
  src/main/cpp/borealis_jni.cpp   hand-written JNI for CoreJni
  src/main/kotlin/                BorealisCore, NativeLibrary, CoreJni
app/src/main/jni/borealis/        Android.mk + borealis_bridge.c (ndk-build side)
app/src/main/kotlin/.../NativeBridge.kt
```

- `BorealisCore.version()` returns the core version (`borealis_core_version()`), or `null` when the native library is unavailable.
- `NativeLibrary(name)` loads a JNI library once and never throws: a missing library or symbol is logged once through `BorealisLog` (tag `CORE`, fields `library`, `operation`) and calls return `null`. Its `loader` parameter is the seam for JVM tests, which therefore never need a real `.so`.
- `BorealisApplication.onCreate` calls `BorealisCore.logStartup(NativeBridge.coreVersion())`: one INFO line `... CORE borealis-core loaded core=0.1.0`, plus a WARN when the ndk-build bridge reports another version or either library is unavailable.

### Two native build systems

AGP allows one `externalNativeBuild` per module, and upstream `:app` uses ndk-build. So:

1. `:borealis:native` builds `core/` with CMake (`add_subdirectory`, tests off) into `libborealis_core.so` and its own `libborealis_jni.so` (Kotlin `BorealisCore`).
2. `prefabPublishing` + `prefab { create("borealis_core") }` publish `libborealis_core.so` and `core/include` as a Prefab package. The package name is the Gradle project name, so it is `native`.
3. `:app` has `buildFeatures.prefab = true`. The Borealis-owned `jni/borealis/Android.mk` (picked up by upstream's `include $(call all-subdir-makefiles)`, so no upstream makefile changes) builds `libborealis_bridge.so` with `LOCAL_SHARED_LIBRARIES := borealis_core` and `$(call import-module,prefab/native)`. `NativeBridge.coreVersion()` calls it, proving that moonlight-core-side ndk-build code can link the core.

Upstream native code (moonlight-common-c, evdev, `Application.mk`) is untouched; the only upstream-facing native settings are `APP_STL=c++_shared` (passed from the convention plugin) and the Prefab flag.

### Static or shared, and the STL

The core is published as a shared library (`BUILD_SHARED_LIBS=ON` inside the module's CMake) built with `ANDROID_STL=c++_shared`, and the app's ndk-build uses `APP_STL=c++_shared`.

- Prefab rejects the alternatives for a shared core: a shared library with a statically linked libc++ "cannot be used with any library using the STL", and consumers that declare no STL are refused for libraries that need libc++ (AGP reports `CXX1211`/`CXX1213`). ndk-build without `APP_STL` counts as "no STL".
- One STL per app: the JNI library and the core (and later the bridge, once it uses C++) share one `libc++_shared.so`, which is packaged next to them. A static core (not tried) would instead be linked, with its STL, into every consumer and duplicate both.
- Only the C ABI of `core.h` is exported (hidden visibility, no STL types across it), so the STL choice never leaks to callers. Upstream code is plain C: `libmoonlight-core.so` does not depend on libc++, and the C bridge does not either.
- Both libraries are 16 KB page aligned on arm64-v8a and x86_64 (NDK 29 default); the 32-bit ABIs use 4 KB like upstream.

### Verifying without a device

```sh
./gradlew assembleNonRootDebug :borealis:native:assembleDebugAndroidTest :app:assembleNonRootDebugAndroidTest
```

The APK has `lib/<abi>/libborealis_core.so`, `libborealis_jni.so`, `libborealis_bridge.so` and `libc++_shared.so` for armeabi-v7a, arm64-v8a, x86 and x86_64. `llvm-nm -D` shows `borealis_core_version` defined in the core library and undefined (imported) in the JNI and bridge libraries. On a device or emulator run `./gradlew :borealis:native:connectedDebugAndroidTest :app:connectedNonRootDebugAndroidTest`; both assert that the packaged libraries report the version declared in `core/CMakeLists.txt`.

## `:borealis:host`

Every LuminalShine compatibility rule on the Android side lives here (the host contract in `docs/host-contract` is the reference). Package root `io.github.magalz.borealis.host`; the module depends on `:borealis:logging` and `:borealis:native`, and `:app` depends on it. It has no production code yet: the packages below are added by the phase 1 tasks.

| Package | Content |
| --- | --- |
| `host.pairing` | Pairing and the paired-host state |
| `host.launch` | App launch and resume requests |
| `host.caps` | Host capabilities derived from `/serverinfo` |
| `host.video` | Codec and video-stream selection |

User-facing strings of the module stay in the module, one file per area, with Brazilian Portuguese next to the default English: `res/values/strings_pairing.xml`, `strings_stream.xml`, `strings_launch.xml` and the same names under `res/values-pt-rBR/`.

### Fixtures in unit tests

`borealis.android.library` gives every unit-test task of a `:borealis:*` module the system property `borealis.fixtures.dir`, the absolute path of `core/tests/fixtures` (layout, provenance and scrubbing rules in its `README.md`). The directory is a test input, so changing a fixture re-runs the tests. In `:borealis:host` the test helper `Fixtures` resolves a fixture by relative path (`Fixtures.text("luminalshine/26.09.1/serverinfo-http.xml")`) and fails with a clear message when the property or the file is missing. `FixturePolicyTest` enforces the public-repository rules on every file under `luminalshine/`: scrubbed host name, `uniqueid`, MAC and addresses, and a `device` or `source` provenance row for each file.
