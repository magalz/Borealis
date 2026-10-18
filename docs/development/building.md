# Building

Primary development platform is Windows. Linux notes for CI are at the end.

## Prerequisites

| Tool | Version | Used for |
| --- | --- | --- |
| Git | recent | Source control and the Moonlight subtree. |
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

### Screenshot tests

The Compose screens in `:borealis:designsystem`, `:borealis:library` and
`:borealis:stream` are checked against reference images
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
  reference, and it uploads the differences as an artifact.
- The Linux Android job renders the same tests without comparing them. The
  tests still count for coverage.
- If the Windows rendering ever drifts between the development machine and
  `windows-latest`, the fallback is to record and verify on Linux instead
  (WSL2 or a manual CI job). That fallback is not set up yet.

### Coverage, licences and SBOM

```sh
./gradlew koverXmlReportBorealis koverHtmlReportBorealis   # coverage (debug unit tests)
./gradlew licensee                                         # licence allowlist
./gradlew :app:cyclonedxDirectBom                          # Gradle SBOM of the release classpath
```

- **Coverage** (Kover, [ADR 0046](../adr/0046-test-coverage-with-kover-llvm-cov-and-codecov.md)):
  the root project merges the debug unit-test coverage of `:borealis:*` and of
  the Borealis Kotlin in `:app`. Upstream `com.limelight.*`
  classes and generated Hilt/Dagger, `BuildConfig`, `R` and Compose singleton
  classes are excluded. Output: `apps/android/build/reports/kover/coverage.xml`
  (JaCoCo format, uploaded to Codecov) and
  `apps/android/build/reports/kover/htmlBorealis/index.html`. The XML names
  sources package-relative (`io/github/magalz/borealis/...`); Codecov matches
  them to repository files by path suffix.
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
