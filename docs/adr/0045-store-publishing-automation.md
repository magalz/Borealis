---
status: proposed
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0045 Automate store publishing with Gradle Play Publisher and an Amazon submission script

## Context and Problem Statement

How should releases be uploaded to Google Play and the Amazon Appstore (see [0023](0023-distribution-channels.md))? The decision is to be made in the release phase, so its status is proposed.

## Considered Options

* Gradle Play Publisher for Play + Amazon App Submission API script (Gradle-native, auditable in CI)
* fastlane supply
* r0adkll upload action
* Manual console

## Decision Outcome

Chosen option: "Gradle Play Publisher for Play + Amazon App Submission API script", because it is Gradle-native and auditable in CI. Status: proposed, to be confirmed in the release phase.

### Consequences

* Good, because publishing is Gradle-native and auditable in CI.
* Bad, because the Amazon part needs its own script.

## Pros and Cons of the Options

### Gradle Play Publisher + Amazon submission script

* Good, because it is Gradle-native.
* Good, because it is auditable in CI.
* Bad, because the Amazon part needs its own script.

### fastlane supply

* Good, because upstream already has `fastlane/` metadata.
* Bad, because it needs Ruby.

### r0adkll upload action

* Good, because it is simple.
* Bad, because it covers Play only.

### Manual console

* Good, because setup is zero.
* Bad, because it is not auditable.

## More Information

See [0023](0023-distribution-channels.md) and [0026](0026-signing-keys.md).
