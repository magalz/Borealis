# PyroWave host and client contract

PyroWave is an experimental, GPU-only wavelet video codec that LuminalShine can
offer as a fourth video format beside H.264, HEVC and AV1. This document records
what the host does and what a client needs to decode and feed back to it. It was
verified against `NortheBridge/luminalshine@5afdbdc6`; see [README.md](README.md)
for the reference conventions and [luminalshine.md](luminalshine.md) for the
surrounding GameStream contract.

Everything here comes from static source reading. No item in this document is a
runtime observation; the items marked **Observed** are in
[luminalshine.md](luminalshine.md). The host's own documentation
states that no live remote client session has been certified
(`docs/pyrowave.md:146-147`). Hardware acceptance is defined against the pinned
Aurora client, and the host's own GPU test decodes with the pinned Aurora decoder
(`docs/pyrowave.md:124-129`, `docs/pyrowave.md:136-138`). Treat every claim about
how a real client behaves as **Unverified** unless it says otherwise.

## 1. Provenance and licences

| Component | Origin | Licence | Reference |
| --- | --- | --- | --- |
| Codec sources and shaders (`third-party/pyrowave/src/pyrowave`, `shaders`) | Hans-Kristian Arntzen, extracted from the `proto/pyrowave` branch of WiVRn | MIT, copyright 2025 | `third-party/pyrowave/README.md:3-5`, `third-party/pyrowave/src/pyrowave/pyrowave_common.h:1-2`, `third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:1-2` |
| Vulkan Memory Allocator single header | AMD | MIT | `third-party/pyrowave/external/vk_mem_alloc.h:1-14` |
| Allocation helpers (`src/vk`) and `src/utils/singleton.h` | WiVRn | GPL-3.0-or-later | `third-party/pyrowave/src/vk/allocation.h:1-17`, `third-party/pyrowave/src/utils/singleton.h:1-5`, `third-party/pyrowave/README.md:146-152` |
| Host adapter (`src/pyrowave`) | Derived from `Koloses/Solarflare` at `48ae555e73428926ae4be234f15513967a839397`; LuminalShine added a Windows scRGB-to-PQ conversion in `rgb2yuv.comp` | Not stated separately; LuminalShine is GPL-3.0 (`LICENSE:1-2`) | `third-party/pyrowave/LUMINALSHINE_ORIGIN.md:1-9`, `docs/pyrowave.md:14-15` |
| Pinned decoder (`pyrowave_decoder.cpp`, `pyrowave_decoder.h`) | Aurora client revision, including its non-coherent decoder-input flush support | MIT (file headers) | `third-party/pyrowave/LUMINALSHINE_ORIGIN.md:11-13` |
| Reference client | Aurora, a Moonlight Qt fork: `Koloses/aurora-qt@2c574a9e79da5b3fa95bab90ffd3d322f5d63ac0` | Not examined | `docs/pyrowave.md:10-11` |
| Client protocol library | `Koloses/moonlight-common-c@a5228faeeda949a3ab0140f55a75bd14fe74e899` | Not examined | `docs/pyrowave.md:12-13` |

The wire contract (the profile bits, the ANNOUNCE attributes and the 0x5502
feedback message) is defined by the pinned Aurora and `moonlight-common-c`
revisions (`src/pyrowave/contract.h:17-18`, `src/stream.cpp:1322-1328`). The
Aurora fork is not present in the reference trees; upstream moonlight-common-c at
874ac95, which Moonlight Android uses, was checked where this document says so.
Any detail of the fork that is not visible from the host side is **Unverified**.

Moonlight Android in this repository is GPL-3.0
(`borealis:apps/android/LICENSE.txt:1-2`). The vendored `src/vk` helpers are
GPL-3.0-or-later and the codec is MIT; the codec README tells integrators to
verify licence compatibility per host project
(`third-party/pyrowave/README.md:151-152`). That review has not been done for
Borealis and is outside the scope of this document.

## 2. Availability gating

The host advertises PyroWave only when every gate below is open.

| Gate | Behaviour | Reference |
| --- | --- | --- |
| Build flag | `SUNSHINE_ENABLE_PYROWAVE` defaults to OFF in CMake; the reusable Windows CI workflow, which takes release inputs, builds with it ON | `cmake/prep/options.cmake:81`, `.github/workflows/ci-windows.yml:3-14`, `.github/workflows/ci-windows.yml:423` |
| Runtime setting | `pyrowave_enabled` defaults to disabled; the host must be restarted after enabling it | `docs/configuration.md:2631-2639`, `src/config.cpp:1635` |
| Startup probe | The four wire profiles are probed once at startup in a child process across plausible hardware adapters; the published mask is the intersection. A failed probe publishes mask 0 until restart | `src/pyrowave/backend.cpp:440-470` |
| Snapshot only | HTTP, RTSP and worker admission read a cached mask and never create a Vulkan device | `src/pyrowave/backend.cpp:473-481` |
| Baseline profile | `available()` is true only if the SDR 4:2:0 profile passed the probe; a machine that supports only an optional profile is not "available" | `src/pyrowave/backend.cpp:487-491` |
| Platform | Windows-only: the encoder uses DXGI textures shared with Vulkan, requires Vulkan 1.3 and keyed-mutex interoperability on the capture adapter | `src/pyrowave/backend.cpp:493-503`, `docs/pyrowave.md:45-46` |

