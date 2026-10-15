---
status: accepted
date: 2026-10-15
decision-makers: João Pedro Magalhães
---

# 0053 Use Material 3 with a Borealis focus indication on TV

## Context and Problem Statement

[0005](0005-ui-toolkit-and-visual-style.md) chose Jetpack Compose with Material 3 and Compose for TV (`androidx.tv.material3`). Phase 2 builds the first screens for phones, TVs and handhelds with gamepads, and the approved tokens define a custom focus. Which component set should the TV screens use?

## Decision Drivers

* One theme and one component set for all form factors.
* The focus must match the approved tokens: a 3dp accent-soft outline, drawn 2dp outside the element, and focused cover tiles scale to 1.04 ([0052](0052-structure-the-phase-2-ui.md), [design/tokens.json](../../design/tokens.json)).
* D-pad and gamepad navigation must work.
* Avoid deprecated APIs. The tv-foundation lazy lists are deprecated in favour of the standard Compose foundation lists.
* Few dependencies.

## Considered Options

* Material 3 everywhere, with a Borealis focus indication.
* `androidx.tv.material3` on TV screens, as [0005](0005-ui-toolkit-and-visual-style.md) says.
* Hybrid: Material 3 plus tv-material `Surface` and `Card` only where focus matters (cover tiles, host cards).
* tv-material everywhere, phones included.

## Decision Outcome

Chosen option: "Material 3 everywhere, with a Borealis focus indication", because the focus is custom anyway, it keeps one theme and one component set, and it avoids the deprecated tv-foundation lists.

The rules that follow from this decision:

* Borealis draws the focus on focusable components with its own indication, built from the tokens.
* The initial focus uses `FocusRequester`. Focus restoration uses `focusRestorer`.
* Holding OK maps to long-click.
* Gamepad A and B rely on the platform fallback to D-pad center and Back. Gamepad X opens the options.
* The form factor (phone, TV or handheld) is chosen at runtime. It comes from the leanback feature or `UiModeManager`, and from the window width.

### Consequences

* Good, because there is one theme and one set of components.
* Good, because the focus matches the approved tokens exactly.
* Good, because no deprecated TV lists are used.
* Bad, because Borealis owns its TV focus behaviour and must test it. Roborazzi screenshots cover the focused states ([0034](0034-screenshot-testing-with-roborazzi.md)), and Compose UI tests cover the key handling.
* Bad, because Borealis does not get the ready-made focus glow and scale of tv-material.

## Pros and Cons of the Options

### Material 3 everywhere, with a Borealis focus indication

* Good, because one theme and one component set serve all form factors.
* Good, because the focus matches the tokens exactly.
* Good, because it uses no deprecated TV lists and no extra TV dependency.
* Bad, because Borealis owns and tests its TV focus behaviour.
* Bad, because it has no ready-made focus glow and scale.

### `androidx.tv.material3` on TV screens

* Good, because its components are built for D-pad navigation.
* Bad, because it means two themes and two component sets.
* Bad, because its default focus differs from the approved tokens.

### Hybrid: Material 3 plus tv-material only where focus matters

* Good, because it gives ready-made focus where it matters most.
* Bad, because it adds a dependency.
* Bad, because two focus systems must be kept consistent.

### tv-material everywhere, phones included

* Good, because there is one component set.
* Bad, because it is not built for touch.
* Bad, because it lacks phone components such as bottom sheets and text fields.

## More Information

Amends [0005](0005-ui-toolkit-and-visual-style.md): Material 3 with a Borealis focus indication instead of `androidx.tv.material3`.

Related: [0052](0052-structure-the-phase-2-ui.md) (Phase 2 UI), [0034](0034-screenshot-testing-with-roborazzi.md) (screenshot tests) and [0044](0044-design-tokens-pipeline.md) (token pipeline).
