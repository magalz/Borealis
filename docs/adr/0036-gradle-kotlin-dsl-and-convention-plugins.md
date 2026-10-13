---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0036 Use Kotlin DSL, version catalog and convention plugins for new Gradle modules

## Context and Problem Statement

How should new Gradle modules be configured? The upstream `app/build.gradle` stays in Groovy. The configuration for Hilt, Compose and quality checks must be consistent across modules.

## Considered Options

* Kotlin DSL + version catalog + convention plugins in `build-logic`
* Kotlin DSL + version catalog without convention plugins
* Groovy (same as upstream)
* Groovy + version catalog

## Decision Outcome

Chosen option: "Kotlin DSL + version catalog + convention plugins in `build-logic`", because a single configuration covers Hilt, Compose and quality checks for all modules. The trade-off is the initial setup. The upstream `app/build.gradle` stays Groovy.

### Consequences

* Good, because one configuration covers Hilt, Compose and quality for all modules.
* Bad, because the initial setup takes more effort.
* Bad, because there are two build DSLs in the repository, Groovy upstream and Kotlin DSL for new modules.

## Pros and Cons of the Options

### Kotlin DSL + version catalog + convention plugins

* Good, because one configuration covers Hilt, Compose and quality.
* Good, because the build is typed.
* Bad, because the initial setup takes more effort.

### Kotlin DSL + version catalog without convention plugins

* Good, because the setup is smaller.
* Bad, because configuration is repeated across modules.

### Groovy (same as upstream)

* Good, because it matches upstream.
* Bad, because it is untyped.

### Groovy + version catalog

* Good, because it is a middle ground.
* Bad, because it is untyped.

## More Information

See [0012](0012-dependency-injection-with-hilt.md), [0017](0017-formatting-and-static-analysis.md) and [0010](0010-prefab-for-android-native-build.md).
