---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0004 Use Kotlin for new Android code

## Context and Problem Statement

Upstream Moonlight Android is written in Java. Which language should new Android code use, and what happens to the upstream Java?

## Considered Options

* Kotlin for new code, upstream Java untouched
* Java only
* Migrate everything to Kotlin
* KMP core

## Decision Outcome

Chosen option: "Kotlin for new code, upstream Java untouched", because Kotlin is the modern Android standard (coroutines, null-safety, Compose) and upstream merges stay trivial.

### Consequences

* Good, because it follows the modern Android standard and supports coroutines, null-safety and Compose.
* Good, because upstream merges stay trivial.
* Bad, because the repository contains two languages.
* Bad, because the Kotlin compiler adds build time.

## Pros and Cons of the Options

### Kotlin for new code, upstream Java untouched

* Good, because it is the modern Android standard (coroutines, null-safety, Compose).
* Good, because upstream merges stay trivial.
* Bad, because two languages live in the repository.
* Bad, because the Kotlin compiler adds build time.

### Java only

* Good, because there is a single language.
* Bad, because it blocks Compose and needs more boilerplate.

### Migrate everything to Kotlin

* Good, because the codebase is homogeneous.
* Bad, because every upstream merge becomes a total conflict; it is effectively a hard fork.

### KMP core

* Good, because the core becomes shareable.
* Bad, because it is premature (see [0003](0003-native-apps-per-platform-with-shared-core.md)).

## More Information

See [0003](0003-native-apps-per-platform-with-shared-core.md) and [0005](0005-ui-toolkit-and-visual-style.md).

Amended by [0054](0054-declare-independence-from-moonlight.md) and [0059](0059-convert-the-inherited-java-to-kotlin.md): the inherited Java is Borealis code and is converted to Kotlin under ADR 0059. The rule for new code stays.
