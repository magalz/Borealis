---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0021 Adopt supply-chain security, an in-app audit trail and license compliance

## Context and Problem Statement

Borealis handles paired hosts, a REST token, TLS pinning and third-party native code. Which audit and compliance measures should it adopt, covering the supply chain, the app's own actions and licenses?

## Considered Options

The notes record three chosen measures and one deferred measure. They give no alternatives for the chosen measures and no pros or cons for them. The Pros and Cons section below records only the rationale stated in the notes.

## Decision Outcome

Chosen options, all three adopted:

* **Supply chain:** Dependabot, dependency review, CodeQL (Kotlin and C++), a CycloneDX SBOM (see [0042](0042-sbom-with-cyclonedx.md)), SHA-256 checksums and GitHub build-provenance attestations on every release.
* **In-app audit trail:** stored in Room, local only, viewable, exportable and erasable. It records pairing and unpairing, host certificate changes, TLS and pinning failures, launch attempts and retries, session start and end (codec, resolution, HDR, duration, errors), REST actions, and LuminalShine token use and changes.
* **License compliance:** CI fails on dependencies outside a GPL-3.0-compatible allowlist (see [0041](0041-license-checking-with-licensee.md)). A generated NOTICE and third-party licenses screen lists components such as PyroWave (MIT), WiVRn (GPL-3.0), OpenSSL (Apache-2.0), Opus (BSD) and IBM Plex (OFL).

Deferred: reproducible builds (needed for F-Droid), after version 1.0.

### Consequences

* Good, because dependency risks, provenance and licenses are checked in CI.
* Good, because the user can see and export a local audit trail of security-relevant events.
* Bad, because the audit trail must be kept in sync with each new REST action and launch path.
* Bad, because reproducible builds are not available until after 1.0, which blocks F-Droid for now (see [0023](0023-distribution-channels.md)).

## Pros and Cons of the Options

### Supply-chain security (Dependabot, CodeQL, SBOM, checksums, attestations)

* Good, because it covers dependencies, code scanning, SBOM and release provenance.
* Bad, because it adds CI steps and tooling to maintain.

### In-app audit trail (Room, local only)

* Good, because the record stays on the device and can be viewed, exported and erased.
* Bad, because it adds storage and must be kept in sync with the actions it records.

### License compliance (GPL-3.0-compatible allowlist)

* Good, because CI blocks incompatible licenses.
* Good, because a NOTICE screen lists third-party licenses.
* Bad, because the allowlist must be maintained as dependencies change.

### Reproducible builds (deferred)

* Good, because they are needed for F-Droid.
* Bad, because they are not available before 1.0.

## More Information

See [0015](0015-github-actions-for-ci-cd.md), [0011](0011-datastore-room-and-keystore-for-new-persistence.md), [0020](0020-logging.md), [0040](0040-dependency-updates-dependabot.md) and [0042](0042-sbom-with-cyclonedx.md).
