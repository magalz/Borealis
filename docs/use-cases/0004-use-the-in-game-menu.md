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
   handle down or presses Back. With a keyboard, the user presses
   Ctrl+Alt+Shift+M, and with a mouse the user holds its Back button for one
   second (A8).
2. The system opens the menu over the stream. On a TV, focus starts on Back to
   stream.
3. The user changes an item. The items are listed in
   [the Phase 2 proposal](../ux/phase-2-proposal.md#in-game-menu-items). Start
   and Select never activate a row. The menu has no lateral movement: Left and
   Right do nothing, and Up and Down reach every row in one order (A9). OK or A
   on an option row (Touch, Gamepad mouse, Pointer speed, Overlay) selects its
   next option, and wraps from the last to the first; the row does not move the
   focus.
4. The user closes the menu with Back to stream, Back, B, a tap outside the
   panel, or the same Select + Start hold. The stream continues.

## Alternative flows

### A1: The user opens the keyboard (branches from step 3)

1. The user chooses Keyboard. The flow continues in
   [UC-005](0005-type-with-the-on-screen-keyboard.md).

### A2: The user disconnects (branches from step 3)

1. The user chooses Disconnect, or presses Ctrl+Alt+Shift+Q, which does the
   same. The menu lists the consequence: the app keeps running on the PC.
2. The screen says "Disconnecting from `<app>`…" (the running app, when the
   name is not known), over the background of the screens outside the game
   ([UC-002](0002-start-a-stream.md), A6). It stays up for about one second at
   least, so it can be read, and until the connection has stopped, for five
   seconds at most. Back does nothing meanwhile. The system writes SESSION_END
   with the reason `disconnect`.
3. The activity finishes once, and the system returns to the Apps screen. The
   app is still running on the host. The Library shows "Disconnected. `<app>` is
   still running on the PC." once, in the snackbar that "App quit." uses (when
   the name is not known: "Disconnected. The app is still running on the PC.").

### A3: The user quits the app (branches from step 3)

1. The user chooses Quit and the app name. The row says what Quit does, one
   sentence on each line:
   - for Desktop, the app whose title is "Desktop", ignoring case: "Ends the
     stream." and "The display and controls go back to the PC.";
   - for any other app, also one whose name is not known: "Closes `<app>` on the
     PC." and "Unsaved progress in it is lost."

   The menu asks for confirmation first, in the Quit popup (Sprint 15). It is a
   panel centred in the chrome layer over the menu, not a dialog window. The
   title is only "Quit `<app>`?", and its body is the same two lines. Cancel
   and Quit sit side by side, also on a phone, and Cancel has the focus. Cancel,
   a tap outside the panel, gamepad B and Back close only the popup, and the
   focus goes back to the Quit row. The menu stays drawn behind the popup but is
   out of reach of the focus, the touch and the screen reader. A screen reader
   announces the title with the body when the popup opens. The words come from
   `QuitCopy`, the one text that the actions sheet of the Library, this popup
   and the question about a running app ([UC-003](0003-join-or-replace-a-running-app.md))
   share.
2. The user confirms. The screen shows "Quitting `<app>`…" from the
   confirmation until the host answers the quit, over the background of the
   screens outside the game. Back leaves the screen, and the quit goes on. The
   host may end the stream before it answers, with error -1, because
   LuminalShine closes the control stream when it gets the cancel. That end is the quit's,
   not an error: the system logs it at info with the time since the quit, runs
   no connectivity test and shows no error. Another code while a quit is under
   way is a network drop and goes the way of an error. The stream ends, and the
   system writes SESSION_END with the reason.
3. The system returns to the Apps screen. The app is no longer running on the
   host.
4. The Library shows "App quit." once (Sprint 10), in a snackbar as wide as its
   text, the same notice that a Quit from the Apps sheet shows. The notice waits
   for a Library that is there and is dropped when none is, as after a stream that
   a launcher shortcut started. A Quit that fails shows no notice and says so in
   a message, and the stream goes on. When the host had already ended the stream,
   the Quit that fails turns the screen into the error popup instead
   ([UC-002](0002-start-a-stream.md), E5).

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

### A8: Open the menu from a keyboard or a mouse (Sprint 15; branches from step 1)

