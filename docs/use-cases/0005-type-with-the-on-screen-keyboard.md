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
   On a phone the shortcut row starts with Esc.
3. The user taps a modifier (Ctrl, Alt, Win or Shift). The modifier latches and
   shows as on.
4. The user taps a key. Letter keys show in lowercase unless Shift is latched;
   the keyboard has no Caps key. The system sends the key to the PC with the
   latched modifiers, and the latched modifiers are cleared.
5. The user taps a shortcut chip (Alt+Tab, Win+Tab, Win, Ctrl+Shift+Esc, Alt+F4,
   Win+D, Esc or F11). The system sends that shortcut once.
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

No failure flow is defined in the approved design. Shortcut chips and keys send
what they name; the host's response to a key is outside this use case.

## Postconditions

- On success: each typed key and shortcut reaches the PC once, with the
  modifiers that were latched for it. The latched modifiers are cleared after
  each typed key.
- No typed key text is written to logs or to the audit trail. This is a
  requirement of this use case; the policy's rule against logging keys applies.

## Acceptance tests

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-005-T1 | Given the keyboard is open, when the user taps Ctrl and then A, then Ctrl+A is sent once and Ctrl is cleared. | unit | exists (Sprint 5, UI) |
| UC-005-T2 | Given the keyboard is open, when the user taps the Alt+Tab chip, then Alt+Tab is sent once. | unit | exists (Sprint 5, UI) |
| UC-005-T3 | Given the TV keyboard is open, when the D-pad moves to a key and OK is pressed, then that key is typed. | UI | exists (Sprint 5, UI; device round) |
| UC-005-T4 | Given the handheld keyboard is open, when Y is pressed, then Backspace is sent. | UI | exists (Sprint 5, UI) |
| UC-005-T5 | Given the phone keyboard is open, when the stream is shown, then the picture stays visible above the keyboard. | UI | partly exists (Sprint 5, UI; device round) |
| UC-005-T6 | Given keys are typed, when logs and the audit trail are read, then no typed key text appears in them. | unit | partly exists (Sprint 5, unit) |
| UC-005-T7 | Given the docked keyboard on a phone, when the shortcut row shows, then Esc is its first chip. | unit | exists (Sprint 5, unit) |
| UC-005-T8 | Given Shift is not latched, when the letter keys show, then they are lowercase; with Shift latched, the next letter shows in uppercase. | UI | exists (Sprint 5, UI; device round) |
| UC-005-T9 | Given the keyboard is open, when the user taps Win+Tab, then Win+Tab is sent once. | unit | exists (Sprint 5, unit; device round) |

Sprint 5 built the Borealis keyboard and its logic. A test marked "exists (Sprint
5, unit)" runs in the unit-test task, and "exists (Sprint 5, UI)" is a Compose
test that runs on Robolectric. "device round" marks a check that the Sprint 5
device round of 2026-10-16 ran on a TV or a phone (see
[device validation](../development/device-validation.md), section 10). The
round notes do not cover the key-text logs (UC-005-T6).

Partly covered:

- UC-005-T5: `StreamKeyboardTest` checks that the docked keyboard fills only the
  bottom half of the screen. It does not check that the stream stays visible
  above it. The device round checked it on the phone.
- UC-005-T6: a test scans the keyboard sources and fails if they call the log
  or `println`. The text path and the audit trail are not checked by a test.

## Host dependency

Keys go to the host through the input channel of the stream
([host contract, section 13](../host-contract/luminalshine.md)). The client
needs no extra host capability.

## Related

- ADRs: [0052](../adr/0052-structure-the-phase-2-ui.md).
- Host contract sections: [13](../host-contract/luminalshine.md#13-input).
- Policies: [privacy and logging](../policies/privacy-and-logging.md).
- Existing logic: Moonlight 12.2 opened the Android soft keyboard. The Borealis
  keyboard (`StreamKeyboard`, `KeyboardLayout`, `KeyboardLatch`) is in
  `:borealis:stream` from Sprint 5. Its System keyboard key opens the Android
  keyboard for text in other languages; free text is sent as UTF-8.
- Existing tests: `StreamKeyboardTest`, `KeyboardLatchTest`,
  `KeyboardNavigationTest`, `KeyboardLayoutTest`, `ShortcutsTest`.
