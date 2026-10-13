---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0041 Use licensee for license checking

## Context and Problem Statement

GPL-3.0 applies (see [0024](0024-public-repository-from-the-start.md)), so every dependency license must be compatible. How should license compliance be checked, and should the build fail on violations? (The allowlist is part of [0021](0021-audit.md).)

## Considered Options

* licensee by Cash App (fails the build on licenses outside a GPL-3.0-compatible allowlist)
* jk1 dependency-license-report
* OSS Review Toolkit
* Manual review

## Decision Outcome

Chosen option: "licensee by Cash App", because it fails the build on licenses outside a GPL-3.0-compatible allowlist. The trade-off is that it works only with Gradle.

### Consequences

* Good, because the build fails on licenses outside the allowlist.
* Bad, because it works only with Gradle, so native dependencies must be covered another way (see [0042](0042-sbom-with-cyclonedx.md)).

## Pros and Cons of the Options

### licensee by Cash App

* Good, because it fails the build on licenses outside the allowlist.
* Bad, because it works only with Gradle.

### jk1 dependency-license-report

* Good, because the report is rich.
* Bad, because it does not block the build.

### OSS Review Toolkit

* Good, because it is complete.
* Bad, because it is heavy.

### Manual review

* Bad, because it is error-prone.

## More Information

See [0021](0021-audit.md) and [0042](0042-sbom-with-cyclonedx.md).
