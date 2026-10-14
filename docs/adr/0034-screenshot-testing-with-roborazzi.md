---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0034 Use Roborazzi for screenshot testing

## Context and Problem Statement

Which tool should capture screenshots of Compose screens to catch visual regressions of the theme (see [0016](0016-testing-strategy-and-tdd.md))?

## Considered Options

* Roborazzi (JVM via Robolectric, with reference images)
* Paparazzi
* Compose Preview Screenshot Testing in AGP
* Shot

## Decision Outcome

Chosen option: "Roborazzi", because it runs on the JVM through Robolectric and compares reference images. The trade-off is that the reference images must be maintained.

### Consequences

* Good, because it runs on the JVM through Robolectric.
* Bad, because reference images must be maintained.

## Pros and Cons of the Options

### Roborazzi

* Good, because it runs on the JVM through Robolectric.
* Bad, because reference images must be maintained.

### Paparazzi

* Good, because it is fast.
* Bad, because it does not use Robolectric.

### Compose Preview Screenshot Testing in AGP

* Good, because it is official.
* Bad, because it is still experimental.

### Shot

* Good, because it runs on a real emulator.
* Bad, because it is slow.

## More Information

See [0016](0016-testing-strategy-and-tdd.md) and [0005](0005-ui-toolkit-and-visual-style.md).
