# Borealis Android modules

Borealis-owned Kotlin code lives here. `apps/android/app` is inherited code from moonlight-android 12.2 (see [ADR 0054](../../../docs/adr/0054-declare-independence-from-moonlight.md)). It is Borealis code now and is changed only when a task needs it.
New code is Kotlin under the package root `io.github.magalz.borealis`.

## Build setup

- `../build-logic` is an included build (registered with `pluginManagement { includeBuild }` in `settings.gradle`) with the convention plugins:
  - `borealis.android.application`: applied by `app/build.gradle`. Through the AGP variant API it sets the application id (`io.github.magalz.borealis`, `.debug` suffix for debug), the labels (`Borealis` / `Borealis Debug`), minSdk 28, disables the upstream `root` flavor, derives the version from git, configures release signing, the Android Lint baseline and Hilt/KSP, and applies Kover, licensee and the CycloneDX SBOM. For the native chain it also enables Prefab, passes `APP_STL=c++_shared` to ndk-build, depends on `:borealis:native` and orders that module's CMake build before the ndk-build configure step (ndk-build checks that the Prefab library file exists).
  - `borealis.android.library`: use it for every `:borealis:*` module (compileSdk 37, minSdk 28, NDK 29.0.14206865 like upstream, Java 17, Lint warnings are errors, JUnit 4 + Truth, AndroidX test runner for `androidTest`). It also applies Kover and licensee. Instrumented tests receive the borealis-core version from `core/CMakeLists.txt` as the instrumentation argument `expectedCoreVersion` (also set on `:app`).
  - `borealis.settings`: applies Spotless and detekt to Borealis-owned paths only: ktfmt (kotlinlang style) for Kotlin and clang-format with `core/.clang-format` for `borealis/native/src/main/cpp` and `app/src/main/jni/borealis`. `clang-format` must be on `PATH`; Spotless needs its exact version, so the build reads it from `clang-format --version`. Without it the C/C++ Spotless tasks fail with "clang-format 22.x not found on PATH". Upstream Java/Groovy/C is never formatted. It also applies Kover to the root project and merges the modules' coverage there.
- Versions live in `../gradle/libs.versions.toml`.
- The `BOREALIS` markers in `settings.gradle`, `app/build.gradle` and `app/src/main/AndroidManifest.xml` stay as history. New edits to inherited code do not need them (ADR 0054).

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
- `Redactor` runs on every message, field and stack trace before any sink: labelled PINs, `Bearer` and `token=`/`apiToken=` values, PEM blocks and long hex/base64 values in `cert`/`key`/`signature` fields, and the pairing and launch secrets of the GameStream requests.
  - URL query values become `[REDACTED]` except for the allowlist in `Redactor.ALLOWED_QUERY_KEYS` (the same list is in `docs/policies/privacy-and-logging.md`); query keys and paths stay, `key=value` pairs in the fragment (`#...`) are treated like query pairs, and `user:password@` in a URL is redacted. The scheme is limited to 32 characters, so a long dotted or hyphenated run cannot make URL detection slow.
  - Address masking is off by default (`Redactor(maskAddresses = true)`). When on it masks IPv4 addresses whose four octets are at most 255 and that are not part of a longer dotted number (`7.1.431.0` stays), IPv6 addresses (`[IP]`) and host names (`[HOST]`): the host part of a URL and the values of keys named `host`, `hostname`, `computer` or `computerName` (also as a suffix, such as `serverHost`). A quoted value (`"Den PC"` or `'Den PC'`) is masked up to the closing quote. A host name in free text, including the upstream `LimeLog` lines, cannot be recognised and is not masked.
  - `Redactor.maskAddresses` is volatile. `BorealisLog.setMaskAddresses(Boolean)` and `BorealisLog.setMinLevel(LogLevel)` change the running configuration without a new `initialize`; they affect new lines only and do nothing before `initialize`.
  - The secret samples live in `core/tests/fixtures/redaction/secret-canaries.tsv` (columns `id`, `input`, `expected`, `secret`; synthetic values only). `RedactorSecretsTest` reads it through `borealis.fixtures.dir`, and `:borealis:diagnostics` tests are meant to reuse it.
