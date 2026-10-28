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
   mouse, Pointer speed, Overlay) changes its option; the row does not move the
   focus.
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

1. The user chooses Quit and the app name. The row says what Quit does, one
   sentence on each line:
   - for Desktop, the app whose title is "Desktop", ignoring case: "Ends the
     stream." and "The display and controls go back to the PC.";
   - for any other app, also one whose name is not known: "Closes `<app>` on the
     PC." and "Unsaved progress in it is lost."

   The menu asks for confirmation first. The title of the confirmation is only
   "Quit `<app>`?", and its body is the same two lines. Cancel has the focus, and
   Cancel returns to the menu.
2. The user confirms. The screen shows "Quitting `<app>`…" until the host answers
   the quit. The host may end the stream before it answers. The stream ends, and
   the system writes SESSION_END with the reason.
3. The system returns to the Apps screen. The app is no longer running on the
   host.
4. The Library shows "App quit." once (Sprint 10), in a snackbar as wide as its
   text, the same notice that a Quit from the Apps sheet shows. The notice waits
   for a Library that is there and is dropped when none is, as after a stream that
   a launcher shortcut started. A Quit that fails shows no notice, and the stream
   goes on.

### A4: Select or Start on a system pad (Sprint 6; branches from step 1)

1. On a controller that Android sees as a system pad and that has a Select
   button, a Start or Select press alone waits up to 60 ms for the other
   button before it reaches the PC.
2. If the other button arrives within the 60 ms, the 1 s hold opens the menu,
   and neither button reaches the PC.
3. A quick tap of one button still reaches the PC as a short press.

### A5: Gamepad mouse in the menu (Sprint 6; branches from step 3)

1. The Gamepad mouse row shows On when any pad emulates the mouse.
2. When the user chooses a value, the system applies it to every pad.

A pad on the USB driver sends both buttons in one report, so nothing is delayed
for it.

### A6: The user changes the pointer speed (Sprint 7; branches from step 3)

1. The Pointer speed row shows under the Gamepad mouse row, and only while the
   gamepad mouse is on. It shows on a TV and on a handheld. A phone shows the
   Touch row instead, and no Pointer speed row.
2. The user chooses Slow, Normal or Fast. The row sets 0.5, 1 or 2 times the
   [Gamepad mouse speed](../ux/phase-2-proposal.md#settings) in Settings (10 to
   200 %, default 50 %). The speed applies to the pointer and to the scrolling
   of the stick chosen in Analog scrolling.
3. The choice is for this session only. It does not change the Settings value.
   The next stream starts at Normal.
4. While RB is held in mouse mode, the pointer moves at a quarter of the speed.
   RB does not reach the PC.

### A7: Select and Start left held after the quit combo (Sprint 9; branches from step 1)

1. The user presses Start, Select, LB and RB together to quit the stream, and lets
   go of the bumpers first. Start and Select are then the only buttons held.
2. The menu chord arms only from a clean start: once a button other than Start
   and Select has been held, Start and Select do not arm it again until every
   button has been let go. So the menu does not open, and no Guide tap reaches
   the PC, where a Guide tap opens Steam.
3. The stream ends once all four buttons are let go. This holds on a system pad
   and on a pad on the USB driver.
4. On a system pad, letting go of Start after the combo does not turn the gamepad
   mouse on or off while the combo is pending, however long Start was held.
