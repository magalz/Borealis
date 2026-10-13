---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0038 Use .githooks with core.hooksPath for git hooks

## Context and Problem Statement

How should local git hooks (for formatting and checks before commit, see [0017](0017-formatting-and-static-analysis.md)) be installed and shared?

## Considered Options

* `.githooks/` with `core.hooksPath`
* pre-commit framework
* lefthook
* Gradle task installing the hooks

## Decision Outcome

Chosen option: "`.githooks/` with `core.hooksPath`", because it needs no extra tools. The trade-off is that activation is manual and must be documented.

### Consequences

* Good, because no extra tools are needed.
* Bad, because each developer must activate the hooks once, manually, which is documented.

## Pros and Cons of the Options

### `.githooks/` with `core.hooksPath`

* Good, because no extra tools are needed.
* Bad, because activation is manual (one-time, documented).

### pre-commit framework

* Good, because it has a big ecosystem.
* Bad, because it needs Python.

### lefthook

* Good, because it is fast.
* Bad, because it needs an extra binary.

### Gradle task installing the hooks

* Good, because installation is automatic.
* Bad, because it is slow.

## More Information

See [0017](0017-formatting-and-static-analysis.md) and [0037](0037-kotlin-formatter-ktfmt.md).
