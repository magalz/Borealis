# Roadmap

This page holds the order of the coming work and the backlog. It describes
the plan. It does not promise dates.

How the roadmap is used:

- The order on this page is the plan. Items are taken in this order unless
  the owner changes it.
- Each item that enters a sprint gets a GitHub issue.
- The phase pull request closes those issues.
- This page is updated when a sprint is planned or merged.

## Done

- **Phase 1, Sprint 1 (PR #8):** the foundation and LuminalShine protocol
  compatibility.
- **Phase 2, Sprints 2 to 9 (PRs #9 to #13, #19, #20 and #32):**
  - Sprint 2 (PR #9): observability, the audit trail, Phase 1 follow-ups and
    release preparation.
  - Sprint 3 (PR #10): Sprint 2 follow-ups and the Phase 2 UX proposal
    ([ADR 0052](adr/0052-structure-the-phase-2-ui.md),
    [proposal](ux/phase-2-proposal.md)).
  - Sprint 4 (PR #11): the Compose foundation, the Library screens and the
    second-device join.
  - Sprint 5 (PR #12): independence from upstream
    ([ADR 0054](adr/0054-declare-independence-from-moonlight.md)), the stream
    screens and the in-game menu.
  - Sprint 6 (PR #13): Settings, Privacy and diagnostics, the audit trail
    viewer and Send to phone, in Compose
    ([ADR 0055](adr/0055-settings-storage-and-the-phase-2-settings-screen.md)).
  - Sprint 7 (PR #19): follow-ups and backlog, the gamepad mouse pointer speed
    and the visual refinement pass. See the
    [Sprint 7 items](#sprint-7-follow-ups-and-backlog).
  - Sprint 8 (PR #20): tidy-up. See the
    [Sprint 8 items](#sprint-8-tidy-up).
  - Sprint 9 (PR #32): UX polish and the handheld. See the
    [Sprint 9 items](#sprint-9-ux-polish-and-the-handheld).

### Sprint 7: follow-ups and backlog

Merged as PR #19 (`f3d88540`).

1. **MediaTek decode time shows "—" in the stats overlay.** Done (issue #14,
   merges `903e399d`, `7cbc60ea` and `967487ea`). The Google TV Streamer shows
   no decode time. The Galaxy S22 shows a number. The Sprint 6 FIFO attempt was
   reverted in `c02fdafb`. Sprint 7 matches output timestamps to input
   timestamps, with pruning, measures the decode time from the queue time in
   microseconds, and logs counters under verbose logging.
2. **The quit combo does not end the stream on pads driven by the app's USB
   driver.** Done (issue #15, merge `5416a79c`). Start, Select, LB and RB ends
   the stream on these pads too.
3. **Investigate the repeated USB permission dialog on Google TV.** Done, fixed
   in this sprint (issue #16, merge `0ad99565`). The 8BitDo receivers change their
   USB identity by themselves, and each change showed Android's attach dialog
   while the USB driver was on. On a TV the attach activity is no longer offered.
   The USB permission dialog is asked only once the stream shows its first frame,
   and a pad that wakes during the stream asks at once, so it shows one dialog.
   Phones are unchanged. The remaining idea, documenting the native pad modes, is
   planned for Sprint 13. See the
   [device validation runbook, section 12](development/device-validation.md#12-results-sprint-7).
4. **Gamepad mouse: pointer speed.** Done (issue #17, merge `1d500bdb`). A speed
   setting from 10% to 200%, default 50%. A 16 ms tick with fraction
   accumulation. Holding RB gives 25% precision. A session row (Slow, Normal,
   Fast) in the in-game menu. The same speed applies to right-stick scrolling.
   The owner had reported that scrolling did not seem to work; the device check
   of 2026-10-17 found that right-stick scrolling reaches the PC on both of the
   owner's pads.
5. **Visual refinement pass.** Done. The icons, and the sizes of dialogs and
   panels, in the design system and in the Library, Settings, Privacy, Audit,
   Send to phone and stream screens. Merges `2ceb5744` (design system), `e44c9893`
   (Library), `b3e450b1` (Settings, Privacy, Audit, Send to phone and stream
   screens) and `fe6273c7` (one window-settling helper for the dialog
   screenshots). The TV was checked on build `fe6273c7`. The handheld was not
   checked on a dedicated device. The S22 in landscape was checked as a handheld
   after item 7, with a Bluetooth pad. See the
   [device validation runbook, section 12](development/device-validation.md#12-results-sprint-7).
6. **A held key does not act in a menu, sheet or dialog.** Done. Found by the
   owner during the sprint. A key that is already down when the in-game menu, an
   action sheet, a choice sheet or a dialog opens does not act in it until it is
   let go. Its repeats and its release are consumed, and a fresh press works.
   System keys (volume, media, channel, mute, headset) are never consumed. The
   owner found it by holding OK on an app tile in the Library: the app actions
   sheet opened, and the release ran its focused action. The owner then found a
   second case: holding OK on the Open button of a host card. The host card is
   now one focus stop with no action button, so that case is gone too (merge
   `4726c010`). Merges `70ed3eb3` (in-game menu), `60d5bfd5` (sheets and dialogs)
   and `4726c010` (host card).
7. **A connected game controller makes a wide window a handheld.** Done (merge
   `89b46c5b`, task commits `748f688c` and `dd3d383f`). Found in the device
   round. A window 600 dp or wider is a handheld while a game controller is
   connected, and the layout goes back to the phone layout when it disconnects.
   The docked keyboard follows the same rule at the stream start. A pad claimed
   by the app's USB driver is no Android input device while claimed. Both 8BitDo
   pads were probed over Bluetooth before the fix. The fix was checked on the S22
   in landscape with the Pro 3 over Bluetooth. See the
   [device validation runbook, section 12](development/device-validation.md#bluetooth-pad-round-and-handheld-fix-2026-10-17).

### Sprint 8: tidy-up

Merged as PR #20 (`68a8972a`).

The device
smoke round of [section 3.21](development/device-validation.md#321-sprint-8-checks)
ran on 2026-10-18 on the TV and the S22. Every item passed except the decoder
crash notice, which cannot be triggered. The results are in
[section 13](development/device-validation.md#13-results-sprint-8).

1. **Dead code, complexity hotspots and duplicated code.** Found with Memtrace
   and checked by hand. In Kotlin every Memtrace candidate had a caller and no
   exact duplicate was found. The dead code removed is the root flavor, the 84
   dead SDK branches, the capture providers minSdk 28 made unreachable,
   `HelpActivity`, unused box art and dialog code, and what the typed detekt
   analysis found. A full sweep of the Java methods moves to the migration.
2. **Root flavor.** Done (merge `48927cca`). The root flavor, its variants and
   the APK paths are removed. The tasks are `assembleDebug` and `assembleRelease`.
3. **Upstream leftovers.** Done (merge `599fed98` and commit `51a04274`). Removed:
   `appveyor.yml`, `store-assets`, the LuaScripts, fastlane and
   `apps/android/.github`. The decoder errata moved to the docs.
4. **Lint baseline.** Done (merge `cbded86b`). The baseline went from 159 to 43
   entries. The dead SDK branches (84 findings) are removed and the security
   entries are reviewed. `HelpActivity`, the Help button and its WebView are
   removed by the owner's decision, because they linked to Moonlight's wiki.
5. **Duplicated game controller rule.** Done (merge `e81583be`). The test of
   what counts as a game controller is one rule. Unifying it with
   `ControllerHandler.isGameControllerDevice` is open; see Sprint 15.
6. **Host parameter objects.** Done (merge `d9865d61`).
7. **Phone title bars.** Done (merge `8a6acbad`). The four phone title bars
   (Apps, Audit, Settings and Privacy) share one phone bar and one back button.
   The back arrows of Add host and Pairing use the same button. The gap below
   the arrow still differed; Sprint 9 fixed it (item 5).
8. **Kotlin suppressions.** Done (merge `543896a9`).
9. **Detekt type analysis.** Done (merge `65f6b3a9`, fix merges `933f368f`,
   `67cef8eb` and `b72163d3`, baselines removed in commit `50854eda`). Every
   Android module runs detekt with type analysis. The 116 findings are fixed,
   except that `InjectDispatcher` and `LongParameterList` are no longer reported
   in test sources (see Sprint 15). `MatchingDeclarationName` is off, because
   Compose files are named after the composable. `detekt.use.worker.api=true` is
   set.
10. **Memtrace rule.** Done (merge `599fed98` and commit `7f7b7721`). The index
    and the queries use the branch `main`. `watch_directory` is not used,
    because it labels changes with the checked-out branch. `memtrace start` runs
    its own watcher under that branch, so the `main` label is refreshed by the
    incremental index after each merge wave.

### Sprint 9: UX polish and the handheld

Merged as PR #32 (`109e7dff`).

The device round ran on 2026-10-19, with
[section 3.22](development/device-validation.md#322-sprint-9-checks)
as its checklist. Its results and the follow-up fixes are in
[section 14](development/device-validation.md#14-results-sprint-9). The final
re-check of the follow-ups passed on 2026-10-20.

1. **Smoother D-pad moves in Settings on a TV.** Done (issue #22, merges
   `8d6e6304`, `5e95a90e` and `1b6d651f`). Reported by the owner on 2026-10-17. A
   move between Settings groups took 120 to 400 ms on the Google TV Streamer.
   The first fix moved focus at once and let the pane follow after 150 ms, but it
   recreated the pane on each move and measured worse (254 to 464 ms), so it was
   replaced. The final design works like stacked tabs, on a TV and on a handheld:
   - the pane of each group is composed, measured and drawn once, and the panes
     are stacked;
   - a switch only changes which pane's layer is visible, with no wait, so focus
     and the pane change in the same frame, in about 50 ms;
   - the selected group is built first and the others one per frame, nearest
     first;
   - Privacy and diagnostics is composed only while it is selected;
   - hidden panes take no focus, touch or accessibility;
   - each group opens at its top, and a row depends only on its own value;
   - opening Settings from the rail puts focus on the selected group.

   The measurements are in
   [section 14](development/device-validation.md#14-results-sprint-9).
2. **The quit warning for Desktop.** Done (issue #23, merge `c7ddfb0b`). Reported
   by the owner on 2026-10-17. Desktop is the app titled "Desktop", ignoring case.
   Quitting it only ends the stream, so it says "Ends the stream." and "The display
   and controls go back to the PC." Any other app says "Closes `<app>` on the PC."
   and "Unsaved progress in it is lost." One sentence per line (owner decision).
   The texts are in the Library sheet and its confirmation, in the stream menu row
   and its confirmation, and in the conflict dialog. A confirmation carries only
   "Quit `<app>`?" in the title and the consequence in the body. Quit and start
   for Desktop says "Ends the stream first." and "Nothing is lost." Disconnect is
   unchanged.
3. **Host details text.** Done (issue #24, merge `a9fe85b5`). Noticed on
   2026-10-17. The Details dialog lists plain lines: name, status, address, other
   addresses, paired, MAC address for wake and running app. An unknown value
   leaves its line out, except the status, which reads "Checking…". An address
   shows its port only when it is not the default HTTP port. The UUID is gone,
   and so is the HTTPS port from these lines. The host capabilities block still
   follows them, with both ports.
4. **Handheld host card height.** Done (issue #25, merge `ecbf2236`).
   Noticed on 2026-10-17. On a handheld the host cards follow their content, as
   on a phone. The 176 dp minimum applies on the TV only.
5. **Phone bar gap.** Done (issue #26, merge `ecbf2236`). Found in
   Sprint 8. Every phone bar leaves 4 dp between the back arrow, the title and the
   actions, as the Material 3 top app bar does.
6. **Make the digits on the Borealis keyboard easier to discover.** Done (issue
   #27, merge `4131aa02`). Reported by the owner on 2026-10-18. The key that opens
   the Fn layer reads "Fn 123", so the digits are found. The layer is unchanged:
   the digits 1 to 0, F1 to F10, the arrows, Home, End, PgUp, PgDn and Del.
7. **Keyboards that count as game controllers.** Done (issue #28, merge
   `36f4534e`). Found in the Sprint 8 round. The shared controller rule and the
   stream's joystick check no longer accept a device that is only a joystick and
   reports an alphabetic keyboard. On the S22, the AULA F75Max reports KEYBOARD,
   DPAD, MOUSE and JOYSTICK sources with keyboard type 2 (alphabetic). The 8BitDo
   Ultimate 2 reports GAMEPAD with a non-alphabetic keyboard and still counts. The
   TV remote fallback is unchanged. On a touch device, the layout rule now counts
   only a connected game controller, so a keyboard alone does not make a handheld
   (`83a6aa66`, item 17).
8. **Steam opens after a quit combo.** Done (issue #29, merge `ea4df39e`). Found
   in the Sprint 8 round, on the S22. The menu chord arms only from a clean start:
   once a button other than Start and Select is held, Start and Select do not
   arm it again until every button is released. Letting the quit combo go bumpers
   first, which leaves Start and Select held alone, therefore sends no Guide tap
   and opens no menu, on system pads and on USB-driver pads. On a system pad, the
   Start hold no longer toggles the gamepad mouse while the quit combo is pending.
   See the
   [Sprint 8 results](development/device-validation.md#13-results-sprint-8).
9. **The animated background and the Appearance group.** Done (issue #30, merges
   `6437ab74`, `b9775c55` and `d1c36307`;
   [ADR 0056](adr/0056-add-an-animated-background-and-the-appearance-group.md)).
   The owner delivered it in this sprint, so it left the backlog.
   - One background sits behind every Library and Settings screen and never in a
     stream. The screens no longer paint their own page.
   - Seven styles: Aurora curtains (the default, the owner's choice), Soft glow,
     Northern lights, Light mist, Flowing light, Starfield and Off. The first two
     are drawn in Compose. The next four are looping clips played with Media3
     ExoPlayer on a SurfaceView (a TextureView since Sprint 10).
   - Reduced motion and the battery saver give a still frame and no player.
   - Navigating removes the old screen at once and fades the new one in.
   - Settings has a new Appearance group, after Advanced and before Privacy and
     diagnostics, with a Background row. The choice is `borealis_background` in the
     Borealis DataStore, as a stable id. The Library follows it live.
   - The provenance of the clips and the scripts that made them are in
     [design/backgrounds](../design/backgrounds/README.md). NOTICE lists Media3
     and Guava.
10. **Ten Settings groups fit the TV.** Done (issue #30, merge `d1c36307`). With
    Appearance the TV group list was about 70 dp too tall: Privacy and diagnostics
    was cut and About was off-screen. A group entry's vertical padding went from
    10 dp to 5 dp, with the focus outline gap unchanged at 10 dp. All ten groups
    fit a 960 x 540 dp TV window without scrolling. The Google TV Streamer shows
    nine, because it has no touchscreen and so no On-screen controls group. The
    handheld list may still scroll. Sprint 10 added Accessibility: there are
    eleven groups, ten on the Streamer, since Sprint 10, and they still fit a
    540 dp TV (see Sprint 10, item 11).
11. **The "preparing the display" text.** Done (issue #31, merge `75dbf7be`). Found
    during the sprint. The message on the stream's notification overlay predates
    the Sprint 5 Starting screen, which is opaque and covers the overlay from the
    connection start to the first frame. No code path showed it, so the message,
    its overlay plumbing and its string are removed. The Starting screen keeps its
    own "Preparing the display" step. The first-frame watchdog and the connection
    warnings are unchanged.
12. **A device round** for what Sprint 7 and Sprint 8 did not run, and for the
    Sprint 9 changes. Run on 2026-10-19; the final re-check passed on 2026-10-20
    ([section 14](development/device-validation.md#14-results-sprint-9)).
    - No dedicated Android handheld is planned. The owner's handheld is the S22 in
      landscape with a pad.
    - The Send to phone Expired and No network states.
    - The Gamepad mouse speed slider at 10% and 200%.
    - The quit combo on a USB-driver pad on the final build (done in the Sprint 8
      round, on the Sprint 8 build).
    - The dialog frames and visual items that the Sprint 7 round did not record
      (rows F15 and F16).
    - The Sprint 9 checks of
      [section 3.22](development/device-validation.md#322-sprint-9-checks).

    See the [device validation runbook, section 12](development/device-validation.md#12-results-sprint-7)
    for the Sprint 7 round. The results of this round go to
    [section 14](development/device-validation.md#14-results-sprint-9).
13. **Keyboard wraps at the end of a row.** Done (merge `39c4648c`). Found by the
    owner in the device round, on the S22 with a pad. Right on the last key of a
    row goes to its first key, and Left on the first key goes to its last, in both
    layers and in the bottom row. Up and Down do not wrap.
14. **Lint no longer fails for a newer version.** Done (merge `f2682e2c`). A fresh
    build failed lint on 2026-10-19, after Gradle 9.8.2 and kotlinx-serialization
    1.12.0 came out. `NewerVersionAvailable`, `GradleDependency`,
    `AndroidGradlePluginVersion` and `OutdatedLibrary` are off in both convention
    plugins. Dependabot reports new versions instead, except for AGP.
15. **A USB-driver pad navigates the in-game menu on a phone.** Done (merge
    `e3a29236`). Found in the device round on the S22. With the pad on the app's
    USB driver, Select and Start opened the menu, but no key moved its focus. The
    window stayed in touch mode, where Compose gives no clickable row focus. A pad
    key now leaves touch mode while the chrome is modal, and the first key acts, as
    on a TV. The owner keeps this.
16. **A keyboard alone does not make a phone a handheld.** Done (merge `83a6aa66`,
    issue #28; [ADR 0052](adr/0052-structure-the-phase-2-ui.md) is amended). Found
    in the device round on the S22 in landscape, with the AULA F75Max only. On a
    touch device only a connected game controller makes a wide window a handheld.
    This is the first symptom of item 7.
17. **The drawn backgrounds run at about 30 fps.** Done (merge `31dc1335`). Found
    in the device round: atrace on the Streamer at 60 Hz showed the Aurora curtains
    frames every 50 ms (20 fps). The loop now wakes before each vsync, so the rate
    is 30 fps at 30, 60, 90 and 120 Hz.
18. **The licence text scrolls with the D-pad on a TV.** Done (merge `3eed2dee`).
    Found in the device round on the Streamer: Up from Close never reached the
    licence text. `focusable()` now comes before `verticalScroll()`, and the text
    shows the focus outline ([ADR 0053](adr/0053-material-3-with-borealis-focus-on-tv.md)). Windows shorter
    than about 320 dp were a known limit, fixed in Sprint 10 (#44).
19. **The stream keyboard is decided when it opens.** Done (merge `5025614e`). Found
    in the device re-check on the S22: with a pad on the USB driver, or a system pad
    switched on after the stream started, the Keyboard of the in-game menu opened
    docked and out of the pad's reach. The type is now decided each time the
    keyboard opens (`StreamChromeHost.dockedKeyboard`), not at session start. A phone
    with no game controller connected docks the keyboard, and a pad held by the USB
    driver counts as connected. A phone with any pad gets the covering keyboard,
    also in portrait.
20. **Final re-check of the follow-ups.** Done on 2026-10-20, on `5025614e`, with
    the steps of
    [section 3.22, re-check after the follow-ups](development/device-validation.md#re-check-after-the-follow-ups):
    all items pass, and portrait with a pad is not applicable on the S22
    ([section 14, final re-check](development/device-validation.md#final-re-check)).

## Next

The 0.1.0 release comes after Sprint 13 and stays at the end of Phase 2
([ADR 0054](adr/0054-declare-independence-from-moonlight.md)), so Sprints 10
to 13 close Phase 2.

### Sprint 10: UX and accessibility

The work for this sprint is on the phase branch `phase/s10-ux-accessibility`,
which starts at `109e7dff`. Each item has an issue, #33 to #44. All twelve items
are done on the phase branch, and the phase pull request is not merged yet. The
decisions are in [ADR 0057](adr/0057-glass-surfaces-and-the-accessibility-settings.md).
The device round of 2026-10-21 ran on `8d5e8332`
([section 15](development/device-validation.md#15-results-sprint-10)).

1. **Text contrast over the animated background (WCAG 2.2).** Done (issue #33,
   merges `186ebff0`, `e8834bbc` and `8d5e8332`; the tool runs in CI since
   `5fe33a63`). A tool in `design/accessibility/` measures the contrast of every
   text token over real frames of the four clips and the two drawn styles, with
   the glass modelled. The worst case of each token counts. The baseline and the
   final numbers are in `docs/ux/accessibility.md`. The target is AA on every
   screen, and 7:1 with high contrast on. Before the sprint ink-3 and ink-4 fell
   to 3.0 to 3.7:1 over the brightest aurora bands.
   - Text that stands on the background has a halo (a stroke of 3 dp, a blur of
     2 dp, `page` at 80 %) and uses ink-2: the lowest result is 5.32:1, and
     14.70:1 under High contrast.
   - The bare icons share that halo: 5.71:1 at the worst, 14.79:1 under High
     contrast, against the 3:1 that they need.
   - The Add host field is a filled glass field with its own outline: label
     4.60:1, focused outline 3.88:1, error text 4.62:1. The switch from an
     outlined field waits for the owner's verdict.
   - CI runs the tests of the tool (the "Design tools" job), and a test fails
     when a clip changes without a new measurement.
2. **Glass panels and cards.** Done (issue #34, merges `07602b1a` and
   `3803117a`). Owner request of 2026-10-19: panels and cards become transparent
   with a heavy blur, so the background stays visible. The owner picked the look
   from a measured comparison of tint, blur, how the clips are handled, and the
   dialogs. Three transparency levels ship: Standard, Reduced and Off. Appearance
   has two more rows, Glass tint (30, 40, 50, 60, 70 or 85 %) and Glass blur
   (8, 16, 24, 32 or 48 dp), which set the Standard level. The glass also works
   outside the Library, because Sprint 11 uses it on the Starting screen.
3. **Accessibility group in Settings.** Done (issue #35, merges `cfe86b84` and
   `947fd5f6`). A new group after Appearance:
   - **Reduce motion:** Follow system, On or Off. It follows changes to the
     system animation scale while the app runs.
   - **Transparency:** Standard, Reduced or Off.
   - **Thicker focus outline:** a switch. On, the outline is 5 dp, in ink.
   - **Text size:** 100, 115 or 130 %, times the system scale.
   - **High contrast:** solid panels, text at 7:1 or more, opaque borders at
     3:1 or more, a dimmed background and the thicker outline. While it is on,
     it overrides only Transparency, Thicker focus outline, Glass tint and Glass
     blur, which are disabled with the reason. Text size, the palette and Reduce
     motion still apply.
   - **Colour-blind palette:** Off, Red-green or Blue-yellow, for the status
     colours. Every status also gets a shape or a text, not colour alone.

   The stream follows the text size, the focus outline, high contrast, the
   palette and reduce motion, read once when the stream starts. The stream gets
   no glass, because the video is behind it.
4. **TalkBack.** Done (issue #36, merge `c6e4b92a`). The app works with
   TalkBack on a phone and on a TV:
   - labels, headings and traversal order;
   - accessibility checks in the Robolectric tests;
   - font scale 2.0 tests;
   - 48 dp touch targets on phones and handhelds. A TV keeps its sizes.

   Three trade-offs of the 48 dp targets are pending the owner's review:
   - on a handheld the Settings group list now scrolls (about 6 of the 11 groups
     show at once), because its entries are 48 dp;
   - the docked phone keyboard is 24 dp taller, because its shortcut chips have
     48 dp cells;
   - the keys of the phone keyboard stay under 48 dp, which is an accepted
     finding of the checks.
5. **Phone portrait Settings.** Done (issue #37, merge `787b1aab`). Collapsible
   sections (option A, owner decision of 2026-10-20):
   - all sections start collapsed, and several can be open;
   - the state survives rotation;
   - About becomes a section, and Privacy and diagnostics stays a row that
     navigates.
6. **OK on a Settings group enters its pane.** Done (issue #38, merge
   `73c76844`). On a TV or a handheld, OK, Enter, the numpad Enter and gamepad
   A on a group move the focus to the first option of its pane, in keyboard
   input mode. Before, only Right did. Reported by the owner on 2026-10-20.
7. **Notices.** Done (issue #39, merge `3a419f4e`).
   - Snackbars are as wide as their text and centred, not full width (owner
     request of 2026-10-19).
   - After a Quit in the stream, the Library shows the same notice as a Quit
     from Apps, "App quit.". This answers the request that "Quitting `<app>`…"
     is too quick to read.
8. **Rail footer.** Done (issue #40, merge `2dc9933d`). The app logo, name and
   version at the bottom of the rail, on a TV and a handheld. Owner request of
   2026-10-20.
9. **About fixes.** Done (issue #41, merge `1fc7fc0c`). The About links open in a
   new task, so Back returns to the app. The Version row loses its chevron,
   which opened nothing.
10. **Focus after Back from Apps.** Done (issue #42, merge `b0faf022`). Focus
    goes to the host card that was opened, not to the first one. Hosts keeps the
    card by key, a Continue card too, and the first card gets the focus again
    only when the host is gone.
11. **Design system cleanup.** Done (issue #43, merge `3803117a`). The copied
    spacing constants move to `BorealisDimens`, `FocusProperties.enter` is
    replaced, and the background has one default. The Settings group padding is
    4 dp, so the 11 groups fit a 540 dp TV (10 on the Streamer).
12. **Keyboard reach in short windows.** Done (issue #44, merge `1fc7fc0c`). The
    licence text stays above Close in short windows and scrolls with the D-pad.
    The Audit detail gets the `focusable()` before `verticalScroll()` order.

### Sprint 11: Appearance

Planned, not started.

- **Fonts:** at least five. A survey picks fonts that help other needs, such
  as dyslexia. Atkinson Hyperlegible is one of them. The survey checks the
  licence and the APK size of each.
- **Accent colour:** purple, green or white, the Borealis colours.
- **Card density:** Comfortable or Compact.
- **Background intensity.**
- **Background during the stream start:** the animated background also shows on
  the Starting screen, with the step text on a glass panel. It stops before the
  first frame. This changes the Sprint 9 rule that the background is never in a
  stream.
  - Risk: on the TV, a background clip and the stream decoder compete for the
    hardware decoder.

### Sprint 12: SecOps

Owner decision of 2026-10-20: a whole sprint on security comes before the
release. Sprint 12 is hardening and security testing. It takes the backlog item
on the `RiskyLibrary` lint check (see
[Backlog, not scheduled](#backlog-not-scheduled)).

### Sprint 13: release preparation

- The root and Android READMEs.
- An install (Obtainium) and pairing guide.
- The compatibility note. It cites LuminalShine 26.10.x once that is stable.
- Show `LuminalShineVersion` from the HTTPS `/serverinfo` in Host details. The
  26.10.0-beta.1 host sends it (#199), and the client still shows the protocol
  version `7.1.431.-1`.
- [PRIVACY.md](../PRIVACY.md).
- The release certificate fingerprint.
- [CONTRIBUTING.md](../CONTRIBUTING.md) and the release docs.
- Curated release notes.
- The dry run on the tag commit, and the release smoke test. See the
  [release procedure](development/release.md).
- **Document the native pad modes.** As part of the install and pairing guide.
  The Pro 3 in Switch mode and the Ultimate 2 in D-input, for the 8BitDo pads.
  See Sprint 7, item 3. On the S22, the Ultimate 2 over Bluetooth LE shows as
  `2dc8:6012`, and the Pro 3 over classic Bluetooth as `057e:2009`. See the
  [Bluetooth pad round](development/device-validation.md#bluetooth-pad-round-and-handheld-fix-2026-10-17).
- **Core CI job.** Move the job back to `ubuntu-latest` after the clang-tidy
  findings in `core/src/pair.cpp` are fixed. The job is pinned to
  `ubuntu-24.04` in `.github/workflows/ci.yml`.
- **Use cases for behaviour that has none.** Owner decision of 2026-10-18. Write
  use cases for:
  - wake and forget a host;
  - pinned host and app shortcuts;
  - the gamepad mouse;
  - the quit combo;
  - the handheld layout.

### First release: `android-v0.1.0`

The owner creates the tag. See the [release procedure](development/release.md).

### Sprint 14: translation and opt-in crash reporting

- **Translation sprint.** The app is English only until this sprint
  ([ADR 0055](adr/0055-settings-storage-and-the-phase-2-settings-screen.md)).
- **Opt-in crash reporting.** Planned, not implemented. See
  [PRIVACY.md](../PRIVACY.md) and [ADR 0022](adr/0022-crash-reporting-and-privacy.md).

### Sprint 15 onward: Java to Kotlin migration

Owner decisions of 2026-10-17 and 2026-10-18. The migration moves the inherited
Java to Kotlin, one area at a time.

- **Why:** one language, null safety, and the static analysis covering all of
  the code.
- **Method:** one area at a time. Write characterization tests before the
  conversion of each area, and run device rounds for it.
- **Start:** after `android-v0.1.0`.
- **Order:**
  1. HTTP and computers come first, because Phase 3 uses them.
  2. Input and video come before Phase 4 (PyroWave).
  3. `MoonBridge` comes last.
- **Dead code:** a full sweep of the Java methods for dead code happens during
  the migration. Sprint 8, item 1 hands it over.
- **Size:** 80 Java files and about 21,600 lines under
  `apps/android/app/src/main/java` on 2026-10-18, almost all of it the stream
  core.
- **Upstream cherry-picks:** an area that is converted no longer takes upstream
  cherry-picks directly. Its fixes are ported by hand. See
  [upstream cherry-picks](development/upstream-sync.md).
- **Detekt tests:** turn the detekt rules `InjectDispatcher` and
  `LongParameterList` back on in test sources, and standardize the tests. Inject
  the dispatchers in the tests and reduce the test builders. This is an explicit
  owner request.
- **Game controller rule:** unify the game controller rule with
  `ControllerHandler.isGameControllerDevice`, possibly in a neutral input module.
  Today `:app` depends on `:borealis:designsystem` for it. `ControllerHandlerRig`
  can then use the shared `InputDevices` fixture.

## Later phases

- **Phase 3: LuminalShine REST features.** Several documents mark these as
  Phase 3. See the [Phase 3 extension points](ux/phase-2-proposal.md#phase-3-extension-points).
  The phase starts with a survey of what is unique to LuminalShine and could
  become a Borealis feature, ranked by value.
  - **Why:** the [host contract](host-contract/luminalshine.md) maps LuminalShine
    for compatibility, that is, what the client must do. It does not cover new
    features.
  - **Sources:** the REST API, the per-client settings, the per-client virtual
    display, the session monitor, the codec options, and what changed after the
    analysed release.
  - **Result:** the survey sets the scope of Phase 3 before any code is written.
    Owner's decision of 2026-10-17.
  - **Box art:** the Library screens show no box art until Phase 3, although
    the app already caches it for pinned shortcut icons and the TV channel. The
    Apps screen shows the initials of each app. App covers are one of the
    [Phase 3 extension points](ux/phase-2-proposal.md#phase-3-extension-points).
- **Phase 4: PyroWave.** See the [host contract](host-contract/pyrowave.md).
  The official LuminalShine build ships experimental PyroWave, opt-in and
  needing Vulkan 1.3, in 26.10.0-beta.1 (according to its release notes).
- **Phase 5: stores.** Google Play and Amazon Appstore. See the
  [release policy](policies/release.md) and
  [ADR 0045](adr/0045-store-publishing-automation.md), which is proposed.
- **Phase 6: Windows client.** Post-MVP. Most PC handhelds run Windows. Its order
  relative to Phases 3 to 5 is not decided.

## Backlog, not scheduled

- **Deferred RTSP parser rework.** The moonlight-common-c rework `aeba988` is
  deferred by the owner. See the [third-party notes](../core/third_party/README.md).

From the Sprint 9 quality review. None of these changes behaviour. Sprint 10
(item 11) took the deprecated `FocusProperties.enter`, the copied spacing
constants and the two background defaults. The rest is not scheduled yet.

- **Quit wording in three places.** The choice between the Desktop text and the
  game text is made in the Library action sheet (`AppActionsSheet.kt`), the
  stream menu (`QuitConfirmation.kt`) and the conflict dialog
  (`ConflictDialog.kt`). The strings repeat across `strings_apps.xml`,
  `strings_menu.xml` and `strings_conflict.xml`, and the Library stores a flag
  while the stream decides from the name of the app.
- **`MenuChordDetector.update`** (`borealis/stream/.../input/MenuChordDetector.kt`).
  Cyclomatic complexity 13 and cognitive complexity 18, seven mutable fields, a
  repeated expression, and an order dependency with no comment.
- **Host details.** `ComputerDetails.toDetailsFacts` in `ComputerHostRepository.kt` (in `:app`) maps the
  whole `Host` with `toHost()` to read one name, the running app. It holds a
  third copy of the online, offline and unknown mapping, and the code has three
  definitions of "no MAC address".
- **Test size and duplication.** The Sprint 9 tests run about 1,160 lines
  against about 590 lines of implementation, with repeated group lists and
  helpers. They belong in shared test support (the `testFixtures` of
  `:borealis:designsystem`).

From the Sprint 9 device round, 2026-10-19. Sprint 10 took the Back from Apps
focus, the About Version row and links (items 9 and 10), and the licence text
and the `AuditScreen.kt` `Detail` order (item 12). The rest is not scheduled
yet.

- Update Gradle 9.8.1 to 9.8.2 and kotlinx-serialization 1.11.0 to 1.12.0.
  Dependabot should open the two updates.
- AGP has no new-version notice. Dependabot ignores it until it has a single
  source, and the lint check is off.
- When the task root is not the launcher intent (for example a pinned shortcut
  opened first), opening the app from the launcher stacks a new Library instance.
  `LibraryActivity` has the standard launch mode; only `Game` is `singleTask`.
  Seen on the Streamer after a start with `am start -n`, whose intent has no
  action or category.
- A touch handheld whose built-in controls report only `KEYBOARD | DPAD`, with no
  GAMEPAD source, now gets the phone layout. This is rare; watch for it.
- On a narrow window with a pad (a phone in split screen), the stream keyboard
  covers the stream but keeps the TV shortcut row, where Esc is the 7th of 8
  chips. `StreamKeyboard.kt` picks the Esc-first row by `docked`, not by width
  (owner, 2026-10-20: backlog).
- Sprint 12 (SecOps): the lint check `RiskyLibrary` stays on, as a security
  alert, although it reads the Google Play SDK Index and so can fail a build with
  no code change, like the version checks that were turned off (owner,
  2026-10-20). Review it in Sprint 12, before the release.

From the Sprint 10 reviews and its device round (2026-10-21). None is scheduled
yet.

- **A `RailFooter` screenshot with a version string.** The references show the
  footer without one.
- **The mark paths are listed twice** in `design/icon/generate.py`.
- **Indeterminate progress indicators ignore Reduce motion.** `QuittingScreen`,
  `AddHostScreen` and `PrivacyPane` draw Material's indicators, which do not ask
  `rememberAnimationsRemoved`.
- **Segments and keyboard keys still lift when focused.** `SegmentedControl` and
  `KeyboardKey` use `liftedWhenFocused`, so the merged tree lists the focused
  one last. Robolectric cannot see TalkBack's traversal links: check it with
  TalkBack on a device.
- **Joined strings for the translation sprint (Sprint 14).** The `groupLabel` of
  `SegmentedControl`, the `HostCard` sentence and the description of a text
  field join their parts with ", " and ". ".
- **No ATF check covers a visible snackbar.**
- **The halo and the tint-only glass have not been seen on API 28 to 30.** No
  device is available; the tests draw them with Robolectric.
- **A UAT note:** "Fn 123" wraps on narrow phones at the default look.
- **Re-verify the 26.10.0-beta.1 host changes in the source,** and capture a
  fixture folder for them. See the [host contract](host-contract/README.md).
- **Quit takes about 17 s on 26.10.0-beta.1.** "Quitting `<app>`…" stays on
  screen until `/cancel` answers, about 17 s, against about 7 s on 26.09.1. It
  looks host-side. Measure it a second time, then report it upstream.
- **A Quit from the user logs an error.** The user's own Quit logs
  `E Borealis.UPSTREAM: Connection terminated: -1`, and the system logs
  `Duplicate finish request` for `com.limelight.Game`.
- **Moving between Settings groups is slower on the Streamer.** With the glass
  at Standard it measured 64 ms at the median (72 ms at the most), against 47 to
  60 ms in Sprint 9. A frame at rest is about 9 ms longer with the blur.
- **The Streamer's only web handler is the third-party Downloader app,** which
  keeps Back after an About link. It is a limit of that environment, not a bug
  of the app.

## Host-side items (LuminalShine)

These are proposals and known issues in the LuminalShine host. The client works
without them. The states are as of 2026-10-20. #199, #200 and #203 were merged
on 2026-10-20, and 26.10.0-beta.1 (a prerelease of 2026-10-20) ships them,
according to its release notes. #194 and #202 are fixed on the host. The open
ones are #198, #201, #204 and #205.

| Item | Kind | Summary |
| --- | --- | --- |
| [NortheBridge/luminalshine#194](https://github.com/NortheBridge/luminalshine/issues/194) | Issue | Stray bytes after a 404 response. Fixed on the host by #200, in 26.10.0-beta.1. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#198](https://github.com/NortheBridge/luminalshine/issues/198) | Issue | Teardown hang after a disconnect. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#199](https://github.com/NortheBridge/luminalshine/pull/199) | PR | Reports the LuminalShine release in `/serverinfo`, so the client can show the host version. Shipped in 26.10.0-beta.1. The client shows it in Sprint 13. |
| [NortheBridge/luminalshine#200](https://github.com/NortheBridge/luminalshine/pull/200) | PR | Sends a single response for unknown GameStream paths. Fix for #194. Shipped in 26.10.0-beta.1. |
| [NortheBridge/luminalshine#201](https://github.com/NortheBridge/luminalshine/issues/201) | Issue | A second client leaving a shared app ends the first client's session (per-client virtual display). See [UC-003](use-cases/0003-join-or-replace-a-running-app.md). |
| [NortheBridge/luminalshine#202](https://github.com/NortheBridge/luminalshine/issues/202) | Issue | A long `/launch` blocks every other HTTPS GameStream request until it finishes. Fixed on the host by #203, in 26.10.0-beta.1. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#203](https://github.com/NortheBridge/luminalshine/pull/203) | PR | Fix for #202. Merged on 2026-10-20 and shipped in 26.10.0-beta.1. |
| [NortheBridge/luminalshine#204](https://github.com/NortheBridge/luminalshine/issues/204) | Issue | The host process crashes after the virtual display cleanup of an ended session. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#205](https://github.com/NortheBridge/luminalshine/issues/205) | Issue | A second client's resume fails the RTSP tag check while the first client's launch is still setting up. See the [host contract](host-contract/luminalshine.md). |
