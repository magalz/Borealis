# UC-009: Change settings

- **Status:** Accepted
- **Platform:** All

## Summary

A user changes the global Borealis settings for streams, input, gamepads,
on-screen controls, audio and advanced behaviour. A change is stored at once and
applies to the next stream. The screen shows only the rows that the device can
use.

## Actors

- Primary actor: the user, on a phone, a TV or a handheld.
- Other actors and systems: the Android system, which reports the device's
  display, decoder and input features.

## Preconditions

- The app is installed.

## Main flow

1. The user opens Settings. On a phone, the user taps the gear in the top bar.
   On a TV or a handheld, the user chooses Settings in the rail.
2. The system shows the settings in groups. The user picks a row. The groups
   and rows are listed in
   [the Phase 2 proposal](../ux/phase-2-proposal.md#settings-groups).
3. The user changes the row. A switch changes at once. A list opens a choice
   sheet. A slider moves with touch, or with D-pad Left and Right.
4. The system stores the value at once. Each row shows its current value. There
   is no Save button.
5. The user presses Back to leave Settings.

## Layout

- A phone shows one list.
- A TV or a handheld shows two panes: the groups on the left and the rows on the
  right. D-pad Right moves into the rows. D-pad Left returns to the groups.

## Alternative flows

### A1: A row is hidden by the device (branches from step 2)

1. The system does not show a row that the device cannot use. Examples: HDR
   without an HDR10 display, Motion sensors below Android 12, and the
   On-screen controls group without a touchscreen.
2. The rest of the group is unchanged.

### A2: A row is disabled by another setting (branches from step 3)

1. The row stays focusable. It is shown disabled, with a note that says why.
   Examples: Analog scrolling while Gamepad mouse is off, and Vibration strength
   while Vibrate fallback is off.
2. The user cannot change a disabled row.

### A3: The native resolution is chosen (branches from step 3)

1. The Resolution list offers the native resolution of the display, filtered by
   the display and the decoder.
2. The system shows a warning that the PC must support this resolution.
3. The user keeps the choice or changes it.

The client does not read the PC's supported modes, so the warning is the only
check.

### A4: Bitrate returns to automatic (branches from step 3)

1. The user changes Resolution or Frame rate.
2. The system sets Bitrate back to automatic. Bitrate then shows the value
   computed from the new resolution and frame rate.

### A5: Frame rate rules (branches from step 3)

1. The Frame rate list hides 120 fps when the display is below 118 Hz, and 90 fps
   when the display is below 88 Hz.
2. When Unlock all frame rates is on, the rules in step 1 do not apply.
3. Turning on Unlock all frame rates updates the Frame rate list at once.

### A6: Reset layout (branches from step 3)

1. The user chooses Reset layout in On-screen controls.
2. The system asks first, with the text "Cannot be undone". Cancel leaves the
   layout unchanged.
3. On confirmation, the system resets the layout of the on-screen controls.

## Postconditions

- On success: the changed value is stored, and it applies to the next stream.
  The current stream is not changed.
- On failure to store a value: the previous value stays, and the row shows it.
  The error is logged; no message is shown.

## Acceptance tests

UC-009-T1 to T8 exist (Sprint 6). UC-009-T9 and T10 were checked on a device in
the Sprint 6 device round (see [device validation](../development/device-validation.md),
section 11).

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-009-T1 | Given a phone, a TV or a handheld, when Settings opens, then the groups and rows listed for that device are shown, and no other row is shown. | UI | exists (Sprint 6, UI) |
| UC-009-T2 | Given a device without a feature, when Settings opens, then the rows that need the feature are not shown. | unit | exists (Sprint 6, unit) |
| UC-009-T3 | Given a row disabled by another setting, when the row is focused, then it is focusable, it cannot be changed, and its note says why it is disabled. | UI | exists (Sprint 6, UI) |
| UC-009-T4 | Given each control type (switch, list, slider and action), when the user changes it, then the value is stored and the row shows it. | unit | exists (Sprint 6, unit) |
| UC-009-T5 | Given Resolution or Frame rate is changed, when the change is stored, then Bitrate is automatic. | unit | exists (Sprint 6, unit) |
| UC-009-T6 | Given the Frame rate rules (120 fps below 118 Hz, 90 fps below 88 Hz, and Unlock all frame rates), when the list is shown, then the rules hold, and turning on Unlock all frame rates updates the list at once. | unit | exists (Sprint 6, unit) |
| UC-009-T7 | Given the On-screen controls group, when Reset layout is chosen, then the system asks first with "Cannot be undone", and Cancel leaves the layout unchanged. | UI | exists (Sprint 6, UI) |
| UC-009-T8 | Given a TV or a handheld, when the user presses D-pad Right and Left, then focus moves into the rows and back to the groups. | UI | exists (Sprint 6, UI) |
| UC-009-T9 | Given an app with the two logging switches stored in the SharedPreferences build, when the app is upgraded, then both switches keep their values. | emulator | exists (Sprint 6, device round) |
| UC-009-T10 | Given a setting is changed, when the next stream starts, then the stream uses the new value. | emulator | exists (Sprint 6, device round) |

## Host dependency

None for storing settings. The Resolution warning tells the user that the PC must
support the chosen resolution. The list of settings does not depend on a
LuminalShine capability.

## Related

- ADRs: [0055](../adr/0055-settings-storage-and-the-phase-2-settings-screen.md),
  [0052](../adr/0052-structure-the-phase-2-ui.md) (settings are global, D7).
- Use cases: [UC-006](0006-send-diagnostics-from-a-tv-to-a-phone.md) and
  [UC-007](0007-review-and-erase-the-audit-trail.md) (the Privacy and diagnostics
  screen, reached from Settings).
- Proposal: [Settings](../ux/phase-2-proposal.md#settings) and
  [Settings groups](../ux/phase-2-proposal.md#settings-groups).
- Policies: [privacy and logging](../policies/privacy-and-logging.md) (where
  settings are stored).
