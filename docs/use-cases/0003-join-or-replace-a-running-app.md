# UC-003: Join or replace a running app

- **Status:** Accepted
- **Platform:** All

## Summary

A user starts an app on a host where another device has already started an
app. The user chooses to join the running session, to quit it and start the
app again, or to cancel. Every choice is explicit, and Cancel has the focus.
The one case with no question is the user's own earlier choice to resume that
app (A3).

## Actors

- Primary actor: the user.
- Other actors and systems: the LuminalShine host, and the other device whose
  session is running.

## Preconditions

- The host is paired with this device ([UC-001](0001-pair-with-a-host.md)).
- The host reports an app that is running. It was started from another device,
  or by this one earlier (Try again after a dropped stream, for example). The
  stream cannot tell a session of this device from one of another: LuminalShine
  gives a paired client no session id, so it asks, unless the user chose to
  resume that app (A3).

## Main flow

1. The user chooses an app on the Apps screen and chooses Start.
2. The system finds a running app on the host and shows the Conflict dialog.
   It finds it in `/serverinfo`, before it sends a launch, or in the host's
   refusal of the launch (400 "An app is already running"). Both paths ask
   (Sprint 15), whether the running app is the app the user chose or another
   one, unless the user chose to resume it (A3). The dialog says whether the
   running app is the same app or another one. It says what quitting costs, in
   a warning below the question. Cancel has the focus. A different app found
   running at start always asks. The system does not quit it on its own, even
   when the Library has not seen that app.
   - The dialog is a panel in the stream chrome (a `StreamDialog`), not a dialog
     window, over the background of the screens outside the game
     ([UC-002](0002-start-a-stream.md), A6). A tap outside the panel is Cancel,
     and Back and the gamepad B button cancel too. Where the host blurs the
     background (API 31 or later, Transparency Standard or Reduced, no High
     contrast, the background not Off), the background is blurred and held still,
     no scrim dims it and the panel is the glass tint
     ([ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md),
     decision 8, as [ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md),
     decision 5, applies it). On API 28 to 30, with Transparency Off, with High
     contrast or with the background Off, the scrim and a solid panel stay. On a
     phone the three buttons stack.
   - When the running app is Desktop, the app whose title is "Desktop", ignoring
     case, the warning says "Quitting ends the stream on the other device." and
     nothing about lost work.
   - For any other app, also one whose name is not known, it says "Quitting ends
     the stream on the other device and closes the app." and "Unsaved progress in
     it is lost." on a second line.
3. The user chooses Join. The running app may be the requested app or another
   one; Join always resumes the running app.
4. The system resumes the running app on the host and writes LAUNCH_CONFLICT
   with the choice "join" and `entry=asked`.
5. The stream starts as in [UC-002](0002-start-a-stream.md), from its step 5.

## Alternative flows

### A1: Quit and relaunch, or quit and start (branches from step 3)

1. The user chooses the third button. It says Quit and relaunch only for the
   same app whose name is known. In every other case it says Quit and start.
2. The system ends the running app on the host and writes LAUNCH_CONFLICT with
   the choice "quit" and `entry=asked`.
3. The system starts the chosen app once. The flow rejoins
   [UC-002](0002-start-a-stream.md) at its step 4.

### A2: The user cancels (branches from step 2)

1. The user chooses Cancel, presses Back or closes the dialog.
2. The system writes LAUNCH_CONFLICT with the choice "cancel" and
   `entry=asked`. It sends no request that changes the host.
3. The Apps screen returns. The use case ends.

### A3: The user chose to resume the app (Sprint 15; branches from step 1)

1. These are the user's choice to resume the app that runs: Resume on the
   actions sheet of the app, a tap on the app that the Library shows running,
   Continue on the Hosts screen, and Try again on the error popup of the stream
   ([UC-002](0002-start-a-stream.md), E5). A Start, a pinned shortcut and a TV
   poster are not, even for the app that runs.
2. When the stream finds that same app running, it joins it without a question.
   The system writes LAUNCH_CONFLICT with the choice "join", outcome success and
   `entry=chosen`. The log says "running app joined on the user's choice" and
   names no app.
3. Another app found running always asks, whatever the user chose.
4. The choice of Try again stays in memory, for the one request that the
   Library handles next, and not in the intent that the stream sends: the
   Library is exported, so any app could otherwise make this client resume with
   no choice of the user ([host contract, section 7.6](../host-contract/luminalshine.md)).
   A Library that stops before it handled that request drops the choice.