1. Ctrl+Alt+Shift+M opens the menu. The system takes the combination before
   the PC sees it, also when the input is not grabbed, and it acts when the
   modifier keys come up, as Ctrl+Alt+Shift+Z (grab) and Ctrl+Alt+Shift+C
   (cursor) do. The M key does not reach the PC.
2. Ctrl+Alt+Shift+Q disconnects, as A2: the screen, the notice and the audit are
   those of the Disconnect row. Before Sprint 15 it finished the activity and
   left no notice.
3. The Back button of the mouse, held for one second, opens the menu and sends
   nothing to the PC. It works only with Mouse back and forward buttons on in
   Settings, Input. A press shorter than one second is the X1 button for the PC,
   sent when the button comes up. A hold that the window losing its focus or the
   activity stopping ends does not open the menu. The Back that Android makes of
   a right click never opens it, and a right click held for seconds does not
   either.
4. The first-run hint names the way. With a keyboard or a mouse and no pad it
   says "Press Ctrl+Alt+Shift+M for the menu", and with Mouse back and forward
   buttons on "Press Ctrl+Alt+Shift+M or hold the mouse Back button for the
   menu". A game controller wins over a keyboard and a mouse, and a virtual
   keyboard, a remote and a touchscreen do not count as them. The hint shows
   for six seconds, once.

### A9: The focus in the menu has no lateral movement (Sprint 15; branches from step 3)

1. Left and Right do nothing on every row and chip. The chrome takes the keys,
   so none reaches the game or the PC: a D-pad, a keyboard, and the left stick
   of a pad on the USB driver alike.
2. Up and Down walk every focusable of the menu in the order that it is drawn,
   one after the other, and stop at the first and the last, with no wrap. The
   chips of Send keys, which wrap over several lines, come in reading order. An
   open Session info has nothing to focus, and Down goes on to the next row.
3. OK, Enter or A on an option row selects the next option. The segments of the
   control are not focus stops, so the keys never go into them. A tap on a
   segment still selects it, and so does a screen reader, which hears "Change
   `<row>`" for the row.
4. Owner decision of 2026-10-28, after the bug of issue #98 did not reproduce.
   Quit is reached with Down and OK alone, also through open Send keys.

### A10: The menu is glass over the video (Sprint 15; branches from step 2)

1. With Menu glass in the stream on Blur, the default, the side panel of the
   menu and the Quit popup are glass over a live copy of the video: a quarter of
   its size, blurred, refreshed 30 times a second while the menu is open and the
   activity is started. Until the first copy arrives the panel is solid. With
   Solid it is a solid panel and no copy is made.
2. The glass is solid, and no copy is made, with Solid, with Transparency Off,
   with High contrast, with Reduce motion, with the battery saver on (followed
   live, while the menu is open), below API 31, and where there is no stream view
   to copy. The tint and the blur are those of the viewer's Transparency: 40 % at 8
   dp at Standard, 85 % at 48 dp at Reduced.
3. Every text on the glass has a halo of 3 dp, so it keeps 4.5:1 over any frame,
   and ink-3 and ink-4 are ink-2 there. The segmented controls sit on a solid
   container of the `surface` colour. The focus outline has a casing in the
   `page` colour, which keeps its 3:1 over the lightest glass. The measures are in
   [ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md),
   decision 10, and in [accessibility](../ux/accessibility.md).
4. The stream reads the option, the transparency and the other look settings
   when it starts, so a change shows in the next stream.

### E1: The stream ends in error (Sprint 15; branches from any step)

1. The stream ends in error while the user streams, with the menu open or not:
   the connection drops, or another device quits the app on the host (error -1).
2. The system asks for one copy of the last frame at once, before the decoder is
   released, and the Connection error popup replaces the menu and the Quit popup
   over that frame, frozen and blurred ([UC-002](0002-start-a-stream.md), E5).
   Try again has the focus; Diagnostics and Close are the other buttons. Back and
   the gamepad B button close the popup, which leaves the stream, and a tap
   outside it does nothing.
3. The end that the user's own Quit caused is not an error (A3), and a graceful
   end of the stream is not either, so neither has this popup.

The approved design defines no other failure flow. The Display for next launch
row is disabled; it needs the Phase 3 host API and cannot be chosen.

