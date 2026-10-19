---
status: accepted
date: 2026-10-15
decision-makers: João Pedro Magalhães
---

# 0052 Structure the Phase 2 UI: hosts home, a Back-opened in-game menu and a dark theme

## Context and Problem Statement

Phase 2 replaces the upstream UI ([0005](0005-ui-toolkit-and-visual-style.md)) with Compose screens for phones, TVs and handhelds. Several structural questions remain open: what the home screen shows, how a user opens the in-game menu when Back leaves the stream today, how sessions are ended, and whether the first release has a light theme. How should the Phase 2 UI be structured?

## Decision Drivers

* One Back key must have one meaning on every device, and it must not end a stream by accident.
* The same screens work with touch, a D-pad and a gamepad.
* Terms must say what happens to the PC: a user must not mix up "leave" and "quit".
* The first release has one palette to test.
* The first run must not add a step that most users skip.

## Considered Options

The options were reviewed per decision. The first option of each decision is the recommendation in the [Phase 2 proposal](../ux/phase-2-proposal.md).

* D1 Light theme: dark only for 1.0; dark plus light that follows the system on phones; dark plus a manual light option; light by default on phones.
* D2 Home: Hosts with a Continue row on top; one library of apps from every host; open the only host directly when there is one; Hosts only, as 12.2.
* D3 Opening the in-game menu: Back opens the menu, with a top handle on touch and Select + Start held on gamepads; a floating button; a five-finger tap on touch; keep Back as leave, with the menu only from the handle.
* D4 Default touch mode: trackpad (relative), as today; direct touch (absolute); ask on the first stream; remember per host.
* D5 Performance overlay: off, one tap away in the menu (Compact or Detailed); Compact on by default; Detailed on by default; only when the stream degrades.
* D6 Leaving a session: "Disconnect" and "`Quit <app>`", each with a one-line consequence; "Leave" and "End app"; "Suspend" and "Close"; keep the 12.2 labels.
* D7 Settings scope: global now, per-host overrides later; per-host profiles now; per-app profiles; presets over global values.
* D8 First run: no onboarding, the empty Hosts screen explains the next step; a three-step welcome; a welcome plus a privacy consent page; a setup wizard that ends in pairing.

## Decision Outcome

Chosen option, per decision, because the owner approved the recommended option of each decision on 2026-10-15:

* D1: dark only for 1.0. The light palette is not built for 1.0.
* D2: Hosts home with a Continue row on top.
* D3: Back opens the in-game menu during a stream. On touch, the top handle opens it. On gamepads, Select + Start held opens it.
* D4: trackpad (relative) as the default touch mode.
* D5: the performance overlay is off by default. Compact or Detailed is one tap away in the menu.
* D6: the session actions are "Disconnect" and "`Quit <app>`", each with a one-line consequence.
* D7: settings are global now. Per-host overrides come later.
* D8: no onboarding. The empty Hosts screen explains the next step.

### Consequences

* Good, because Back has one meaning on every device and does not end a stream by accident.
* Good, because the session actions say what happens to the app on the PC.
* Good, because one palette is tested in 1.0 and matches the host UI.
* Good, because the first run has no steps to skip.
* Bad, because a user of 12.2 must learn that Back now opens the menu.
* Bad, because a light theme for phones waits for a later release, and users who prefer light have no option in 1.0.
* Bad, because global settings cannot differ per host until the overrides arrive.
* Bad, because the empty Hosts screen is the only teaching for a new user.

## Pros and Cons of the Options

### D1 Light theme: dark only for 1.0

* Good, because it is one palette to test, and TVs are dark rooms.
* Good, because there is no flash when a stream ends.
* Bad, because users who prefer light have no option in 1.0.

#### D1 alternatives

* Dark, plus light that follows the system on phones: Good, because it respects the system choice. Bad, because it doubles the visual QA.
* Dark, plus a manual light option: Good, because it gives a choice. Bad, because it keeps two palettes.
* Light by default on phones: Good, because it is common for phone apps. Bad, because it clashes with the host UI and with night-time streaming.

### D2 Home: Hosts with a Continue row on top

* Good, because one tap returns to a running session.
* Good, because it stays close to how people think about PCs.
* Bad, because a user with one PC still sees the host list.

#### D2 alternatives

