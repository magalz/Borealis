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
   planned for Sprint 17. See the
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
   `ControllerHandler.isGameControllerDevice` is open; see Sprint 13.
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
   in test sources (see Sprint 14). `MatchingDeclarationName` is off, because
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
   palette and reduce motion, read once when the stream starts. The stream gets
   no glass, because the video is behind it.
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

The 0.1.0 release comes after Sprint 17 and stays at the end of Phase 2
([ADR 0054](adr/0054-declare-independence-from-moonlight.md)), so Sprints 11 to 17
close Phase 2.

### Order of the coming sprints (owner decision of 2026-10-21)

The owner asked for the order that gives the least rework with the most safety,
with any number of sprints. The phase pull requests stay small and easy to test.

| Sprint | Scope |
| --- | --- |
| 11 | Appearance |
| 12 | Kotlin migration 1: network, host and support |
| 13 | Kotlin migration 2: input |
| 14 | Kotlin migration 3: video, audio and the stream activity |
| 15 | The stream screens and glass |
| 16 | SecOps |
| 17 | Release preparation |
| — | `android-v0.1.0` |
| 18 | Translation and opt-in crash reporting |

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
- SecOps comes after the last feature sprint, so it audits the final code. Release
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
  them. Kotlin code that Java still calls keeps a Java-friendly API (`@JvmStatic`,
  `@JvmField`, `@JvmOverloads`) until its last Java caller is converted.
- **ADR:** [ADR 0059](adr/0059-convert-the-inherited-java-to-kotlin.md) records the conversion
  rules. It amends [ADR 0004](adr/0004-kotlin-for-new-android-code.md).
- **Dead code:** the full sweep of the Java methods for dead code happens area by
  area. Sprint 8, item 1 hands it over.
- **Size:** 33 Java files and 14,214 lines under `apps/android/app/src/main/java`
  after Sprint 12, almost all of it the input, video and `Game` code of Sprints 13
  and 14. Before Sprint 12 it was 80 Java files and 21,539 lines (2026-10-21).
- **Upstream cherry-picks:** an area that is converted no longer takes upstream
  cherry-picks directly. Its fixes are ported by hand. See
  [upstream cherry-picks](development/upstream-sync.md).
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
  its own (ADR 0059, rule 7).
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

About 8,000 lines: `binding/input` (4,060, with `ControllerHandler` at 3,659 and
`KeyboardTranslator`), `binding/input/virtual_controller` (1,905),
`binding/input/driver` (the USB drivers, 1,173), `binding/input/touch` (591) and
`binding/input/capture` (253).

- **Game controller rule.** Unify the game controller rule with
  `ControllerHandler.isGameControllerDevice`, possibly in a neutral input module.
  Today `:app` depends on `:borealis:designsystem` for it. `ControllerHandlerRig`
  can then use the shared `InputDevices` fixture. The unification is its own task,
  after the conversion. Any difference between the two rules goes to the owner.
- The device round: both 8BitDo pads (the Pro 3 in Switch mode, the Ultimate 2 in
  D-input), a USB pad, the on-screen controller and touch on the S22, the keyboard
  and mouse, the gamepad mouse, the menu chord and the quit combo.
- Phase 4 (PyroWave) uses input and video.

### Sprint 14: Kotlin migration, video, audio and the stream activity

About 6,200 lines: `binding/video` (2,985, with `MediaCodecDecoderRenderer` at
1,876 and `MediaCodecHelper` at 1,004), `binding/audio` (216), `Game.java`
(2,600) and `nvstream/jni/MoonBridge` (431), which comes last.

- The per-SoC decoder decisions of `MediaCodecHelper` get table-driven
  characterization tests before the conversion.
- `MoonBridge` keeps its name, its package and every native signature; the static
  methods the C code calls need `@JvmStatic`.
- **Detekt tests:** turn the detekt rules `InjectDispatcher` and
  `LongParameterList` back on in test sources, and standardize the tests. Inject
  the dispatchers in the tests and reduce the test builders. This is an explicit
  owner request.
- **Test size and duplication.** Moved from the Sprint 9 quality review. The Sprint
  9 tests run about 1,160 lines against about 590 lines of implementation, with
  repeated group lists and helpers. They belong in shared test support (the
  `testFixtures` of `:borealis:designsystem`). The migration ends with the tests
  standardized.
- The device round: each codec the devices decode, HDR, audio, the performance
  overlay, the Starting and Quitting screens, a second device during a launch, and
  Quit, on the TV and the S22.

### Sprint 15: The stream screens and glass

Planned, not started.

