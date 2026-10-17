# UC-004: Use the in-game menu

- **Status:** Accepted
- **Platform:** All

## Summary

During a stream, the user opens one menu to reach the keyboard, the
performance overlay, the session information and the session actions. Back
opens the menu instead of leaving the stream, so the stream is not lost by
accident.

## Actors

- Primary actor: the user, who is streaming an app.
- Other actors and systems: the LuminalShine host.

## Preconditions

- A stream is running ([UC-002](0002-start-a-stream.md)).

## Main flow

1. The user opens the menu. On a TV, the user presses Back. On a gamepad, the
   user holds Select + Start for one second. On a phone, the user pulls the top
   handle down or presses Back.
2. The system opens the menu over the stream. On a TV, focus starts on Back to
   stream.
3. The user changes an item. The items are listed in
   [the Phase 2 proposal](../ux/phase-2-proposal.md#in-game-menu-items). Start
   and Select never activate a row. OK or A on an option row (Touch, Gamepad
   mouse, Overlay) changes its option; the row does not move the focus.
4. The user closes the menu with Back to stream, Back, B, a tap outside the
   panel, or the same Select + Start hold. The stream continues.

## Alternative flows

### A1: The user opens the keyboard (branches from step 3)

1. The user chooses Keyboard. The flow continues in
   [UC-005](0005-type-with-the-on-screen-keyboard.md).

### A2: The user disconnects (branches from step 3)

1. The user chooses Disconnect. The menu lists the consequence: the app keeps
   running on the PC.
2. The stream ends. The system writes SESSION_END with the reason.
3. The system returns to the Apps screen. The app is still running on the host.

### A3: The user quits the app (branches from step 3)

1. The user chooses Quit and the app name. The menu shows the consequence: the
   app ends on the PC. The menu asks for confirmation first; Cancel has the
   focus, and Cancel returns to the menu.
2. The user confirms. The screen shows "Quitting `<app>`…" until the host answers
   the quit. The host may end the stream before it answers. The stream ends, and
   the system writes SESSION_END with the reason.
3. The system returns to the Apps screen. The app is no longer running on the
   host.

### A4: Select or Start on a system pad (planned, Sprint 6; branches from step 1)

1. On a controller that Android sees as a system pad and that has a Select
   button, a Start or Select press alone waits up to 60 ms for the other
   button before it reaches the PC.
2. If the other button arrives within the 60 ms, the 1 s hold opens the menu,
   and neither button reaches the PC.
3. A quick tap of one button still reaches the PC as a short press.

### A5: Gamepad mouse in the menu (planned, Sprint 6; branches from step 3)

1. The Gamepad mouse row shows On when any pad emulates the mouse.
2. When the user chooses a value, the system applies it to every pad.

A pad on the USB driver sends both buttons in one report, so nothing is delayed
for it.

No failure flow is defined in the approved design. The Display for next launch
row is disabled; it needs the Phase 3 host API and cannot be chosen.

## Postconditions

- On close: the stream continues, with the overlay state the user chose.
- On Disconnect: the app keeps running on the host. SESSION_END is in the
  audit trail.
- On Quit: the app is no longer running on the host. SESSION_END is in the
  audit trail.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-004-T1 | Given a TV stream, when the user presses Back, then the menu opens and the stream does not end. | UI | exists (Sprint 5, unit; device round) |
| UC-004-T2 | Given a gamepad stream, when Select + Start is held for one second, then the menu opens; a short press does not open it and reaches the PC as a short press of Back and Start. | unit | exists (Sprint 5, unit; device round) |
| UC-004-T3 | Given a phone stream, when the user pulls the top handle down, then the menu opens. | UI | exists (Sprint 5, UI; device round) |
| UC-004-T4 | Given the menu is open, when the user chooses Disconnect, then the stream ends, the app keeps running on the host and SESSION_END is written. | emulator | exists (Sprint 5, device round) |
| UC-004-T5 | Given the menu is open, when the user chooses Quit, then the app ends on the host and SESSION_END is written. | emulator | exists (Sprint 5, device round) |
| UC-004-T6 | Given the Overlay row is set to Off, Compact or Detailed, when the menu closes, then the overlay shows that choice. | UI | partly exists (Sprint 5, UI; device round) |
| UC-004-T7 | Given the menu is open, when the user reads the Display for next launch row, then the row is disabled and cannot be chosen. | UI | exists (Sprint 5, UI) |
| UC-004-T8 | Given the menu is open, when the user presses Start or Select, then no row is activated. | unit | exists (Sprint 5, unit; device round) |
| UC-004-T9 | Given an option row has focus, when the user presses OK or A, then its option changes and the focus stays on the row. | UI | exists (Sprint 5, UI; device round) |
| UC-004-T10 | Given a USB-driver pad is in use, when the menu is open, then its D-pad, left stick, A and B act as they do on a system pad, and Start holds toggle the gamepad mouse. | unit | exists (Sprint 5, unit; device round) |
| UC-004-T11 | Given Select and Start are held, when the chord is pending, then neither reaches the PC, and a release before one second sends the PC a short press of Back and Start (a Guide tap on a pad with Select and no Guide button). | unit | exists (Sprint 5, unit; device round) |
| UC-004-T12 | Given a system window has just closed and the stream window has its focus back, when OK, Enter, DPAD_CENTER or A is pressed within one second, then the key is ignored. | unit | exists (Sprint 5, unit; device round) |
| UC-004-T13 | Given a system pad with a Select button, when Select is pressed alone, then it reaches the PC within 60 ms unless the other button arrives first. | unit | planned (Sprint 6) |
| UC-004-T14 | Given a system pad, when Select and Start arrive within 60 ms of each other, then the 1 s hold opens the menu and neither button reaches the PC. | unit | planned (Sprint 6) |
| UC-004-T15 | Given a system pad, when one button is tapped quickly on its own, then it reaches the PC as a short press. | unit | planned (Sprint 6) |
| UC-004-T16 | Given a pad on the USB driver, when Select and Start are pressed, then both arrive in one report and neither is delayed. | unit | planned (Sprint 6) |
| UC-004-T17 | Given any pad emulates the mouse, when the menu opens, then the Gamepad mouse row shows On. | UI | planned (Sprint 6) |
| UC-004-T18 | Given the user chooses a Gamepad mouse value in the menu, then the value applies to every pad. | unit | planned (Sprint 6) |

Sprint 5 built the in-game menu and its logic. A test marked "exists (Sprint 5,
unit)" runs in the unit-test task, and "exists (Sprint 5, UI)" is a Compose test
that runs on Robolectric. "device round" marks a check that the Sprint 5 device
round of 2026-10-16 ran on a TV or a phone (see
[device validation](../development/device-validation.md), section 10). The
gamepad chord was run on a TV with both 8BitDo pads, and on a USB-driver pad
(UC-004-T2, T8 to T11).

