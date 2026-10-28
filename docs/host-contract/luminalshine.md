# LuminalShine client contract

This document describes what a LuminalShine host offers to a GameStream/Moonlight
style client and what it requires from one. It was verified against
`NortheBridge/luminalshine@5afdbdc6` (2026-10-05, nearest tag `26.09.1`); see
[README.md](README.md) for the reference conventions, the update procedure and
the compatibility table. PyroWave is covered separately in
[pyrowave.md](pyrowave.md).

The items marked **Changed in 26.10.0-beta.1** come from the 26.10.0-beta.1
release notes (2026-10-20), except the Web UI `POST /api/apps/import` note, which comes from the
commit history, and were verified in the host source at
`NortheBridge/luminalshine@46f6936d` (2026-10-22, `git describe`:
`26.09.1-38-g46f6936d`), a later commit than the revision above. The `src/`
references inside those items are lines at `46f6936d`, not at `5afdbdc6`, and
`src/` is the same at `46f6936d` and at `e2662a44` (the merge of PR #206,
2026-10-20). The plain-HTTP responses of a running 26.10.0-beta.1 host are recorded
in [the 26.10.0-beta.1 fixtures](../../core/tests/fixtures/luminalshine/26.10.0-beta.1/PROVENANCE.md).

The items marked **Changed in 26.10.0** and **Changed in 26.10.1** come from the
release notes of those releases (`gh release view`; GitHub published 26.10.0 on
2026-10-26 and 26.10.1 on 2026-10-28, UTC). They were verified in the host source at
`NortheBridge/luminalshine@dba21a9e` (the `26.10.1` tag, commit date 2026-10-28
UTC), read with `git show`, `git diff` and `git grep` on the tags `26.10.0-beta.1`
(`e2662a44`), `26.10.0` (`a9928254`) and `26.10.1`, without a checkout. The `src/`
references inside those items are lines at `dba21a9e`. `src/` is the same at
`a9928254` and at `dba21a9e`, except for `src/platform/windows/desktop_guard_win32.h`,
which is new in 26.10.1.
**Nothing client-facing changed since 26.10.0-beta.1:** `src/nvhttp.cpp`,
`src/rtsp.cpp`, `src/config.cpp` and `src/nvhttp.h` are the same file (the same git
blob) at `e2662a44`, `46f6936d`, `a9928254` and `dba21a9e`, so pairing, launch,
resume, cancel, `/serverinfo`, RTSP, audio and the defaults are as the sections below
say. The only edits in `src/stream.cpp` and `src/input.cpp` since then concern the check
for a missing gamepad driver (`src/stream.cpp:3376-3385`, `src/input.cpp:1755-1763`).
What changed is on the host side: the encoder probe (section 9), the virtual gamepads
(sections 12, 13, 17.2 and 18, item 13) and, in 26.10.1, the desktop helper (Exclusive
mode with OLED Protection), which by the release notes does not alter the networking
protocols, authentication, encoder selection or capture-start logic. An upgrade ends a
running stream (section 18, item 18). The rest of the document was not re-verified: its
line numbers are the ones at `5afdbdc6`, or at `46f6936d` where an item says so, except
the references of section 7.5, which are at `dba21a9e`. The plain-HTTP responses of a
running 26.10.0 or 26.10.1 host were not captured.

Everything here comes from static reading of the host source, except the items
marked **Observed** (runtime observations from the Sprint 2 and 3 device rounds and,
in section 13, from the VHF gamepad round of 2026-10-28) and the recorded `/unpair`
responses in section 18, item 4. Statements about
runtime behaviour that the source alone cannot establish are marked
**Unverified**.

## 1. Host identity

- LuminalShine descends from Sunshine through Vibeshine. It is not derived from
  Apollo (`gh-pages-template/compare.html:66-82`).
- The project describes itself as a Windows-only streaming host (`README.md:26`).
  The virtual display, GPU capture and input emulation described below are
  Windows code paths (`src/platform/windows/virtual_display.cpp`,
  `src/platform/windows/input.cpp`).
