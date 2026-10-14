---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0016 Adopt four test layers and TDD

## Context and Problem Statement

Which testing layers should Borealis use, and what development paradigm should govern them? The decision covers four layers, rolled out progressively. TDD (red-green-refactor, a RED test commit before the implementation) is the development paradigm.

## Considered Options

All four layers are adopted together, so the options below are the layers themselves rather than competing alternatives.

* Unit tests on the JVM and core C++ tests
* Robolectric and Compose UI tests on the JVM
* Emulator tests (Gradle Managed Devices) and screenshot tests (Roborazzi)
* Host contract tests with golden fixtures captured from a real LuminalShine

## Decision Outcome

Chosen options: all four layers, rolled out progressively, with TDD as the paradigm:

* Unit tests on the JVM (JUnit 4, MockK, Turbine, Truth) and core C++ tests (GoogleTest, as LuminalShine; ASan/UBSan; libFuzzer on parsers). JUnit 4 is used because Robolectric and instrumented tests use its runner.
* Robolectric and Compose UI tests on the JVM, for fast UI coverage without a real decoder or GPU.
* Emulator tests (Gradle Managed Devices) and screenshot tests (Roborazzi), which catch visual regressions of the theme. Reference images must be maintained.
* Host contract tests: golden fixtures captured from a real LuminalShine (serverinfo, launch responses, SDP), reusable by future platforms and recaptured per host version. Optional real-host end-to-end tests.

### Consequences

* Good, because the layers cover logic, UI, visuals and the host contract.
* Good, because the fixtures are reusable by future platforms.
* Bad, because screenshot reference images must be maintained, and emulator tests are slower.
* Bad, because the fixtures must be recaptured per host version.

## Pros and Cons of the Options

### Unit tests on the JVM and core C++ tests (GoogleTest, ASan/UBSan, libFuzzer)

* Good, because it covers JVM logic and native core logic with fast tests.
* Good, because ASan/UBSan and libFuzzer on parsers mitigate the lack of memory safety in C++ (see [0008](0008-shared-core-language-and-scope.md)).

### Robolectric and Compose UI tests on the JVM

* Good, because UI coverage is fast.
* Bad, because there is no real decoder or GPU.

### Emulator tests and screenshot tests (Roborazzi)

* Good, because visual regressions of the theme are caught.
* Bad, because the runs are slower.
* Bad, because reference images must be maintained.

### Host contract tests with golden fixtures

* Good, because the fixtures are reusable by future platforms.
* Good, because they are captured from a real LuminalShine.
* Bad, because they must be recaptured per host version.
* Neutral, because real-host end-to-end tests are optional.

## More Information

See [0015](0015-github-actions-for-ci-cd.md), [0017](0017-formatting-and-static-analysis.md), [0034](0034-screenshot-testing-with-roborazzi.md) and [0008](0008-shared-core-language-and-scope.md).