UC-004-T13 to T18 are planned for Sprint 6. They cover the gamepad follow-ups in
A4 and A5.

Partly covered:

- UC-004-T1: `StreamBackTest` checks that Back opens the menu and that nothing
  else is called (no stream end). It runs on the session logic, not on a TV
  screen.
- UC-004-T4 and UC-004-T5: the unit tests check the SESSION_END reason and that
  the activity finishes. The device round checked on the TV that the app stays
  on the host (Disconnect) and ends on it (Quit).
- UC-004-T6: `InGameMenuTest` checks that the Overlay row sets the mode the user
  picks. No automated test checks the overlay on the screen after the menu
  closes. The device round checked it on the TV.

## Host dependency

Disconnect and Quit use the host's stop and quit paths. Quit ends the app for
every client ([host contract, section 7.6](../host-contract/luminalshine.md)).
Display for next launch needs the Phase 3 host API
([host contract, section 17.2](../host-contract/luminalshine.md#172-endpoints-relevant-to-a-client)).
Until then the row is disabled.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md).
- Host contract sections: [7.6](../host-contract/luminalshine.md#76-cancel),
  [17.2](../host-contract/luminalshine.md#172-endpoints-relevant-to-a-client).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing logic: the menu, the pull handle and the first-run hint are in
  `:borealis:stream` (Sprint 5). Moonlight 12.2 left the stream at once on
  Back; Borealis opens the menu instead. Disconnect and Quit end the session
  with SESSION_END `reason=disconnect` and `reason=quit`. A stop by leaving the
  stream screen is `reason=user`, and a session the host ends with a graceful
  end is recorded with `reason=host_ended`. While a quit is pending, any end of
  the stream is recorded as `reason=quit`, and the screen shows "Quitting". If
  the quit fails after the stream has ended, the failure screen shows. If the
  stream still runs, it stays on.
- Existing tests: `InGameMenuTest`, `MenuHandleTest`, `MenuHintTest`,
  `StreamBackTest`, `MenuChordTest`, `MenuChordDetectorTest`,
  `UsbMenuChordTest`, `ChromeGamepadKeysTest`, `StreamChromeHostTest`,
  `GameStreamActionsTest`, `SessionAuditTest`.
