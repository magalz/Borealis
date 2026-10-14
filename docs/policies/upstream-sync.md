# Upstream sync policy

Borealis tracks [moonlight-android](https://github.com/moonlight-stream/moonlight-android)
through a git subtree at `apps/android/`. This page is the policy; the
procedure is in [../development/upstream-sync.md](../development/upstream-sync.md).

## Principles

1. **Stay close to upstream.** Borealis code lives in
   `apps/android/borealis/` and `apps/android/build-logic/`. Changes inside
   upstream files are small hooks marked `// BOREALIS:`.
2. **Never reformat upstream files.** No whitespace, import-order or style
   changes. Formatters, linters and the editor config exclude upstream paths.
3. **One PR per Moonlight release.** Sync on a branch named
   `sync/moonlight-<version>` and open a PR. Do not mix a sync with feature
   work.
4. **Sync with the subtree command**, not by copying files:
   `git subtree pull --prefix=apps/android -m "sync: moonlight-android <version>" moonlight-android <tag-or-commit>`
   (prefer the upstream release tag over `master`).
5. **The `moonlight-common-c` submodule stays at its upstream path**
   (`apps/android/app/src/main/jni/moonlight-core/moonlight-common-c`), but its
   URL is the Borealis fork `magalz/moonlight-common-c` (branch `borealis`,
   [ADR 0048](../adr/0048-fork-moonlight-common-c.md)). Its pointer moves only
   with an upstream sync or a reviewed fork patch, unless a security fix
   requires moving it earlier (noted in the PR). Every commit the pointer
   references carries a fork tag `borealis/<upstream short sha>-<n>`; fork tags
   are never rewritten. Fork patches are offered upstream.
6. **Compatibility is recorded.** Every sync PR adds a row to the compatibility table in
   [../development/upstream-sync.md](../development/upstream-sync.md)
   (Moonlight and LuminalShine versions tested together).
7. **Offer fixes upstream.** If a Borealis fix belongs in Moonlight, send it
   there too, so the hook can shrink or disappear.

## What gets synced

- Upstream releases (tagged versions) are synced.
- Upstream commits between releases are synced only for security fixes or when
  a Borealis change depends on them.
- Upstream changes that conflict with a Borealis hook are resolved in favour
  of keeping the hook minimal; the PR explains the resolution.

## Checks for a sync PR

- Full CI is green (format and lint exclude upstream files; the Android Lint
  baseline is updated only for upstream warnings).
- All `// BOREALIS:` hooks still exist and their tests pass.
- Characterization tests for touched upstream behaviour pass.
- The `sync:` commit appears under "Upstream" in the changelog.

## Licences

Upstream is GPL-3.0. A sync that brings new third-party components updates
[NOTICE](../../NOTICE) and passes the
[dependency policy](dependencies.md).
