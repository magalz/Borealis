---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0033 Use Navigation Compose with type-safe routes

## Context and Problem Statement

Which navigation approach should the Compose UI use for screens and routes?

## Considered Options

* Navigation Compose, with type-safe routes
* Voyager
* Decompose
* Manual state-based navigation

## Decision Outcome

Chosen option: "Navigation Compose, with type-safe routes", because it is the official library. The trade-off is that its API is still evolving.

### Consequences

* Good, because it is the official library, with type-safe routes.
* Bad, because its API is still evolving.

## Pros and Cons of the Options

### Navigation Compose, with type-safe routes

* Good, because it is the official library.
* Bad, because its API is still evolving.

### Voyager

* Good, because it is simple.
* Bad, because it is a third-party library.

### Decompose

* Good, because it is robust.
* Bad, because it is complex.

### Manual state-based navigation

* Good, because it gives full control.
* Bad, because it reinvents the wheel.

## More Information

See [0005](0005-ui-toolkit-and-visual-style.md) and [0007](0007-official-android-app-architecture.md).
