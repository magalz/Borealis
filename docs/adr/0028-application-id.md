---
status: proposed
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0028 Use io.github.magalz.borealis as applicationId

## Context and Problem Statement

The Android `applicationId` identifies the app on devices and in Google Play. It is immutable after the first Play upload. Which identifier should Borealis use?

## Considered Options

* `io.github.magalz.borealis`
* `com.borealis`
* `com.sivants.borealis`
* `com.candelamoon` (obsolete working name)

## Decision Outcome

Chosen option: "`io.github.magalz.borealis`", because the GitHub namespace guarantees ownership, and the name is unique on Google Play. Its length is the trade-off.

Status: proposed. Confirm before the first Play upload, because the identifier cannot change afterwards.

### Consequences

* Good, because ownership is guaranteed through the GitHub namespace, and the name is unique on Google Play.
* Bad, because the identifier is long.
* Bad, because it is immutable after the first Play upload, so it must be confirmed before that upload.

## Pros and Cons of the Options

### `io.github.magalz.borealis`

* Good, because ownership is guaranteed through the GitHub namespace.
* Good, because it is unique on Google Play.
* Bad, because it is long.

### `com.borealis`

* Good, because it is short.
* Bad, because the domain is not owned and is likely taken.

### `com.sivants.borealis`

* Good, because it is tied to an owner-related domain.
* Bad, because it ties the app to a company.

### `com.candelamoon` (obsolete)

* Bad, because it is an obsolete working name (see [0001](0001-name-the-product-borealis.md)).

## More Information

See [0001](0001-name-the-product-borealis.md) and [0023](0023-distribution-channels.md).
