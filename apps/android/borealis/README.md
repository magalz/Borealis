# Borealis Android modules

Borealis-owned Kotlin code lives here; `apps/android/app` stays the (minimally hooked) upstream Moonlight module.
New code is Kotlin under the package root `io.github.magalz.borealis`.

## Build setup

- `../build-logic` is an included build (registered with `pluginManagement { includeBuild }` in `settings.gradle`) with the convention plugins:
  - `borealis.android.application`: applied by `app/build.gradle`. Through the AGP variant API it sets the application id (`io.github.magalz.borealis`, `.debug` suffix for debug), the labels (`Borealis` / `Borealis Debug`), minSdk 28, disables the upstream `root` flavor, derives the version from git, configures release signing, the Android Lint baseline and Hilt/KSP.
  - `borealis.android.library`: use it for every `:borealis:*` module (compileSdk 37, minSdk 28, Java 17, Lint warnings are errors, JUnit 4 + Truth).
  - `borealis.settings`: applies Spotless (ktfmt, kotlinlang style) and detekt to Borealis-owned paths only. Upstream Java/Groovy is never formatted.
- Versions live in `../gradle/libs.versions.toml`.
- Upstream hooks are marked `BOREALIS` in `settings.gradle`, `app/build.gradle` and `app/src/main/AndroidManifest.xml`.

### Version

`versionName`/`versionCode` come from the latest `android-vMAJOR.MINOR.PATCH` tag (`versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`). Without a tag: `0.0.0-dev`, code 1. Commits after a tag show as `1.2.3-5-gabcdef1`.

### Release signing

Release stays unsigned unless all four values are present, either in `apps/android/keystore.properties` (git-ignored) or in the environment:

| keystore.properties | environment |
| --- | --- |
| `storeFile` | `BOREALIS_KEYSTORE_FILE` |
| `storePassword` | `BOREALIS_KEYSTORE_PASSWORD` |
| `keyAlias` | `BOREALIS_KEY_ALIAS` |
| `keyPassword` | `BOREALIS_KEY_PASSWORD` |

### Checks (from `apps/android`)

```
./gradlew spotlessCheck detekt lint testDebugUnitTest assembleNonRootDebug
./gradlew :build-logic:test        # build-logic unit tests
./gradlew spotlessApply            # format Borealis-owned Kotlin
./gradlew :app:updateLintBaseline  # only when deliberately accepting upstream lint findings
```

Lint on `:app` uses `app/lint-baseline.xml` for the existing upstream findings; anything new fails the build, and Borealis modules have no baseline.
An opt-in pre-commit hook lives in `.githooks` at the repository root (`git config core.hooksPath .githooks`).

## `:borealis:logging`

`BorealisLog` is the only logging entry point for Borealis code.

- Levels `VERBOSE..ERROR`, subsystem tags `LogTag` (APP, HOST, PAIR, LAUNCH, RTSP, VIDEO, PYROWAVE, INPUT, REST, CORE, UPSTREAM), structured fields: `BorealisLog.i(LogTag.HOST, "paired", "host" to name)` renders as `... INFO HOST paired host=name`.
- Before `BorealisLog.initialize(...)` (done in `BorealisApplication.onCreate`) records are dropped; sinks never throw into the caller.
- `Redactor` runs on every message, field and stack trace before any sink: labelled PINs, `Bearer` and `token=`/`apiToken=` values, PEM blocks and long hex/base64 values in `cert`/`key`/`signature` fields. IP masking is opt-in (`Redactor(maskIpAddresses = true)`).
- Sinks: `LogcatSink` (tag `Borealis.<TAG>`) and `RotatingFileSink` (`filesDir/logs/borealis.log`, 5 files x 1 MB, one line per record, ISO-8601 UTC).
- `JulBridge.install()` forwards the upstream `LimeLog` (`java.util.logging`) to `BorealisLog` with tag `UPSTREAM`.
- `CrashHandler.install(dir, header)` writes a redacted `crash-<timestamp>.txt` next to the logs and delegates to the previous handler.
