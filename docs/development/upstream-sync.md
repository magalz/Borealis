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

- If upstream moved `moonlight-common-c`, update the submodule so that it
  points at the commit upstream chose, at the same path:
  `git submodule update --init --recursive`.
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

## Compatibility table

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| initial import | 12.2 | pointer recorded in the submodule | not yet tested | Baseline import. |