- The host reports the GameStream protocol version `7.1.431.-1`; the negative
  fourth component tells Moonlight that the server is Sunshine-derived
  (`src/nvhttp.h:32-37`). It reports the emulated GFE version `3.23.0.74`
  (`src/nvhttp.h:42`). These are protocol-emulation constants, not the
  LuminalShine release version; in `26.09.1` there is no field in `/serverinfo`
  that carries the LuminalShine version (see proposal P1 in section 20).
  **Changed in 26.10.0-beta.1** (#199; verified in source at `46f6936d`): the
  HTTPS `/serverinfo` appends one element, `LuminalShineVersion`, as the last
  child of `<root>`, after `state`, and keeps `appversion` (`7.1.431.-1`) and
  `GfeVersion` (`src/nvhttp.cpp:2532-2533`, `2612-2617`; the element is written by
  `put_luminalshine_version`, `src/nvhttp.cpp:2507-2509`, `src/nvhttp.h:69-78`).
  The call sits inside `if constexpr (std::is_same_v<SunshineHTTPS, T>)`, so the
  plain-HTTP response is unchanged and carries no version: a client can read the
  release only over HTTPS, that is, once it is paired. The header comment gives
  the reason: the HTTP port answers unauthenticated peers
  (`src/nvhttp.h:73-75`). The value is `PROJECT_VERSION`, fixed when the host is
  built: the CI `BUILD_VERSION`, the release tag or the nearest git tag, with a
  leading `v` removed (`cmake/prep/build_version.cmake:44-51`, `107-115`,
  `137-141`), so a client must treat the string as free text, not as a strict
  version. The plain-HTTP capture of a 26.10.0-beta.1 host (2026-10-23) has no
  `LuminalShineVersion` and is byte for byte the 26.09.1 response. The HTTPS
  value of that host was not read (no HTTPS call was allowed). The Borealis
  client does not read the field yet (Sprint 20), so Host details still shows
  `7.1.431.-1`.
- `/serverinfo` reports `state` as `SUNSHINE_SERVER_BUSY` while an app is
  running and `SUNSHINE_SERVER_FREE` otherwise, and `currentgame` as the running
  app id (`src/nvhttp.cpp:2578-2581`). Moonlight therefore classifies the host
  as non-NVIDIA (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:399-400`).
- The host project lists Twilight (LG webOS) as its first-party client, and its
  documentation states that any Moonlight client works
  (`docs/system_requirements.md:70-76`, `gh-pages-template/clients.html:86-96`).
  No code branches on the names Artemis, Apollo, Moonlight or Aurora; only
  comments and moonlight-common-c includes mention them (searched `src`).

## 2. Ports and discovery

All ports are offsets from the base port, default 47989
(`src/config.cpp:948`, `src/network.cpp:325-327`).

| Service | Offset | Default port | Reference |
| --- | --- | --- | --- |
| GameStream HTTP | 0 | 47989 | `src/nvhttp.h:47` |
| GameStream HTTPS | -5 | 47984 | `src/nvhttp.h:52` |
| Web UI and REST API (HTTPS) | +1 | 47990 | `src/confighttp.h:31` |
| Video (UDP) | +9 | 47998 | `src/stream.h:23` |
| Control (ENet over UDP) | +10 | 47999 | `src/stream.h:24` |
| Audio (UDP) | +11 | 48000 | `src/stream.h:25` |
| RTSP (TCP) | +21 | 48010 | `src/rtsp.h:27` |

The `sessionUrl0` value of `/launch` and `/resume` carries only the RTSP port
(`src/nvhttp.cpp:3135-3142`). The video, control and audio ports are announced
through `server_port=<port>` in the RTSP SETUP responses (`src/rtsp.cpp:1389-1390`).
A client should use those values instead of hard-coding offsets.

Discovery is mDNS service type `_nvstream._tcp`
(`src/platform/common.h:907`), registered on the HTTP port with a single empty
TXT string (`src/platform/windows/publish.cpp:106-135`). No attributes are
published, so a client must query `/serverinfo` to learn anything beyond the
instance name, host name and HTTP port. The MAC address is returned only over
HTTPS to a paired client; over HTTP the placeholder `00:00:00:00:00:00` is
returned (`src/nvhttp.cpp:2518-2523`).

For `/serverinfo` the host returns `LocalIP` as `127.0.0.1` when the request
arrives over a real IPv6 address, so a client must not overwrite its stored LAN
IPv4 address with it (`src/nvhttp.cpp:2525-2538`, the branch at `2534-2535` is not
conditional on the scheme;
Moonlight already skips `127.` addresses in
`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/ComputerDetails.java:119-120`).

## 3. GameStream HTTP(S) endpoints

The host serves exactly the endpoints in the table. Any other path on either
server returns an XML body with `status_code` 404 (`src/nvhttp.cpp:2127-2143`,
`src/nvhttp.cpp:3723-3748`). All endpoints use GET.

**Changed in 26.10.0-beta.1** (#200, which fixes #194; verified in source at
`46f6936d`): an unknown path, on both servers, returns one correctly framed
response, `HTTP/1.1 200 OK` with `Content-Length: 64` and the XML
`status_code="404"`, and then the connection closes. `not_found` now writes the XML
once and sets `close_connection_after_response`; the second write that streamed a
header-less `HTTP/1.1 404 NOT FOUND` block into the response is gone
(`src/nvhttp.cpp:2146-2158`, instantiated for `SimpleWeb::HTTP` at `2160`, declared
in `src/nvhttp.h:93-102`). Both servers register it as their default resource
(`src/nvhttp.cpp:3822` and `3850`). The host's own regression test pins the exact
bytes, `HTTP/1.1 200 OK\r\nContent-Length: 64\r\n\r\n` plus the 64-byte body and
then end of stream, with nothing after it
(`tests/unit/test_nvhttp_not_found.cpp:37-39`, `161-166`). A running 26.10.0-beta.1
host answered an unknown path with those 103 bytes on 2026-10-23
([`unknown-path-http.response`](../../core/tests/fixtures/luminalshine/26.10.0-beta.1/unknown-path-http.response)).
The framing is not visible in the host source of this checkout, because
Simple-Web-Server is a git submodule that is not checked out: the header bytes
come from that test and from the capture. The 26.09.1 behaviour below, with the
trailing bytes, stays the rule for older hosts.

| Path | HTTP | HTTPS | Purpose |
| --- | --- | --- | --- |
| `/serverinfo` | yes | yes | Host description. Over HTTPS, `PairStatus` is 1 whenever the request carries a `uniqueid` query parameter; the host does not look the client up, because the HTTPS server only serves clients whose certificate is paired (`src/nvhttp.cpp:2494-2501`) |
| `/pair` | yes | yes (all phases are accepted; a normal client sends only `pairchallenge` here) | Pairing (section 4; `src/nvhttp.cpp:3725`, `3744`, `2204-2345`) |
| `/applist` | no | yes | App ids, titles and `IsHdrSupported` (`src/nvhttp.cpp:2665-2689`) |
| `/appasset` | no | yes | App cover as `image/png` for an `appid` (`src/nvhttp.cpp:3588-3599`) |
| `/launch` | no | yes | Start an app and prepare a stream (section 7) |
| `/resume` | no | yes | Resume the running app (section 7) |
| `/cancel` | no | yes | Terminate sessions and the running app (section 7.6) |

Not present, by absence from the route table (`src/nvhttp.cpp:3723-3748`;
`src/nvhttp.cpp:3822-3854` at `46f6936d`, which adds no route):
`/unpair`, `/actions/clipboard`, and any other Apollo/Artemis extension.
Unpairing is available only through the REST API (section 17).

The HTTPS server verifies the client certificate during the TLS handshake and
returns a 401 XML body with the message `The client is not authorized.
Certificate verification failed.` when verification fails
(`src/nvhttp.cpp:3708-3720`). The host requires the certificate to match a paired
client's stored certificate (`src/nvhttp.cpp:3683-3693`).

`/applist` returns, for every app, only `ID`, `AppTitle` and `IsHdrSupported`
(`src/nvhttp.cpp:2680-2689`). `IsHdrSupported` reflects only whether HEVC
Main10 is active on the host (`video::active_hevc_mode == 3`,
`src/nvhttp.cpp:2685`); it says nothing about AV1 Main10 or PyroWave HDR.

The host gives no field for the kind of app, so a client cannot tell Desktop from
a game by data. Borealis recognises Desktop by its title: the app titled
"Desktop", ignoring case and the spaces around the title. A title that only
contains the word, such as "Remote Desktop", is another app, and so is an unknown
title. The client uses this only for the wording of Quit: Desktop ends the stream
and closes no program, and any other app is closed and loses its unsaved progress
([UC-004](../use-cases/0004-use-the-in-game-menu.md)). If the host renames
Desktop, the client treats it as any other app, which is the cautious reading.

## 4. Pairing

The cryptographic exchange is the standard four-phase GameStream pairing
(`getservercert`, `clientchallenge`, `serverchallengeresp`, `clientpairingsecret`,
then `pairchallenge` over HTTPS): `src/nvhttp.cpp:1941-2106`, dispatched at
`src/nvhttp.cpp:2204-2345`. The differences that matter to a client:

### 4.1 PIN and name are entered on the host

- The client derives its key from a four-digit decimal PIN and the salt in the
  usual way. The PIN must be exactly four ASCII digits
  (`src/nvhttp.cpp:1706-1710`).
- The user enters the PIN and the client's display name in the host Web UI,
  which calls `POST /api/pin` with `{"pin","name","pairing_id"}`
  (`src/confighttp.cpp:3988-4038`; `pairing_id` is optional). The paired name is
  taken from that submission and trimmed (`src/nvhttp.cpp:2434`). Any
  `devicename` the client sends is not used for the name.
- The HTTP request for phase 1 (`phrase=getservercert`) is held open until the
  user submits the PIN. Moonlight Android already uses a client with no read
  timeout for exactly this request
  (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/PairingManager.java:205-209`).
  Any other client must do the same.
- A host that has the PIN-on-stdin flag set reads the PIN from standard input
  instead (`src/nvhttp.cpp:2280-2282`). This is a host operator mode; it is not
  expected in normal installs.

### 4.2 Pairing sessions and limits

| Limit | Value | Reference |
| --- | --- | --- |
| Pending pairing session lifetime | 5 minutes | `src/nvhttp.cpp:917` |
| Completed-outcome retention | 5 minutes | `src/nvhttp.cpp:918` |
| Maximum concurrent pending pairings | 32 | `src/nvhttp.cpp:919` |
| Maximum retained outcomes | 64 | `src/nvhttp.cpp:920` |

- Concurrent pairing requests are told apart by an opaque `pairing_id` that the
  host generates (`src/nvhttp.cpp:2243-2250`) and that the Web UI lists through
  `GET /api/pin` (`src/confighttp.cpp:3949-3970`). The client never sees it.
  Pairing states are `pending_pin`, `awaiting_client`, `paired`, `failed`,
  `expired` (`src/nvhttp.cpp:1661-1676`).
- Every phase after the first must come from the same normalized IP address as
  phase 1, or the host answers 403 `Pairing request peer mismatch`
  (`src/nvhttp.cpp:2312-2317`). The address is compared after normalization, so
  an IPv4-mapped IPv6 address equals its IPv4 form
  (`src/network.cpp:270-272`, `src/nvhttp.cpp:2173-2174`). A client that switches
  between IPv4 and IPv6 between phases, for example when Android changes
  network, will fail pairing.
- A phase 1 retry with the same certificate and salt is answered idempotently
  only after the PIN was applied, while the session is in the `getservercert`
  phase (`last_phase == GETSERVERCERT`, `src/nvhttp.cpp:2210-2233`). Any other
  non-initial phase, including a same-certificate retry in a later phase, gets
  409 `Pairing exchange already in progress` (`src/nvhttp.cpp:2230`). A retry
  while the PIN is still pending re-arms the held response and answers the
  previous held request with 409 `Pairing request superseded by retry`
  (`src/nvhttp.cpp:2184`, `src/nvhttp.cpp:2252-2271`).
- Any out-of-order phase, short salt, short secret, invalid certificate or
  failed hash makes the host delete the session and answer 400 with a message
  such as `Out of order call to getservercert`
  (`src/nvhttp.cpp:1933-1939`, `1941-1950`, `2040-2060`).

### 4.3 Pairing status codes

These are XML `status_code` values (HTTP status is 200 for all of them unless
noted otherwise by the web server).

| Code | Message | Meaning | Reference |
| --- | --- | --- | --- |
| 400 | `Missing uniqueid parameter` | Client bug | `src/nvhttp.cpp:2166` |
| 400 | `Invalid uniqueid` | Phase 2 to 4 for an unknown session (expired or reset) | `src/nvhttp.cpp:2306` |
| 400 | various (`Out of order call to ...`, `Salt too short`, `Client pairing secret too short`, `Invalid client certificate`) | Protocol error; session deleted; restart pairing | `src/nvhttp.cpp:1933-1939` |
| 400 | `Pairing exchange invalidated` | A held phase 1 response was invalidated by an out-of-order phase | `src/nvhttp.cpp:2191` |
| 403 | `Pairing request peer mismatch` | A later phase came from a different IP | `src/nvhttp.cpp:2314` |
| 404 | `Invalid pairing request` | Unknown query | `src/nvhttp.cpp:2342` |
| 408 | `Pairing request expired` | The 5 minute window elapsed before the PIN was entered | `src/nvhttp.cpp:1908-1910` |
| 409 | `Pairing exchange already in progress` | A phase 1 request arrived after the PIN was applied and it is not an exact retry in the `getservercert` phase (this includes a same-certificate retry in a later phase) | `src/nvhttp.cpp:2230` |
| 409 | `Pairing request identity mismatch` | Retry used a different certificate or salt while the PIN is pending | `src/nvhttp.cpp:2265` |
| 409 | `Pairing request superseded by retry` | The held phase 1 request was replaced by a retry | `src/nvhttp.cpp:2184` |
| 410 | `Host pairing state was reset` | The host operator reset stored state | `src/nvhttp.cpp:3905` |
| 503 | `Too many pending pairing requests` | 32 pending pairings exist | `src/nvhttp.cpp:2239` |

Recommended client behaviour: do not retry 400, 403, 404 or 410 automatically;
tell the user to restart pairing. 408 means "enter the PIN faster and restart".
409 and 503 may be retried once after a short delay, or explained to the user
(another device may be pairing).

Borealis classifies these errors in `borealis_pair_classify` (`core/README.md`),
by status and case-insensitive message text:

| Status and message | Borealis action |
| --- | --- |
| 400 `Out of order call to getservercert`, 400 `Invalid uniqueid`, 400 `Pairing exchange invalidated`, 410 `Host pairing state was reset` | Tell the user the host cleared an unfinished attempt, then start pairing once more with a new PIN. A second one in the restarted attempt is shown as a normal failure |
| 409 `Pairing exchange already in progress`, 409 `Pairing request identity mismatch`, 503 `Too many pending pairing requests` | A message per error: the host holds another session (it expires after 5 minutes; restarting LuminalShine clears it), or another device is pairing. No restart |
| 403 `Pairing request peer mismatch`, 409 `Pairing request superseded by retry` | A message per error, no restart |
| anything else | The upstream text |

**After a wrong PIN (LuminalShine 26.09.1).** LuminalShine has no `/unpair` route, so it keeps the
session of the failed attempt. The next attempt's phase 1 is held until the PIN is
typed in the Web UI, then fails with 400 `Out of order call to getservercert`;
that failure deletes the session (`fail_pair`, `src/nvhttp.cpp:1929-1945`), so the
attempt after it succeeds. The automatic restart turns this into one extra PIN
dialog instead of a raw error.

**Changed since 26.09.1 (in the 26.10.0-beta.1 release).** Seen live on 2026-10-21 and verified in source at
`46f6936d`. The behaviour is the pairing rework `e31d66e9` (PR #188, 2026-10-01).
The `26.09.1` tag does not contain it (the tag has no 409 `Pairing exchange already
in progress`); `5afdbdc6` and every later commit do. `pair`, `fail_pair`,
`serverchallengeresp` and `submit_pin` are the same at `5afdbdc6` and at `46f6936d`,
and `src/` is the same at `e2662a44`, at `236aff9a` and at `46f6936d` (the later
commits change `.github/`, `scripts/` and `tests/` only).

- The client runs phases 1 to 3, detects the wrong PIN after phase 3
  (`serverchallengeresp`), and stops, as every Moonlight client does. The host
  never sees the mistake: phase 3 only decrypts the client's response with the key
  derived from the salt and the PIN the host holds, and answers `paired` 1 with
  the signed server secret (`src/nvhttp.cpp:2030-2058`). The host compares the
  client hash only in phase 4 (`src/nvhttp.cpp:2095-2097`). The client then sends
  `/unpair`, which LuminalShine does not route (the route tables,
  `src/nvhttp.cpp:3822-3854`, have no `/unpair`), so it reaches `not_found` and
  gets the single 404 response of section 3. Nothing on the host removes the
  session.
- The host keeps the session at `last_phase == SERVERCHALLENGERESP`
  (`src/nvhttp.cpp:2035`). Its log ends at "Pairing PIN delivered; awaiting client
  proof" (`src/nvhttp.cpp:2499`). Phase 4 never arrives, so `fail_pair`
  (`src/nvhttp.cpp:1953-1959`), which removes a session on a failure, does not
  run.
- Every new phase 1 from the same client gets 409 `Pairing exchange already in
  progress` at once (`src/nvhttp.cpp:2227-2251`): the session exists and its phase
  is not `NONE`, and only a phase-1 retry inside the `getservercert` phase is
  answered again. The session is keyed by the client's `uniqueid` query value in an
  in-memory map (`src/nvhttp.cpp:909`, `2189`, `2225`). It expires 5 minutes after
  its last update (`kPairingSessionTtl`, `src/nvhttp.cpp:918`; `session_expired`,
  `1732-1737`) and is dropped by the next `/pair` call, or the next listing or PIN
  submission in the Web UI (`prune_expired_pairings_locked`, `1894-1912`; callers
  at `2218`, `2370`, `2412`). Restarting Borealis does not clear it as long as
  Borealis keeps its `uniqueid`; restarting LuminalShine does, because the map is
  not persisted.
- The automatic restart of Borealis never runs on that host, because 409 maps to
  "No restart" (the table above). The user waits up to 5 minutes, or restarts
  LuminalShine, before pairing again.

The decision on this finding is in the roadmap, under Sprint 19 (SecOps).

## 5. Client identity and per-client host settings

### 5.1 Identity comes from the certificate

- The host resolves the calling client from the verified TLS client certificate
  of the HTTPS connection (`src/nvhttp.cpp:1233-1331`,
  `src/nvhttp.cpp:1419-1441`). A client's stored identity is a generated UUID
  attached to its certificate at pairing time (`src/nvhttp.cpp:1209-1231`).
- The `uniqueid` parameter is deliberately ignored for identity. The source
  documents why: Moonlight sends the same fixed value (`0123456789ABCDEF`) for
  everyone (`src/nvhttp.cpp:1423-1431`).
- A client whose certificate cannot be resolved is not refused. The session
  continues with shared virtual-display allocation and without per-client
  settings (`src/nvhttp.cpp:1433-1440`,
  `src/nvhttp.cpp:455-466`).
- Consequence: the client certificate must be stable for the life of the
  pairing, and every request that needs a per-client behaviour must go over
  HTTPS with that certificate.

### 5.2 Per-client settings

Each paired client has these host-side settings (`src/nvhttp.cpp:872-885`). They
are changed only on the host, in the Web UI or with `POST /api/clients/update`
(`src/confighttp.cpp:2010-2115`, section 17). The client cannot set them.

| Setting | Effect | Reference |
| --- | --- | --- |
| `name` | The paired name; also the virtual display label | `src/nvhttp.cpp:2434`, `src/nvhttp.cpp:535-549` |
| `display_mode` (`WxHxFPS`) | Overrides the client's requested mode; disables RTSP reconciliation | `src/nvhttp.cpp:1509-1521`, `src/requested_display_mode.h:16-24` |
| `always_use_virtual_display` | Forces a virtual display for this client; clears `output_name_override` | `src/nvhttp.cpp:1523-1525`, `src/nvhttp.cpp:3946` |
| `output_name_override` | Capture a named output | `src/nvhttp.cpp:1525-1527` |
| `virtual_display_mode` (`disabled`, `per_client`, `shared`) | Overrides the host virtual display mode | `src/nvhttp.cpp:1528-1533`, `src/config.h:25-29` |
| `virtual_display_layout` (`exclusive`, `extended`, `extended_primary`, `extended_isolated`, `extended_primary_isolated`) | Display layout while streaming | `src/nvhttp.cpp:1534-1538`, `src/config.h:32-37` |
| `hdr_profile` (ICC name) | Applied to the virtual display or physical output | `src/nvhttp.cpp:1538-1540`, `src/nvhttp.cpp:3034-3038`, `3407` |
| `prefer_10bit_sdr` | Per-client override of the host setting (section 9.2) | `src/rtsp.cpp:1636-1637` |
| `config_overrides` | Per-client overrides of an allow-listed set of stream, input and PyroWave settings | `src/config.cpp:2113-2170`, applied at `src/nvhttp.cpp:2896-2932` |

`POST /api/clients/update` is a partial replace. When the body contains at least
one extended field (`name`, `display_mode`, `output_name_override`,
`always_use_virtual_display`, `virtual_display_mode`, `virtual_display_layout`,
`config_overrides` or `prefer_10bit_sdr`), the fields `name`, `display_mode`,
`output_name_override`, `always_use_virtual_display`, `virtual_display_mode`,
`virtual_display_layout` and `prefer_10bit_sdr` are reset to empty, false or
unset when they are omitted, and an omitted name becomes empty
(`src/confighttp.cpp:2038-2059`, `src/confighttp.cpp:2092`,
`src/nvhttp.cpp:3943-3949`). `config_overrides` and `hdr_profile` are kept when
omitted (`src/confighttp.cpp:2029-2036`, `src/confighttp.cpp:2061-2064`,
`src/nvhttp.cpp:3950-3955`). A body with no extended field changes only
`hdr_profile`, and clears it when `hdr_profile` is also absent
(`src/confighttp.cpp:2038-2052`). A companion that changes one setting must read
the client with `GET /api/clients/list` and send the complete set of the
resettable fields back.

## 6. `/serverinfo` fields

| Field | Value | Reference |
| --- | --- | --- |
| `hostname` | Configured host name | `src/nvhttp.cpp:2509` |
| `appversion` | `7.1.431.-1` | `src/nvhttp.cpp:2511` |
| `GfeVersion` | `3.23.0.74` | `src/nvhttp.cpp:2512` |
| `uniqueid` | Host unique id | `src/nvhttp.cpp:2513` |
| `HttpsPort`, `ExternalPort` | HTTPS and HTTP ports | `src/nvhttp.cpp:2514-2515` |
| `mac` | MAC over HTTPS, placeholder over HTTP | `src/nvhttp.cpp:2518-2523` |
| `LocalIP` | Local address, `127.0.0.1` over IPv6 | `src/nvhttp.cpp:2525-2538` |
| `MaxLumaPixelsHEVC` | `1869449984` when HEVC is active, otherwise `0` | `src/nvhttp.cpp:2540` |
| `ServerCodecModeSupport` | Bit field (section 9) | `src/nvhttp.cpp:2542-2576` |
| `PairStatus` | See section 3 | `src/nvhttp.cpp:2494-2501` |
| `currentgame` | Running app id, `0` if none | `src/nvhttp.cpp:2578` |
| `state` | `SUNSHINE_SERVER_BUSY` or `SUNSHINE_SERVER_FREE` | `src/nvhttp.cpp:2581` |

Absent, in contrast with Apollo-derived hosts: `Permission`, `ServerCommand` and
`VirtualDisplayCapable` (searched `src/nvhttp.cpp`: no occurrences).
`ExternalIP` is not sent either; Moonlight Android treats it as optional
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:394`).
`VirtualDisplayDriverReady` appears only in the `/launch` and `/resume`
responses (`src/nvhttp.cpp:3144-3148`, `src/nvhttp.cpp:3482-3486`).

## 7. Launch and resume

### 7.1 Parameters the host reads

`/launch` requires `rikey`, `rikeyid`, `localAudioPlayMode` and `appid`
(`src/nvhttp.cpp:2797-2803`). `/resume` requires `rikey` and `rikeyid` only and
treats `localAudioPlayMode` as optional; when it is present and no session is
active the host updates its stored value (`src/nvhttp.cpp:3250-3258`,
`src/nvhttp.cpp:3295-3301`).

The host's session builder reads these query parameters and no others
(`src/nvhttp.cpp:1389-1660`):

| Parameter | Use | Reference |
| --- | --- | --- |
| `rikey`, `rikeyid` | AES-GCM key material and IV prefix; a key shorter than 16 bytes is zero-padded | `src/nvhttp.cpp:1448-1462`, `src/nvhttp.cpp:1650-1655` |
| `mode` | `WxHxFPS`; FPS may be fractional or in millihertz (60000 becomes 60); default `0x0x0` | `src/nvhttp.cpp:1464-1507` |
| `appid` | App to launch | `src/nvhttp.cpp:1543` |
| `clientName` | Only used as a fallback device name; the paired name wins | `src/nvhttp.cpp:1443-1446` |
| `sops` | Optimal settings flag | `src/nvhttp.cpp:1614` |
| `surroundAudioInfo`, `surroundParams`, `continuousAudio` | Audio layout (section 14) | `src/nvhttp.cpp:1615-1617` |
| `gcmap` | Gamepad mask | `src/nvhttp.cpp:1618` |
| `hdrMode` | HDR requested (`1`) | `src/nvhttp.cpp:1619` |
| `corever` | `1` or greater selects encrypted RTSP (`rtspenc://`) | `src/nvhttp.cpp:1636-1645` |
| `localAudioPlayMode` | Play audio on the host as well | `src/nvhttp.cpp:2822` |
| `uniqueid` | Logged and stored; not used for identity | `src/nvhttp.cpp:1542` |

Parameters sent by Apollo/Artemis clients that the host does not read include
`virtualDisplay`, `scaleFactor`, `appuuid` and `gcpersist`
(`artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:878-892`;
none of them occurs in `src/nvhttp.cpp`).
`hdrMode` can be overridden on the host by the `dd_hdr_request_override`
setting (`auto`, `force_on`, `force_off`), which replaces the client's request
(`src/nvhttp.cpp:1620-1634`, `src/config.cpp:473-485`).

### 7.2 Response

On success, status 200 with `sessionUrl0` (an `rtsp://` or `rtspenc://` URL),
`gamesession` 1 and `VirtualDisplayDriverReady` (`src/nvhttp.cpp:3133-3148`);
for resume the equivalent is `resume` 1 (`src/nvhttp.cpp:3471-3486`).

### 7.3 Per-client virtual display

- The host setting `virtual_display_mode` defaults to `per_client`
  (`src/config.cpp:802`, `src/config.cpp:712-721`; documented as a default in
  `docs/configuration.md:1142-1160`). The default therefore creates a virtual
  display (LuminalVGD) for every launch, unless an app or per-client output
  override, or `virtual_display_mode=disabled`, applies
  (`src/nvhttp.cpp:275-278`, `292-294`, `1563`).
- In `per_client` mode the monitor is keyed by the client's certificate UUID, so
  a returning client reclaims its connector and per-monitor Windows settings, and
  it is labelled with the paired name (`src/nvhttp.cpp:455-466`,
  `src/nvhttp.cpp:480-513`, `src/nvhttp.cpp:535-549`). When the client identity
  cannot be resolved or the mode is `shared`, a shared display is used.
- The monitor is created at the mode the client requested in `/launch`
  (width, height, fps) and in HDR when `hdrMode` is 1
  (`src/nvhttp.cpp:515-533`, `src/nvhttp.cpp:584-596`). The creation code lives
  in `prepare_virtual_display_for_session`, which starts at
  `src/nvhttp.cpp:247`.
- If another session is already running, no second virtual display is created;
  an existing one is reused if it can be found
  (`src/nvhttp.cpp:380-398`).
- For `/resume`, display changes are only made when
  `config::video.dd.config_revert_on_disconnect` is true; otherwise the existing
  virtual display is preserved, or recreated on demand if it is gone
  (`src/nvhttp.cpp:3295-3296`, `src/nvhttp.cpp:286-324`).
- **Observed, not verified in the source:** while the host builds a per-client
  virtual display for one client's `/launch` (about 14 s on the Sprint 2 test
  host), it answers no other HTTPS request, because the server has a single io
  thread (upstream issue `NortheBridge/luminalshine#202`; the proposed fix,
  PR #203, was not merged as of 2026-10-17). A second client's `GET /serverinfo` then runs into the
  client's 7 s read timeout. Borealis retries a `/serverinfo` read timeout at
  stream start, up to 3 attempts within about 25 s and until the launch is
  cancelled, and then reports the timeout as before. Only `/serverinfo` is
  retried: `/launch`, `/resume`, `/cancel`, `/quit` and pairing are not
  idempotent. A client that polls `/serverinfo` in the background can see the
  host as briefly offline during that window.
  **Changed in 26.10.0-beta.1** (#203, which fixes #202; verified in source at
  `46f6936d`): PR #203 was merged on 2026-10-20 and ships in this release. The
  `/launch`, `/resume` and `/cancel` routes no longer run their handler on the
  HTTPS I/O thread. Each one posts it, through `run_on_launch_worker`
  (`src/nvhttp.cpp:3649-3692`), to `launch_worker`, one `util::serial_executor_t`
  created in `nvhttp::start` (`src/nvhttp.cpp:3728-3730`, routes at `3829-3844`;
  `src/serial_executor.h:25-109`). That executor has one thread, named
  `nvhttp::launch`, which runs the posted handlers one at a time in posting order
  (`src/serial_executor.h:52-62`, `85-101`). The three handlers stay serialised
  with each other, as they were on the I/O thread: a `/cancel` waits behind a
  `/launch` in progress (`src/nvhttp.cpp:3839-3841`). `/serverinfo`, `/pair`,
  `/applist`, `/appasset` and the TLS handshakes stay on the I/O thread, which a
  long `/launch` no longer holds (#202). `/serverinfo` also no longer waits for the
  lock a launch holds while it starts the app: it reads the running app with
  `proc::proc.running_nowait()` (`src/nvhttp.cpp:2607-2609`,
  `src/process.cpp:1793-1803`), which returns the id the launch published early,
  so a host that is still starting its app already answers
  `SUNSHINE_SERVER_BUSY`. While the host shuts down the worker drops queued
  requests without an answer (their connections are closed,
  `src/nvhttp.cpp:3898-3904`); a request that arrives in the gap gets 503 with the
  message `The host is shutting down.` (`src/nvhttp.cpp:3681-3690`). The host
  source of this checkout does not show the single I/O thread itself, because it
  is a Simple-Web-Server default and that submodule is not checked out; the
  comment at `src/nvhttp.cpp:3650-3652` states it. The worker serialises the three
  handlers with each other only: the RTSP setup that follows a handler's response
  is not part of its task, so the source does not show whether the race of #205
  is closed. Borealis keeps the 3-attempt `/serverinfo` retry and the 3 s wait
  before `/resume` for older hosts and for #205, which is still open, and keeps
  the handling of the not-found cases for 26.09.1. In the device round of
  2026-10-21 neither was triggered on 26.10.0-beta.1 (no retry, no wait and no
  503, with a second device opening the host during a launch); whether the race
  of #205 is still reachable is not tested.
- **Observed, not verified in the source (Sprint 3):** when a second client's
  `/resume` is accepted while the first client's `/launch` is still setting up,
  the host logs `Failed to verify RTSP message tag` and the second client's RTSP
  `OPTIONS` fails (`Failed to decrypt RTSP response`, error -1). The first
  client streams normally. Reported upstream as `NortheBridge/luminalshine#205`.
  Borealis waits about 3 s before `/resume` when the host was slow to answer
  `/serverinfo`; when RTSP still fails with error -1 within 5 s of an accepted
  same-app `/resume` on a non-GFE host, it restarts the whole start once, never
  quits another device's app, and on a second failure shows an explanation
  instead of the firewall paragraph.
- **Observed (Sprint 3):** the host process crashed once right after the virtual
  display cleanup of an ended session (`unhandled exception 0x20474343` in
  `KERNELBASE.dll`) and restarted about 9 s later. Requests in that window get a
  connection reset. Reported upstream as `NortheBridge/luminalshine#204`.
- **Client behaviour (Sprint 5):** the Borealis error screen classifies three
  host states: host busy (the retries of the start ran out, or the host
  reported a launch collision), host offline, and certificate changed. The
  client requests a keyframe (`LiRequestIdrFrame`, through
  `MoonBridge.requestIdrFrame`) when the user chooses Refresh the picture in
  the in-game menu. Free text typed on the on-screen keyboard is sent to the
  host as real UTF-8 bytes, so emoji and accented letters arrive intact.

### 7.4 Mode reconciliation between `/launch` and RTSP

The monitor is built at the `/launch` mode, but the stream resolution and frame
rate that the client announces over RTSP are authoritative. After ANNOUNCE has
been parsed and before capture starts, the host compares the two
(`src/rtsp.cpp:1778-1790`):

- Reconciliation applies when the session uses a virtual display, no per-client
  `display_mode` override is set, the RTSP mode is valid and the `/launch` mode
  differs (`src/requested_display_mode.h:16-24`).
- The RTSP frame rate is `round(clientRefreshRateX100 / 100)` when
  `clientRefreshRateX100` is greater than zero, otherwise `maxFPS`
  (`src/rtsp.cpp:1783-1785`). The host ignores `clientRefreshRateX100`
  when it disagrees with `maxFPS` after rounding, and then uses `maxFPS`
  (`src/rtsp.cpp:1561-1574`).
- When the modes differ, the host removes the monitor, recreates it at the RTSP
  mode and re-applies the display configuration. It logs `Requested display mode
  changed between HTTP launch ... rebuilding LuminalVGD once before capture
  admission` as a warning. When they agree it logs `Requested display mode
  reconciled: HTTP and RTSP agree` (`src/rtsp.cpp:65-135`, log lines at 80 and
  87-89).
- If the rebuild or the apply fails, ANNOUNCE is answered with RTSP 503
  `Virtual display mode reconciliation failed` (`src/rtsp.cpp:1786-1789`).

The rebuild adds visible latency (the display is removed and re-created while
the client waits) and is a failure point. Borealis should send identical native
width, height and frame rate in `/launch` `mode=` and in the ANNOUNCE attributes
`x-nv-video[0].clientViewportWd`, `clientViewportHt` and `maxFPS`.

Moonlight Android 12.2 can produce exactly this mismatch. It builds the
`/launch` mode from the user's selected frame rate
(`setLaunchRefreshRate(prefConfig.fps)`,
`borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:488`, and the
`fps` of the `mode=` parameter in `launchApp`,
`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:816`)
but streams at `chosenFrameRate` (`setRefreshRate(chosenFrameRate)`,
`borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:489`).
With frame pacing "cap FPS", a selected rate that is equal to or at most 3 Hz
above the display refresh rate, and a refresh rate above 49 Hz,
`chosenFrameRate` becomes the rounded refresh rate minus 1, for example 59 for
a 60 Hz selection
(`borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:467-483`).
A host with the default per-client virtual display then rebuilds the monitor
on every such launch. This is confirmed: the Android client passes
`getRefreshRate()` (the `chosenFrameRate`) to the native library
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:536`),
and upstream moonlight-common-c writes `StreamConfig.fps` into
`x-nv-video[0].maxFPS` (`borealis:core/third_party/moonlight-common-c/src/SdpGenerator.c:320-321`, upstream 874ac95; this is
not the Aurora fork).

Borealis no longer produces this mismatch against a host that is not GFE:
`launchApp` takes the `mode=` frame rate from `LaunchFrameRate.forLaunch`, which
returns the stream frame rate for such a host
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:795-797`).
**Observed** in the Sprint 2 round (Streamer), with frame pacing "cap FPS": the
`/launch` mode was `1280x720x59` and the host logged `HTTP and RTSP agree` (row 3.7 of
[device-validation.md section 7](../development/device-validation.md#7-results-sprint-2-2026-10-14)).

When a per-client `display_mode` override is configured on the host, the host's
mode wins and no reconciliation happens. **Unverified:** what the stream looks
like when the announced RTSP mode then differs from the overridden monitor mode
(for example whether the frame is scaled).

### 7.5 Launch and resume status codes

Status is carried in the XML `status_code` attribute of the response (HTTP
status is 200 in all of these); Moonlight raises it as `HostHttpResponseException`
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:294-308`).
The "recommended client behaviour" column is a Borealis recommendation, not a
host guarantee. `ping_timeout` is 10 s by default (`src/config.cpp:844`).

**Reference revision, 26.10.1** (the references moved; the table itself is unchanged): every
`src/` reference of this table and of its notes is a line at `dba21a9e` (the `26.10.1`
tag). The table was taken at `5afdbdc6`. Its `src/nvhttp.cpp` references trailed 26.10.1 by 43
lines (the 400 `An app is already running on this host` is at `src/nvhttp.cpp:2859-2860`
now, and the 409 `Another client launch is already pending` at `2874-2875`) and its
`src/process.cpp` references by 1. `src/rtsp.cpp`, `src/config.cpp` and
`src/platform/windows/display_helper_integration.cpp` are the same file at both
revisions, so their references did not move. The codes and messages are those of
26.10.0-beta.1: `src/nvhttp.cpp` is the same file at `46f6936d` and at `dba21a9e`.

| Code | Message | Where | Meaning | Recommended client behaviour |
| --- | --- | --- | --- | --- |
| 400 | `Missing a required launch parameter` | launch `src/nvhttp.cpp:2848` | A required query parameter is missing | No retry; client bug |
| 400 | `Missing a required resume parameter` | resume `src/nvhttp.cpp:3299` | As above | No retry; client bug |
| 400 | `An app is already running on this host` | launch `src/nvhttp.cpp:2859` | `/launch` was called while an app runs | No retry of `/launch`. Moonlight's `startApp` already resumes or quits first when `/serverinfo` shows a running app (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:395-430`; Borealis turned the quit into a user choice, section 7.6); the 400 arrives when another device's launch started after that read. Borealis then re-reads `/serverinfo` for up to 3 s: `currentgame` 0 launches again once, otherwise the user chooses Join (`/resume`), Quit and relaunch (`/cancel`, then `/launch`) or Cancel. `/cancel` is never called without that choice (section 7.6) |
| 409 | `Another client launch is already pending; retry the connection.` | launch `src/nvhttp.cpp:2874-2875`, `2896-2897`, `3200-3201`; resume `3308-3309`, `3324-3325`, `3538-3539` | A previous `/launch` or `/resume` has not been claimed by an RTSP connection yet; the pending launch expires after `ping_timeout` (`src/rtsp.cpp:606`) | Retry with backoff for up to about 12 s in total (for example 1, 2, 3, 5 s), then show the host message. Do not call `/cancel`: it would also cancel another client's pending launch (`src/rtsp.cpp:1132-1136`) |
| 503 | `The previous stream is still restoring the display; retry the connection.` | launch `src/nvhttp.cpp:2888-2889`; resume `3317-3318` | The host waited 8 s (`acquire_launch(8s)`, `src/nvhttp.cpp:2884`, `3313`) for the previous session's cleanup and gave up | Retry, 2 to 3 attempts with 2 to 5 s delays (each attempt can itself take 8 s), then show the message |
| 503 | `GPU/display stack is not ready; retry after recovery or reboot the host.` | launch `src/nvhttp.cpp:3011-3012`; resume `3374-3375` | The D3D11 health probe failed | Limited retries (for example 2 with 5 s delay), then tell the user to restart the host or its GPU driver |
| 503 | `Failed to prepare the client virtual display.` | launch `src/nvhttp.cpp:3049`; resume `3425` | Building the display request failed for a virtual-display session | Limited retries (1 to 2), then show the message |
| 503 | `Windows did not activate the virtual display.`, `Windows did not apply the requested virtual-display mode.`, `The virtual display activated, but its capture ring did not begin publishing frames.`, `The Windows display helper was unavailable.`, or `Display configuration failed.` | launch `src/nvhttp.cpp:3062-3064`; resume `3436-3438`; strings `src/platform/windows/display_helper_integration.cpp:1802-1816` | Mandatory virtual-display apply or readiness check failed | Limited retries (1 to 2); on repeat, show the message; a retry with a different mode is a reasonable manual action |
| 503 | `Failed to initialize video capture/encoding. Is a display connected and turned on?` | launch `src/nvhttp.cpp:3127-3128`; resume `3496-3497` | The pre-stream encoder probe failed | Limited retries (1 to 2), then show the message |
| 503 | `The host is shutting down.` | launch and resume, `src/nvhttp.cpp:3681-3690` (new in 26.10.0-beta.1) | The request reached the launch worker after the host began to shut down | No retry; show the message. Borealis today treats an unknown 503 as a capture or display failure and retries once after 3 s (`core/src/launch.cpp:22-44`); that one retry is harmless |
| 503 | `No running app to resume` | resume `src/nvhttp.cpp:3287-3288` | `/resume` with no app running | Do not retry; re-read `/serverinfo` and call `/launch` |
| 403 | `Encryption is mandatory for this host but unsupported by the client` | launch `src/nvhttp.cpp:3139-3140`; resume `3507-3508` | The host's LAN or WAN encryption policy is mandatory and the client did not send `corever` of 1 or more | No retry; this is a client defect or an outdated protocol library |
| 404 | `Failed to start the specified application` | launch `src/nvhttp.cpp:3154-3156` (code is the return value of `proc::execute`) | Unknown `appid` returns 404 (`src/process.cpp:1105-1110`) | No retry; refresh `/applist` |
| -1 | `Failed to start the specified application` | launch `src/nvhttp.cpp:3154-3156` | The app launch failed, for example the command could not be run (`src/process.cpp:1673-1676`) | No automatic retry; show the message |

Notes on the table:

- Failure bodies carry `resume` 0 and/or `gamesession` 0, depending on the path
  (`src/nvhttp.cpp:2847`, `2858`, `2876`, `3199`, `3509`). With a non-200 status
  Moonlight throws before it reads either field.
- On every launch or resume the host first checks for a pending launch, then
  waits for the display lease, then re-checks (`src/nvhttp.cpp:2871-2899`,
  `src/nvhttp.cpp:3305-3326`). A client therefore sees 409 quickly but 503 after
  up to 8 s.
- On these failure paths the handler's fail guards revert a partially prepared
  display and release the display lease before the response is written, so a
  retry starts from a clean state (`src/nvhttp.cpp:2788-2839`).
- `/launch` takes a deferred path when the host runs as a service without an
  interactive session: the app launch is deferred until sign-in and `execute`
  returns 0 (`src/process.cpp:1383-1387`). **Unverified:** what the client
  observes in that case.
- The Moonlight Android 12.2 client does not retry. It shows the message and
  fails. Borealis added `launchWithRetry`, which runs the retry policy and which
  `startApp` calls for `/resume` and, through `launchNotRunningApp`, for `/launch`
  (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:171-179`).
  It also changed the catch blocks of the start thread, so a failure goes to the
  error screen, which never shows the exception text
  (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:492-510`).

### 7.6 Cancel

`/cancel` always answers `cancel` 1 and status 200, terminates every RTSP
session including a pending launch, terminates the running app and tears down
the virtual display unless a deferred launch must be preserved
(`src/nvhttp.cpp:3508-3586`, `src/rtsp.cpp:1132-1136`). If a successor
`/launch` wins the lifecycle race the cancel cleanup is skipped
(`src/nvhttp.cpp:3540-3550`). Moonlight 12.2 answered a `currentgame` for a
different app by cancelling then launching
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:395-399`,
`:437-458`; the `else` branch at `:406-407` called `quitAndLaunch` directly in
12.2). Borealis does not: it asks the user first, and never quits another
device's app without the user's explicit choice, even when the Library has not
seen that app (`NvConnection.resolveConflict`, section 7.5).

`/cancel` ends the app of any client, not only the caller's: every Moonlight-family
client sends the same `uniqueid`, and the host does not check an owner. `/resume`
has no owner check either (`src/nvhttp.cpp:3183-3360`). A client must therefore
never call `/cancel` or `/resume` for an app it did not start without the user's
explicit choice.

## 8. RTSP session setup

The client opens an RTSP connection to the port in `sessionUrl0` within
`ping_timeout` of the `/launch` answer, or the pending launch is discarded
(`src/rtsp.cpp:602-607`). RTSP methods are `OPTIONS`, `DESCRIBE`, `SETUP`,
`ANNOUNCE`, `PLAY` (`src/rtsp.cpp:1870-1874`).

### 8.1 DESCRIBE (host to client)

The SDP the host returns (`src/rtsp.cpp:1265-1348`) contains:

| Attribute | Meaning | Reference |
| --- | --- | --- |
| `a=x-ss-general.featureFlags:<n>` | Host input capability bits (section 13) | `src/rtsp.cpp:1277` |
| `a=x-ss-general.encryptionSupported:<n>` | Always `SS_ENC_CONTROL_V2` and `SS_ENC_AUDIO`, plus `SS_ENC_VIDEO` unless the endpoint's mode is never | `src/rtsp.cpp:1280-1298` |
| `a=x-ss-general.encryptionRequested:<n>` | `SS_ENC_CONTROL_V2`, plus video and audio when the mode is mandatory | `src/rtsp.cpp:1281-1298` |
| `a=x-nv-video[0].refPicInvalidation:1` | Only if the encoder probe found reference invalidation support | `src/rtsp.cpp:1300-1302` |
| `sprop-parameter-sets=AAAAAU` | HEVC is available | `src/rtsp.cpp:1304-1306` |
| `a=rtpmap:98 AV1/90000` | AV1 is available | `src/rtsp.cpp:1308-1310` |
| `a=rtpmap:99 PYROWAVE/90000` | PyroWave is enabled and available | `src/rtsp.cpp:1312-1314` |
| `a=fmtp:97 surround-params=...` | Opus layouts for every audio configuration | `src/rtsp.cpp:1315-1346` |

### 8.2 SETUP

SETUP answers `Session: DEADBEEFCAFE;timeout = 90`, `Transport: server_port=<port>`
and one identifier: `X-SS-Connect-Data` for the control stream or
`X-SS-Ping-Payload` for audio and video (`src/rtsp.cpp:1351-1407`). The client
must echo these values in the ENet connect data and in its initial UDP ping to
the video and audio ports.

### 8.3 ANNOUNCE (client to host)

The host reads the ANNOUNCE attributes in `src/rtsp.cpp:1410-1760`. The video
attributes it uses are `x-nv-video[0].clientViewportWd`, `clientViewportHt`,
`maxFPS`, `clientRefreshRateX100`, `packetSize`, `videoEncoderSlicesPerFrame`,
`maxNumReferenceFrames`, `encoderCscMode`, `dynamicRangeMode`,
`x-nv-vqos[0].bitStreamFormat`, `x-nv-vqos[0].bw.maximumBitrateKbps`,
`x-ss-video[0].chromaSamplingType`, `x-ss-video[0].intraRefresh` and
`x-ml-video.configuredBitrateKbps` (`src/rtsp.cpp:1523-1550`, `1576`).

Attributes without a default are mandatory; a missing one is answered with RTSP
400 (`src/rtsp.cpp:1519-1550`, `1577-1578`): `x-nv-audio.surround.numChannels`,
`x-nv-audio.surround.channelMask`, `x-nv-audio.surround.AudioQuality`,
`x-nv-video[0].packetSize`, `clientViewportWd`, `clientViewportHt`, `maxFPS`,
`x-nv-vqos[0].bw.maximumBitrateKbps`, `x-nv-video[0].videoEncoderSlicesPerFrame`
and `x-nv-video[0].maxNumReferenceFrames`. All other attributes the host reads
have defaults (`src/rtsp.cpp:1496-1510`).

Upstream moonlight-common-c also announces `x-ml-general.featureFlags` with a
frame-FEC-status bit to Sunshine hosts (`borealis:core/third_party/moonlight-common-c/src/SdpGenerator.c:270-274`,
upstream 874ac95) and, when video encryption is enabled, subtracts the 32-byte
`ENC_VIDEO_HEADER` from the announced `packetSize` (`borealis:core/third_party/moonlight-common-c/src/SdpGenerator.c:323-330`,
`borealis:core/third_party/moonlight-common-c/src/Video.h:15-19`).

Responses to ANNOUNCE:

| RTSP status | Condition | Reference |
| --- | --- | --- |
| 400 `BAD REQUEST` | A required attribute is missing; invalid `packetSize` (below 64 or above 65535); HEVC or AV1 requested but disabled; unknown `bitStreamFormat`; PyroWave malformed attribute, duplicate attribute, or rejected profile or mode | `src/rtsp.cpp:1578`, `1591-1593`, `1675-1677`, `1682-1684`, `1689-1691`, `1717`, `1727`, `1742` |
| 403 `Forbidden` | Mandatory encryption policy and the client did not enable both video and audio encryption | `src/rtsp.cpp:1746-1753` |
| 410 `Gone` | The pending launch was cancelled or expired while the client was connecting | `src/rtsp.cpp:1435`, `1765`, `1816` |
| 500 `Internal Server Error` | `stream::session::start` failed | `src/rtsp.cpp:1801-1806` |
| 503 `Virtual display mode reconciliation failed` | Section 7.4 | `src/rtsp.cpp:1786-1789` |

A client that retries ANNOUNCE after a terminal rejection must first start a new
`/launch`: a rejection retires the pending launch immediately
(`src/rtsp.cpp:1410-1422`).

## 9. Codec advertisement

`ServerCodecModeSupport` is a bit field built from the encoder probe and the
configuration (`src/nvhttp.cpp:2542-2576`):

- H.264 is always set. H.264 4:4:4 requires `yuv444_streaming` and a passed
  probe.
- HEVC is set when the HEVC mode is 2 or more; HEVC Main10 when it is 3 or more.
- AV1 Main 8-bit is set when the AV1 mode is 2 or more; AV1 Main10 when it is 3
  or more.
- 4:4:4 variants of HEVC and AV1 are set only when `yuv444_streaming` is true
  (default true, `src/config.cpp:748`) and the encoder probe confirmed them for
  that codec.
- PyroWave bits are added by `pyrowave::server_codec_mask` when PyroWave is
  enabled and available (`src/nvhttp.cpp:2573-2575`); see
  [pyrowave.md](pyrowave.md) for the bit values.
- AV1 needs an AV1 encoder on the host. **Observed** on the Sprint 2 and 3 test
  host (an RTX 3080 Ti): the host logs `Encoder [av1_nvenc] is not supported on this GPU`
  and advertises no AV1. Moonlight 12.2, and Borealis with it, selects an AV1
  decoder only when the user forces AV1.

**Changed in 26.10.0** (#210, which fixes #209; verified in source at `dba21a9e`; the
code that builds `ServerCodecModeSupport`, `src/nvhttp.cpp:2563-2605`, is unchanged): a
codec that fails the encoder probe no longer stops sessions that use another codec.
The commit message of the fix (`5446f244`) gives the cause: the probe set every
capability bit of a codec before it validated the codec, and a codec that failed kept
them, so the encoder snapshot that the isolated video worker receives carried a 4:4:4
bit on an unsupported codec and was refused, and a failed AV1 validation stopped every
session, H.264 and HEVC included. The worker still refuses an inconsistent snapshot
(`src/video.cpp:1564-1572`, `src/platform/windows/video_worker.cpp:645-650`). What
changed is that the probe now clears all the capabilities of a codec that did not pass
(`src/video.cpp:4175-4178`). Second, the probe cache key now includes `hevc_mode` and
`av1_mode` (`src/video.cpp:289-293`, used at `4369`), so changing either setting probes
again, without a host restart; before, the key came from the detected GPUs only. Third, the
host now logs why it refuses an encoder snapshot or a worker start message
(`src/video.cpp:1522-1570`, `src/platform/windows/video_worker.cpp:588-650`). The
upstream report came from an RTX 4080 host that upstream could not reproduce, so the
release notes say the fix is covered by snapshot tests and not by a streaming test on
that hardware. The Borealis test host's GPU has no AV1 encoder and streamed H.264 and
HEVC on 26.10.0-beta.1, so the report was not seen here.

The legacy bit values come from upstream moonlight-common-c at 874ac95 (not the
Aurora fork), which the Moonlight Android client uses: `SCM_H264` `0x1`,
`SCM_HEVC` `0x100`, `SCM_HEVC_MAIN10` `0x200`, `SCM_AV1_MAIN8` `0x10000` and
`SCM_AV1_MAIN10` `0x20000` (`borealis:core/third_party/moonlight-common-c/src/Limelight.h:506-510`). The Android client tests
`0x20200` (`0x200` or `0x20000`) for HDR
(`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:333`)
and `0x200` for resolutions above 4K (`:343-344`), a check that applies only to
a GFE host (`context.isNvidiaServerSoftware`) and never to LuminalShine. The
4:4:4 bits (`SCM_H264_HIGH8_444` and following) are defined
immediately after them in the same header; they were not needed here.

The client selects a format with `x-nv-vqos[0].bitStreamFormat` (0 H.264, 1 HEVC,
2 AV1, 3 PyroWave per `src/rtsp.cpp:1673-1744`),
`x-nv-video[0].dynamicRangeMode` (0 SDR, 1 HDR) and
`x-ss-video[0].chromaSamplingType` (0 4:2:0, 1 4:4:4)
(`src/rtsp.cpp:1547-1549`).

### 9.1 4:4:4 downgrades

- At ANNOUNCE, a legacy-codec request for 4:4:4 while `yuv444_streaming` is
  false is silently changed to 4:2:0 and the stream starts
  (`src/rtsp.cpp:1552-1559`). PyroWave is excluded from this downgrade.
- Mid-session, if the 4:4:4 encoder fails to build and the display is not in a
  recovery window, the video thread silently falls back to 4:2:0 for the rest of
  the session (`src/video.cpp:2758-2806`, `src/yuv444_fallback.h:1-55`).
  The only consumer of the downgrade event outside the video thread is the
  session-statistics update (`src/stream.cpp:1572-1584`; the other
  references to the event are in the worker IPC in
  `src/platform/windows/video_worker.cpp:846`, `:991-992`, `:1241-1242`). No
  control message is sent to the client. The comment in
  `src/yuv444_fallback.h:23-24` says the client is told; the code does not do
  that.
- A PyroWave 4:4:4 session is never downgraded; the encoder build fails instead
  (`src/video.cpp:2765-2774`).

A client that requests 4:4:4 must therefore detect a chroma change from the
bitstream (new SPS for HEVC and H.264, new sequence header for AV1) at the next
IDR instead of trusting the
negotiation. The Moonlight Android client has no 4:4:4 support today.

### 9.2 `prefer_10bit_sdr`

If the host setting `prefer_10bit_sdr` (default false, `src/config.cpp:747`) or
the per-client override is true, an SDR request (`dynamicRangeMode` 0, no
`hdrMode`) for HEVC or AV1 is silently turned into a Main10 SDR session when the
host's HEVC or AV1 mode is 3 or more (`src/rtsp.cpp:1636-1646`). The client
asked for 8-bit SDR and receives 10-bit SDR. **Unverified:** whether Moonlight
Android's MediaCodec path accepts this on a device without Main10 support; the
host decides this with no client input beyond the codec choice.

### 9.3 HDR

- `hdrMode=1` on `/launch` creates an HDR virtual display
  (`src/nvhttp.cpp:1619`, `src/nvhttp.cpp:584-596`). The host can force HDR on
  or off regardless of the request with `dd_hdr_request_override`
  (`src/nvhttp.cpp:1620-1634`).
- The host sends control message 0x010e (HDR mode: an enabled byte followed by
  the Sunshine HDR metadata block) when HDR is on or off
  (`src/stream.cpp:1221-1246`, structure at `src/stream.cpp:292-297`). The
  message is held back until the control peer has connected
  (`src/stream.cpp:1222-1226`, `src/stream.cpp:1566-1572`).
- A client should treat 0x010e and the bitstream as the truth about HDR and not
  assume the stream is HDR because it asked for it.
- `/applist` reports HDR support from HEVC Main10 only (section 3).

## 10. Time to first frame

### 10.1 Slow first frame and the runt keepalive

- The host can take a long time to produce the first frame: the source says
  NVENC session creation alone can take about 7 s on some driver and GPU
  combinations (`src/stream.cpp:2780-2790`).
- To keep the client from timing out, if no real frame has been transmitted 8 s
  after the UDP peer was attached, the host sends a single 1-byte UDP datagram on
  the video port (`src/stream.cpp:2791-2806`). The comment states that
  Moonlight terminates with "no video received" if no datagram arrives within
  10 s of PLAY, and separately requires a complete frame within 10 s of the first
  datagram; sending the runt late stretches the first-frame budget to about 18 s.
- A client must discard datagrams shorter than the minimum RTP size without
  counting them as video, and must tolerate a first frame at about 18 s. A
  client that treats the runt as a corrupt packet or aborts earlier than that
  will fail on hosts that are slow to start.

### 10.2 Strict-first-frame clients

- A paired client name whose lowercase form contains `xbox` or `webos` is treated
  as a strict-first-frame client (`src/stream.cpp:2857-2868`). It is the only
  name-based special case in the host.
- For such clients the host holds the ANNOUNCE response for up to 8 s until the
  video pipeline reports ready and extends the pending-launch timer to 30 s
  (`src/rtsp.cpp:1826-1849`), adds 10 s to the video and audio initial-ping
  windows (`src/stream.cpp:2744`, `src/stream.cpp:2835`) and 18 s to the control
  stream establishment window (`src/stream.cpp:3293-3297`). The flag is set at
  session creation (`src/stream.cpp:3425`).
- The rule keys on the paired name, which the user types in the host Web UI
  (section 4). A Borealis device name must not contain `xbox` or `webos`, or the
  ANNOUNCE response is held for up to 8 s.

## 11. Video transport summary

Video is RTP over UDP with Reed-Solomon FEC in up to four FEC blocks per frame,
optional AES-GCM per packet, and an 8-byte frame header at the start of the
frame payload. PyroWave-specific layout is in [pyrowave.md](pyrowave.md).

| Field | Layout | Reference |
| --- | --- | --- |
| `headerType` | 1 byte, always `0x01` (short header) | `src/stream.cpp:1982` |
| `frame_processing_latency` | 2 bytes little endian; host processing time in units of 0.1 ms; 0 if the frame is repeated or no value is known | `src/stream.cpp:175-186`, `src/stream.cpp:1990-2005` |
| `frameType` | 1 byte: 1 normal P-frame, 2 IDR, 4 P-frame with intra-refresh blocks, 5 P-frame after reference invalidation. The host emits 1, 2 and 5 | `src/stream.cpp:187-193`, `src/stream.cpp:1983-1985` |
| `lastPayloadLen` | 2 bytes little endian; length of the last packet's payload, for codecs that cannot take zero padding such as AV1 | `src/stream.cpp:194-196`, `src/stream.cpp:1986-1989` |
| unknown | 2 bytes | `src/stream.cpp:198` |

The FEC percentage defaults to 20 (`src/config.cpp:848`). If a frame needs more
than four FEC blocks the host turns FEC off for that frame
(`src/stream.cpp:2032-2053`). The packet size is the client's `packetSize`
attribute.

## 12. Control stream

The control stream is ENet over UDP. When `x-nv-general.useReliableUdp` is 13
the stream is encrypted and unencrypted messages are dropped
(`src/stream.cpp:868-873`). Message types (`src/stream.cpp:73-90`):

| Type | Direction | Name | Behaviour | Reference |
| --- | --- | --- | --- | --- |
| 0x0305, 0x0307 | client to host | Start A, Start B | Accepted, no action | `src/stream.cpp:1255-1261` |
| 0x0301 | client to host | Invalidate reference frames | Two 64-bit frame indices; shorter payloads are dropped. Only meaningful if DESCRIBE advertised `refPicInvalidation`; for PyroWave it forces a full frame | `src/stream.cpp:1363-1382`, `src/pyrowave/backend.cpp:93` |
| 0x0201 | client to host | Loss statistics | Counted when session monitoring is on | `src/stream.cpp:1293-1311` |
| 0x0206 | client to host | Input data | Length-prefixed, AES-GCM | `src/stream.cpp:1384-1420` |
| 0x0302 | client to host | Request IDR | Raises the IDR event | `src/stream.cpp:1313-1320` |
| 0x0001 | both | Fully encrypted wrapper | Encrypted control v2 messages | `src/stream.cpp:1423-1486` |
| 0x0200 | client to host | Periodic ping | Accepted | `src/stream.cpp:1251-1253` |
| 0x0109, legacy 0x0100 | client to host | Client termination | Reason is a 32-bit big endian value (or 16-bit little endian for short payloads); the session stops | `src/stream.cpp:1263-1291` |
| 0x0109 | host to client | Host termination | Sent on shutdown with reason `0x80030023` (graceful) | `src/stream.cpp:1618-1625` |
| 0x010b | host to client | Rumble | Large and small motor values | `src/stream.cpp:1124-1140` |
| 0x010e | host to client | HDR mode | Section 9.3 | `src/stream.cpp:1221-1246` |
| 0x5500 | host to client | Trigger rumble (Sunshine extension) | Defined; no emitter in `src/platform/windows/input.cpp` (searched). **Changed in 26.10.0:** the VHF gamepad backend sends it (note below the table) | `src/stream.cpp:1141-1157` |
| 0x5501 | host to client | Set motion event (Sunshine extension) | Windows input asks for accelerometer and gyroscope at 100 Hz when it emulates a DualShock 4. **Changed in 26.10.0:** the VHF backend asks, for a DualShock 4, a DualSense or a Switch Pro Controller (note below the table) | `src/stream.cpp:1158-1172`, `src/platform/windows/input.cpp:277-278` |
| 0x5502 | host to client | Set RGB LED (Sunshine extension) | Sent when the emulated DualShock 4 light bar changes. **Changed in 26.10.0:** sent when the light colour of a PlayStation profile changes (note below the table) | `src/stream.cpp:1173-1189`, `src/platform/windows/input.cpp:385-396` |
| 0x5502 | client to host | PyroWave frame FEC status | See [pyrowave.md](pyrowave.md); the host accepts it only when the session's format is PyroWave and only with an exact 21-byte payload | `src/stream.cpp:1322-1361` |
| 0x5503 | host to client | Set adaptive triggers (Sunshine extension) | Defined; no emitter in `src/platform/windows/input.cpp` (searched). **Changed in 26.10.0:** the VHF backend sends it for a DualSense (note below the table) | `src/stream.cpp:1190-1200` |

`0x5502` is used in both directions with different payloads. A client must
discriminate by direction, not by type.

**Changed in 26.10.0** (verified in source at `dba21a9e`): the Windows host drives the
VHF gamepad driver instead of ViGEmBus (section 13), and the feedback it sends to the
client is built in `raise_feedback` (`src/platform/windows/vhf_gamepad.cpp:310-398`).
The wire encoders in `src/stream.cpp:1123-1205` are the ones the table cites; the
host raises these messages:

- Rumble (`0x010b`) when the two motor values change, and for the first value of a
  controller, zero included (`src/platform/windows/vhf_gamepad.cpp:344-354`,
  `src/platform/windows/vhf_gamepad_policy.cpp:187-212`).
- Set RGB LED (`0x5502`) when a decoded PlayStation output report carries a light
  colour that changed (`vhf_gamepad.cpp:355-362`, `vhf_gamepad_policy.cpp:294-299`).
- Set adaptive triggers (`0x5503`) when a decoded PlayStation output report carries
  trigger effects that changed; the host forwards the driver's effect modes and
  parameters verbatim, with both triggers flagged (`vhf_gamepad.cpp:364-381`,
  `vhf_gamepad_policy.cpp:301-311`, `vhf_gamepad_policy.h:116-120`). The host's
  configuration text ties this to the `dualsense` profile
  (`docs/configuration.md:360`).
- Trigger rumble (`0x5500`) when a decoded `xbox_rumble` driver event carries trigger
  motors that changed (`vhf_gamepad.cpp:383-393`, `vhf_gamepad_policy.cpp:315-333`).
  The host's comment ties the event to Xbox controllers, which have impulse triggers
  (`vhf_gamepad_policy.h:109-111`); which driver profile produces it is the driver's
  business, and the driver source is a submodule that is not checked out
  (`third-party/libvirtualgamepad`).
- Set motion event (`0x5501`, accelerometer and gyroscope at 100 Hz) when a
  DualShock 4, DualSense or Switch Pro controller is created (`vhf_gamepad.cpp:188-192`,
  `662-669`), and again by the DualShock 4 maintenance when its gyro stream goes stale
  (`vhf_gamepad.cpp:474-489`).

## 13. Input

- The host accepts the standard Sunshine input extensions: relative and absolute
  mouse, buttons, vertical and horizontal scroll, keyboard, UTF-8 text, pen,
  touch, controller arrival, controller touch, controller motion and controller
  battery (`src/input.cpp:1593`, `src/input.cpp:1640-1682`).
- Feature bits advertised in DESCRIBE `x-ss-general.featureFlags`
  (`src/platform/common.h:254-260`, `src/platform/windows/input.cpp:1811-1829`):
  `0x01` pen and touch events (set only if the Windows API
  `CreateSyntheticPointerDevice` is present and `native_pen_touch` is true,
  default true at `src/config.cpp:893`); `0x02` controller touch events (set
  unless the gamepad emulation mode is `x360`; **changed in 26.10.0**, see the item
  below).
- **Changed in 26.10.0** (verified in source at `dba21a9e`; the `src/` lines of this
  item are at that revision, and 26.10.1 keeps the same driver package): the Windows
  host no longer uses ViGEmBus. It drives Nonary's `libvirtualgamepad` UMDF/VHF driver,
  as Vibepollo does. A host before 26.10.0 used another backend with other profiles;
  what follows holds from 26.10.0.
  - **Driver.** The pinned package is v0.1.0-beta.9, driver 0.1.0.53, protocol 2. It
    needs x64 Windows 10 version 2004 (build 19041) or newer
    (`docs/virtual-gamepad-driver.md:3-9`; release notes of 26.10.0 and 26.10.1). The
    profiles the host names are Xbox Series, Xbox One, DualSense, DualShock 4 and
    Switch Pro (`src/platform/windows/vhf_gamepad.cpp:574-579`). There is no Xbox 360
    profile (`docs/configuration.md:350-365`).
  - **No driver.** The host probes the driver once at start
    (`src/platform/windows/input.cpp:57`). Without it the host allocates no gamepad: it
    logs `LuminalShine virtual gamepad driver is not available` with a status
    (`vhf_gamepad.cpp:558-562`) and `No gamepad input is available`
    (`src/main.cpp:844-846`, `src/input.cpp:1755-1763`), and a controller arrival fails
    with `Couldn't connect to the LuminalShine virtual gamepad driver`
    (`vhf_gamepad.cpp:620-626`). With it, the start log reads `LuminalShine virtual
    gamepad driver is available (up to N controllers; offers ...)`, where N is the
    driver's maximum and the list names the profiles it offers
    (`vhf_gamepad.cpp:571-590`). The host does not tell the client: `/serverinfo` has no
    field for it (section 6). A client can ask the REST API (section 17.2). In a build with
    the tray, a notice shows when a stream starts (`src/stream.cpp:3376-3385`).
  - **The `gamepad` setting.** Values: `auto` (the default, the first entry),
    `xbox_series`, `xbox_one`, `ds4`, `dualsense`, `switch_pro` and `x360`
    (`src/platform/windows/input.cpp:809-829`, `src/config.cpp:879-882`). `x360` stays
    as a legacy alias and selects Xbox Series
    (`src/platform/windows/vhf_gamepad_policy.cpp:50-52`,
    `docs/configuration.md:362`). The setting can also be a per-app or a per-client
    override: `gamepad` is in the allow-list of `config_overrides`
    (`src/config.cpp:2117-2120`; section 5.2), applied at launch
    (`src/nvhttp.cpp:2939-2975`). An explicit profile that the driver does not offer is
    refused, not replaced by another: no controller is created
    (`vhf_gamepad.cpp:135-175`, `630-637`).
  - **The choice under `auto`** (`vhf_gamepad_policy.cpp:37-76`). The client's
    controller type decides first: PlayStation gives a DualShock 4, Nintendo gives a
    Switch Pro Controller, and Xbox gives the automatic profile. Only another type
    (unknown) lets the capabilities count: an accelerometer or a gyroscope gives a
    DualShock 4 when `motion_as_ds4` is on, and a touchpad gives a DualShock 4 when
    `touchpad_as_ds4` is on (both default true, `src/config.cpp:884-885`). Anything else
    gets the automatic profile, which is the first of Xbox Series, DualSense and
    DualShock 4 that the driver offers (`select_automatic_profile`, `:20-35`): Xbox
    Series on a driver that offers it. So an Xbox or a Nintendo type wins over motion
    and touchpad, which the host's own tests pin
    (`tests/unit/platform/windows/test_vhf_gamepad_policy.cpp:87-115`, `144-156`).
  - **Xbox One.** Only the setting `xbox_one` returns it
    (`vhf_gamepad_policy.cpp:53-55`); no client type or capability does.
    **Observed** in the VHF gamepad round of 2026-10-28
    ([device-validation section 19](../development/device-validation.md#vhf-gamepad-round-2026-10-28)):
    the same 8BitDo Ultimate 2 was created as an Xbox One controller on one client and as
    an Xbox Series controller on another. By the source, the `gamepad` value in effect
    for the Xbox One launch was `xbox_one`. The round did not read the host's setting or
    the client's overrides, so where that value came from is not known.
  - **What a profile carries.** The host accepts motion and a battery state only for the
    DualShock 4, the DualSense and the Switch Pro Controller (`has_motion`,
    `vhf_gamepad.cpp:188-192`, used at `794` and `823`), not for Xbox Series or Xbox One.
    It accepts controller touch only for the PlayStation profiles (`:184-186`, `728`):
    the Switch Pro Controller has motion and a battery but no touchpad. When a
    controller with motion is created, the host asks the client for accelerometer and
    gyroscope events at 100 Hz (`:662-669`). The touchpad-click and miscellaneous button
    bits go to the driver as they come (`vhf_gamepad_policy.cpp:98-100`,
    `vhf_gamepad_policy.h:258-269`); what
    each profile does with them is the driver's, and the driver source is a submodule that
    is not checked out. `ds4_back_as_touchpad_click` applies only when `gamepad` is `ds4`
    (`vhf_gamepad.cpp:871-875`).
  - **Feature bit `0x02`** (controller touch events) is now set unless `gamepad` is
    `x360`, `xbox_series`, `xbox_one` or `switch_pro`
    (`vhf_gamepad_policy.cpp:78-87`, `src/platform/windows/input.cpp:835-841`); the old
    rule looked at `x360` alone.
  - **Feedback.** The host sends rumble, trigger rumble, light colour, adaptive triggers
    and the motion request as section 12 lists. **DualSense adaptive triggers: Borealis
    has no handler.** `CONNECTION_LISTENER_CALLBACKS` has a `setAdaptiveTriggers` field
    (`core/third_party/moonlight-common-c/src/Limelight.h:481`, `499`), and the JNI table
    of the app leaves it unset: it fills `rumbleTriggers`, `setMotionEventState` and
    `setControllerLED` but not `setAdaptiveTriggers`
    (`borealis@f81be3409:apps/android/app/src/main/jni/moonlight-core/callbacks.c:421-434`).
    moonlight-common-c then puts in a function that does nothing
    (`core/third_party/moonlight-common-c/src/FakeCallbacks.c:146-148`), so the message
    arrives and is dropped. A search of `apps/android` and `core` for `adaptive trigger`,
    `TRIGGER_EFFECT` and `setAdaptiveTriggers` finds only the vendored library. Trigger
    rumble has a handler (`callbacks.c:431`, `ControllerHandler.handleRumbleTriggers`).
- The host settings `mouse` and `keyboard` gate all client input. With
  `mouse = disabled` the host drops every relative and absolute mouse, button,
  scroll, touch and pen packet (`src/input.cpp:452-983`); with
  `keyboard = disabled` it drops keys (`src/input.cpp:771-772`, `:872-873`). The
  host does not advertise either setting, so a client cannot tell. **Observed**
  in Sprint 2: touch "did not work" on the Galaxy S22 because the test host had
  `mouse = disabled`.

## 14. Audio

- Opus stereo, 5.1 and 7.1, each in a normal and a high-quality variant
  (`src/audio.h:15-23`, `src/audio.cpp:34`). The client selects the layout with
  `surroundAudioInfo` and, for custom mappings, `surroundParams`
  (`src/nvhttp.cpp:1615-1616`, `src/rtsp.cpp:1608-1634`).
- For stereo, the host reads the quality from the `Host` header of the RTSP
  request: a `Host` containing `0.0.0.0` means normal quality
  (`src/rtsp.cpp:1597-1607`).
- `continuousAudio=1` makes the host send audio without silence gaps
  (`src/nvhttp.cpp:1617`, `src/rtsp.cpp:1631-1634`).
- `localAudioPlayMode` selects whether the host also plays audio locally
  (`src/nvhttp.cpp:2822`).
- There is no client-to-host microphone stream: the host has no microphone
  control type, no `SS_ENC_*` microphone flag and no microphone SDP attribute
  (searched `src/stream.cpp`, `src/rtsp.cpp`, `src/audio.cpp`; the only
  `microphone` identifiers in `src/audio.cpp:228-286` belong to the host's
  loopback capture).

## 15. Encryption

- A client that sends `corever` of 1 or more selects encrypted RTSP
  (`rtspenc://`) and the host builds an AES-128-GCM cipher from `rikey`
  (`src/nvhttp.cpp:1636-1645`, `src/nvhttp.cpp:1448-1462`).
- Policy is per network class: LAN and local PC use `lan_encryption_mode`
  (default 0, never), everything else uses `wan_encryption_mode` (default 1,
  opportunistic); 2 is mandatory
  (`src/network.cpp:285-292`, `src/config.cpp:851-852`, `src/config.h:225-227`).
- DESCRIBE reports `encryptionSupported` and `encryptionRequested`
  (section 8.1). The client reports its choice in
  `x-ss-general.encryptionEnabled`; a legacy `nvFeatureFlags` bit also enables
  audio encryption (`src/rtsp.cpp:1532-1537`).
- A mandatory host refuses a client that cannot encrypt both video and audio:
  `/launch` and `/resume` return 403 and ANNOUNCE returns RTSP 403 (section 7.5,
  section 8.3).

## 16. WebRTC preemption

The host also offers a browser transport (WebRTC). It cannot run beside a
Moonlight session:

- `/launch` and `/resume` close every browser session and any browser capture
  still idling before they prepare the display
  (`src/nvhttp.cpp:2708-2719`, `src/nvhttp.cpp:2859`, `src/nvhttp.cpp:3286`;
  `architecture.md:85-107`).
- The browser is refused while a Moonlight session is streaming or a Moonlight
  launch is pending (`architecture.md:89-103`).

For Borealis this means a stream start can silently evict a browser viewer;
nothing is required of the client.

## 17. REST API

The REST API shares the Web UI HTTPS server (default port 47990, base port +1,
section 2; `src/confighttp.cpp:4506-4650`). It is a companion surface; none of
the streaming flows need it.

### 17.1 Authentication

Every `/api/` path except `/api/auth/login` and `/api/auth/logout` requires a
credential (`src/http_auth.cpp:1403-1476`):

| Scheme | Header | Notes |
| --- | --- | --- |
| Bearer API token | `Authorization: Bearer <token>` | The token carries a list of `{path, methods}` scopes and the request path and method must match one of them (`src/http_auth.cpp:1320-1327`). Created with `POST /api/token` and a body such as `{"scopes":[{"path":"/api/apps","methods":["GET"]}]}` (`src/confighttp.cpp:4269-4296`). The set of token-eligible routes is listed by `GET /api/token/routes` |
| Session token | `Authorization: Session <token>` or the session cookie | Obtained from `POST /api/auth/login` with `{"username","password"}`; refreshed with `POST /api/auth/refresh` (`src/confighttp.cpp:4764-4797`, `src/http_auth.cpp:1358-1376`) |
| Basic | `Authorization: Basic <base64>` | Admin credentials (`src/http_auth.cpp:1312-1318`) |

- A login with `"scope":"stats"` returns a session whose role is `stats`. That
  role may call only `GET /api/sessions`, `GET /api/sessions/{id}`,
  `GET /api/sessions/{id}/export.json`, `GET /api/metadata`,
  `GET /api/configLocale` and the `/api/auth/` endpoints; everything else is
  403 `insufficient_role` (`src/confighttp.cpp:4794-4796`,
  `src/http_auth.cpp:1331-1356`, `src/http_auth.cpp:1370-1376`).
- Access is limited by network class: the `origin_web_ui_allowed` setting
  (default `lan`) rejects clients outside that class with 403
  `origin_forbidden` (`src/config.cpp:857`, `src/http_auth.cpp:1415-1428`).
- The REST server is created from the same certificate and key settings as the
  GameStream HTTPS server (`config::nvhttp.cert`, `config::nvhttp.pkey`:
  `src/nvhttp.cpp:3630`, `src/confighttp.cpp:4512`), so a client that pinned the
  host certificate at pairing may be able to reuse that pin. **Unverified:**
  that both servers present the same certificate in a real installation.

### 17.2 Endpoints relevant to a client

| Method and path | Purpose | Reference |
| --- | --- | --- |
| `GET /api/pin` | Pending pairings (`pairing_id`, `client_address`, `state`, `age_seconds`) | `src/confighttp.cpp:3949-3970` |
| `POST /api/pin` | Submit PIN and name, optional `pairing_id`; result names such as `pin_delivered`, `unknown_pairing_id`, `invalid_pin`, `expired` | `src/confighttp.cpp:3988-4038`, `src/nvhttp.cpp:1688-1731` |
| `GET /api/apps` | Application definitions | `src/confighttp.cpp:1306-1330` |
| `GET /api/apps/{uuid}/cover` | Cover by app UUID | `src/confighttp.cpp:1468-1475` |
| `POST /api/apps/close` | Terminate the running app | `src/confighttp.cpp:1776-1795` |
| `GET /api/session/status` | `activeSessions`, `appRunning`, `paused` | `src/confighttp.cpp:2955-2970` |
| `GET /api/health/gamepad` | Whether the virtual gamepad driver is available: `{"installed": true, "version": "protocol <n>"}`, with `version` only when it is installed (Windows only). **Changed in 26.10.0** (verified in source at `dba21a9e`): it replaces `GET /api/vigembus/status` and `POST /api/vigembus/install`, which a host of 26.10.0-beta.1 or older serves (`src/confighttp.cpp:4650-4651` at `e2662a44`) and 26.10.0 does not | `src/confighttp.cpp:716-737`, `4566`, `src/platform/windows/misc.cpp:498-511` |
| `GET /api/clients/list` | Paired clients with per-client settings and a `connected` flag | `src/confighttp.cpp:1888-1905`, `src/nvhttp.cpp:2590-2640` |
| `POST /api/clients/update` | Replace per-client settings (section 5.2) | `src/confighttp.cpp:2010-2115` |
| `POST /api/clients/disconnect` | Disconnect a client's sessions without unpairing | `src/confighttp.cpp:2117-2138` |
| `POST /api/clients/unpair` | Unpair one client by `uuid` | `src/confighttp.cpp:2308-2332` |
| `GET /api/sessions`, `GET /api/sessions/{id}` | Session telemetry, proxied to the LuminalShineSessionMonitor service; 503 if that service is unreachable | `src/confighttp.cpp:2146-2200`, `src/confighttp.cpp:4591-4599` |
| `/api/webrtc/sessions/**` | Browser transport signaling | `src/confighttp.cpp:4602-4615` |

The POST endpoints in this table require `Content-Type: application/json`.

**Changed in 26.10.0-beta.1** (verified in source at `46f6936d`): the Web UI gains
`POST /api/apps/import`, which replaces the whole application list from an
`apps.json` document of at most 5 MiB (`src/confighttp.cpp:1616-1647`, route at
`src/confighttp.cpp:4585`, validation in `src/apps_import.h`). It is an
administration endpoint, not a streaming one, and a client does not need it.

## 18. Client obligations for Borealis

These are actionable requirements derived from the sections above. Where the
Moonlight Android 12.2 client already does the right thing the item says so.

1. **Pair over HTTP with no read timeout on phase 1 and tell the user what to
   do.** The user must open the host Web UI (`https://<host>:47990`) and enter
   the PIN and a device name (section 4.1). Borealis should display the PIN, the
   Web UI address and a suggested name. Moonlight Android already uses a
   no-timeout client for phase 1.
2. **Keep all four pairing phases on one IP address.** Pin the HTTP client to
   the address used for phase 1 and do not fall back from IPv6 to IPv4 or the
   reverse mid-pairing (section 4.2). Give up after 5 minutes (host answers 408).
3. **Handle the pairing status codes in section 4.3** with distinct user
   messages, and do not retry 400, 403, 404 or 410.
4. **Do not rely on `GET /unpair`.** It is not routed and returns 404. Moonlight
   Android calls it on every pairing failure path and for the in-app unpair
   (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/PairingManager.java:219-289`;
   the 12.2 `PcView` that made the in-app call is gone, and the Library's
   `ComputerHostRepository.forget` makes it now,
   `borealis@a050fbac:apps/android/app/src/main/kotlin/io/github/magalz/borealis/library/ComputerHostRepository.kt:93-99,117-126`).
   Borealis must treat that call as best-effort, and must explain that removing a
   host on the phone does not unpair it on the host (unpair through the Web UI or
   `POST /api/clients/unpair`). **Verified against LuminalShine 26.09.1
   (2026-10-14, fixture `core/tests/fixtures/luminalshine/26.09.1/unpair-http.response`):**
   over plain HTTP the host answers `HTTP/1.1 200 OK` with `Content-Length: 64`
   and an XML body whose `status_code` is 404, then writes 88 more bytes on the
   same connection (`HTTP/1.1 404 NOT FOUND`, CRLF and the XML again, with no
   header block) and closes the socket about 80 ms later
   (`src/nvhttp.cpp:2136-2140`). The client therefore does not get an HTTP 404:
   `unpair()` returns normally and ignores the body, so `FileNotFoundException`
   does not mask PIN_WRONG or FAILED. The risk is the trailing bytes: an HTTP
   client that pools the connection can read them as the start of its next
   response on that connection. Send `/unpair` without connection reuse and
   read its XML `status_code`. **Changed in 26.10.0-beta.1** (#200; verified in
   source at `46f6936d`, and recorded on a running 26.10.0-beta.1 host on
   2026-10-23, fixture
   [`unpair-http.response`](../../core/tests/fixtures/luminalshine/26.10.0-beta.1/unpair-http.response)):
   `/unpair` is still not routed (`src/nvhttp.cpp:3822-3854`), so it reaches
   `not_found`, which now writes one response (`src/nvhttp.cpp:2146-2158`, section
   3). Over plain HTTP the host answers `HTTP/1.1 200 OK` with
   `Content-Length: 64` and the XML body with `status_code="404"`, 103 bytes in
   all, and closes the socket; the 88 trailing bytes are gone (the capture read
   to the end of the stream and found nothing after the body). The response is
   byte for byte the one of an unknown path. Keep the 26.09.1 behaviour and the
   `BestEffortUnpair` `Connection: close` for older hosts.
5. **Keep the client certificate stable and use HTTPS for everything except
   pairing phases 1 to 4 and the initial `/serverinfo`.** Identity, per-client
   settings and the virtual display depend on the certificate (section 5.1).
   Surface the 401 `Certificate verification failed` response as "this device is
   no longer paired".
6. **Name the device without `xbox` or `webos`** (section 10.2), or accept an
   ANNOUNCE hold of up to 8 s and make every RTSP request timeout at least 10 s.
7. **Implement the launch retry policy of section 7.5.** Moonlight Android does
   not retry. Use bounded backoff for 409 and 503, no retry for 400, 403, 404 and
   -1, never call `/cancel` to clear a 409, and show the host's message text for
   503. Make the retry budget a configuration value, not a constant.
8. **Follow the existing `currentgame` logic** when `/serverinfo` shows a running
   app (upstream's resume or quit prompt in the app list), and **ask the user**
   when the 400 `An app is already running` row arrives instead: Join, Quit and
   relaunch, or Cancel (sections 7.5 and 7.6).
9. **Send the same native mode in `/launch` and in ANNOUNCE.** `mode=WxHxFPS`
   must equal `clientViewportWd`, `clientViewportHt` and `maxFPS`, otherwise
   the host rebuilds the virtual display (section 7.4). Moonlight Android sets
   `clientRefreshRateX100` from the display refresh rate
   (`borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:498`); the
   host ignores it when it disagrees with `maxFPS`, so that attribute is
   harmless. The launch rate and the stream rate are the ones that must match;
   Moonlight Android can make them differ (end of section 7.4), so Borealis must
   pass the same value to both.
10. **Parse `ServerCodecModeSupport` as a bit field, ignore unknown bits, and
    never assume a codec from the host name or version** (section 9). Add a
    capability model that can hold the PyroWave bits (see
    [pyrowave.md](pyrowave.md)).
11. **Verify chroma and dynamic range from the bitstream, not the negotiation.**
    Expect a silent 4:4:4 to 4:2:0 downgrade and a silent 8-bit SDR to 10-bit SDR
    upgrade (sections 9.1 and 9.2). Trust control message 0x010e for HDR state.
12. **Tolerate a slow first frame.** Discard datagrams below the minimum RTP
    size, do not fail before about 18 s without a frame, and show "waiting for
    the host" in the UI (section 10.1).
13. **Send the correct controller type and capabilities in controller arrival
    packets**, because the host picks the emulated pad from them
    (section 13). Handle rumble (0x010b), motion enable (0x5501) and RGB LED
    (0x5502, host to client). Expect no trigger effects from a host before 26.10.0.
    **Changed in 26.10.0** (verified in source at `dba21a9e`): the VHF backend also sends
    trigger rumble (0x5500) and, for a DualSense, adaptive triggers (0x5503) (sections 12
    and 13). Borealis handles trigger rumble and drops adaptive triggers, which it has no
    callback for.
14. **Send `corever` of 1 and the `x-ss-general.encryptionEnabled` flags**, and
    treat a 403 on launch or ANNOUNCE as a hard protocol error (section 15).
    `corever` is required only when the encryption policy is mandatory
    (`src/nvhttp.cpp:3096`, `src/rtsp.cpp:1748`); upstream moonlight-common-c
    always sends `&corever=1` (`borealis:core/third_party/moonlight-common-c/src/Connection.c:553`, upstream 874ac95), so
    Moonlight Android already complies.
15. **Do not offer microphone passthrough, clipboard sync, server commands,
    OTP pairing or a virtual-display toggle that depends on host support**
    (section 19).
16. **Send 0x0302 (IDR request) after unrecoverable loss**; for PyroWave also send
    the 0x5502 status (see [pyrowave.md](pyrowave.md)).
17. **Treat the REST API as optional and credential-gated.** Use a least
    privilege token with an explicit `{path, methods}` scope or the `stats` role;
    never store admin credentials in the clear; expect 403 `origin_forbidden` from
    outside the LAN.
18. **Expect a host upgrade to end a running stream.** **Changed in 26.10.0 and
    26.10.1** (from the release notes): the "Upgrading" section of both releases says
    that installing over the previous release keeps settings and pairings, ends an
    active stream during the upgrade, and lets the stream restart after the service
    returns (`gh release view 26.10.0`, `gh release view 26.10.1`; seen in the release
    notes, not in the installer source). The client keeps its pairing, so a lost stream
    followed by an offline host that comes back is not a reason to unpair.

## 19. Gaps versus Apollo and Artemis clients

An Apollo-aware client such as Artemis assumes host features that LuminalShine
does not provide:

| Feature in Artemis | LuminalShine behaviour | Reference |
| --- | --- | --- |
| `otpauth` pairing argument (a hash of PIN, salt and a passphrase) | Ignored; the host waits for the PIN in the Web UI | `artemis:app/src/main/java/com/limelight/nvstream/http/PairingManager.java:215-223`, `src/nvhttp.cpp:2204-2345` |
| `GET /actions/clipboard` and `POST /actions/clipboard` | 404 | `artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:925-930`, `src/nvhttp.cpp:3723-3748` |
| `VirtualDisplayCapable` and `VirtualDisplayDriverReady` in `/serverinfo` | Capability field never sent; the driver-ready field is returned only in `/launch` and `/resume` | `artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:432-434`, `:550-565`; `src/nvhttp.cpp:3144-3148` |
| `virtualDisplay=`, `scaleFactor=`, `appuuid=`, `gcpersist=` launch parameters | Ignored | `artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:878-892` |
| `Permission` in `/serverinfo` | Not sent | `artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:412-419` |
| `ServerCommand` list in `/serverinfo` | Not sent | `artemis:app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:437`, `:568-570` |
| `GET /unpair` | 404 | `borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java:745-749` |

The virtual-display gap is the one most likely to mislead: Artemis concludes the
host has no virtual display and refuses to offer it
(`artemis:app/src/main/java/com/limelight/AppView.java:513`, `:540`, `:760`),
although LuminalShine creates a per-client virtual display by default
(section 7.3). Borealis should not copy that capability check; it should treat the
virtual display as a host-side policy that is always possibly active.

## 20. Proposals for LuminalShine

These are requests Borealis would make to the LuminalShine project. They change
nothing in this document until the host adopts them.

- **P1. An explicit capability and version marker in `/serverinfo`.** Add fields
  such as `LuminalShineVersion` (the release, for example `26.09.1`),
  `LuminalShineApiLevel` (an integer bumped for client-visible protocol
  changes) and a `Capabilities` list (for example `pyrowave`, `virtual-display`,
  `launch-retry-codes`). Today a client can only infer the host from the absence
  of Apollo fields and from `appversion` `7.1.431.-1`, which is a fixed
  emulation constant (`src/nvhttp.h:37`). **Partly adopted in 26.10.0-beta.1**
  (#199; verified in source at `46f6936d`): `LuminalShineVersion` is in the HTTPS
  `/serverinfo` only (`src/nvhttp.cpp:2507-2509`, `2614-2617`; section 1), so a
  client can read the release only after it is paired, by design
  (`src/nvhttp.h:73-75`). Nothing in `src/` writes a `LuminalShineApiLevel` or a
  `Capabilities` element (a search of `src/` finds neither name), so the
  capability list and the API level of this proposal are still open.
- **P2. A client-requested PyroWave bitrate cap.** The host ignores the client's
  bitrate and always uses `pyrowave_bitrate_mbps`, default 700
  (`src/rtsp.cpp:1694`). Honour the client's `x-nv-vqos[0].bw.maximumBitrateKbps`
  or `x-ml-video.configuredBitrateKbps` as an upper bound on the host target, so a
  phone on Wi-Fi can ask for less without an operator changing host settings.
- **P3. A KEEP-frame replenishment capability attribute.** With
  `pyrowave_refresh_interval` above 0 the host sends KEEP frames that only
  certified clients can decode, and the wire protocol has no way for a client to
  declare support (`docs/pyrowave.md:80-88`). Add a DESCRIBE attribute (host
  supports replenishment) and an ANNOUNCE attribute (client honours code-1
  frames) so the host can enable replenishment per session.
- **P4. Tell the client about a 4:4:4 downgrade.** Send a control message when the
  in-session chroma changes, as `src/yuv444_fallback.h:23-24` already claims the
  host does (section 9.1).
- **P5. A documented, stable error vocabulary.** The launch and resume status
  messages in section 7.5 are the de facto API; document them, and add a
  machine-readable `Retry-After` hint for 409 and 503.

## Appendix: host documentation drift found while verifying

| Statement | Location | Actual behaviour |
| --- | --- | --- |
| `/cancel` does not pop the pending launch | `architecture.md:101` | `/cancel` calls `terminate_sessions`, which cancels the pending launch (`src/nvhttp.cpp:3531`, `src/rtsp.cpp:1132-1135`) |
| The 4:4:4 fallback tells the client the chroma changed | `src/yuv444_fallback.h:23-24` | Only the session statistics are updated; no client message (`src/stream.cpp:1572-1584`) |
