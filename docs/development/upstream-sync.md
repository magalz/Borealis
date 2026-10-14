# Upstream sync procedure

Step-by-step procedure for bringing a new moonlight-android release into
`apps/android/`. Rules and rationale: [../policies/upstream-sync.md](../policies/upstream-sync.md).

One PR per Moonlight release. Never reformat upstream files at any step.

## 1. Prepare

1. Make sure `main` is up to date and your working tree is clean.
2. Add the upstream remote once:

   ```sh
   git remote add moonlight-android https://github.com/moonlight-stream/moonlight-android.git
   ```

3. Fetch and look at what changed:

   ```sh
   git fetch moonlight-android --tags
   git log --oneline <current-version-tag>..<new-version-tag>
   ```

4. Read the upstream release notes. Note changes to the submodule pointer
   (`moonlight-common-c`), dependencies, Gradle or SDK versions, and files
   that carry a `// BOREALIS:` hook.
5. Create the branch:

   ```sh
   git switch -c sync/moonlight-<version> main
   ```

## 2. Pull

```sh
git subtree pull --prefix=apps/android -m "sync: moonlight-android <version>" moonlight-android <tag-or-commit>
```

Prefer the upstream release tag over `master`, so that the sync is exactly the
release. Keep the default merge behaviour; do not squash away the upstream history
unless the owner decides otherwise.

## 3. Resolve conflicts

Conflicts happen where a Borealis hook sits in an upstream file.

1. Take the upstream version of the file.
2. Re-apply the Borealis hook, with its `// BOREALIS:` marker, as small as
   possible. If upstream changed the surrounding code, adapt the hook rather
   than the upstream lines.
3. Do not fix style, imports or whitespace in upstream files.
4. If a hook no longer fits, move the logic into `apps/android/borealis/` and
   shrink the hook, or propose the change upstream.
5. Search for all hooks to make sure none got lost:

   ```sh
   git grep -n "BOREALIS:" -- apps/android
   ```

   Compare with the list from before the sync.

## 4. Submodules and dependencies

