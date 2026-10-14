---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0007 Use the official Android app architecture

## Context and Problem Statement

Upstream Moonlight uses an activity-centric style with very large activities (`Game.java` is about 2,700 lines), which is hard to test and a poor fit for Compose. Which architecture should new Android code follow?

## Considered Options

* Official Android app architecture: UI layer, domain layer with use cases, data layer; MVVM with unidirectional data flow (ViewModel + StateFlow), use cases documented in `docs/use-cases`
* MVI
* Activity-centric (upstream style)
* Full Clean Architecture

## Decision Outcome

Chosen option: "Official Android app architecture (MVVM with unidirectional data flow, ViewModel + StateFlow)", because it is the official guidance, fits Compose, and is testable on the JVM. It results in more files than the upstream style. If it grows into Clean Architecture, that is accepted.

### Consequences

* Good, because it is official guidance, fits Compose and is testable on the JVM.
* Bad, because it produces more files than the upstream style.

## Pros and Cons of the Options

### Official Android app architecture (MVVM, ViewModel + StateFlow)

* Good, because it is official guidance.
* Good, because it fits Compose.
* Good, because it is testable on the JVM.
* Bad, because it produces more files than the upstream style.

### MVI

* Good, because it has a predictable single state.
* Bad, because it needs more ceremony.

### Activity-centric (upstream style)

* Good, because it is consistent with the legacy code.
* Bad, because it produces giant activities (`Game.java` is about 2,700 lines).
* Bad, because it is hard to test and a poor fit for Compose.

### Full Clean Architecture

* Good, because it gives maximal separation of concerns.
* Bad, because it adds boilerplate and pass-through use cases, and is slower to build.

## More Information

See [0005](0005-ui-toolkit-and-visual-style.md), [0012](0012-dependency-injection-with-hilt.md) and [0029](0029-async-with-coroutines-and-flow.md).
