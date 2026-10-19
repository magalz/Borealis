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
- **Phase 2, Sprints 2 to 7 (PRs #9 to #13 and #19):**
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
   planned for Sprint 10. See the
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

## Next

The 0.1.0 release comes after Sprint 10 and stays at the end of Phase 2
([ADR 0054](adr/0054-declare-independence-from-moonlight.md)), so Sprints 7 to
10 close Phase 2.

### Sprint 8: tidy-up

The work for this sprint is on the phase branch `phase/s8-tidy-up`. The device
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
   `ControllerHandler.isGameControllerDevice` is open; see Sprint 12.
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
   in test sources (see Sprint 12). `MatchingDeclarationName` is off, because
   Compose files are named after the composable. `detekt.use.worker.api=true` is
   set.
10. **Memtrace rule.** Done (merge `599fed98` and commit `7f7b7721`). The index
    and the queries use the branch `main`. `watch_directory` is not used,
    because it labels changes with the checked-out branch. `memtrace start` runs
    its own watcher under that branch, so the `main` label is refreshed by the
    incremental index after each merge wave.

### Sprint 9: UX polish and the handheld

The work for this sprint is on the phase branch `phase/s9-ux-handheld`, which
starts at `68a8972a`. All code items are merged on it. The final device round,
[section 3.22](development/device-validation.md#322-sprint-9-checks), has not run
yet. Its results go to
[section 14](development/device-validation.md#14-results-sprint-9).

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
4. **Handheld host card height.** Done (issues #25 and #26, merge `ecbf2236`).
   Noticed on 2026-10-17. On a handheld the host cards follow their content, as
   on a phone. The 176 dp minimum applies on the TV only.
5. **Phone bar gap.** Done (issues #25 and #26, merge `ecbf2236`). Found in
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
   TV remote fallback is unchanged.
8. **Steam opens after a quit combo.** Done (issue #29, merge `ea4df39e`). Found
   in the Sprint 8 round, on the S22. The menu chord arms only from a clean start:
   once a button other than Start and Select is held, Start and Select do not
   arm it again until every button is released. Letting the quit combo go with a
   bumper last therefore sends no Guide tap and opens no menu, on system pads and
   on USB-driver pads. On a system pad, the Start hold no longer toggles the
   gamepad mouse while the quit combo is pending. See the
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
     ExoPlayer on a SurfaceView.
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
    fit a 960 x 540 dp TV without scrolling. The handheld list may still scroll.
11. **The "preparing the display" text.** Done (issue #31, merge `75dbf7be`). Found
    during the sprint. The message on the stream's notification overlay predates
    the Sprint 5 Starting screen, which is opaque and covers the overlay from the
    connection start to the first frame. No code path showed it, so the message,
    its overlay plumbing and its string are removed. The Starting screen keeps its
    own "Preparing the display" step. The first-frame watchdog and the connection
    warnings are unchanged.
12. **A device round** for what Sprint 7 and Sprint 8 did not run, and for the
    Sprint 9 changes. Open.
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

### Next: UX and accessibility

Not planned as a sprint yet. These items came up while the background was built.
The owner sets their place relative to Sprint 10 when they are planned.

1. **Accessibility and WCAG review.** The background lies behind every Library and
   Settings screen. Review:
   - the contrast of text over the background, in the two Compose styles and the
     four clips;
   - the look of the cards over the background;
   - the small ink-3 and ink-4 texts. Over the brightest aurora bands they fall to
     3.0 to 3.7:1, below the 4.5:1 that WCAG 2.x asks for small text. On the plain
     page and on surfaces they keep 4.5:1 or more (see the
     [contrast table](ux/phase-2-proposal.md#contrast)).
2. **Phone portrait Settings navigation.** Requested by the owner. The phone shows
   every group in one long list. Two options, to be decided in that discussion:
   - (A) collapsible sections that start collapsed;
   - (B) two levels, as the Android Settings app does: a list of the groups, then
     the page of the chosen group. The recommendation is B.

### Sprint 10: release preparation

- The root and Android READMEs.
- An install (Obtainium) and pairing guide.
- The compatibility note.
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

### Sprint 11: translation and opt-in crash reporting

- **Translation sprint.** The app is English only until this sprint
  ([ADR 0055](adr/0055-settings-storage-and-the-phase-2-settings-screen.md)).
- **Opt-in crash reporting.** Planned, not implemented. See
  [PRIVACY.md](../PRIVACY.md) and [ADR 0022](adr/0022-crash-reporting-and-privacy.md).

### Sprint 12 onward: Java to Kotlin migration

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
- **Phase 5: stores.** Google Play and Amazon Appstore. See the
  [release policy](policies/release.md) and
  [ADR 0045](adr/0045-store-publishing-automation.md), which is proposed.
- **Phase 6: Windows client.** Post-MVP. Most PC handhelds run Windows. Its order
  relative to Phases 3 to 5 is not decided.

## Backlog, not scheduled

- **Deferred RTSP parser rework.** The moonlight-common-c rework `aeba988` is
  deferred by the owner. See the [third-party notes](../core/third_party/README.md).

## Host-side items (LuminalShine)

These are proposals and known issues in the LuminalShine host. The client works
without them. All of them are open as of 2026-10-17.

| Item | Kind | Summary |
| --- | --- | --- |
| [NortheBridge/luminalshine#194](https://github.com/NortheBridge/luminalshine/issues/194) | Issue | Stray bytes after a 404 response. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#198](https://github.com/NortheBridge/luminalshine/issues/198) | Issue | Teardown hang after a disconnect. See the [host contract](host-contract/README.md). |
| [NortheBridge/luminalshine#199](https://github.com/NortheBridge/luminalshine/pull/199) | PR | Reports the LuminalShine release in `/serverinfo`, so the client can show the host version. |
| [NortheBridge/luminalshine#200](https://github.com/NortheBridge/luminalshine/pull/200) | PR | Sends a single response for unknown GameStream paths. Proposed fix for #194. |
| [NortheBridge/luminalshine#201](https://github.com/NortheBridge/luminalshine/issues/201) | Issue | A second client leaving a shared app ends the first client's session (per-client virtual display). See [UC-003](use-cases/0003-join-or-replace-a-running-app.md). |
| [NortheBridge/luminalshine#202](https://github.com/NortheBridge/luminalshine/issues/202) | Issue | A long `/launch` blocks every other HTTPS GameStream request until it finishes. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#203](https://github.com/NortheBridge/luminalshine/pull/203) | PR | Proposed fix for #202. Not merged. |
| [NortheBridge/luminalshine#204](https://github.com/NortheBridge/luminalshine/issues/204) | Issue | The host process crashes after the virtual display cleanup of an ended session. See the [host contract](host-contract/luminalshine.md). |
| [NortheBridge/luminalshine#205](https://github.com/NortheBridge/luminalshine/issues/205) | Issue | A second client's resume fails the RTSP tag check while the first client's launch is still setting up. See the [host contract](host-contract/luminalshine.md). |