* One library of apps from every host: Good, because it is fast with many hosts. Bad, because it hides which PC runs an app and its state.
* Open the only host directly when there is one: Good, because it saves a step. Bad, because the behaviour changes when a second host appears.
* Hosts only, as 12.2: Good, because nothing changes. Bad, because there is no quick resume.

### D3 Opening the in-game menu: Back, the top handle, Select + Start

* Good, because it works on every device and Back keeps one meaning.
* Good, because nothing floats over the game.
* Bad, because the handle must be discovered on touch, and users of 12.2 must learn the change.

#### D3 alternatives

* Floating button (Artemis style): Good, because it is always visible. Bad, because it covers part of the picture.
* Five-finger tap on touch (Artemis): Bad, because it is invisible, hard to discover and impossible on a TV.
* Keep Back as leave, with the menu only from the handle: Good, because nothing changes. Bad, because there is no menu on a TV or gamepad-only device.

### D4 Default touch mode: trackpad

* Good, because it is precise on small screens and familiar to 12.2 users.
* Bad, because it is less natural on tablets.

#### D4 alternatives

* Direct touch (absolute): Good, because it is natural on tablets. Bad, because it is imprecise on phones.
* Ask on the first stream: Good, because it is explicit. Bad, because it adds a step before the first stream.
* Remember per host: Good, because it fits mixed use. Bad, because it adds state to explain.

### D5 Performance overlay: off by default

* Good, because the picture is clean, and the diagnostics are one tap away in the menu.
* Bad, because problems are not visible at once.

#### D5 alternatives

* Compact on by default: Good, because problems are visible at once. Bad, because it is permanent clutter.
* Detailed on by default: Good, because it suits testers. Bad, because it is heavy for everyone else.
* Only when the stream degrades: Good, because it is smart. Bad, because its thresholds can misfire.

### D6 Leaving a session: "Disconnect" and "`Quit <app>`"

* Good, because the words are plain and the app name makes the target explicit.
* Bad, because the labels are longer than the 12.2 labels.

#### D6 alternatives

* "Leave" and "End app": Good, because they are short. Bad, because they are less precise.
* "Suspend" and "Close": Good, because they are familiar from consoles. Bad, because suspend is not what the host does.
* Keep the 12.2 labels: Good, because nothing changes. Bad, because users keep mixing them up.

### D7 Settings scope: global now, overrides later

* Good, because it is a small first step, and the screen leaves room for overrides.
* Bad, because a desk PC and a living-room PC cannot differ yet.

#### D7 alternatives

* Per-host profiles now: Good, because they fit two PCs. Bad, because they add screens and a migration now.
* Per-app profiles: Good, because they give fine control. Bad, because they are heavy to manage.
* Presets (Quality, Balanced, Lowest latency) over global values: Good, because they are simple. Bad, because advanced values are still needed.

### D8 First run: no onboarding

* Good, because there is nothing to skip, and the empty state carries the teaching.
* Bad, because a new user gets no introduction beyond the empty screen.

#### D8 alternatives

* A three-step welcome: Good, because it is friendly. Bad, because it is one more thing to maintain.
* Welcome plus a privacy consent page: Good, because consent is shown. Bad, because it is needed only once crash reporting exists ([0022](0022-crash-reporting-and-privacy.md)).
* A setup wizard that ends in pairing: Good, because it is guided. Bad, because it is rigid for users with several hosts.

## More Information

* The proposal, with the screens, states, input rules and captures: [docs/ux/phase-2-proposal.md](../ux/phase-2-proposal.md).
* The clickable prototype: [docs/ux/prototype.html](../ux/prototype.html).
* The approved tokens, dark only: [design/tokens.json](../../design/tokens.json).
* The use cases that follow from this decision: [UC-001](../use-cases/0001-pair-with-a-host.md) to [UC-008](../use-cases/0008-add-a-host-by-address.md).
* Amends [0005](0005-ui-toolkit-and-visual-style.md): violet accent shades and dark only for 1.0.
* Related: [0034](0034-screenshot-testing-with-roborazzi.md) (screenshot tests) and [0044](0044-design-tokens-pipeline.md) (token pipeline).

Amended in Sprint 9 (see the [roadmap](../roadmap.md#sprint-9-ux-polish-and-the-handheld)): by the owner's decision of 2026-10-18, the consequence of Quit shows one sentence per line, so it can take two lines. It says what Quit does: for Desktop the stream ends and the display and controls go back to the PC; for any other app the app closes on the PC and its unsaved progress is lost.