- `CrashHandler.install(dir, header, redactor)` takes the redactor of `BorealisLog` (created once in `BorealisApplication`), so crash reports follow "Mask addresses" too.
- The minimum level applies to every sink. `BorealisApplication` initialises it to DEBUG in debug builds and INFO in release builds (so the file sink records INFO and above there); the logcat sink keeps its own minimum.
- Sinks: `LogcatSink` (tag `Borealis.<TAG>`) and `RotatingFileSink` (`noBackupFilesDir/logs/borealis.log`, 5 files x 1 MB, one line per record, ISO-8601 UTC).
  - `RotatingFileSink` is asynchronous. `write` queues the record in a bounded queue of 1,024 records, and a writer thread appends them. When the queue is full the record is dropped and counted, and a `N records dropped` line is queued once there is room. `flush` waits for the queue to drain (2 s by default), and `flushAll` does so for every open sink. The crash handler and the diagnostic bundle call it first.
  - `LogStorage` holds the directory name (`logs`, under `noBackupFilesDir`) and the keys of the two switches.
  - `NativeLogForwarder` passes the moonlight-common-c lines to `BorealisLog` under `LogTag.COMMONC`, so they are redacted. A token bucket limits them to a burst of 100 lines, refilled at 10 lines per second. The first line accepted after a suppression is preceded by `N lines suppressed`.
- `JulBridge.install()` forwards the upstream `LimeLog` (`java.util.logging`) to `BorealisLog` with tag `UPSTREAM`.
- `CrashHandler.install(dir, header)` writes a redacted `crash-<timestamp>.txt` next to the logs and delegates to the previous handler.

## `:borealis:settings`

The Borealis settings screen (ADR 0050 items 3 and 7). Package `io.github.magalz.borealis.settings`; it is a framework `PreferenceFragment` like the upstream screen, so the Activity cannot be reached with `android:fragment` and a custom Preference opens it.

- `BorealisSettingsPreference` is the only entry in the upstream `app/src/main/res/xml/preferences.xml` (marked `<!-- BOREALIS: -->`). It starts `BorealisSettingsActivity` with an explicit intent. The Activity is declared in this module's manifest with `android:exported="false"` and no intent filter.
- `res/xml/borealis_preferences.xml` holds the entries, one element each, so a new entry is one element in the first category:
  - category "Privacy and diagnostics": `borealis_verbose_logging` ("Verbose logging") and `borealis_mask_addresses` ("Mask addresses in logs and diagnostics"), both default off, in the default SharedPreferences (`SettingsKeys` holds the key constants and must match the XML); "Export diagnostics" (`ExportDiagnosticsPreference`) and "Erase audit trail" (`EraseAuditPreference`) are custom Preference classes of `:borealis:diagnostics`;
  - category "About": version name with the commit (`borealis_git_sha`), "Source code" and "Privacy policy" as `LinkPreference`, "Licence: GPL-3.0" as plain text and "Open-source licences" as `LicensesPreference`.
- `LinkPreference` opens its `url` attribute with `ACTION_VIEW`. When no activity can open it (Google TV often has no browser) it shows the URL in a dialog instead. The URL is also the summary.
- `LicensesPreference` shows `assets/legal/NOTICE` and `LICENSE` (copied into the APK by `LegalAssetsTask`, ADR 0050 item 7) in a scrollable dialog; the text view takes focus, so a D-pad scrolls it. When a build bundles neither file the dialog says so. The unit tests cover the loader (`LegalTexts`) with fake files and the dialog's missing-file path, because this library module has no `legal/` assets of its own.
- `ContentPadding` (installed by the Activity) pads the content view with the system-bar and display-cutout insets, because the window is edge-to-edge from Android 15, and with 15 dp overscan padding on a TV (`UI_MODE_TYPE_TELEVISION`), the values of upstream `UiHelper.notifyNewRootView`.
- `LogSettings.start(context, baseLevel)` applies the two switches to `BorealisLog` at startup and on every change (verbose lowers the minimum level to DEBUG, otherwise it is `baseLevel`). `BorealisApplication` calls it once after `BorealisLog.initialize` and keeps the result in a field, because `SharedPreferences` holds its listeners weakly.
- The module declares `borealis_git_sha` as `translatable="false"` with the value `unknown`; the app overrides it with the built commit. Strings are in `res/values/strings_settings.xml`, English only for now.
- Upstream hooks outside the module, each marked `BOREALIS`: the one preference element in `preferences.xml`, and in `app/src/main/jni/moonlight-core/simplejni.c` an early return in `MoonBridge.findExternalAddressIP4` (returns `NULL`, no STUN request, no WAN address in logcat) and in `MoonBridge.testClientConnectivity` (returns `ML_TEST_RESULT_INCONCLUSIVE`, no connection test). The Java callers already handle both results.