## Postconditions

- On close: the stream continues, with the overlay state the user chose.
- On Disconnect: the app keeps running on the host. SESSION_END is in the
  audit trail, and the Library shows the notice.
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
| UC-004-T9 | Given an option row has focus, when the user presses OK or A, then its next option is selected, the last wraps to the first, and the focus stays on the row. | UI | exists (Sprint 5, UI; device round; Sprint 15: `InGameMenuFocusTest`) |
| UC-004-T10 | Given a USB-driver pad is in use, when the menu is open, then its D-pad and left stick move the focus up and down only, A and B act as they do on a system pad, and Start holds toggle the gamepad mouse. | unit | exists (Sprint 5, unit; device round; Sprint 15: `UsbChromeMenuFocusTest`) |
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
| UC-004-T35 | Given the menu is open, when the user chooses Quit, then the Quit popup is a panel in the chrome layer with the question as its heading and the two lines as its body, Cancel focused, a tap outside, B and Back close only the popup and the focus returns to the Quit row, and the menu behind is out of reach. | UI | exists (Sprint 15, UI): `QuitPopupTest`, `StreamDialogBackTest`, `QuitCopyTest` |
| UC-004-T36 | Given the stream runs, when Ctrl+Alt+Shift+M is pressed, then the menu opens, also with the input not grabbed, and the M does not reach the host; when Ctrl+Alt+Shift+Q is pressed, then the session disconnects. | unit | exists (Sprint 15, unit): `GameSpecialKeysTest` |
| UC-004-T37 | Given the Back button of the mouse with Mouse back and forward buttons on, when it is held for one second, then the menu opens and nothing goes to the host; a shorter press is X1 sent when the button comes up; a right click never opens the menu. | unit | exists (Sprint 15, unit): `MouseBackHoldTest` |
| UC-004-T38 | Given a keyboard or a mouse and no pad, when the hint shows, then it names the chord, and the Back button too when the navigation buttons are on; a pad, a virtual keyboard, a remote and a touchscreen do not count. | UI | exists (Sprint 15, UI): `MenuHintTest`, `StreamingLayerHintTest` |
| UC-004-T39 | Given Left, Right, Up, Down, OK and A on every row and chip, when the menu is open, then Left and Right do nothing, Up and Down walk the rows and chips in drawing order and stop at the ends, and Quit is reached with Down and OK alone. | UI, unit | exists (Sprint 15): `InGameMenuFocusTest`, `UsbChromeMenuFocusTest`, `SegmentedControlTest` |
| UC-004-T40 | Given Disconnect, when the user chooses it, then the phase is Disconnecting at once, the screen names the app, it stays up about a second at least and five seconds at most, Back does nothing, the activity finishes once, and the Library shows the Disconnected notice once. | unit, UI | exists (Sprint 15): `StreamSessionTest`, `DisconnectingScreenTest`, `GameStreamActionsLeaveTest`, `BackWhileDisconnectingTest`, `LibraryQuitNoticeTest`, `QuitNoticeTest` |
| UC-004-T41 | Given a Quit that is under way, when the host ends the stream with -1, then no connectivity test runs, the end is logged at info with the time since the quit, and the screen stays on Quitting; any other code is a network drop. | unit | exists (Sprint 15, unit): `GameQuitEndTest`, `StreamSessionLeaveLogTest`, `QuitLogTest` |
| UC-004-T42 | Given Menu glass in the stream on Blur, the transparency, High contrast, Reduce motion, the battery saver and the API level, when the menu opens, then the panel and the Quit popup are glass or solid as A10 says, the copies run only while the menu is open and the activity is started, and the Solid option and API 28 to 30 register no battery saver listener. | UI, unit | exists (Sprint 15): `MenuGlassRulesTest`, `MenuGlassOptionTest`, `MenuGlassCopiesTest`, `MenuGlassDrawTest`, `MenuGlassApi30Test`, `VideoGlassSpecTest`, `VideoGlassFramesTest`, `PixelCopySourceTest`, `StreamAppearanceTest` |
| UC-004-T43 | Given the glass, when text stands on it, then every text colour of the menu and the popup keeps 4.5:1 on the ring of its halo over the lightest glass, the segmented controls keep 4.5:1 on their solid container, and the focus outline keeps 3:1 with its casing. | UI, tool | exists (Sprint 15): `TextHaloTest`, `MenuGlassHaloTest`, `MenuGlassCasingTest`, `MenuGlassFocusTest`, `FocusOverGlassTest`, `VideoGlassHaloTest` in `design/accessibility/test_contrast.py` |
| UC-004-T44 | Given a stream that ends in error after its first frame, when the popup shows, then it stands on the frozen last frame as [UC-002](0002-start-a-stream.md), E5 says. | UI, unit | see UC-002-T20 to UC-002-T23 |

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

