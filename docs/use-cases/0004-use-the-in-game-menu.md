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
   user holds Select + Start. On a phone, the user pulls the top handle down
   or presses Back.
2. The system opens the menu over the stream. On a TV, focus starts on Back to
   stream.
3. The user changes an item. The items are listed in
   [the Phase 2 proposal](../ux/phase-2-proposal.md#in-game-menu-items).
4. The user closes the menu with Back to stream, Back, B, or a tap outside the
   panel. The stream continues.

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
   app ends on the PC.
2. The stream ends. The system writes SESSION_END with the reason.
3. The system returns to the Apps screen. The app is no longer running on the
   host.

## Failure flows

None in the approved design. The Display for next launch row is disabled; it
needs the Phase 3 host API and cannot be chosen.

## Postconditions

- On close: the stream continues, with the overlay state the user chose.
- On Disconnect: the app keeps running on the host. SESSION_END is in the
  audit trail.
- On Quit: the app is no longer running on the host. SESSION_END is in the
  audit trail.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-004-T1 | Given a TV stream, when the user presses Back, then the menu opens and the stream does not end. | UI | planned |
| UC-004-T2 | Given a gamepad stream, when Select + Start is held, then the menu opens; a short press does not open it. | unit | planned |
| UC-004-T3 | Given a phone stream, when the user pulls the top handle down, then the menu opens. | UI | planned |
| UC-004-T4 | Given the menu is open, when the user chooses Disconnect, then the stream ends, the app keeps running on the host and SESSION_END is written. | emulator | planned |
| UC-004-T5 | Given the menu is open, when the user chooses Quit, then the app ends on the host and SESSION_END is written. | emulator | planned |
| UC-004-T6 | Given the Overlay row is set to Off, Compact or Detailed, when the menu closes, then the overlay shows that choice. | UI | planned |
| UC-004-T7 | Given the menu is open, when the user reads the Display for next launch row, then the row is disabled and cannot be chosen. | UI | planned |

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
- Existing logic: none for the menu. Moonlight 12.2 leaves the stream at once on
  Back. Disconnect and Quit end the session with SESSION_END `reason=user`;
  a session the host ends is recorded with `reason=host_ended` (Sprint 2).
