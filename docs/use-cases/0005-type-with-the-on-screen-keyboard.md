# UC-005: Type with the on-screen keyboard

- **Status:** Accepted
- **Platform:** All

## Summary

During a stream, the user types text and sends shortcuts to the PC from the
device, with modifier keys that latch and one row of Windows shortcuts that are
awkward to type.

## Actors

- Primary actor: the user, who is streaming an app.
- Other actors and systems: the LuminalShine host, which receives the keys.

## Preconditions

- A stream is running ([UC-002](0002-start-a-stream.md)).

## Main flow

1. The user opens Keyboard from the in-game menu
   ([UC-004](0004-use-the-in-game-menu.md)). On touch, a three-finger tap also
   opens the keyboard, as in Moonlight 12.2.
2. The system shows the keyboard. On a phone it is docked at the bottom and the
   stream stays visible above it. On a TV or handheld it is shown on the stream screen.
3. The user taps a modifier (Ctrl, Alt, Win or Shift). The modifier latches and
   shows as on.
4. The user taps a key. The system sends the key to the PC with the latched
   modifiers, and the latched modifiers are cleared.
5. The user taps a shortcut chip (Alt+Tab, Win, Ctrl+Shift+Esc, Alt+F4, Win+D,
   Esc or F11). The system sends that shortcut once.
6. The user chooses Close. The system returns to the full stream.

## Alternative flows

### A1: The user types with the D-pad (branches from step 4, TV)

1. The D-pad moves across the keys. OK types the focused key.
2. Rejoins the main flow at step 5.

### A2: The user types on a gamepad (branches from step 4, handheld)

1. The D-pad moves across the keys. A types the focused key. Y sends
   Backspace. B closes the keyboard.
2. Rejoins the main flow at step 5, or ends at B.

### A3: A physical keyboard is attached

1. The keys go to the PC from the physical keyboard. Android 16 keyboard
   capture stays for physical keyboards, as in 12.2.
2. The on-screen keyboard opens only on request, from the menu or with a
   three-finger tap. Both keyboards can be used at the same time; the
   on-screen modifiers latch only for keys typed on screen.

## Failure flows

None in the approved design. Shortcut chips and keys send what they name; the
host's response to a key is outside this use case.

## Postconditions

- On success: each typed key and shortcut reaches the PC once, with the
  modifiers that were latched for it. The latched modifiers are cleared after
  each typed key.
- No typed key text is written to logs or to the audit trail. This is a
  requirement of this use case; the policy's rule against logging keys applies.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-005-T1 | Given the keyboard is open, when the user taps Ctrl and then A, then Ctrl+A is sent once and Ctrl is cleared. | unit | planned |
| UC-005-T2 | Given the keyboard is open, when the user taps the Alt+Tab chip, then Alt+Tab is sent once. | unit | planned |
| UC-005-T3 | Given the TV keyboard is open, when the D-pad moves to a key and OK is pressed, then that key is typed. | UI | planned |
| UC-005-T4 | Given the handheld keyboard is open, when Y is pressed, then Backspace is sent. | UI | planned |
| UC-005-T5 | Given the phone keyboard is open, when the stream is shown, then the picture stays visible above the keyboard. | UI | planned |
| UC-005-T6 | Given keys are typed, when logs and the audit trail are read, then no typed key text appears in them. | unit | planned |

## Host dependency

Keys go to the host through the input channel of the stream
([host contract, section 13](../host-contract/luminalshine.md)). The client
needs no extra host capability.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md).
- Host contract sections: [13](../host-contract/luminalshine.md#13-input).
- Policies: [privacy and logging](../policies/privacy-and-logging.md).
- Existing logic: Moonlight 12.2 opens the Android soft keyboard. The Phase 2
  keyboard is planned.
