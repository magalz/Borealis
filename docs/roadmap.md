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
- **Phase 2, Sprints 2 to 6 (PRs #9 to #13):**
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

## Next

### Sprint 7: follow-ups and backlog

The work for this sprint is on the phase branch `phase/s7-followups-polish`.

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

### Sprint 8: tidy-up

The work for this sprint is on the phase branch `phase/s8-tidy-up`. The device
smoke round of [section 3.21](development/device-validation.md#321-sprint-8-checks)
has not run yet. Its results go in [section 13](development/device-validation.md#13-results-sprint-8).

1. **Dead code, complexity hotspots and duplicated code.** Found with Memtrace
   and checked by hand. The merges below carry the work.
   TODO(verify): which findings of this item remain open.
2. **Root flavor.** Done (merge `48927cca`). The root flavor, its variants and
   the APK paths are removed. The tasks are `assembleDebug` and `assembleRelease`.
3. **Upstream leftovers.** Done (merges `599fed98` and `51a04274`). Removed:
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
7. **Phone title bars.** Done (merge `8a6acbad`). The three phone title bars
   share one phone bar and one back button. The gap below the arrow still
   differs; see Sprint 9.
8. **Kotlin suppressions.** Done (merge `543896a9`).
9. **Detekt type analysis.** Done (merge `65f6b3a9`, fixes `933f368f`,
   `67cef8eb` and `b72163d3`, baselines removed in `50854eda`). Every Android
   module runs detekt with type analysis. The 116 findings are fixed. Two rules
   stay off in test sources only: `InjectDispatcher` and `LongParameterList`
   (see Sprint 12). `MatchingDeclarationName` is off, because Compose files are
   named after the composable. `detekt.use.worker.api=true` is set.
10. **Memtrace rule.** Done (merges `599fed98` and `7f7b7721`). The index and the
    queries use the branch `main`. There is no watcher, because it ignores the
    branch parameter. The index is refreshed after each merge wave.

### Sprint 9: UX polish and the handheld

- **Smoother D-pad moves in Settings on a TV.** Reported by the owner on
  2026-10-17. Moving from one Settings row to the next has a small delay, while
  every other screen moves at once. Investigate where the time goes, for
  example rows that recompose or reload their options on every focus change.
- **The quit warning for Desktop.** Reported by the owner on 2026-10-17.
  Quitting the stream asks "Quit Desktop? Unsaved work is lost.", but quitting
  Desktop only ends the stream and gives the display back to the PC; nothing is
  lost. The warning still fits a game, which the quit closes. The strings are
  `stream_menu_quit_confirm`, `apps_confirm_quit`, `apps_action_quit_consequence`
  and `stream_conflict_warning`.
- **Host details text.** Noticed on 2026-10-17. The host Details dialog lists
  raw fields inherited from Moonlight, such as "Active Address: null" and
  "State: OFFLINE". Show only what a user needs, in plain words.
- **Handheld host card height.** Noticed on 2026-10-17. On the S22 as a
  handheld (360 dp tall), the host cards use the TV height of 176 dp, which
  leaves a lot of empty space. Adapt the card height for a handheld.
- **Phone bar gap.** Found in Sprint 8. Settings and Privacy have 4 dp between
  the back arrow and the title, and Apps and Audit have none. Unifying the gap
  needs re-recorded screenshot references.
- **The animated background:** integrated in this sprint if the owner has
  delivered it by then; otherwise it stays in the backlog.
- **A device round** for what Sprint 7 did not run:
  - a dedicated Android handheld (the owner gets one);
  - the Send to phone Expired and No network states;
  - the Gamepad mouse speed slider at 10% and 200%;
  - the quit combo on a USB-driver pad on the final build;
  - the dialog frames and visual items that the Sprint 7 round did not record
    (rows F15 and F16).

  See the [device validation runbook, section 12](development/device-validation.md#12-results-sprint-7)
  for the Sprint 7 round.

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
- **Order:**
  1. HTTP and computers. Phase 3 uses them.
  2. Input and video. This comes before Phase 4 (PyroWave).
  3. `MoonBridge`, last.
- **Size:** about 85 Java files and 22k lines in the stream core.
  TODO(verify): the figure is the owner's. A count of the tracked `.java` files
  in `core/` and `apps/android/` on 2026-10-18 gives 80 files.
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
- **Phase 4: PyroWave.** See the [host contract](host-contract/pyrowave.md).
- **Phase 5: stores.** Google Play and Amazon Appstore. See the
  [release policy](policies/release.md) and
  [ADR 0045](adr/0045-store-publishing-automation.md), which is proposed.
- **Phase 6: Windows client.** Post-MVP. Most PC handhelds run Windows. Its order
  relative to Phases 3 to 5 is not decided.

## Backlog, not scheduled

- **Animated app background.** Simple and discreet, made by the owner. It is
  integrated when delivered: in Sprint 9 if it is ready by then, otherwise in a
  later visual polish.
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
