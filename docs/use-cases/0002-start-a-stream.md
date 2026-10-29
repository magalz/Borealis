# UC-002: Start a stream

- **Status:** Accepted
- **Platform:** All

## Summary

A user starts an app on a paired host and sees its picture on the device. The
user sees each stage of the start and, when the start fails, a message that
says what happened and what to do next.

## Actors

- Primary actor: the user.
- Other actors and systems: the LuminalShine host (`/serverinfo`, `/launch`,
  the per-client virtual display, the RTSP setup and the first frame).

## Preconditions

- The host is paired with this device ([UC-001](0001-pair-with-a-host.md)).
- The host is on the network and the user has chosen an app that is not
  running.

## Main flow

1. The user selects the host on the Hosts screen and the app on the Apps
   screen, and chooses Start.
2. The system shows the Starting stream screen over the background that the
   user chose in Settings: the title, the stages on a glass panel (connecting,
   starting the app, preparing the display, waiting for the first frame) and
   Cancel. The connecting stage stays current until the host answers. How the
   background behaves is in A6.
3. The system reads `/serverinfo` from the host.
4. The system sends the launch request. When the request ends, the system
   writes LAUNCH_ATTEMPT with its result.
5. The host builds the display for this device. The screen shows the stage.
   On the Sprint 2 test host this took about 14 s
   ([host contract, section 7.3](../host-contract/luminalshine.md)).
6. The host sets up the stream and sends the first frame. The system waits for
   it. A first frame can take about 18 s; the system must not give up earlier
   ([host contract, section 10](../host-contract/luminalshine.md)).
7. The picture appears, and the background goes with the phase: it ends when
   the phase turns Streaming. The first frame can come before the connection
   reports that the stream started, so the cut follows the phase and not the
   stage. The system wrote SESSION_START when the connection started, before
   the first frame. It holds the codec, resolution, frame rate and HDR state. A
   hint explains how to open the menu on this device the first time
   ([UC-004](0004-use-the-in-game-menu.md)).
8. The session ends when the user leaves it or the host ends it. The system
   writes SESSION_END with the duration and the reason. If the host ends a
   stream that ran, the flow continues at E5.

## Alternative flows

### A1: The host does not answer `/serverinfo` in time (branches from step 3)

1. The system retries the read after a timeout, up to 3 attempts within about
   25 s, while the start is not cancelled. Only `/serverinfo` is retried.
2. When the host answers, the flow rejoins the main flow at step 4.

### A2: The host asks for a retry (branches from step 4)

1. The system writes LAUNCH_RETRY for each retry and shows the retry count and
   the wait.
2. The flow rejoins the main flow at step 5 when the host accepts the launch.

### A3: The user cancels (branches from step 2)

1. The user chooses Cancel or presses Back. The system ends the pending launch
   and closes the stream screen. The Library is shown again: the app returns to
   the Library screen it was started from (on the TV, the Apps screen).
2. The system does not call `/cancel` for an app it did not start
   ([host contract, section 7.6](../host-contract/luminalshine.md)).
3. The use case ends.

### A4: Another app is already running on the host (branches from step 3 or 4)

1. The host reports a running app, in `/serverinfo` at step 3 or in its refusal
   of the launch at step 4, and the system shows the conflict dialog. It does
   not, for the app that the user chose to resume
   ([UC-003](0003-join-or-replace-a-running-app.md), A3).
2. The flow continues in [UC-003](0003-join-or-replace-a-running-app.md).

### A5: The user goes back from Apps (Sprint 10; branches from step 1)

1. The user opens a host on the Hosts screen, so the Apps screen shows, and then
   presses Back. On a TV or a handheld the Hosts screen comes back with the focus
   on the card that was opened, found by its host, a Continue card included. It
   does not go to the first card.
2. The focus is also restored after Pairing, so the card of the host that was
   paired has it; on a rotation; after a tap on a handheld, where a card cannot
   take the focus but a tap chooses it; and after Cancel on Add host.
