---
status: accepted
date: 2026-10-14
decision-makers: João Pedro Magalhães
---

# 0049 Gate pull requests on 90% patch coverage and a 1% project tolerance

## Context and Problem Statement

[0046](0046-test-coverage-with-kover-llvm-cov-and-codecov.md) left the coverage gate to be decided after the first pull request. Measured on main after Phase 0, Kotlin coverage (Kover) is 89.5% of lines and 89.6% of branches. Per module, `:borealis:logging` is 257 of 270 lines, `nativecore` is 33 of 35 lines, and the app package is 0 of 19 lines, because its Android-only entry code cannot be tested on the JVM. C++ coverage of borealis-core is 100% (3 of 3 lines). Which gate should block merges, and on which measure?

## Decision Drivers

* The gate must match the measured baseline, not an arbitrary number
* New code should be tested first (TDD)
* Real regressions must block merges
* Rounding and small changes near the average must not fail pull requests

## Considered Options

* Patch at least 90% and project within a 1% tolerance, both required
* Zero project tolerance ("no drop")
* Project within a 0.5% tolerance
* Patch at least 80% required, project informational
* Patch at least 70% required
* Informational only
* Fixed project floor of 85%

## Decision Outcome

Chosen option: "Patch at least 90% and project within a 1% tolerance, both required", because it reinforces TDD on new code and blocks real regressions. The 1% tolerance lets rounding and patches between 90% and the current average pass. The owner first chose zero project tolerance ("no drop") and then moved to the 1% tolerance for this reason.

Decision details:

* `codecov.yml` sets the patch `target` to `90%`. The project `target` is `auto` with a `threshold` of `1%`. Both have `informational: false` and `if_not_found: success`.
* `precision: 2` and `round: down` are set.
* `parsers.jacoco.partials_as_hits: true` is set, because Kotlin `when` and null-safety produce partial lines.
* The C++ lcov report is line-only. The branch records are stripped by `core/scripts/coverage.sh`.
* The core CI job runs on every pull request, so every pull request uploads coverage and Codecov always posts statuses.
* Codecov upload failures fail CI. The upload step only runs when a token exists.
* Dependabot needs `CODECOV_TOKEN` in the Dependabot secrets store, because Dependabot runs cannot read Actions secrets.
* `codecov/patch` and `codecov/project` become required in the `main` ruleset only after they are observed on a docs-only pull request and the Dependabot secret exists.
* Upstream Java hooks are excluded from coverage (`com.limelight.*`). Each hook must stay at about five lines with no logic. The logic belongs in Kotlin or borealis-core. br-reviewer enforces this.
* Pull requests from forks get no Codecov statuses, because they have no token. The owner handles them.

### Consequences

* Good, because new code is tested first, and real regressions block merges
* Good, because every pull request gets Codecov statuses
* Good, because the gate matches the measured baseline and tolerates rounding
* Bad, because merges depend on the self-hosted Codecov being available
* Bad, because upload failures now fail CI, which changes the consequence stated in [0046](0046-test-coverage-with-kover-llvm-cov-and-codecov.md)
* Bad, because the upstream hooks must stay minimal, so the logic moves into Kotlin or borealis-core
* Bad, because pull requests from forks get no Codecov statuses and need owner handling

This ADR refines the gating part of [0046](0046-test-coverage-with-kover-llvm-cov-and-codecov.md).

## Pros and Cons of the Options

### Patch at least 90% and project within a 1% tolerance, both required

* Good, because it reinforces TDD on new code
* Good, because it blocks real regressions
* Good, because the 1% tolerance lets rounding and small patches pass
* Bad, because merges depend on the self-hosted Codecov

### Zero project tolerance ("no drop")

* Good, because it matches the literal "no drop" rule
* Bad, because the effective gate becomes the project average, and rounding can fail pull requests

### Project within a 0.5% tolerance

* Good, because it is a middle ground between zero and 1% tolerance
* Bad, because it is still sensitive to small refactors

### Patch at least 80% required, project informational

* Good, because it adds less friction
* Bad, because it is weaker than the measured baseline

### Patch at least 70% required

* Good, because it has the least friction
* Bad, because it is far below the real coverage of about 90%

### Informational only

* Good, because it has zero friction
* Bad, because coverage can drop unnoticed

### Fixed project floor of 85%

* Good, because the threshold is predictable
* Bad, because it allows drops above the floor

## More Information

* Related: [0046](0046-test-coverage-with-kover-llvm-cov-and-codecov.md), which this ADR refines.
* Related: [0016](0016-testing-strategy-and-tdd.md) for the testing strategy and TDD.
* Related: [0047](0047-harvest-upstream-then-emancipate.md) for the upstream Java hooks that are excluded from coverage.

Amended by [0059](0059-convert-the-inherited-java-to-kotlin.md): code converted from the inherited Java counts in coverage. Only the Java that stays is excluded, and converted code is held to the same 90% patch gate and 1% project tolerance as the rest.