- If upstream moved `moonlight-common-c`, rebase the Borealis fork patches
  onto the commit upstream chose and point the submodule at the new tagged fork
  commit, at the same path (see
  [the moonlight-common-c fork](#moonlight-common-c-fork)).
- If prebuilt libraries or dependency versions changed, update
  [NOTICE](../../NOTICE) and check the
  [dependency policy](../policies/dependencies.md).
- Update the native SBOM, `apps/android/sbom/native-components.cdx.json`
  (CycloneDX JSON, shipped with every release), whenever the native components
  change. Gradle cannot see them, so nothing fails if it goes stale:
  - `moonlight-common-c`: the commit from `git submodule status --recursive`
    (also its `purl` and `version`), plus the nested `enet` and `nanors`
    commits.
  - OpenSSL, Opus and minisdl: the versions under
    `apps/android/app/src/main/jni/moonlight-core` (`opensslv.h`, the
    `moonlight-mobile-deps` build, [NOTICE](../../NOTICE)).
  - `libc++_shared`: the NDK version (`ndkVersion` in `app/build.gradle`).
  - `borealis-core`: the version in `core/CMakeLists.txt`.
  - Keep the licences equal to [NOTICE](../../NOTICE). The Gradle dependencies
    need no manual step: the SBOM is generated from the build, and
    `./gradlew licensee` fails on a new licence (see
    [building](building.md#coverage-licences-and-sbom)).
- Keep the duplicated toolchain values in step. Upstream declares them in its
  own files and Borealis repeats them in build logic, so a sync that bumps one
  side must bump the other in the same PR:
  - AGP: `apps/android/build.gradle` (`com.android.tools.build:gradle:<x>`)
    and `agp` in `apps/android/gradle/libs.versions.toml`. Keep the bundled
    Kotlin version in the catalog equal to the one AGP ships with.
  - `compileSdk`: `apps/android/app/build.gradle` and `BorealisBuild` in
    `apps/android/build-logic`.
  - NDK: `ndkVersion` in `apps/android/app/build.gradle`; update the SDK
    install command in [building](building.md) if it changes.

## 5. Re-run the checks

From `apps/android/`:

```sh
./gradlew assembleNonRootDebug
./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test licensee
```

- Lint treats warnings as errors and the baseline matches on message text, so
  new upstream code, new lint checks from an AGP bump, or changed version
  messages can fail `lint` after a sync. When every new finding comes from
  upstream code, regenerate the baseline deliberately with
  `./gradlew :app:updateLintBaseline`, review the diff of
  `apps/android/app/lint-baseline.xml`, and commit it in its own commit
  (`build: refresh lint baseline for moonlight-android <version>`). Findings in
  Borealis code are fixed, never baselined.
- The baseline must work on every OS (CI runs on Linux, development on
  Windows). Before committing a regenerated baseline, remove entries whose
  location or message depends on the machine: absolute paths (for example jars
  in the Gradle cache), `local.properties`, or Windows `\` paths inside
  messages. Suppress those findings with a path-based `<ignore>` in
  `apps/android/app/lint.xml` instead (marked `<!-- BOREALIS: -->`). CI fails
  if the baseline contains absolute paths or `local.properties` entries.
- Characterization and hook tests must pass. A failing one means the hook or
  the assumption changed; fix the hook, not the test, unless upstream
  behaviour legitimately changed (then update the test and say why).
- Build and smoke-test against a LuminalShine host if one is available.

## 6. Update compatibility records

Add a row to the [compatibility table](#compatibility-table) below
(Moonlight version, `moonlight-common-c` commit, tested LuminalShine
versions). Record anything that no longer works.

## 7. Commit and open the PR

- Commit message for the merge or follow-up: `sync: moonlight-android <version>`
  (type `sync` shows under "Upstream" in the changelog).
- Fixes needed after the pull are separate commits (`fix:`, `chore:`).
- PR description: upstream version range, notable upstream changes, conflicts
  and how they were resolved, hooks changed, compatibility table update, and
  test results.
- The owner opens, reviews and merges the PR once CI is green.

## 8. After merge

- Delete the sync branch.
- Check that the next release's changelog lists the sync under "Upstream".

## moonlight-common-c fork

The `moonlight-common-c` submodule is cloned from the Borealis fork,
`https://github.com/magalz/moonlight-common-c` (branch `borealis`), declared in
`.gitmodules`. The reason and the options considered are in
[ADR 0048](../adr/0048-fork-moonlight-common-c.md). The fork holds the upstream
history plus the Borealis patches on top, nothing else.

### Patches

| Patch | File | Upstream base | Purpose |
| --- | --- | --- | --- |
| Wait longer for the first full video frame and enforce the deadline during silence | `src/VideoStream.c` | `874ac95` | Adds `FIRST_FULL_FRAME_TIMEOUT_SEC 25`, measured from the first datagram of any size, for the `ML_ERROR_NO_VIDEO_FRAME` (-101) deadline, and evaluates it also when the receive times out, so a runt datagram followed by silence ends the stream. `FIRST_FRAME_TIMEOUT_SEC 10` still bounds the wait for any video traffic (-100). Needed because LuminalShine can need up to about 18 s after the client attaches to send the first frame and sends a 1-byte runt datagram about 8 s in, which leaves upstream's 10 s deadline no margin ([host contract](../host-contract/luminalshine.md), section 10.1). The worst case to terminate is about 35 s (up to 10 s waiting for any traffic, then 25 s), below the 45 s Android watchdog. |

Every changed line carries a `BOREALIS:` comment. Offer each patch upstream.
moonlight-common-c has no C test harness, so patches are verified by build,
review and device logs (`Received first video packet after N ms`).

### Working on the fork

1. Clone or enter the submodule and check out the `borealis` branch:

   ```sh
   cd apps/android/app/src/main/jni/moonlight-core/moonlight-common-c
   git fetch origin
   git switch borealis
   ```

2. Add a patch as one commit on `borealis` that touches only the files it
   needs, in upstream commit style. Match the line endings of the file.
3. Push the fork commit and tag it `borealis/<upstream short sha>-<n>`
   (for example `borealis/874ac95-1`; `<n>` counts the fork states for that
   upstream commit). Tags are never rewritten or force-pushed.
4. Only then commit the new submodule pointer in the Borealis tree. The
   Borealis pointer may reference only a pushed, tagged fork commit.

### Rebasing the patches onto a new upstream commit

When moonlight-android moves its submodule pointer:

1. Add the upstream remote once:
   `git remote add upstream https://github.com/moonlight-stream/moonlight-common-c.git`.
2. `git fetch upstream` and note the new upstream commit `<new>` that
   moonlight-android points at.
3. Create the new fork branch state without touching the old tags:
   `git switch -c rebase-<new short sha> borealis`, then
   `git rebase --onto <new> <old upstream commit>`.
4. Resolve conflicts keeping each patch minimal with its `BOREALIS:` markers;
   check that every patch in the table above still applies and works.
5. Fast-forward `borealis` to the result, push it, and tag the commit
   `borealis/<new short sha>-1`.
6. Update the patch table (upstream base), the `moonlight-common-c` row of the
   compatibility table, [NOTICE](../../NOTICE) and the SBOM, then point the
   submodule at the tagged commit.

## Compatibility table

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| initial import | 12.2 | fork `magalz/moonlight-common-c` `borealis`, upstream base `874ac95` plus the first full frame deadline patch (tag `borealis/874ac95-1`) | not yet tested | Baseline import. |
| `phase/1-luminalshine-compat` at `d6b3116e` | 12.2 | tag `borealis/874ac95-1` | 26.09.1 | Galaxy S22 (Android 16) and Google TV Streamer (Android 14); results in [device-validation.md](device-validation.md) section 6. |
