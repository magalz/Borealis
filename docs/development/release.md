# Release procedure

Step-by-step procedure for releasing the Android app. Rules and rationale:
[../policies/release.md](../policies/release.md).

The owner performs the manual steps. AI agents never tag, approve or merge.

## 1. Prepare

1. All intended PRs are merged and `main` is green.
2. Decide the version using SemVer (for example `0.3.0`). The tag is the
   version; there is no version-bump commit.
3. Check the compatibility notes: the Moonlight version in the tree and the
   LuminalShine versions tested.
4. Update `PRIVACY.md`, `NOTICE` and the docs if anything user-visible or
   legal changed since the last release.

## 2. Preview the changelog

```sh
git cliff --unreleased --tag android-v0.3.0
```

Check that the sections (Added, Changed, Fixed, Security, Documentation,
Upstream) read well.

**First release.** The upstream Moonlight history is in this repository (the
subtree is not squashed), so the first changelog must start after the import:

```sh
git cliff f397cda5.. --tag android-v0.1.0
```

**Upstream commits.** Commits that arrive through upstream syncs are parsed
like any other. Most Moonlight commits are not Conventional Commits and are
filtered out (`filter_unconventional` and `filter_commits` in `cliff.toml`),
but upstream commits that happen to start with `feat` or `fix` can appear. Read
the generated notes before publishing and treat the "Upstream" group (the
`sync:` commits) as the reliable record of what came from Moonlight.

Fix poor commit messages by editing PR titles before the merge, not by rewriting history. Generate the final file as part of the
release workflow.

## 3. Tag

From an up-to-date `main`:

```sh
git switch main && git pull
git tag -a android-v0.3.0 -m "Borealis Android 0.3.0"
git push origin android-v0.3.0
```

Pre-releases use a suffix: `android-v0.3.0-rc.1`.

## 4. CI release workflow

Pushing the tag starts the release workflow, which:

1. Builds the **unsigned** release artifacts from the tagged commit.
2. **Waits for manual approval** of the GitHub Environment `release`. Review
   the run, then approve it.
3. After approval, signs the artifacts with the owner-held key (available only
   in the `release` environment).
4. Generates the SHA-256 checksums and the CycloneDX SBOM of the **signed**
   artifacts.
5. Creates the build-provenance attestations for the signed artifacts.
6. Generates the changelog for the release notes with git-cliff.
7. Creates the GitHub Release (draft or pre-release first) with the APK(s),
   AAB, checksums, SBOM and release notes.

Do not approve if the run, the commit or the artifacts look wrong.

## 5. Verify

- Download the release and check it:

  ```sh
  sha256sum -c SHA256SUMS
  gh attestation verify <artifact> --repo magalz/Borealis
  ```

- Check the APK signature with `apksigner verify --print-certs`.
- Install on at least one phone/tablet and one TV device and stream from a
  LuminalShine host.
- Publish the GitHub Release when satisfied.

## 6. Stores

Store uploads are manual at first; automation comes later.

- **Google Play:** upload the AAB signed with the upload key; Play App Signing
  re-signs for distribution. Use a staged rollout.
- **Amazon Appstore:** upload the signed APK.
- **Obtainium:** follows GitHub Releases automatically.

Publish to GitHub Releases before any store.

## 7. After the release

- Confirm the release notes and the changelog match.
- Update docs that mention the "latest" version if needed.
- Close the related milestone or issues.

## If something is wrong

- Do not move or re-use a published tag. Fix on `main` and release a patch
  version.
- For a security problem follow [SECURITY.md](../../SECURITY.md); publish an
  advisory once the fixed release is available.