5. Remaining risk: nothing tells a session of this device from one of another.
   When another device restarted the app that the Library shows running, Resume
   joins it without a question. LuminalShine gives a paired client no session
   id; the host proposal is the discussion
   [NortheBridge/luminalshine#221](https://github.com/NortheBridge/luminalshine/discussions/221).

### E1: The host still reports the app running (branches from step 3 or 1)

1. The host answers that an app is already running. The system does not show
   the dialog a second time. It shows the Connection error popup with the raw
   code.
2. The system writes LAUNCH_ATTEMPT with the failure outcome.
3. The use case ends.

### E2: A restart finds another app running (branches from the start)

1. The system restarts a start once when the RTSP handshake fails right after a
   same-app resume, whether the user answered Join or chose to resume. The
   restart does not ask again for the app that it joined, and it records the
   choice once. If another app is running at that point, the restart does not
   quit it and does not ask again.
2. The start fails with the message for a collision between devices. The
   other app keeps running.
3. The use case ends.

## Postconditions

- On Join, and on a chosen resume: this device streams the running app. No
  other app was quit.
- On Quit and relaunch or quit and start: the chosen app runs for this device.
  The other device's stream has ended.
- On Cancel: the host is unchanged.
- In every case LAUNCH_CONFLICT holds the detail `choice=<join|quit|cancel>`,
  the id of the running app and `entry=<asked|chosen>`: `asked` when the user
  answered the dialog, `chosen` when the user's earlier choice to resume joined
  without one (always `choice=join`). It does not hold the app name. The audit
  viewer titles the entry "An app was already running on the PC".

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-003-T1 | Given another device runs the app, when the user starts it, then the Conflict dialog shows with Cancel focused. | UI | exists (Sprint 5, UI) |
| UC-003-T2 | Given the user chooses Join, when the host accepts the resume, then the stream starts without a new app launch. | unit | exists (Sprint 2, unit) |
| UC-003-T3 | Given the user chooses Quit and relaunch, when the quit succeeds, then the app is launched once and LAUNCH_CONFLICT records "quit". | unit | exists (Sprint 2, unit) |
| UC-003-T4 | Given the user cancels or presses Back, when the dialog closes, then no request changes the host and LAUNCH_CONFLICT records "cancel". | unit | exists (Sprint 2, unit; device round) |
| UC-003-T5 | Given the user has made one choice, when the host still reports a conflict, then the dialog is not shown again and the launch fails with the error. | unit | exists (Sprint 2, unit) |
| UC-003-T6 | Given the dialog is open, when the user reads it, then it says that quitting ends the stream on the other device. | UI | exists (Sprint 5, UI) |
| UC-003-T7 | Given another app runs on the host when the user starts an app, when the start reads the host, then the Conflict dialog asks and nothing is quit before the user chooses. | unit | exists (Sprint 5, unit; device round) |
| UC-003-T8 | Given the dialog is open for a different app, or for the same app whose name is unknown, when the user reads the third button, then it says Quit and start; for the same app with a known name it says Quit and relaunch. | UI | exists (Sprint 5, UI) |
| UC-003-T9 | Given the running app is Desktop, whatever its case or the spaces around its title, when the dialog opens, then the warning says only that quitting ends the stream on the other device, whether the same app or another one was requested. | UI | exists (Sprint 9, UI) |
| UC-003-T10 | Given the running app is not Desktop (a game, an app whose title only contains "Desktop", or an unknown app), when the dialog opens, then the warning says that quitting closes the app and that its unsaved progress is lost. | UI | exists (Sprint 9, UI) |
| UC-003-T11 | Given the user did not choose to resume and `/serverinfo` shows the app that the start asked for running, when the start reads it, then the dialog asks at once, with no second read of `/serverinfo`, and LAUNCH_CONFLICT records the answer with `entry=asked`. | unit | exists (Sprint 15, unit): `StartSameAppTest`, `LaunchConflictsTest`, `LaunchAuditTest` |
| UC-003-T12 | Given the user did not choose to resume and the host refuses the launch because the app runs, when the start resolves it, then the dialog asks, and Cancel changes nothing on the host. | unit | exists (Sprint 15, unit): `StartSameAppTest`, `LaunchConflictsTest` |
| UC-003-T13 | Given the user chose Resume, Continue or Try again for the app that runs, when the start finds it running on either path, then it joins without asking and LAUNCH_CONFLICT records `choice=join` with `entry=chosen`, once also after a restart. | unit | exists (Sprint 15, unit): `StartSameAppTest`, `LaunchConflictsTest`, `LaunchAuditTest` |
| UC-003-T14 | Given the user chose Resume for an app and another app runs, when the start finds it, then the dialog asks. | unit | exists (Sprint 15, unit): `StartSameAppTest`, `LaunchConflictsTest` |
| UC-003-T15 | Given Resume, a tap on the running app, Continue and Try again, then each says that the user chose to resume; given a Start, Quit and start, a shortcut or a TV poster for the running app, then none does, and the choice of Try again is used once and is dropped when the Library is left. | unit | exists (Sprint 15, unit): `AppsViewModelResumeTest`, `HostsViewModelTest`, `ResumeChoiceTest`, `LibraryActivityRequestTest`, `GameStreamLauncherTest` |
| UC-003-T16 | Given the dialog is open, when the user taps outside the panel, presses Back or presses gamepad B, then the answer is Cancel; a tap in the panel answers nothing. | UI | exists (Sprint 15, UI): `ConflictDialogTest`, `StreamDialogBackTest`, `StreamDialogTest` |
| UC-003-T17 | Given the dialog over the background, when the host blurs it, then no scrim dims the background, it is held still and the panel is the glass tint; on API 28 to 30 the scrim and the solid panel stay. | UI | exists (Sprint 15, UI): `DialogOverBackgroundTest`, `DialogOverBackgroundApi30Test` |

Tests marked "exists (Sprint 2, unit)" cover the launch logic. Tests marked
"exists (Sprint 5, UI)" or "exists (Sprint 9, UI)" are Compose tests of the
Conflict dialog in `:borealis:stream`; `ConflictDialogTest` holds them.
UC-003-T11 to T17 are Sprint 15 rows (issue #101 and the dialog of #103). The
unit tests of the connection run on the `app` rig (`StartSameAppTest`).

Before Sprint 15, a second device for the same app joined the running session
with no question. On LuminalShine, the race that makes a same-app `/launch`
conflict is practically unreachable: while the host builds a display it holds
`/serverinfo`, so the second device found the app there and resumed it (device
round, 2026-10-16). Sprint 15 (issue #101) asks on that path too, so the dialog
no longer depends on the race: a start that finds the app it asked for running
in `/serverinfo` asks unless the user chose to resume (UC-003-T11, A3).

Revisit this after the round on 26.10.0-beta.1 (#203). According to its release
notes, that host runs launch, resume and cancel in order on a worker of their
own, off the HTTPS I/O thread, so `/serverinfo` is no longer held while a display
is built. In the round of 2026-10-21 a second device (the S22) opened the apps of
the host while the Streamer's launch was setting up: its list loaded, its host
polls succeeded, and neither device logged a retry, the 3 s wait before
`/resume` or a 503
([device validation](../development/device-validation.md#15-results-sprint-10)).
The race that made the `/launch` conflict reachable is the one of 26.09.1 and
older hosts, and whether it is still reachable on 26.10.0-beta.1 is not tested.
The question no longer depends on it.

## Host dependency

The flow needs `/serverinfo` (the running app), `/resume`, and the quit path.
`/cancel` ends the app of any client, not only the caller's, and `/resume` has
no owner check ([host contract, section 7.6](../host-contract/luminalshine.md)).
The client therefore quits or resumes only after the user's explicit choice.

Known host limit (LuminalShine issue #201): in `per_client` mode, a second
device that joins a running app shares the first device's virtual display.
When the second device leaves, the first device's session can end (LuminalShine
issue #201). The cause in the issue is not confirmed. Join is therefore a
host limitation, not only a client choice. The issue is still open.

Dated note (2026-10-30): in a two-client run on the evening of 2026-10-29, on a
diagnostics nightly of the host (`0.0.5-nightly`, which changes the logs only),
three Disconnects of the second device did not end the first device's session,
while a Quit (`/cancel`) of the second device ended both, as designed. Issue #201
stays open. The run is in [device validation](../development/device-validation.md#20-results-sprint-15),
section 20.

LuminalShine issue #205 (a second client's `/resume` fails the RTSP tag check
while the first client's launch is still setting up) was closed upstream on
2026-10-29. Borealis still has the 3 s wait before `/resume` and the single
restart of the start, which Sprint 15 did not change.

The host gives a paired client no session id, so the client cannot tell a
session of its own from another device's. That is why the dialog asks and the
only exception is the user's choice (A3). The proposal to add the owner of the
session to `/serverinfo` (a launch id, or "opened by you") is the discussion
[NortheBridge/luminalshine#221](https://github.com/NortheBridge/luminalshine/discussions/221).

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md),
  [0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md)
  (decision 8, the glass of a dialog),
  [0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md)
  (decision 5, the dialog in the stream).
- Host contract sections: [7.3](../host-contract/luminalshine.md#73-per-client-virtual-display),
  [7.5](../host-contract/luminalshine.md#75-launch-and-resume-status-codes)
  (400 "An app is already running on this host"),
  [7.6](../host-contract/luminalshine.md#76-cancel).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing logic: the choice logic exists from Sprint 2 and 3
  (`LaunchConflicts`; `askAbout` and `joinedByChoice` are Sprint 15). The
  Conflict dialog is the Sprint 5 screen in the stream chrome, and it reads the
  same choices. Since Sprint 15 it is a `StreamDialog`, and the lines of its
  warning are `QuitCopy` of the design system, the one text that the actions
  sheet of the Library, the Quit popup of the stream and this dialog share. The
  choice to resume travels as `StreamRequest.resumeChosen`, the `ResumeChoice`
  of the Library for Try again, the launch extra `ResumeChosen` and
  `StreamConfiguration.resumeChosen`.
- Existing tests: `LaunchConflictsTest`, `DifferentAppStartTest`,
  `ConflictDialogTest`, `StreamSessionTest`, and from Sprint 15
  `StartSameAppTest`, `LaunchAuditTest`, `AppsViewModelResumeTest`,
  `ResumeChoiceTest`, `LibraryActivityRequestTest`, `StreamDialogTest`,
  `StreamDialogBackTest`, `DialogOverBackgroundTest`, `QuitCopyTest`.