Both `/serverinfo` and the RTSP DESCRIBE response test
`config::pyrowave.enabled && pyrowave::available()`
(`src/nvhttp.cpp:2573-2575`, `src/rtsp.cpp:1312-1314`).

**Release.** The release notes of 26.10.0-beta.1, a prerelease of 2026-10-20,
report that the official Windows build now includes experimental, opt-in PyroWave,
needing Vulkan 1.3, with a target of 700 Mbps (reported in the 26.10.0-beta.1 release notes (2026-10-20); not yet verified in source).

## 3. Negotiation

### 3.1 `/serverinfo`: `ServerCodecModeSupport` bits

PyroWave uses four profile bits that sit above the legacy codec bits
(`src/pyrowave/contract.h:17-22`). Only profiles that passed the probe are set,
and the 4:4:4 bits are set only when `yuv444_streaming` is true (default true,
`src/config.cpp:748`) (`src/pyrowave/contract.h:34-60`,
`src/nvhttp.cpp:2544`, `src/nvhttp.cpp:2573-2575`).

| Profile | Bit | Dynamic range | Chroma |
| --- | --- | --- | --- |
| SDR 4:2:0 | `0x00800000` | SDR (BT.709) | 4:2:0 |
| SDR 4:4:4 | `0x01000000` | SDR (BT.709) | 4:4:4 |
| HDR 4:2:0 | `0x02000000` | HDR10 (BT.2020, PQ) | 4:2:0 |
| HDR 4:4:4 | `0x04000000` | HDR10 (BT.2020, PQ) | 4:4:4 |

`/applist`'s `IsHdrSupported` says nothing about PyroWave HDR
(`src/nvhttp.cpp:2685`); a client must read the profile bits.

### 3.2 RTSP DESCRIBE

When PyroWave is enabled and available the SDP contains
`a=rtpmap:99 PYROWAVE/90000` (`src/rtsp.cpp:1312-1314`).

### 3.3 RTSP ANNOUNCE

The client selects PyroWave with `x-nv-vqos[0].bitStreamFormat:3`
(`src/pyrowave/contract.h:13`, `src/rtsp.cpp:1672-1744`) and identifies the
profile with the existing attributes:

| Attribute | Value | Role |
| --- | --- | --- |
| `x-nv-vqos[0].bitStreamFormat` | `3` | Selects PyroWave |
| `x-nv-video[0].dynamicRangeMode` | `0` SDR, `1` HDR | Selects the HDR or SDR profile (`src/rtsp.cpp:1548`, `src/rtsp.cpp:1721-1725`) |
| `x-ss-video[0].chromaSamplingType` | `0` 4:2:0, `1` 4:4:4 | Selects the chroma profile (`src/rtsp.cpp:1549`) |
| `x-nv-video[0].clientViewportWd`, `clientViewportHt` | Stream width and height | Validated, and the frame size the encoder uses (`src/rtsp.cpp:1539-1540`, `src/pyrowave/backend.cpp:524-528`) |
| `x-nv-video[0].maxFPS` | Frame rate | Validated (`src/rtsp.cpp:1541`, `src/rtsp.cpp:1724-1725`) |
| `x-nv-video[0].packetSize` | Packet size in bytes | Validated and used for packet alignment (`src/rtsp.cpp:1527`, `src/rtsp.cpp:1695`) |
| `x-ss-video[0].pyrowaveAdaptiveFec` | Exactly `0` or `1`, optional | Opts in to loss-driven FEC adaptation (section 6) |
| `x-ss-video[0].pyrowaveAdaptiveBitrate` | Exactly `0` or `1`, optional | Opts in to loss-driven bitrate adaptation (section 6) |

Parsing and validation (`src/rtsp.cpp:1463-1493`, `src/rtsp.cpp:1689-1730`):

- The two optional attributes are parsed strictly. The value must be the single
  character `0` or `1`: no whitespace, no sign, no textual boolean
  (`src/pyrowave/contract.h:64-72`). Legacy attributes tolerate one trailing
  space; these two do not (`src/rtsp.cpp:1482-1487`). A missing attribute means
  `0`.
- A duplicated attribute is rejected (RTSP 400 `BAD REQUEST`,
  `src/rtsp.cpp:1689-1692`). A malformed value is rejected the same way
  (`src/rtsp.cpp:1698-1717`).
- The request is also rejected with RTSP 400 when PyroWave is disabled, when it
  asks for 4:4:4 while `yuv444_streaming` is off, when the requested
  dynamic-range and chroma combination was not probed, or when the mode exceeds
  the transport limits of section 4 (`src/rtsp.cpp:1721-1728`).
- Everything else about ANNOUNCE (audio, encryption flags, the launch
  reconciliation) is unchanged from [luminalshine.md](luminalshine.md) section 8.

