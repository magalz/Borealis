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

Upstream commits applied after the fork base `874ac95`. None yet.

### Not harvested

Upstream commits known and deliberately left out. None yet.
