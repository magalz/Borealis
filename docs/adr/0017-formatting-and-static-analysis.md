---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0017 Use Spotless, detekt, Android Lint and clang-tidy with a pre-commit hook

## Context and Problem Statement

Which formatting and static analysis tools should enforce code quality for Kotlin and C++? Upstream files must never be reformatted.

## Considered Options

* Spotless (ktfmt kotlinlang style for Kotlin; clang-format with LuminalShine's `.clang-format` for C++) + detekt + Android Lint (with a baseline for upstream warnings) + clang-tidy for the core + pre-commit hook + CI enforcement
* ktlint + Android Lint
* Qodana or SonarCloud
* Android Lint only

## Decision Outcome

Chosen option: "Spotless + detekt + Android Lint + clang-tidy + pre-commit hook + CI enforcement", because it covers Kotlin and C++ with one set of gates. The rule is that upstream files are never reformatted.

### Consequences

* Good, because Kotlin and C++ are both covered by the same gates.
* Good, because the Android Lint baseline keeps upstream warnings out of new work.
* Bad, because it has several tools to set up and maintain.

Code and style rules recorded with this decision:

* Kotlin: ktfmt kotlinlang style (see [0037](0037-kotlin-formatter-ktfmt.md)).
* C++: LuminalShine's `.clang-format` (LLVM based) and clang-tidy.
* Upstream Java is left untouched, and no new Java code is written.
* Markdown: markdownlint.
* `.editorconfig` at the repository root.
* Upstream files are never reformatted. Upstream hooks are marked `// BOREALIS:`.

Definition of Done:

* A test is written first (TDD), see [0016](0016-testing-strategy-and-tdd.md).
* CI is green: format, lint, detekt, tests and build.
* Docs and the ADR are updated when a decision is made.
* Log and audit entries are added when applicable.
* No reformatting of upstream files.

## Pros and Cons of the Options

### Spotless + detekt + Android Lint + clang-tidy

* Good, because Kotlin and C++ are both covered.
* Good, because CI enforces the gates and a pre-commit hook runs them locally.
* Good, because a Lint baseline isolates upstream warnings.
* Bad, because it has several tools to set up and maintain.

### ktlint + Android Lint

* Good, because it is simple.
* Bad, because there is no Kotlin static analysis beyond style, and nothing for C++.

### Qodana or SonarCloud

* Good, because it provides dashboards and quality gates.
* Bad, because it is an external service with a cost, and it still needs a local formatter.

### Android Lint only

* Good, because setup is zero.
* Bad, because diffs are noisy, and there is nothing for C++.

## More Information

See [0037](0037-kotlin-formatter-ktfmt.md), [0038](0038-git-hooks-with-githooks.md) and [0016](0016-testing-strategy-and-tdd.md).

Amended by [0059](0059-convert-the-inherited-java-to-kotlin.md): the inherited Java is converted to Kotlin, and the converted code follows the Kotlin rules above. No new Java code is written.