## `:borealis:diagnostics`

The local audit trail and the diagnostic bundle (ADR 0050). Package `io.github.magalz.borealis.diagnostics`; it depends on `:borealis:logging`. `:borealis:settings` shows its two entries ("Export diagnostics" and "Erase audit trail"). The policy is in [privacy-and-logging.md](../../../docs/policies/privacy-and-logging.md).

### Audit trail

- `BorealisAudit` in `:borealis:logging` is the facade that the host code calls. Until a sink is installed, events are dropped. It redacts each event before the sink receives it.
- `AuditStore` queues each event, and one writer coroutine stores them in a Room database at `noBackupFilesDir/audit.db` (table `audit_events`). The queue holds 256 events and drops the oldest one when it is full.
- Retention keeps the newest 2,000 entries and nothing older than 90 days. Deduplication applies only to `CERTIFICATE_CHANGED` and `TLS_FAILURE`, within 24 hours (`AuditDeduplicator`).
- `AuditSection` writes the trail to `audit.jsonl` in the bundle: one JSON object per line, oldest first, UTC timestamps. It redacts each entry again with the current "Mask addresses" setting.
- `EraseAuditPreference` is the "Erase audit trail" row. It asks for confirmation, with Cancel focused first, and deletes the stored entries. Bundles that were exported before are not changed.

### Diagnostic bundle

- `ExportDiagnosticsPreference` runs `ExportFlow`, which builds the zip in `cacheDir/diagnostics`. The file name is `borealis-diagnostics-<timestamp>.zip`. A second tap while the bundle is being built does nothing, and only the newest bundle is kept.
- Layout: `manifest.json`, `decoders.json`, one file per section (`audit.jsonl`) and the log files under `logs/`.
- The uncompressed content stays within 16 MiB. Sections are written first. The log files get the rest, newest first. The first file that does not fit keeps its newest lines, and the older files are dropped. `manifest.json` lists what was cut.
- Each section redacts its own content, so a JSON line is not redacted twice. The log lines are re-redacted line by line with the current setting, so "Mask addresses" also applies to lines written before it was turned on.
- `flushLogs` runs before the log files are read, so queued lines reach the files first.
- `DecoderReport` builds `decoders.json`: the H.264, HEVC and AV1 decoders with their profile names, and the HDR types of the default display.
- The zip is shared through a `FileProvider` with the authority `${applicationId}.diagnostics.fileprovider` (paths in `borealis_diagnostics_paths.xml`). On a phone or tablet the share sheet opens when an app can receive the zip. On a TV (`UI_MODE_TYPE_TELEVISION` or the leanback feature) it never opens by itself, because apps such as VLC accept any `ACTION_SEND` and would take the file: the bundle is saved and the "Diagnostics saved" dialog shows where. A phone with no receiver does the same. The save goes through a `BundleStore`: on API 29 and later `DownloadsBundleStore` inserts the zip into `MediaStore.Downloads` (MIME `application/zip`, `IS_PENDING` while it is written, no storage permission), so the file is at `/sdcard/Download/<name>` and the dialog shows `Download/<name>` and `adb pull /sdcard/Download/<name>`. On API 28 `AppFilesBundleStore` copies it to `getExternalFilesDir("diagnostics")` and the dialog shows that path and its `adb pull` command. Only the newest bundle this app saved is kept (MediaStore shows an app only its own entries). The dialog text is focusable and scrolls, and Close has the focus. On a phone or tablet an optional "Share…" button opens the chooser with the `FileProvider` URI; on a TV the dialog has no such button, because the only receiver there is usually a video player.

### Hilt