## 4. Host-side constraints

| Constraint | Detail | Reference |
| --- | --- | --- |
| Bitrate is host-controlled | The client's `bw.maximumBitrateKbps` and `configuredBitrateKbps` are replaced by the host setting `pyrowave_bitrate_mbps` (default 700, range 0 to 10000, 0 means 700) | `src/rtsp.cpp:1694`, `src/pyrowave/contract.h:14-15`, `src/pyrowave/contract.h:145-148`, `src/config.cpp:1636`, `docs/configuration.md:2641-2654` |
| Operator override per client | `pyrowave_enabled`, `pyrowave_bitrate_mbps`, `pyrowave_quality_bias` and `pyrowave_refresh_interval` are in the allow-list for per-client and per-app `config_overrides`. **Unverified:** that a per-client `pyrowave_bitrate_mbps` takes effect end to end for a launch | `src/config.cpp:2113-2165`, `src/nvhttp.cpp:2896-2932` |
| Colour conversion is forced | `encoderCscMode` is set to 2: limited-range BT.709 for SDR, BT.2020 with PQ for HDR; the client's value is ignored and 10-bit SDR preference is switched off | `src/rtsp.cpp:1731-1732`, `docs/pyrowave.md:3-4` |
| Quality settings are host-only | `pyrowave_quality_bias` (0 to 3) and `pyrowave_refresh_interval` (0 to 255) are copied from the host configuration; the client cannot change them | `src/rtsp.cpp:1696-1697`, `src/config.cpp:1637-1638`, `src/pyrowave/backend.cpp:513-514` |
| Dimensions | Width and height from 128 to 8192, both even | `src/pyrowave/contract.h:166-168` |
| Frame rate | `maxFPS` from 1 to 240 | `src/pyrowave/contract.h:168` |
| Packet size | 256 to 2048 bytes (PyroWave only; ordinary codecs accept 64 to 65535) | `src/pyrowave/contract.h:152-157`, `src/rtsp.cpp:1588-1593` |
| Frame budget | The per-frame byte budget `kbps * 1000 / (fps * 8)` must not exceed 75% of the transport's hard frame limit | `src/pyrowave/contract.h:149-171` |
| HDR needs an HDR display | A request with `dynamicRangeMode=1` fails encoder creation if the capture display is not in HDR. The client must send `hdrMode=1` on `/launch` (so the virtual display is created as HDR) and `dynamicRangeMode=1` in ANNOUNCE. The failure happens when the encoder is built, after ANNOUNCE was accepted; **Unverified:** what the client observes | `src/pyrowave/backend.cpp:519`, `docs/pyrowave.md:57-61` |
| No 4:4:4 downgrade | At ANNOUNCE a 4:4:4 request with `yuv444_streaming` off is rejected (400), unlike ordinary codecs, which are silently changed to 4:2:0. In a running session a failed 4:4:4 encoder build fails; it is never downgraded | `src/rtsp.cpp:1552-1559`, `src/rtsp.cpp:1721-1725`, `src/video.cpp:2765-2774` |
| Hard frame size bound | A frame larger than the negotiated bound is dropped (the encoder returns no data); if that frame answered a refresh request, the request is queued again | `src/pyrowave/pyrowave_encode.cpp:863-868`, `src/pyrowave/backend.cpp:532` |
| Block size bound | A block record of 4096 words or more makes the whole frame invalid (12-bit `payload_words`) | `src/pyrowave/pyrowave_encode.cpp:641` |
| FEC is dropped for huge frames | A frame needing more than four FEC blocks is sent without FEC | `src/stream.cpp:2032-2053` |
| Pacing | PyroWave pacing may drain up to 15 Gbps (ordinary codecs are capped lower) | `src/stream.cpp:2117` |

### 4.1 Frame budget arithmetic

The hard frame limit is `(packetSize - 128) * 4 * 1023 - 1024` bytes, and the
admitted budget is that value minus a quarter
(`src/pyrowave/contract.h:152-164`). The frame budget at the host bitrate must
fit. The table below is derived from those formulas for the default
700 Mbps (`kbps` 700000) and is an approximation (integer rounding aside).

