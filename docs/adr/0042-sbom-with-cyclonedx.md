---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0042 Generate SBOMs with CycloneDX

## Context and Problem Statement

Borealis must publish a software bill of materials (SBOM) that covers both Gradle and native (CMake) dependencies (see [0021](0021-audit.md)). Which SBOM generator should be used?

## Considered Options

* CycloneDX Gradle plugin + CMake manifest for native
* SPDX Gradle plugin
* GitHub dependency graph export
* Syft

## Decision Outcome

Chosen option: "CycloneDX Gradle plugin + CMake manifest for native", because CycloneDX is an industry standard. The trade-off is that two generators are used.

### Consequences

* Good, because CycloneDX is an industry standard and covers Gradle plus a CMake manifest for native code.
* Bad, because two generators are used.

## Pros and Cons of the Options

### CycloneDX Gradle plugin + CMake manifest for native

* Good, because it is an industry standard.
* Bad, because two generators are used.

### SPDX Gradle plugin

* Good, because SPDX is an ISO standard.
* Bad, because it has less tooling.

### GitHub dependency graph export

* Good, because setup is zero.
* Bad, because it is incomplete for native code.

### Syft

* Good, because it scans the APK.
* Bad, because it is heuristic.

## More Information

See [0021](0021-audit.md), [0040](0040-dependency-updates-dependabot.md) and [0041](0041-license-checking-with-licensee.md).
