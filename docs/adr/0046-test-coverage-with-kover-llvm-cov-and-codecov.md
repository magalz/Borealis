---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0046 Measure test coverage with Kover and llvm-cov, reported to a self-hosted Codecov

## Context and Problem Statement

How should Borealis measure test coverage in CI, with which tools for the Kotlin Android app and the C++ core, and should coverage block merges yet? The owner wants coverage reported on every pull request. Codecov is self-hosted by the owner at <https://pipeline.magalz.space>, and CI authenticates with the repository secret `CODECOV_TOKEN`. Coverage is uploaded per area with Codecov flags (`android`, `core`).

## Decision Drivers

* Kotlin code should be measured with a tool that understands Kotlin constructs
* Coverage must aggregate across modules without manual wiring
* The C++ core should be measured with the same compiler family as CI and Android
* Coverage must not block delivery before the real baseline is known

## Considered Options

* Kotlin: Kover, AGP built-in JaCoCo, manual JaCoCo plugin, Kover with AGP JaCoCo for instrumented tests
* C++: llvm-cov, gcovr with GCC, gcov with lcov, no C++ coverage for now
* Gating: informational now with a gate decided later, patch gate from day one, project and patch gate, never gate

## Decision Outcome

Chosen options:

* Kotlin: "Kover", because it is the JetBrains plugin made for Kotlin, aggregates modules easily, emits XML in JaCoCo format that Codecov reads, and understands inline functions, coroutines and data classes better than plain JaCoCo. Simple filters exclude Hilt and KSP generated code. It works with JVM and Robolectric tests with one more Gradle plugin. It does not cover instrumented emulator tests.
* C++: "llvm-cov", because it is clang source-based coverage (`-fprofile-instr-generate` and `-fcoverage-mapping`), uses the same compiler as CI and Android, and gives precise region and branch coverage. A new CMake preset `coverage` exports lcov, and the runner needs `llvm-profdata` and `llvm-cov`.
* Gating: coverage is informational for now. Codecov comments and statuses on pull requests are not required checks. The coverage gate (thresholds for project and/or patch) is decided after the first pull request, once the real coverage of the codebase is known.
* Uploads are per area with the Codecov flags `android` and `core`.

### Consequences

* Good, because coverage is measured for Kotlin and C++ with tools that fit each language, and it is reported on every pull request
* Good, because no gate blocks work before the measured baseline is known
* Good, because the TDD flow of [0016](0016-testing-strategy-and-tdd.md) gets a visible metric
* Bad, because Kover does not cover instrumented emulator tests, so that part of coverage is missing until a second tool is added
* Bad, because coverage can drop unnoticed until the gate is decided
* Bad, because upstream Moonlight code under `apps/android/app` has no Borealis tests and is excluded from coverage through Codecov ignore paths
* Bad, because the self-hosted Codecov must be reachable for uploads, and CI does not fail if an upload fails
* Bad, because the runner needs `llvm-profdata` and `llvm-cov` for the C++ coverage preset

## Pros and Cons of the Options

### Kover

* Good, because it is made for Kotlin by JetBrains
* Good, because it aggregates modules easily
* Good, because its JaCoCo-format XML is read by Codecov
* Good, because it handles inline functions, coroutines and data classes better than plain JaCoCo
* Good, because filters exclude Hilt and KSP generated code
* Bad, because it does not cover instrumented emulator tests
* Bad, because it adds one Gradle plugin

### AGP built-in JaCoCo

* Good, because it needs no extra plugin and is the official Android path
* Good, because it also covers instrumented tests
* Bad, because it produces one report per module and variant, so aggregation is manual
* Bad, because it is less precise with Kotlin constructs

### Manual JaCoCo plugin

* Good, because it gives full control
* Good, because JaCoCo is the industry standard
* Bad, because it needs a lot of Android configuration
* Bad, because it is fragile across AGP updates

### Kover with AGP JaCoCo for instrumented tests

* Good, because it is the most complete option once emulator tests exist
* Bad, because it uses two tools
* Bad, because it is only worth the cost once instrumented tests exist

### llvm-cov

* Good, because it is clang source-based coverage with precise region and branch data
* Good, because it uses the same compiler as CI and Android
* Good, because a CMake preset `coverage` exports lcov
* Bad, because the runner needs `llvm-profdata` and `llvm-cov`

### gcovr with GCC

* Good, because it is mature and exports Cobertura XML
* Good, because it needs no clang
* Bad, because it uses GCC instead of the clang toolchain used elsewhere
* Bad, because it needs Python

### gcov with lcov

* Good, because it is classic and well documented
* Bad, because it needs more steps
* Bad, because it is less precise than llvm-cov with clang

### No C++ coverage for now

* Good, because there is nothing to configure
* Bad, because Phase 1 puts critical logic in the core without a metric

### Informational now, gate later

* Good, because the gate threshold is set from the measured coverage of the first pull request
* Good, because it adds no friction before the baseline is known
* Bad, because coverage can drop unnoticed in the meantime

### Patch gate from day one

* Good, because it reinforces TDD on every change
* Bad, because it may block build-only pull requests
* Bad, because it depends on the availability of the self-hosted Codecov

### Project and patch gate

* Good, because it gives the maximum guarantee
* Bad, because it has the most friction
* Bad, because project coverage includes untested upstream code unless excluded

### Never gate

* Good, because it has zero friction
* Bad, because coverage can drop unnoticed

## More Information

* Related: [0016](0016-testing-strategy-and-tdd.md) for the testing strategy and TDD.
* Related: [0015](0015-github-actions-for-ci-cd.md) for the CI platform where the coverage jobs run.
