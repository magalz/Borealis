---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0032 Use Coil 3 for image loading

## Context and Problem Statement

Which image loading library should the app use for images it covers (for example, app artwork)?

## Considered Options

* Coil 3
* Glide
* Picasso
* Upstream `CachedAppAssetLoader`

## Decision Outcome

Chosen option: "Coil 3", because it is Compose-first and uses coroutines. The trade-off is a new dependency.

### Consequences

* Good, because it is Compose-first and uses coroutines.
* Bad, because it adds a new dependency.

## Pros and Cons of the Options

### Coil 3

* Good, because it is Compose-first and uses coroutines.
* Bad, because it is a new dependency.

### Glide

* Good, because it is mature.
* Bad, because it is oriented to Views.

### Picasso

* Good, because it is simple.
* Bad, because it is stagnant.

### Upstream `CachedAppAssetLoader`

* Good, because it has zero dependencies.
* Bad, because it is tied to the old Views.

## More Information

See [0005](0005-ui-toolkit-and-visual-style.md).
