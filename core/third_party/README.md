# Vendored third-party source

This directory holds source that Borealis owns and builds itself. It is not a mirror: once a file is
here, a change to it is a Borealis change, recorded in this README.

## moonlight-common-c

The Moonlight streaming protocol library, compiled into `libmoonlight-core.so` by
`apps/android/app/src/main/jni/moonlight-core/Android.mk`. ENet and nanors live inside it as plain
directories, as upstream lays them out.

| Directory | Source | Revision | Licence |
| --- | --- | --- | --- |
| `moonlight-common-c/` | [magalz/moonlight-common-c](https://github.com/magalz/moonlight-common-c) fork, tag `borealis/874ac95-1` | `591bddfa980406cf89ee5c9b150579a887cb160e` | GPL-3.0 (`LICENSE.txt`, with the section 7 app store exception added upstream in `5a26329`) |
| `moonlight-common-c/enet/` | [cgutman/enet](https://github.com/cgutman/enet) | `aca87840b57f045a1f7f9299e4b1b9b8e2a5e2f1` (`v1.3.15-118-gaca8784`) | MIT (`enet/LICENSE`) |
| `moonlight-common-c/nanors/` | [sleepybishop/nanors](https://github.com/sleepybishop/nanors) | `b1e3c22ca0cdc0bb83e3cd6ed1a2fc77869ed99a` | MIT (`nanors/LICENSE`) |

The fork revision `591bddf` is upstream `874ac9548f1bd6f095ef2b435c42cdde460e7821` (`Add
LI_CTYPE_STEAM controller type`) plus one Borealis commit. The `.github/` folders and the nested
`.gitmodules` of the three source trees were dropped; everything else is byte for byte what the
sources contain (`.gitattributes` marks the directory `-text` so line endings are kept as they are).

### Borealis patch

- `src/VideoStream.c` (fork commit `591bddf`): waits longer for the first full video frame and
  enforces the deadline during silence ([ADR 0048](../../docs/adr/0048-fork-moonlight-common-c.md)).
  Every changed line carries a `BOREALIS:` comment.

### Changing these files

- Edit them like any Borealis source, with a Conventional Commit, and mark every change in the C
  code with a `BOREALIS:` comment. Never reformat vendored files: formatters and linters skip this
  directory on purpose, and a reformat would hide the real changes and break later cherry-picks.
- Upstream fixes arrive only by cherry-pick, never by a merge. In a clone of
  `moonlight-stream/moonlight-common-c`:

  ```sh
  git format-patch <base>..<commit> -o /tmp/patches
  # in Borealis, one commit per upstream commit, upstream author kept:
  git am --directory=core/third_party/moonlight-common-c /tmp/patches/*.patch
  ```

  Then append `Harvested from moonlight-stream/moonlight-common-c@<full sha>.` to each commit
  message and list the commit below.
- Compare the exported symbols of `libmoonlight-core.so` before and after (`llvm-nm -D
  --defined-only`) and explain every difference in the commit that causes it.

### Harvested

Upstream commits applied after the fork base `874ac95`, oldest first. Each is a commit of its own
here and its message names the upstream SHA.

| Upstream commit | Subject | Touches |
| --- | --- | --- |
| `d85371c` | Fix RFI after a multi-block frame loss | `src/RtpVideoQueue.c` |
| `be43885` | Fix handling of a partially dropped IDR frame | `src/VideoDepacketizer.c` |
| `62e0663` | Don't speculatively report losses if RFI is disabled | `src/RtpVideoQueue.c` |
| `5a26329` | Add section 7 exception for app store distribution | `LICENSE.txt` |
| `f900dd4` | Add MODIFIER_EXTENDED flag | `src/InputStream.c`, `src/Limelight.h` |
| `e2722dc` | Fix uninitialized stack data in the periodic ping payload | `src/ControlStream.c` |
| `5ec8397` | Fix memory leak if duplicate RTSP options are inserted | `src/RtspParser.c` |

None of them touches `src/VideoStream.c`, so they do not interact with the Borealis patch. They add
no exported symbol to `libmoonlight-core.so`.

### Not harvested

Upstream commits known and deliberately left out.

| Upstream commit | Subject | Reason |
| --- | --- | --- |
| `aeba988` | Rework RTSP parser to fix many spec compliance issues | Deferred by the owner until it has seen wider use. |
