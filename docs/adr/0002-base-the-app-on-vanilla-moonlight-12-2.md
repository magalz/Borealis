---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0002 Base the app on vanilla Moonlight 12.2

## Context and Problem Statement

Borealis is a Moonlight-based game-streaming client tailored to the LuminalShine host (NortheBridge/luminalshine, a Windows-only fork of Sunshine via Vibeshine). LuminalShine is not Apollo-derived, so Artemis' Apollo extensions (clipboard, OTP pairing, virtualDisplay, scaleFactor, server commands, permissions) do not work with it. Artemis stopped syncing upstream in 2024-07 and has been inactive since 2025-09. Moonlight 12.2 (2026-09-12) is the first real upstream update in about two years. Which code base should Borealis start from?

## Considered Options

* Vanilla Moonlight 12.2 with upstream history preserved
* Artemis, merged with Moonlight 12.2

## Decision Outcome

Chosen option: "Vanilla Moonlight 12.2 with upstream history preserved", because it is current and maintained. Artemis is used only as a reference for the in-game menu, keyboard and overlay, which are rewritten for LuminalShine. The UX is redesigned, so those features must be re-implemented.

### Consequences

* Good, because the base is current and maintained, and upstream merges stay possible.
* Bad, because the in-game menu, keyboard and overlay features from Artemis must be re-implemented for LuminalShine.

## Pros and Cons of the Options

### Vanilla Moonlight 12.2 with upstream history preserved

* Good, because it is current and maintained.
* Good, because upstream history is preserved, so upstream merges remain possible.
* Bad, because Artemis features (in-game menu, keyboard, overlay) must be rewritten for LuminalShine.

### Artemis, merged with Moonlight 12.2

* Good, because it inherits the Artemis UX.
* Bad, because it brings CRLF normalization of 131 files, a toolchain jump, a rewrite of the latency code for microsecond timestamps, and about 18k unmaintained lines.
* Bad, because its Apollo layer is dead weight against LuminalShine; it would even conclude that the host has no virtual display.

## More Information

See [0001](0001-name-the-product-borealis.md), [0005](0005-ui-toolkit-and-visual-style.md) and [0008](0008-shared-core-language-and-scope.md).

Amended by [0054](0054-declare-independence-from-moonlight.md): upstream merges no longer remain possible. The code under `apps/android/` is Borealis code, and upstream fixes arrive only by cherry-pick.
