# Architecture Decision Records

This folder holds the architecture decision records (ADRs) for Borealis. Each ADR records one decision, the options that were considered, the chosen option and its consequences. ADRs use the [MADR 4](https://adr.github.io/madr/) format; the template is [0000-template.md](0000-template.md).

## Adding an ADR

1. Copy [0000-template.md](0000-template.md) to a new file named `NNNN-kebab-case-title.md`, where `NNNN` is the next free number (the index below lists the last one).
2. Fill in the front matter (`status`, `date`, `decision-makers`), the context, the considered options, the decision outcome with "Chosen option: ..., because ...", the consequences and the pros and cons of each option.
3. Set the status to `proposed` while the decision is open.
4. Add a row to the index below.
5. Link the ADR from related ADRs in their "More Information" section, where relevant.

## Status lifecycle

```text
proposed --> accepted --> superseded by ADR-NNNN
                     \--> deprecated
```

* **proposed**: the decision is open for discussion and not yet in force.
* **accepted**: the decision is in force. An accepted ADR is never rewritten.
* **superseded by ADR-NNNN**: a later ADR replaces this decision. Write a new ADR, mark the old one as superseded, and link both ways.
* **deprecated**: the decision no longer applies and nothing replaces it.

Never rewrite an accepted ADR to change its decision. If a decision changes, write a new ADR that supersedes it. Small factual corrections (typos, broken links) are allowed.

## Index

| Number | Title | Status | Date |
| --- | --- | --- | --- |
| 0001 | [Name the product Borealis](0001-name-the-product-borealis.md) | accepted | 2026-10-13 |
| 0002 | [Base the app on vanilla Moonlight 12.2](0002-base-the-app-on-vanilla-moonlight-12-2.md) | accepted | 2026-10-13 |
| 0003 | [Native apps per platform with a shared C/C++ core](0003-native-apps-per-platform-with-shared-core.md) | accepted | 2026-10-13 |
| 0004 | [Use Kotlin for new Android code](0004-kotlin-for-new-android-code.md) | accepted | 2026-10-13 |
| 0005 | [Use Jetpack Compose with Material 3 and Compose for TV](0005-ui-toolkit-and-visual-style.md) | accepted | 2026-10-13 |
| 0006 | [Set minSdk to 28](0006-min-sdk-28.md) | accepted | 2026-10-13 |
| 0007 | [Use the official Android app architecture](0007-official-android-app-architecture.md) | accepted | 2026-10-13 |
| 0008 | [Share a C++20 core (borealis-core) with protocol, decoder and host rules](0008-shared-core-language-and-scope.md) | accepted | 2026-10-13 |
| 0009 | [Use an organized monorepo](0009-organized-monorepo-layout.md) | accepted | 2026-10-13 |
| 0010 | [Build the Android native core via Prefab](0010-prefab-for-android-native-build.md) | accepted | 2026-10-13 |
| 0011 | [Use DataStore, Room and Android Keystore for new persistence](0011-datastore-room-and-keystore-for-new-persistence.md) | accepted | 2026-10-13 |
| 0012 | [Use Hilt for dependency injection](0012-dependency-injection-with-hilt.md) | accepted | 2026-10-13 |
| 0013 | [Use GitHub Flow with merge commits and Conventional Commits](0013-github-flow-with-merge-commits.md) | accepted | 2026-10-13 |
| 0014 | [Version each app with SemVer and prefixed tags](0014-semver-per-app-with-prefixed-tags.md) | accepted | 2026-10-13 |
| 0015 | [Use GitHub Actions for CI/CD](0015-github-actions-for-ci-cd.md) | accepted | 2026-10-13 |
| 0016 | [Adopt four test layers and TDD](0016-testing-strategy-and-tdd.md) | accepted | 2026-10-13 |
| 0017 | [Use Spotless, detekt, Android Lint and clang-tidy with a pre-commit hook](0017-formatting-and-static-analysis.md) | accepted | 2026-10-13 |
| 0018 | [Write all repository content in English](0018-language-of-repository-content.md) | accepted | 2026-10-13 |
| 0019 | [Record decisions as MADR ADRs and changes in a generated changelog](0019-decision-and-change-records.md) | accepted | 2026-10-13 |
| 0020 | [Use an own logger facade (BorealisLog) and a diagnostic bundle](0020-logging.md) | accepted | 2026-10-13 |
| 0021 | [Adopt supply-chain security, an in-app audit trail and license compliance](0021-audit.md) | accepted | 2026-10-13 |
| 0022 | [Use opt-in Sentry for crash reporting](0022-crash-reporting-and-privacy.md) | accepted | 2026-10-13 |
| 0023 | [Distribute through GitHub Releases, Google Play and Amazon Appstore](0023-distribution-channels.md) | accepted | 2026-10-13 |
| 0024 | [Make the repository public from the start](0024-public-repository-from-the-start.md) | accepted | 2026-10-13 |
| 0025 | [Require PRs with green CI and let only the owner merge, with AI assistance](0025-review-and-ai-assisted-development-policy.md) | accepted | 2026-10-13 |
| 0026 | [Use one owner-generated app signing key for all channels](0026-signing-keys.md) | accepted | 2026-10-13 |
| 0027 | [Organize AI agents as an orchestrator with specialized br-* roles](0027-agent-organization.md) | accepted | 2026-10-13 |
| 0028 | [Use io.github.magalz.borealis as applicationId](0028-application-id.md) | accepted | 2026-10-14 |
| 0029 | [Use Kotlin Coroutines and Flow for async](0029-async-with-coroutines-and-flow.md) | accepted | 2026-10-13 |
| 0030 | [Use kotlinx.serialization](0030-serialization-with-kotlinx.md) | accepted | 2026-10-13 |
| 0031 | [Use OkHttp with kotlinx.serialization for the LuminalShine REST client](0031-rest-client-okhttp.md) | accepted | 2026-10-13 |
| 0032 | [Use Coil 3 for image loading](0032-image-loading-with-coil.md) | accepted | 2026-10-13 |
| 0033 | [Use Navigation Compose with type-safe routes](0033-navigation-compose.md) | accepted | 2026-10-13 |
| 0034 | [Use Roborazzi for screenshot testing](0034-screenshot-testing-with-roborazzi.md) | accepted | 2026-10-13 |
| 0035 | [Store the REST token with Tink and Android Keystore](0035-secrets-storage-with-tink-and-keystore.md) | accepted | 2026-10-13 |
| 0036 | [Use Kotlin DSL, version catalog and convention plugins for new Gradle modules](0036-gradle-kotlin-dsl-and-convention-plugins.md) | accepted | 2026-10-13 |
| 0037 | [Use ktfmt with the kotlinlang style](0037-kotlin-formatter-ktfmt.md) | accepted | 2026-10-13 |
| 0038 | [Use .githooks with core.hooksPath for git hooks](0038-git-hooks-with-githooks.md) | accepted | 2026-10-13 |
| 0039 | [Generate the changelog with git-cliff](0039-changelog-with-git-cliff.md) | accepted | 2026-10-13 |
| 0040 | [Use Dependabot for dependency updates](0040-dependency-updates-dependabot.md) | accepted | 2026-10-13 |
| 0041 | [Use licensee for license checking](0041-license-checking-with-licensee.md) | accepted | 2026-10-13 |
| 0042 | [Generate SBOMs with CycloneDX](0042-sbom-with-cyclonedx.md) | accepted | 2026-10-13 |
| 0043 | [Use a thin C API with hand-written JNI](0043-thin-c-api-with-hand-written-jni.md) | accepted | 2026-10-13 |
| 0044 | [Generate BorealisTokens.kt from design/tokens.json](0044-design-tokens-pipeline.md) | accepted | 2026-10-13 |
| 0045 | [Automate store publishing with Gradle Play Publisher and an Amazon submission script](0045-store-publishing-automation.md) | proposed | 2026-10-13 |
| 0046 | [Measure test coverage with Kover and llvm-cov, reported to a self-hosted Codecov](0046-test-coverage-with-kover-llvm-cov-and-codecov.md) | accepted | 2026-10-13 |
| 0047 | [Harvest upstream code, then emancipate at a milestone](0047-harvest-upstream-then-emancipate.md) | accepted | 2026-10-14 |
| 0048 | [Fork moonlight-common-c with a first-frame deadline patch](0048-fork-moonlight-common-c.md) | superseded by ADR-0054 | 2026-10-14 |
| 0049 | [Gate pull requests on 90% patch coverage and a 1% project tolerance](0049-coverage-gate.md) | accepted | 2026-10-14 |
| 0050 | [Record diagnostics, audit trail, interim settings and the first release channel](0050-diagnostics-audit-and-interim-settings.md) | accepted | 2026-10-14 |
| 0051 | [Send the diagnostic bundle from a TV to a phone with a QR code](0051-send-the-diagnostic-bundle-to-a-phone-with-a-qr-code.md) | accepted | 2026-10-15 |
| 0052 | [Structure the Phase 2 UI: hosts home, a Back-opened in-game menu and a dark theme](0052-structure-the-phase-2-ui.md) | accepted | 2026-10-15 |
| 0053 | [Use Material 3 with a Borealis focus indication on TV](0053-material-3-with-borealis-focus-on-tv.md) | accepted | 2026-10-15 |
| 0054 | [Declare independence from Moonlight](0054-declare-independence-from-moonlight.md) | accepted | 2026-10-15 |
| 0055 | [Store settings through one repository and build the Phase 2 settings screen](0055-settings-storage-and-the-phase-2-settings-screen.md) | accepted | 2026-10-16 |
| 0056 | [Add an animated background and the Appearance group](0056-add-an-animated-background-and-the-appearance-group.md) | accepted | 2026-10-19 |
| 0057 | [Add glass surfaces and the Accessibility settings](0057-glass-surfaces-and-the-accessibility-settings.md) | accepted | 2026-10-20 |
| 0058 | [Add the appearance and reading options](0058-appearance-and-reading-options.md) | accepted | 2026-10-21 |
| 0059 | [Convert the inherited Java to Kotlin](0059-convert-the-inherited-java-to-kotlin.md) | accepted | 2026-10-23 |
