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
git subtree pull --prefix=apps/android moonlight-android master
```

Use the upstream tag or commit for the release when `master` has moved past
it. Keep the default merge behaviour; do not squash away the upstream history
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

## 5. Re-run the checks

From `apps/android/`:

```sh
./gradlew assembleNonRootDebug
./gradlew spotlessCheck detekt lint testDebugUnitTest
```

- New Android Lint warnings that come from upstream code go into the lint
  baseline; warnings in Borealis code are fixed.
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