- `BundleModule` declares `@Multibinds Set<DiagnosticSection>`, so the bundle works with no contributor. A module adds a section with `@Provides @IntoSet` or `@Binds @IntoSet` (`AuditModule` uses `@Provides @IntoSet` for `AuditSection`).
- `DiagnosticsEntryPoint` gives the set to classes that Hilt does not create, such as the preferences.

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
- `HostRules` / `NativeHostRules` expose the four LuminalShine host rules of `core/include/borealis/host.h`: `parseCaps(appVersion, codecMask)` (family, version, HEVC, HEVC Main10, AV1, AV1 Main10, 4:4:4 and the PyroWave profiles), `decideLaunch(verb, status, message, attemptsInCategory)` (the launch/resume retry decision, including `RESOLVE_CONFLICT` for a 400 "An app is already running" on `/launch`), `classifyPairing(status, message)` (`PairAction` and `PairCategory` of a `/pair` error) and `launchFps(isGfe, launchFps, streamFps)`. Every call returns `null` when the native library is unavailable or the core rejects the input, so callers fall back to the upstream behaviour; `HostRules` is the interface T4b's consumers take so JVM tests can use a fake.
- The JNI carries only primitives: UTF-8 `ByteArray` inputs (`null` array = NULL pointer with length 0, an empty array = empty string) and `IntArray` results whose first element is the `BOREALIS_*` status (caps: `[status, family, v0..v3, codec_flags, pyrowave_profiles]`; decision: `[status, action, delay_ms, category, max_attempts]`; pairing decision: `[status, action, category]`). `HostMarshalling` holds the pure Kotlin side and is unit-tested on the JVM:
  - the host message is cut to 256 UTF-8 bytes without splitting a character (the core matches ASCII substrings only);
  - an `appversion` over 64 bytes is not truncated: the call returns `null` and logs a WARN on `CORE` with the byte count only;
  - `codecMask` keeps its raw 32-bit pattern (`0x80000000` is `Int.MIN_VALUE`); a negative `attemptsInCategory` is clamped to `Int.MAX_VALUE` because the core reads it as `uint32` (a huge count, hence `FAIL`);
  - a non-OK status, a malformed array or an unknown enum value decodes to `null` with a WARN on `CORE` (status or value only, never input content). `HostCaps.version` is always four ints, all 0 for `HostFamily.UNKNOWN`.
- `borealis_jni.cpp` copies the inputs into bounded stack buffers (`GetByteArrayRegion` after a length check), never throws and clears any pending Java exception before returning `null`.
- `GoldenVectorsInstrumentedTest` replays `core/tests/fixtures/vectors/*.tsv` (host caps, launch decisions, pairing classification and frame rate) (added as androidTest assets in `build.gradle.kts`) through `NativeHostRules`, so the C ABI and the Kotlin binding cannot drift apart. It needs a device or emulator (`connectedDebugAndroidTest`); `assembleDebugAndroidTest` only compiles it.
- `NativeLibrary(name)` loads a JNI library once and never throws: a missing library or symbol is logged once through `BorealisLog` (tag `CORE`, fields `library`, `operation`) and calls return `null`. Its `loader` parameter is the seam for JVM tests, which therefore never need a real `.so`.
- `BorealisApplication.onCreate` calls `BorealisCore.logStartup(NativeBridge.coreVersion())`: one INFO line `... CORE borealis-core loaded core=0.1.0`, plus a WARN when the ndk-build bridge reports another version or either library is unavailable.

### Two native build systems

AGP allows one `externalNativeBuild` per module, and upstream `:app` uses ndk-build. So:

1. `:borealis:native` builds `core/` with CMake (`add_subdirectory`, tests off) into `libborealis_core.so` and its own `libborealis_jni.so` (Kotlin `BorealisCore`).
2. `prefabPublishing` + `prefab { create("borealis_core") }` publish `libborealis_core.so` and `core/include` as a Prefab package. The package name is the Gradle project name, so it is `native`.
3. `:app` has `buildFeatures.prefab = true`. The Borealis-owned `jni/borealis/Android.mk` (picked up by upstream's `include $(call all-subdir-makefiles)`, so no upstream makefile changes) builds `libborealis_bridge.so` with `LOCAL_SHARED_LIBRARIES := borealis_core` and `$(call import-module,prefab/native)`. `NativeBridge.coreVersion()` calls it, proving that moonlight-core-side ndk-build code can link the core.

Upstream native code (evdev, `Application.mk`) is untouched, and moonlight-common-c is vendored in `core/third_party/`, and its changes from upstream are listed in `core/third_party/README.md`; the only upstream-facing native settings are `APP_STL=c++_shared` (passed from the convention plugin) and the Prefab flag.

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
| `host.pairing` | PIN and unpair hints, best-effort `/unpair`, messages and the single restart for host pairing errors |
| `host.launch` | App launch and resume requests (retries, cancellation, the running-app conflict) |
| `host.caps` | Host capabilities derived from `/serverinfo` |
| `host.video` | Stream startup: first-frame message and watchdog, no-video error hints |

### `host.pairing`

LuminalShine does not route `/unpair`, and the user types the PIN and the device name in the host Web UI (contract sections 4.1 and 10.2, requirement 4).

- `BestEffortUnpair.unpair(client, url)`: sends `/unpair` with `Connection: close`, because LuminalShine writes stray bytes after its response on that connection, reads at most 16 KiB of body and returns true only when the XML root has `status_code="200"`. An I/O error, a non-2xx status or an unreadable body is false instead of an exception (it blocks on the network, so call it off the main thread) and the outcome is logged on `LogTag.PAIR` without the URL. `NvHTTP.unpair()` delegates to it and returns the result; `PairingManager` ignores it. The Library's removal of a host (`ComputerHostRepository`) calls it best effort, and the host is removed on this device either way.
- `PairingHints`: `webUiUrl(address, httpPort)` is `https://<host>:<httpPort + 1>` (IPv6 bracketed once, zone id percent-encoded per RFC 6874, null for a port outside 1..65534 or an unusable address); `suggestedDeviceName(model)` removes `xbox` and `webos` in any case and falls back to `Borealis`. The Library pairing (`NvPairingService`) uses both.

- `PairingErrors.describe(context, error, fallback, restarted)` turns the host answer behind a failed pairing step into a `PairingFailure(message, restart)`. `HostRules.classifyPairing` decides: for a session the host dropped (400 `Out of order call to getservercert`, 400 `Invalid uniqueid`, 400 `Pairing exchange invalidated`, 410 `Host pairing state was reset`) the message is "The host had an unfinished pairing attempt and has cleared it. Enter the new PIN." and `restart` is true, once: when `restarted` is already true the same error is a plain failure ("The host cleared the pairing attempt again..."). The errors of a session the host still holds (409 in progress, 409 identity mismatch which means another device is pairing, 503 too many pending) and the specific failures (403 peer mismatch, 409 superseded) get their own text and never restart. Anything else, a non-host exception and an unavailable core keep the upstream text. The log line carries the status, category and action only, never the host text or a PIN.
- The Library pairing (`NvPairingService` in `:app`) calls it in its failure path, with the `restarted` flag. When `restart` is true the pairing runs once more, so one new PIN prompt appears. The 12.2 `PcView.doPair` that did this before is removed with `PcView` (Sprint 5).
- OkHttp: the pairing clients keep OkHttp's default `retryOnConnectionFailure`. The held phase 1 request has no read timeout, so a timeout cannot resend it; only a connection-level failure before the response can make OkHttp send it once more, and the host answers an identical retry by superseding the first request (409 `Pairing request superseded by retry`) instead of failing. No evidence of a problem, so it is left as it is.

### `host.launch`

LuminalShine answers `/launch` and `/resume` with 409 and 503 errors that ask the client to retry (another launch is pending, the previous stream is still restoring the display, the GPU or display stack is not ready; host contract 7.5), and rebuilds its virtual display when the `/launch` and RTSP modes differ (7.4). Upstream Moonlight never retries and, with frame pacing "cap FPS", sends two different rates.

- `LaunchRetryRunner` runs one request through an attempt function. A `HostError(code, message)` that the hook's extractor recognises goes to `HostRules.decideLaunch`. The runner counts retries per category (the core is stateless, so a first call learns the category and a second one carries the count), waits the decided delay in slices of at most 250 ms while polling the cancel supplier, and gives up when the next wait would pass the total budget (`DEFAULT_BUDGET_MS`, 60 s). It reports `SUCCEEDED`, `FAILED` (the host answered 200 but did not start the app), `FALLBACK_TO_LAUNCH` (a `/resume` answered 503 "No running app to resume") `CONFLICT` (a `/launch` answered 400 "An app is already running on this host", only while the caller's `conflictAllowed` supplier says that it may still be resolved; otherwise the error is rethrown), or `CANCELLED`. A cancellation, including an interrupted wait, returns without throwing.
- Everything else is rethrown as the original instance, so upstream's 470/525/599 handling and `stageFailed` mapping keep working: a throwable the extractor does not recognise, a FAIL decision, an exhausted category or budget. When `HostRules` returns null (native library not loaded) the original exception is rethrown too, with one ERROR on `LogTag.LAUNCH`. Retries log the category, the attempt and the delay, never the host message. The runner has no HTTP access and never calls `/cancel`, which would also cancel another client's pending launch.
- `LaunchRetries.run(...)` is the Java-facing entry point. GFE hosts (`isNvidiaServerSoftware`) bypass the runner, as upstream. It maps the verb string, the cancel `BooleanSupplier` and a `RetryProgress` callback (category ordinal, attempt, retries allowed, delay) to the runner.
- `LaunchConflicts` (one per `NvConnection`) resolves a `CONFLICT` with the user, at most once per launch (`isOpen()` is the `conflictAllowed` supplier, so a second 400 after Join or Quit fails like any other error and the chain resolver, quit, launch, 400 cannot loop). `resolve(...)` re-reads `currentgame` from `/serverinfo` through a supplied reader for up to 3 s (every second, waiting in 250 ms slices, honouring the cancel flag): `0` launches again once through the normal path; otherwise it asks through `ConflictPrompt` (running app id and name when the app list tells it, requested app id and name; the dialog says when they are the same app) and waits for the `ConflictAnswer` in 250 ms slices. Join is `/resume` of the running app id through the existing retry path (a `/resume` that finds nothing to resume launches instead); Quit and relaunch is the upstream `quitAndLaunch`; Cancel, the dialog closing, Back, `onStop`, `cancelLaunch()` and an interrupted wait end the launch quietly, with no `stageFailed` dialog. `ConflictAnswer.choose` returns whether the choice won. It never calls `/cancel` itself and logs the choice only, never an app name.
- `LaunchProgressText.format` builds the spinner message in English from `strings_launch.xml`, for example "Another device is starting a stream on this host. Retrying in 2 s (retry 2 of 4)." The number counts retries, not requests: the first request is not a retry. There is one text per retried category plus a generic one.
- `LaunchFrameRate.forLaunch(isGfe, launchFps, streamFps)` is the `mode=` frame rate of `/launch`: the stream rate announced over RTSP for non-GFE hosts, and the upstream rule (`isGfe && launchFps > 60 ? 0 : launchFps`) for GFE and whenever `HostRules.launchFps` returns null.