UC-004-T35 to T44 are Sprint 15 rows (issues #97, #98, #100, #102 and #104,
and #105 for the error popup). The unit tests of the keys, the hold and the end of a Quit run on the
`Game` rig of `:app`. No test sees the live copy of a real video, and no
screenshot reference stands for the colour of an HDR copy, which has not been
judged: the owner's device round checks the glass on the TV and on the S22.

UC-004-T32 to T34 are Sprint 10 rows. `GameStreamActionsTest` checks that a Quit
that worked leaves the notice and a failed one leaves none (T32). The Sprint 10
device round saw "App quit." once on the Streamer, 3 s after the Library came
back and gone 5 s later
([device validation](../development/device-validation.md#15-results-sprint-10)).
That round also saw "Quitting `<app>`…" stay on screen about 17 s once, in the
night round, and 5.5 to 6.7 s in the morning smoke, until the host answered
`/cancel`. The Quit logged `Connection terminated: -1` as an error. Sprint 15
(issue #97) made the -1 after the user's own Quit the quit's end (UC-004-T41),
and logs where the time goes (`quit cancel sent`, `quit cancel answered ms=`,
`quit check answered ms=`, `connection stop done ms=`, `activity finished`). The
wait and the -1 are on the host's side: LuminalShine issues #217 (`/cancel`
answers 5 to 17 s after a quit) and #218 (`/cancel` drops the control stream
without the termination message, so clients log the user's own Quit as -1), both
filed on 2026-10-29. See the [roadmap](../roadmap.md#sprint-15-the-stream-screens-and-glass).

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
How long `/cancel` takes to answer, and that the host drops the control stream
without a termination message, are LuminalShine issues #217 and #218.
Display for next launch needs the Phase 3 host API
([host contract, section 17.2](../host-contract/luminalshine.md#172-endpoints-relevant-to-a-client)).
Until then the row is disabled.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md),
  [0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md) (the
  glass of the menu, its halo, and the screens outside the game).
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
  the quit fails after the stream has ended, the error popup shows. If the
  stream still runs, it stays on. Since Sprint 15 the menu is a side panel
  with the glass of `ui/glass/` (`VideoGlass`), the Quit popup is a
  `StreamDialog`, and the phases include Disconnecting.
- Existing tests: `InGameMenuTest`, `MenuHandleTest`, `MenuHintTest`,
  `StreamBackTest`, `MenuChordTest`, `MenuChordDetectorTest`,
  `UsbMenuChordTest`, `ChromeGamepadKeysTest`, `StreamChromeHostTest`,
  `GameStreamActionsTest`, `SessionAuditTest`, `StreamSessionTest`,
  `PointerMotionTest`, `GamepadMouseSpeedTest`, `QuitComboTest`,
  `UsbStartHoldTest`, `GuideTapTest`, `SystemPadQuitComboTest`,
  `QuitNoticeTest`, `LibraryQuitNoticeTest`, `BorealisSnackbarHostTest`, and
  from Sprint 15 `QuitPopupTest`, `InGameMenuFocusTest`,
  `UsbChromeMenuFocusTest`, `GameSpecialKeysTest`, `MouseBackHoldTest`,
  `MenuHintTest`, `StreamingLayerHintTest`, `DisconnectingScreenTest`,
  `GameStreamActionsLeaveTest`, `GameQuitEndTest`, `StreamSessionLeaveLogTest`,
  `QuitLogTest`, `MenuGlassRulesTest`, `MenuGlassOptionTest`,
  `MenuGlassCopiesTest`, `TextHaloTest`, `FocusOverGlassTest`.
