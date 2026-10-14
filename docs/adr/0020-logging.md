---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0020 Use an own logger facade (BorealisLog) and a diagnostic bundle

## Context and Problem Statement

Logs come from Kotlin, upstream LimeLog and native code (common-c and core). Users must be able to send useful diagnostics without leaking secrets. Which logging approach should Borealis use?

## Considered Options

* Own logger facade (`BorealisLog`) + diagnostic bundle
* Timber + file tree
* SLF4J + logback-android
* Logcat only (upstream)

## Decision Outcome

Chosen option: "Own logger facade (`BorealisLog`) + diagnostic bundle", because it provides one sink for Kotlin, upstream LimeLog and native logs (common-c and core via callback), with subsystem tags, structured key=value output, redaction and an export.

Specification:

* Subsystem tags: host, pair, launch, rtsp, video, pyrowave, input, rest.
* Structured key=value output.
* Rotating file: 5 files of 1 MB each.
* Automatic redaction of PIN, tokens, keys and certificates. IP masking is optional.
* "Export diagnostics" zip with logs, device info, decoder and Vulkan capabilities and versions.

### Consequences

* Good, because there is one sink for Kotlin, upstream and native logs.
* Good, because redaction is automatic and users can export a diagnostic bundle.
* Bad, because Borealis must maintain its own code for the facade.

## Pros and Cons of the Options

### Own logger facade (`BorealisLog`) + diagnostic bundle

* Good, because one sink covers Kotlin, upstream LimeLog and native logs.
* Good, because redaction and the diagnostic export are built in.
* Bad, because the facade is custom code to maintain.

### Timber + file tree

* Good, because it is a known library.
* Bad, because native logs and LimeLog need adapters.
* Bad, because redaction and export are still custom.

### SLF4J + logback-android

* Good, because appenders are configurable.
* Bad, because it is heavy for Android, uses XML config and has the same native gap.

### Logcat only (upstream)

* Good, because it needs zero work.
* Bad, because users have no logs to send, and there is no redaction.

## More Information

See [0021](0021-audit.md), [0022](0022-crash-reporting-and-privacy.md) and [0014](0014-semver-per-app-with-prefixed-tags.md).
