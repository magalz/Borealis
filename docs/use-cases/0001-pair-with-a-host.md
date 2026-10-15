# UC-001: Pair with a host

- **Status:** Accepted
- **Platform:** All

## Summary

A user pairs Borealis with a LuminalShine host by typing the PIN shown on the
device into the host's Web UI. Pairing lets the device start streams from that
host, and it is the only way a host becomes trusted.

## Actors

- Primary actor: the user, who has the device and the PC in front of them.
- Other actors and systems: the LuminalShine host (GameStream HTTP and the Web
  UI on port 47990).

## Preconditions

- The host is on the network and reachable on its GameStream port (47989 by
  default).
- The host is not already paired with this device.
- The LuminalShine Web UI is open on the PC, in a browser.

## Main flow

1. The user selects a host that shows the Pair action on the Hosts screen.
2. The system shows the Pairing screen with a four-digit PIN, for example
   `4817`, and says where to enter it: the Clients page of the LuminalShine
   Web UI, for example `https://192.168.1.20:47990`.
3. The system writes PAIRING_START to the audit trail and waits for the host
   to accept the PIN. The wait has no client-side timeout; the PIN expires on
   the host after 5 minutes.
4. The user enters the PIN and a device name in the host Web UI. The device
   name must not contain `xbox` or `webos`.
5. The host accepts the PIN. The system stores the pairing and writes
   PAIRING_SUCCESS.
6. The Pairing screen shows "Paired" and opens the apps of the host.

## Alternative flows

### A1: The client restarts the pairing once (branches from step 3)

1. After certain failures ([host contract, section 4.3](../host-contract/luminalshine.md)),
   the client restarts the pairing once with a new PIN. The host does not ask
   for a new PIN.
2. The system writes PAIRING_RESTART and shows a note and the new PIN.
3. Rejoins the main flow at step 3.

### A2: The user cancels (branches from step 2)

1. The user chooses Cancel. The screen closes and nothing is stored.
2. No audit entry is written for the cancel itself. If the host then ends the
   attempt with an error, the system records PAIRING_FAILURE, as today.
3. The use case ends.

### E1: The PIN expires (branches from step 3)

1. After 5 minutes the screen says that the PIN expired and offers Get a new
   PIN and Cancel.
2. The system writes PAIRING_FAILURE.
3. Get a new PIN restarts from step 2; Cancel ends the use case.

### E2: The host rejects the pairing (branches from steps 3 and 4)

1. The host can reject the pairing while the user enters the PIN on the host
   (step 4), or while the client sends the pairing steps (step 3). The system
   shows a message for the host's error, for example that the host cleared an
   unfinished attempt or that another pairing is in progress
   ([host contract, section 4.3](../host-contract/luminalshine.md)).
2. The system writes PAIRING_FAILURE with the host's error code.
3. The use case ends. The user can pair again.

## Postconditions

- On success: the host is listed as paired. Its identity is stored for later
  checks ([host contract, section 5.1](../host-contract/luminalshine.md)).
- On failure: no pairing is stored. The PIN is not written to any log or audit
  entry.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-001-T1 | Given a host that offers Pair, when pairing starts, then the screen shows a four-digit PIN and says where to enter it. | UI | planned |
| UC-001-T2 | Given the host accepts the PIN, when pairing completes, then PAIRING_SUCCESS is written and no log or audit entry contains the PIN. | unit | exists (Sprint 2, unit) |
| UC-001-T3 | Given a host error that allows one restart, when the client restarts the pairing, then the new PIN is shown and PAIRING_RESTART is written. | unit | exists (Sprint 2, unit) |
| UC-001-T4 | Given no PIN accepted within 5 minutes, when the timer ends, then the screen shows Expired with Get a new PIN and PAIRING_FAILURE is written. | UI | planned |
| UC-001-T5 | Given the pairing screen is open, when the user reads the steps, then the screen tells the user to avoid `xbox` and `webos` in the device name. | UI | planned |
| UC-001-T6 | Given the user chooses Cancel, when the screen closes, then no pairing is stored. | UI | planned |

Planned tests cover the Phase 2 screens; tests marked as existing cover logic
that the Phase 2 screens reuse.

## Host dependency

The pairing needs the LuminalShine pairing exchange and the Web UI, which
accepts the PIN and the device name
([host contract, section 4](../host-contract/luminalshine.md)). Without the
Web UI the user cannot enter the PIN, so the flow cannot finish. The client
does not need any capability beyond the standard pairing exchange.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md) (Phase 2 UI),
  [0050](../adr/0050-diagnostics-audit-and-interim-settings.md) (audit trail).
- Host contract sections: [4](../host-contract/luminalshine.md#4-pairing),
  [4.1](../host-contract/luminalshine.md#41-pin-and-name-are-entered-on-the-host),
  [4.2](../host-contract/luminalshine.md#42-pairing-sessions-and-limits),
  [4.3](../host-contract/luminalshine.md#43-pairing-status-codes),
  [5.1](../host-contract/luminalshine.md#51-identity-comes-from-the-certificate),
  [10.2](../host-contract/luminalshine.md#102-strict-first-frame-clients).
- Policies: [privacy and logging](../policies/privacy-and-logging.md#audit-trail).
- Existing tests: `PairingAuditTest`, `PairingErrorsTest`, and the core
  `borealis_pair_classify` vectors (`core/tests/pair_classify_test.cpp`).
- Existing logic: the pairing restart and the host pairing errors exist from
  Sprint 2 and 3. The Phase 2 screen is planned; it rebuilds the screen, not the
  logic.
