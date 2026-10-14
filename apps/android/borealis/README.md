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
  src/main/kotlin/                BorealisCore, NativeLibrary, CoreJni, HostRules, HostMarshalling
app/src/main/jni/borealis/        Android.mk + borealis_bridge.c (ndk-build side)
app/src/main/kotlin/.../NativeBridge.kt
```

- `BorealisCore.version()` returns the core version (`borealis_core_version()`), or `null` when the native library is unavailable.
- `HostRules` / `NativeHostRules` expose the three LuminalShine host rules of `core/include/borealis/host.h`: `parseCaps(appVersion, codecMask)` (family, version, HEVC, HEVC Main10, AV1, AV1 Main10, 4:4:4 and the PyroWave profiles), `decideLaunch(verb, status, message, attemptsInCategory)` (the launch/resume retry decision) and `launchFps(isGfe, launchFps, streamFps)`. Every call returns `null` when the native library is unavailable or the core rejects the input, so callers fall back to the upstream behaviour; `HostRules` is the interface T4b's consumers take so JVM tests can use a fake.
- The JNI carries only primitives: UTF-8 `ByteArray` inputs (`null` array = NULL pointer with length 0, an empty array = empty string) and `IntArray` results whose first element is the `BOREALIS_*` status (caps: `[status, family, v0..v3, codec_flags, pyrowave_profiles]`; decision: `[status, action, delay_ms, category, max_attempts]`). `HostMarshalling` holds the pure Kotlin side and is unit-tested on the JVM:
  - the host message is cut to 256 UTF-8 bytes without splitting a character (the core matches ASCII substrings only);
  - an `appversion` over 64 bytes is not truncated: the call returns `null` and logs a WARN on `CORE` with the byte count only;
  - `codecMask` keeps its raw 32-bit pattern (`0x80000000` is `Int.MIN_VALUE`); a negative `attemptsInCategory` is clamped to `Int.MAX_VALUE` because the core reads it as `uint32` (a huge count, hence `FAIL`);
  - a non-OK status, a malformed array or an unknown enum value decodes to `null` with a WARN on `CORE` (status or value only, never input content). `HostCaps.version` is always four ints, all 0 for `HostFamily.UNKNOWN`.
- `borealis_jni.cpp` copies the inputs into bounded stack buffers (`GetByteArrayRegion` after a length check), never throws and clears any pending Java exception before returning `null`.
- `GoldenVectorsInstrumentedTest` replays `core/tests/fixtures/vectors/*.tsv` (added as androidTest assets in `build.gradle.kts`) through `NativeHostRules`, so the C ABI and the Kotlin binding cannot drift apart. It needs a device or emulator (`connectedDebugAndroidTest`); `assembleDebugAndroidTest` only compiles it.
- `NativeLibrary(name)` loads a JNI library once and never throws: a missing library or symbol is logged once through `BorealisLog` (tag `CORE`, fields `library`, `operation`) and calls return `null`. Its `loader` parameter is the seam for JVM tests, which therefore never need a real `.so`.
- `BorealisApplication.onCreate` calls `BorealisCore.logStartup(NativeBridge.coreVersion())`: one INFO line `... CORE borealis-core loaded core=0.1.0`, plus a WARN when the ndk-build bridge reports another version or either library is unavailable.

### Two native build systems

AGP allows one `externalNativeBuild` per module, and upstream `:app` uses ndk-build. So:

1. `:borealis:native` builds `core/` with CMake (`add_subdirectory`, tests off) into `libborealis_core.so` and its own `libborealis_jni.so` (Kotlin `BorealisCore`).
2. `prefabPublishing` + `prefab { create("borealis_core") }` publish `libborealis_core.so` and `core/include` as a Prefab package. The package name is the Gradle project name, so it is `native`.
3. `:app` has `buildFeatures.prefab = true`. The Borealis-owned `jni/borealis/Android.mk` (picked up by upstream's `include $(call all-subdir-makefiles)`, so no upstream makefile changes) builds `libborealis_bridge.so` with `LOCAL_SHARED_LIBRARIES := borealis_core` and `$(call import-module,prefab/native)`. `NativeBridge.coreVersion()` calls it, proving that moonlight-core-side ndk-build code can link the core.

Upstream native code (evdev, `Application.mk`) is untouched, and moonlight-common-c differs from upstream only by the reviewed patches on the Borealis fork (`docs/development/upstream-sync.md`); the only upstream-facing native settings are `APP_STL=c++_shared` (passed from the convention plugin) and the Prefab flag.

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

Every LuminalShine compatibility rule on the Android side lives here (the host contract in `docs/host-contract` is the reference). Package root `io.github.magalz.borealis.host`; the module depends on `:borealis:logging` and `:borealis:native`, and `:app` depends on it. The packages below are added by the phase 1 tasks.

| Package | Content |
| --- | --- |
| `host.pairing` | PIN and unpair hints, best-effort `/unpair` |
| `host.launch` | App launch and resume requests (retries, cancellation) |
| `host.caps` | Host capabilities derived from `/serverinfo` |
| `host.video` | Stream startup: first-frame message and watchdog, no-video error hints |

### `host.pairing`

LuminalShine does not route `/unpair`, and the user types the PIN and the device name in the host Web UI (contract sections 4.1 and 10.2, requirement 4).

- `BestEffortUnpair.unpair(client, url)`: sends `/unpair` with `Connection: close`, because LuminalShine writes stray bytes after its response on that connection, reads at most 16 KiB of body and returns true only when the XML root has `status_code="200"`. An I/O error, a non-2xx status or an unreadable body is false instead of an exception (it blocks on the network, so call it off the main thread) and the outcome is logged on `LogTag.PAIR` without the URL. `NvHTTP.unpair()` delegates to it and returns the result; `PairingManager` ignores it, `PcView.doUnpair` shows the Web UI hint when it is false.
- `PairingHints`: `webUiUrl(address, httpPort)` is `https://<host>:<httpPort + 1>` (IPv6 bracketed once, zone id percent-encoded per RFC 6874, null for a port outside 1..65534 or an unusable address); `suggestedDeviceName(model)` removes `xbox` and `webos` in any case and falls back to `Borealis`; `pinHint` and `unpairHint` build the texts of `strings_pairing.xml` for the PIN dialog of `PcView.doPair` and the failed unpair.

### `host.launch`

LuminalShine answers `/launch` and `/resume` with 409 and 503 errors that ask the client to retry (another launch is pending, the previous stream is still restoring the display, the GPU or display stack is not ready; host contract 7.5), and rebuilds its virtual display when the `/launch` and RTSP modes differ (7.4). Upstream Moonlight never retries and, with frame pacing "cap FPS", sends two different rates.

- `LaunchRetryRunner` runs one request through an attempt function. A `HostError(code, message)` that the hook's extractor recognises goes to `HostRules.decideLaunch`. The runner counts retries per category (the core is stateless, so a first call learns the category and a second one carries the count), waits the decided delay in slices of at most 250 ms while polling the cancel supplier, and gives up when the next wait would pass the total budget (`DEFAULT_BUDGET_MS`, 60 s). It reports `SUCCEEDED`, `FAILED` (the host answered 200 but did not start the app), `FALLBACK_TO_LAUNCH` (a `/resume` answered 503 "No running app to resume") or `CANCELLED`. A cancellation, including an interrupted wait, returns without throwing.
- Everything else is rethrown as the original instance, so upstream's 470/525/599 handling and `stageFailed` mapping keep working: a throwable the extractor does not recognise, a FAIL decision, an exhausted category or budget. When `HostRules` returns null (native library not loaded) the original exception is rethrown too, with one ERROR on `LogTag.LAUNCH`. Retries log the category, the attempt and the delay, never the host message. The runner has no HTTP access and never calls `/cancel`, which would also cancel another client's pending launch.
- `LaunchRetries.run(...)` is the Java-facing entry point. GFE hosts (`isNvidiaServerSoftware`) bypass the runner, as upstream. It maps the verb string, the cancel `BooleanSupplier` and a `RetryProgress` callback (category ordinal, attempt, retries allowed, delay) to the runner.
- `LaunchProgressText.format` builds the spinner message in English or pt-BR from `strings_launch.xml`, for example "Another device is starting a stream on this host. Retrying in 2 s (attempt 2 of 4)." There is one text per retried category plus a generic one.
- `LaunchFrameRate.forLaunch(isGfe, launchFps, streamFps)` is the `mode=` frame rate of `/launch`: the stream rate announced over RTSP for non-GFE hosts, and the upstream rule (`isGfe && launchFps > 60 ? 0 : launchFps`) for GFE and whenever `HostRules.launchFps` returns null.

Upstream hooks (each marked `// BOREALIS:`):

| File | Hook |
| --- | --- |
| `NvConnection` | `launchWithRetry` feeds `launchApp` to `LaunchRetries` for the `/resume` and `/launch` calls; the resume branch falls back to `launchNotRunningApp` on `FALLBACK_TO_LAUNCH` and both return on `CANCELLED`; `volatile launchCancelled` with `cancelLaunch()`; `start()` returns without `stageFailed` when the flag is set, before `connectionAllowed.acquire()` and again right after it |
| `NvConnectionListener` | `default launchRetrying(category, attempt, maxAttempts, delayMs)` |
| `NvHTTP.launchApp` | the `mode=` frame rate comes from `LaunchFrameRate` |
| `Game` | `launchRetrying` sets the spinner message on the UI thread; `onStop` and `onDestroy` call `conn.cancelLaunch()` |

The cancel flag never goes through `NvConnection.stop()`: `stop()` releases the static `connectionAllowed` semaphore unconditionally, and `Game` only calls it for a connection that was started. For the same reason a launch cancelled after `acquire()` releases the permit it just took before returning.

### `host.caps`

- `HostInfoReader.read(hostId, appVersion, codecMask, httpsPort, httpPort)` builds a `BorealisHostInfo` (the `HostCaps` of `HostRules.parseCaps` plus both ports) from the raw `appversion` and `ServerCodecModeSupport` texts of `/serverinfo`. A missing or unreadable mask counts as 0 (the low 32 bits of a larger number are kept, so `0x80000000` arrives as `2147483648`), so a malformed host cannot break the poll. It returns null when the rules are unavailable or reject the version text.
- `/serverinfo` is polled all the time, so the reader logs one INFO on `LogTag.HOST` per host, and only when the information of that host changes. `NativeHostRules.parseCaps` itself still logs a WARN on every call for an `appversion` over 64 bytes.
- `NvHTTP.getComputerDetails` stores the result in the transient `ComputerDetails.borealis`, which `update()` copies. `HostInfoText.appendTo` appends a localized block to the PC details dialog (`PcView`): host software and version, HEVC, AV1, 10-bit and 4:4:4, the PyroWave profiles or "off", and the HTTP and HTTPS ports.

### `host.video`

LuminalShine can need about 18 s after the client attaches to send the first frame (contract section 10.1); upstream Moonlight dismisses the spinner at `connectionStarted`, long before that.

- `StreamStartup.create(...)` builds the `StreamStartupGuard` that `Game` holds. `Game` passes only plumbing (UI-thread runner, overlay text and visibility setters, the picture-in-picture flag and the termination callback), so its hooks hold no logic.
- `PreparingMessage` shows "host is preparing the display" on the non-modal notification overlay from `connectionStarted` until the first rendered frame. A connection warning takes precedence, and once the first frame arrives the overlay follows only the last connection status.
- `FirstFrameWatchdog` (states IDLE, ARMED, SATISFIED, FIRED, CANCELLED; one terminal state per stream) ends the stream with `ML_ERROR_NO_VIDEO_FRAME` if no frame is rendered 45 s after `connectionStarted`, above the about 35 s worst case of the patched moonlight-common-c deadlines. The termination runs off the main thread because `Game.connectionTerminated` does network I/O first. The watchdog is cancelled on stop, `onStop`, `onDestroy` and termination.
- `FirstFrameSignal` is raised by `MediaCodecDecoderRenderer` at the first output buffer released for rendering (Choreographer and renderer-thread paths); after that it costs one volatile read per frame.
- `StreamErrorHints.withHint` appends a LuminalShine hint to the -100 (no video traffic) and -101 (no complete frame) messages; the blocked-port message is left unchanged.

User-facing strings of the module stay in the module, one file per area, with Brazilian Portuguese next to the default English: `res/values/strings_pairing.xml`, `strings_stream.xml`, `strings_launch.xml` and the same names under `res/values-pt-rBR/`.

### Fixtures in unit tests

`borealis.android.library` gives every unit-test task of a `:borealis:*` module the system property `borealis.fixtures.dir`, the absolute path of `core/tests/fixtures` (layout, provenance and scrubbing rules in its `README.md`). The directory is a test input, so changing a fixture re-runs the tests. In `:borealis:host` the test helper `Fixtures` resolves a fixture by relative path (`Fixtures.text("luminalshine/26.09.1/serverinfo-http.xml")`) and fails with a clear message when the property or the file is missing. `FixturePolicyTest` enforces the public-repository rules on every file under `luminalshine/`: scrubbed host name, `uniqueid`, MAC and addresses, and a `device` or `source` provenance row for each file.
