---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0023 Distribute through GitHub Releases, Google Play and Amazon Appstore

## Context and Problem Statement

Through which channels should Borealis be distributed to phones, TVs (including Fire TV) and handhelds?

## Considered Options

* GitHub Releases (signed universal and per-ABI APKs, checksums), with Obtainium for updates
* Google Play (internal and closed testing first)
* Amazon Appstore
* Not now: F-Droid and IzzyOnDroid

## Decision Outcome

Chosen options: GitHub Releases, Google Play and Amazon Appstore.

* **GitHub Releases:** signed universal and per-ABI APKs with checksums, updated through Obtainium. It gives full control and works for TV sideloading (unknown-sources prompt).
* **Google Play:** internal and closed testing first. It gives reach and automatic updates. Google Play has review and policy requirements, a privacy policy is required, and Play App Signing is used (see [0026](0026-signing-keys.md)).
* **Amazon Appstore:** easy installation on Fire TV, which minSdk 28 covers (see [0006](0006-min-sdk-28.md)). It has another review process. Amazon is moving new devices to Vega OS, which does not run Android apps.

Not now: F-Droid and IzzyOnDroid, because F-Droid requires reproducible builds or its own build and no proprietary dependencies (see [0021](0021-audit.md)).

### Consequences

* Good, because TVs can be sideloaded and Fire TV is covered through Amazon.
* Good, because Play gives reach and automatic updates.
* Bad, because Play and Amazon each have their own review processes and policies.
* Bad, because Amazon is moving new devices to Vega OS, which does not run Android apps.

## Pros and Cons of the Options

### GitHub Releases with Obtainium

* Good, because it gives full control.
* Good, because it works for TV sideloading.
* Bad, because users must allow installation from unknown sources.

### Google Play (internal and closed testing first)

* Good, because it gives reach and automatic updates.
* Bad, because it has review and policy requirements, and a privacy policy is required.

### Amazon Appstore

* Good, because it installs easily on Fire TV, which minSdk 28 covers.
* Bad, because it has another review process.
* Bad, because Amazon is moving new devices to Vega OS, which does not run Android apps.

### Not now: F-Droid and IzzyOnDroid

* Good, because it builds trust within the community.
* Bad, because F-Droid requires reproducible builds or its own build, and no proprietary dependencies.

## More Information

See [0026](0026-signing-keys.md), [0045](0045-store-publishing-automation.md) and [0021](0021-audit.md).

Amended by [0050](0050-diagnostics-audit-and-interim-settings.md).
