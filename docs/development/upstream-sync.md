# Upstream cherry-picks

Procedure for bringing an upstream fix into Borealis. The rules are in
[../policies/upstream-sync.md](../policies/upstream-sync.md). The decision is in
[ADR 0054](../adr/0054-declare-independence-from-moonlight.md) (2026-10-15).

The subtree pull, the hook inventory and the moonlight-common-c fork-tag
procedure ended with ADR 0054. This page no longer describes them.

Upstream fixes arrive only by cherry-pick. There are two parts: moonlight-android
(the Android app under `apps/android/`) and moonlight-common-c (the native
protocol library under `core/third_party/moonlight-common-c/`).

## 1. Prepare

1. Make sure `main` is up to date and your working tree is clean.
2. Keep the `moonlight-android` remote configured. Add it once if it is missing:

   ```sh
   git remote add moonlight-android https://github.com/moonlight-stream/moonlight-android.git
   ```

3. Fetch and look at what changed since the last harvest:

   ```sh
   git fetch moonlight-android --tags
   git log --oneline <last-harvested>..<upstream-ref>
   ```

   The last harvested commit is `b48494cb` (moonlight-android 12.2, the same
   commit as the Borealis subtree at the time of ADR 0054).
4. Read the upstream release notes. Note changes to dependencies, Gradle or SDK
   versions, and to the moonlight-common-c version.
5. Check the [compatibility table](#compatibility-table). Add a row if the
   harvest changes what was tested.

## 2. moonlight-android

1. Create a branch for the harvest from `main`.
2. Pick each commit with the subtree strategy option:

   ```sh
   git cherry-pick -Xsubtree=apps/android <sha>
   ```

   If a commit does not apply cleanly, port the change by hand into the
   Borealis file.
3. Resolve conflicts by keeping the Borealis code and taking the fix. Do not
   reformat or restyle whole files, so the diff stays reviewable.
4. Keep the duplicated toolchain values in step. When a harvest changes one of
   them, change the other in the same PR:
   - AGP: `apps/android/build.gradle` and `agp` in
     `apps/android/gradle/libs.versions.toml`.
   - `compileSdk`: `apps/android/app/build.gradle` and `BorealisBuild` in
     `apps/android/build-logic`.
   - NDK: `ndkVersion` in `apps/android/app/build.gradle`. If it changes, update
     the SDK install command in [building](building.md).
5. If new upstream code adds Android Lint warnings, fix them. Regenerate the
   baseline only for upstream findings, with `./gradlew :app:updateLintBaseline`,
   and commit the baseline in its own commit. Check that the baseline has no
   machine-specific paths.
6. Run the checks from `apps/android/` (see [building](building.md)):

   ```sh
   ./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test assembleDebug licensee
   ```

7. Open one PR per harvest. The description lists the upstream commits, what
   was ported by hand, conflicts and how they were resolved, and the test
   results. The owner reviews and merges it.
8. If a dependency or licence changed, update [NOTICE](../../NOTICE) and check
   the [dependency policy](../policies/dependencies.md).

## 3. moonlight-common-c

The vendored copy lives at `core/third_party/moonlight-common-c/`. Its README,
[core/third_party/README.md](../../core/third_party/README.md), records the
source commit, the licences, the Borealis patch and the upstream commits that
were and were not harvested.

1. Add the upstream remote once:

   ```sh
   git remote add upstream-common-c https://github.com/moonlight-stream/moonlight-common-c.git
   git fetch upstream-common-c
   ```

2. Export the commits to harvest as patches. The base is the last commit
   recorded in `core/third_party/README.md`:

   ```sh
   git format-patch -o <patch-dir> <base>..<sha>
   ```

3. Apply the patches into the vendored path:

   ```sh
   git am --directory=core/third_party/moonlight-common-c <patch-dir>/*.patch
   ```

4. Update `core/third_party/README.md`: the harvested commits, the source commit
   and the licence lines if they changed.
5. Build and test `borealis-core` from `core/`:

   ```sh
   cmake --preset dev && cmake --build --preset dev && ctest --preset dev
   ```

6. Check the stream path on a device, if the change touches it (see
   [device-validation](device-validation.md)).
7. Update [NOTICE](../../NOTICE) and the native SBOM entry for moonlight-common-c
   if its version or licence changed.

## Compatibility table

Record each harvest or tested pairing here. Rows before ADR 0054 name the
moonlight-common-c fork tag, which is now an archive. From 2026-10-15 the
moonlight-common-c column names the vendored source commit.

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| initial import | 12.2 | fork `magalz/moonlight-common-c` `borealis`, upstream base `874ac95` plus the first full frame deadline patch (tag `borealis/874ac95-1`) | not yet tested | Baseline import. |
| `phase/1-luminalshine-compat` at `d6b3116e` | 12.2 | tag `borealis/874ac95-1` | 26.09.1 | Galaxy S22 (Android 16) and Google TV Streamer (Android 14); results in [device-validation.md](device-validation.md) section 6. |
