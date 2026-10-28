# UC-010: Adjust accessibility

- **Status:** Accepted
- **Platform:** All

## Summary

A user makes Borealis easier to see and to use: less motion, less transparency, a
thicker focus outline, bigger text, high contrast, or a palette for colour
blindness. A choice applies to the Library and Settings at once, with no restart,
and the stream reads the choices when it starts. A user of a screen reader
(TalkBack) hears what each screen is, each host and each step, and a finger finds
targets of 48 dp on a phone and a handheld.

## Actors

- Primary actor: the user, on a phone, a TV or a handheld.
- Other actors and systems: the Android system, which reports the animation
  scale, the font scale and whether TalkBack is on.

## Preconditions

- The app is installed.

## Main flow

1. The user opens Settings and chooses the Accessibility group, which is after
   Appearance ([UC-009](0009-change-settings.md)). On a phone it is a section.
2. The system shows six rows with their current values: Reduce motion,
   Transparency, Thicker focus outline, Text size, High contrast and
   Colour-blind palette.
3. The user changes a row. The system stores the choice at once, under a stable
   id, and the Library and Settings change at once. A stored value that no choice
   has shows the default.
4. The user leaves Settings.
5. The user starts a stream. The stream screens read the look once, when the
   stream starts: the text size, the focus outline, High contrast, the palette
   and Reduce motion. A change made during a stream shows in the next one. The
   stream has no glass, because the video is behind it.

## Alternative flows

### A1: Reduce motion (branches from step 3)

1. Follow system, the default, removes the animations when the animation scale of
   the system is zero (the system setting "Remove animations"). The app follows
   the scale while it runs. On removes them. Off keeps them, also when the system
   removes them.