3. When the list moved the card out of view, the grid scrolls to it first. When
   the host changed its status or its place meanwhile, the card is still found
   by the host. When the host is gone, the first card has the focus.
4. A phone does not restore the focus: its screen starts with none, in touch mode.

### A6: The background outside the game (Sprint 15; branches from step 2)

1. Every screen outside the game stands on one background: Starting, the
   question about a running app ([UC-003](0003-join-or-replace-a-running-app.md)),
   an error, Quitting and Disconnecting. It is the style that the user chose in
   Settings, Background, at the strength of Background intensity. The
   Appearance row Background before and after the stream says how it behaves:
   Moving (the default), Still (one frame of the style) or Off (the plain page).
   The stream reads the row when it starts, so a change shows in the next
   stream.
2. A clip style (Northern lights, Light mist, Flowing light or Starfield) plays
   only while no decoder of the stream is held. The session holds the decoder
   from the stage Preparing the display, so the clip is released there and its
   still stays. The clip plays again once the connection has stopped. A drawn
   style (Aurora curtains or Soft glow) keeps moving through the start. Under
   the question about a running app and under the error popup, where the host
   blurs the background (API 31 or later, Transparency Standard or Reduced, no
   High contrast, the background not Off), every style is held still, a drawn
   style too ([ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md),
   decision 6).
3. Reduce motion and the battery saver hold every style still and make no
   player, whatever the row says. With Transparency Off or High contrast the
   panel of the steps is a solid surface. Below API 31 it has the Reduced tint
   and no blur.
4. The title, the host name and the note stand on the background, so they have
   its halo. The steps stand on the glass panel, which keeps their text at 4.5:1.
5. The background stays until the phase turns Streaming. It also stands behind
   the screens of a quit or a disconnect that ends the stream.

### E1: The host is offline (branches from step 3)

1. The Connection error popup says the host is offline and offers Wake PC. It
   shows the raw code for support. Every error of this use case is this popup:
   a centred panel with the message, Try again (focused), the action that fits
   the error (Wake PC or Pair again), Diagnostics and Close. A tap outside it
   does nothing. Back and the gamepad B button close it, which is Close, and
   the Library is shown again.
2. The use case ends. The user can try again.

### E2: The host certificate changed (branches from step 3)

1. The Connection error popup says that the host identity changed and offers
   Pair again.
2. The system writes CERTIFICATE_CHANGED. No launch request is sent.
3. The use case ends. Pair again starts [UC-001](0001-pair-with-a-host.md).

### E3: The host stays busy (branches from step 3)

1. After the retries in A1, the Connection error popup says the host is busy
   and offers Try again.
2. No launch request is sent, so no audit entry is written. The retries and the
   final timeout are in the log. LAUNCH_ATTEMPT and LAUNCH_RETRY are written
   only for `/launch` and `/resume`. When the host busy answer comes from one of
   those requests (a 409 or 503 that runs out of retries), the audit trail
   records it.
3. The use case ends.

### E4: The first frame does not arrive (branches from step 6)

1. The stream ends with error -100 or -101 from moonlight-common-c. If no
   frame has been rendered 45 s after the connection started, the Borealis
   first-frame watchdog ends the stream through the same -101 path
   ([ADR 0048](../adr/0048-fork-moonlight-common-c.md), superseded by
   [ADR 0054](../adr/0054-declare-independence-from-moonlight.md)). The system shows the
   Connection error popup with the raw code and the LuminalShine hint. No frame
   was shown, so there is none to copy, and the popup stands on the background
   of A6.
2. SESSION_START was written when the connection started. The system writes
   SESSION_END with outcome FAILURE and `reason=error code=<code>`.

### E5: The host ends a stream that ran (branches from step 8)

1. The stream ends with error -1, for example because another device quit the
   app on the host. The Connection error popup says "`<app>` stopped" and that
   the stream ended. It says the PC may have quit the app or the connection
   dropped. It shows the raw code and offers Try again (focused), Diagnostics
   and Close.
