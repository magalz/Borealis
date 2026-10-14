---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0037 Use ktfmt with the kotlinlang style

## Context and Problem Statement

Which formatter should enforce the style of new Kotlin code, and can it be verified in CI? (Part of the formatting rules in [0017](0017-formatting-and-static-analysis.md).)

## Considered Options

* ktfmt kotlinlang style
* ktlint (official style)
* ktfmt Google style
* IDE formatter only

## Decision Outcome

Chosen option: "ktfmt kotlinlang style", because it is deterministic and needs zero configuration. The trade-off is little room for customization.

### Consequences

* Good, because output is deterministic and needs zero configuration.
* Good, because CI can verify it.
* Bad, because there is little room for customization.

## Pros and Cons of the Options

### ktfmt kotlinlang style

* Good, because it is deterministic and needs zero configuration.
* Bad, because there is little room for customization.

### ktlint (official style)

* Good, because it is configurable.
* Bad, because it has more rules to tune.

### ktfmt Google style

* Bad, because it uses 2-space indentation, which is unusual on Android.

### IDE formatter only

* Bad, because it cannot be verified in CI.

## More Information

See [0017](0017-formatting-and-static-analysis.md) and [0038](0038-git-hooks-with-githooks.md).
