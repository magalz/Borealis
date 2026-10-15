# UC-003: Join or replace a running app

- **Status:** Accepted
- **Platform:** All

## Summary

A user starts an app on a host where another device has already started an
app. The user chooses to join the running session, to quit it and start the
app again, or to cancel. Every choice is explicit, and Cancel has the focus.

## Actors

- Primary actor: the user.
- Other actors and systems: the LuminalShine host, and the other device whose
  session is running.

## Preconditions

- The host is paired with this device ([UC-001](0001-pair-with-a-host.md)).
- The host reports an app that is running, started from another device.

## Main flow

1. The user chooses an app on the Apps screen and chooses Start.
2. The system finds a running app on the host and shows the Conflict dialog.
   The dialog says whether the running app is the same app or another one. It
   says that quitting ends the stream on the other device. Cancel has the focus.
3. The user chooses Join (same app).
4. The system resumes the running app on the host and writes LAUNCH_CONFLICT
   with the choice "join".
5. The stream starts as in [UC-002](0002-start-a-stream.md), from its step 5.

## Alternative flows

### A1: Quit and relaunch, or quit and start (branches from step 3)

1. The user chooses Quit and relaunch (same app) or Quit and start (other app).
2. The system ends the running app on the host and writes LAUNCH_CONFLICT with
   the choice "quit".
3. The system starts the chosen app once. The flow rejoins
   [UC-002](0002-start-a-stream.md) at its step 4.

### A2: The user cancels (branches from step 2)

1. The user chooses Cancel, presses Back or closes the dialog.
2. The system writes LAUNCH_CONFLICT with the choice "cancel". It sends no
   request that changes the host.
3. The Apps screen returns. The use case ends.

## Failure flows

### E1: The host still reports the app running (branches from step 3 or 1)

1. The host answers that an app is already running. The system does not show
   the dialog a second time. It shows the Connection error screen with the raw
   code.
2. The system writes LAUNCH_ATTEMPT with the failure outcome.
3. The use case ends.

## Postconditions

- On Join: this device streams the running app. No other app was quit.
- On Quit and relaunch or quit and start: the chosen app runs for this device.
  The other device's stream has ended.
- On Cancel: the host is unchanged.
- In every case LAUNCH_CONFLICT holds the choice. The entry holds no app
  name; the Sprint 3 code logs the choice only.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-003-T1 | Given another device runs the app, when the user starts it, then the Conflict dialog shows with Cancel focused. | UI | planned |
| UC-003-T2 | Given the user chooses Join, when the host accepts the resume, then the stream starts without a new app launch. | unit | planned |
| UC-003-T3 | Given the user chooses Quit and relaunch, when the quit succeeds, then the app is launched once and LAUNCH_CONFLICT records "quit". | unit | planned |
| UC-003-T4 | Given the user cancels or presses Back, when the dialog closes, then no request changes the host and LAUNCH_CONFLICT records "cancel". | unit | planned |
| UC-003-T5 | Given the user has made one choice, when the host still reports a conflict, then the dialog is not shown again and the launch fails with the error. | unit | planned |
| UC-003-T6 | Given the dialog is open, when the user reads it, then it says that quitting ends the stream on the other device. | UI | planned |

## Host dependency

The flow needs `/serverinfo` (the running app), `/resume`, and the quit path.
`/cancel` ends the app of any client, not only the caller's, and `/resume` has
no owner check ([host contract, section 7.6](../host-contract/luminalshine.md)).
The client therefore quits or resumes only after the user's explicit choice.

Known host limit (LuminalShine issue #201): in `per_client` mode, a second
device that joins a running app shares the first device's virtual display.
When either device leaves, both sessions can end. Join is therefore a
host limitation, not only a client choice.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md).
- Host contract sections: [7.3](../host-contract/luminalshine.md#73-per-client-virtual-display),
  [7.5](../host-contract/luminalshine.md#75-launch-and-resume-status-codes)
  (400 "An app is already running on this host"),
  [7.6](../host-contract/luminalshine.md#76-cancel).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing logic: the conflict dialog and the choice logic exist from Sprint 2
  and 3. The Phase 2 dialog is planned; it rebuilds the screen, not the logic.