Upstream hooks (each marked `// BOREALIS:`):

| File | Hook |
| --- | --- |
| `NvConnection` | `launchWithRetry` feeds `launchApp` to `LaunchRetries` for the `/resume` and `/launch` calls; the resume outcome is kept in a local so that `FALLBACK_TO_LAUNCH` calls `launchNotRunningApp` after the resume try/catch (a 470 or 525 of the fallback `/launch` is not handled as a resume error), and both calls return on `CANCELLED`; `volatile launchCancelled` with `cancelLaunch()`; `start()` returns without `stageFailed` when the flag is set, before `connectionAllowed.acquire()` and again right after it |
| `NvConnection` (conflict) | `LaunchConflicts` field; `launchWithRetry` passes `conflicts::isOpen`; `launchNotRunningApp` calls the new `resolveConflict` on `LaunchOutcome.CONFLICT`. `resolveConflict` (about 8 lines of lambdas, no logic) hands `LaunchConflicts` the `/serverinfo` read, the app-name lookup, the listener, and the existing relaunch, `joinRunning` and `quitAndLaunch` paths; `joinRunning` is the resume with upstream's "Failed to resume existing session" message when the host answers 200 without resuming |
| `NvConnectionListener` | `default launchRetrying(category, attempt, maxAttempts, delayMs)`; `default launchConflict(runningAppId, runningAppName, requestedAppId, requestedAppName, answer)`, which answers Cancel when there is no UI |
| `NvHTTP.launchApp` | the `mode=` frame rate comes from `LaunchFrameRate` |
| `Game` | `launchRetrying` and `launchConflict` forward to `StreamSession` on the UI thread (Sprint 5): the retry text and the Conflict question are phases of the stream chrome, in `:borealis:stream`. `onStop` answers a pending question as Cancel, and `onStop` and `onDestroy` call `conn.cancelLaunch()` |

The cancel flag never goes through `NvConnection.stop()`: `stop()` releases the static `connectionAllowed` semaphore unconditionally, and `Game` only calls it for a connection that was started. For the same reason a launch cancelled after `acquire()` releases the permit it just took before returning.

### `host.caps`