2. The popup stands on the last frame of the stream, frozen and blurred (Sprint
   15). The system asks for one copy of the video when the end is reported, at
   the top of `connectionTerminated` (and of `stageFailed`), before the
   connectivity test and before the stop of the connection, because the decoder
   is released a fraction of a second later. It asks only after the first frame
   was shown, not for the end that the user's own Quit caused and not for a
   graceful end.
3. The frame is blurred where the device can blur and the settings allow it
   (API 31 or later, Transparency not Off, no High contrast). Otherwise it
   shows sharp. The scrim stays over it in both cases. The panel is the glass of
   the menu over the frame with Menu glass in the stream on Blur, and a solid
   surface with Solid, Transparency Off, High contrast or below API 31. Reduce
   motion and the battery saver change nothing: the frame is still. While the
   copy is pending, which took about 0.2 to 0.3 s in the device rounds, the
   popup is on black with a solid panel. When no copy exists or it failed, the
   popup stands on the background of A6.
4. The system writes SESSION_END with outcome FAILURE and `reason=error
   code=-1`. A graceful end (code 0) is `reason=host_ended`. A quit that the
   user chose is `reason=quit` ([UC-004](0004-use-the-in-game-menu.md)).
5. The use case ends. Back or Close ends the activity and lets the copy go.
   Try again starts a new activity.

Known limit: the frame is placed when the copy ends, so a rotation or a resize
after the failure does not place it again.

## Postconditions

- On success: the picture of the app is shown. SESSION_START is in the audit
  trail.
- On failure or cancel: no stream is running. The error code is shown. No
  secret is stored. An audit entry records a launch attempt when one was made.
  No raw Android toast appears over the error popup, and the exception text
  is not shown on it; the exception goes to the log. The copy of the last
  frame is in memory only: nothing saves it, and it is let go when the activity
  is destroyed.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-002-T1 | Given the host answers at once, when the user starts an app, then the stages appear in order and the picture is shown after the first frame. | emulator | exists (Sprint 5, device round) |
