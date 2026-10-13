---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0012 Use Hilt for dependency injection

## Context and Problem Statement

Borealis needs dependency injection for its new Kotlin code, including ViewModels, Compose and tests. Upstream classes are not annotated, so they must reach Borealis services in a different way. Which DI approach should be used?

## Considered Options

* Hilt
* Manual container
* Koin
* Metro

## Decision Outcome

Chosen option: "Hilt", because it is the official recommendation, its graph is validated at compile time, and it integrates with ViewModel, Compose and tests. Upstream classes are not annotated and reach Borealis services through EntryPoints.

### Consequences

* Good, because the graph is validated at compile time, with official ViewModel, Compose and test integration.
* Good, because upstream classes need no annotations; they use EntryPoints.
* Bad, because KSP adds build time.
* Bad, because it requires an annotated `Application`, which is a small manifest hook.

## Pros and Cons of the Options

### Hilt

* Good, because it is the official recommendation.
* Good, because the graph is validated at compile time.
* Good, because it integrates with ViewModel, Compose and tests.
* Bad, because KSP adds build time.
* Bad, because it requires an annotated `Application` (a small manifest hook).

### Manual container

* Good, because it has zero dependencies.
* Bad, because boilerplate grows and scoping is manual.

### Koin

* Good, because it has a simple runtime DSL.
* Bad, because graph errors appear only at runtime.

### Metro

* Good, because it is compile-time DI via a Kotlin compiler plugin, and it is fast.
* Bad, because it is a young project and tied to Kotlin compiler versions.

## More Information

See [0007](0007-official-android-app-architecture.md) and [0036](0036-gradle-kotlin-dsl-and-convention-plugins.md).