- `HostInfoReader.read(hostId, appVersion, codecMask, httpsPort, httpPort)` builds a `BorealisHostInfo` (the `HostCaps` of `HostRules.parseCaps` plus both ports) from the raw `appversion` and `ServerCodecModeSupport` texts of `/serverinfo`. A missing or unreadable mask counts as 0 (the low 32 bits of a larger number are kept, so `0x80000000` arrives as `2147483648`), so a malformed host cannot break the poll. It returns null when the rules are unavailable or reject the version text.
- `/serverinfo` is polled all the time, so the reader logs one INFO on `LogTag.HOST` per host, and only when the information of that host changes. `NativeHostRules.parseCaps` itself still logs a WARN on every call for an `appversion` over 64 bytes.
- `NvHTTP.getComputerDetails` stores the result in the transient `ComputerDetails.borealis`, which `update()` copies. `HostInfoText.appendTo` appends a localized block to the host details text that `ComputerHostRepository.details` returns to the Library: host software and version, HEVC, AV1, 10-bit and 4:4:4, the PyroWave profiles or "off", and the HTTP and HTTPS ports.

### `host.video`

LuminalShine can need about 18 s after the client attaches to send the first frame (contract section 10.1); upstream Moonlight dismisses the spinner at `connectionStarted`, long before that.

- `StreamStartup.create(...)` builds the `StreamStartupGuard` that `Game` holds. `Game` passes only plumbing (UI-thread runner, overlay text and visibility setters, the picture-in-picture flag and the termination callback), so its hooks hold no logic.
- `PreparingMessage` shows "host is preparing the display" on the non-modal notification overlay from `connectionStarted` until the first rendered frame. A connection warning takes precedence, and once the first frame arrives the overlay follows only the last connection status.
- `FirstFrameWatchdog` (states IDLE, ARMED, SATISFIED, FIRED, CANCELLED; one terminal state per stream) ends the stream with `ML_ERROR_NO_VIDEO_FRAME` if no frame is rendered 45 s after `connectionStarted`, above the about 35 s worst case of the patched moonlight-common-c deadlines. The termination runs off the main thread because `Game.connectionTerminated` does network I/O first. The watchdog is cancelled on stop, `onStop`, `onDestroy` and termination.
- `FirstFrameSignal` is raised by `MediaCodecDecoderRenderer` at the first output buffer released for rendering (Choreographer and renderer-thread paths); after that it costs one volatile read per frame.
- `StreamErrorHints.withHint` appends a LuminalShine hint to the -100 (no video traffic) and -101 (no complete frame) messages; the blocked-port message is left unchanged.
- `HevcSpsMonitor` (fed by `MediaCodecDecoderRenderer` with each HEVC SPS) logs the profile, chroma format and bit depth on `LogTag.VIDEO` when they change, and warns when the stream is 10-bit and the decoder does not declare Main10 or when 4:4:4 is downgraded to 4:2:0; `HevcSps` reads the SPS (emulation prevention, sub-layers, bounded Exp-Golomb) without ever throwing, so decoding is unaffected.

User-facing strings of the module stay in the module, one file per area. The app is English-only until a translation sprint: the inherited translations and the Brazilian Portuguese files were removed, and the locale config lists `en` only. Files: `res/values/strings_pairing.xml`, `strings_stream.xml` and `strings_launch.xml`.

### Fixtures in unit tests

`borealis.android.library` gives every unit-test task of a `:borealis:*` module the system property `borealis.fixtures.dir`, the absolute path of `core/tests/fixtures` (layout, provenance and scrubbing rules in its `README.md`). The directory is a test input, so changing a fixture re-runs the tests. In `:borealis:host` the test helper `Fixtures` resolves a fixture by relative path (`Fixtures.text("luminalshine/26.09.1/serverinfo-http.xml")`) and fails with a clear message when the property or the file is missing. `FixturePolicyTest` enforces the public-repository rules on every file under `luminalshine/`: scrubbed host name, `uniqueid`, MAC and addresses, and a `device` or `source` provenance row for each file.

## Library and launcher shortcuts (`:app`)

