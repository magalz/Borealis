# Building

Primary development platform is Windows. Linux notes for CI are at the end.

## Prerequisites

| Tool | Version | Used for |
| --- | --- | --- |
| Git | recent | Source control. The inherited Moonlight code and the vendored moonlight-common-c are in this repository ([ADR 0054](../adr/0054-declare-independence-from-moonlight.md)). |
| JDK | 17 | Gradle and Android build. |
| Android SDK | platform `android-37.0`, build-tools `37.0.0`, platform-tools | Android client. |
| Android NDK | `29.0.14206865` | Native code (moonlight-core). |
| CMake | `4.1.2` (SDK) for Android; host `core/` needs 3.25 or newer | Native builds. |
| Ninja | any recent (the SDK's `cmake/4.1.2/bin` ships `ninja.exe`) | `core/` presets. |
| clang / LLVM | recent | `core/` host build, clang-tidy. |
| clang-format | 22.x (CI pins 22.1.8 via `CLANG_FORMAT_VERSION` in `.github/workflows/ci.yml`) | Formatting of `core/` and Borealis C/C++ in `apps/android` (Spotless calls it from PATH). A different major version can format differently; `pip install clang-format==22.1.8` gives the same binary as CI. |

## Clone

```sh
git clone https://github.com/magalz/Borealis.git
cd Borealis
git config core.hooksPath .githooks
```

No submodules are needed. The streaming protocol library (moonlight-common-c,
with ENet and nanors) is vendored under `core/third_party/moonlight-common-c`
and built from there by the native build; see `core/third_party/README.md`.

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
   path. Escape the drive-letter colon (`C\:`), use forward slashes (or `\\`),
   and save the file with LF line endings; otherwise Android Lint reports
   `PropertyEscape`:

   ```properties
   sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
   ```

3. Make sure `JAVA_HOME` points at JDK 17 (`java -version` must report 17).

## Android client

Run from `apps/android/`. The app has one variant per build type, `debug` and
`release`.

```sh
cd apps/android
./gradlew assembleDebug
```

The debug APK is `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

Checks (format, static analysis, lint and JVM unit tests):

```sh
./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test licensee
```

Fix formatting of Borealis code:

```sh
./gradlew spotlessApply
```

Spotless does not touch upstream files. If it wants to change one, stop and
report it; never reformat upstream code. Spotless formats Borealis C/C++ with
the `clang-format` on `PATH`; without it the C/C++ Spotless tasks fail with
"clang-format 22.x not found on PATH" while the rest of the build keeps working.

### Lint baseline

`apps/android/app/lint-baseline.xml` hides the Android Lint findings that
Borealis accepts; every other finding fails the `lint` task. It holds no
finding that the code can satisfy: the dead SDK-version checks, the unused
resources and the dependencies declared outside the version catalog were fixed
in the Sprint 8 sweep. Lint reports a baseline entry that no longer matches as
fixed, so regenerate the file with `./gradlew :app:updateLintBaseline` in the
commit that fixes the code, and check that it has no absolute or Windows path
(CI fails on that). This is the rule for the file in every case, also when a
cherry-pick from upstream brings new findings.

The convention plugins turn off the lint checks that compare the versions in
use with what upstream has released (`NewerVersionAvailable`,
`GradleDependency`, `AndroidGradlePluginVersion` and `OutdatedLibrary`). Their
result changes with the date, so a build would fail without any change in the
code. Lint does not check for newer versions; Dependabot does, except for AGP,
which it ignores (see [dependencies](../policies/dependencies.md#updates-dependabot)).

What stays, and why:

| Lint ids | Why they stay |
| --- | --- |
| `UnusedAttribute`, `RtlHardcoded`, `UseRequiresApi` | Cosmetic. The attributes apply from API 30 or 33 and older versions ignore them. The gravity in `activity_game.xml` is the stream notification overlay. The two `UseRequiresApi` entries suggest `@RequiresApi` in place of `@TargetApi` in `Game.java` (converted in Sprint 14) and in `NsdManagerDiscoveryAgent.kt`. The `@TargetApi` annotations are for API 30 and later; the ones for API 28 and below went with the dead checks. |
| `OldTargetApi` | A `targetSdk` of 37 is a platform update, decided on its own and not in a lint sweep. |
| `NotShrinkingResources` | Release builds minify without `shrinkResources`. Turning it on changes the release package, so it is a release decision. |
| `DefaultLocale`, `ApplySharedPref`, `SourceLockedOrientationActivity`, `VectorRaster` | Inherited code that does what it should. `toLowerCase()` runs on device and GPU names, in `MediaCodecHelper.java` (converted in Sprint 14). `commit()` is deliberate: the crash listener must persist the crash count before the process dies. The stream locks the orientation on purpose. `ic_settings.xml` is a large vector. |

`ViewConstructor` has no baseline entry since Sprint 13: the seven virtual-controller views
carry `@SuppressLint("ViewConstructor")` in source, because they are built in code only.

`apps/android/app/lint.xml` holds four more ignores, by path, beside the
baseline:

| Lint id and path | Why |
| --- | --- |
| `InvalidPackage` in `**/bcpkix-jdk15on-*.jar` | Inherited from upstream. It names the old Bouncy Castle jar; the app uses `bcpkix-jdk18on` now, so the pattern matches nothing. |
| `TrustAllX509TrustManager` in `**/bcpkix-jdk18on-*.jar` | The finding is in the Bouncy Castle jar, not in Borealis code. A baseline entry would hold the absolute path of the jar in the Gradle cache, which differs between machines. |
| `IconMissingDensityFolder` in `src/main/res` | The message holds the resource folder as a machine-specific path (a Windows path on the development machine), so a baseline entry does not match on Linux CI. |
| `IconXmlAndPng` in `src/main/res/mipmap-*/ic_launcher.png` | The legacy launcher PNGs next to the adaptive icon are deliberate; see [design/icon/README.md](../../design/icon/README.md). |

Security findings, each reviewed on 2026-10-17:

- **`SetJavaScriptEnabled`.** Removed by the owner's decision. `HelpActivity`
  was a WebView on Moonlight's troubleshooting wiki, reached only from the Help
  button of the decoder-crash dialog. The activity, `HelpLauncher` and the
  button are gone, so the app no longer has a WebView.
- **`ExportedContentProvider`** (`PosterContentProvider`). The Android TV home
  screen reads the poster URIs of the TV channel from another process, so the
  provider must stay exported. It is read-only (`insert`, `update`, `delete`
  and `query` throw, and any mode but `r` is refused) and serves only
  `cache/boxart/<host id>/<app id>.png`: `PosterRequest` accepts a host id of
  hexadecimal digits and hyphens and a non-negative number as app id, and
  `PosterContentProviderTest` covers the traversal attempts.
- **`InsecureBaseConfiguration`** (`network_security_config.xml`). GameStream
  speaks plain HTTP to the host the user typed, by address or by name:
  `/serverinfo` (the first call and the fallback) and the pairing handshake go
  to `http://<host>:47989`, and the other requests use HTTPS pinned to the
  certificate stored at pairing. A `domain-config` cannot name an arbitrary
  address, so limiting cleartext would break hosts typed by IP. The base config
  trusts the system CAs only.
- **`CustomX509TrustManager`** (`NvHTTP`). It asks the platform trust manager
  first and only then accepts the one certificate pinned at pairing, so it does
  not trust everything.
- **`MissingLeanbackLauncher`.** A false positive: lint reads the `:app`
  manifest, while the launcher entry, `LibraryActivity`, is declared in
  `:borealis:library` with the `LEANBACK_LAUNCHER` filter, and
  `LauncherEntryTest` checks it in the merged manifest.

### Static analysis (detekt)

`./gradlew detekt` is the one entry point. In every Android module (`:app` and
`:borealis:*`) it runs detekt with type resolution on the `debug` sources, the
unit tests, the instrumented tests and the test fixtures; from the root it runs
the plain task on `build-logic`. One module alone: `./gradlew :borealis:host:detekt`.

- **Why typed:** the plain task skips every rule that needs the compiler's
  analysis, about 40 of the active ones: unused private members, unreachable
  code, `!!` and unsafe casts, platform types, long parameter lists and more.
  The typed task compiles the module first, as the unit tests do, so it costs
  more than the plain one did.
- **One config:** `apps/android/config/detekt/detekt.yml`, on top of detekt's
  defaults, for all modules. Its overrides:
  - `MatchingDeclarationName` is off for all sources. A Compose file is named
    after its composable and keeps one small helper type beside it, which the
    rule reads as a mismatch, and detekt has no option for that.
  - `FunctionNaming` and `LongParameterList` ignore functions annotated
    `@Composable`: composables are named in PascalCase and take their state and
    callbacks as parameters.
  - `ReturnCount` allows 3 returns, and `MagicNumber` ignores enums.
- **`build-logic` stays plain:** the analysis does not apply the compiler
  plugins of the Kotlin DSL and would read its Gradle code as unresolved.
- **No baseline:** every finding fails the build. Fix it, or, when a rule does
  not fit the code (a framework constraint, a Java caller), suppress it on the
  narrowest element with the reason on the same line. Sprint 8 fixed the 116
  findings the typed analysis first found, except that two rules are no longer
  reported in test sources (below), and removed the temporary baselines.
- **The analysis runs in a worker process** (`detekt.use.worker.api=true` in
  `apps/android/gradle.properties`). In the Gradle daemon it once kept the jars
  of a module's compile classpath (`R.jar`, `classes.jar`) open on Windows,
  while several builds ran at once, and the next build that regenerated one
  failed with "Couldn't delete ... R.jar". The worker ends with the build. A
  warm local run takes about 30 s more; CI, which starts cold, is unchanged.
- **Two rules are off in test sources:** `InjectDispatcher` and
  `LongParameterList` (see `config/detekt/detekt.yml`). The Java to Kotlin
  migration turns them back on in Sprint 14 and standardizes the tests.

### Screenshot tests

The Compose screens in `:borealis:designsystem`, `:borealis:library`,
`:borealis:stream` and `:borealis:settings` are checked against reference images
([ADR 0034](../adr/0034-screenshot-testing-with-roborazzi.md)). The references
are in `src/test/screenshots/` of each module. Run these commands from
`apps/android/`.

Record the references on Windows, after a change that alters a screen:

```sh
./gradlew :borealis:designsystem:recordRoborazziDebug :borealis:library:recordRoborazziDebug :borealis:stream:recordRoborazziDebug :borealis:settings:recordRoborazziDebug
```

Verify the screens against the committed references:

```sh
./gradlew :borealis:designsystem:testDebugUnitTest :borealis:library:testDebugUnitTest :borealis:stream:testDebugUnitTest :borealis:settings:testDebugUnitTest -Proborazzi.test.verify=true
```

- The "Android screenshots" job in CI (`.github/workflows/ci.yml`) runs the
  verify command on `windows-latest`. It fails when a screen differs from its
  reference, and it uploads the differences as an artifact. Since Sprint 10 the
  job counts toward the required "CI result" check, so a screen that differs
  blocks the merge.
- The Linux Android job renders the same tests without comparing them. The
  tests still count for coverage.
- If the Windows rendering ever drifts between the development machine and
  `windows-latest`, the fallback is to record and verify on Linux instead
  (WSL2 or a manual CI job). That fallback is not set up yet.

### Contrast tool and the glass table

`design/accessibility/` holds a Python tool that measures the contrast of text
over the animated backgrounds and writes the glass table of the app. Its tests
are plain `unittest`. Run them from the repository root; they need Python 3.11
or later, `numpy` and `Pillow`:

```sh
python -m unittest discover -s design/accessibility
```

- The "Design tools" job in CI runs the same tests, with
  `python -I -m unittest discover -s design/accessibility -v` on Python 3.14,
  numpy 2.4.6 and Pillow 12.2.0, the versions the tool was tried with. It runs
  when anything under `design/` changes, and when the glass table
  (`GlassContrastTable.kt`), the icons (`BorealisIcons.kt`) or a background clip
  (`background_*.mp4`) changes, because the tests of the tool read those files.
  The job counts toward "CI result".
- `GlassContrastTable.kt` is generated by `contrast.py glass-table`; do not edit
  it by hand. It carries the SHA-256 of each clip, and
  `GlassContrastTableClipsTest` in `:borealis:designsystem` fails when a clip
  changes without a new measurement. The two backgrounds that are drawn in code
  have no clip, so a change to them needs the table measured again by hand
  (see [design/backgrounds](../../design/backgrounds/README.md)).
- The measurement itself needs the frames of the backgrounds, `ffmpeg` and
  Gradle: see [design/accessibility](../../design/accessibility/README.md).
- `docs/ux/accessibility.md` is a Gradle test input of `:borealis:designsystem`.
  `BackdropHaloTest` and the tests of the halo read the line "The choice: ..."
  of that file, so every edit of it runs the unit tests of the module again, and
  the line must stay once, on one line.

### Coverage, licences and SBOM

```sh
./gradlew koverXmlReportBorealis koverHtmlReportBorealis   # coverage (debug unit tests)
./gradlew licensee                                         # licence allowlist
./gradlew :app:cyclonedxDirectBom                          # Gradle SBOM of the release classpath
```

- **Coverage** (Kover, [ADR 0046](../adr/0046-test-coverage-with-kover-llvm-cov-and-codecov.md)):
  the root project merges the debug unit-test coverage of `:borealis:*` and of
  `:app`. Code in `com.limelight` counts in the Kover report, except the Java
  that stays Java (below). The input package `com.limelight.binding.input`
  counts since Sprint 13 converted it. Converted code is held to the same Codecov gate as the rest
  ([ADR 0059](../adr/0059-convert-the-inherited-java-to-kotlin.md),
  [ADR 0049](../adr/0049-coverage-gate.md)). Not counted:
  - the Java that stays Java until Sprint 14:
    `com.limelight.Game` with its nested classes, and the packages
    `com.limelight.binding.video`, `com.limelight.binding.audio` and
    `com.limelight.nvstream.jni`. `BorealisCoverage.EXCLUDED_CLASSES` lists them
    and shrinks as each one is converted;
  - generated Hilt/Dagger, Room and serialization classes, `BuildConfig`, `R`
    and Compose singleton classes.

  Output: `apps/android/build/reports/kover/coverage.xml`
  (JaCoCo format, uploaded to Codecov) and
  `apps/android/build/reports/kover/htmlBorealis/index.html`. The XML names
  sources package-relative (`io/github/magalz/borealis/...`,
  `com/limelight/...`); Codecov matches them to repository files by path
  suffix. Java under `app/src/main/java` never reaches Codecov (`codecov.yml`
  ignores it); the Java that stays is also excluded from the Kover report.
- **Licences** (licensee, [ADR 0041](../adr/0041-license-checking-with-licensee.md)):
  `licensee` runs for `:app` (the debug and release runtime classpaths) and for
  every `:borealis:*` module, and is part of `check`. Allowed: Apache-2.0, MIT,
  BSD-2-Clause, BSD-3-Clause, ISC and Zlib, plus the URL-only licences of
  Bouncy Castle and JCodec, each justified in
  `build-logic/.../BorealisLicenses.kt`. A new dependency with any other
  licence fails the build; do not widen the list without an ADR (see the
  [dependency policy](../policies/dependencies.md)). `coreLibraryDesugaring`
  (desugar_jdk_libs, GPL-2.0 with Classpath Exception) is not part of the
  scanned runtime classpath.
- **SBOM** ([ADR 0042](../adr/0042-sbom-with-cyclonedx.md)):
  `:app:cyclonedxDirectBom` writes
  `apps/android/app/build/reports/sbom/borealis-android.cdx.json`
  (CycloneDX 1.6) for the `release` runtime classpath. Native components
  that Gradle cannot see are listed by hand in
  `apps/android/sbom/native-components.cdx.json`; update it as described in
  [upstream-sync.md](upstream-sync.md). Both files are attached to every
  release.

The checks a CI run should execute are:

```sh
./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test assembleDebug licensee koverXmlReportBorealis
```

A change under `design/`, or to the glass table, the icons or the clips, also
runs the contrast tool's tests (above).

### Release build (dry run)

The release build is unsigned by default and needs an `android-vX.Y.Z` tag in
the history (`git tag android-v0.0.1`, delete it afterwards). It is signed only
when the four `BOREALIS_*` values from the
[Borealis module README](../../apps/android/borealis/README.md) are set, which
the release workflow never does: it signs the unsigned artifacts afterwards.

```sh
./gradlew assembleRelease
# apps/android/app/build/outputs/apk/release/app-release-unsigned.apk
```

There is no AAB until Phase 5 (see [release.md](release.md)). To try the
release checks and signing, run `.github/scripts/check-apk.sh` and
`.github/scripts/sign-apk.sh` against the unsigned APK with a throwaway
keystore created outside the repository, or start the dry run on GitHub
(`gh workflow run release.yml --ref <branch>`).

Other test levels: Robolectric and Compose UI tests run with the unit tests,
and screenshot tests use Roborazzi (see above). Emulator tests with Gradle
Managed Devices are planned and not set up.

Each unit-test task gets a temporary folder of its own as `java.io.tmpdir`
(`BorealisTestTempDir.kt`). On Windows it is `<drive>\.bt\<8 hex>` at the root
of the checkout's drive, unique per checkout, module and task, so that
Robolectric's SQLite paths stay under the 260 characters Windows allows.
Elsewhere it is `build/tmp/<task>/jvm-tmp`. The task empties the folder before a
run and deletes it after the run. A failed or killed run leaves the folder until
the next run of that task. The `:app` unit-test JVM has a 2 GB heap
(`BorealisUnitTestHeap.kt`), because Robolectric keeps a sandbox for each SDK
level and set of shadows.

## borealis-core (C++)

Run from `core/`. Presets: `dev`, `asan`, `release` and `coverage`.

```sh
cd core
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

Formatting check, as CI runs it (clang-format 22; the version is pinned in `.github/workflows/ci.yml`):

```sh
cd core
find include src tests fuzz -type f \( -name '*.h' -o -name '*.hpp' -o -name '*.cpp' \) -print0 | xargs -0 clang-format --dry-run --Werror
```

Sanitizers (ASan/UBSan):

```sh
cmake --preset asan
cmake --build --preset asan
ctest --preset asan
```

Coverage (clang and llvm-cov; the report is `core/build/coverage/coverage.lcov`):

```sh
sh scripts/coverage.sh
```

The "Core (C++)" job in CI also runs clang-tidy, the ASan/UBSan build and this
coverage report. It runs the libFuzzer targets (Linux with clang only) when
`core/` or `.github/workflows/ci.yml` changed in the pull request, and on every
run that is not a pull request. See [core/README.md](../../core/README.md) for
clang-tidy, the fuzzers and the environment variables of the coverage script.

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
- A plain checkout is enough: there are no submodules. Fetch the full history
  (`fetch-depth: 0`) because the version is derived from tags.
- Shell scripts are POSIX `sh`/`bash`; paths use forward slashes.
- Release signing happens only in the `release` environment (see
  [release.md](release.md)); pull request builds never receive secrets.

## Troubleshooting

- *SDK not found:* check `ANDROID_HOME` and `apps/android/local.properties`.
- *Native build fails with missing headers:* check that `core/third_party/moonlight-common-c` exists (the checkout is incomplete).
- *Wrong Java version:* Gradle needs JDK 17; check `JAVA_HOME`.