| `packetSize` | Admitted frame budget (bytes) | Minimum `maxFPS` |
| --- | --- | --- |
| 256 | 392064 | 224 |
| 1024 | 2749056 | 32 |
| 1392 (Moonlight Android's LAN value, `borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:494`) | 3878448 | 23 |
| 1500 | 4209900 | 21 |
| 2048 | 5891712 | 15 |

Moonlight Android negotiates 1024 instead of 1392 on connections it classifies
as remote (`borealis@2f321dc77:apps/android/app/src/main/java/com/limelight/nvstream/NvConnection.java:370-372`,
passed to the native library at `:537`), which raises the minimum to 32 fps at
700 Mbps. When video encryption is enabled, upstream moonlight-common-c announces
`packetSize` minus 32 bytes (`borealis:core/third_party/moonlight-common-c/src/SdpGenerator.c:323-330`), which raises the
minimums slightly (about 24 fps at 1392 - 32 and about 34 fps at 1024 - 32,
derived).

Consequence: with the default host bitrate a session below about 23 fps (LAN) or
32 fps (remote) is rejected with RTSP 400, and a lower host bitrate lowers that
minimum proportionally. At 700 Mbps and 60 fps the average frame budget is 1458333
bytes.

## 5. Bitstream and transport layout

### 5.1 Overview

PyroWave rides on the ordinary GameStream video transport: an RTP packet carries
an `NV_VIDEO_PACKET` header, the payload is split across up to four
Reed-Solomon FEC blocks, and optional AES-GCM applies per packet
(`src/stream.cpp:2009-2053`, `src/stream.cpp:2185-2240`,
[luminalshine.md](luminalshine.md) section 11). The video frame starts with the
8-byte short frame header; its `frameType` is 2 for a full PyroWave frame and 1
for a KEEP frame (`src/stream.cpp:1982-1989`,
`src/pyrowave/pyrowave_encode.cpp:765`, `src/pyrowave/backend.cpp:549`).

The video payload carried by one packet is `packetSize - sizeof(NV_VIDEO_PACKET)`
bytes: the host uses that value as the PyroWave payload size and as the divisor
for `lastPayloadLen` (`src/pyrowave/backend.cpp:527`,
`src/stream.cpp:1986-1988`). `NV_VIDEO_PACKET` is 16 bytes (4 + 4 + 1 + 1 + 1 +
1 + 4) in upstream moonlight-common-c at 874ac95
(`borealis:core/third_party/moonlight-common-c/src/Video.h:27-35`; not the Aurora fork), so this document writes the
payload size as `packetSize - 16`.

Each encoded frame is one self-delimiting blob
(`src/pyrowave/pyrowave_encode.cpp:695-861`):

1. one 8-byte sequence header record,
2. zero or more block records, each with an 8-byte header and a coded payload,
3. padding records interleaved so that block records do not cross RTP payload
   boundaries (section 5.5).

All multi-byte fields are little endian: the host copies the structures as they
are in memory on a little-endian machine. Bit-fields below are written as the C
declarations list them, allocated from the least significant bit; the layout is
implementation-defined in C++ and is **Unverified** against a hex dump, but it is
consistent with the host's own code, which masks the sequence field at bits 4 to
6 of byte 3 of the block header (`src/pyrowave/pyrowave_encode.cpp:107-115`).

### 5.2 Sequence header (`BitstreamSequenceHeader`, 8 bytes)

Definition: `third-party/pyrowave/src/pyrowave/pyrowave_common.h:96-114`.

| Bits | Width | Field | Meaning |
| --- | --- | --- | --- |
| 0 to 13 (word 0) | 14 | `width_minus_1` | Frame width minus 1 |
| 14 to 27 (word 0) | 14 | `height_minus_1` | Frame height minus 1 |
| 28 to 30 (word 0) | 3 | `sequence` | Frame sequence number, modulo 8 |
| 31 (word 0) | 1 | `extended` | 1 for a sequence header, 0 for a block header |
| 0 to 23 (word 1) | 24 | `total_blocks` | Number of block records that follow in this frame (the host sets it to the count of blocks it sends, `src/pyrowave/pyrowave_encode.cpp:835`) |
| 24 to 25 (word 1) | 2 | `code` | 0 full frame (`START_OF_FRAME`), 1 KEEP frame (`START_OF_FRAME_KEEP`); other values are rejected by the decoder |
| 26 (word 1) | 1 | `chroma_resolution` | 0 4:2:0, 1 4:4:4 |
| 27 (word 1) | 1 | `color_primaries` | 0 BT.709, 1 BT.2020 |
| 28 (word 1) | 1 | `transfer_function` | 0 BT.709, 1 PQ |
| 29 (word 1) | 1 | `ycbcr_transform` | 0 BT.709, 1 BT.2020 |
| 30 (word 1) | 1 | `ycbcr_range` | 0 full, 1 limited (the host always sets limited) |
| 31 (word 1) | 1 | `chroma_siting` | 0 centre, 1 left (the host leaves it 0) |

The host sets the colour fields from the negotiated profile; they are
informational because the client already knows the profile from negotiation
(`src/pyrowave/pyrowave_encode.cpp:828-846`). The decoder does check
`chroma_resolution` against the decoder's configured chroma, and the width and
height against the decoder's configured size
(`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:426-430, 451-456`).

### 5.3 Block header (`BitstreamHeader`, 8 bytes)

Definition: `third-party/pyrowave/src/pyrowave/pyrowave_common.h:30-40`. One block
record is this header followed by the block's coded words; each block covers a
32 by 32 luma region.

| Bits | Width | Field | Meaning |
| --- | --- | --- | --- |
| 0 to 15 (word 0) | 16 | `ballot` | Per-block coefficient presence mask; 0 means an explicit zero block |
| 16 to 27 (word 0) | 12 | `payload_words` | Total record size in 32-bit words, including this 8-byte header (a header-only record is 2 words); the decoder reads `payload_words * 4 - 8` payload bytes after the header (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:417`); the host rejects records of 4096 words or more |
| 28 to 30 (word 0) | 3 | `sequence` | Frame sequence number, modulo 8, equal to the sequence header's |
| 31 (word 0) | 1 | `extended` | 0 for a block header |
| 0 to 7 (word 1) | 8 | `quant_code` | Quantiser code for the block |
| 8 to 31 (word 1) | 24 | `block_index` | Index of the 32 by 32 block in the frame |

An explicit zero block is a header-only record with `ballot` 0, `payload_words`
2, `quant_code` 0 and the frame's sequence number. The decoder stores zeros for
that block, replacing whatever it held
(`src/pyrowave/pyrowave_encode.cpp:847-860`).

### 5.4 Padding record

A padding record is the 32-bit value `0xFFFFFFFF`, a 32-bit word count `N`, and
`N` zero 32-bit words; the whole record is `8 + 4 * N` bytes
(`third-party/pyrowave/src/pyrowave/pyrowave_common.h:53-58`). The decoder checks
the first 32-bit word of each 8-byte header it reads and skips the rest of the
record when it equals the magic
(`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:359-369`). The magic
cannot be a real header because it would set `extended` and the largest width
(`third-party/pyrowave/src/pyrowave/pyrowave_common.h:53-58`). The host writes
padding records of at least 8 bytes and always word-aligned
(`src/pyrowave/pyrowave_encode.cpp:782-787`).

### 5.5 RTP alignment

The encoder keeps every block record inside one RTP payload where it can, so
that a lost packet costs only the blocks it carried
(`src/pyrowave/pyrowave_encode.cpp:769-826`):

- The payload size is `packetSize - 16`. The first payload also carries the
  8-byte video frame header, so the first boundary is at `payload - 8` bytes of
  PyroWave data (`src/pyrowave/pyrowave_encode.cpp:776`).
- Before appending a record the encoder pads to the next boundary if the record
  would cross it. It pads at most once per record. A record that is larger than
  one payload spans payloads. A tail of fewer than 8 bytes cannot hold a padding
  record, so that boundary stays unaligned; a record whose placement would leave
  an unpaddable 4-byte tail is placed as is
  (`src/pyrowave/pyrowave_encode.cpp:795-826`).
- The sequence header is the first record and is aligned the same way
  (`src/pyrowave/pyrowave_encode.cpp:827-846`).

A client must feed whole RTP payloads to the decoder in order and must tell the
decoder where a payload was lost (section 7.2, step 5).

### 5.6 Full frames and KEEP frames

- With `pyrowave_refresh_interval` 0 (the default) every frame is a full frame:
  code 0, frame type 2, every coded block present
  (`src/pyrowave/pyrowave_encode.cpp:703-708`, `docs/pyrowave.md:80-82`). Absent
  blocks decode to zero. Packet loss therefore affects one frame only.
- With `pyrowave_refresh_interval` above 0 the host uses conditional
  replenishment: most frames are KEEP frames (code 1, frame type 1) that carry
  only blocks that changed plus a rolling refresh slice
  (`src/pyrowave/pyrowave_encode.cpp:742-765`). In a KEEP frame, blocks absent
  from the bitstream keep the previous frame's wavelet coefficients instead of
  decoding to zero (`third-party/pyrowave/src/pyrowave/pyrowave_common.h:44-51`).
  The encoder must send a code 0 frame first. This is "fork extension" behaviour,
  certified only for the pinned Aurora revisions: the wire protocol has no
  capability attribute for it (`docs/pyrowave.md:80-88`).
- A decoder that has seen only KEEP frames and no full frame reports
  `awaiting_state_init()`; the client should keep requesting an IDR while that
  holds (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:76-82`).
- A client IDR request always yields a hard full frame, never a KEEP frame,
  because the requester may have missed the stream's first frame
  (`src/pyrowave/pyrowave_encode.cpp:703-708`, `src/pyrowave/pyrowave_encode.h:97-102`,
  `src/pyrowave/pyrowave_encode.h:166-173`).

### 5.7 Decoder parsing rules

From `DecoderInput::push_data`
(`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:336-470`) and
`DecoderInput::clear` (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:304-319`):

- Bytes are accumulated into the 8-byte header first. A first word equal to the
  padding magic starts a skip of `N * 4` bytes.
- `extended` 0 means a block record. The 3-bit sequence is compared with the last
  one using modulo-8 arithmetic; a difference above 3 is treated as backwards
  and the data is discarded. A new sequence value clears the accumulated state.
- A block with `block_index` at or beyond the block count is an error; a block
  that was already received in this sequence is skipped.
- A block is registered only after its whole payload has arrived, so a truncated
  tail never registers a partial block.
- `extended` 1 means a sequence header: the chroma and the dimensions must match
  the decoder, otherwise it fails. `code` 0 sets full-frame semantics and marks
  the decoder as initialised; `code` 1 sets keep-previous semantics. If no
  sequence header arrives, the decoder defaults to keep-previous semantics.
- Parse anomalies (backwards sequence, out-of-range or duplicate block,
  unknown sequence code, dimension mismatch) increment a counter that is never
  reset (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:84-91`).

## 6. Client to host feedback

All feedback travels on the control stream
([luminalshine.md](luminalshine.md) section 12).

### 6.1 IDR request (0x0302) and reference invalidation (0x0301)

Both messages make the host send a hard full frame: IDR through
`request_idr_frame`, and invalidation because the PyroWave session maps it to the
same call (`src/pyrowave/backend.cpp:91-93`, `src/stream.cpp:1313-1320`,
`src/stream.cpp:1363-1382`). With the default full-frame mode the effect is
small, since the next frame is full anyway.

### 6.2 Frame FEC status (0x5502, client to host)

The type 0x5502 is also the host-to-client RGB LED message; the direction decides
the meaning (`src/stream.cpp:1322-1328`). The host accepts the client form only in
a PyroWave session and only with a payload of exactly 21 bytes
(`src/pyrowave/contract.h:97-101`, `src/stream.cpp:1329-1335`). All fields are big
endian (`src/pyrowave/contract.h:100-136`).

| Offset | Size | Field | Meaning |
| --- | --- | --- | --- |
| 0 | 4 | `frame_index` | Frame the report is about |
| 4 | 2 | `highest_received_sequence` | Highest RTP sequence number received |
| 6 | 2 | `next_contiguous_sequence` | Next sequence number expected contiguously |
| 8 | 2 | `missing_before_highest` | Packets missing below the highest received |
| 10 | 2 | `total_data_packets` | Data packets in this FEC block |
| 12 | 2 | `total_parity_packets` | Parity packets in this FEC block |
| 14 | 2 | `received_data_packets` | Data packets received |
| 16 | 2 | `received_parity_packets` | Parity packets received |
| 18 | 1 | `fec_percentage` | FEC percentage of the block |
| 19 | 1 | `block_index` | FEC block index within the frame |
| 20 | 1 | `block_count` | FEC blocks in the frame (1 to 4) |

The host discards a report when `total_data_packets` is 0, when received data
exceeds total data, when received parity exceeds total parity, when
`block_count` is outside 1 to 4, or when `block_index` is not below
`block_count` (`src/pyrowave/contract.h:121-135`). A report "has loss" when
`missing_before_highest` is non-zero or fewer packets arrived than were sent; it is
"unrecoverable" when fewer packets arrived than there are data packets
(`src/pyrowave/contract.h:87-95`).

The client sends one such report for each completed FEC block (per the host
comment at `src/stream.cpp:1322-1324`). Upstream moonlight-common-c at 874ac95
(not the Aurora fork) sends this message in exactly this layout to Sunshine
hosts: the struct is `SS_FRAME_FEC_STATUS` with big-endian fields and type
`0x5502` (`borealis:core/third_party/moonlight-common-c/src/Video.h:56-70`), it is filled in
`borealis:core/third_party/moonlight-common-c/src/RtpVideoQueue.c:93-109`, sent at `borealis:core/third_party/moonlight-common-c/src/RtpVideoQueue.c:346`, `598` and
`644` (vendored copy), enabled by the client feature flag announced at
`borealis:core/third_party/moonlight-common-c/src/SdpGenerator.c:270-274`, and transmitted unreliably on the control stream
(`borealis:core/third_party/moonlight-common-c/src/ControlStream.c:1401-1415`). The Aurora fork was not examined; whether it
differs from upstream here is **Unverified**.

### 6.3 Host reaction

The reaction happens only after the first frame has been transmitted and only
for the first loss report of each frame (`src/stream.cpp:1336-1339`):

| Condition | Action | Reference |
| --- | --- | --- |
| `pyrowaveAdaptiveFec` is 1 | Raise the FEC boost by 4 percentage points per newly affected frame, up to a boost of 16. The boost expires 10 s after the last loss. The boost only raises the effective FEC up to 25%; the effective value never falls below the host's base (`fec_percentage`, default 20, so by default the boost can add at most 5 points), and a base of 25 or more receives no boost. The video budget is scaled down so video plus FEC stays within the original envelope | `src/pyrowave/contract.h:206-244`, `src/stream.cpp:1341-1349`, `src/stream.cpp:2009-2018`, `src/config.cpp:848` |
| `pyrowaveAdaptiveBitrate` is 1 | Multiply the encoder's bitrate scale by 0.8 on a loss report, with a floor of 50% of the negotiated rate; recover by a factor of 1.0006 per frame (about 3.7% per second at 60 fps) while no loss is reported | `src/stream.cpp:1350-1352`, `src/pyrowave/pyrowave_encode.cpp:917-930`, `src/pyrowave/contract.h:183-205` |
| Unrecoverable loss and `pyrowave_refresh_interval` above 0 | Queue a hard full refresh (once per frame) | `src/stream.cpp:1355-1360` |

Both adaptations are session-local and compose
(`docs/pyrowave.md:63-68`). A client that does not send 0x5502, or that does not
send the opt-in attributes, simply gets no adaptation.

## 7. Client decoder requirements

These come from the vendored decoder, which is pinned to the Aurora revision.

### 7.1 Vulkan requirements

- Vulkan compute, with an optional fragment-shader inverse-DWT path selected by
  the `fragment_path` constructor argument (default false; the README example
  enables it)
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:185`,
  `third-party/pyrowave/README.md:91`,
  `third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:589-590`). The decoder's
  comments note that texel buffers are slow on mobile GPUs and that texture
  sampling is what mobile vendors optimise for
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:210-212`).
- Required subgroup operations: basic, vote, ballot, arithmetic, shuffle and
  shuffle-relative. A device without all of them makes construction throw
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:520-540`). The decoder also reads those properties from
  the core 1.1 structure on drivers that leave the 1.1 aggregate zeroed
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:522-528`).
- A subgroup size range compatible with 4 to 128 lanes, checked with
  `supports_subgroup_size_log2(prop13, true, 2, 7)`
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:567-568`).
- 8-bit storage: either `storageBuffer8BitAccess`, or a texel-buffer fallback
  when `maxTexelBufferElements` is at least 16 777 216; the texel-buffer path is
  also chosen on vendors other than AMD, Intel and NVIDIA when that limit is met,
  which covers Mali and Adreno devices that report it (**Unverified** on real
  devices)
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:570-587`).
- Optional: `shaderFloat16` selects the faster `_fp16` shader variants
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:1430`).
- The pinned decoder backfills the Vulkan 1.3 and 1.2 feature and property
  structures on Vulkan 1.1 and 1.2 drivers (it names Adreno and the Tegra X1)
  and flushes the host-written input buffers for non-coherent memory
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:544-564`, `third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:97-101`).
- The codec README states a Vulkan 1.3 device with subgroup support
  (`third-party/pyrowave/README.md:127-131`); the backfills are what the pinned
  decoder adds for older drivers. **Unverified:** which Android GPUs and drivers
  satisfy all of the above.

