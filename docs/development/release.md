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
   legal changed since the last release. Check that
   `apps/android/sbom/native-components.cdx.json` still matches the native
   components in the APK (see [upstream-sync.md](upstream-sync.md)) and that
   `./gradlew licensee` passes.

## 2. Preview the changelog

```sh
git cliff --unreleased --tag android-v0.3.0
```

Check that the sections (Added, Changed, Fixed, Security, Documentation,
Upstream) read well.

**First release.** The upstream Moonlight history is in this repository (the
subtree is not squashed), so the first changelog must start after the import.
The release workflow does this itself; to preview it:

```sh
git cliff f397cda5.. --tag android-v0.1.0
```

**Upstream commits.** Commits that arrive through upstream syncs are parsed
like any other. Most Moonlight commits are not Conventional Commits and are
filtered out (`filter_unconventional` and `filter_commits` in `cliff.toml`),
but upstream commits that happen to start with `feat` or `fix` can appear. Read
the generated notes before publishing and treat the "Upstream" group (the
`sync:` commits) as the reliable record of what came from Moonlight.

Fix poor commit messages by editing PR titles before the merge, not by
rewriting history. The workflow generates the release notes from the same
configuration; it does not commit a `CHANGELOG.md`.

## 3. Tag

From an up-to-date `main`:

```sh
git switch main && git pull
git tag -a android-v0.3.0 -m "Borealis Android 0.3.0"
git push origin android-v0.3.0
```

The workflow accepts only plain `android-vMAJOR.MINOR.PATCH` tags on a commit
that is reachable from `main`. Pre-release tags such as `android-v0.3.0-rc.1`
are rejected for now: the Gradle version derivation skips them (see
`apps/android/build-logic`), so the APK would carry the previous release's
version.

## 4. CI release workflow

Pushing the tag starts
[`.github/workflows/release.yml`](../../.github/workflows/release.yml). It has
two jobs.

**`build`** (no secrets, read-only token):

1. Checks out the tagged commit with submodules and full history, validates
   the tag and that the commit is on `main`.
2. Runs `./gradlew assembleNonRootRelease bundleNonRootRelease :app:cyclonedxDirectBom`
   in `apps/android` (JDK 17, Gradle build cache disabled). The APK is a
   universal, **unsigned** `nonRoot` release build; its version must equal the
   tag.
3. Collects the unsigned APK and AAB, the Gradle SBOM
   (`app/build/reports/sbom/borealis-android.cdx.json`) and the hand-maintained
   native SBOM (`apps/android/sbom/native-components.cdx.json`).
4. Generates the release notes with git-cliff (`orhun/git-cliff-action`) for
   the range from the previous `android-vX.Y.Z` tag to this tag. The first
   release starts at `f397cda5`, the last upstream commit before the Borealis
   import.
5. Uploads everything as the artifact `borealis-android-unsigned` (7 days).

**`publish`** (`environment: release`):

1. **Waits for manual approval** of the `release` environment. Review the run,
   the tagged commit and the unsigned artifacts first. The environment secrets
   are released only after approval.
2. Downloads the unsigned artifacts and locates `apksigner` and `zipalign`
   (build-tools `37.0.0`).
3. Decodes the keystore from `BOREALIS_KEYSTORE_BASE64` into a temporary file.
4. Signs the APK with `apksigner` (APK Signature Scheme v2 and v3; v1 and v4
   off, because minSdk is 28) and the AAB with `jarsigner`.
5. Verifies: `zipalign -c -P 16 4`, `apksigner verify` (v2 and v3 must both be
   `true`), `jarsigner -verify`, and that the APK signer's certificate SHA-256
   equals the keystore's.
6. Removes the keystore file with `shred -u`, always, also when an earlier step
   failed.
7. Writes `SHA256SUMS` over the signed APK, the signed AAB and both SBOMs and
   checks it.
8. Creates build-provenance attestations (`actions/attest-build-provenance`)
   for those four files.
9. Creates a **draft** GitHub Release for the tag (`gh release create --draft`)
   with the generated notes and these assets: `borealis-android-X.Y.Z.apk`,
   `borealis-android-X.Y.Z.aab`, `SHA256SUMS`,
   `borealis-android-X.Y.Z.cdx.json` (Gradle dependencies of the release APK)
   and `borealis-android-native-X.Y.Z.cdx.json` (native components).

Do not approve if the run, the commit or the artifacts look wrong. Per-ABI APK
splits are not built yet; the one APK contains all four ABIs.

### One-time setup (owner)

Create the GitHub Environment `release` (Settings, Environments) with required
reviewers, and add these **environment secrets** (not repository secrets, so
that only the approved `publish` job can read them):

| Secret | Content |
| --- | --- |
| `BOREALIS_KEYSTORE_BASE64` | The release keystore, base64 encoded on one line (`base64 -w0 release.jks`). |
| `BOREALIS_KEYSTORE_PASSWORD` | Keystore password. |
| `BOREALIS_KEY_ALIAS` | Alias of the signing key. |
| `BOREALIS_KEY_PASSWORD` | Key password. |

The workflow uses the built-in `GITHUB_TOKEN` (`contents: write`,
`id-token: write` and `attestations: write`, in the `publish` job only). Never
add the keystore or its passwords anywhere else.

## 5. Verify

- Download the draft release's assets and check them:

  ```sh
  sha256sum -c SHA256SUMS
  gh attestation verify borealis-android-0.3.0.apk --repo magalz/Borealis
  ```

- Check the APK signature and certificate; it must be the owner's release
  certificate:

  ```sh
  apksigner verify --verbose --print-certs borealis-android-0.3.0.apk
  ```

- Install on at least one phone/tablet and one TV device and stream from a
  LuminalShine host.
- Review the notes, then publish the draft release when satisfied.

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
