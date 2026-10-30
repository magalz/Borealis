# UC-009: Change settings

- **Status:** Accepted
- **Platform:** All

## Summary

A user changes the global Borealis settings for streams, input, gamepads,
on-screen controls, audio, advanced behaviour, appearance and accessibility. A
change is stored at once and applies to the next stream. The exceptions are the
settings of the Appearance and Accessibility groups (the background, the glass
and the accessibility choices): they change the Library and Settings at once,
and the stream reads them when it starts. The screen shows only the rows that the
device can use.

## Actors

- Primary actor: the user, on a phone, a TV or a handheld.
- Other actors and systems: the Android system, which reports the device's
  display, decoder and input features.

## Preconditions

- The app is installed.

## Main flow

1. The user opens Settings. On a phone, the user taps the gear in the top bar.
   On a TV or a handheld, the user chooses Settings in the rail.
2. The system shows the settings in 11 groups, in this order: Stream, Video,
   Input, Gamepad, On-screen controls, Audio, Advanced, Appearance,
   Accessibility, Privacy and diagnostics, and About. The user picks a row. The
   groups and rows are listed in
   [the Phase 2 proposal](../ux/phase-2-proposal.md#settings-groups).
3. The user changes the row. A switch changes at once. A list opens a choice
   sheet. A slider moves with touch, or with D-pad Left and Right.
4. The system stores the value at once. Each row shows its current value. There
   is no Save button.
5. The user presses Back to leave Settings.

## Layout

- A phone shows one list in which every group is a collapsible section (A8).
- A TV or a handheld shows two panes: the groups on the left and the rows on the
  right. D-pad Right, or OK, moves into the rows (A9). D-pad Left returns to the
  groups, and Left again returns to the rail.
- Opening Settings from the rail puts focus on the selected group.
- Moving focus from group to group changes the pane at once, in the same frame,
  with no wait. Every group has its own pane, built once and stacked with the
  others, so a switch only changes which pane is visible. The selected group is
  built first and the others follow, one per frame, nearest first. Privacy and
  diagnostics is built only while it is selected.
- A pane that is not shown takes no focus, touch or accessibility. A group opens
  at its top whenever it is shown.
- The list fits a 540 dp TV window without scrolling, even with all eleven groups
  (the On-screen controls group needs a touchscreen, so the Google TV Streamer
  shows ten). On a handheld the list scrolls, because its entries are 48 dp.

The 48 dp touch targets of Sprint 10 have effects that are pending the owner's
review. At 800 × 480 dp (the `settingsscreen_handheld_stream.png` reference) 6
of the 11 groups show (Stream to Audio) and the other 5 need scrolling. In a
window 360 dp tall, such as a phone in landscape with a pad, about 4 show. The
docked keyboard of a phone is 24 dp taller, because its shortcut chips have 48
dp cells. The keys of the phone keyboard stay under 48 dp, which is an accepted
finding of the accessibility checks
([accessibility](../ux/accessibility.md#accepted-findings)).

## Alternative flows

### A1: A row is hidden by the device (branches from step 2)

1. The system does not show a row that the device cannot use. Examples: HDR
   without an HDR10 display, Motion sensors below Android 12, and the
   On-screen controls group without a touchscreen.
2. The rest of the group is unchanged.

### A2: A row is disabled by another setting (branches from step 3)

1. The row stays focusable. It is shown disabled, with a note that says why.
   Examples: Analog scrolling and Gamepad mouse speed while Gamepad mouse is off,
   and Vibration strength while Vibrate fallback is off.
   - Glass tint and Glass blur are disabled while High contrast is on ("High
     contrast is on in Accessibility"), and while Transparency is Reduced or Off
     ("Transparency is set to Reduced in Accessibility", and the same for Off).
     High contrast is the reason given first.
   - Transparency and Thicker focus outline are disabled while High contrast is
     on, because it sets solid panels and the thicker outline itself.
2. The user cannot change a disabled row. A disabled row still shows the value
   that is stored.

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

### A7: The user changes the background (branches from step 3)

1. The user opens the Appearance group and chooses the Background row. The system
   opens a list of the seven styles, with the current one chosen: Aurora curtains,
   Soft glow, Northern lights, Light mist, Flowing light, Starfield and Off.
2. The user chooses a style. The system stores its id under `borealis_background`.
   The background behind Settings changes at once, with no restart.
3. While Reduce motion removes animations (Follow system follows the system
   setting that removes them) or the battery saver is on, a style shows one still
   frame and nothing plays.
4. A stored value that no style has shows Aurora curtains, the default.

The background shows behind every Library and Settings screen. In a stream it shows on
the screens outside the game, as the Library shows it, with no option of its own, and
never behind the video ([UC-002](0002-start-a-stream.md), A6,
[ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md), which amends
[ADR 0056](../adr/0056-add-an-animated-background-and-the-appearance-group.md) decision 5).

### A8: A phone shows the groups as sections (Sprint 10; branches from step 2)

1. On a phone, every group is a collapsible section with a header of at least
   48 dp. All sections start collapsed, and the header says Collapsed or
   Expanded to a screen reader.
2. A tap on a header opens its section in place, and the next tap closes it.
   Several sections can be open at once, and opening one does not scroll the
   list.
3. The open sections survive a rotation, and a trip to Privacy and diagnostics
   and back, which leaves the scroll position as it was.
4. About is a section. Privacy and diagnostics stays a row between the groups and
   About, and it opens its own screen.
5. With Reduce motion removing animations, a section opens and closes at once.

### A9: OK enters the pane of a group (Sprint 10; TV and handheld, branches from step 2)

1. A group has the focus. The user presses OK, Enter, the numpad Enter or the
   A button of a gamepad.
2. The system selects the group, if another one was selected, and moves the focus
   to the first row of its pane, as Right does. Left returns to the group.
3. This holds in the keyboard input mode only. A tap on a group, in the touch
   mode, selects it and leaves the focus where it is. Moving the focus to
   another group before the pane has taken it cancels the entry.

### A10: The user changes the glass (Sprint 10; branches from step 3)

1. In Appearance, after Background, the user opens Glass tint (30, 40, 50, 60, 70
   or 85 %; 40 % is the default) or Glass blur (8, 16, 24, 32 or 48 dp; 8 dp is
   the default). They set the Standard level of Transparency.
2. The system stores the id of the choice, the percent or the dp, under
   `borealis_glass_tint` or `borealis_glass_blur`. The panels, the cards and
   the rail of the Library and Settings change at once.
3. A background that needs a more opaque glass raises the tint above the choice:
   Northern lights is raised to 70 % at 8 dp. A stored value that is not offered
   shows the default.
4. The rows are disabled with a note while High contrast is on, or Transparency
   is Reduced or Off (A2).

### A11: The user changes an Accessibility row (Sprint 10; branches from step 3)

1. The Accessibility group, after Appearance, has seven rows: Reduce motion
   (Follow system, On or Off), Transparency (Standard, Reduced or Off), Thicker
   focus outline (a switch), Text size (100, 115 or 130 %), Text spacing (Normal,
   Wide or Wider), High contrast (a switch) and Colour-blind palette (Off,
   Red-green or Blue-yellow). The defaults are Follow system, Standard, off, 100 %,
   Normal, off and Off. Text spacing is a Sprint 11 row ([UC-010](0010-adjust-accessibility.md), A8).
2. The system stores the choice at once under a stable id and applies it to the
   Library and Settings with no restart. A stored value that no choice has shows
   the default.
3. The stream reads these choices when it starts. [UC-010](0010-adjust-accessibility.md)
   describes each one.

### A12: The About links and the Version row (Sprint 10; branches from step 2)

1. The Version row of About shows the version and the commit. It has no chevron
   and opens nothing. It is a stop for the D-pad and is read as one sentence, not
   as a button.
2. Source code and Privacy policy open their link in a task of their own, so Back
   from the browser returns to Borealis. A link that no app takes shows its
   address in a dialog.
3. Open-source licences opens a dialog whose text is above Close, also in a short
   window. On a TV the D-pad Up and Down scroll the text.
4. On a TV, a link in About opens a dialog instead of the browser (Sprint 11). The
   dialog shows the URL and a QR code, 240 dp square with a quiet zone of four
   modules, so that a phone can open the same link. Back closes the dialog. A
   phone or a handheld opens the browser, as in step 2.

### A13: The user changes the font, the accent, the background intensity or the card density (Sprint 11; branches from step 3)

1. In Appearance, the rows are Font, Accent colour, Background (A7), Background
   intensity, Stream menu style (A14), Card density, and then Glass tint and Glass
   blur (A10): eight rows.
2. Font has seven choices: IBM Plex Sans (the default), Atkinson Hyperlegible Next,
   Luciole, Lexend, Andika, B612 and the system font. Each has a purpose line under
   its name, and each name is drawn in its own font. The system stores the id under
   `borealis_font` (`plex`, `atkinson_next`, `luciole`, `lexend`, `andika`, `b612` or
   `system`). The codes, such as the PIN and the addresses, use the monospaced
   companion of the font where it has one, and IBM Plex Mono otherwise.
3. Accent colour has three choices: Violet (the default), Teal and White. The system
   stores the id under `borealis_accent` (`violet`, `teal` or `white`).
4. Background intensity has four choices: 100 % (the default), 75 %, 50 % and 25 %.
   The system stores them under `borealis_background_intensity` as `100`, `75`, `50`
   and `25`. The background is dimmed under the glass.
5. Card density has three choices: Comfortable (the default), Compact and Dense. The
   system stores them under `borealis_card_density` as `comfortable`, `compact` and
   `dense`. Compact and Dense add one column on every device, except with Text spacing
   at Wider, where they keep the columns of Comfortable (the Wider that is drawn: at a font
   scale of 2.0 Wider is drawn as Wide, and the column is added). The hosts and apps in a
   row are 4 and 6 on a TV (3 and 5 in Comfortable), 3 and 5 on a handheld (2 and 4),
   2 and 4 on a phone (1 and 3), and 3 and 5 on a wide phone (2 and 4). Dense also cuts
   the card padding from 18 to 12 dp on a TV and a handheld, and from 14 to 10 dp on a
   phone. It cuts the Continue cover from 56 to 44 dp and the least height of a TV host
   card from 176 to 164 dp, and it does this at Wider too. The gutter of the screen does
   not change. The host cards of a row are as high as the tallest of them. A Running
   badge with no room for its label shows its dot alone, in a 20 dp ring. A change
   re-lays out the screen at once and keeps the focus.
6. Each choice applies to the Library and Settings at once, with no restart, and the
   stream reads it when it starts, as in A11.

### A14: The user changes the style of the stream menu (Sprint 15; branches from step 3)

1. In Appearance, after Background intensity, the row Stream menu style sets the
   glass of the in-game menu. It has Blurred (the default) and Solid. Blurred is a
   live blurred copy of the video behind the in-game menu and its Quit popup, and
   Solid is a plain panel.
2. The system stores the stable id under `borealis_stream_menu_glass` (`blur` or
   `solid`). A stored value that no choice has shows the default. The ids and the
   default are those of the first version of the row, which was named Menu glass in
   the stream and offered Blur and Solid, so no stored value changes its meaning.
3. The row changes nothing in the Library or in Settings. The stream reads it once,
   when it starts, so a change shows in the next stream.
4. The row is not disabled by the Accessibility settings. Transparency Off, High
   contrast, Reduce motion, the battery saver and an API level below 31 win in the
   stream: [UC-002](0002-start-a-stream.md), A6, and
   [UC-004](0004-use-the-in-game-menu.md), A10, say how.
5. The screens outside the game have no row of their own. They stand on the
   background of A7, as the Library shows it (UC-002, A6). The first version of
   Sprint 15 also had a row, Background before and after the stream (Moving, Still
   or Off), stored under `borealis_stream_background`. The owner dropped it on
   2026-10-30. The setting is no longer in the catalog, so a value stored under that
   key is never read: the DataStore leaves it in the file and nothing uses it.

## Postconditions

- On success: the changed value is stored, and it applies to the next stream.
  The current stream is not changed. The Appearance and Accessibility settings
  are the exception: they change the Library and Settings at once, and a stream
  reads them when it starts.
- On failure to store a value: the previous value stays, and the row shows it.
  The error is logged; no message is shown.

## Acceptance tests

UC-009-T1 to T8 exist (Sprint 6). UC-009-T9 and T10 were checked on a device in
the Sprint 6 device round (see [device validation](../development/device-validation.md),
section 11). UC-009-T11 to T21 are Sprint 9 rows. The Compose tests run on
Robolectric. T22 is checked in the Sprint 9 device round (section 3.22).
UC-009-T23 to T31 are Sprint 10 rows, and T32 is the Sprint 10 device round
(section 3.23).
UC-009-T33 to T37 are Sprint 11 rows, and T38 and T39 are Sprint 15 rows.

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
| UC-009-T9 | Given an app with the two logging switches stored in the SharedPreferences build, when the app is upgraded, then both switches keep their values. | device | exists (Sprint 6, device round) |
| UC-009-T10 | Given a setting is changed, when the next stream starts, then the stream uses the new value. | device | exists (Sprint 6, device round) |
| UC-009-T11 | Given the Appearance group, when the user opens the Background row, then the list offers the seven styles, with Aurora curtains first and chosen by default. | UI | exists (Sprint 9, UI): `SettingsTvTest`, `SettingsHandheldTest`, `SettingsPhoneTest` |
| UC-009-T12 | Given the user chooses a style, when the choice is stored, then `borealis_background` holds the stable id of the style, never its ordinal, and the row shows the style. | unit, UI | exists (Sprint 9): `BackgroundChoicesTest`, `SettingsViewModelTest`, `StoreSettingsRepositoryTest`, `DataStoreBackingTest`, `SettingsTvTest` |
| UC-009-T13 | Given a stored value that no style has, when Settings opens, then the default style is read and shown as chosen. | unit, UI | exists (Sprint 9): `BackgroundChoicesTest`, `SettingsTvTest` |
| UC-009-T14 | Given the Library shows Settings, when the style changes, then the background changes at once with no restart, and Off leaves the plain page colour. | UI | exists (Sprint 9, UI): `LibraryActivityBackgroundTest` |
| UC-009-T15 | Given a TV or a handheld, when focus moves to another group, then its pane shows at once and no row is composed, measured or drawn again. | UI | exists (Sprint 9, UI): `SettingsTwoPaneStackTest` |
| UC-009-T16 | Given Settings opens, then the selected pane is built first and the others one per frame, nearest first, and a pane that brings its own content (Privacy and diagnostics) is composed only while it is shown. | UI | exists (Sprint 9, UI): `SettingsTwoPaneBuildingTest` |
| UC-009-T17 | Given a pane that is not shown, then it cannot be touched, takes no focus from the D-pad or Tab, and has no accessibility node. | UI | exists (Sprint 9, UI): `SettingsTwoPaneStackTest`, `SettingsTwoPaneBuildingTest` |
| UC-009-T18 | Given a group that was scrolled, when it is shown again, then it opens at its top. | UI | exists (Sprint 9, UI): `SettingsTwoPaneSwitchingTest` |
| UC-009-T19 | Given one setting changes, then the rows of the other settings are not recomposed. | UI, unit | exists (Sprint 9): `SettingsRowSkippingTest`, `SettingsRowValueTest` |
| UC-009-T20 | Given the user chooses Settings in the rail with the D-pad, then focus is on the selected group, and Left from the group returns to the rail. | UI | exists (Sprint 9, UI): `LibrarySettingsTvTest` |
| UC-009-T21 | Given a 540 dp TV window with all eleven groups (ten without On-screen controls, as on the Streamer), when Settings opens, then every group is whole inside the window, and Down from Stream to About focuses each group in turn without the list scrolling. | UI | exists (Sprint 9 for ten groups, Sprint 10 for eleven, UI): `SettingsTvTest` |
| UC-009-T22 | Given the Google TV Streamer, when the user moves between groups, then focus and the pane change together without a wait. | device | planned (Sprint 9 device round, section 3.22); measured with atrace in section 14 |
| UC-009-T23 | Given a phone, when Settings opens, then every group is a collapsed section; a tap opens a section and the next tap closes it, several can be open, the open ones survive a recreation and a trip to Privacy and diagnostics, About is a section, and Privacy and diagnostics is a link row. | UI | exists (Sprint 10, UI): `SettingsPhoneSectionsTest`, `CollapsibleSectionTest` |
| UC-009-T24 | Given a TV or a handheld and a group with the focus, when the user presses OK, Enter, the numpad Enter or the gamepad A, then the focus is on the first row of its pane and Left returns to the group; given a tap, then the group is selected and the focus stays. | UI | exists (Sprint 10, UI): `SettingsTwoPaneTest`, `SettingsTvTest` |
| UC-009-T25 | Given the Appearance group, when it opens, then it has eight rows, Font, Accent colour, Background, Background intensity, Stream menu style, Card density, Glass tint (30 to 85 %) and Glass blur (8 to 48 dp), in that order, each stored as a stable id, and a stored value that is not offered shows the default. | unit, UI | exists (Sprint 10; the Sprint 11 rows and their order: `SettingsAccessibilityTest`; the Sprint 15 row: `StreamAppearanceTest`): `BorealisLooksTest`, `DataStoreBackingTest` |
| UC-009-T26 | Given the Accessibility group, when it opens, then it has its seven rows, in order (Text spacing among them), at their defaults, each stores a stable id, and a stored value that no choice has shows the default. | unit, UI | exists (Sprint 10; seven rows since Sprint 11: `SettingsAccessibilityTest`): `BorealisLooksTest` |
| UC-009-T27 | Given High contrast is on, then Glass tint, Glass blur, Transparency and Thicker focus outline are disabled with the note, and the other rows can be changed; given Transparency is Reduced or Off, then the two glass rows are disabled with their note. | unit, UI | exists (Sprint 10): `DefaultSettingsOptionsTest`, `SettingsAccessibilityTest` |
| UC-009-T28 | Given the Library shows Settings, when an Appearance or Accessibility setting changes, then the Library changes at once with no restart. | UI | exists (Sprint 10, UI): `LibraryActivityLookTest`, `LibraryActivityBackgroundTest` |
| UC-009-T29 | Given About, when the Version row shows, then it is read as text and not as a button, it has no click; a link opens in a task of its own; a link that no app takes shows its address. | UI | exists (Sprint 10, UI): `SettingsRowFixesTest`, `SettingsSystemTest`, `SettingsRowsTest` |
| UC-009-T30 | Given a short window, when the licence dialog opens, then the text is wholly above Close, Up from Close reaches it and Down scrolls it. | UI | exists (Sprint 10, UI): `LicensesDialogShortWindowTest`, `LicensesDialogTvTest` |
| UC-009-T31 | Given the Settings screens at a font scale of 2.0, then no text is cut or ends in an ellipsis. | UI | exists (Sprint 10, UI): `SettingsLargeTextFitTest`, `AuditLargeTextFitTest` |
| UC-009-T32 | Given the Google TV Streamer and the S22, when the user runs the Sprint 10 checks (glass, frame time, 11 groups, OK entering a pane, the phone sections, the About links), then each holds. | device | checked in the Sprint 10 round of 2026-10-21, build `8d5e8332` (section 3.23, results in section 15); the visual items are the owner's |
| UC-009-T33 | Given the Appearance group, when the user chooses a Font (IBM Plex Sans, Atkinson Hyperlegible Next, Luciole, Lexend, Andika, B612 or the system font), then the choice is stored under `borealis_font` as its id, the text of the Library and Settings is drawn in it at once, the codes use its monospaced companion (IBM Plex Mono where it has none), and a stored id that is not offered shows IBM Plex Sans. | UI, unit | exists (Sprint 11, UI and unit): `SettingsFontTest`, `FontChoiceTypographyTest`, `BorealisLooksTest` |
| UC-009-T34 | Given the Appearance group, when the user chooses an Accent colour (Violet, Teal or White), then the choice is stored under `borealis_accent` as its id, the Library changes at once, the selected labels keep 4.5:1 or more over the glass, and a stored id that is not offered shows Violet. Under a colour-blind palette Violet, and the soft accent of White, are drawn as the variants that keep 20 or more from the status colours. | UI, unit | exists (Sprint 11, UI and unit): `AccentColorsTest`, `AccentPaletteVariantsTest`, `AccentTextContrastTest`, `BorealisLooksTest` |
| UC-009-T35 | Given the Appearance group, when the user chooses a Background intensity (100, 75, 50 or 25 %), then the choice is stored under `borealis_background_intensity` as `100`, `75`, `50` or `25`, the background is veiled by 0, 25, 50 or 75 % of the page colour, the Library changes at once, and under High contrast the stronger of the veil and the dim wins. | UI, unit | exists (Sprint 11, UI and unit): `BackgroundIntensityTest`, `LibraryBackgroundIntensityTest`, `BorealisLooksTest` |
| UC-009-T36 | Given the Appearance group, when the user chooses a Card density (Comfortable, Compact or Dense), then the choice is stored under `borealis_card_density` as its id, Compact and Dense add one column to the hosts and apps rows (not with Text spacing at Wider, where they keep the columns of Comfortable), Dense cuts the card padding, the gutter, the gaps, the touch targets and the TV focus scale stay the same, a Running badge with no room for its label shows its dot alone, and the screen re-lays out at once and keeps the focus. | UI, unit | exists (Sprint 11, UI and unit): `CardDensityPaddingTest`, `CardDensityLayoutTest`, `CardDensityInvariantsTest`, `LibraryActivityCardDensityTest` |
| UC-009-T37 | Given a TV, when the user chooses Source code or Privacy policy in About, then a dialog shows the address of the link and a 240 dp QR code that decodes to that address, Close and Back dismiss the dialog and the focus returns to the row, and a phone or a handheld opens the browser instead. | UI | exists (Sprint 11, UI): `AboutLinksTvTest` |
| UC-009-T38 | Given the Appearance group, when the user chooses the Stream menu style (Blurred or Solid), then the choice is stored under `borealis_stream_menu_glass` as `blur` or `solid`, a stored value that no choice has shows Blurred, and nothing in the Library changes. Given a value stored under `borealis_stream_background`, the key of the dropped row Background before and after the stream, then it is not read, and no other setting changes. | unit, UI | exists (Sprint 15): `StreamAppearanceTest`, `SettingsViewModelTest`, `DataStoreBackingTest`, `BorealisLooksTest` |
| UC-009-T39 | Given the Stream menu style and the Background are stored, when a stream starts, then it reads them once, nothing stored gives Blurred and the default style, and a change shows in the next stream. | unit | exists (Sprint 15, unit): `StreamSessionFactoryVisualsTest`, `StreamSessionVisualsTest` |

## Host dependency

None for storing settings. The Resolution warning tells the user that the PC must
support the chosen resolution. The list of settings does not depend on a
LuminalShine capability.

## Related

- ADRs: [0055](../adr/0055-settings-storage-and-the-phase-2-settings-screen.md),
  [0052](../adr/0052-structure-the-phase-2-ui.md) (settings are global, D7),
  [0056](../adr/0056-add-an-animated-background-and-the-appearance-group.md)
  (the Appearance group and the background),
  [0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md) (the glass
  and the Accessibility group),
  [0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md) (the
  stream menu style).
- Use cases: [UC-010](0010-adjust-accessibility.md) (the Accessibility settings),
  [UC-006](0006-send-diagnostics-from-a-tv-to-a-phone.md) and
  [UC-007](0007-review-and-erase-the-audit-trail.md) (the Privacy and diagnostics
  screen, reached from Settings).
- Proposal: [Settings](../ux/phase-2-proposal.md#settings) and
  [Settings groups](../ux/phase-2-proposal.md#settings-groups).
- Policies: [privacy and logging](../policies/privacy-and-logging.md) (where
  settings are stored).
