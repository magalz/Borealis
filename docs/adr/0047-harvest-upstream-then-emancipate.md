---
status: accepted
date: 2026-10-14
decision-makers: João Pedro Magalhães
---

# 0047 Harvest upstream code, then emancipate at a milestone

## Context and Problem Statement

Moonlight Android went about two years without code changes (v12.1 in 2024-02, v12.2 in 2026-09). The owner wants Borealis to become an independently maintained project. How should Borealis use the upstream code and the upstream sync policy until it is independent, and when should it declare independence? GPL-3.0 lets Borealis reuse code from Moonlight, moonlight-common-c, Artemis (a GPL-3.0 fork of moonlight-android), and LuminalShine and Sunshine (both GPL-3.0), and from PyroWave (MIT), keeping attribution in NOTICE.

## Decision Drivers

* Upstream fixes and features should still be harvested while the new UI is not yet in place
* Borealis must become independently maintained
* Large refactors before the new UI exists add risk
* Reused GPL-3.0 and MIT code must keep its attribution in NOTICE

## Considered Options

* Harvest, then emancipate at a milestone
* Independence now
* Independence per area
* Track upstream indefinitely

## Decision Outcome

Chosen option: "Harvest, then emancipate at a milestone", because it keeps the upstream sync and the minimal `// BOREALIS:` hooks while the Compose UI replaces the upstream screens, and it defers large refactors until the new UI exists.

The milestone is the end of Phase 2, or an earlier owner decision. At that point a new ADR declares independence:

* Rule R-001 and the upstream-sync policy become historical.
* Inherited code becomes Borealis code, free to refactor, reorganize and migrate from Java to Kotlin.
* Moonlight improvements arrive by manual cherry-pick. The `moonlight-android` remote stays configured.

Until then, the hook discipline of R-001 stays in force.

### Consequences

* Good, because upstream code and fixes can still be harvested while the new UI is built
* Good, because large refactors wait until the new UI exists
* Good, because the milestone gives a clear point where Borealis declares its independence
* Bad, because R-001 and the upstream-sync policy stay in force until the milestone
* Bad, because build tooling may move ahead of upstream while harvesting: Dependabot may update the Gradle wrapper, Kotlin and the dependencies declared in upstream files when CI passes, and a later sync resolves conflicts by taking the newer version
* Neutral, because AGP stays ignored by Dependabot until it is declared in one place (it is currently declared in both `apps/android/build.gradle` and `apps/android/gradle/libs.versions.toml`)
* Neutral, because Kotlin may lead the version bundled with AGP when CI passes

This ADR refines [0002](0002-base-the-app-on-vanilla-moonlight-12-2.md) and [0040](0040-dependency-updates-dependabot.md) (Dependabot ignore list), and complements [0009](0009-organized-monorepo-layout.md).

## Pros and Cons of the Options

### Harvest, then emancipate at a milestone

* Good, because the subtree sync and the hooks keep working while the new UI is built
* Good, because a possible Moonlight 12.3 can still be harvested by sync
* Good, because the independence decision is taken at a clear milestone
* Bad, because hook discipline stays in force for a while longer

### Independence now

* Good, because a new ADR ends the sync policy today
* Good, because R-001 is relaxed in Phase 1, and code can be changed freely at once
* Bad, because a possible Moonlight 12.3 becomes manual cherry-pick work
* Bad, because large refactors before the new UI exists raise the risk

### Independence per area

* Good, because the protocol and core (the moonlight-common-c fork and borealis-core) are independent from now on
* Bad, because the Android app keeps its hooks until Phase 2
* Bad, because two rule sets coexist for a while

### Track upstream indefinitely

* Good, because every upstream change arrives by merge
* Bad, because it contradicts the goal of independent maintenance

## More Information

* Related: [0048](0048-fork-moonlight-common-c.md) for the moonlight-common-c fork, which complements this decision.
* Related: [0049](0049-coverage-gate.md) for the coverage gate and the exclusion of upstream Java hooks from coverage.
* Related: [0002](0002-base-the-app-on-vanilla-moonlight-12-2.md), [0009](0009-organized-monorepo-layout.md) and [0040](0040-dependency-updates-dependabot.md).
* Policies: [upstream sync](../policies/upstream-sync.md) and [dependencies](../policies/dependencies.md).