- The Library is the launcher. `LibraryActivity` (`:borealis:library`) shows the Compose screens: Hosts, Add host, Pairing, Apps and App actions. Sprint 5 removed `PcView` and `AppView`, with the 12.2 grids and the code that only they used.
- `ShortcutTrampoline` (`app/src/main/kotlin/com/limelight/ShortcutTrampoline.kt`) is the entry of every launcher shortcut. It keeps the 12.2 component name and extras (`UUID`, `Name`, `AppId`, `AppName`, `HDR`), so the shortcuts that 12.2 pinned keep working. `ShortcutIntentParser` maps the extras to a `LibraryLaunchRequest`: a PC shortcut opens the apps of that PC, an app shortcut launches that app, and a shortcut that names no PC (the name-only shortcuts of old releases) opens the host list. The trampoline starts `LibraryActivity` with clear-top and single-top, then finishes.
- A launch request starts the app as a tap does. When another app is running, it opens the conflict sheet instead.
- Opening a host publishes or updates its launcher shortcut (`HostShortcuts.reportHostOpened`). On a phone, "Add to home screen" on an app pins a shortcut for that app (`HostShortcuts.pinApp`). TVs do not offer it. Pinned app shortcuts use the generic PC icon.
- Not done: the TV channel "add to home screen" prompt after pairing.

## `:borealis:stream`

The stream chrome: the screens and state that sit over the stream in `Game`. Package root `io.github.magalz.borealis.stream`. It holds the starting screen, the conflict dialog, the connection error screen, the in-game menu with its pull handle and first-run hint, the Borealis keyboard and the performance overlay. It depends on `:borealis:host`, `:borealis:native` and `:borealis:designsystem`, and it exposes `androidx.activity` to its callers.

- Contracts: `StreamUiState` with the phases `Starting`, `Conflict`, `Failed` and `Streaming`; `StreamActions` (what the activity does for the chrome) and `StreamIntents` (what the screens call); `PerfSnapshot` and its sink. `StreamSession` implements the intents and takes events from any thread into one `StateFlow`. Once a failure is shown, late events do not change it.
- `ui/`: `StartingScreen`, `ConflictDialog`, `ConnectionErrorScreen` (with `error/`), `menu/` (`InGameMenu`, `MenuHandle`, `QuitConfirmation`), `StreamKeyboard` with `keyboard/` (layout and latch), `overlay/` (`PerfOverlay`, `PerfFormat`) and `hint/MenuHint`. `StreamChromeRoot` draws the chrome and `StreamingLayer` draws the overlay and the handle. Strings are in `res/values/` only, in English.
- `host/`: `StreamChromeHost` is a `ComposeView` that `Game` adds over the stream view with `addContentView`. Its modes are IDLE, OVERLAY, DOCKED, MODAL and HIDDEN. Idle takes no focus, touch or motion. Modal takes them all. Hidden (picture in picture) takes nothing. `InputRoute` decides, from the mode, whether a key or motion event goes to the chrome or to the PC, and `HeldKeys` tracks the keys sent to the PC so that they are released when the chrome takes the input.
- `input/`: `MenuChordDetector` opens the menu when Select and Start are held for one second. `GuideChord` sends a Guide tap when Start and Select are released early on a pad without a Mode button. `ChromeKeySink` turns the buttons of a USB-driver pad into the key events the menu uses.
- `keys/VirtualKeys`: the chords of the Send keys row and of the keyboard shortcut row, with their Windows virtual-key codes and modifiers.

Input routing:

- In MODAL mode the chrome takes the keys, except the volume and media keys, and the motion events. A hardware Escape acts as Back.
- In the other modes the stream keeps the keys and the touches. A docked keyboard takes no focus, so physical keys still go to the PC.
- While the Android keyboard is open, a gamepad's D-pad, Enter and Back go to it, not to the PC (`ImeKeyGate` in `:app`).
- Back opens the menu while the stream runs, closes an open panel, leaves while the stream starts or after a failure, and cancels the conflict question.

`Game` wiring: `Game` extends `ComponentActivity`. `StreamSessionFactory` in `:app` builds the session, and `GameStreamActions` in `:app` implements `StreamActions` on the connection. `Game` forwards the stage, retry, conflict, failure, connection and performance events to the session. The manifest opts `Game` out of predictive back, so that the Back key reaches `onBackPressed` (`BackOptOutTest`).

Tests: `StreamSessionTest`, `StreamSessionChromeModeTest`, `StreamBackTest`, `StreamChromeHostTest`, `InputRouteTest`, `MenuChordDetectorTest`, `GuideChordTest`, `StreamKeyboardTest`, `KeyboardLatchTest`, `InGameMenuTest`, `MenuHandleTest`, `ConnectionErrorScreenTest`, `StartingScreenTest` and `ConflictDialogTest`. Roborazzi references are in `src/test/screenshots/` (see [building](../../../docs/development/building.md#screenshot-tests)).