- **Background during the stream start:** the animated background also shows on
  the Starting screen, with the step text on a glass panel. It stops before the
  first frame. This changes the Sprint 9 rule that the background is never in a
  stream.
  - Risk: on the TV, a background clip and the stream decoder compete for the
    hardware decoder.
- **Connecting and disconnecting screens.** Added at the Sprint 10 UAT
  (2026-10-21). The Quitting screen gets the same treatment as the Starting
  screen above: translucent over the animated background, with the step text on
  a blurred glass panel.
- **In-game menu in the Library's glass style.** Added at the Sprint 10 UAT
  (2026-10-21). The video is drawn in a `SurfaceView`, which the Library's
  `GlassHost` cannot blur. A spike compares a blurred `PixelCopy` snapshot of
  the video, refreshed a few times a second, with tint only. This changes the
  rule of [ADR 0057](adr/0057-glass-surfaces-and-the-accessibility-settings.md)
  that the stream has no glass, so it needs a new decision.
- **Quit confirmation of the stream as a centred popup.** Added at the Sprint 10
  UAT (2026-10-21). It is a row in the side panel today.
- **Quit wording in three places.** The choice between the Desktop text and the
  game text is made in the Library action sheet (`AppActionsSheet.kt`), the
  stream menu (`QuitConfirmation.kt`) and the conflict dialog
  (`ConflictDialog.kt`). The strings repeat across `strings_apps.xml`,
  `strings_menu.xml` and `strings_conflict.xml`, and the Library stores a flag
  while the stream decides from the name of the app. The popup rewrites the
  stream's quit confirmation.
- **A Quit from the user logs an error.** The user's own Quit logs
  `E Borealis.UPSTREAM: Connection terminated: -1`, and the system logs
  `Duplicate finish request` for `com.limelight.Game`.
- **Quit took about 17 s once on 26.10.0-beta.1.** "Quitting `<app>`…" stayed on
  screen until `/cancel` answered: about 17 s once in the night round of
  2026-10-21, and 5.5 to 6.7 s in the morning smoke of the same day, against 5 to
  7 s on 26.09.1. The cause is not known, because the client logs nothing in
  between. Measure it again in this round, and report it upstream if it repeats.
- **Indeterminate progress indicators ignore Reduce motion.** `QuittingScreen`,
  `AddHostScreen` and `PrivacyPane` draw Material's indicators, which do not ask
  `rememberAnimationsRemoved`. The Quitting screen is redone in this sprint; Add
  host and Privacy take the same fix.
- **Segments and keyboard keys still lift when focused.** `SegmentedControl` and
  `KeyboardKey` use `liftedWhenFocused`, so the merged tree lists the focused one
  last. Robolectric cannot see TalkBack's traversal links: check it with TalkBack
  on a device in this sprint.
