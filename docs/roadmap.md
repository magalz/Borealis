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
- **Phase 2, Sprints 2 to 10 (PRs #9 to #13, #19, #20, #32 and #45):**
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
  - Sprint 10 (PR #45): UX and accessibility. See the
    [Sprint 10 items](#sprint-10-ux-and-accessibility).

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
   planned for Sprint 20. See the
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
   `ControllerHandler.isGameControllerDevice` was settled in Sprint 13 as two named rules
   side by side in `ConnectedGamepads.kt`, with `ControllerHandler.isGameControllerDevice`
   delegating to `isStreamControllerDevice`.
6. **Host parameter objects.** Done (merge `d9865d61`).
7. **Phone title bars.** Done (merge `8a6acbad`). The four phone title bars
   (Apps, Audit, Settings and Privacy) share one phone bar and one back button.
   The back arrows of Add host and Pairing use the same button. The gap below
   the arrow still differed; Sprint 9 fixed it (item 5).
8. **Kotlin suppressions.** Done (merge `543896a9`).
9. **Detekt type analysis.** Done (merge `65f6b3a9`, fix merges `933f368f`,
   `67cef8eb` and `b72163d3`, baselines removed in commit `50854eda`). Every
   Android module runs detekt with type analysis. The 116 findings are fixed,
   except that `InjectDispatcher` and `LongParameterList` were not reported
   in test sources until Sprint 14 turned them back on (see Sprint 14).
   `MatchingDeclarationName` is off, because
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

### Sprint 10: UX and accessibility

Merged as PR #45 (`7e300e53`) on 2026-10-21. Each item had an issue, #33 to #44,
closed by the PR. The decisions are in
[ADR 0057](adr/0057-glass-surfaces-and-the-accessibility-settings.md).
The device round of 2026-10-21 ran on `8d5e8332`, and the owner's user
acceptance test (UAT) of the same day ran on `20861a28`
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
     4.60:1, focused outline 3.88:1, error text 4.62:1. The owner accepted the
     filled field at the Sprint 10 UAT (2026-10-21), so the switch is decided.
   - CI runs the tests of the tool (the "Design tools" job), and a test fails
     when a clip changes without a new measurement.
2. **Glass panels and cards.** Done (issue #34, merges `07602b1a` and
   `3803117a`). Owner request of 2026-10-19: panels and cards become transparent
   with a heavy blur, so the background stays visible. The owner picked the look
   from a measured comparison of tint, blur, how the clips are handled, and the
   dialogs. Three transparency levels ship: Standard, Reduced and Off. Appearance
   has two more rows, Glass tint (30, 40, 50, 60, 70 or 85 %) and Glass blur
   (8, 16, 24, 32 or 48 dp), which set the Standard level. The glass also works
   outside the Library, because Sprint 15 uses it on the Starting screen.
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
   palette and reduce motion, read once when the stream starts. The stream got
   no glass then, because the video is behind it; Sprint 15 gives the menu and
   the popups of the stream glass from a copy of the video
   ([ADR 0061](adr/0061-glass-and-the-animated-background-in-the-stream.md)).
4. **TalkBack.** Done (issue #36, merge `c6e4b92a`). The app works with
   TalkBack on a phone and on a TV:
   - labels, headings and traversal order;
   - accessibility checks in the Robolectric tests;
   - font scale 2.0 tests;
   - 48 dp touch targets on phones and handhelds. A TV keeps its sizes.

   The owner accepted these trade-offs of the 48 dp targets as they are, at the
   Sprint 10 UAT (2026-10-21):
   - on a handheld the Settings group list now scrolls, because its entries are
     48 dp. At 800 × 480 dp (the `settingsscreen_handheld_stream.png` reference)
     6 of the 11 groups show (Stream to Audio) and the other 5 need scrolling.
     In a window 360 dp tall, such as a phone in landscape with a pad, about 4
     show.
   - the docked phone keyboard is 24 dp taller, because its shortcut chips have
     48 dp cells;
   - the keys of the phone keyboard stay under 48 dp, which is an accepted
     finding of the checks;
   - phone buttons stay 40 dp and menu rows stay 44 dp.
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

## Next

The 0.1.0 release comes after Sprint 20 and stays at the end of Phase 2
([ADR 0054](adr/0054-declare-independence-from-moonlight.md)), so Sprints 11 to 20
close Phase 2.

### Order of the coming sprints (owner decisions of 2026-10-21 and 2026-10-28)

The owner asked for the order that gives the least rework with the most safety,
with any number of sprints. The phase pull requests stay small and easy to test.

| Sprint | Scope |
| --- | --- |
| 11 | Appearance |
| 12 | Kotlin migration 1: network, host and support |
| 13 | Kotlin migration 2: input |
| 14 | Kotlin migration 3: video, audio and the stream activity |
| 15 | The stream screens and glass |
| 16 | Fixes of the converted code |
| 17 | General fixes |
| 18 | Refactor of the converted code |
| 19 | SecOps |
| 20 | Release preparation |
| — | `android-v0.1.0` |
| 21 | Translation and opt-in crash reporting |

Owner decisions of 2026-10-28: three sprints come before SecOps, the fixes of the converted
code (Sprint 16), the general fixes (Sprint 17) and the refactor of the converted code
(Sprint 18).

- On 2026-10-27 the owner decided that the conversion defects are fixed after Sprint 14,
  and no sprint held them. The refactor of the marked places had no sprint either.
- The fixes and the refactor come before SecOps, so that SecOps audits the final code, as
  the order already says.
- The fixes come first. Their triage removes dead and unreachable paths, which shrinks the
  refactor, and the refactor then does not have to keep known defects on purpose.
- Later the same day the owner asked for one more sprint before SecOps, only for general
  fixes, and placed it after the conversion fixes. It takes what is not a conversion item and
  does not fit one of the other planned sprints squarely. It sits before the refactor so that
  every fix comes before it, for the same reason as above: the refactor does not keep known
  defects.
- The release moves three sprints later. Phase 2 now closes with Sprints 11 to 20.
- The renumbering: before 2026-10-28 SecOps was Sprint 16, release preparation was Sprint 17,
  and translation and opt-in crash reporting was Sprint 18. Sprints 15 and earlier keep their
  numbers.

Why this order:

- Appearance comes first. It continues the design-system work of Sprint 10 (the
  glass, the background, the contrast tool) and touches no Java.
- The migration comes before SecOps, so the security sprint audits the code that
  ships, with the static analysis covering all of it. It goes from the most
  testable area (the network and host code, mostly logic with recorded host
  responses) to the one that depends most on devices (video and the stream
  activity).
- A conversion keeps behaviour. Fixes and features in converted code wait for a
  later sprint, so a device round can tell a conversion regression from a feature
  change.
- The stream screens come after the migration, so their changes to `Game`, the
  decoder and the stream start are written once, in Kotlin, and the conversion is
  checked on the devices before they change.
- SecOps comes after the last feature sprint, and since 2026-10-28 after the fixes of the
  converted code, the general fixes and the refactor, so it audits the final code. Release
  preparation follows it.
- The three areas are independent. If a migration sprint's device round shows
  problems, the remaining migration sprints move after the release and the rest of
  the order stays.

### Sprint 11: Appearance

Merged as pull requests #55 (2026-10-22) and #56 (2026-10-23). Owner decisions of 2026-10-21. Every font in the font
choice is there for an accessibility reason, from a survey of primary sources
that [ADR 0058](adr/0058-appearance-and-reading-options.md) records.

- **TV text size.** Done (issue #47, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 1). On the TV, body text goes from 15 to 16 sp, and the minimum
  text (small labels and section kickers) from 12 to 13 sp. The secondary text,
  at the rail size, stays 14.5 sp. About 16 sp is the suggested minimum for text
  read at 3 m. The handheld keeps its scale. The TV screenshot references are
  recorded again.
- **Font choice.** Done (issue #48, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 2). Replaces the old "Fonts" item.
  - Each font has a one-line purpose under its name in the picker, and the name
    is drawn in its own font.
  - The codes (the PIN, addresses, chips, the performance overlay, the adb command
    and the licence text) use the font's own monospaced companion where one exists,
    and IBM Plex Mono otherwise. The key shortcut chips use the codes font, and the
    on-screen keyboard keys use the text font.
  - The candidates, confirmed at the gate after the screens are drawn:
    - IBM Plex Sans, the default, with IBM Plex Mono;
    - Atkinson Hyperlegible Next, for low vision: letters that look alike are
      drawn to differ. With Atkinson Hyperlegible Mono;
    - Luciole, made with readers with low vision and tested in a published
      study. CC BY 4.0;
    - Lexend, with wider spacing built into the letters;
    - Andika, for new readers: mirror letters such as b and d look different;
    - B612, made for cockpit screens: quick to read at a glance. With B612 Mono;
    - OpenDyslexic was considered and not offered (ADR 0058, decision 3);
    - the system font.
  - All fonts are bundled in the APK, from their official releases, with no
    subsetting. The survey checks the licence and the size of each.
- **Text spacing.** Done (issue #49, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 4). A new row in the Accessibility group: Normal, Wide or Wider.
  It widens the spacing between letters, between words and between lines, after
  WCAG 1.4.12. More space between letters and words together is what helped
  readers with dyslexia in controlled studies; fonts made for dyslexia did not.
  A spike first checked how Compose can widen word spacing.
  At a font scale of 2.0 Wider is drawn as Wide, and a dialog and a line in the row say so.
- **Accent colour:** Done (issue #50, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 5). The app offers violet, teal and white. Green was measured and not offered.
  The drawn backgrounds stop taking their violets from the accent, so the glass table stays
  valid. Selected labels on glass keep 4.5:1. Before Sprint 11 the selected segment,
  chip and rail item drew their label in `accent`, which was 3.21:1 over Flowing
  light and 3.62:1 over Northern lights as text ([accessibility, Table S](ux/accessibility.md)); they
  now use `accent-soft` ([accessibility, Table W](ux/accessibility.md)).
- **Card density:** Done (issue #51, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 7). Comfortable, Compact or Dense: one column more in Compact and Dense, and less
  card padding in Dense.
- **Background intensity.** Done (issue #52, [ADR 0058](adr/0058-appearance-and-reading-options.md),
  decision 8). A page-coloured veil under the glass, at 0, 25, 50 or 75 %. It never
  raises the brightest pixel, so the glass floors stay valid.
- **About links on the TV show the link in Borealis.** Done (issue #53,
  [ADR 0058](adr/0058-appearance-and-reading-options.md), decision 9). Added at the Sprint 10 UAT
  (2026-10-21). A dialog with the URL and a QR code, instead of opening another
  app. ZXing is already in the project for Send to phone. See the finding on the
  Streamer in
  [device validation, section 15](development/device-validation.md#owner-checks-uat-2026-10-21).
- **The licences screen shows the font licences.** Done (issue #48). The licences dialog
  shows the licence of every font. Before, `OFL.txt` shipped in the APK, but the dialog
  showed only NOTICE and LICENSE (`LegalTexts.kt`).
- **A `RailFooter` screenshot with a version string.** Done (issue #54). The references show the
  footer without one.
- **The mark paths are listed twice** in `design/icon/generate.py`. Done (issue #54).
- **No ATF check covers a visible snackbar.** Done (issue #54): an ATF check of Hosts
  with a snackbar.

The last three items come from the Sprint 10 reviews. They fit here because
Sprint 11 records the screenshot references again.

### Kotlin migration (Sprints 12 to 14)

Owner decisions of 2026-10-17, 2026-10-18 and 2026-10-21. The migration moves the
inherited Java to Kotlin before SecOps, one area at a time, in three sprints.

- **Why:** one language, null safety, the static analysis covering all of the
  code, and SecOps auditing the code that ships.
- **Method:** one area per sprint, so one phase pull request per area.
  Characterization tests pin the current behaviour before each area is converted,
  and each sprint has a device round for its area.
- **Behaviour:** a conversion keeps behaviour. Fixes and features in converted
  code go to a later sprint.
- **Names:** the `com.limelight` package, the `com.limelight.nvstream.jni.MoonBridge`
  class and every native method signature stay, because the JNI symbols depend on
  them. Converted code kept a Java-friendly API (`@JvmStatic`, `@JvmField`,
  `@JvmOverloads`) for as long as Java called it. Sprint 14 converted the last Java
  caller, so no Java is left. The annotations are still in the code (see the Sprint 14
  backlog, item (a)). `MoonBridge` keeps its static natives and callbacks, which the C code
  finds by name, and its `@JvmStatic` and `@JvmField` members, because a Robolectric shadow
  replaces its static initializer
  ([ADR 0060](adr/0060-convert-moonbridge-to-a-kotlin-object.md)).
- **ADR:** [ADR 0059](adr/0059-convert-the-inherited-java-to-kotlin.md) records the conversion
  rules. It amends [ADR 0004](adr/0004-kotlin-for-new-android-code.md).
- **Dead code:** the full sweep of the Java methods for dead code happens area by
  area. Sprint 8, item 1 hands it over.
- **Size:** 33 Java files and 14,214 lines under `apps/android/app/src/main/java`
  after Sprint 12, almost all of it the input, video and `Game` code of Sprints 13
  and 14. Before Sprint 12 it was 80 Java files and 21,539 lines (2026-10-21).
  Sprint 13 converted the input code: 26 Java files and 7,982 lines. After Sprint 13 it was
  7 Java files and 6,232 lines (`git ls-tree` and `wc -l` at `895d0419a`): `Game.java`
  (2,600), `binding/video` (2,985 in four files, with `MediaCodecDecoderRenderer` at 1,876),
  `binding/audio` (216), `nvstream/jni/MoonBridge.java` (431). All of it was Sprint 14, which
  converted it: no Java file is left (`git ls-files '*.java'` lists nothing), and
  `apps/android/app/src/main/java` no longer exists.
- **Upstream cherry-picks:** an area that is converted no longer takes upstream
  cherry-picks directly. Its fixes are ported by hand. Since Sprint 14 that is every
  area of the app. See [upstream cherry-picks](development/upstream-sync.md).
- **If a round shows problems:** the remaining migration sprints move after the
  release. The rest of the order stays.

### Sprint 12: Kotlin migration, network, host and support

Converted in the phase branch `phase/s12-kotlin-network`, one merged task per area. Every
conversion is merged; the device round is pending. Owner decisions of 2026-10-23, recorded in
[ADR 0059](adr/0059-convert-the-inherited-java-to-kotlin.md):

- **H1, placement and history:** converted files move to `src/main/kotlin/com/limelight/...`
  in a rename-only commit, and the conversion follows in its own commit (ADR 0059, rule 1).
- **A1, static analysis:** the conversion is faithful. Mechanical detekt findings are fixed,
  structural rules are suppressed on the declaration with a backlog item, and there is no
  detekt baseline (ADR 0059, rule 6).
- **C1, coverage:** converted code counts in Kover coverage at once. The exclusion narrows to
  the Java that stays. Converted code is held to the same Codecov gate as the rest (90% of
  the patch): the separate statuses first planned were dropped once the converted code
  measured 97.0% line coverage at the end of the sprint. Each migration sprint is merged on
  its own (ADR 0059, rule 7). Sprint 14 took the exclusion away: no `com.limelight` class is
  left out of the Kover report, and no Java is left.
- **D1, dead code:** verified dead code is removed before conversion, each removal in its own
  commit with its evidence (ADR 0059, rule 8).
- **Behaviour:** a conversion keeps behaviour. Bugs found are reported and go to the backlog
  (ADR 0059, rule 10).

Reference source: the LuminalShine reference is now the owner's fork at `D:\Repos\luminalshine`.

Size: 47 files and 7,325 lines, under `apps/android/app/src/main/java`:

- network and host, 31 files and 5,402 lines: `nvstream/http` (`NvHTTP` and pairing, 1,467),
  `computers` (1,577), `nvstream/mdns` (728), `nvstream/wol`, `binding/crypto`,
  `discovery`, and `nvstream` (`NvConnection` and the stream configuration, 991),
  `nvstream/av` and `nvstream/input`;
- support, 16 files and 1,923 lines: `preferences` (`PreferenceConfiguration`, 599), `utils`
  (1,004), `ui`, `grid/assets`, `PosterContentProvider`, `LimeLog` and `binding`.

Outcome, by issue (each one a merged task of the phase):

- **Done, #57 tooling.** Kover counts the converted `com.limelight` code in coverage.
- **Done, #58 host contract.** The 26.10.0-beta.1 changes checked in the source, with
  their fixtures. See the [host contract](host-contract/README.md).
- **Done, #59 host model.** `ComputerDetails`, `NvApp`, `HostHttpResponseException` and
  `ComputerManagerListener`.
- **Done, #60 stream types.** The stream configuration, the renderer and the packet types.
- **Done, #61 discovery.** Host discovery and Wake-on-LAN.
- **Done, #62 preferences.** `PreferenceConfiguration` and `GlPreferences`.
- **Done, #63 crypto and pairing.** The client identity and pairing.
- **Done, #64 nvhttp.** The GameStream HTTP client.
- **Done, #65 computer store.** The host list and the client id. The three legacy
  database readers were removed.
- **Done, #66 support.** `LimeLog`, `utils`, `ui`, `DiskAssetLoader`, `PosterContentProvider`
  and `Vector2d`.
- **Done, #67 nvconnection.** The stream connection.
- **Done, #68 computer manager.** `ComputerManagerService`.

Removed, not converted:

- **Legacy database readers.** The three `LegacyDatabaseReader` classes (308 lines) read
  `computers.db`, `computers2.db` and `computers3.db`, the databases of older Moonlight
  versions under the `com.limelight` application id. Borealis's application id is
  `io.github.magalz.borealis`, so these files never exist in its data (ADR 0059, rule 8).
- **`ByteBufferDescriptor` and four unused helpers.** Verified unused and removed before
  conversion, each removal with its own commit (ADR 0059, rule 8): `LimeLog.setFileHandler`
  and the `UiHelper` methods `notifyNewRootView`, `applyStatusBarPadding` and
  `displayDeletePcConfirmationDialog`. The strings `yes`, `no` and `delete_pc_msg`, used only
  by the last one, went with it.

`Vector2d` was first reported unused, but `ControllerHandler` uses it. It was converted
instead of removed.

Items:

- **Done.** HTTP and computers came first, because Phase 3 uses them.
- **Done (#58).** Re-verify the 26.10.0-beta.1 host changes, moved from the backlog: the
  changes are checked in the source, and a fixture folder captures them. The
  characterization tests of the HTTP layer use these fixtures.
- **Pending.** The device round: discovery, Add host, pairing (including a wrong PIN on
  26.10.0-beta.1), Wake-on-LAN, Forget, the app list, pinned shortcuts and the TV
  channel, launch, resume and Quit. The results are in
  [device-validation section 17](development/device-validation.md#17-results-sprint-12);
  Forget and Wake-on-LAN are still the owner's.

### Sprint 13: Kotlin migration, input

Converted in the phase branch `phase/s13-kotlin-input`, one merged task per area. Every
conversion is merged; the device round is done. Owner decisions of 2026-10-25:

- **Characterize, then convert.** `ControllerHandler` (3,659 lines) was pinned first by
  three characterization tasks, for events, devices and feedback, which worked in parallel
  on separate test packages. One task then converted it with every test unchanged. Two
  reviewers read the conversion, one for each half of the Java file (lines 1 to 1,850, and
  1,850 to the end). The three characterization tasks ran 250 (events), 277 (devices) and
  333 (feedback) test runs, 860 in all. The input tests are in two namespaces: 1,123 in
  `io.github.magalz.borealis.input.*` (69 classes) and 511 in `com.limelight.binding.input.*`,
  1,634 in all (the phase review's run of both). The whole `:app` suite had 3,179 tests at
  `895d0419a`, in the phase review. These are test runs: a contract test that runs once per
  SDK subclass counts once for each run.
- **Controller rule.** Two named rules sit side by side in `ConnectedGamepads.kt`
  (`:borealis:designsystem`), with no behaviour change. `isGameController` says whether a
  device is a real pad. `isStreamControllerDevice` says whether `Game` offers a device's key
  events to the controller handler before the keyboard handler; it is broader on purpose.
  `ControllerHandler.isGameControllerDevice` delegates to `isStreamControllerDevice`.
- **Test temporary folders.** Each unit-test task gets a temporary folder of its own, and
  the task empties it before each run. The leftover folders of the mutation runs had filled
  the C: drive on 2026-10-25 (see [building](development/building.md)).

Size: 26 Java files and 7,982 lines under
`apps/android/app/src/main/java/com/limelight/binding/input`, moved by seven `chore: move`
commits, one per task, as rename-only commits. After the conversion they are 26 Kotlin files
and 8,399 lines under `apps/android/app/src/main/kotlin/com/limelight/binding/input`. By area,
in Java lines: `binding/input` (4,060, with `ControllerHandler` at 3,659 and
`KeyboardTranslator` at 401), `virtual_controller` (1,905, 10 files), `driver` (the USB
drivers, 1,173, 7 files), `touch` (591, 3 files) and `capture` (253, 4 files). No Java is
left under `binding/input`.

Outcome, by issue (each one a merged task of the phase):

- **Done, #73 keyboard and capture.** `KeyboardTranslator` and the mouse capture providers.
- **Done, #74 touch.** The absolute and relative touch contexts.
- **Done, #75 USB drivers.** The USB controller drivers and `UsbDriverService`.
- **Done, #76 on-screen controller.** The core (`VirtualController` and its configuration
  loader) and the elements (the sticks, the buttons, the pad and the triggers).
- **Done, #77 ControllerHandler.** Pinned by the three characterization tasks, then
  converted in one task.
- **Done, #78 controller rule.** The stream's rule, `isStreamControllerDevice`, next to
  `isGameController` (see above).

Removed, not converted: each removal has its own `refactor: remove` commit with its evidence
(ADR 0059, rule 8). Grouped by class:

- **`ControllerHandler`:** the `hasGameController` field, written twice and read nowhere.
- **`KeyboardTranslator`:** ten unused `VK_*` constants, and `getDeviceKeyCodeForQwertyKeyCode`,
  a method of the private `KeyboardMapping` that nothing calls. The `device` field of
  `KeyboardMapping` went with it, because only this method read it.
- **`InputCaptureProvider`:** `isCapturingEnabled`, which returns the flag that
  `isCapturingActive()` already returns.
- **`UsbDriverBinder`:** `stop`, which has no caller; the service stops itself.
- **`VirtualControllerConfigurationLoader`:** the unused private `getPercent`.
- **`VirtualController`:** the debug switch and its `_DBG` helper, with five calls that built
  a message that was then discarded.
- **`VirtualControllerElement`:** `showConfigurationDialog`, `setColors`, two commented-out
  colour choosers, and the debug flag `_PRINT_DEBUG_INFORMATION` with its output `_DBG` in the
  element classes (the eight debug calls of the stick, button and pad classes went with it).
- **`AnalogStick`:** the private `circle_stick` flag, which was never read, and three unused
  `SIZE_RADIUS` constants.
Test code, not a `refactor: remove` commit: `ShadowOscMoonBridge`, a test shadow of the
on-screen controller tests, was deleted in `a65a0bca0` (a `test:` commit), and those tests use
the shared MoonBridge shadow.

Lint: the lint baseline lost the 15 entries of the converted input Java (`b11ec77a6`, merged
in `f8c20d882`). The 27 entries left filter 11 `UnusedAttribute`, 3 `RtlHardcoded`, 2
`UseRequiresApi` (`Game.java` and `NsdManagerDiscoveryAgent.kt`), 2 `DefaultLocale` (both in
`MediaCodecHelper.java`) and one each of nine other ids. None is in `binding/input`. The seven `ViewConstructor` entries are no longer in the baseline: the views
carry `@SuppressLint("ViewConstructor")` in source.

Items:

- **Done.** The device round of 2026-10-26: both 8BitDo pads (the Pro 3 in Switch mode, the
  Ultimate 2 in D-input), the on-screen controller and touch on the S22, the keyboard and
  mouse, the gamepad mouse, the menu chord and the quit combo. The results are in
  [device-validation section 18](development/device-validation.md#18-results-sprint-13). The
  round found no Sprint 13 regression, and four items went to the backlog (item (d) under
  Backlog, not scheduled). The owner moved them to
  [Sprint 17](#sprint-17-general-fixes) on 2026-10-28.
- Phase 4 (PyroWave) uses input and video.

### Sprint 14: Kotlin migration, video, audio and the stream activity

Converted in the phase branch `phase/s14-kotlin-stream`, one merged task per area. Every
conversion is merged. The device round and the owner's UAT ran on 2026-10-28 and found no
Sprint 14 regression; the results are in
[device-validation section 19](development/device-validation.md#19-results-sprint-14). No inherited
Java is left: `git ls-files '*.java'` lists nothing, and `apps/android/app/src/main/java` no
longer exists.
Owner decisions of 2026-10-26 and 2026-10-27:

- **MoonBridge first.** [ADR 0060](adr/0060-convert-moonbridge-to-a-kotlin-object.md)
  (2026-10-26) amends rule 1.3 of ADR 0059. `MoonBridge` is converted first, as a Kotlin
  `object` that keeps its name, its package and every JNI signature, so `Game` and the
  renderer are converted against its final shape. A JNI contract test reads the C sources and
  the class file, and checks every exported native and every callback that the C code looks
  up. ASM 9.10.1 is a `testImplementation` of `:app` for that test (owner, 2026-10-26).
- **Characterize, remove dead code, move, convert.** Each area was pinned first by
  characterization tests that pass on the Java. The dead code that the owner approved was then
  removed, each removal in its own commit with its evidence (ADR 0059, rule 8). A rename-only
  `chore: move` commit and the `refactor: convert` commit followed (rule 1). Each suite killed
  60 to 65 mutants of the Java.
- **Game, in four tasks and one conversion.** `Game.java` (2,600 lines) was pinned by a
  Robolectric rig (`GameRig`, 143 tests) and by three characterization tasks, for the keys (79
  tests), the mouse, touch and pen path (129) and the session, display and window (188), which
  worked in parallel on separate test packages. They retired every assertion that read
  `Game.java` as text (33 old tests), so no test reads a `.java` file any more. One task then
  converted `Game` with every test unchanged. Two reviewers read the conversion, one for each
  half of the file.
- **Removals.** Each removal has its own commit with its evidence. The owner approved the
  removals in `MoonBridge`, the renderer and `VideoStats`, and accepted the log change below.
  On 2026-10-27 the owner decided that the dead code of the renderer and the `connecting` flag
  of `Game` go, and that if Borealis needs them later, it writes its own.
- **Coverage of `Game`.** The owner accepted the line coverage of `Game.kt` on 2026-10-27
  ("Accept; device round checks"). The device round adds the Samsung keyboard (meta key)
  capture on the S22, and picture-in-picture: enter and leave, the auto-enter, and the
  suppression while the USB permission prompt is open.
- **Defects wait.** The defects found are kept as they were, each pinned by a test. The owner
  decided on 2026-10-27 that they are fixed after Sprint 14, and on 2026-10-28 that they are
  fixed in [Sprint 16](#sprint-16-fixes-of-the-converted-code). See the Sprint 14 block under
  Backlog, not scheduled.
- **Test standardization.** The detekt rules `InjectDispatcher` and `LongParameterList` are on
  for every test source (owner request, planned since 2026-10-18). See issue #91 below.

Size: 7 Java files and 6,232 lines under `apps/android/app/src/main/java` at the start
(`git ls-tree` and `wc -l` at `b205ef3fc`): `Game.java` (2,600), `binding/video` (2,985 in four
files, with `MediaCodecDecoderRenderer` at 1,876 and `MediaCodecHelper` at 1,004),
`binding/audio` (216) and `nvstream/jni/MoonBridge.java` (431). After the removals the Java
was 6,015 lines (counted at the five `chore: move` commits, which moved the seven files as
rename-only commits). After the conversion they are 7 Kotlin files and 6,900 lines under
`apps/android/app/src/main/kotlin/com/limelight`: `Game.kt` (2,978),
`binding/video/MediaCodecDecoderRenderer.kt` (2,063), `binding/video/MediaCodecHelper.kt`
(1,006), `nvstream/jni/MoonBridge.kt` (528), `binding/audio/AndroidAudioRenderer.kt` (235),
`binding/video/VideoStats.kt` (85) and `binding/video/CrashListener.kt` (5).

Outcome, by issue (each one a merged task of the phase):

- **Done, #86 MoonBridge.** A Kotlin `object` whose non-private members are `@JvmStatic`,
  `@JvmField` or `const val`, with the natives as `@JvmStatic external fun` (ADR 0060). The JNI
  contract test, and tests of the static initializer, every callback and `AudioConfiguration`.
  `ShadowRecordingBridge` is the canonical test shadow: it covers every native.
- **Done, #87 MediaCodecHelper.** An `object`. 79 table-driven characterization tests pin the
  per-device decoder decisions. Each row loads a fresh copy of the class through a child-first
  class loader, so the class-load branches are pinned too.
- **Done, #88 AndroidAudioRenderer.** 55 characterization tests pin the channel masks, the four
  `AudioTrack` attempts and their skips, the return codes, the playback and the cleanup. The
  constructor takes a non-null `Context`, which its only caller passes.
- **Done, #89 video renderer.** `MediaCodecDecoderRenderer`, `VideoStats` and `CrashListener`.
  261 characterization tests pin the decoder choice, the setup, the bytes queued (the H.264
  SPS is checked against an independent bit-writer oracle), the stats, the recovery, the crash
  text and the threads, through a recording `MediaCodec` shadow. One review, one fix round.
- **Done, #90 Game.** The rig, the three characterization tasks and the conversion (see above).
- **Done, #91 test standardization.** In two merges. `:borealis:*` (`caf9c492a`): the 22
  findings of the two detekt rules in test sources are fixed (test dispatchers are injected, and
  long parameter lists became data classes, `copy()` from a base value or shorter helpers), the
  `SettingsTwoPane` tests share a base class, and `setContentInKeyboardMode` lives once in the
  design-system test fixtures. `:app` (`0e709993e`): the same for the app tests, and the rest of
  the standardization, below.
- **Done, tooling (no issue).** Kover counted the remaining Java during the sprint, so the owner
  could decide on the coverage of `Game` and the renderer from measured numbers. The starting
  line coverage was `Game` 0%, `MediaCodecDecoderRenderer` 0%, `MediaCodecHelper` 33%,
  `MoonBridge` 11% and `AndroidAudioRenderer` 0%, and the Kover total went from 98.31% to
  88.82%. Codecov ignored the empty `app/src/main/java` until the wrap-up removed that line.
- **Done, ADR 0060 (no issue).** See above.

Removed, not converted: each removal has its own `refactor: remove` commit with its evidence
(ADR 0059, rule 8). Grouped by class:

- **`MoonBridge`:** the native `getPendingVideoFrames`, with its wrapper in `simplejni.c`; the
  `ML_PORT_INDEX_*` constants, `ML_PORT_FLAG_ALL`, `ML_PORT_FLAG_TCP_48010` and the
  `ML_PORT_FLAG_UDP_*` constants; `LI_CCAP_DUAL_TOUCHPAD` and `LI_PEN_BUTTON_TERTIARY`; and
  `BUFFER_TYPE_PICDATA` and `FRAME_TYPE_PFRAME` (owner, 2026-10-26).
- **`MediaCodecHelper`:** `dumpDecoders`, with the commented-out call in the renderer;
  `preferredDecoders` and `findPreferredDecoder`, a list that nothing ever filled; and
  `decoderNeedsSpsBitstreamRestrictions`, with its prefix list.
- **`MediaCodecDecoderRenderer`:** the fields `needsSpsBitstreamFixup` and `isExynos4`, with
  their two log blocks (see the accepted log change below); the write-only `foreground` flag,
  with its two writers; `USE_FRAME_RENDER_TIME`, `FRAME_RENDER_TIME_ONLY` and the listener that
  was never registered (the `totalTimeMs` sums stay); the `meteredNetwork` and `meteredData`
  parameters; the FLUSH codec recovery; the AV1 performance-point helpers and branches; two
  defensive checks (the "Decoder reset failed" check and the `replaySps` fetch failure); the
  Rec. 601 and Rec. 2020 colour cases of `createBaseMediaFormat`; and the unreachable
  `else { return null }` of `findAv1Decoder` (`677d1915d`).
- **`VideoStats`:** `getFps` and `VideoStatsFps`.
- **`Game`:** the `connecting` flag, which was never true.

**The accepted log change.** Removing `needsSpsBitstreamFixup` and `isExynos4` is the one change
of behaviour that the owner accepted (2026-10-26). Both fields were read only by the log
lines that followed them, so the lines "needs SPS bitstream restrictions fixup" and "is on
Exynos 4" are gone from the log. The decoder setup also no longer calls `isExynos4Device()`
once per decoder, which read `/proc/cpuinfo` and `/sys/devices/system` each time. The other
removals change nothing that a caller can see, except that a multi-window callback after an
early return of `onCreate` no longer throws a `NullPointerException` (the `foreground` flag).

Kept on purpose:

- **Kept because code uses it.** `AudioConfiguration.getSurroundAudioInfo` (`NvHTTP.kt` sends it
  in the `/launch` and `/resume` request) and the two port flags `ML_PORT_FLAG_TCP_47984` and
  `ML_PORT_FLAG_TCP_47989` (`NvConnection.kt` passes them as the port flags of a start that
  failed on the network). They looked unused, and they are not.
- **Kept although no production code uses it.** `LI_BATTERY_STATE_NOT_PRESENT`,
  `LI_CTYPE_STEAM` (`ArrivalReportTest` reads it), the empty `Game.stageComplete`, the
  `resume` parameter of `launchStarting`, and the native touch
  path of `Game` (`trySendTouchEvent`, `sendTouchEventForPointer` and
  `MoonBridge.sendTouchEvent`), which stays parked. Upstream turned that path off on
  2023-09-16, Artemis ships it as "Multi-touch", and LuminalShine accepts native pen and touch
  by default (`native_pen_touch`). The owner keeps it. See the Sprint 13 backlog, item (d),
  "Direct touch".

Accepted deviations of the conversions, for the record (each reviewed):

- **`MoonBridge`:** the callbacks read the static renderer and listener once, where the Java
  read them twice, which removes a race `NullPointerException` inside a native callback. The
  `checkNotNull` sites (`StreamFailures.failure`, `stringifyPortFlags`, `bridgeClLogMessage` and
  `bridgeClStageFailed`) throw `IllegalStateException` where the callee threw
  `NullPointerException`.
- **`AndroidAudioRenderer`:** the constructor takes a non-null `Context`.
- **Renderer:** narrower constructors, explicit `NullPointerException` messages, a final class,
  the `RendererException` companion, `VideoStats` internal, and five `private final` fields.
- **`Game`:** a final class, a final `setMetaKeyCaptureState`, a `Companion` field, synthetic
  accessors and a final `usbDriverServiceConnection`; the fields assigned in `onCreate` are
  `lateinit`, so a read before the assignment throws `UninitializedPropertyAccessException`
  where the Java threw `NullPointerException` (same paths, and nothing catches either);
  non-null framework parameters; `@RequiresApi(R)` instead of `@TargetApi(R)`; and the KTX
  `edit(commit = true)`.

Lint: the lint baseline lost the 5 entries of `Game.java` and `MediaCodecHelper.java`
(`1afbded4a`, merged in `a74266ec1`): `ApplySharedPref`, `SourceLockedOrientationActivity` and
`UseRequiresApi` of `Game.java`, and the two `DefaultLocale` entries of `MediaCodecHelper.java`.
The 22 entries left filter 11 `UnusedAttribute`, 3 `RtlHardcoded` and one each of eight other
ids (`UseRequiresApi` in `NsdManagerDiscoveryAgent.kt`, `OldTargetApi`, `NotShrinkingResources`,
`VectorRaster`, `MissingLeanbackLauncher`, `InsecureBaseConfiguration`,
`ExportedContentProvider` and `CustomX509TrustManager`). See
[building](development/building.md#lint-baseline).

Coverage of the converted files (Kover lines, from the report of the phase at `a74266ec1`, the
run that produced these figures): `Game.kt` 95.5% (the Java was 96.4%),
`MediaCodecDecoderRenderer.kt` 99.7% (920 of 923 lines), `VideoStats.kt` 100%,
`MediaCodecHelper` 95.4%, `AndroidAudioRenderer` 97.6% and `MoonBridge` 98.4%. The lines of
`Game.kt` that no test reaches are the branches for Android before 11 and
before 12, the branches for Android 16 (`BAKLAVA`) and SDK 37, the catches of the Samsung
reflection, the `SecurityException` of the Wi-Fi lock, the first `surfaceChanged` and the parked
native touch path. The rest of `MediaCodecHelper` reads `/proc` of the host. `:app` runs 4,140
tests (the executed set at `0e709993e`).

Test standardization of `:app` (issue #91, `0e709993e`), with no change to production code, to
the executed tests or to their assertions:

- **Detekt.** `InjectDispatcher` and `LongParameterList` are on for every test source of every
  module, `:app` included (`config/detekt/detekt.yml`). Test dispatchers are injected, and the
  long parameter lists of builders are data classes or `copy()` from a base value.
  `LongParameterList` ignores `@Composable` and `@Implementation`, because a Robolectric shadow
  method mirrors the signature it replaces.
- **Shadows.** Five `MoonBridge` shadows were folded into `ShadowRecordingBridge`, so the app
  tests have 2 (7 before): `ShadowRecordingBridge`, and `ShadowStunMoonBridge`, which records
  the network that the process was bound to when the STUN request was made. A reset of the
  recording shadow clears the real bridge state.
- **Threads.** The tests destroy the controller handlers that they start. A probe at the end of
  the `:app` suite found 705 idle `ControllerHandler` `HandlerThread`s among 777 threads, and
  finds none now.
- **Shared test package.** `io.github.magalz.borealis.testing` holds `AndroidLevels` (the API
  level constants), `LimeLogCapture`, `ControllerContexts` (the pad-context injection),
  `Coroutines` (`unconfinedScope()` and `WorkerDispatcherRule`) and `OwnedResources` (a rule
  that closes what a test hands it).
- **Robolectric.** The configurations go from 41 to 39 (the limit is 42 sandboxes).

Entire. Three configuration commits ride the phase pull request, made by the owner on
2026-10-27: `fb9fcdb68` ("Enable Entire"), `4e1c39d13` (Entire Brain is enabled, and `AGENTS.md`,
[orchestration](development/orchestration.md) and the `br-*` agents route code questions
between Memtrace and Brain) and `5afbb49a8` (`CLAUDE.md` loads `AGENTS.md` again).

Items:

- **Done.** The conversions, the rigs and the standardization above. The defects that the
  characterization tests pin are in the Sprint 14 block of the backlog, and are fixed in
  [Sprint 16](#sprint-16-fixes-of-the-converted-code) (owner, 2026-10-28).
- **Partly done.** Test size and duplication, moved here from the Sprint 9 quality review. The
  tests of every module follow the detekt rules now (see #91). The duplicates across modules
  need `testFixtures` build changes, and are item (c) of the Sprint 14 block of the backlog.
- **Run: the device round and the owner's UAT.** The device round, on the Google TV Streamer with
  the owner's OK and on the S22: each codec the devices decode, HDR, audio, the performance
  overlay, the Starting and Quitting screens, a second device during a launch,
  picture-in-picture, the Samsung keyboard capture, the rare natives (JNI binds each one at its
  first call), CheckJNI (the debug build turns it on by itself), Quit, and an upgrade over the
  `main` build. The checks are in
  [device-validation section 3.27](development/device-validation.md#327-sprint-14-checks). The
  round ran on 2026-10-28 and found no Sprint 14 regression: the agent part first, then the
  owner's part (the pads, the keyboard, the touch, the USB prompt, the hearing and the picture)
  and the owner's end-of-migration UAT, with the orchestrator reading the logs and running some
  steps. The UAT is
  [section 3.28](development/device-validation.md#328-owners-30-minute-gameplay-uat-sprint-14):
  at least 30 minutes of play on one game while the stream options change (resolution, frame
  rate, bitrate, codec, HDR, frame pacing, audio and input), then pairing, a wrong PIN, Add host,
  Forget, Wake-on-LAN, the pinned shortcut, the TV channel, resume and Quit. The results are in
  [section 19](development/device-validation.md#19-results-sprint-14); no stopwatch figure of the
  play time was recorded. The findings went to
  Sprint 15, to Sprint 17 and to Sprint 19 (the owner first put some of them in the backlog,
  item (d) of the Sprint 14 block, and organized them the same day), and the host
  crashed twice, the known #204, since closed as a duplicate of #212 (see the
  [host-side items](#host-side-items-luminalshine)).
  Wake-on-LAN could not be tested with the PC on Wi-Fi; it needs Ethernet, or Wi-Fi wake
  support, and a sleep that holds.

### Sprint 15: The stream screens and glass

Built in the phase branch `phase/s15-stream-screens`, one merged task per issue
([#97](https://github.com/magalz/Borealis/issues/97) to
[#105](https://github.com/magalz/Borealis/issues/105)). The phase pull request is
[#106](https://github.com/magalz/Borealis/pull/106). The device round and the owner's UAT ran on 2026-10-29
and 2026-10-30, on the builds of `2e76dfcfb` and `1455bcf0a`, and no step failed. The owner made three decisions at the UAT
that changed the code (below). The checks are
[device-validation section 3.29](development/device-validation.md#329-sprint-15-checks) and
[section 3.30](development/device-validation.md#330-owners-20-minute-uat-sprint-15), and the results are in
[section 20](development/device-validation.md#20-results-sprint-15). The head of the phase is `4429af309`,
with the three UAT changes, which no device has run yet. On 2026-10-28 the owner moved the Library, Settings
and layout items that this section held to [Sprint 17](#sprint-17-general-fixes); each says so there. The
stream items stayed, and the code of each is done.

Owner decisions:

- **Gate G2 (2026-10-28), after a live comparison on the TV and the S22.** The in-game menu and its
  popups get glass from a live copy of the video at a quarter of its size, blurred and refreshed 30 times
  a second, or a solid panel (Blur or Solid). There is no tint-only option (the owner judged it a
  nightmare for accessibility) and no frozen copy (very strange and ugly when the video moves). Every
  screen outside the game shows the user's background, moving, with no option for which screens. Both were
  Appearance options in the first version; the background option was dropped at the UAT (below).
  [ADR 0061](adr/0061-glass-and-the-animated-background-in-the-stream.md) records the
  decision, and amends decision 5 of ADR 0056 and decisions 10 and 13 of ADR 0057. The spike that gave the
  measures (issue #99, branch `spike/s15-stream-glass`) was never merged.
- **No lateral movement in the in-game menu (2026-10-28).** The owner could not reproduce the focus loss of
  the Sprint 12 round, and decided that Left and Right do nothing in the menu.
- **The contrast of the glass (2026-10-29).** The glass is the viewer's own tint and blur (40 percent at 8 dp
  at Standard, 85 percent at 48 dp at Reduced). The text on it has a halo, the segmented controls sit on a
  solid container, and the focus outline has a casing. The tint floor of 83 percent of the first version was
  dropped.
- **The error of a running stream is a popup (2026-10-29).** It stands over the last frame, frozen and
  blurred, and the scrim stays over the blurred frame too (the scrim is the light one since the UAT).
- **The owner's UAT (2026-10-30).** Every step passed, and the owner decided three things, which are in the
  code ([ADR 0061](adr/0061-glass-and-the-animated-background-in-the-stream.md), decision 11; merges `4dabac9db`
  and `1d73bc0b7`):
  - **The popups over the game.** The Quit popup and the error popup over the frozen frame looked like a hole,
    a panel of 40 percent glass under a scrim of 72 percent. Now the scrim is the page at 35 percent (the side
    panel's) and the panel is 85 percent of the surface, solid where the rules say. Over the background
    outside the game, the glass of ADR 0057 decision 8 is unchanged. On the 85 percent panel over white, ink is
    9.50:1, ink-2 6.74:1, accent-soft 6.20:1 and danger-ink 5.01:1 without the halo. The halo stays, and the
    owner may drop it later.
  - **No option for the background outside the game.** "Background before and after the stream" is dropped.
    The screens outside the game follow the Background of the Library (style, Off, Reduce motion, the battery
    saver). A clip still shows its still while the stream holds the decoder, and every style is held still
    under the conflict dialog and the error popup, where the host blurs. A value stored under the old key is
    ignored and stays in the DataStore file.
  - **"Menu glass in the stream" is "Stream menu style", Blurred or Solid**, with the same stored ids and
    default.
  The owner's review of the 53 screenshot references (gate G1) made no other mark.

Items, with what each became:

- **Background during the stream start.** Done in #103 (ADR 0061). The Starting screen shows its steps on
  a glass panel over the user's background, and so do the other screens outside the game: the conflict,
  the error, Quitting and Disconnecting. It is the user's Background as the Library shows it, with no option
  of its own: the first version had "Background before and after the stream" (Moving, Still or Off), and the
  owner dropped it at the UAT on 2026-10-30. The background goes when the phase turns Streaming, not before the
  first frame: the first frame can come before the connection reports the stream started, so the cut
  follows the phase. This changes the Sprint 9 rule that the background is never in a stream.
  - The risk, a background clip competing with the stream decoder for the hardware decoder on the TV, is
    answered by the clip lifecycle of ADR 0061: a clip style plays only while no stream decoder is
    configured, is released before the decoder is configured, and shows its still while a decoder is up
    (Quitting, Disconnecting, or an error before the connection has stopped). A Compose style keeps moving.
    In the spike the TV showed 0 decoder errors and no start cost beyond the spread of about 2 s between
    runs. The accessibility rules of ADR 0057 win, as they do in the Library.
- **Connecting and disconnecting screens.** Added at the Sprint 10 UAT (2026-10-21). Done in #103 and #97.
  Quitting, and the new Disconnecting screen, show their text over the background with its halo.
- **In-game menu in the Library's glass style.** Added at the Sprint 10 UAT (2026-10-21). Done: the spike
  and ADR 0061 (#99), and #104. The menu, its side panel and the Quit popup draw the copy of the video
  under the viewer's tint, with the Appearance option "Stream menu style" at Blurred (the default) or
  Solid (named "Menu glass in the stream", Blur or Solid, until the UAT of 2026-10-30). The copies stop when
  the menu closes and in picture-in-picture. The Quit popup over the menu is a firm panel under a light scrim
  since the UAT (the owner's decisions above). ADR 0061 amends decision 10 of
  [ADR 0057](adr/0057-glass-surfaces-and-the-accessibility-settings.md), which gave the stream no glass.
- **Quit confirmation of the stream as a centred popup.** Added at the Sprint 10 UAT (2026-10-21). Done in
  #100. The popup is inside the chrome layer, not a dialog window, and shares `StreamDialog` with the
  conflict dialog. A tap outside, B and Back close only the popup, and the focus returns to the Quit row.
- **Quit wording in three places.** Done in #100: `QuitCopy` in the design system is the one source of the
  wording, for the Library's action sheet, the popup and the conflict dialog.
- **A Quit from the user logs an error.** Done on the client in #97. The `-1` that follows the user's own
  Quit is logged at info (`quit stream ended`), skips the connectivity test and shows no error, and every
  exit asks the system to finish the activity once, so `Duplicate finish request` should be gone (the round
  checks it on the devices). The host side is
  [NortheBridge/luminalshine#218](https://github.com/NortheBridge/luminalshine/issues/218): the host
  disconnects the control stream with no termination message (see the
  [host contract](host-contract/luminalshine.md), section 7.6).
- **Quit took about 17 s once on 26.10.0-beta.1.** Partly done. #97 logs the stages of a Quit with their
  times (`quit accepted`, `quit cancel sent`, `quit cancel answered ms=`, `quit check answered ms=`,
  `quit stream ended`, `connection stop done ms=`, `activity finished`), and "Quitting `<app>`…" shows from
  the confirmation. By the host source the wait is the host's: it answers `/cancel` only after it has
  stopped the sessions, ended the app and restored the display, and which step took the 17 s is not known.
  The owner filed it as
  [NortheBridge/luminalshine#217](https://github.com/NortheBridge/luminalshine/issues/217), with the list
  of what `/cancel` waits on. The stream of that Quit ends with the `-1` of
  [#218](https://github.com/NortheBridge/luminalshine/issues/218) before the host answers (the item
  above). The round timed it again on 26.10.1 (3.29, step 1): each of three Quits on the TV took 5.6 to 6.0 s in
  `/cancel`, and about 5 s of it was the display helper's transactional revert in the join of the last session
  ([device-validation section 20](development/device-validation.md#20-results-sprint-15)).
- **Indeterminate progress indicators ignore Reduce motion.** Done for the Quitting and Disconnecting
  screens, which draw the Borealis loader, and it stands still when animations are removed (#97). The Add
  host and Privacy part is in [Sprint 17](#sprint-17-general-fixes).
- **The in-game menu loses its focus on Left or Right.** Found by the owner in the Sprint 12 round
  (2026-10-24). Done in #98, by a decision and not a fix: the bug did not reproduce, and on 2026-10-28
  the owner decided that the menu has no lateral movement. Left and Right are consumed, an option row
  changes its value on OK or A and wraps, and Up and Down reach every row and every Send keys chip, in one
  order. A touch on a segment still works on the phone. The sequence of D4 (the round pressed 25 Downs, OK,
  Right, OK on the S22 and 14 Downs on the TV) reaches Quit in an adapted form that two tests pin: Down to
  Send keys, OK, Right, OK, then Down to Quit. The round checks it on the devices.
- **Second device during a launch.** Found in the Sprint 14 device round (2026-10-28). Done in #101: a
  start that finds an app running asks on both paths, the `/serverinfo` path and the 400 path, unless the
  user chose Resume, Continue or Try again for that app. The audit trail records `entry=asked` or
  `entry=chosen`. A pinned shortcut or a TV poster for a running app asks now. One risk stays: if another
  device restarted the app that the Library shows running, Resume enters it without asking, because the
  host gives a paired client no session id. The host proposal is
  [discussion #221](https://github.com/NortheBridge/luminalshine/discussions/221). The rule is in the
  [host contract](host-contract/luminalshine.md), section 7.6. It changes
  [UC-003](use-cases/0003-join-or-replace-a-running-app.md), where every choice was explicit.
- **Disconnect gives no feedback.** Found in the Sprint 14 UAT (owner, 2026-10-28). Done in #97: the
  phase Disconnecting shows "Disconnecting from `<app>`…" for at least about 1 s and waits for the stop
  for at most 5 s, and then the Library says "Disconnected. `<app>` is still running on the PC.", the way
  it says "App quit.".
- **A keyboard and mouse combo opens the in-game menu.** Found in the Sprint 14 UAT (owner, 2026-10-28).
  Done in #102: Ctrl+Alt+Shift+M opens the menu, consumed before the PC, and the Back button of the mouse
  held for 1 s opens it too (with "Mouse back and forward buttons" on; a short press still reaches the PC,
  and the right click that Android delivers as Back does not count). Ctrl+Alt+Shift+Q goes through the
  session's Disconnect. The stream hint names the chord, and the mouse Back hold when that setting is on.
  A game that uses the side button as push-to-talk opens the menu after 1 s.
- **The error of a running stream as a popup over the last frame.** Added by the owner on 2026-10-29. Done
  in #105. When a stream that showed a frame fails, the error is the shared `StreamDialog` (the message, Try
  again, Diagnostics and Close, with Wake PC or Pair again where they applied) over one copy of the video
  taken at the top of `connectionTerminated` and `stageFailed`, before the connectivity test and the stop:
  on the S22 the decoder is released about 260 ms later and a copy then fails. The user's Quit, a graceful
  end and the first-frame watchdog get no copy. With no frame, the popup stands on the user's background. A
  tap outside does nothing, and Back and B close it, to the Library. The panel follows the Stream menu
  style rules, and the conflict dialog and the popup over the background get the window blur of ADR 0057
  decision 8 (a blurred, frozen background and no scrim on API 31 and later). Over the frame, the popup has
  the light scrim and the firm panel that the UAT decided (above).

Known limits of the code, and where each one is now. They were placed on 2026-10-30, with the findings of the
UAT, so none is left with no place. The owner can move any of them:

- **Focus outline with Thicker focus outline:** 0.5 dp of its casing is cut by the scroll clip of the side
  panel at the left and right edges, and at the top and bottom when the menu is scrolled to an end. The
  ring and 1 dp of casing stay whole, and 3:1 holds. A design-system layout change would remove it. In
  [Sprint 17](#sprint-17-general-fixes), Accessibility and layout.
- **The halo of the popup's title and body:** its faint tail is cut by the 2 dp bleed of the content layer.
  It is cosmetic, and the contrast tests are not affected. In Sprint 17, Accessibility and layout.
- **A rotation after a failure:** the frozen frame is placed and scaled when it is copied, so a rotation,
  a split-screen resize or leaving picture-in-picture after the failure leaves it at the old offset. It is
  rare, and nothing crashes. In Sprint 17, Video and the stream screens.
- **A copy that never answers:** the pending state has no time cap; it relies on `PixelCopy` answering
  every request. If it never did, the popup would stay on black under its scrim with a solid panel, and
  every action would work. While a copy is pending (about 0.1 to 0.3 s) the panel is solid; it turns to glass when
  the frame arrives. The copies of the round took 76 ms on the S22 and 314 ms on the TV. In Sprint 17,
  Video and the stream screens.
- **A phone in landscape:** the three or four actions of the popup stack, and with large text the panel
  scrolls. It is covered by a test and a reference. No device round has reported it (the S22 steps of 3.29 are
  not recorded), so it is a device check in [Sprint 17](#sprint-17-general-fixes), Video and the stream
  screens.
- **Not measurable in Robolectric:** whether the offscreen layer of the halo is kept across frames (its
  cost on a TV), bitmap reuse against the hardware renderer, the colour of an HDR copy, and the first copy
  after the menu opens (129.7 ms on the S22 in the spike). The owner passed the look of the menu on the S22
  in the UAT (U4) with no note, but none of the four was measured, so they are device checks in Sprint 17,
  Video and the stream screens. A real `PixelCopy` at the moment of a drop is no longer open: the logs of the TV (2026-10-29) and of
  the S22 show the request before `Connection terminated` and the copy after it.
- **`StreamChromeHost.handleBack`** still handles the Failed phase, which is unreachable while the popup is
  composed. It is a harmless fallback. Two branches have no test, `FrozenFrame.kt` (the copy worked but the
  view has no bounds when it ends) and `VideoSource.kt` (a refused `PixelCopy` request); the patch
  coverage of #105 is 98.7%. In [Sprint 18](#sprint-18-refactor-of-the-converted-code), with the
  tooling items: remove the unreachable branch, and test or delete the two branches.
- **The test fixtures:** other tests that end on a screen outside the game with the default look (the
  default background style, which moves) have the order hazard that `StreamBackTest` had, and
  `VideoGlassSpec.kt` repeats the Reduced values (85 percent, 48 dp) that `GlassRules` keeps private. Both
  are in Sprint 18.

The two flaky tests and the duplicated pixel helpers, which the owner listed for the backlog on 2026-10-30 from the
gates and reviews of the phase, are in Sprint 18 too. The bottom-edge line on the TV and the owner's mouse, which
came from the UAT, and the orphan DataStore key are in Sprint 17. The results of the
round are in [device-validation section 20](development/device-validation.md#20-results-sprint-15).

Run: done on 2026-10-29 and 2026-10-30, and the results are in
[device-validation section 20](development/device-validation.md#20-results-sprint-15). The device round, on the
Google TV Streamer with the owner's OK, was the drop check of #105 and the TV part of
[section 3.29](development/device-validation.md#329-sprint-15-checks) (Quit three times with the stage lines
and the host log, Disconnect, the menu with no lateral movement, and the popup over the frozen frame). The
owner's UAT, [section 3.30](development/device-validation.md#330-owners-20-minute-uat-sprint-15), about 20
minutes by hand on both devices, passed every step and ended with the decisions above. The S22 steps of 3.29
that the UAT does not repeat have no result recorded, and no device has run the changes of the UAT yet; the owner
decides whether the next device round runs them.

### Sprint 16: fixes of the converted code

Planned, not started. Owner decision of 2026-10-28; see the
[order of the coming sprints](#order-of-the-coming-sprints-owner-decisions-of-2026-10-21-and-2026-10-28).

- **Scope:** item (b) of the blocks "From the Sprint 12 conversion", "From the Sprint 13
  conversion" and "From the Sprint 14 conversion" of the [backlog](#backlog-not-scheduled): the
  defects that the conversions found and kept. 34 are open from Sprint 12 (the first, #69, is
  fixed), 53 from Sprint 13 and 41 from Sprint 14.
- **Triage first:** the sprint starts with a triage that the owner approves. Each defect is a
  real defect (fixed), latent, or upstream behaviour (kept, with the reason).
- **Method:** a fix follows the [TDD rule](development/tdd.md). The test that pins the defect is
  changed to the fixed behaviour in its own failing commit, then the fix.
- **Order:** the fixes come before the refactor of Sprint 18. The triage removes dead and
  unreachable paths, which shrinks the refactor, and the refactor then does not have to keep known
  defects on purpose.
- **Ends with a device round.**

### Sprint 17: general fixes

Planned, not started. Owner decision of 2026-10-28, later the same day; see the
[order of the coming sprints](#order-of-the-coming-sprints-owner-decisions-of-2026-10-21-and-2026-10-28).

The sprint takes everything that is not a conversion item and does not fit one of the other planned
sprints squarely (owner: "everything that is not conversion and does not fit perfectly in one of the
sprints already planned"). It comes after the fixes of the converted code, so that every fix comes
before the refactor. Each item keeps its text, its source and its owner date, and says where it came
from: Sprint 15, the [backlog](#backlog-not-scheduled), or the backlog by way of Sprint 15 on the
same day.

The deferred RTSP parser rework is not here: it stays in the backlog, deferred by the owner. What
the plan already holds for [SecOps](#sprint-19-secops) and
[release preparation](#sprint-20-release-preparation) stays there too.

#### Audio

The audio items come from the two audio entries of item (d) of the Sprint 14 block of the backlog
(the runs, and the audio behind the picture), and from the audio follow-up of 2026-10-28 on the TV.
The follow-up ran without code changes, as the owner asked; its method, its table and its findings
are in
[device-validation, Audio follow-up, 2026-10-28](development/device-validation.md#audio-follow-up-2026-10-28).

- **Audio runs on the S22 and on the TV.** Moved here from item (d) of the Sprint 14 block of the
  backlog. `AndroidAudioRenderer` drops a decoded packet while 40 ms or more of audio are pending
  (`PENDING_AUDIO_LIMIT_MS`, the upstream rule, which bounds the latency), and logs "Too much
  pending audio data". LuminalShine sends audio only while the PC plays sound, so each sound after
  silence arrives at once. On the S22 the queue reaches about 90 ms and about 14 packets are dropped
  (the log lines of one run), which may cut the start of short sounds. On the S22 the `main` build
  does the same. The agent part did not see it on the TV, but the owner's UAT did, with the audio of
  a game: the owner heard a loss in the first seconds of each stream (A3), and stutters at 1080p
  (A2). The log has the runs at the start of the stream and in mid-play, with no network loss:
  07:57:52 to 07:58:04 and 07:58:16 to 07:58:21 at 1080p (A2, pending 45 to 65 ms), and 08:20:00 to
  08:20:12 with Balanced frame pacing (A4). The cause of the mid-play runs is not known. Possible
  ways: ask the host for continuous audio, or a short start buffer, and look at the mid-play runs on
  their own (finding U2). The "ask the host for continuous audio" way is also the optional feature
  that item (d) of the Sprint 12 block listed (merged here, owner, 2026-10-28): send
  `continuousAudio=1` in `/launch`, so the client gets a steady audio stream on a silent PC.
  LuminalShine supports it. The owner asked for an investigation first, without code changes
  (2026-10-28), and it ran the same day: an A/B of the Sprint 14 build and the build before it on
  the TV, and a reading of the code. The runs are older than Sprint 14: the build before it dropped
  three times as much in the same condition. They follow 1080p, and Balanced most (none at 720p),
  and they come when sound starts again after silence, and once as a 13 s run in the middle of a
  sound. Decision (owner, 2026-10-28): fix them in this sprint, measured before and after with the
  same method as the follow-up (eight short beeps with 3 s of silence between them, then a 60 s
  tone, one stream for each row).
- **Audio behind the picture on the TV: the Bluetooth notice.** The entry "Audio behind the picture
  on the TV" of item (d) of the Sprint 14 block of the backlog becomes this work, because the cause
  is known now. The owner heard the audio after the picture (lip sync) in every channel layout,
  stereo too (A5, finding U3). The owner first put it in the backlog, with no A/B against `main`
  ("the aim now is to see that everything works as before"), and then asked for an investigation
  first, without code changes (2026-10-28). The follow-up found the cause: the Bluetooth output.
  With the owner's Bluetooth audio device connected, the Streamer sent the audio of the stream to
  Bluetooth A2DP, whose output thread reported 270 to 281 ms of latency, against 141 ms for HDMI,
  and with Bluetooth off the owner judged the sync good. Other apps hold their video to match the
  audio; a game stream does not. Decision (owner, 2026-10-28): when the audio goes to a Bluetooth
  output, show a one-line notice for a few seconds, and then let it go away.
- **The audio delay in the performance overlay.** Decision (owner, 2026-10-28): if possible, add a
  line to the performance overlay with the audio delay. Android can estimate it from the
  `AudioTrack` timestamps; the client reads none today.
- **A 2-channel output, with 5.1 or 7.1 asked for.** On the Streamer, a track for 7.1 on a
  2-channel output leaves the fast path (5.1 was not measured): it goes to the normal mixer, with a buffer 11 to 16 times
  larger, which adds about 250 to 320 ms (Android's figures in the follow-up: 405 ms against 151 to
  163 ms on HDMI, 615 ms against 291 ms on Bluetooth). Decision (owner, 2026-10-28): when the
  current output of the device has only 2 channels and the setting asks for 5.1 or 7.1, ask the host
  for stereo, log it, and say so with a one-line notice for a few seconds at the start of the
  stream.
- **One notice modal.** The Bluetooth notice and the channel notice share one small notice modal,
  since there are two now (owner, 2026-10-28).

#### Library and Settings

Moved here from Sprint 15 (owner, 2026-10-28), unless an item says otherwise.

- **A pinned app shortcut fails when Borealis is not running.** Found in the Sprint 12 round
  (2026-10-24); the owner put it in Sprint 15, and moved it here. The launch request of the shortcut
  fires once the app list loads from the cache, before the first poll gives the host an address, so
  `ServerHelper.doStart` shows "Computer is offline" and starts nothing. Proposed fix, with a test
  first: wait for the host's first poll answer before the stream starts; a host that never answers
  still ends in the offline message. See
  [device-validation section 17](development/device-validation.md#17-results-sprint-12),
  the follow-up on the S22. The Sprint 14 round did not measure it: in B6 of
  [section 19](development/device-validation.md#19-results-sprint-14) the owner pinned the
  shortcut and said that it works, but the tap with Borealis not running was not reported.
- **Back from Apps to Hosts plays a shrinking animation.** Found by the owner in the Sprint 12 round
  (2026-10-24), on the TV and the phone. The other screens of the Library fade (`FadingNavHost` in
  `LibraryNavHost.kt`). Find where the shrink comes from, and make this back like the others.
- **A forgotten host is not found again until the app restarts.** Found by the owner in the
  Sprint 12 round (2026-10-24). After Forget, the host does not come back as "Found on this network" until
  the app is closed and opened again. Check whether discovery skips a host it already reported, in
  `ComputerManagerService` and the mDNS agents, with a test first. The owner first asked for it at
  the Sprint 10 UAT (2026-10-21), as a backlog item that is merged here (owner, 2026-10-28). The
  Sprint 14 UAT saw it again on the TV: in B1 of
  [device-validation section 19](development/device-validation.md#19-results-sprint-14)
  the host came back only after the app was killed.
- **A refresh button on Hosts.** After Forget, the Hosts list does not update until the app is
  restarted. The owner wants a refresh button on Hosts. The owner had put it after SecOps
  (Sprint 19), or post-MVP. Moved here from the backlog, with the forgotten-host item above (owner,
  2026-10-28). The fix above may make it unneeded; the owner decides.
- **Add host says "Pair it" for a host that is already paired, and does not say that the host is
  already added.** Found in the Sprint 12 round (2026-10-24); the owner put it in Sprint 15, and
  moved it here. `shell_host_added` shows for any added host. Word it by the host's pairing state.
  In the Sprint 14 UAT (2026-10-28) the owner added a host that was already added or paired and got
  no word about it: say "already added" or "already paired". The same screen has the keyboard of the
  field, moved to Sprint 15 from item (d) of the Sprint 14 block of the backlog, and from there to
  this sprint (owner, 2026-10-28): the owner wants a numeric-style keyboard, with `.`, `/` and `:`
  at hand, instead of the full keyboard, if Android allows it. `AddHostScreen.kt` already sets
  `KeyboardType.Uri`, so check what that gives on the S22 and on the TV (the keyboard of Google TV
  may ignore it), and whether another input type is possible. Host names must still be typeable. See
  B2 and finding U6 in
  [device-validation section 19](development/device-validation.md#19-results-sprint-14).
- **TV focus after the host Options sheet.** Moved to Sprint 15 from item (d) of the Sprint 14 block
  of the backlog, and from there to this sprint (owner, 2026-10-28). After the sheet (Details and
  Forget) was closed with Back over the Apps screen, no node had focus, and the D-pad did nothing
  until another Back, which went to Hosts with the focus on the Continue row. The owner had left the
  sheet open in B1 and B4. It is Library code, not Sprint 14. See
  [device-validation section 19](development/device-validation.md#19-results-sprint-14),
  finding U11.
- **The "Audio effects" text.** Moved to Sprint 15 from item (d) of the Sprint 14 block of the
  backlog, and from there to this sprint (owner, 2026-10-28). The setting reads "Let system audio
  effects work while you stream. This can add latency." (`settings_audio_fx_summary` in
  `strings_settings_ui.xml` of `:borealis:settings`), and the owner found it unclear. Say what it
  does: it lets the device's equalizer and sound effects act on the stream, and it uses the slower
  audio path, because `AndroidAudioRenderer` skips the low-latency track when audio effects are on
  (`enableAudioFx && lowLatency`). See
  [device-validation section 19](development/device-validation.md#19-results-sprint-14),
  finding U4.
- **A sample vibration.** Moved to Sprint 15 from item (d) of the Sprint 14 block of the backlog,
  and from there to this sprint (owner, 2026-10-28). When a vibration option is turned on in
  Settings, give a short sample vibration, so that its effect can be felt. See
  [device-validation section 19](development/device-validation.md#19-results-sprint-14),
  finding U9.
- **Indeterminate progress indicators of Add host and Privacy ignore Reduce motion.**
  `AddHostScreen` and `PrivacyPane` draw Material's indicators, which do not ask
  `rememberAnimationsRemoved`. This is the Add host and Privacy part of the Sprint 15 item of the
  same name; the Quitting screen part stays in
  [Sprint 15](#sprint-15-the-stream-screens-and-glass), which redoes that screen.

- **The orphan DataStore key of the dropped stream option.** From Sprint 15 (owner, 2026-10-30). The key
  `borealis_stream_background` of the option "Background before and after the stream", which the UAT dropped,
  stays in `borealis_settings` on a device whose build stored it. Nothing reads it
  ([ADR 0061](adr/0061-glass-and-the-animated-background-in-the-stream.md), decision 11), and the
  [privacy policy](policies/privacy-and-logging.md) says it stays until the app data is cleared. No release
  has had the option, so only debug builds of Sprint 15 hold one. Decide in the triage: keep it, since it is a
  stable id with nothing personal, or remove it with one DataStore edit.

#### Input

Moved here from item (d) of the Sprint 13 block of the backlog (owner, 2026-10-28). They were found
in the Sprint 13 device round (2026-10-26), on the S22, and none is a Sprint 13 regression. The
results are in
[device-validation section 18](development/device-validation.md#18-results-sprint-13).

- **Trackpad drag.** In trackpad mode, the default, a drag is a press and hold without moving for
  about 650 ms, then a move (`RelativeTouchContext.kt`, `DRAG_TIME_THRESHOLD`). The owner expected
  the usual double tap and hold, which does not start a drag.
- **Direct touch.** A long press and a move select text on the PC instead of dragging, as they do on
  a phone. The owner found it hard to reproduce on a desktop. Sprint 14 kept the native touch path
  of `Game` (`trySendTouchEvent`, `sendTouchEventForPointer` and `MoonBridge.sendTouchEvent`),
  parked: upstream turned it off on 2023-09-16 ("Disable native touch passthrough for now"), Artemis
  ships it as its MultiTouch mode, and LuminalShine accepts native pen and touch by default
  (`native_pen_touch`). Direct mode sends mouse input today; the parked path would send the touch to
  the host as a touch event.
- **Alt+Tab.** With Alt held, the first Tab is lost, and Windows opens its Alt menu. The main build
  (`ded969bdb`, Sprint 12 code) does the same, so it is not from Sprint 13.
- **Ultimate 2, Home held.** The PC acts as if Win+Tab was pressed. The app sends the Guide button
  while the pad's Home button is held, and nothing in the input code maps it to Win+Tab. Probably
  Windows' own long press of the Xbox button; to verify on the host. In the
  [VHF gamepad round](development/device-validation.md#vhf-gamepad-round-2026-10-28) of
  2026-10-28, on the S22, Home short and held passed (owner). The Win+Tab check against
  Windows' Task View on purpose is still to do.

- **The side Back button of the owner's mouse arrives as a right click.** Found by the owner in the Sprint 15
  UAT (2026-10-30). The inherited path (`Game.handleKeyDown` and `handleKeyUp`) turns a Back key from a mouse
  source into a right click while "Mouse back and forward buttons" is off. With it on, `handleMotionEvent` reads
  `BUTTON_BACK`, and only that opens the menu after 1 s (Sprint 15, #102); a short press goes to the PC on
  release. A raw capture of the events of that mouse is needed first: the source, the key code and the button
  state of a press of the side button, with the setting on and off. The fix follows, with a test first.

#### Pads

Moved here from item (d) of the Sprint 14 block of the backlog (owner, 2026-10-28): the first two
items. Both are investigations. They were found in the Sprint 14 device round (2026-10-28), and
neither is a Sprint 14 regression. Later the same day the owner put the VHF gamepad driver on the
host (LuminalShine 26.10.0 replaced ViGEmBus with it; 26.10.1 keeps it), and the
[VHF gamepad round](development/device-validation.md#vhf-gamepad-round-2026-10-28) changed the
facts of both items and added the third item and the open decision. See the
[host contract, section 13](host-contract/luminalshine.md#13-input).

- **Pad battery.** No battery reaches the PC. The client sends the battery state only when Android
  reports one for the pad (`InputDevice.batteryState.isPresent` on Android 12 and later, or the
  SHIELD API; `ControllerHandler.sendControllerBatteryPacket` returns otherwise; 3.27 step 10). In
  the VHF round of 2026-10-28, Android showed no battery for either pad: the Ultimate 2 (over
  Bluetooth on the S22, and through its receiver in D-input on the TV) and the Pro 3 (on its
  receiver in Switch mode, on the S22). The host side has changed: it no longer makes an Xbox 360
  controller by default, nor a wired DualShock 4 through ViGEmBus. Under `auto`, its default, it
  makes an Xbox Series for an Xbox pad or for a pad of unknown type that reports neither a sensor
  nor a touchpad, a Switch Pro Controller for a Nintendo pad, and a DualShock 4 for a PlayStation
  pad or for a pad of unknown type that reports a sensor or a touchpad (with `motion_as_ds4` and
  `touchpad_as_ds4` on, the defaults); a pad reported as an Xbox pad stays an Xbox Series whatever
  else it reports. Borealis reports any pad with a sensor that is not a PlayStation pad as unknown
  (see the open decision below). An Xbox One or a DualSense comes only from the host's `gamepad`
  setting (global, per app or per client), and `auto` falls back to a DualSense only
  when the driver lacks the Xbox Series profile. The host accepts a battery state only for the
  DualShock 4, the DualSense and the Switch Pro Controller, not for an Xbox Series or an Xbox One.
  The round made an Xbox Series for the Ultimate 2 on the S22, an Xbox One for it on the TV (so
  the setting was `xbox_one` for that launch; the round did not read it), and a Switch Pro
  Controller for the Pro 3.
  Still to check: whether Android reports a battery for the Pro 3 over Bluetooth (`dumpsys input` or
  a log line); the round used its receiver only. See A6, C5 and finding U7.
- **Pad gyro.** The gyro of the 8BitDo Pro 3 works on Windows (Steam's controller test), but neither
  Android device exposes it to the app: the Streamer sees the pad through the USB receiver as a
  plain HID gamepad (no sensor in `dumpsys input`), and the S22 sees it in the pad's 8BitDo
  mode. Try Switch mode over Bluetooth on the S22. Run the Motion fallback check (the phone's
  sensors to a DualShock 4) with a pad connected: it did not run in the UAT, because no pad reached
  the host in the streams of 10:29 and 10:35. See A8, C5 and finding U8. The VHF host accepts
  motion only for a DualShock 4, a DualSense and a Switch Pro Controller, and when it creates one it
  asks the client for accelerometer and gyroscope events at 100 Hz; which of them it makes depends
  on what the client reports (see the open decision below). In the VHF round neither pad had a
  sensor on Android (the Ultimate 2 on both devices, the Pro 3 on its receiver on the S22), so no
  motion could reach the host.
- **Rumble for pads in D-input or Switch mode.** Found in the VHF gamepad round (2026-10-28); the
  owner sent it to this sprint the same day. Pads in D-input or Switch mode get no rumble on
  Android: the S22 and TV kernels have no `NINTENDO_FF`, the TV has no `XPAD_FF`, and the 8BitDo
  D-input identity is generic HID. Rumble reaches Borealis and then goes to the phone (the fallback)
  or nowhere (the TV). XInput mode gives rumble only through the Borealis USB driver, and the owner
  says XInput pads do not work in other Android apps. Possible fix to investigate: extend the
  Borealis userspace USB driver to the Switch Pro receiver (`057E:2009`, the Pro 3 dongle; rumble,
  and maybe gyro and battery from the `0x30` reports) and maybe to the 8BitDo D-input receiver
  (`2DC8:6012`), the way it handles XInput today. Also check why the XInput receiver (`2DC8:310B`)
  is not usable outside Borealis on the TV (the `xpad` device match on that kernel). The kernel
  facts are in [device-validation section 19](development/device-validation.md#vhf-gamepad-round-2026-10-28).
- **Open owner decision: a Nintendo pad whose sensors Android exposes.** Recorded on 2026-10-28
  from the VHF gamepad round; the owner has not decided. When Android exposes the sensors of a
  Nintendo pad (the Pro 3 in Switch mode), should the host make it a Switch Pro Controller or a
  DualShock 4? The host's policy
  ([contract, section 13](host-contract/luminalshine.md#13-input)) gives one or the other by what
  the client reports:
  - **Switch Pro Controller**, when the client reports the Nintendo type, which Borealis does for
    vendor `057E` (`ControllerHandler.sendControllerArrival`), unless it overrides the type (next
    item). The Nintendo type wins over a motion capability. The host then accepts motion and a
    battery state, and no touchpad.
  - **DualShock 4**, when the client reports the type as unknown with an accelerometer or a gyroscope
    and `motion_as_ds4` is on (the default). Borealis does this today for every pad that is not a
    PlayStation pad and has a sensor manager: `sendControllerArrival` then reports the type as
    unknown, shows a toast and turns on the clickpad emulation (see the Sprint 13 note of the
    [backlog](#backlog-not-scheduled)). The host then accepts motion, a battery state and touch,
    and it sends the light colour to the client.

  So, with the current client, a Nintendo pad with a sensor becomes a DualShock 4. A Switch Pro
  Controller needs the client to report the Nintendo type, or the host's `gamepad` setting (global
  or per client) set to `switch_pro`.

#### Video and the stream screens

From the Sprint 15 UAT (owner, 2026-10-30) and from the known limits that Sprint 15 left open. The results of
the round are in [device-validation section 20](development/device-validation.md#20-results-sprint-15).

- **A purple and cyan line at the bottom edge of the video on the TV.** Found by the owner in the Sprint 15
  UAT: once, with Steam Big Picture, a purple and cyan line with white blocks showed at the bottom edge of the
  video, and went away by itself. The owner has a photo. In the TV logs of the agent part, the decoder's output
  format and crop change at the start of every stream, so that change alone does not set this stream apart. The
  cause is not known. First look at the decoder log of a stream that shows it, and run the same app on the
  build before Sprint 14, as the audio follow-up did, to tell a conversion defect from an older one.
- **The frozen frame after a rotation.** From Sprint 15 (the known limits). The frame is placed and scaled when
  the copy ends, so a rotation, a split-screen resize or leaving picture-in-picture after the failure leaves it
  at the old offset. It is rare, and nothing crashes.
- **A time cap for the pending copy.** From Sprint 15 (the known limits). The error popup waits on black for the
  copy with no time limit, and relies on `PixelCopy` answering every request it accepts. Add a cap, and show the
  background when it runs out. The copies took 76 ms on the S22 and 314 ms on the TV in the round.
- **Device checks left open by Sprint 15.** Four checks that Robolectric cannot make, and one that no round
  has reported: whether the offscreen layer of the halo is kept across frames (its cost on a TV), bitmap reuse
  against the hardware renderer, the colour of an HDR copy on the S22, and the delay of the first copy after the
  menu opens (129.7 ms on the S22 in the spike); and the error popup on a phone in landscape, where its three or
  four actions stack and, with large text, the panel scrolls (a test and a reference cover it). The next device
  round looks at them, with the new scrim and panel of the popups and the screens that follow the Library
  (device-validation section 20, "Not run and owner items").

#### Accessibility and layout

Moved here from Sprint 15 or from the backlog (owner, 2026-10-28), as each item says.

- **Combined font scales above 2.0 at Normal.** A system font of 2.0 with the Text size Largest is
  2.6. There Normal text fails the fit tests in 8 of the 84 runs of the Library (Apps with a long
  host name, on a TV and on a phone) and in 1 of the 70 runs of the components (the rows of the
  settings on a TV). Wider is drawn as Wide from 2.0, and Wide and Normal are never limited. Wide
  above a combined 2.0 has not been measured, only Normal was. See
  [accessibility, text spacing](ux/accessibility.md#text-spacing-known-limitations).
  Moved here from the backlog.
- **Segments and keyboard keys still lift when focused.** `SegmentedControl` and `KeyboardKey` use
  `liftedWhenFocused`, so the merged tree lists the focused one last. Robolectric cannot see
  TalkBack's traversal links: check it with TalkBack on a device in this sprint.
- **A UAT note:** "Fn 123" wraps on narrow phones at the default look.
- **A touch handheld with only `KEYBOARD | DPAD`.** From the Sprint 9 device round
  (2026-10-19), moved here from the backlog. A touch handheld whose built-in controls report only
  `KEYBOARD | DPAD`, with no GAMEPAD source, now gets the phone layout. This is rare;
  watch for it.
- **The Esc row of the stream keyboard in a narrow window.** From the Sprint 9 device round
  (2026-10-19), moved here from the backlog. On a narrow window with a pad (a phone in split
  screen), the stream keyboard covers the stream but keeps the TV shortcut row, where Esc is the 7th
  of 8 chips. `StreamKeyboard.kt` picks the Esc-first row by `docked`, not by width (owner,
  2026-10-20: backlog).
- **The halo and the tint-only glass have not been seen on API 28 to 30.** From the Sprint 10
  reviews and its device round (2026-10-21), moved here from the backlog. No device is available;
  the tests draw them with Robolectric. The check needs a device.

- **The focus casing and the halo of the popups over the video.** From Sprint 15 (the known limits). With
  Thicker focus outline, 0.5 dp of the casing of the focus outline is cut by the scroll clip of the side panel
  at its edges (the ring and 1 dp of casing stay whole, and 3:1 holds), and the faint tail of the 3 dp halo is
  cut at the 2 dp bleed of the content layer on the title and body of the popups. A design-system layout change
  would remove both. The second is cosmetic.

### Sprint 18: refactor of the converted code

Planned, not started. Owner decision of 2026-10-28.

- **Scope:** item (a) of the same three blocks of the [backlog](#backlog-not-scheduled): the 166
  places marked `(Sprint 12 backlog)` (49), `(Sprint 13 backlog)` (65) and `(Sprint 14 backlog)`
  (52), with the other notes of item (a) of the Sprint 14 block.
- **Test and tooling items.** Moved here from the [backlog](#backlog-not-scheduled) (owner,
  2026-10-28):
  - item (c) of the block "From the Sprint 14 conversion": the tests that read text, the test code that
    repeats across modules, the smaller leftovers and the Robolectric limit;
  - the open entries of item (c) of the block "From the Sprint 13 conversion": the wall-clock test
    (`ButtonTimingCharacterizationTest`) and the Robolectric sandbox limit. Its entries marked "Done in Sprint 14"
    are done;
  - **`MenuChordDetector.update`** (`borealis/stream/.../input/MenuChordDetector.kt`), from the Sprint 9
    quality review: a complexity refactor. Cyclomatic complexity 13 and cognitive complexity 18, seven mutable
    fields, a repeated expression, and an order dependency with no comment;
  - **Test fixtures and the Reduced glass values**, from [Sprint 15](#sprint-15-the-stream-screens-and-glass)
    (the reviews of #104): other tests that end on a screen outside the game with the default look (the
    default background style, which moves) have the order hazard that `StreamBackTest` had, so consider a
    background Off default in the test fixtures; and `VideoGlassSpec.kt` (`:borealis:stream`) repeats the Reduced values
    (85 percent, 48 dp) that `GlassRules` keeps private, with a comment that points at ADR 0057, so keep
    them in one place.
  - **Two flaky tests and the duplicated pixel helpers**, from Sprint 15 (the gates and reviews of the phase; the owner listed them for the backlog on 2026-10-30).
    `StreamChromeHostFrozenFrameTest` failed once under load with a Robolectric `NullPointerException` before
    the body of the test, and `StreamBackTest` "back opens the menu…" failed once on the timing of
    `menuHintShown`. The stream tests also repeat pixel helpers: `ui/glass/PixelSupport.kt` against the private
    copies in `DialogOverBackgroundTest` and `DialogOverBackgroundApi30Test`, and the private `assertNear`
    copies in four stream tests. Make the two tests deterministic, and keep the helpers in one place.
  - **The Failed phase of `StreamChromeHost.handleBack` and two untested branches**, from Sprint 15 (the known
    limits): the branch is unreachable while the popup is composed, and `FrozenFrame.kt` (the copy worked but
    the view has no bounds when it ends) and `VideoSource.kt` (a refused `PixelCopy` request) have no test.
    Remove the first, and test or delete the others.
  - **The test heap of `:borealis:library` and `:borealis:settings`**, from Sprint 15: under load
    (two Gradle runs at once on 2026-10-29) their accessibility tests ran out of memory. Only `:app`
    gets the 2 GB heap of `BorealisUnitTestHeap` (`build-logic`); decide whether these two modules
    need it too, or whether one Gradle run at a time stays the rule.
- **Method:** behaviour does not change. The characterization tests guide the refactor. A
  refactored place loses its suppression and its marker.
- **Ends with a device round.**

### Sprint 19: SecOps

Owner decision of 2026-10-20: a whole sprint on security comes before the
release. Sprint 19 is hardening and security testing.

- It comes after the migration (owner decision of 2026-10-21), and after the fixes, the general
  fixes and the refactor of the converted code (owner decisions of 2026-10-28), so it audits the
  Kotlin code that ships.
- The sprint starts with a survey of the attack surface, which sets its scope. The
  candidate areas are the network and pairing code, the certificates and stored
  keys, the vendored C (`core/fuzz` has the fuzzers), the exported components in
  the manifest, the logs, and the dependencies.
- **`RiskyLibrary` lint check.** It stays on, as a security alert, although it reads
  the Google Play SDK Index and so can fail a build with no code change, like the
  version checks that were turned off (owner, 2026-10-20). Review it in this sprint,
  before the release.
- **Dependency updates.** Update Gradle 9.8.1 to 9.8.2 and kotlinx-serialization
  1.11.0 to 1.12.0, if Dependabot has not opened them. Dependabot should open the
  two updates.
- **AGP version check.** AGP has no new-version notice. Dependabot ignores it until
  it has a single source, and the lint check is off.
- **LuminalShine wrong-PIN fix, as a contribution to the host.** Added at the
  Sprint 10 UAT (2026-10-21). Filed upstream on 2026-10-29 as
  [NortheBridge/luminalshine#216](https://github.com/NortheBridge/luminalshine/issues/216), where
  the owner plans to make fixes A and C; the owner confirms the scope at planning. On
  LuminalShine 26.10.0-beta.1, a wrong PIN keeps the pairing session for 5
  minutes, and a retry gets 409 until it expires. The owner saw the same symptom
  again in the Sprint 12 round (2026-10-24): a retry shows a new PIN for a moment,
  then the "try again" screen. The app log shows twelve attempts one after another,
  each answered 409 within 0.1 s, about 0.7 to 21 s apart; who started each is not
  recorded. Whether the client should wait on 409 `IN_PROGRESS` is for planning. On
  2026-10-28 (Sprint 14 UAT) the owner saw the lockout as a loop: each Try again, or a
  tap on the card, shows a PIN for about 1 s and then "Another pairing is already in
  progress on the PC."; the log has one request for each attempt, none automatic. The
  fix:
  - Fix A: a fresh phase 1 from the same peer address and the same client
    certificate, after the PIN was applied, ends the old exchange as FAILED and
    starts a new one that needs a new PIN.
  - Fix C: a session stalled after the PIN expires after 30 s instead of 5 min.
    5 min stays for a session still waiting for its PIN.
  - Not chosen: option D, where Borealis sends phase 4 with an invalid secret
    after a wrong PIN, so the host deletes the session at once. The owner has
    not chosen it.
  - An independent review recommended against a `/unpair` cancel route.
  - See [host contract, section 4.3](host-contract/luminalshine.md#43-pairing-status-codes)
    and the [host-side table](#host-side-items-luminalshine).
- **Security observations from the Sprint 12 conversion.** Paths are under
  `apps/android/app/src/main/kotlin/`. Each one is a finding to audit, not a fix.
  - Pinning accepts any certificate the platform trusts; the pin only limits the
    untrusted ones (`com/limelight/nvstream/http/NvHTTP.kt`).
  - A failed identity save is only printed, and a corrupt or missing identity is
    regenerated silently, so every host pairs again with no signal
    (`com/limelight/binding/crypto/AndroidCryptoProvider.kt`).
  - The PIN hash is compared with `Arrays.equals`, which is not constant time
    (`com/limelight/nvstream/http/PairingManager.kt`).
  - `hexToBytes` is lenient (non-hex characters and Unicode digits), and a short host
    answer is zero-padded (`com/limelight/nvstream/http/PairingManager.kt`).
  - Every client sends the constant `uniqueid` `0123456789ABCDEF`
    (`com/limelight/nvstream/http/NvHTTP.kt`).
  - A failed HTTPS serverinfo for a pinned host falls back to plain HTTP
    (`com/limelight/nvstream/http/NvHTTP.kt`).
  - The client id is logged at INFO with "UID is now" (`com/limelight/computers/IdentityManager.kt`).
  - `printStackTrace` writes to stderr, and can carry host names or addresses; the
    Wake-on-LAN sender is one case (`com/limelight/nvstream/wol/WakeOnLanSender.kt`).
  - The `FileNotFoundException` message carries the full URL; a debug log can show it
    (`com/limelight/nvstream/http/NvHTTP.kt`).
  - `defaultNetworkLock` has no `try`/`finally`, so an exception under a VPN wedges
    discovery (`com/limelight/computers/ComputerManagerService.kt`).
  - `waitForPollingStopped` spins and prints stack traces while it is interrupted during
    a poll (`com/limelight/computers/ComputerManagerService.kt`). Not reachable today.
  - Not listed: the debug URL logging of the HTTP client. The `JulBridge` and the
    `Redactor` mask `rikey`, the salt and the client certificate.
- **Addresses in the logs (owner, 2026-10-24).** Local addresses may stay in the logs;
  public addresses, and those of internet overlays such as Tailscale, may not. In every
  line and export, mask the addresses outside 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16,
  loopback and link-local: public IPv4, the CGNAT range 100.64.0.0/10, global IPv6 and the
  Tailscale ULA `fd7a:115c:a1e0::/48`. The central place is the `Redactor` of the
  logging module. moonlight-common-c lines go straight to logcat, unredacted, when the
  forward to `BorealisLog` fails (before it is set up, or on a JNI error): the fallback
  in `BridgeClLogMessage` (`app/src/main/jni/moonlight-core/callbacks.c`) must be
  covered too. The STUN print in `simplejni.c` is dead code since ADR 0050. Host names
  stay visible, and the "Mask addresses" switch still masks every address and the host
  names it recognises. See
  [device-validation section 17](development/device-validation.md#17-results-sprint-12), P2.
- **`findExternalAddressIP4` with a VPN.** Moved here from item (d) of the Sprint 14 block of
  the backlog (owner, 2026-10-28), because this sprint already masks the Tailscale addresses.
  The native is not reached on the LAN: `ComputerManagerService.populateExternalAddress` calls
  it only when the active network is a VPN (the same structure as the inherited Java and
  upstream), so the Sprint 14 round did not reach it (see
  [device-validation section 3.27](development/device-validation.md#327-sprint-14-checks),
  step 10). Test it with a VPN on the device (Tailscale is a VPN on Android): the Library finds
  the host, and the log has no JNI error.
- **CodeQL `java/path-injection` on `PosterContentProvider`.** CodeQL flags the box-art
  file path of the exported provider, on the Java file on `main` and on the converted
  Kotlin file (pull request #70). The host id is checked by `PosterRequest.checkedAppId`,
  which CodeQL does not see as a sanitizer. Owner decision of 2026-10-24: the alerts stay
  open until this sprint, which makes the check visible to CodeQL or dismisses them with
  the reason.

### Sprint 20: release preparation

- The root and Android READMEs.
- An install (Obtainium) and pairing guide.
- The compatibility note. It cites LuminalShine 26.10.x once that is stable.
- Show `LuminalShineVersion` from the HTTPS `/serverinfo` in Host details. The
  26.10.0-beta.1 host sends it (#199, release notes), and the client still shows
  the protocol version `7.1.431.-1`.
- **Host details mapping.** Backlog item from the Sprint 9 quality review. Do it
  with the `LuminalShineVersion` item above, because both change the same mapping.
  `ComputerDetails.toDetailsFacts` in `ComputerHostRepository.kt` (in `:app`) maps
  the whole `Host` with `toHost()` to read one name, the running app. It holds a
  third copy of the online, offline and unknown mapping, and the code has three
  definitions of "no MAC address".
- **LibraryActivity task root.** When the task root is not the launcher intent (for
  example a pinned shortcut opened first), opening the app from the launcher stacks
  a new Library instance. `LibraryActivity` has the standard launch mode; only
  `Game` is `singleTask`. Seen on the Streamer after a start with `am start -n`,
  whose intent has no action or category.
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

### Sprint 21: translation and opt-in crash reporting

- **Translation sprint.** The app is English only until this sprint
  ([ADR 0055](adr/0055-settings-storage-and-the-phase-2-settings-screen.md)).
- **Opt-in crash reporting.** Planned, not implemented. See
  [PRIVACY.md](../PRIVACY.md) and [ADR 0022](adr/0022-crash-reporting-and-privacy.md).
- **Joined strings for the translation sprint.** The `groupLabel` of
  `SegmentedControl`, the `HostCard` sentence and the description of a text field
  join their parts with ", " and ". ".

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
    The owner confirmed this on 2026-10-24, after the Sprint 12 round. The host serves
    each app's image at `/appasset`, and a default `box.png` for an app with none. The
    owner chooses what such an app shows after seeing the covers in the grid.
- **Phase 4: PyroWave.** See the [host contract](host-contract/pyrowave.md).
  The official LuminalShine build ships experimental PyroWave, opt-in and
  needing Vulkan 1.3, in 26.10.0-beta.1 (release notes).
- **Phase 5: stores.** Google Play and Amazon Appstore. See the
  [release policy](policies/release.md) and
  [ADR 0045](adr/0045-store-publishing-automation.md), which is proposed.
- **Phase 6: Windows client.** Post-MVP. Most PC handhelds run Windows. Its order
  relative to Phases 3 to 5 is not decided.

## Backlog, not scheduled

On 2026-10-28 the owner moved nearly all of this backlog to
[Sprint 17](#sprint-17-general-fixes) (general fixes) and
[Sprint 18](#sprint-18-refactor-of-the-converted-code) (refactor of the converted code). Each
block below says where its entries went. The conversion blocks keep their lists (a), (b) and (c) in
place, because the sprints that take them point here.

- **Deferred RTSP parser rework.** The moonlight-common-c rework `aeba988` is
  deferred by the owner. See the [third-party notes](../core/third_party/README.md).

The combined font scales above 2.0 at Normal moved to [Sprint 17](#sprint-17-general-fixes)
(owner, 2026-10-28).

From the Sprint 9 quality review. None of these changes behaviour. Sprint 10 (item 11) took the
deprecated `FocusProperties.enter`, the copied spacing constants and the two background defaults.
The test size item moved to Sprint 14, the quit wording to Sprint 15 and the host details to
Sprint 20. The `MenuChordDetector.update` complexity refactor moved to
[Sprint 18](#sprint-18-refactor-of-the-converted-code) (owner, 2026-10-28). Nothing is left
here.

From the Sprint 9 device round, 2026-10-19. Sprint 10 took the Back from Apps focus, the About
Version row and links (items 9 and 10), and the licence text and the `AuditScreen.kt` `Detail` order
(item 12). The Gradle and AGP items moved to Sprint 19, the `RiskyLibrary` check to Sprint 19, and
the `LibraryActivity` item to Sprint 20. The two layout items (the touch handheld with only
`KEYBOARD | DPAD`, and the Esc row of the stream keyboard in a narrow window) moved to
[Sprint 17](#sprint-17-general-fixes) (owner, 2026-10-28). Nothing is left here.

From the Sprint 10 reviews and its device round (2026-10-21). The other items moved to Sprints 11,
12, 15 and 21. The check of the halo and the tint-only glass on API 28 to 30 moved to
[Sprint 17](#sprint-17-general-fixes) (owner, 2026-10-28). Nothing is left here.

From the Sprint 10 UAT (2026-10-21). The refresh button on Hosts moved to
[Sprint 17](#sprint-17-general-fixes) on 2026-10-28, with the forgotten-host item; the owner
had put it after SecOps (Sprint 19), or post-MVP. The look at why the list does not update moved to
Sprint 15 on 2026-10-28, merged into the item "A forgotten host is not found again until the app
restarts", and from there to Sprint 17 the same day. The list code (`ComputerManagerService`) is
Kotlin from Sprint 12 on. Nothing is left here.

From the Sprint 12 conversion (2026-10-23). Item (a) is scheduled for
[Sprint 18](#sprint-18-refactor-of-the-converted-code) and item (b) for
[Sprint 16](#sprint-16-fixes-of-the-converted-code) (owner, 2026-10-28). Item (c) is listed under
[Sprint 19](#sprint-19-secops), and item (d) went to Sprints 17 and 19. Paths are under
`apps/android/app/src/main/kotlin/com/limelight/`, except `HeldStageFailures.kt`,
`library/UpstreamHostShortcuts.kt` and `library/ComputerManagerConnection.kt`, which are under
`apps/android/app/src/main/kotlin/io/github/magalz/borealis/`.

- **(a) Refactor the converted code.** The 49 places marked `(Sprint 12 backlog)` each
  hold one suppression annotation (detekt rules, and in two places a lint check or a
  compiler warning), so the converted code keeps the shape of the
  inherited Java (ADR 0059, rule 6). Most are structural rules (`ReturnCount`,
  `CyclomaticComplexMethod`, `NestedBlockDepth`, `LongMethod` and similar). Some suppress
  `PrintStackTrace`, where the Java wrote to stderr, and a few suppress `ThrowsCount`,
  `TooGenericExceptionThrown` or `TrulyRandom`. Some comments say that a fix would be a
  behaviour change. By file, with the count:
  - `PosterContentProvider.kt`: 1
  - `binding/crypto/AndroidCryptoProvider.kt`: 5
  - `computers/ComputerDatabaseManager.kt`: 2
  - `computers/ComputerManagerService.kt`: 9
  - `computers/IdentityManager.kt`: 2 (one wraps over two lines)
  - `nvstream/NvConnection.kt`: 5
  - `nvstream/NvConnectionListener.kt`: 1
  - `nvstream/StreamConfiguration.kt`: 1
  - `nvstream/av/video/VideoDecoderRenderer.kt`: 1
  - `nvstream/http/NvHTTP.kt`: 4
  - `nvstream/http/PairingManager.kt`: 2
  - `nvstream/mdns/JmDNSDiscoveryAgent.kt`: 1
  - `nvstream/mdns/MdnsDiscoveryAgent.kt`: 1
  - `nvstream/mdns/NsdManagerDiscoveryAgent.kt`: 1
  - `nvstream/wol/WakeOnLanSender.kt`: 3
  - `preferences/PreferenceConfiguration.kt`: 3
  - `utils/ShortcutHelper.kt`: 1
  - `utils/TvChannelHelper.kt`: 6

  That is 49 suppressions in 18 files.
- **(b) Bugs found during the Sprint 12 conversion.** Only the first is fixed. The conversion keeps
  behaviour (ADR 0059, rule 10).
  - **Priority, fixed by issue #69:** `utils/ShortcutHelper.kt`, `reapShortcutsForDynamicAdd`,
    looped forever when the dynamic shortcut list was full and `forceAdd` was true, because
    it never re-read the list. `library/UpstreamHostShortcuts.kt`, `reportHostOpened`, runs
    it inside `synchronized(publishing)`, so publishing blocked. The upstream code has the same
    loop. The loop now re-reads the list after each removal and stops if a removal does not
    shrink it.
  - `nvstream/http/ComputerDetails.kt`: `update()` overwrites `state`, `name`, `uuid`,
    `rawAppList` and other fields without a condition. `library/ComputerManagerConnection.kt`
    works around it with `withListFrom`.
  - `nvstream/http/ComputerDetails.kt`: `AddressTuple` is mutable, and its `hashCode`
    depends on the mutable fields. A latent hazard, not a live bug.
  - `HeldStageFailures.kt` (`io/github/magalz/borealis/`): does not forward
    `launchRetrying`, `launchStarting` or `launchConflict`. Unreachable today.
  - `preferences/PreferenceConfiguration.kt`: `readPreferences` throws
    `NumberFormatException` on a bad stored `list_fps`, width or height, and no caller
    catches it.
  - `preferences/PreferenceConfiguration.kt`: a stored resolution with no "x" that is not a
    legacy name (for example `"Native"`) is silently rewritten as 1280x720.
  - `preferences/PreferenceConfiguration.kt`: `checkbox_51_surround=true` overwrites a
    user-chosen `list_audio_config`. When it is false, the legacy key stays forever.
  - `preferences/PreferenceConfiguration.kt`: the legacy `seekbar_bitrate` is read but never
    migrated; only the streaming-settings reset removes it.
  - `preferences/PreferenceConfiguration.kt`: `isSquarishScreen(0, y)` gives NaN or
    Infinity, and so returns false.
  - `utils/TvChannelHelper.kt`: uses the logo width as its height. The logo is always square.
  - `grid/assets/DiskAssetLoader.kt`: `LoaderTuple` has `equals` without `hashCode`.
  - `utils/ShortcutHelper.kt`: `disableComputerShortcut` matches by prefix, so a host `ab`
    catches the shortcuts of host `abc`.
  - `PosterContentProvider.kt`: `openFile` serves any three-segment path as box art.
  - `utils/TvChannelHelper.kt`: `setIntentUri` is unused. `requestChannelOnHomeScreen`
    queries the provider twice.
  - `computers/ComputerDatabaseManager.kt`: `updateComputer` throws
    `SQLiteConstraintException` for a null name.
  - `computers/ComputerDatabaseManager.kt`: `getAllComputers` throws on one bad row and hides
    the whole list. `ComputerManagerService.onCreate` calls it without a guard.
  - `computers/ComputerDatabaseManager.kt`: a row with a null uuid is stored and can never be
    deleted.
  - `computers/IdentityManager.kt`: "UID written to disk" is logged before the flush. The
    client id file has no hex check.
  - `nvstream/mdns/NsdManagerDiscoveryAgent.kt`: leaks a `ServiceInfoCallback` when a service
    is found twice. Its executor is never shut down, and `DiscoveryService.kt` has no
    `onDestroy`.
  - `nvstream/wol/WakeOnLanSender.kt`: the port maths overflows above an HTTP port of about
    65514. The `IllegalArgumentException` that follows aborts the whole send.
  - `nvstream/mdns/MdnsDiscoveryAgent.kt`: the IPv6-only add and notify run outside
    `synchronized(computers)`.
  - `nvstream/mdns/JmDNSDiscoveryAgent.kt`: a dead `catch (UnsupportedEncodingException)`.
  - `nvstream/http/NvHTTP.kt`, kept as they are: an app id above `Int.MAX_VALUE` is dropped;
    `status_code` is truncated to 32 bits; text before the first `<App>` throws an unchecked
    `NoSuchElementException`; the release path uses the platform default charset.
  - `nvstream/NvConnection.kt`: `stop()` releases a semaphore permit even when the connection
    never took one, and a failed `startConnection` releases one too. Permits can exceed 1, so
    two connections could start together.
  - `nvstream/NvConnection.kt`: a start whose app has no id finds the app by name but launches
    with `appid=0`. `Game` always passes an id.
  - `nvstream/NvConnection.kt`: the `HostHttpResponseException` catch of the start thread does
    not check `launchCancelled`. The 470 and 525 messages also apply to errors while
    resolving a conflict (Join).
  - `computers/ComputerManagerService.kt`: `waitForPollingStopped` spins and prints stack
    traces while it is interrupted during a poll. Not reachable today.
  - `computers/ComputerManagerService.kt`: `getLocalDatabaseReference` is a non-atomic
    check-then-increment. `runPoll` leaks a database reference if the listener or the
    database throws.
  - `computers/ComputerManagerService.kt`: `populateExternalAddress` leaks `defaultNetworkLock`
    when STUN throws, and has a redundant condition.
  - `computers/ComputerManagerService.kt`: an offline host is re-notified every fourth failed
    poll, and the OFFLINE write counts as a success for the TTL.
  - `computers/ComputerManagerService.kt`: `addComputerBlocking` adds a preset-ONLINE computer
    without a uuid, so the next `addTuple` hits an NPE.
  - `computers/ComputerManagerService.kt`: `startPolling` without discovery throws after it has
    already changed its state. `invalidateStateForComputer` blocks for the length of an
    in-flight poll.
  - `computers/ComputerManagerService.kt`: the mDNS `notifyComputerAdded` polls on the agent's
    single executor.
  - `computers/ComputerManagerService.kt` (low): calling `ApplistPoller.start()` twice orphans
    the first thread. `lastSuccessfulPollMs` is read under a different lock than the one it is
    written under.
  - `computers/ComputerManagerService.kt`: `ReachabilityTuple` is dead code. It is kept.
- **(c) For SecOps (Sprint 19).** The security observations of the conversion are listed
  under [Sprint 19](#sprint-19-secops), one line each, with their files.
- **(d) From the Sprint 12 device round (2026-10-23).**
  - The bridged upstream log lines (`Borealis.UPSTREAM`, from `LimeLog`) carry host names
    and IP addresses in clear, as the inherited code always did. This is not a regression.
    The owner decided on 2026-10-24: host names stay, and public and overlay addresses are
    masked in Sprint 19 (see Sprint 19). See
    [device-validation section 17](development/device-validation.md#17-results-sprint-12), P2.
  - Optional feature: send `continuousAudio=1` in `/launch`. Merged into the "Audio runs" entry
    of item (d) of the Sprint 14 block, which moved to [Sprint 17](#sprint-17-general-fixes) (owner, 2026-10-28).

From the Sprint 13 conversion (2026-10-25). Item (a) is scheduled for
[Sprint 18](#sprint-18-refactor-of-the-converted-code), item (b) for
[Sprint 16](#sprint-16-fixes-of-the-converted-code), the open entries of item (c) for
[Sprint 18](#sprint-18-refactor-of-the-converted-code) and item (d) for
[Sprint 17](#sprint-17-general-fixes) (owner, 2026-10-28). None of the bugs is fixed yet: the
conversion keeps behaviour (ADR 0059, rule 10). Paths are under
`apps/android/app/src/main/kotlin/com/limelight/binding/input/`, unless an item says otherwise.

- **(a) Refactor the converted code.** The 65 places marked `(Sprint 13 backlog)` sit in 22
  files. 64 of them are next to a suppression annotation. The one in `driver/AbstractController.kt`
  marks the never-assigned `supportedButtonFlags`, which is a bug (see (b)). Most suppress
  structural rules (`ReturnCount`, `LongMethod`, `ComplexCondition`, `CyclomaticComplexMethod`,
  `TooManyFunctions`, `LoopWithTooManyJumpStatements`, `LongParameterList`, `NestedBlockDepth`
  and `LargeClass`). Some suppress `PrintStackTrace`, where the Java wrote to stderr, and seven
  suppress `DEPRECATION`. A few suppress `TooGenericExceptionCaught`, `UnusedPrivateProperty`,
  `ObjectLiteralToLambda` or `AbstractClassCanBeConcreteClass`. Ten of the marked places carry
  an Android lint suppression instead: `@SuppressLint("ViewConstructor")` on the seven
  on-screen controller views, `@SuppressLint("InlinedApi")` on two declarations (one in
  `ControllerHandler.kt`, one in `KeyboardTranslator.kt`) and
  `@SuppressLint("UnspecifiedRegisterReceiverFlag")` on the receiver of `UsbDriverService.kt`.
  A third `InlinedApi`, on the button map of `ControllerHandler.kt` (`ANDROID_TO_LI_BUTTON_MAP`,
  line 3866), has no marker. By file, with the count:
  - `ControllerHandler.kt`: 28 (three of its markers wrap over two lines)
  - `KeyboardTranslator.kt`: 1
  - `capture/AndroidNativePointerCaptureProvider.kt`: 2
  - `capture/InputCaptureProvider.kt`: 1
  - `driver/AbstractController.kt`: 1
  - `driver/AbstractXboxController.kt`: 2
  - `driver/UsbDriverListener.kt`: 1
  - `driver/UsbDriverService.kt`: 6
  - `driver/Xbox360Controller.kt`: 1
  - `driver/Xbox360WirelessDongle.kt`: 2
  - `driver/XboxOneController.kt`: 3
  - `touch/AbsoluteTouchContext.kt`: 1
  - `touch/RelativeTouchContext.kt`: 1
  - `virtual_controller/AnalogStick.kt`: 2
  - `virtual_controller/DigitalButton.kt`: 2
  - `virtual_controller/DigitalPad.kt`: 2
  - `virtual_controller/LeftAnalogStick.kt`: 1
  - `virtual_controller/LeftTrigger.kt`: 1
  - `virtual_controller/RightAnalogStick.kt`: 1
  - `virtual_controller/RightTrigger.kt`: 1
  - `virtual_controller/VirtualControllerConfigurationLoader.kt`: 4
  - `virtual_controller/VirtualControllerElement.kt`: 1

  That is 65 places in 22 files.
- **(b) Bugs and oddities found during the conversion, by area.** Each one is kept as it was.
  - **Keyboard and capture** (`KeyboardTranslator.kt`, `capture/`):
    - A negative key code for a device with a layout throws `ArrayIndexOutOfBoundsException` in
      `translate` and `hasNormalizedMapping`: only the upper bound is checked.
    - A key code above `KeyEvent.getMaxKeyCode()` from a device crashes the `KeyboardMapping`
      constructor.
    - The delayed pointer-capture request (500 ms) checks for a capture device when it fires,
      not whether capture is still on. `disableCapture()` within those 500 ms still ends with
      `requestPointerCapture()`.
    - `hideCursor()` and `showCursor()` register and unregister the device listener
      unconditionally. `showCursor()` releases a capture that was never requested (harmless).
    - `InputCaptureManager` logs "Android O+ native mouse capture", though there is one provider.
    - The Windows `VK_*` codes are duplicated in `borealis/stream/.../KeyboardLayout.kt`.
  - **Touch** (`touch/`):
    - `RelativeTouchContext`: the release runnables of the middle, X1 and X2 buttons are
      unreachable, because `getMouseButtonIndex` gives only left or right.
    - A view of width or height 0 gives an infinite scale factor, so a delta becomes -1.
    - In a two-finger scroll, an x-only move sends `sendMouseHighResScroll(0)`.
    - `AbsoluteTouchContext.lastTouchLocationX` is written and never read.
    - A first tap within 250 ms of time 0 near (0,0) skips the reposition (unreachable with uptime).
  - **USB drivers** (`driver/`):
    - `AbstractController.supportedButtonFlags` is never assigned, so a USB pad's arrival reports
      0 supported buttons to the host.
    - An Xbox One pad shows Guide pressed in its first reports, until a 0x07 report.
    - `stop()` sends `rumble(0, 0)`, which keeps the Xbox One trigger motors running. A host of
      LuminalShine 26.10.0 or later can send trigger rumble (control message 0x5500, from the VHF
      backend; [contract, section 12](host-contract/luminalshine.md#12-control-stream)), so
      this path can run now. The VHF round of 2026-10-28 did not test trigger rumble.
    - Races: `stopped` is not volatile, and `controllers` is not synchronized while `stop()` on
      the input thread re-enters `deviceRemoved`. A `ConcurrentModificationException` or a
      skipped entry is possible.
    - An attach broadcast with no device throws a `NullPointerException` after 1 s on the main
      looper; a permission broadcast with no device throws at once.
    - `Xbox360WirelessDongle.start()` always returns false, so its `stop`, `rumble` and
      `rumbleTriggers` never run, and the `else` of the claim chain is unreachable.
    - Y axes: `~raw / 32767f` gives about -1.00003 at the extreme. The Xbox One triggers are not
      clamped: values above 1023 or negative pass through.
    - A blocked `bulkTransfer` ignores `interrupt()`, so after `stop()` the input thread lingers
      up to the 3 s timeout.
    - `onCreate`, `onDestroy` and `onReceive` of the service do not call `super`.
    - The Kotlin reads `listener` once where the Java read it twice. The review accepted this:
      a race that threw on the input thread no longer throws.
  - **On-screen controller** (`virtual_controller/`):
    - The Y button's flag `0x8000` is sign-extended from a `short` into `reportOscState(int)`,
      so the packet carries `0xFFFF8000`. This is latent: `moonlight-common-c` masks negative
      button flags with `0xFFFF` (`core/third_party/moonlight-common-c/src/InputStream.c`, lines
      1012 to 1021).
    - A finger exactly above or below the stick's centre gets the opposite angle. One pixel off
      is right.
    - The first stick touch within 350 ms of time 0 is a double click.
    - A stick whose way is 0 pixels gives NaN (0/0), sent as `(short)NaN = 0`.
    - MOVE or UP with no DOWN inside the stick's circle sends revoke and a movement of (0,0).
    - The right-down line of the pad starts 5 px further out than the other lines.
    - A second finger is answered true even when the element refuses it.
    - The icon branch of `DigitalButton` is unreachable: `setIcon` is always -1.
    - A corrupt saved-layout entry applies its earlier numbers before the `JSONException`.
    - `keyLong` is always 0 in `createDigitalButton`, so a long click only resends the same state.
    - `createDefaultLayout` reads and writes the default preferences on every call.
  - **ControllerHandler** (events, devices and feedback):
    - Combining: `x |= maxByMagnitude(x, v)` can send bits of two pads on one controller number.
      Ties take the later pad.
    - `handleSetMotionEventState` stops after the first pad with a matching controller number
      that has a sensor manager (a matching pad without one is skipped with `continue`). So when
      two pads on one number both have a sensor manager, only the first gets the state.
    - `handleRumbleTriggers` passes a possibly null `vibratorManager` to `rumbleQuadVibrators`.
      A host of LuminalShine 26.10.0 or later (VHF backend) can send trigger rumble now, so
      this path can run. The VHF round of 2026-10-28 did not test trigger rumble.
    - The controller-input fusing block is written out three times (about lines 1479 to 1514 of
      the Java).
    - The `gestures` constructor property is stored and never read.
    - `(byte)(lt * 0xFF)` turns negative above 0.5; `(short)(x * 0x7FFE)` wraps past plus or
      minus 1.
    - The held-time `(int)` cast: a hold of 2^32 + 10 ms looks like 10 ms. A hold of 2^31 ms or
      more makes the sleep negative, and `handleButtonUp` throws `IllegalArgumentException`.
    - An unknown paddle scan code makes the whole handler return false: no packet, and no
      emulation checks.
    - The raw D-pad codes 704 to 707 are tried first, so 0x2c3 is Down on a pad with paddles.
    - A zero-width touchpad range divides by zero and sends NaN coordinates.
    - A key up for a button that is not down still sends a packet, and key repeats resend.
    - `onInputDeviceChanged` forgets the held buttons and turns the gamepad mouse off for that
      device.
    - Removing a pad that shares controller 0 zeroes it for the pad that is left.
    - The 17th pad shares controller 0 without reserving it. The mask is `1 << count` and wraps
      at 16.
    - `isExternal` matches `gpio`, `joy_key` and `keypad` case-sensitively; the other names are not.
    - A Shield comment says small stick deadzones, but the code only sets `searchIsMode` and
      `hasMode`.
    - ADT-1 and ASUS set the trigger deadzone with no trigger axes. ADT-1 forces `hasSelect` and
      `hasMode`; Thrustmaster Score A forces `hasMode = false`; the Xbox One S firmware check
      tests only gas.
    - Any non-PlayStation pad with a sensor manager is reported as UNKNOWN, shows a toast and
      gets clickpad emulation.
    - Sensors: a first all-zero reading is dropped as a repeat; a negative rate gives a negative
      period; a second pad on the same number gets no sensor registration; `destroy()` leaves the
      listeners set, so a later `disableSensors()` sends a stationary gyro event.
    - Battery: the percentage goes through a float and a truncating byte (53 becomes 52, 59
      becomes 58; out of range wraps). A host of LuminalShine 26.10.0 or later accepts a battery
      state for a DualShock 4, a DualSense or a Switch Pro Controller
      ([contract, section 13](host-contract/luminalshine.md#13-input)), so a value can reach a
      game now. Android showed no battery for either pad in the VHF round of 2026-10-28, so
      the path did not run. `LI_BATTERY_STATE_NOT_PRESENT` is never used, and an
      unknown status is retried at every poll.
    - Rumble: small motors truncate to an amplitude of 0 and cancel the vibration.
      `sceManager.rumble` gets sign-extended shorts (0xC000 arrives as -16384), which is probably
      a bug. A USB pad that sent no input answers to controller 0.
- **(c) Test and tooling items.**
  - **Done in Sprint 14 (`s14-format`, merge `728aae024`).** Spotless (ktfmt) covered
    `borealis/**` and `build-logic/**`, tests included. Of `:app`, only `app/src/main/kotlin`
    was formatted, so `app/src/test` was not. Detekt covers the tests. Spotless now formats
    `app/src/test` and `app/src/androidTest` too.
  - **Done in Sprint 14 (`s14-format`, merge `728aae024`).** `.gitattributes` marked
    `apps/android/**` as `-text`, so the Borealis Kotlin under `app/src` mixed CRLF and LF line
    endings. `apps/android/app/src/**/*.kt` is now `text eol=lf`.
  - **Done in Sprint 14 (`s14-tests-app`, merge `0e709993e`).** Test helpers repeated across the
    Sprint 13 test packages (rigs, `MoonBridge` shadows and the `ANDROID_xx` constants). They are
    folded: the `MoonBridge` shadows went from 7 to 2, and the shared `testing/` package holds
    the API level constants, the `LimeLog` capture, the pad-context injection, the coroutine
    helpers and the rule that closes what a test owns.
  - **Done in Sprint 14 (`s14-tests-app`, merge `0e709993e`).** About 300 idle
    `HandlerThread("ControllerHandler")` threads stayed alive in the test JVM, because tests
    rarely called `destroy()`. The tests destroy the handlers they start: the probe found 705
    idle threads among 777 at the end of the `:app` suite, and finds none now.
  - One wall-clock test: `ButtonTimingCharacterizationTest` compares real sleep times with a
    25 ms threshold. It failed in two runs of the phase review, because on Windows a 1 ms sleep
    can take a whole 15.6 ms timer tick. The phase fixes loosened it, and the `waited` test pins
    the same threshold without the clock.
  - Robolectric caches its sandboxes up to a limit (3 times the supported SDK levels, 42 in
    Robolectric 4.17) and evicts the least recently used one. Each distinct
    `@Config(shadows = ...)` set and SDK level needs a sandbox. The phase review counted 32
    distinct sandboxes at `895d0419a`, and the `:app` test heap peaked at 1.17 GB of its 2 GB
    (the heap is 2 GB since Sprint 13).
- **(d) Found in the Sprint 13 device round (2026-10-26), on the S22.** None is a Sprint 13
  regression. The results are in
  [device-validation section 18](development/device-validation.md#18-results-sprint-13).
  The four entries (the trackpad drag, direct touch, Alt+Tab, and the Ultimate 2 with Home held)
  moved to [Sprint 17](#sprint-17-general-fixes), under Input (owner, 2026-10-28).

From the Sprint 14 conversion (2026-10-27). The owner decided on 2026-10-27 that the defects
of item (b) are fixed after Sprint 14. On 2026-10-28 the owner scheduled item (a) for
[Sprint 18](#sprint-18-refactor-of-the-converted-code), item (b) for
[Sprint 16](#sprint-16-fixes-of-the-converted-code), item (c) for
[Sprint 18](#sprint-18-refactor-of-the-converted-code) and item (d) for Sprints 17
and 19, as item (d) says. None of the defects is fixed yet: the conversion keeps behaviour (ADR
0059, rule 10), and each one is kept as it was and pinned by a test that passes on the Java and on
the Kotlin, so a fix changes that test on purpose. Paths are under
`apps/android/app/src/main/kotlin/com/limelight/`, unless an item says otherwise. A pinning test is
named by its class and its test. The tests are under `apps/android/app/src/test/kotlin/`: in
`io/github/magalz/borealis/stream/game/` for `Game` (the folders `keys/`, `motion/` and `session/`,
and the files of the folder itself), in `com/limelight/binding/video/` for the renderer and
`MediaCodecHelper`, in `com/limelight/binding/audio/` and in `com/limelight/nvstream/jni/`. Where no
test is named, none pins the item.

- **(a) Refactor the converted code.** The 52 places marked `(Sprint 14 backlog)` each sit next
  to a suppression annotation, so the converted code keeps the shape of the inherited Java
  (ADR 0059, rule 6). Most suppress structural rules (`CyclomaticComplexMethod`, `LongMethod`,
  `ReturnCount`, `NestedBlockDepth`, `ComplexCondition`, `LoopWithTooManyJumpStatements`,
  `LongParameterList`, `LargeClass` and `TooManyFunctions`). Some suppress `PrintStackTrace`,
  where the Java wrote to stderr, and `TooGenericExceptionCaught`. Four suppress
  `ForbiddenComment`, for the inherited TODOs. One place each suppresses `FunctionNaming`,
  `UnusedPrivateFunction` (the parked touch path), `SwallowedException`,
  `AbstractClassCanBeInterface`, and `ThrowsCount` with `TooGenericExceptionThrown`. A few carry a
  compiler warning (`DEPRECATION`, `OVERRIDE_DEPRECATION` and
  `PLATFORM_CLASS_MAPPED_TO_KOTLIN`) or an Android lint suppression
  (`SourceLockedOrientationActivity`, `InlinedApi` and `ClickableViewAccessibility`). By file,
  with the count:
  - `Game.kt`: 17
  - `binding/video/MediaCodecDecoderRenderer.kt`: 13
  - `binding/video/MediaCodecHelper.kt`: 12
  - `nvstream/jni/MoonBridge.kt`: 8
  - `binding/audio/AndroidAudioRenderer.kt`: 1
  - `nvstream/av/video/VideoDecoderRenderer.kt`: 1

  That is 52 places in 6 files. Also:
  - `Game.setMetaKeyCaptureState` has no caller outside `Game` (the tests reach it through the
    grab), so it could be private.
  - The `@SuppressLint("InlinedApi")` on `hideSystemUi` is probably not needed, because `minSdk`
    is 28.
  - Six helpers of `Game` were `private static` in the Java and are private instance methods now
    (`normalizeValueInRange`, `getPressureOrDistance`, `getRotationDegrees`, `polarToCartesian`,
    `cartesianToR` and `convertToolTypeToStylusToolType`). They use no state of `Game`.
  - `VideoDecoderRenderer` could become an interface (its `AbstractClassCanBeInterface`
    suppression). `MediaCodecDecoderRenderer` extends it and calls its constructor, so the two
    change together.
  - `MediaCodecHelper.getAdrenoVersionString` keeps `trim { it <= ' ' }`, which a lint hint
    (`TrimLambda`) would replace. The Java `trim()` removes every character up to the space, not
    only whitespace, so the lambda keeps the behaviour.
  - `SettingsRepository.setNow` (`borealis/settings`) has no caller outside the tests. The owner
    decides whether it goes.
  - No Java is left, so the Java-facing annotations that converted classes kept for Java callers
    (`@JvmStatic`, `@JvmField`, `@JvmOverloads` and `@Throws`) are not needed for them any more.
    At `a74266ec1` the app Kotlin holds 151, 205, 2 and 61 of them, `MoonBridge.kt` 57 `@JvmStatic`
    and 5 `@JvmField`. Dropping one is not mechanical:
    - The C code calls only static methods of `MoonBridge`, by name and descriptor
      (`GetStaticMethodID` in `callbacks.c` and `borealis_log.c`; it looks up no field), and the
      natives are `@JvmStatic external fun`. Keep those.
    - `MoonBridgeStaticInitializerTest` reads the `AUDIO_CONFIGURATION_*` fields, and
      `channelCount` and `channelMask`, by name, so they keep `@JvmField`.
    - Five reflection tests pin the Java-visible API: `InputJavaVisibleApiTest`,
      `ComputerStoreJavaVisibleApiTest`, `JavaVisibleApiTest` (crypto), `NvHttpJavaApiTest` and
      `SupportJavaVisibleApiTest`. A commit that drops an annotation changes or removes the
      matching test in the same commit.
    - Other tests use reflection too (`ControllerHandlerRig` and `OscRig`).
- **(b) Bugs and oddities found during the conversion, by area.** Each one is kept as it was.
  - **Keys** (`Game.kt`):
    - `onWindowFocusChanged` clears `modifierFlags` but not `specialKeyCode` and
      `waitingForAllModifiersUp`. After a focus change the next key can quit, toggle the grab or
      toggle the cursor. A real defect, low. Fix: reset both. Pinned by `GameSpecialKeysTest`, "a
      chord that waits is done by the next key when the focus change forgot the modifiers".
    - A code point above U+FFFF from a hardware keyboard is narrowed to 16 bits (U+1F600 arrives
      as U+F600). Latent, very low. Pinned by `GameKeySendTest`, "a character is sent as UTF-8
      text, and one outside the basic plane loses its high bits".
    - The key ups of a chord that waits for the modifiers go through to the host, which sees the
      key up of a key whose key down it never saw. Pinned by `GameSpecialKeysTest`, "while the
      chord waits for the modifiers, the keys down are eaten and the keys up go through".
  - **Motion** (`Game.kt`):
    - `ACTION_CANCEL` from a mouse or a stylus sends a pen `CANCEL_ALL` and returns before the
      buttons and `lastButtonState`. Pinned by `PenEventsTest`, "a cancel of a mouse is a pen
      cancel too, whatever the tool".
    - Grouped buttons press the shared host button again at every change: releasing one while
      the other is down sends a press. Pinned by `MouseButtonsTest`, "two buttons of a group
      share the host button, which is pressed again at every change".
    - On Samsung DeX, `ACTION_DOWN` adds the primary button, so a right click is left plus
      right. Pinned by `MouseButtonsTest`, "the DeX touchpad right click comes with the left
      button that it did not send".
    - A mouse event that reaches the activity itself (`onGenericMotionEvent`) has no view, so
      the position is not sent, and the buttons still are. Pinned by `MouseMovementTest`, "the
      activity itself has no view and does not place the pointer" (the position) and "the
      activity itself still sends the buttons".
    - `controllerHandler.handleMotionEvent` always answers true, so the `return false` after it
      in `handleMotionEvent` is dead. Pinned for a known pad by `MotionGateTest`, "a joystick
      source goes to the controller handler, which sends the stick to the host".
    - `getStreamViewRelativeNormalizedXY` divides by the width and the height of the stream view
      without a guard. No test pins it.
    - On the trackpad, a tap of two fingers presses the right button only when the second finger
      lifts first, and it does so at that lift. When the first finger lifts first, nothing is
      sent. Pinned by `FingerTouchTest`, "when the second of two fingers lifts, the first goes on
      moving the pointer from where it was" and "when the first of two fingers lifts, the second
      takes its place as the finger that moves".
  - **Session** (`Game.kt`):
    - `onUsbPermissionPromptCompleted` can arrive without a matching starting call:
      `UsbDriverService.kt` calls its listener with `?.`, so `Game` can get a completed call for
      a prompt whose starting call reached no listener. `suppressPipRefCount` then goes to -1,
      and the auto-enter of picture-in-picture stays off until a dialog opens. Fix: clamp it at
      0. Pinned by `GameUsbServiceTest`, "a dialog that closes without having opened leaves the
      count below zero".
    - Leaving before the connection starts skips the end of the audit session and `conn.stop`,
      and delays the stop of the controller handler, because `stopConnection` acts only when
      `connected` (the `connecting` flag was removed as never true). `onDestroy` still stops the
      controller handler, so for it this is a delay, not a leak. The effect on a device is not
      checked.
      Pinned by `GameStopTest`, "onStop leaves the controller handler of a stream that never
      connected to onDestroy" and "onStop leaves the native library alone when no connection
      started".
  - **Renderer** (`binding/video/`):
    - `stop()` and `cleanup()` throw a `NullPointerException` before `start()` and `setup`
      (`stop()` first tells the decoder to stop). Pinned by `RendererLifecycleTest`, "stop before
      start fails with a null pointer after it has told the decoder to stop" and "cleanup before
      setup fails with a null pointer".
    - `getPreferredColorSpace` is a constant (Rec. 709). Pinned by `RendererCapabilitiesTest`,
      "the preferred colour space is Rec 709".
    - The wait of the codec recovery has no check for `stopping`. The comment in the code says
      why: a thread that gives up in a half-quiesced state leaves the others asleep. The wait
      ends when the recovery is done, when `prepareForStop` cancels it or when the thread is
      interrupted, so the risk is a recovery whose other threads never come, with no stop
      (a deadlock). The closest pin is `RendererThreadsTest`, "a thread that is interrupted while
      it waits for the others gives up the recovery", which covers the interrupt path only.
    - `VideoStats.add` takes a minimum host latency of 0 when the newer window has none. Pinned
      by `RendererStatsTest`, "the lowest host latency reads zero when the newer window has none".
    - `replaySps` always writes profile 100 (High), and `needsBaselineSpsHack` is cleared before
      the replay. Pinned by `RendererSubmitH264Test`, "the replay after the baseline hack says
      High profile whatever the stream said" and "the baseline hack is used once".
    - `replaySps` calls `fetchNextInputBuffer()` and ignores its result. The call finds the
      buffer that the queue call before it already fetched, so it does nothing. The owner
      removed its failure check on 2026-10-27 and kept the call. No test pins it.
    - `numFramesIn` counts a frame before the check for an oversize decode unit, so the crash
      text counts the frame that crashed. Pinned by `RendererCrashTest`, "the text lists the
      decoders, the formats, the device and the counters" (`Frames in-out: 1, 0`).
    - `setHdrMode` before `setup` also marks a codec restart, before any codec exists. Only the
      format is pinned: `RendererHdrTest`, "HDR metadata set before setup goes into the format as
      a static info of 25 bytes".
    - A frame number of 0 never flips the stats window, because the window start is set again
      while `lastFrameNumber` is 0. No test pins it (the tests start at 1).
    - `RendererException.getMessage` can be null. Pinned by `RendererCrashTest`, "the exception
      has no message of its own, only its text".
    - A redundant `else if (!e.isRecoverable)` in `handleDecoderException` follows
      `if (e.isRecoverable)`. It changes no behaviour, so no test pins it.
  - **MediaCodecHelper** (`binding/video/`):
    - `initialize` sets `initialized` only at its end, so a retry after an exception adds the
      Amazon entries again. No test pins it.
    - The Adreno pattern takes the last three digits of the renderer text, so "6400" gives 400
      (no test pins this), and an "Adreno" with no three-digit model counts as another GPU
      (pinned by `MediaCodecHelperInitializeTest`, "the Adreno model decides the Qualcomm HEVC
      decoders").
    - `findKnownSafeDecoder` asks for `getCapabilitiesForType` twice for a decoder in the first
      round (its own call, and `decoderSupportsAndroidRLowLatency`). No test pins it.
    - Two checks return before the check for `initialized`, so they answer before `initialize`
      where the others throw. Pinned by `MediaCodecHelperUninitializedTest`, "a decoder that
      answers without a list also answers before initialize" and "the decisions on a decoder
      throw before initialize unless they answer first".
    - The "sw" checks are case sensitive. Pinned by `MediaCodecHelperCapabilityDecisionsTest`, "a
      decoder with sw in its name is never whitelisted for HEVC" (its row "the match is case
      sensitive").
    - The search for the required profile is skipped when `findKnownSafeDecoder` throws for a
      decoder: the search goes to the first decoder. Pinned by `MediaCodecHelperSelectionTest`,
      "a decoder that throws for its capabilities sends the search to the first decoder".
  - **Audio** (`binding/audio/AndroidAudioRenderer.kt`):
    - A `release()` that throws keeps the track, and setup answers 0 with a track that never
      played. Pinned by `AudioRendererSetupCharacterizationTest`, "a track that cannot be
      released stays, and setup answers 0 without a track that played".
    - A second setup leaks the first track. Pinned by `AudioRendererSetupCharacterizationTest`,
      "a second setup creates another track and leaves the first one alone".
    - `getMinBufferSize` is asked even when both low latency attempts are skipped. Pinned by
      `AudioRendererSetupCharacterizationTest`, "a hardware rate that is not the rate of the
      stream skips both low latency attempts".
    - A packet of no samples (`samplesPerFrame` 0) throws `ArithmeticException` in the second
      attempt. Pinned by `AudioRendererSetupCharacterizationTest`, "a packet of no samples makes
      the second attempt divide by zero".
    - `cleanup` does not set the track to null, so a second cleanup is refused by the platform.
      Pinned by `AudioRendererPlaybackCharacterizationTest`, "cleanup keeps the released track,
      so a second cleanup is refused by the platform".
    - The pending duration is read twice when it is too long: once for the check and once for
      the log. Pinned by `AudioRendererPlaybackCharacterizationTest`, "40 milliseconds of pending
      audio drops the samples and logs the duration read again".
    - `track` is not `@Volatile`, though the native threads of the audio stream use it. A
      memory-model matter, so no test pins it.
    - A comment on the 7.1 mask `0x18FC` still says that the constant is hardcoded for Lollipop.
      Only the value is pinned: `AudioRendererSetupCharacterizationTest`, "8 channels ask for the
      7 point 1 surround mask".
  - **MoonBridge** (`nvstream/jni/MoonBridge.kt` and `app/src/main/jni/moonlight-core/`):
    - `VIDEO_FORMAT_MASK_10BIT` is `0x2200` in Kotlin and `0xAA00` in `Limelight.h`, which also
      counts the 4:4:4 formats. It has no effect today, because the client never asks for them.
      Pinned by `MoonBridgeJniContractTest`, "the constants that Limelight h defines have the same
      value in MoonBridge" (this is its only difference) and "the 10-bit video mask of MoonBridge
      keeps the two 10-bit formats the client asks for".
    - `AudioConfiguration.equals` compares `toInt()`, so `AudioConfiguration(0x100, 0)` equals
      `AudioConfiguration(0, 1)`. Pinned by `AudioConfigurationTest`, "equality is decided by the
      packed int, not by the two fields".
    - `simplejni.c` keeps unreachable code after the early returns of `findExternalAddressIP4` and
      `testClientConnectivity` (ADR 0050). It is C that the unit tests do not run, so no test
      pins it.
    - Ten bridges of `callbacks.c` do not check for a Java exception after the call: the start,
      stop and cleanup of the video and the audio renderer, the three stage ones and
      `BridgeClConnectionStarted`. This is upstream code, and no test runs it.
- **(c) Test and tooling items.**
  - `GameWiringTest` (`io/github/magalz/borealis/stream/`) keeps 6 tests that read text: 3 read
    `NvConnection.kt` and 3 read `src/main/res`. Replace them with behaviour tests.
  - Test code repeats across modules, and sharing it needs `testFixtures` build changes:
    `MainDispatcherRule` is in `:borealis:library` and `:borealis:settings`,
    `WorkerDispatcherRule` is in `:borealis:diagnostics`, `:borealis:settings` and `:app`, and
    the `PHONE`, `TV` and `HANDHELD` device qualifiers are declared in each module that uses them.
  - Smaller leftovers:
    - `ANDROID_NS` is declared in 3 test files of `:app` (`BackOptOutTest`,
      `ManifestActivitiesTest` and `ShortcutIconTest`).
    - 24 literals of the form `@Config(sdk = [30])` remain in the `:app` tests, and could use the
      constants of `AndroidLevels`.
    - The `BorealisCoverageTest` test (in `build-logic`) is named "the Java that sprint 14
      converts is counted", and no Java is left.
    - `apps/android/borealis/README.md`, line 16, says that "Upstream Java/Groovy/C is never
      formatted".
  - Robolectric has 39 distinct configurations at the end of Sprint 14 (41 before `s14-tests-app`).
    The limit is 42 sandboxes, so a new one needs a look at what the tests share.
- **(d) Found in the Sprint 14 device round (2026-10-28).** The owner put these in the backlog on
  2026-10-28: the audio runs and `findExternalAddressIP4` from the agent part, the rest from the
  owner's part and the UAT. The owner organized them the same day, in two steps. First, four moved
  to [Sprint 15](#sprint-15-the-stream-screens-and-glass) (the "Audio effects" text, the Add
  host field keyboard, a sample vibration and the TV focus after the host Options sheet), and the
  `findExternalAddressIP4` test with a VPN moved to [Sprint 19](#sprint-19-secops). Then,
  later the same day, all of the rest went to [Sprint 17](#sprint-17-general-fixes): the audio
  runs and the audio behind the picture (Audio), the pad battery and the pad gyro (Pads), and the
  four of the first step (Library and Settings). Nothing is left here, and none is a Sprint 14
  regression. The results are in
  [device-validation section 19](development/device-validation.md#19-results-sprint-14);
  each entry names its source there (the rows of the table, and the findings U1 to U12).

## Host-side items (LuminalShine)

These are proposals and known issues in the LuminalShine host. The client works
without them. The states are as of 2026-10-29 (read with `gh` that day). #199, #200 and #203 were merged
on 2026-10-20, and 26.10.0-beta.1 (a prerelease of 2026-10-20) ships them
(release notes). #194 and #202 are fixed on the host (release notes). On 2026-10-29 the owner
closed #204 (as a duplicate of #212) and #205 (as fixed), and filed five issues, #216 to #220,
and five discussions in the Ideas category, #221 to #225, from the findings of Borealis. The
open ones are #198, #201, #212, #214, #216 to #220 and the owner's pull requests #213 and #215
(#198 and #201 were checked again on 2026-10-29 and are still open). The rows add what the Sprint 15 round saw
on 2026-10-29 and 2026-10-30 ([device-validation section 20](development/device-validation.md#20-results-sprint-15));
those observations were not read from GitHub, so the states stay as of 2026-10-29.

| Item | Kind | Summary |
| --- | --- | --- |
| [NortheBridge/luminalshine#194](https://github.com/NortheBridge/luminalshine/issues/194) | Issue | Stray bytes after a 404 response. Fixed on the host by #200, in 26.10.0-beta.1 (release notes). See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#198](https://github.com/NortheBridge/luminalshine/issues/198) | Issue | Teardown hang after a disconnect. Open. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#199](https://github.com/NortheBridge/luminalshine/pull/199) | PR | Reports the LuminalShine release in `/serverinfo`, so the client can show the host version. Shipped in 26.10.0-beta.1 (release notes). The client shows it in Sprint 20. |
| [NortheBridge/luminalshine#200](https://github.com/NortheBridge/luminalshine/pull/200) | PR | Sends a single response for unknown GameStream paths. Fix for #194. Shipped in 26.10.0-beta.1 (release notes). |
| [NortheBridge/luminalshine#201](https://github.com/NortheBridge/luminalshine/issues/201) | Issue | A second client leaving a shared app ends the first client's session (per-client virtual display). Open. See [UC-003](use-cases/0003-join-or-replace-a-running-app.md). On 2026-10-29 a run with two clients on a diagnostics build of the host (a nightly, `0.0.5-nightly`, not 26.10.1) saw the first client keep streaming through three Disconnects of the second, and the second client's Quit stop both sessions with `reason=explicit_cancel` (the first client then dropped with -1). It does not say whether the case of the issue is fixed. |
| [NortheBridge/luminalshine#202](https://github.com/NortheBridge/luminalshine/issues/202) | Issue | A long `/launch` blocks every other HTTPS GameStream request until it finishes. Fixed on the host by #203, in 26.10.0-beta.1 (release notes). See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#203](https://github.com/NortheBridge/luminalshine/pull/203) | PR | Fix for #202. Merged on 2026-10-20 and shipped in 26.10.0-beta.1 (release notes). |
| [NortheBridge/luminalshine#204](https://github.com/NortheBridge/luminalshine/issues/204) | Issue | The host process crashes after the virtual display cleanup of an ended session. Closed on 2026-10-29 as a duplicate of #212: the same exception (`0x20474343`), at the same offset in `KERNELBASE.dll`, right after the same log line. The crash is not fixed: see #212. It was seen twice on 2026-10-28 on 26.10.0-beta.1, at 06:58:56 and 09:16:06, in the Sprint 14 round ([device-validation section 19](development/device-validation.md#19-results-sprint-14)). See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#205](https://github.com/NortheBridge/luminalshine/issues/205) | Issue | A second client's resume fails the RTSP tag check while the first client's launch is still setting up. Closed on 2026-10-29 as completed, with a comment: seen on 26.09.1, fixed in 26.10.0 by #188 (a retryable 409 while another client's launch is pending) and #203. The issue is to be reopened if it reproduces on 26.10.1 or later. Borealis keeps its retries (the 3-attempt `/serverinfo` read, the 3 s wait before `/resume` and the single restart after an RTSP -1): 26.09.1 has neither fix, and the race was not tested on a host that has them. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#212](https://github.com/NortheBridge/luminalshine/issues/212) | Issue | The host crashes with an unhandled C++ exception right after "Virtual display cleanup: finished" on a client disconnect: 3 times in about 35 disconnects on 2026-10-28 on 26.10.1, and twice that day on 26.10.0-beta.1. The cause, from the minidumps: the display-helper watchdog joins its own thread. It happened again on 2026-10-30 at 00:21:40 on 26.10.1, right after the same line. It voided the agent's retest of "another app" in the Sprint 15 round. Open; the fix is #213. See the [host contract](host-contract/luminalshine.md), section 7.3. |
| [NortheBridge/luminalshine#213](https://github.com/NortheBridge/luminalshine/pull/213) | PR | The owner's fix for #212: the watchdog no longer joins itself on teardown. Open. |
| [NortheBridge/luminalshine#214](https://github.com/NortheBridge/luminalshine/issues/214) | Issue | Hardens the display-helper watchdog start and stop: the thread object is not synchronized, and the watchdog's "never reap" rule has no test. Follow-up to #212. Open. |
| [NortheBridge/luminalshine#215](https://github.com/NortheBridge/luminalshine/pull/215) | PR | The owner's fix for #214, stacked on #213. Open. |
| [NortheBridge/luminalshine#216](https://github.com/NortheBridge/luminalshine/issues/216) | Issue | Wrong-PIN lockout, filed 2026-10-29 (it was the finding "Wrong-PIN lockout on 26.10.0-beta.1", not filed until then): after a wrong PIN, the host keeps the pairing session, and every retry gets 409 for up to 5 minutes. Restarting LuminalShine clears it. Seen live on 2026-10-21 and 2026-10-24 and checked in source at `dba21a9e` (the lines are the same since 26.10.0-beta.1). The owner plans to fix it: a new phase 1 from the same peer and certificate ends the old exchange (A), and a session stalled after the PIN expires after 30 s (C). Open. See the [host contract](host-contract/luminalshine.md) and [Sprint 19](#sprint-19-secops). |
| [NortheBridge/luminalshine#217](https://github.com/NortheBridge/luminalshine/issues/217) | Issue | `/cancel` answers 5 to 17 s after a quit, and the client cannot tell a slow quit from a hung one. Filed 2026-10-29 with the list of what `/cancel` waits on. The owner plans to fix it. On 26.10.1 on 2026-10-29, each of three Quits on the TV took 5.6 to 6.0 s in `/cancel`, and about 5 s of it was the display helper's transactional revert (4.83 to 5.35 s) in the join of the last session. Open. See the [host contract](host-contract/luminalshine.md), section 7.6, and [Sprint 15](#sprint-15-the-stream-screens-and-glass). |
| [NortheBridge/luminalshine#218](https://github.com/NortheBridge/luminalshine/issues/218) | Issue | `/cancel` drops the control stream without the termination message, so clients report error -1 for the user's own Quit. Filed 2026-10-29. The owner plans to fix it. Open. Borealis handles the user's own Quit on its side (Sprint 15). See the [host contract](host-contract/luminalshine.md), section 7.6. |
| [NortheBridge/luminalshine#219](https://github.com/NortheBridge/luminalshine/issues/219) | Issue | The 4:4:4 fallback says it tells the client about the chroma change, but nothing is sent. Filed 2026-10-29; it is proposal P4. The owner starts with the comments. Open. See the [host contract](host-contract/luminalshine.md), sections 9.1 and 20. |
| [NortheBridge/luminalshine#220](https://github.com/NortheBridge/luminalshine/issues/220) | Issue | The host's `architecture.md` (line 103) says `/cancel` does not pop the pending launch; it does. Filed 2026-10-29. The owner plans to fix it. Open. See the appendix of the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#221](https://github.com/NortheBridge/luminalshine/discussions/221) | Discussion | Let a paired client know whether the running app is its own session: a launch id, or an "opened by you" flag, in the HTTPS `/serverinfo`. Filed 2026-10-29 (Ideas); proposal P6. It would close the risk that stays after Sprint 15's second-device rule. See the [host contract](host-contract/luminalshine.md), sections 7.6 and 20. |
| [NortheBridge/luminalshine#222](https://github.com/NortheBridge/luminalshine/discussions/222) | Discussion | An API level and a capability list in `/serverinfo`. Filed 2026-10-29 (Ideas); the rest of proposal P1. |
| [NortheBridge/luminalshine#223](https://github.com/NortheBridge/luminalshine/discussions/223) | Discussion | PyroWave: honour the client's bitrate as an upper bound. Filed 2026-10-29 (Ideas); proposal P2. |
| [NortheBridge/luminalshine#224](https://github.com/NortheBridge/luminalshine/discussions/224) | Discussion | PyroWave: a capability attribute for KEEP-frame replenishment. Filed 2026-10-29 (Ideas); proposal P3. |
| [NortheBridge/luminalshine#225](https://github.com/NortheBridge/luminalshine/discussions/225) | Discussion | Document the launch and resume status codes, and add `Retry-After` to the retryable 409 and 503. Filed 2026-10-29 (Ideas); proposal P5. |
