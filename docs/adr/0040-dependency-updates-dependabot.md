---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0040 Use Dependabot for dependency updates

## Context and Problem Statement

How should dependency updates be automated for Gradle, GitHub Actions and submodules?

## Considered Options

* Dependabot
* Renovate
* Gradle Versions plugin
* Manual updates

## Decision Outcome

Chosen option: "Dependabot", because it is native to GitHub and covers Gradle, Actions and submodules. The trade-off is that it is less flexible.

### Consequences

* Good, because it is native to GitHub and covers Gradle, Actions and submodules.
* Bad, because it is less flexible than alternatives.

## Pros and Cons of the Options

### Dependabot

* Good, because it is native to GitHub and covers Gradle, Actions and submodules.
* Bad, because it is less flexible.

### Renovate

* Good, because it is very configurable.
* Bad, because it is an external app.

### Gradle Versions plugin

* Good, because it produces a report.
* Bad, because updates are manual.

### Manual updates

* Good, because the team keeps control.
* Bad, because updates are forgotten.

## More Information

See [0021](0021-audit.md) (supply-chain security) and [0042](0042-sbom-with-cyclonedx.md).
