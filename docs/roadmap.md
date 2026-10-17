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
In the owner's order:

1. **MediaTek decode time shows "—" in the stats overlay.** The Google TV
   Streamer shows no decode time. The Galaxy S22 shows a number. The Sprint 6
   FIFO attempt was reverted in `c02fdafb`. Sprint 7 matches output timestamps
   to input timestamps, with pruning, and logs counters under verbose logging.
2. **The quit combo does not end the stream on pads driven by the app's USB
   driver.** Start, Select, LB and RB works on system pads only. The combo is
   checked in the key path, not in the USB report path.
3. **Investigate the repeated USB permission dialog on Google TV.** Sprint 7
   collects logs. There is no code change yet.
4. **Gamepad mouse: pointer speed.** A speed setting from 10% to 200%, default
   50%. A 16 ms tick with fraction accumulation. Holding RB gives 25%
   precision. A session row (Slow, Normal, Fast) in the in-game menu. The same
   speed applies to right-stick scrolling. The owner reports that scrolling
   does not seem to work today; a device check in Sprint 7 confirms this.
5. **Visual refinement pass.** Icons, and the sizes of dialogs and panels. The
   owner chooses the changes from a contact sheet of every screen.

### Sprint 8: tidy-up

- Dead code, complexity hotspots and duplicated code. Found with Memtrace and
  checked by hand.
- The stale entries in the lint baseline, `apps/android/app/lint-baseline.xml`.
- Upstream leftovers in `apps/android/app/build.gradle`,
  `apps/android/appveyor.yml` and `apps/android/store-assets/`.

### Sprint 9: release preparation

- The root and Android READMEs.
- An install (Obtainium) and pairing guide.
- The compatibility note.
- [PRIVACY.md](../PRIVACY.md).
- The release certificate fingerprint.
- [CONTRIBUTING.md](../CONTRIBUTING.md) and the release docs.
- Curated release notes.
- The dry run on the tag commit, and the release smoke test. See the
  [release procedure](development/release.md).

### First release: `android-v0.1.0`

The owner creates the tag. See the [release procedure](development/release.md).

## Later phases

- **Phase 3: LuminalShine REST features.** Several documents mark these as
  Phase 3. See the [Phase 3 extension points](ux/phase-2-proposal.md#phase-3-extension-points).
- **Phase 4: PyroWave.** See the [host contract](host-contract/pyrowave.md).
- **Phase 5: stores.** Google Play and Amazon Appstore. See the
  [release policy](policies/release.md) and
  [ADR 0045](adr/0045-store-publishing-automation.md), which is proposed.

## Backlog, not scheduled

- **Animated app background.** Simple and discreet, made by the owner. It is
  the last item, together with any visual polish that remains.
- **Translation sprint.** The app is English only until this sprint
  ([ADR 0055](adr/0055-settings-storage-and-the-phase-2-settings-screen.md)).
- **Opt-in crash reporting.** Planned, not implemented. See
  [PRIVACY.md](../PRIVACY.md) and [ADR 0022](adr/0022-crash-reporting-and-privacy.md).
- **Deferred RTSP parser rework.** The moonlight-common-c rework `aeba988` is
  deferred by the owner. See the [third-party notes](../core/third_party/README.md).

## Host-side items (LuminalShine)

These are proposals and known issues in the LuminalShine host. The client works
without them. All of them are open as of 2026-10-17.

| Item | Kind | Summary |
| --- | --- | --- |
| NortheBridge/luminalshine#194 | Issue | Stray bytes after a 404 response. See the [host contract](host-contract/README.md). |
| NortheBridge/luminalshine#198 | Issue | Teardown hang after a disconnect. See the [host contract](host-contract/README.md). |
| NortheBridge/luminalshine#199 | PR | TODO(verify): summary not found in this repository. |
| NortheBridge/luminalshine#200 | PR | TODO(verify): summary not found in this repository. |
| NortheBridge/luminalshine#201 | Issue | TODO(verify): summary not found in this repository. |
| NortheBridge/luminalshine#202 | Issue | The host builds a per-client virtual display on one thread, so other requests wait. See the [host contract](host-contract/luminalshine.md). |
| NortheBridge/luminalshine#203 | PR | Proposed fix for #202. Not merged. |
| NortheBridge/luminalshine#204 | Issue | The host process crashes after the virtual display cleanup of an ended session. See the [host contract](host-contract/luminalshine.md). |
| NortheBridge/luminalshine#205 | Issue | A second client's resume fails the RTSP tag check while the first client's launch is still setting up. See the [host contract](host-contract/luminalshine.md). |
