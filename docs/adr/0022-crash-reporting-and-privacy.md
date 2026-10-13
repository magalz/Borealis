---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0022 Use opt-in Sentry for crash reporting

## Context and Problem Statement

The hardest crashes will be native: common-c, the PyroWave Vulkan decoder, JNI, and GPU drivers on TVs and handhelds. Some devices have no Google Play Services (Fire TV, many TV boxes), and desktop and iOS may come later. Which crash reporting should Borealis use, and how is user privacy protected?

## Considered Options

* Sentry, opt-in
* Crashlytics, opt-in
* ACRA, opt-in
* Zero telemetry

## Decision Outcome

Chosen option: "Sentry, opt-in", because it gives symbolicated Kotlin and native crashes (symbols uploaded by CI), works without Google Play Services, and provides the same dashboard for future desktop (sentry-native) and iOS (sentry-cocoa).

Safeguards:

* Explicit consent on the first run; the default is off.
* EU data region.
* `sendDefaultPii=false`.
* `beforeSend` applies the BorealisLog redaction (see [0020](0020-logging.md)).
* The diagnostic bundle can be attached, and log events are sent as breadcrumbs.

Complement: Play Console Android vitals (free, Play installs only).

### Consequences

* Good, because native and Kotlin crashes are symbolicated, and it works without Google Play Services.
* Good, because the same dashboard serves Android, desktop and iOS.
* Good, because users must consent, and redaction runs before anything is sent.
* Bad, because it is an external service with a free-plan event quota.

## Pros and Cons of the Options

### Sentry, opt-in

* Good, because it symbolicates Kotlin and native crashes.
* Good, because it works without Google Play Services.
* Good, because one dashboard covers Android, desktop (sentry-native) and iOS (sentry-cocoa).
* Bad, because it is an external service for consenting users only, with a free-plan event quota.

### Crashlytics, opt-in

* Good, because it is free and has strong NDK support.
* Bad, because it is a proprietary Google dependency, and it is uncertain without Play Services (Fire OS).
* Bad, because it covers mobile only.

### ACRA, opt-in

* Good, because it is fully open source.
* Bad, because it is weak on native crashes.
* Bad, because there is no dashboard without self-hosting Acrarium.

### Zero telemetry

* Good, because it gives maximum privacy.
* Bad, because problems are visible only when someone sends a bundle.

## More Information

See [0020](0020-logging.md), [0021](0021-audit.md) and [0015](0015-github-actions-for-ci-cd.md).