| UC-002-T2 | Given `/serverinfo` times out twice and then answers, when the stream starts, then a third attempt is made within 25 s and the launch continues. | unit | exists (Sprint 3, unit) |
| UC-002-T3 | Given `/serverinfo` still times out after 3 attempts, when the budget ends, then the screen shows Host busy and no `/launch` is sent. | unit | partly exists (Sprint 5, unit) |
| UC-002-T4 | Given the host asks for a retry, when the launch retries, then LAUNCH_RETRY is written for each retry and the screen shows the count. | unit | exists (Sprint 2, unit) |
| UC-002-T5 | Given a pending launch, when the user chooses Cancel at any stage, then the Apps screen returns and no stream starts. | UI | partly exists (Sprint 5, UI; device round) |
| UC-002-T6 | Given the host certificate changed, when the stream starts, then the error popup offers Pair again and CERTIFICATE_CHANGED is written. | unit | partly exists (Sprint 5, unit and UI) |
| UC-002-T7 | Given the host does not answer, when the start ends, then the screen says the host is offline and offers Wake PC. | UI | exists (Sprint 5, UI; device round) |
| UC-002-T8 | Given a picture is shown, when the session ends, then SESSION_START holds the codec, resolution, frame rate and HDR state, and SESSION_END holds the duration and the reason. | unit | exists (Sprint 3, unit) |
| UC-002-T9 | Given a stream that ran ends with error -1 from the host, when the error popup shows, then its title is "`<app>` stopped" and SESSION_END holds `reason=error code=-1`. | unit | exists (Sprint 5, unit; device round) |
| UC-002-T10 | Given the host does not answer `/serverinfo` yet, when the retries run, then the connecting stage stays current and the 15 s note does not show. | emulator | exists (Sprint 5, device round) |
| UC-002-T11 | Given a TV or a handheld and the Apps screen of the second host, when the user presses Back, then the card of that host has the focus, and not a Continue card above it or the first card. | UI | exists (Sprint 10, UI): `LibraryAppsBackFocusTest` |
| UC-002-T12 | Given the host went offline, or the list was refreshed or moved the card out of view, when the user presses Back from Apps, then the card still has the focus, found by its host. | UI | exists (Sprint 10, UI): `LibraryAppsBackFocusTest` |
| UC-002-T13 | Given the host is gone when the user presses Back from Apps, then the first card has the focus. | UI | exists (Sprint 10, UI): `LibraryAppsBackFocusTest` |
| UC-002-T14 | Given the user paired a host, opened its apps, and presses Back, or rotated the screen, or tapped a card on a handheld, or cancelled Add host, then the card that had the focus has it again. | UI | exists (Sprint 10, UI): `LibraryAppsBackFocusTest` |
| UC-002-T15 | Given the focus moves between cards, then no card is recomposed. | UI | exists (Sprint 10, UI): `HostsRecompositionTest` |
| UC-002-T16 | Given the Starting screen, when it shows, then it stands on the background of the user, the title and the note have its halo, and the four steps are on a glass panel. | UI | exists (Sprint 15, UI): `OutsideGameBackgroundTest`, `OutsideGameAccessibilityTest`, `StartingScreenTest` |
| UC-002-T17 | Given the phase becomes Streaming, then the background goes, and it does not go when only the connection reports the stream. | UI | exists (Sprint 15, UI): `OutsideGameBackgroundTest` |
| UC-002-T18 | Given a clip style, when the start reaches the stage Preparing the display, then the player is released and the still shows; when the connection has stopped, the clip plays again; a drawn style keeps moving. | UI, unit | exists (Sprint 15): `OutsideGameClipTest`, `StreamSessionDecoderTest` |
| UC-002-T19 | Given Moving, Still or Off, Reduce motion, the battery saver, Transparency Off or High contrast, when a screen outside the game shows, then it moves, stands still or is the plain page as A6 says. | UI | exists (Sprint 15, UI): `OutsideGameBackgroundTest`, `OutsideGameAccessibilityTest`, `StreamSessionVisualsTest`, `StreamSessionFactoryVisualsTest` |
| UC-002-T20 | Given an error, when it shows, then it is a popup with Try again focused, the action of the error, Diagnostics and Close, a tap outside does nothing, and Back and gamepad B close it. | UI | exists (Sprint 15, UI): `ConnectionErrorScreenTest`, `StreamDialogBackTest` |
| UC-002-T21 | Given a stream that ended in error after its first frame, when the popup shows, then it stands on the frozen last frame under its scrim, and with no frame, or with a copy that failed, on the background. | UI | exists (Sprint 15, UI): `FrozenFrameScreenTest`, `FrozenFrameTest`, `StreamChromeHostFrozenFrameTest` |
| UC-002-T22 | Given the stream ends in error, when `connectionTerminated` or `stageFailed` is called, then the copy is asked for before the connectivity test and the stop, and not before the first frame, for the first-frame watchdog, for the end of the user's Quit or for a graceful end. | unit | exists (Sprint 15, unit): `GameFrozenFrameTest` |
| UC-002-T23 | Given the menu glass option, the transparency, High contrast and the API level, when the popup stands on the frame, then the frame and the panel are blurred, sharp or solid as E5 says. | UI | exists (Sprint 15, UI): `FrozenFrameScreenTest`, `FrozenFrameLookTest`, `FrozenFrameApi30Test`, `ConnectionErrorFrameScreenshotTest` |
| UC-002-T24 | Given the error popup over the background, when the host blurs the background, then no scrim dims it and the panel is the glass tint; on API 28 to 30 the scrim and a solid panel stay. | UI | exists (Sprint 15, UI): `DialogOverBackgroundTest`, `DialogOverBackgroundApi30Test` |

