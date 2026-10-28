# Host contract

This folder is the canonical, source-referenced description of what the
LuminalShine host offers to a streaming client and what it requires from one.
Every later Borealis task that touches the host (retry policy, capability
parsing, PyroWave, REST companion features) builds on the facts recorded here
instead of re-reading the host source.

## Contents

| File | Subject |
| --- | --- |
| [luminalshine.md](luminalshine.md) | GameStream HTTP(S), pairing, client identity, launch/resume, RTSP, control stream, input, audio, encryption, discovery, REST API, WebRTC preemption, client obligations for Borealis, gaps versus Apollo/Artemis clients, proposals for LuminalShine |
| [pyrowave.md](pyrowave.md) | The experimental PyroWave codec: provenance, availability gating, negotiation, host constraints, bitstream and transport layout, client feedback, decoder requirements, Android limitations |

## Verified against

| Item | Value |
| --- | --- |
| Host repository | `NortheBridge/luminalshine` (<https://github.com/NortheBridge/luminalshine>) |
| Commit | `5afdbdc6d04e0ad24cdcab5cfa9743bfc4f2dca8` (`NortheBridge/luminalshine@5afdbdc6`) |
| Commit date | 2026-10-05 |
| Nearest release tag | `26.09.1` (`git describe`: `26.09.1-15-g5afdbdc6`) |
| Contract revision date | 2026-10-28 |
| Protocol version string the host reports | `7.1.431.-1` (`src/nvhttp.h:37`) |
| Lineage | Sunshine, then Vibeshine, then LuminalShine; not derived from Apollo (`gh-pages-template/compare.html:66-82`) |
| Later release | `26.10.0-beta.1`, a prerelease of 2026-10-20. The items the release notes (2026-10-20) list are recorded in `luminalshine.md` as "Changed in 26.10.0-beta.1" and were verified in source at the commit in the next row. The rest of the contract is verified at `5afdbdc6` only: re-verify it in a later sprint |
| Commit for the 26.10.0-beta.1 items | `46f6936d249ea88f1e8042ff63e056aa29d5db21` (`NortheBridge/luminalshine@46f6936d`), commit date 2026-10-22, `git describe`: `26.09.1-38-g46f6936d`. The local clone had no `26.10.0-beta.1` tag then; the tag, fetched since, is `e2662a44` (checked on 2026-10-28). `src/` is the same at `46f6936d` and at `e2662a44` (the merge of PR #206, 2026-10-20), which contains the merges of PRs #199, #200 and #203: the commits in between change only `.github/`, `scripts/` and `tests/` |
| 26.10.0-beta.1 fixtures | Plain-HTTP responses captured from a running 26.10.0-beta.1 host on 2026-10-23: [`core/tests/fixtures/luminalshine/26.10.0-beta.1/`](../../core/tests/fixtures/luminalshine/26.10.0-beta.1/PROVENANCE.md) |
| Later releases | `26.10.0` (published 2026-10-26, UTC) and `26.10.1` (published 2026-10-28, UTC; a maintenance release of the desktop helper). The items the release notes list are recorded in `luminalshine.md` as "Changed in 26.10.0" and "Changed in 26.10.1" and were verified in source at the commit in the next row. Nothing client-facing changed since 26.10.0-beta.1: `src/nvhttp.cpp`, `src/rtsp.cpp`, `src/config.cpp` and `src/nvhttp.h` are the same git blobs at `e2662a44`, `46f6936d`, `a9928254` and `dba21a9e`. The rest of the contract is still verified at `5afdbdc6` (or `46f6936d`), except the line references of section 7.5, which were moved to `dba21a9e` |
| Commit for the 26.10.0 and 26.10.1 items | `dba21a9ef69222b40a18227c2783853b954dfdf7` (`NortheBridge/luminalshine@dba21a9e`), the `26.10.1` tag, commit date 2026-10-28 00:32 UTC, `git describe`: `26.10.1`. The tags `26.10.0-beta.1` (`e2662a44`), `26.10.0` (`a9928254`, commit date 2026-10-26 00:56 UTC) and `26.10.1` were read with `git show`, `git diff` and `git grep`, with no checkout (the local clone has the owner's own branch checked out). `src/` is the same at `a9928254` and at `dba21a9e`, except for the new `src/platform/windows/desktop_guard_win32.h` |
| 26.10.0 and 26.10.1 fixtures | Not captured. No plain-HTTP capture of a running 26.10.0 or 26.10.1 host exists, so `core/tests/fixtures/luminalshine/26.10.1/` is not there yet, and no contract test reads fixtures of these versions. Take the capture in the next fixture task (step 6 below) |

The host source tree is the only authority for the host side. The Moonlight
client facts were checked against the Moonlight Android 12.2 code in this
repository (`apps/android`, Borealis code since ADR 0054) at the commit named
under "Reference conventions" below. Comparisons with Artemis were checked against
Artemis at commit `c5cf27f4` (2026-09-09).

## Reference conventions

- A reference of the form `src/nvhttp.cpp:2831` is a path relative to the
  luminalshine repository root at `5afdbdc6`, followed by a line (or
  `first-last` line range).
- Inside an item marked **Changed in 26.10.0-beta.1** in `luminalshine.md`, a
  `src/` reference is relative to the same repository at `46f6936d` (the commit
  named in the "Verified against" table), not at `5afdbdc6`.
- Inside an item marked **Changed in 26.10.0** or **Changed in 26.10.1** in
  `luminalshine.md`, a `src/` reference (and a `docs/` or `third-party/` reference of
  the host) is relative to the same repository at `dba21a9e` (the `26.10.1` tag), the
  commit named in the "Verified against" table. The references of section 7.5 of
  `luminalshine.md` are at `dba21a9e` too: they were moved from `5afdbdc6`. A reference
  of such an item to this repository is pinned `borealis@f81be3409:` (the merge of #95).
- A reference prefixed with `borealis:` is relative to the root of this
  repository (for example `borealis:apps/android/app/src/main/jni/moonlight-core/callbacks.c:404-410`).
  A reference prefixed with `borealis@<commit>:` is relative to the root of this repository
  at that commit (for example `borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:815`).
  Every `borealis:` reference was checked at commit `a050fbac` (2026-10-18).
  References to files that Sprint 12 converted to Kotlin are pinned to
  `borealis@2f321dc77`, the last commit before the Kotlin conversion, so their line numbers stay true.
  `Game.java`, which Sprint 14 converted, is pinned to `borealis@5816473bc`, the merge of the
  last `Game` characterization task, before the `connecting` flag was removed. Its line numbers
  were checked at that commit.
  The files are Borealis code since
  [ADR 0054](../adr/0054-declare-independence-from-moonlight.md), so a line can
  move whenever Borealis edits its file; where the Moonlight 12.2 behaviour a sentence
  describes no longer exists in that form, the sentence says so.
  Vendored moonlight-common-c files are cited by their path under
  `core/third_party/moonlight-common-c/` (upstream `874ac95` plus the fork patch
  and the fixes listed in [core/third_party/README.md](../../core/third_party/README.md)).
- A reference prefixed with `artemis:` is relative to the Artemis repository
  root at `c5cf27f4`.
- A claim marked **Unverified** was not confirmed against source or against a
  running host. Do not build behaviour on it without verifying it first.
- Statements under the heading "Borealis recommendation" or in the
  "Client obligations for Borealis" section are design guidance, not host
  behaviour.
- No claim about a live host session is made anywhere in this folder, except
  the items marked **Observed** (device rounds of Sprints 2 and 3, and the VHF gamepad
  round of 2026-10-28 in section 13) and the
  recorded `/unpair` responses in [luminalshine.md](luminalshine.md) section 18,
  item 4 (the Phase 1 capture of 26.09.1 and the capture of 26.10.0-beta.1 on
  2026-10-23). The rest of the analysis was static source reading.

## How to update the contract when the host changes

1. Fetch the new LuminalShine revision and record its commit hash, commit date
   and nearest tag.
2. Re-verify every reference. Open each cited path and line range, fix line
   numbers that drifted, and correct or delete any claim the source no longer
   supports. The most volatile areas are listed in the next section.
3. Diff the revision against `5afdbdc6` for the files named in the next
   section so that new endpoints, status codes and attributes are not missed
   (a plain `git diff 5afdbdc6..<new> -- <paths>` is sufficient).
4. Update `luminalshine.md` and `pyrowave.md`, keeping the section structure.
   Move superseded behaviour into a short "Changed since" note rather than
   silently rewriting it, so Borealis can still talk to older hosts.
5. Bump the "Verified against" table above (commit, date, tag), the Borealis
   commit named under "Reference conventions" and the compatibility table below.
6. Update the contract fixtures (recorded host responses and captured
   handshakes used by the Borealis core tests). They live in
   `core/tests/fixtures/luminalshine/<version>/`, one directory per verified
   LuminalShine version. The directories exist: `26.09.1/` holds
   `serverinfo-http.xml`, `unpair-http.response` and `PROVENANCE.md`;
   `26.10.0-beta.1/` holds `serverinfo-http.xml`, `unknown-path-http.response`,
   `unpair-http.response` and `PROVENANCE.md`; the golden vectors are in
   `core/tests/fixtures/vectors/`. Both fixture folders hold plain-HTTP responses
   only, so neither has the HTTPS-only `LuminalShineVersion`. There is no
   `26.10.1/` directory yet (the capture is not done). Add a new directory
   for a new version instead of overwriting the old one.
7. Run the contract tests against the new fixtures and record the result in the
   compatibility table.
8. Commit with a Conventional Commit message such as
   `docs(host-contract): re-verify against luminalshine <tag>`.

### Volatile areas to re-check first

| Area | Host files |
| --- | --- |
| Launch/resume status codes and messages | `src/nvhttp.cpp` (`launch`, `resume`, `cancel`, `run_on_launch_worker`), `src/serial_executor.h` |
| Unknown paths and the host version marker | `src/nvhttp.cpp` (`not_found`, `serverinfo`, `put_luminalshine_version`), `src/nvhttp.h` |
| Pairing flow and limits | `src/nvhttp.cpp` (`pair`, `submit_pin`) |
| Codec advertisement | `src/nvhttp.cpp` (`serverinfo`), `src/rtsp.cpp` (`cmd_describe`) |
| ANNOUNCE parsing and validation | `src/rtsp.cpp` (`cmd_announce`) |
| Display-mode reconciliation | `src/rtsp.cpp`, `src/requested_display_mode.h` |
| Control stream and feedback | `src/stream.cpp` (`controlBroadcastThread`) |
| PyroWave contract | `src/pyrowave/contract.h`, `src/pyrowave/backend.cpp`, `src/pyrowave/pyrowave_encode.cpp`, `third-party/pyrowave/src/pyrowave/pyrowave_common.h`, `docs/pyrowave.md` |
| REST API and authentication | `src/confighttp.cpp`, `src/http_auth.cpp` |
| Default settings | `src/config.cpp` |
| Gamepad emulation (the VHF driver, from 26.10.0) | `src/platform/windows/input.cpp`, `src/platform/windows/vhf_gamepad.cpp`, `src/platform/windows/vhf_gamepad_policy.cpp`, `src/platform/windows/misc.cpp` (`is_virtual_gamepad_available`), `docs/virtual-gamepad-driver.md`, `docs/configuration.md` (`gamepad`) |
| Encoder probe | `src/video.cpp` (`build_probe_cache_key`, `import_encoder_probe_snapshot`), `src/platform/windows/video_worker.cpp` |

## Compatibility table

The table is a stub. A row is added for each Borealis release; the status
column is filled in by the contract tests and manual verification described
above.

| Borealis version | LuminalShine version | Host commit | Status | Notes |
| --- | --- | --- | --- | --- |
| (unreleased) | `26.09.1` + 15 commits | `5afdbdc6` | Known issues | Device-tested on 26.09.1 in Phase 1 and in the later sprint rounds (Galaxy S22 and Google TV Streamer; see [device-validation.md](../development/device-validation.md)). The Sprint 8 round of 2026-10-18 ran against LuminalShine 26.09.1 ([section 13](../development/device-validation.md#13-results-sprint-8)). Known issues: the 400 "Out of order call" after a wrong PIN, handled by one automatic restart; NortheBridge/luminalshine#194 (stray bytes after a 404; fixed by PR #200, which shipped in 26.10.0-beta.1: verified in source at `46f6936d` and on a running host); #198 (teardown hang after a disconnect); #201 (a second client leaving a shared app ends the first client's session); #202 (a long `/launch` blocks every other HTTPS request; fixed by PR #203, which shipped in 26.10.0-beta.1: verified in source at `46f6936d`); #204 (host crash after the virtual display cleanup of an ended session); #205 (a second client's `/resume` fails the RTSP tag check while the first client's launch is still setting up). The [roadmap](../roadmap.md#host-side-items-luminalshine) lists the host items with the state when last checked |
| (unreleased) | `26.10.0-beta.1` (a prerelease of 2026-10-20) | `46f6936d` (the commit its items were verified at; the beta tag, fetched on 2026-10-28, is `e2662a44`, where `src/` is the same) | Known issues | The release items #199, #200 and #203 are verified in source at `46f6936d` ([luminalshine.md](luminalshine.md), "Changed in 26.10.0-beta.1"), and the plain-HTTP responses of a running host are recorded in the [26.10.0-beta.1 fixtures](../../core/tests/fixtures/luminalshine/26.10.0-beta.1/PROVENANCE.md); no contract test reads them yet. The status is Known issues because the issues below stay open. Live-checked in part in the Sprint 10 round of 2026-10-21 ([section 15](../development/device-validation.md#15-results-sprint-10)): launch, stream, quit, a second device during a launch (#203) and Host details; pairing, teardown and `/unpair` were not run. Known issues: #198, #201, #204 and #205 stay open; #194 and #202 are fixed on the host. "Quitting `<app>`…" stayed on screen until `/cancel` answered: about 17 s once in the night round of 2026-10-21, and 5.5 to 6.7 s in the morning smoke of the same day (5 to 7 s on 26.09.1). The cause is not known, and it is in [Sprint 15](../roadmap.md#sprint-15-the-stream-screens-and-glass) of the roadmap. The release ships #199 (`LuminalShineVersion` in the HTTPS `/serverinfo` only), #200 and #203. The pairing, teardown and PyroWave work its notes mention (for example `e31d66e9`, `d9fc0190` and `57b56e9b`) is already in `5afdbdc6`, which the rest of the contract covers; the wrong-PIN 409 after a failed pairing comes from `e31d66e9`, not from the 26.10.0-beta.1 merges. Its other source changes need no client change: a lock on the paired-client list; `/serverinfo` reading the running app without waiting for a launch, so a host still starting its app now answers `SUNSHINE_SERVER_BUSY` at once; a 503 `The host is shutting down.` during host shutdown ([luminalshine.md](luminalshine.md), section 7.5); and `POST /api/apps/import` in the Web UI REST API |
| (unreleased) | `26.10.0` (published 2026-10-26) | `a9928254` (the tag was read; the items were verified at `dba21a9e`, where `src/` is the same apart from `desktop_guard_win32.h`) | Known issues | The release replaces the ViGEm gamepad bus with the VHF virtual gamepad driver (package v0.1.0-beta.9, driver 0.1.0.53), fixes the encoder probe after a failed codec (#210) and adds a host-persisted changelog to the Web UI. The contract records the first two in [luminalshine.md](luminalshine.md) ("Changed in 26.10.0", sections 9, 12, 13, 17.2 and 18). The client-facing files are those of 26.10.0-beta.1, so no client change is needed; Borealis has no handler for the DualSense adaptive triggers that the new backend can send. No fixtures. Known issues: #198, #201, #204 and #205 stay open (checked on 2026-10-28) |
| (unreleased) | `26.10.1` (published 2026-10-28) | `dba21a9e` | Known issues | A maintenance release of the desktop helper (Exclusive mode with OLED Protection: application routing to the virtual display, and the input of the black covers), host side only. By the release notes it does not alter the networking protocols, authentication, encoder selection or capture-start logic, and the client-facing files are those of 26.10.0-beta.1. Live-checked in part in the VHF gamepad round of 2026-10-28 ([section 19](../development/device-validation.md#vhf-gamepad-round-2026-10-28)): launch and stream on the Galaxy S22 and the Google TV Streamer, and the virtual gamepad (an Xbox Series, an Xbox One and a Switch Pro Controller were created, and rumble reached the client). That round looked at the pads only: it recorded no pairing, resume, Quit timing or `/unpair` check, and it did not test trigger rumble, two pads at once or the XInput receiver. The 26.10.1 fixtures are not captured, and no contract test reads fixtures of this version. Known issues: #198, #201, #204 and #205 are still open (checked on 2026-10-28; no commit between 26.10.0-beta.1 and 26.10.1 mentions them). `/cancel` waits for the display helper's revert (`src/platform/windows/virtual_display_cleanup.cpp:116-123`, unchanged since 26.10.0-beta.1) and 26.10.1 changes that helper (`tools/display_settings_helper.cpp`), so the Quit timing may differ from the figures in the 26.10.0-beta.1 row; [Sprint 15](../roadmap.md#sprint-15-the-stream-screens-and-glass) measures it again |

Allowed status values:

| Status | Meaning |
| --- | --- |
| Documented, not yet tested | The contract exists but no automated or manual test has run against this host version |
| Fixture-tested | Contract tests pass against recorded fixtures for this version |
| Live-verified | A Borealis build was exercised against a real host of this version (pairing, launch, resume, stream) |
| Known issues | A deviation from the contract was found; see the notes column |
| Unsupported | The host version is known to be incompatible |
