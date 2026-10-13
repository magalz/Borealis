---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0044 Generate BorealisTokens.kt from design/tokens.json

## Context and Problem Statement

The Mission Control visual style (see [0005](0005-ui-toolkit-and-visual-style.md)) comes from LuminalShine's semantic colors, with purple overrides for Borealis. How should the design tokens reach the Kotlin code without drifting from the source?

## Considered Options

* `design/tokens.json` (source: LuminalShine `semanticColors` with purple overrides) + a Gradle task generating `BorealisTokens.kt`
* Style Dictionary
* Hand-written Kotlin
* Material Theme Builder export

## Decision Outcome

Chosen option: "`design/tokens.json` + a Gradle task generating `BorealisTokens.kt`", because the generated file stays in sync with the source, and the generator is a small task of our own.

### Consequences

* Good, because the generated Kotlin stays in sync with the token source.
* Bad, because a small Gradle task must be written and maintained.

## Pros and Cons of the Options

### `design/tokens.json` + Gradle task

* Good, because the generated file stays in sync with the source.
* Good, because the generator is a small task of our own.
* Bad, because the task must be written and maintained.

### Style Dictionary

* Good, because it is a standard tool.
* Bad, because it needs Node.

### Hand-written Kotlin

* Good, because it is simple.
* Bad, because it drifts from the source.

### Material Theme Builder export

* Good, because it is fast.
* Bad, because it ignores the LuminalShine tokens.

## More Information

See [0005](0005-ui-toolkit-and-visual-style.md) and [0036](0036-gradle-kotlin-dsl-and-convention-plugins.md).