Sprint 5 built the stream screens and their logic. A test marked "exists
(Sprint 5, unit)" runs in the unit-test task, and "exists (Sprint 5, UI)" is a
Compose test that runs on Robolectric. "device round" marks a check that the
Sprint 5 device round of 2026-10-16 ran on a TV or a phone (see
[device validation](../development/device-validation.md), section 10).

UC-002-T16 to T24 are Sprint 15 rows. They are Compose tests on Robolectric
unless the level says unit, and `GameFrozenFrameTest` runs on the `Game` rig.
What no test sees is how the frozen frame looks. No round has recorded it,
and the owner's device round checks it. A real `PixelCopy` at the moment of a
drop was read on the TV in the log only: `Frozen frame requested` came before
`Connection terminated`, and `Frozen frame copied` after it
([ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md),
the measurements of 2026-10-29).

Partly covered:

- UC-002-T3: the classifier turns an exhausted `/serverinfo` timeout into
  Host busy, and the screen shows it. No test checks yet that `/launch` is not
  sent after that timeout.
- UC-002-T5: the Cancel button of the Starting screen is tested. The return to
  the Apps screen and the cancel at every stage are not tested.
- UC-002-T6: the error popup offers Pair again, and the classifier reports a
  changed certificate. No test checks the CERTIFICATE_CHANGED audit entry here.

## Host dependency

The flow needs `/serverinfo` (section 6), `/launch` and its response (sections
7.1 and 7.2), the per-client display (section 7.3), the first frame (section
10) and the cancel rules (section 7.6). The host's `virtual_display_mode`
defaults to `per_client`; a shared display is used when the client identity
cannot be resolved ([section 7.3](../host-contract/luminalshine.md)).

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md),
  [0050](../adr/0050-diagnostics-audit-and-interim-settings.md),
  [0048](../adr/0048-fork-moonlight-common-c.md) (first-frame deadline,
  superseded by [0054](../adr/0054-declare-independence-from-moonlight.md)),
  [0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md) (the
  background outside the game, and the error popup over the last frame).
- Host contract sections: [6](../host-contract/luminalshine.md#6-serverinfo-fields),
  [7](../host-contract/luminalshine.md#7-launch-and-resume),
  [7.3](../host-contract/luminalshine.md#73-per-client-virtual-display),
  [7.5](../host-contract/luminalshine.md#75-launch-and-resume-status-codes),
  [7.6](../host-contract/luminalshine.md#76-cancel),
  [10](../host-contract/luminalshine.md#10-time-to-first-frame).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing logic: the launch retries, the `/serverinfo` retry at stream start
  (3 attempts in about 25 s), and the failure classifier (Host busy, Host
  offline, Certificate changed, generic) exist. The Starting, Conflict and
  Connection error screens exist from Sprint 5, in `:borealis:stream`. In
  Sprint 15 the error became a popup (`StreamDialog`, `ConnectionErrorScreen`),
  the screens stand on `OutsideGameBackground`, and the frozen frame is
  `FrozenFrame` with `OverFrozenFrame` (`ui/glass/`).
- Existing tests: `ServerInfoRetryTest`, `LaunchRetriesTest`,
  `LaunchRetryRunnerTest`, `SessionAuditTest`, `StreamFailureClassifierTest`,
  `StartingScreenTest`, `ConnectionErrorScreenTest`, `StreamSessionTest`,
  `LibraryAppsBackFocusTest`, `HostsRecompositionTest`, and from Sprint 15
  `OutsideGameBackgroundTest`, `OutsideGameClipTest`,
  `OutsideGameAccessibilityTest`, `DialogOverBackgroundTest`,
  `StreamSessionDecoderTest`, `StreamSessionVisualsTest`,
  `StreamSessionFactoryVisualsTest`, `StreamDialogTest`, `StreamDialogBackTest`,
  `FrozenFrameTest`, `FrozenFrameScreenTest`, `FrozenFrameLookTest`,
  `StreamChromeHostFrozenFrameTest`, `GameFrozenFrameTest`.
