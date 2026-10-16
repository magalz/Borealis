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
2. The system shows the Starting stream screen with the stages: connecting,
   starting the app, preparing the display, waiting for the first frame.
3. The system reads `/serverinfo` from the host.
4. The system sends the launch request. When the request ends, the system
   writes LAUNCH_ATTEMPT with its result.
5. The host builds the display for this device. The screen shows the stage.
   On the Sprint 2 test host this took about 14 s
   ([host contract, section 7.3](../host-contract/luminalshine.md)).
6. The host sets up the stream and sends the first frame. The system waits for
   it. A first frame can take about 18 s; the system must not give up earlier
   ([host contract, section 10](../host-contract/luminalshine.md)).
7. The picture appears. The system wrote SESSION_START when the connection
   started, before the first frame. It holds the codec, resolution, frame rate
   and HDR state. A hint explains how to open the menu on this device the first
   time.
8. The session ends when the user leaves it or the host ends it. The system
   writes SESSION_END with the duration and the reason.

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

### A4: Another app is already running on the host (branches from step 4)

1. The host reports a running app and the system shows the conflict dialog.
2. The flow continues in [UC-003](0003-join-or-replace-a-running-app.md).

### E1: The host is offline (branches from step 3)

1. The Connection error screen says the host is offline and offers Wake PC.
   It shows the raw code for support.
2. The use case ends. The user can try again.

### E2: The host certificate changed (branches from step 3)

1. The Connection error screen says that the host identity changed and offers
   Pair again.
2. The system writes CERTIFICATE_CHANGED. No launch request is sent.
3. The use case ends. Pair again starts [UC-001](0001-pair-with-a-host.md).

### E3: The host stays busy (branches from step 3)

1. After the retries in A1, the Connection error screen says the host is busy
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
   Connection error screen with the raw code and the LuminalShine hint.
2. SESSION_START was written when the connection started. The system writes
   SESSION_END with outcome FAILURE and `reason=error code=<code>`.

## Postconditions

- On success: the picture of the app is shown. SESSION_START is in the audit
  trail.
- On failure or cancel: no stream is running. The error code is shown. No
  secret is stored. An audit entry records a launch attempt when one was made.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-002-T1 | Given the host answers at once, when the user starts an app, then the stages appear in order and the picture is shown after the first frame. | emulator | exists (Sprint 5, device round) |
| UC-002-T2 | Given `/serverinfo` times out twice and then answers, when the stream starts, then a third attempt is made within 25 s and the launch continues. | unit | exists (Sprint 3, unit) |
| UC-002-T3 | Given `/serverinfo` still times out after 3 attempts, when the budget ends, then the screen shows Host busy and no `/launch` is sent. | unit | partly exists (Sprint 5, unit) |
| UC-002-T4 | Given the host asks for a retry, when the launch retries, then LAUNCH_RETRY is written for each retry and the screen shows the count. | unit | exists (Sprint 2, unit) |
| UC-002-T5 | Given a pending launch, when the user chooses Cancel at any stage, then the Apps screen returns and no stream starts. | UI | partly exists (Sprint 5, UI) |
| UC-002-T6 | Given the host certificate changed, when the stream starts, then the error screen offers Pair again and CERTIFICATE_CHANGED is written. | unit | partly exists (Sprint 5, unit and UI) |
| UC-002-T7 | Given the host does not answer, when the start ends, then the screen says the host is offline and offers Wake PC. | UI | exists (Sprint 5, UI) |
| UC-002-T8 | Given a picture is shown, when the session ends, then SESSION_START holds the codec, resolution, frame rate and HDR state, and SESSION_END holds the duration and the reason. | unit | exists (Sprint 3, unit) |

Sprint 5 built the stream screens and their logic. A test marked "exists
(Sprint 5, unit)" runs in the unit-test task, and "exists (Sprint 5, UI)" is a
Compose test that runs on Robolectric. "device round" marks a check that the
Sprint 5 device round of 2026-10-16 ran on a TV or a phone (see
[device validation](../development/device-validation.md), section 10).

Partly covered:

- UC-002-T3: the classifier turns an exhausted `/serverinfo` timeout into
  Host busy, and the screen shows it. No test checks yet that `/launch` is not
  sent after that timeout.
- UC-002-T5: the Cancel button of the Starting screen is tested. The return to
  the Apps screen and the cancel at every stage are not tested.
- UC-002-T6: the error screen offers Pair again, and the classifier reports a
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
  superseded by [0054](../adr/0054-declare-independence-from-moonlight.md)).
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
  Connection error screens exist from Sprint 5, in `:borealis:stream`.
- Existing tests: `ServerInfoRetryTest`, `LaunchRetriesTest`,
  `LaunchRetryRunnerTest`, `SessionAuditTest`, `StreamFailureClassifierTest`,
  `StartingScreenTest`, `ConnectionErrorScreenTest`, `StreamSessionTest`.