- **A UAT note:** "Fn 123" wraps on narrow phones at the default look.
- **A pinned app shortcut fails when Borealis is not running.** Found in the Sprint 12
  round (2026-10-24); the owner put it here. The launch request of the shortcut fires
  once the app list loads from the cache, before the first poll gives the host an
  address, so `ServerHelper.doStart` shows "Computer is offline" and starts nothing.
  Proposed fix, with a test first: wait for the host's first poll answer before the
  stream starts; a host that never answers still ends in the offline message. See
  [device-validation section 17](development/device-validation.md#17-results-sprint-12),
  the follow-up on the S22.
- **The in-game menu loses its focus on Left or Right.** Found by the owner in the
  Sprint 12 round (2026-10-24); the owner put it here. Left or Right, on the D-pad or
  the analog stick, makes the focus outline disappear, and it does not come back until
  the menu is closed and opened again. It happens often with the analog stick. Check
  where the focus goes, and whether D4 of the round (keys from the phone not
  reaching Quit) is the same cause.
- **Add host says "Pair it" for a host that is already paired.** Found in the Sprint
  12 round (2026-10-24); the owner put it here. `shell_host_added` shows for any
  added host. Word it by the host's pairing state.
- **Back from Apps to Hosts plays a shrinking animation.** Found by the owner in the
  Sprint 12 round (2026-10-24), on the TV and the phone. The other screens of the
  Library fade (`FadingNavHost` in `LibraryNavHost.kt`). Find where the shrink comes
  from, and make this back like the others.
- **A forgotten host is not found again until the app restarts.** Found by the owner in
  the Sprint 12 round (2026-10-24). After Forget, the host does not come back as
  "Found on this network" until the app is closed and opened again. Check whether
  discovery skips a host it already reported, in `ComputerManagerService` and the
  mDNS agents, with a test first.

### Sprint 16: SecOps

Owner decision of 2026-10-20: a whole sprint on security comes before the
release. Sprint 16 is hardening and security testing.

- It comes after the migration (owner decision of 2026-10-21), so it audits the
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
  Sprint 10 UAT (2026-10-21). Proposed; the owner confirms at planning. On
  LuminalShine 26.10.0-beta.1, a wrong PIN keeps the pairing session for 5
  minutes, and a retry gets 409 until it expires. The owner saw the same symptom
  again in the Sprint 12 round (2026-10-24): a retry shows a new PIN for a moment,
  then the "try again" screen. The app log shows twelve attempts one after another,
  each answered 409 within 0.1 s, 0.6 to 21 s apart; who started each is not
  recorded. Whether the client should wait on 409 `IN_PROGRESS` is for planning. The
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
- **CodeQL `java/path-injection` on `PosterContentProvider`.** CodeQL flags the box-art
  file path of the exported provider, on the Java file on `main` and on the converted
  Kotlin file (pull request #70). The host id is checked by `PosterRequest.checkedAppId`,
  which CodeQL does not see as a sanitizer. Owner decision of 2026-10-24: the alerts stay
  open until this sprint, which makes the check visible to CodeQL or dismisses them with
  the reason.

### Sprint 17: release preparation

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

### Sprint 18: translation and opt-in crash reporting

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

- **Deferred RTSP parser rework.** The moonlight-common-c rework `aeba988` is
  deferred by the owner. See the [third-party notes](../core/third_party/README.md).

- **Combined font scales above 2.0 at Normal.** A system font of 2.0 with the Text size Largest is
  2.6. There Normal text fails the fit tests in 8 of the 84 runs of the Library (Apps with a long
  host name, on a TV and on a phone) and in 1 of the 70 runs of the components (the rows of the
  settings on a TV). Wider is drawn as Wide from 2.0, and Wide and Normal are never limited. Wide
  above a combined 2.0 has not been measured, only Normal was. See
  [accessibility, text spacing](ux/accessibility.md#text-spacing-known-limitations).

From the Sprint 9 quality review. None of these changes behaviour. Sprint 10
(item 11) took the deprecated `FocusProperties.enter`, the copied spacing
constants and the two background defaults. The test size item moved to Sprint 14,
the quit wording to Sprint 15 and the host details to Sprint 17. The rest is not
scheduled yet.

- **`MenuChordDetector.update`** (`borealis/stream/.../input/MenuChordDetector.kt`).
  Cyclomatic complexity 13 and cognitive complexity 18, seven mutable fields, a
  repeated expression, and an order dependency with no comment.

From the Sprint 9 device round, 2026-10-19. Sprint 10 took the Back from Apps
focus, the About Version row and links (items 9 and 10), and the licence text
and the `AuditScreen.kt` `Detail` order (item 12). The Gradle and AGP items moved
to Sprint 16, the `RiskyLibrary` check to Sprint 16, and the `LibraryActivity`
item to Sprint 17. The rest is not scheduled yet.

- A touch handheld whose built-in controls report only `KEYBOARD | DPAD`, with no
  GAMEPAD source, now gets the phone layout. This is rare; watch for it.
- On a narrow window with a pad (a phone in split screen), the stream keyboard
  covers the stream but keeps the TV shortcut row, where Esc is the 7th of 8
  chips. `StreamKeyboard.kt` picks the Esc-first row by `docked`, not by width
  (owner, 2026-10-20: backlog).

From the Sprint 10 reviews and its device round (2026-10-21). The other items
moved to Sprints 11, 12, 15 and 18. None is scheduled yet.

- **The halo and the tint-only glass have not been seen on API 28 to 30.** No
  device is available; the tests draw them with Robolectric.

From the Sprint 10 UAT (2026-10-21). None is scheduled yet; the owner put them
after SecOps (Sprint 16), or post-MVP. The list code (`ComputerManagerService`) is
Kotlin from Sprint 12 on.

- **A refresh button on Hosts.** After Forget, the Hosts list does not update
  until the app is restarted. The owner wants a refresh button on Hosts. Schedule:
  after SecOps (Sprint 16), or post-MVP.
- **Why the Hosts list does not update after Forget.** The owner wants this
  looked into, so the list updates by itself. Schedule: after SecOps (Sprint 16),
  or post-MVP.

From the Sprint 12 conversion (2026-10-23). Nothing here is scheduled; the owner decides
when. Paths are under `apps/android/app/src/main/kotlin/com/limelight/`, except
`HeldStageFailures.kt`, `library/UpstreamHostShortcuts.kt` and
`library/ComputerManagerConnection.kt`, which are under
`apps/android/app/src/main/kotlin/io/github/magalz/borealis/`.

- **(a) Refactor the converted code.** The 48 places marked `(Sprint 12 backlog)` each
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
  - `computers/IdentityManager.kt`: 1
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

  That is 48 suppressions in 18 files.
- **(b) Bugs found during the Sprint 12 conversion.** None is fixed. The conversion keeps
  behaviour (ADR 0059, rule 10).
  - **Priority:** `utils/ShortcutHelper.kt`, `reapShortcutsForDynamicAdd`, loops forever
    when the dynamic shortcut list is full and `forceAdd` is true, because it never re-reads
    the list. `library/UpstreamHostShortcuts.kt`, `reportHostOpened`, runs it inside
    `synchronized(publishing)`, so publishing blocks. The upstream code has the same loop.
    The fix is to re-read the list inside the loop. Propose it to the owner as a priority
    fix after Sprint 12.
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
- **(c) For SecOps (Sprint 16).** The security observations of the conversion are listed
  under Sprint 16, one line each, with their files.
- **(d) From the Sprint 12 device round (2026-10-23).**
  - The bridged upstream log lines (`Borealis.UPSTREAM`, from `LimeLog`) carry host names
    and IP addresses in clear, as the inherited code always did. This is not a regression.
    The owner decided on 2026-10-24: host names stay, and public and overlay addresses are
    masked in Sprint 16 (see Sprint 16). See
    [device-validation section 17](development/device-validation.md#17-results-sprint-12), P2.
  - Optional feature: send `continuousAudio=1` in `/launch`, so the client gets a steady
    audio stream on a silent PC. LuminalShine supports it.

## Host-side items (LuminalShine)

These are proposals and known issues in the LuminalShine host. The client works
without them. The states are as of 2026-10-20. #199, #200 and #203 were merged
on 2026-10-20, and 26.10.0-beta.1 (a prerelease of 2026-10-20) ships them
(release notes). #194 and #202 are fixed on the host (release notes). The open
ones are #198, #201, #204 and #205.

| Item | Kind | Summary |
| --- | --- | --- |
| [NortheBridge/luminalshine#194](https://github.com/NortheBridge/luminalshine/issues/194) | Issue | Stray bytes after a 404 response. Fixed on the host by #200, in 26.10.0-beta.1 (release notes). See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#198](https://github.com/NortheBridge/luminalshine/issues/198) | Issue | Teardown hang after a disconnect. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#199](https://github.com/NortheBridge/luminalshine/pull/199) | PR | Reports the LuminalShine release in `/serverinfo`, so the client can show the host version. Shipped in 26.10.0-beta.1 (release notes). The client shows it in Sprint 17. |
| [NortheBridge/luminalshine#200](https://github.com/NortheBridge/luminalshine/pull/200) | PR | Sends a single response for unknown GameStream paths. Fix for #194. Shipped in 26.10.0-beta.1 (release notes). |
| [NortheBridge/luminalshine#201](https://github.com/NortheBridge/luminalshine/issues/201) | Issue | A second client leaving a shared app ends the first client's session (per-client virtual display). See [UC-003](use-cases/0003-join-or-replace-a-running-app.md). |
| [NortheBridge/luminalshine#202](https://github.com/NortheBridge/luminalshine/issues/202) | Issue | A long `/launch` blocks every other HTTPS GameStream request until it finishes. Fixed on the host by #203, in 26.10.0-beta.1 (release notes). See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#203](https://github.com/NortheBridge/luminalshine/pull/203) | PR | Fix for #202. Merged on 2026-10-20 and shipped in 26.10.0-beta.1 (release notes). |
| [NortheBridge/luminalshine#204](https://github.com/NortheBridge/luminalshine/issues/204) | Issue | The host process crashes after the virtual display cleanup of an ended session. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#205](https://github.com/NortheBridge/luminalshine/issues/205) | Issue | A second client's resume fails the RTSP tag check while the first client's launch is still setting up. See the [host contract](host-contract/luminalshine.md). |
| Wrong-PIN lockout on 26.10.0-beta.1 | Finding | After a wrong PIN, the host keeps the pairing session, and every retry gets 409 for up to 5 minutes. Restarting LuminalShine clears it. Seen live on 2026-10-21 and checked in source at `e2662a44`. Not filed upstream yet. See the [host contract](host-contract/luminalshine.md) and [Sprint 16](#sprint-16-secops). |