### 7.2 API outline

(`third-party/pyrowave/README.md:68-100`, `third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:19-108`,
`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:185-188`)

1. Construct exactly one `vk_allocator` (VMA singleton) after creating the device
   and before any `Encoder` or `Decoder`
   (`third-party/pyrowave/README.md:104-115`).
2. Create `PyroWave::Decoder(physicalDevice, device, width, height, chroma,
   fragment_path)`. The width, height and chroma must equal the values announced
   in ANNOUNCE and used by the host, or the sequence header is rejected
   (section 5.2).
3. Create a `PyroWave::DecoderInput` for the decoder.
4. Feed each received RTP payload (after FEC recovery) in order with
   `DecoderInput::push_data`.
5. Call `DecoderInput::resync_at_packet_boundary()` at a payload boundary that is
   known to follow lost data. The decoder's own comment warns against calling it
   at an arbitrary boundary, because records larger than one payload legitimately
   span payload boundaries (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:321-335`).
6. Call `DecoderInput::flush()` before decoding on non-coherent memory
   (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:97-101`).
7. Record `Decoder::decode(cmd, input, {viewY, viewCb, viewCr})` into a command
   buffer. The three views are single-plane storage image views of the output
   image. The host encoder reads 8-bit planes (R8) for SDR and 16-bit unorm planes
   (R16) for HDR (`src/pyrowave/pyrowave_encode.cpp:51`,
   `src/pyrowave/pyrowave_encode.cpp:184-186`); the codec README only describes
   the 8-bit layout (`third-party/pyrowave/README.md:132-136`), so the decoder-side
   plane format for HDR is **Unverified** here.
