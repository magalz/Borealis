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
| Contract revision date | 2026-10-18 |
| Protocol version string the host reports | `7.1.431.-1` (`src/nvhttp.h:37`) |
| Lineage | Sunshine, then Vibeshine, then LuminalShine; not derived from Apollo (`gh-pages-template/compare.html:66-82`) |

The host source tree is the only authority for the host side. The Moonlight
client facts were checked against the Moonlight Android 12.2 subtree in this
repository (`apps/android`). Comparisons with Artemis were checked against
Artemis at commit `c5cf27f4` (2026-09-09).

## Reference conventions

- A reference of the form `src/nvhttp.cpp:2831` is a path relative to the
  luminalshine repository root at `5afdbdc6`, followed by a line (or
  `first-last` line range).
- A reference prefixed with `borealis:` is relative to the root of this
  repository (for example `borealis:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:815`).
  Every `borealis:` reference was checked at commit `a050fbac` (2026-10-18).
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
  the items marked **Observed** (device rounds of Sprints 2 and 3) and the
  recorded `/unpair` response in [luminalshine.md](luminalshine.md) section 18,
  item 4 (Phase 1 capture). The rest of the analysis was static source reading.

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
   LuminalShine version. The directory exists: `26.09.1/` holds
   `serverinfo-http.xml`, `unpair-http.response` and `PROVENANCE.md`, and the
   golden vectors are in `core/tests/fixtures/vectors/`. Add a new directory for a new version
   instead of overwriting the old one.
7. Run the contract tests against the new fixtures and record the result in the
   compatibility table.
8. Commit with a Conventional Commit message such as
   `docs(host-contract): re-verify against luminalshine <tag>`.

### Volatile areas to re-check first

| Area | Host files |
| --- | --- |
| Launch/resume status codes and messages | `src/nvhttp.cpp` (`launch`, `resume`, `cancel`) |
| Pairing flow and limits | `src/nvhttp.cpp` (`pair`, `submit_pin`) |
| Codec advertisement | `src/nvhttp.cpp` (`serverinfo`), `src/rtsp.cpp` (`cmd_describe`) |
| ANNOUNCE parsing and validation | `src/rtsp.cpp` (`cmd_announce`) |
| Display-mode reconciliation | `src/rtsp.cpp`, `src/requested_display_mode.h` |
| Control stream and feedback | `src/stream.cpp` (`controlBroadcastThread`) |
| PyroWave contract | `src/pyrowave/contract.h`, `src/pyrowave/backend.cpp`, `src/pyrowave/pyrowave_encode.cpp`, `third-party/pyrowave/src/pyrowave/pyrowave_common.h`, `docs/pyrowave.md` |
| REST API and authentication | `src/confighttp.cpp`, `src/http_auth.cpp` |
| Default settings | `src/config.cpp` |

## Compatibility table

The table is a stub. A row is added for each Borealis release; the status
column is filled in by the contract tests and manual verification described
above.

| Borealis version | LuminalShine version | Host commit | Status | Notes |
| --- | --- | --- | --- | --- |
| (unreleased) | `26.09.1` + 15 commits | `5afdbdc6` | Known issues | Device-tested on 26.09.1 in Phase 1 and in the later sprint rounds (Galaxy S22 and Google TV Streamer; see [device-validation.md](../development/device-validation.md)). The Sprint 8 round of 2026-10-18 ran against LuminalShine 26.09.1 ([section 13](../development/device-validation.md#13-results-sprint-8)). Known issues: the 400 "Out of order call" after a wrong PIN, handled by one automatic restart; NortheBridge/luminalshine#194 (stray bytes after a 404); #198 (teardown hang after a disconnect); #201 (a second client leaving a shared app ends the first client's session); #202 (a long `/launch` blocks every other HTTPS request; PR #203 proposes a fix); #204 (host crash after the virtual display cleanup of an ended session); #205 (a second client's `/resume` fails the RTSP tag check while the first client's launch is still setting up). The [roadmap](../roadmap.md#host-side-items-luminalshine) lists the host items with the state when last checked |

Allowed status values:

| Status | Meaning |
| --- | --- |
| Documented, not yet tested | The contract exists but no automated or manual test has run against this host version |
| Fixture-tested | Contract tests pass against recorded fixtures for this version |
| Live-verified | A Borealis build was exercised against a real host of this version (pairing, launch, resume, stream) |
| Known issues | A deviation from the contract was found; see the notes column |
| Unsupported | The host version is known to be incompatible |