5. After every button has been let go, Start and Select arm the chord again.

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
| UC-004-T13 | Given a system pad with a Select button, when Select is pressed alone, then it reaches the PC within 60 ms unless the other button arrives first. | unit | exists (Sprint 6, unit) |
| UC-004-T14 | Given a system pad, when Select and Start arrive within 60 ms of each other, then the 1 s hold opens the menu and neither button reaches the PC. | unit | exists (Sprint 6, unit) |
| UC-004-T15 | Given a system pad, when one button is tapped quickly on its own, then it reaches the PC as a short press. | unit | exists (Sprint 6, unit) |
| UC-004-T16 | Given a pad on the USB driver, when Select and Start are pressed, then both arrive in one report and neither is delayed. | unit | exists (Sprint 6, unit) |
| UC-004-T17 | Given any pad emulates the mouse, when the menu opens, then the Gamepad mouse row shows On. | UI | exists (Sprint 6, unit) |
| UC-004-T18 | Given the user chooses a Gamepad mouse value in the menu, then the value applies to every pad. | unit | exists (Sprint 6, unit) |
| UC-004-T19 | Given a TV or handheld stream with the gamepad mouse on, when the menu opens, then the Pointer speed row shows under the Gamepad mouse row. | UI | exists (Sprint 7, UI) |
| UC-004-T20 | Given the gamepad mouse is off, or the device is a phone, when the menu opens, then the Pointer speed row is not shown. | UI | exists (Sprint 7, UI) |
| UC-004-T21 | Given the user chooses Slow, Normal or Fast, when the pointer or a stick scrolls, then the move is scaled by that factor for this session only. | unit | exists (Sprint 7, unit) |
| UC-004-T22 | Given a stream starts, then the pointer speed is Normal. | unit | exists (Sprint 7, unit) |
| UC-004-T23 | Given the gamepad mouse is on and RB is held, when a stick moves the pointer, then the move is a quarter of the speed and RB does not reach the PC. | unit | exists (Sprint 7, unit) |
| UC-004-T24 | Given a pad on the USB driver, when Start, Select, LB and RB are pressed together and then all are let go, then the stream ends, and holding Start for the combo does not turn the gamepad mouse on. | unit, device | exists (Sprint 7, unit; device round 2026-10-17) |
| UC-004-T25 | Given a key is held when the menu opens, when the user keeps it held and then lets it go, then the key does not act in the menu; a fresh press acts. | unit, device | exists (Sprint 7, unit; device round 2026-10-17) |
| UC-004-T26 | Given the quit combo is let go bumpers first, when Start and Select stay held, then the chord does not arm: no menu opens and no Guide tap reaches the PC. | unit | exists (Sprint 9, unit) |
| UC-004-T27 | Given every button has been let go after the quit combo, when Select and Start are held again, then the chord arms again. | unit | exists (Sprint 9, unit) |
| UC-004-T28 | Given a system pad and the quit combo held for more than 750 ms, when the bumpers and then Start are let go, then the gamepad mouse is not toggled and no Guide tap is sent. | unit | exists (Sprint 9, unit) |
| UC-004-T29 | Given the running app is Desktop, when the menu shows Quit, then the row says "Ends the stream." and "The display and controls go back to the PC." on two lines, and the confirmation is titled "Quit Desktop?" with those lines as its body. | UI | exists (Sprint 9, UI) |
| UC-004-T30 | Given the running app is a game, when the menu shows Quit, then the row says "Closes `<app>` on the PC." and "Unsaved progress in it is lost." on two lines, and the confirmation is titled "Quit `<app>`?" with those lines as its body. | UI | exists (Sprint 9, UI) |
| UC-004-T31 | Given an app titled "Desktop" with another case or spaces around it, then it is Desktop; given a title that only contains "Desktop", such as "Remote Desktop", then it is a game. | UI | exists (Sprint 9, UI) |
| UC-004-T32 | Given a Quit in the stream that the host answers, when the stream ends, then the Library shows "App quit." once, as wide as its text; given a Quit that fails, then no notice is left. | unit, UI | exists (Sprint 10): `GameStreamActionsTest`, `LibraryQuitNoticeTest` |
| UC-004-T33 | Given a notice that a Quit left, then it is delivered once; it waits while the Library is stopped; it is dropped when no Library is there, and is not shown again after the activity is recreated. | unit, UI | exists (Sprint 10): `QuitNoticeTest`, `LibraryQuitNoticeTest` |
| UC-004-T34 | Given a snackbar, when it shows, then it is as wide as its text, centred and at most 600 dp, a long message wraps, and a screen reader is told of it. | UI | exists (Sprint 10, UI): `BorealisSnackbarHostTest` |

Sprint 5 built the in-game menu and its logic. A test marked "exists (Sprint 5,
unit)" runs in the unit-test task, and "exists (Sprint 5, UI)" is a Compose test
that runs on Robolectric. "device round" marks a check that the Sprint 5 device
round of 2026-10-16 ran on a TV or a phone (see
[device validation](../development/device-validation.md), section 10). The
gamepad chord was run on a TV with both 8BitDo pads, and on a USB-driver pad
(UC-004-T2, T8 to T11).

UC-004-T13 to T15 exist (Sprint 6, unit): `MenuChordDetectorTest` and
`GuideTapTest` check the 60 ms hold of Select and Start on a system pad.
UC-004-T16 to T18 exist (Sprint 6, unit): `UsbMenuChordTest` checks that a lone
Start on the USB driver is in the packet of its own report, and
`MouseRowFollowsStartHoldTest` and `GamepadMouseTest` check the Gamepad mouse
row and that the menu's value reaches every pad.

UC-004-T19 to T24 are Sprint 7 rows. `InGameMenuTest` checks the Pointer speed
row (T19, T20). `StreamSessionTest` checks the default and the session choice
(T21, T22). `GamepadMouseSpeedTest` checks the factors, the RB precision and the
right-stick scrolling (T21, T23). `QuitComboTest`, `UsbMenuChordTest` and
`UsbStartHoldTest` check the quit combo on a USB-driver pad (T24). The device
round of 2026-10-17 ran the quit combo on one pad, the 8BitDo Ultimate 2.

UC-004-T26 to T31 are Sprint 9 rows. `MenuChordDetectorTest`, `GuideTapTest` and
`UsbMenuChordTest` check that the chord does not arm after the quit combo (T26,
T27), and `SystemPadQuitComboTest` checks the Start hold on a system pad (T28).
`InGameMenuTest` checks the Quit row and its confirmation (T29 to T31). The
Sprint 9 device round checks the quit combo on the S22 with a pad
([device validation](../development/device-validation.md#322-sprint-9-checks)).

UC-004-T32 to T34 are Sprint 10 rows. `GameStreamActionsTest` checks that a Quit
that worked leaves the notice and a failed one leaves none (T32). The Sprint 10
device round saw "App quit." once on the Streamer, 3 s after the Library came
back and gone 5 s later
([device validation](../development/device-validation.md#15-results-sprint-10)).
That round also saw "Quitting `<app>`…" stay on screen about 17 s once, in the
night round, and 5.5 to 6.7 s in the morning smoke, until the host answered
`/cancel`. The cause is not known. The Quit logged `Connection terminated: -1`
as an error; [Sprint 15](../roadmap.md#sprint-15-the-stream-screens-and-glass) of the roadmap has both.

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
  `GameStreamActionsTest`, `SessionAuditTest`, `StreamSessionTest`,
  `PointerMotionTest`, `GamepadMouseSpeedTest`, `QuitComboTest`,
  `UsbStartHoldTest`, `GuideTapTest`, `SystemPadQuitComboTest`,
  `QuitNoticeTest`, `LibraryQuitNoticeTest`, `BorealisSnackbarHostTest`.