8. Convert YCbCr to RGB in the client: limited-range BT.709 for SDR,
   BT.2020 over PQ for HDR. The vendored fragment shader
   `third-party/pyrowave/shaders/yuv2rgb.frag.glsl` contains both matrices.
9. After each frame, check `keep_previous_frame()` and `awaiting_state_init()`.
   While `awaiting_state_init()` is true, keep requesting an IDR (0x0302).
10. Read `parse_anomalies()` between frames and surface the difference in client
    logs (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.h:84-91`).

### 7.3 Build requirements

A C++20 compiler (the headers use `std::span`, `std::format` and designated
initialisers), the Vulkan headers including `vulkan/vulkan_raii.hpp`, Python 3 and
`glslangValidator` at build time; the shaders are compiled and embedded during
the build (`third-party/pyrowave/README.md:36-43`). The dispatcher configuration
(`VULKAN_HPP_DISPATCH_LOADER_DYNAMIC`) must agree between the module and the host
application, and only one VMA implementation may be linked
(`third-party/pyrowave/README.md:116-126`). `PYROWAVE_PRECISION` (default 1)
selects fp16 or fp32 wavelet precision
(`third-party/pyrowave/README.md:127-131`).

## 8. Practical Android limitations

- **The client cannot ask for less bitrate.** The default target is 700 Mbps of
  compressed video; FEC, audio and headers come on top
  (`src/rtsp.cpp:1694`, `docs/pyrowave.md:63-64`). The adaptive controller backs
  off to a floor of 50% (350 Mbps at the default) and not further
  (`src/pyrowave/contract.h:183-205`). Reducing the rate means changing the host
  configuration, either globally (`pyrowave_bitrate_mbps`) or per client through
  `config_overrides` (section 4, **Unverified** end to end). Proposal P2 in
  [luminalshine.md](luminalshine.md) section 20 asks the host to honour a client
  cap.
- **Minimum frame rate.** With the default bitrate the host rejects frame rates
  below roughly 23 fps at Moonlight Android's LAN packet size (1392), and below
  roughly 32 fps when the client classifies the connection as remote and
  negotiates 1024 (section 4.1).
- **No stock PyroWave support in the client stack.** The stock Moonlight client
  negotiates a regular codec (`docs/pyrowave.md:28-29`). Moonlight Android decodes
  with `MediaCodec` through a renderer typed `MediaCodecDecoderRenderer`
  (`borealis@5816473bc:apps/android/app/src/main/java/com/limelight/Game.java:169`) and its
  native library registers decoder callbacks in
  `borealis:apps/android/app/src/main/jni/moonlight-core/callbacks.c:404-410`.
  PyroWave needs a new Vulkan decode and present path and the format-3 and
  profile-bit handling, which come from the pinned `moonlight-common-c` fork;
  upstream moonlight-common-c at 874ac95 contains no PyroWave support (a search of
  its `src` for `pyrowave` finds nothing). The 0x5502 feedback message already
  exists upstream (section 6.2); the host drops it unless the session is PyroWave.
- **GPU and driver support is unproven on Android.** The decoder has fallbacks for
  Adreno and for 8-bit storage on non-desktop vendors
  (`third-party/pyrowave/src/pyrowave/pyrowave_decoder.cpp:544-585`), but no document records a tested Android
  device. **Unverified:** driver support, decode time, thermal behaviour and
  power cost on phones.
- **HDR output** requires a Vulkan present path that can display BT.2020 PQ.
  **Unverified:** feasibility on Android.
- **No certified remote session.** The host's own validation used 128 by 128
  textures and the pinned Aurora decoder, not a 1080p throughput measurement
  (`docs/pyrowave.md:131-147`).
- **Licensing** must be reviewed before shipping (section 1).

## 9. Upstream roadmap hints

From the host's own documentation:

- The client's phase-offset extension is not applied to the host capture clock
  yet (`docs/pyrowave.md:29-30`).
- A full 10 Gbps transport and client-coordinated fragmentation are a separate
  milestone, and the current four-block GameStream transport cannot carry every
  bitrate and frame-rate combination (`docs/pyrowave.md:69-73`,
  `docs/pyrowave.md:160-161`).
- There is no replenishment-capability attribute yet, so conditional
  replenishment is certified only for the pinned Aurora and `moonlight-common-c`
  revisions (`docs/pyrowave.md:84-87`).
- The feature stays opt-in until live acceptance on NVIDIA, AMD and Intel and a
  measurement of bitrate, frame loss, latency and colour fidelity are done
  (`docs/pyrowave.md:124-129`, `docs/pyrowave.md:149-161`). The release notes of
  26.10.0-beta.1 report that the official Windows build now includes it,
  experimental and opt-in (reported in the 26.10.0-beta.1 release notes (2026-10-20); not yet verified in source).

## 10. Client obligations for a Borealis PyroWave client

These extend the obligations in [luminalshine.md](luminalshine.md) section 18.

1. Offer PyroWave only when `ServerCodecModeSupport` has the profile bit for the
   mode the user selected (section 3.1) and the SDP contains
   `PYROWAVE/90000` (section 3.2).
2. Send `bitStreamFormat:3`, `dynamicRangeMode`, `chromaSamplingType`,
   `clientViewportWd`, `clientViewportHt`, `maxFPS` and `packetSize` consistent
   with the profile, the same width, height and frame rate as in `/launch`, and a
   frame rate high enough for the host's frame budget (sections 4 and 4.1).
3. For HDR send `hdrMode=1` on `/launch` and `dynamicRangeMode=1` in ANNOUNCE.
4. Send the optional adaptation attributes only as exactly `0` or `1`, once each,
   and only if the client really sends 0x5502 reports (section 6).
5. Never expect a 4:4:4 session to degrade silently; treat a failed 4:4:4 start as
   a failed start.
6. Do not rely on the host bitrate; display the real network cost to the user and
   tell them that the host operator sets the rate.
7. Create the decoder at the announced size and chroma, feed whole RTP payloads,
   signal lost payloads to the decoder, and request an IDR while
   `awaiting_state_init()` is true (section 7).
8. Send one 0x5502 report per completed FEC block, in the layout of section 6.2,
   with every field validated against the ranges the host enforces.
9. Do not assume KEEP frames exist. Handle code 0 and code 1 sequence headers
   either way, and never request replenishment behaviour that the host did not
   enable.