2. With the animations removed, the background is one still frame and no clip
   plays, a tile takes the focus without the 1.04 scale, a screen shows without
   its fade, a list scrolls without animation, the Aurora loader and the check
   mark stand still and a section opens at once. The indeterminate progress
   indicators of Material (Quitting, Add host and Privacy and diagnostics) do not
   follow it yet; Sprints [15](../roadmap.md#sprint-15-the-stream-screens-and-glass) and
   [17](../roadmap.md#sprint-17-general-fixes) of the roadmap have the item.
3. The battery saver also gives the background one still frame.

### A2: Transparency (branches from step 3)

1. Standard shows the glass with the tint and the blur of Appearance (40 % and
   8 dp by default). Reduced shows it at 85 % and 48 dp, whatever Appearance says.
   Off draws solid panels.
2. The glass rows of Appearance are disabled with a note while Transparency is
   Reduced or Off ([UC-009](0009-change-settings.md), A2).
3. A device below Android 12 cannot blur: every level but Off is the Reduced tint
   with no blur. The panels are solid on the plain page (the background Off).

### A3: Thicker focus outline (branches from step 3)

1. The outline of the focused item is 5 dp and in the ink colour, instead of 3 dp
   in accent-soft. A cover tile still scales on focus unless Reduce motion removes
   the animations.
2. The thicker outline is not cut off in lists, grids and the chrome.

### A4: Text size (branches from step 3)

1. The user chooses 100 %, 115 % or 130 %. The text of Borealis is that much
   larger than the font size of the system, in the screens, the dialogs and the
   sheets. Sizes in dp do not change.
2. At the font scale 2.0 of the system, the text of the Library, Settings, Privacy
   and the Audit trail is not cut off and does not end in an ellipsis.

### A5: High contrast (branches from step 3)

1. The system draws solid panels, text at 7:1 or more, opaque borders at 3:1 or
   more, the thicker outline, and a dimmed background (the page colour at 0.85
   over it).
2. High contrast overrides only Transparency, Thicker focus outline, Glass tint
   and Glass blur. Their rows are disabled and say "High contrast is on in
   Accessibility". Text size, Reduce motion and the palette still apply, and the
   palette is applied after High contrast.
3. Loose text over the background keeps 7:1 over every background.

### A6: Colour-blind palette (branches from step 3)

1. The user chooses Red-green or Blue-yellow. The status colours (success,
   warning, danger and offline) change to the colours of that palette, which keep
   apart for the colour vision it is for. The accent adapts too (Sprint 11): where
   Violet, or the soft accent of White, would come within 20 of a status colour
   under the vision of the palette, it is drawn as a variant (a pale rose for
   Violet under Red-green, a lighter and pinker violet for Violet under
   Blue-yellow, and a soft accent with a trace of cyan for White under
   Blue-yellow). Teal keeps its colour.
2. Whatever the palette, a status is not told by colour alone: the dot of a host
   is a filled circle for online, a filled triangle for attention, and a hollow
   ring for offline and unknown. The card says it in words too.
3. The Online dot has a soft dark shadow in the page colour (Sprint 11), and a black
   one, a little wider, on a TV, where the glass is darker. It is static, and it is
   not drawn under High contrast. It keeps the dot apart from the glass under it
   ([accessibility](../ux/accessibility.md#the-offline-colour-and-the-status-shapes)).

### A7: The user uses TalkBack (Sprint 10; any step)

1. The titles of Hosts, Apps (the host name), Add host, Pairing, the Audit trail
   and Settings are headings, and so are the section labels, so TalkBack can jump
   between them.
2. A sheet, a dialog, the in-game menu and the keyboard say what they are when they
   open: the options of a host say the name of the host.
3. A host card is read as one sentence: the name, the status, the address, the
   capabilities and "Running Desktop" when an app runs. The dot says nothing of its
   own.
4. A step of the pairing is read as one thing, with its number, its text and its
   address.
5. A wrong, expired or failed PIN and the other outcomes of the pairing, and the
   error under the address of Add host, are announced when they appear, because
   the focus stays on the button.
6. A segment of the in-game menu says its row, so that no two are both "Off".
7. On a phone and a handheld, a touch target is 48 dp: a segment, a group of the
   settings list, a chip and the handle of a sheet. A TV has no touch screen, so
   it keeps its sizes.

### A8: Text spacing (Sprint 11; branches from step 3)

1. The Accessibility group has a Text spacing row, after Text size, with three
   choices: Normal (the default), Wide and Wider. The system stores the id under
   `borealis_text_spacing` (`normal`, `wide` or `wider`).
2. Wide sets the letter spacing to 0.12 em, the word spacing to 0.16 em and the line
   height to 1.5. Wider sets 0.18 em, 0.5 em and 1.5. A style keeps its own spacing
   where it is larger. The word spacing is drawn by the design-system `Text`, which
   adds a span on each space. TalkBack reads the original text. Typed text gets no word
   spacing, and the labels of text fields do. Where a layout does not fit at Wide or
   Wider, it is changed rather than cut: a value row stacks its value under the text
   when the longest word does not fit, a slider's value is limited to 40 % of its row,
   the host detail line wraps, the key captions are drawn with the spacing of Wide at
   Wider, and Compact and Dense keep the columns of Comfortable at Wider. The
   known limits are in [accessibility](../ux/accessibility.md#text-spacing-known-limitations).
3. At a font scale of 2.0, the largest that Android offers, Wider is drawn as Wide, and
   Wide and Normal are drawn as chosen. The scale is the font scale of the system times
   the Text size, so Largest text over a system font of 1.3 (1.69) draws Wider. The
   choice stays stored, and Wider comes back when the scale falls. The stream chrome
   follows the same rule. When the user chooses Wider while the font scale has reached
   2.0, a dialog opens with the title "Wider spacing is limited". It says that, at this
   text size, Borealis uses Wide spacing instead of Wider so that nothing is cut off,
   that the choice stays, and that Wider comes back when the system font size or the Text
   size is smaller. It has one OK button, which has the focus on a TV, and TalkBack reads
   the title as the name of the window. For as long as the limit is reached, the Text
   spacing row says "Drawn as Wide at this text size, so that nothing is cut off." under
   its value, so that a user who raised the font later can see why. The measurements are
   in [accessibility](../ux/accessibility.md#text-spacing-the-limit-at-a-very-large-font).

## Postconditions

- On success: the choice is stored and shown in the Library and Settings at once.
  The next stream uses it. No choice is uploaded or written to a log or a
  diagnostic bundle ([privacy and logging](../policies/privacy-and-logging.md)).
- On failure to store a value: the previous value stays, and the row shows it.

## Acceptance tests

UC-010-T1 to T14 are Sprint 10 tests (Compose tests run on Robolectric).
UC-010-T15 is the Sprint 10 device round.
UC-010-T16 to T18 are Sprint 11 rows.

| ID | Behaviour | Level | Status |
| --- | --- | --- | --- |
| UC-010-T1 | Given a theme with a look, when the look changes, then the content that is shown changes with it, and a theme with no look has the defaults. | UI | exists (Sprint 10, UI): `BorealisLookThemeTest` |
| UC-010-T2 | Given each Accessibility setting, when it is stored, then `BorealisLook` holds the value of its stable id, and an id that no choice has stands for the default. | unit | exists (Sprint 10, unit): `BorealisLooksTest` |
| UC-010-T3 | Given Reduce motion Follow system, On or Off, when the system animation scale or the setting changes, then the animations are removed or kept as the rule says, while the content is shown. | UI | exists (Sprint 10, UI): `ReducedMotionTest` |
| UC-010-T4 | Given High contrast, when the colours are read, then every text role is 7:1 or more on the page, the surfaces and the selected rows, the borders are opaque and 3:1 or more, and a palette composes with it. | unit | exists (Sprint 10, unit): `HighContrastColorsTest` |
| UC-010-T5 | Given a colour-blind palette, when the status colours are read, then every pair is 20 or more apart in CIEDE2000 for the dichromacies it is for, each colour is 3:1 or more, and the status inks are 7:1 or more (the bar of the test; 9.2:1 measured); and the accent and the soft accent, and their High contrast colours, are 20 or more apart from the four status colours under the vision of the palette, as the variants of Violet and White make them (Sprint 11). | unit | exists (Sprint 10 and 11, unit): `ColourVisionPaletteTest`, `AccentPaletteVariantsTest` |
| UC-010-T6 | Given a status, when the dot is drawn, then it is a filled circle, a filled triangle or a hollow ring, whatever the colour. | UI | exists (Sprint 10, UI): `StatusDotTest`; the Online shadow (Sprint 11) is in the same test class |
| UC-010-T7 | Given Thicker focus outline or High contrast, when an item has the focus, then the outline is 5 dp, starts 2 dp outside the item, is in ink, and keeps 3:1 over the page, the surface and the brightest backdrop of every glass. | UI | exists (Sprint 10, UI): `ThickFocusOutlineTest` |
| UC-010-T8 | Given a look, a background and an API level, when the glass is made, then Standard, Reduced, Off, High contrast and the plain page give the glass of ADR 0057, and a TV runs the drawn background at 24 frames a second only while the blur is drawn. | unit | exists (Sprint 10, unit): `GlassRulesTest` |
| UC-010-T9 | Given a stream starts, when the look is read, then it is the look that the settings store, and the chrome screens draw with the look that the host was built with. | unit, UI | exists (Sprint 10): `StreamSessionFactoryLookTest`, `StreamChromeHostLookTest` |
| UC-010-T10 | Given Hosts, Apps, Add host, Pairing, the Audit trail, Settings, the menu, the keyboard and the dialogs, when a screen reader reads them, then the titles are headings, the sheets and the panels say what they are, a host card is one sentence and a pairing step is one thing. | UI | exists (Sprint 10, UI): `HostsTalkBackTest`, `LibraryScreensTalkBackTest`, `AuditTalkBackTest`, `SettingsTalkBackTest`, `StreamTalkBackTest`, `ComponentTalkBackTest` |
| UC-010-T11 | Given a wrong or expired PIN, or an error under the address of Add host, when it appears, then a screen reader announces it. | UI | exists (Sprint 10, UI): `LibraryScreensTalkBackTest` |
| UC-010-T12 | Given the main screens on a phone, a handheld and a TV, when the Accessibility Test Framework runs over them, then it finds nothing but the accepted findings, each with its reason. | UI | exists (Sprint 10, UI): `LibraryAccessibilityChecksTest`, `SettingsAccessibilityChecksTest`, `StreamAccessibilityChecksTest`, `PrivacyAccessibilityChecksTest`, `AuditAccessibilityChecksTest`, `SendToPhoneAccessibilityChecksTest` |
| UC-010-T13 | Given a phone or a handheld, when the segments, the groups, the chips and the sheet handle are measured, then they are 48 dp; given a TV, then they keep their sizes. | UI | exists (Sprint 10, UI): `TouchTargetTest` |
| UC-010-T14 | Given a font scale of 2.0, when the Library, Settings, Privacy, the Audit trail and the components are shown, then no text is cut or ends in an ellipsis, at Normal and at Wider (drawn as Wide at 2.0), and at Wide in IBM Plex Sans. | UI | exists (Sprint 10, UI; Wide and Wider since Sprint 11): `LargeTextFitTest`, `SettingsLargeTextFitTest`, `AuditLargeTextFitTest` |
| UC-010-T15 | Given the Streamer and the S22, when the user changes each Accessibility setting and uses TalkBack, then each holds. | device | checked in part in the Sprint 10 round of 2026-10-21 (section 3.23, results in section 15); the visual items and TalkBack are the owner's |
| UC-010-T16 | Given the Accessibility group, when the user chooses Text spacing (Normal, Wide or Wider), then the choice is stored under `borealis_text_spacing` as `normal`, `wide` or `wider`, the text draws the letter and word spacing of A8 (Wide 0.12 em and 0.16 em, Wider 0.18 em and 0.5 em), the line height is at least 1.5 at Wide and Wider, TalkBack reads the original text, and a layout that does not fit at Wide or Wider is changed rather than cut, as in A8. | UI, unit | exists (Sprint 11, UI and unit): `TextSpacingTypographyTest`, `TextSpacingThemeTest`, `TextWordSpacingTest`, `WordGapTest`, `SettingsTextSpacingTest` |
| UC-010-T17 | Given an Online host, when its dot is drawn, then a soft dark shadow in the page colour lies round the dot, outside its box, it is static, and it is not drawn under High contrast. | UI | exists (Sprint 11, UI): `StatusDotTest` |
| UC-010-T18 | Given Wider chosen, when the font scale (the system scale times the Text size) is below 2.0, then Wider is drawn as Wider, and from 2.0 it is drawn as Wide, in the Library, Settings and the stream chrome, and the choice stays stored; when the user chooses Wider at 2.0 or more, then a dialog says that Wide is used instead, with one OK button that has the focus on a TV and a title that names the window, the row says the same under its value, and no dialog opens below 2.0 or for Wide or Normal. Given the Pairing screen of a TV, the rail footer and an Audit entry at large text, then the steps keep their room, the name wraps and the title keeps its room. | UI, unit | exists (Sprint 11, UI and unit): `TextSpacingLimitThemeTest`, `StreamChromeTextSpacingTest`, `SettingsTextSpacingLimitTest`, `SettingsTextSpacingLimitPhoneTest`, `SpacingLimitScreenshotTest`, `PairingTvRoomTest`, `RailFooterNarrowTest`, `AuditEntryRoomTest` |

Not covered by a test: the traversal links that TalkBack follows (Robolectric
cannot see them), a visible snackbar (no check covers it), and the halo and the
tint-only glass on API 28 to 30, which have not been seen on a device.

## Host dependency

None. The settings are stored on the device and are never sent to the host.

## Related

- ADRs: [0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md)
  (glass and the Accessibility settings),
  [0053](../adr/0053-material-3-with-borealis-focus-on-tv.md) (the focus
  outline), [0056](../adr/0056-add-an-animated-background-and-the-appearance-group.md)
  (the background and Reduce motion).
- Use cases: [UC-009](0009-change-settings.md) (the Settings screen and the rows).
- Documents: [accessibility](../ux/accessibility.md) (the contrast measurements,
  the palettes, the touch targets and a WCAG 2.2 checklist) and the
  [runbook, section 3.23](../development/device-validation.md#323-sprint-10-checks).
- Policies: [privacy and logging](../policies/privacy-and-logging.md) (where
  settings are stored).
- Existing logic: `BorealisLook` and `borealisLook(snapshot)` map the settings to
  the look; `BorealisTheme` provides it; the Library passes it live and the
  stream reads it once in `StreamSessionFactory.look`.
