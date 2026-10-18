# Release procedure

Step-by-step procedure for releasing the Android app. Rules and rationale:
[../policies/release.md](../policies/release.md).

The owner performs the manual steps. AI agents never tag, approve or merge.

## 1. Prepare

1. All intended PRs are merged and `main` is green.
2. Decide the version using SemVer (for example `0.3.0`). The tag is the
   version; there is no version-bump commit.
3. Update the compatibility note, [`.github/release-compatibility.md`](../../.github/release-compatibility.md):
   the LuminalShine version tested, the Moonlight version in the tree, the
   moonlight-common-c base and patches (`core/third_party/README.md`) and the known host
   issues. The release workflow appends this file to the release notes as it
   is, so keep it written for readers of the release page (absolute links).
4. Update `PRIVACY.md`, `NOTICE` and the docs if anything user-visible or
   legal changed since the last release. Check that
   `apps/android/sbom/native-components.cdx.json` still matches the native
   components in the APK (see [upstream-sync.md](upstream-sync.md)) and that
   `./gradlew licensee` passes.
5. Run the dry run (section 3) on the commit you are about to tag, at least
   before the first release and after any change to the release machinery.

## 2. Preview the changelog

```sh
git cliff --unreleased --tag android-v0.3.0
```

Check that the sections (Added, Changed, Fixed, Security, Performance,
Documentation, Upstream) read well. The "Upstream" section stays empty:
harvest commits are not `sync:` commits.

**First release.** The upstream Moonlight history is in this repository (the
subtree is not squashed), so the first changelog must start after the import.
The release workflow does this itself with the range `f397cda5..`
(`FIRST_RELEASE_BASE`). `f397cda5` is the last commit of the import: the
Borealis commit that registers the moonlight-common-c submodule, which comes
after the merge of the Moonlight subtree (`0e28ad26`). The range excludes that
commit and everything reachable from it, so all upstream history is left out;
the last upstream commit is `b48494cb`, "Version 12.2". To preview it:

```sh
git cliff f397cda5.. --tag android-v0.1.0
```

**Upstream commits.** Harvested upstream commits keep their upstream subject
and have no `sync:` prefix, so the "Upstream" group stays empty. They are parsed
like any other commit. Most Moonlight commits are not Conventional Commits and
are filtered out (`filter_unconventional` and `filter_commits` in `cliff.toml`),
but upstream commits that happen to start with `feat` or `fix` can appear. Read
the generated notes before publishing. The record of what came from upstream is
[core/third_party/README.md](../../core/third_party/README.md) for
moonlight-common-c, and the compatibility table in
[upstream-sync.md](upstream-sync.md).

Fix poor commit messages by editing PR titles before the merge, not by
rewriting history. The workflow generates the release notes from the same
configuration; it does not commit a `CHANGELOG.md`.

## 3. Dry run

Run the release pipeline without a tag whenever the release machinery changes,
and before the first real tag. The dry run builds the release APK as if the tag
`android-v0.1.0` existed, signs it with a throwaway key and publishes nothing.

- **By hand:** `gh workflow run release.yml --ref <branch>`, then
  `gh run watch`.
- **Automatically:** a pull request that changes `.github/workflows/release.yml`,
  `.github/scripts/`, `.github/release-compatibility.md`, `apps/android/build-logic/`,
  `apps/android/app/proguard-rules.pro` or `apps/android/gradle/libs.versions.toml`
  starts it.

It needs no secrets and does not use the `release` environment. What it does:

1. Creates a throwaway local tag `android-v0.1.0` on the runner (never pushed),
   so the version is `0.1.0` and the version code is `1000`.
2. Builds the release APK with R8 and the Gradle SBOM, as the release does.
3. Checks the APK (`.github/scripts/check-apk.sh`): package
   `io.github.magalz.borealis` (no `.unofficial` suffix), label `Borealis`,
   versionName and versionCode as above, not debuggable, the JNI entry
   classes `com.limelight.nvstream.jni.MoonBridge` and
   `io.github.magalz.borealis.nativecore.CoreJni` still in the dex, and
   `libmoonlight-core.so` present for all four ABIs (the universal APK must
   carry every one).
4. Generates the release notes (git-cliff) and appends the compatibility note.
5. Creates a throwaway keystore with `keytool` and signs the APK with the same
   script the `publish` job uses (`.github/scripts/sign-apk.sh`): zipalign
   check, `apksigner` v2 and v3 signing, verification and `SHA256SUMS`.
6. Uploads the result as the artifact `DRY-RUN-not-a-release`, kept for 3 days.
   The file name contains `DRY-RUN-NOT-A-RELEASE`; it is signed with a key that
   is discarded, so it can never update or replace a release.

## 4. Tag

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

## 5. CI release workflow

Pushing the tag starts
[`.github/workflows/release.yml`](../../.github/workflows/release.yml). A tag
push runs two jobs. The dry run of section 3 uses the same `build` job and
replaces `publish` with a job that signs with a throwaway key.

**`build`** (no secrets, read-only token):

1. Checks out the tagged commit with full history, validates
   the tag and that the commit is on `main`.
2. Installs the Android SDK packages (platform, build-tools, NDK, CMake) and
   runs `./gradlew assembleRelease :app:cyclonedxDirectBom` in
   `apps/android` (JDK 17, Gradle build cache disabled). The APK is a
   universal, **unsigned** release build; its version must equal the
   tag. No AAB is built (see "Distribution").
3. Checks the APK identity and the R8 result with `.github/scripts/check-apk.sh`
   (the checks listed in section 3).
4. Collects the unsigned APK, the Gradle SBOM
   (`app/build/reports/sbom/borealis-android.cdx.json`) and the hand-maintained
   native SBOM (`apps/android/sbom/native-components.cdx.json`).
5. Generates the release notes with git-cliff (`orhun/git-cliff-action`) for
   the range from the previous `android-vX.Y.Z` tag to this tag, then appends
   the compatibility note. The first release uses the range `f397cda5..` (see
   section 2).
6. Uploads everything as the artifact `borealis-android-unsigned`, kept for
   30 days. Approve within that time; after it expires, delete the tag only if
   nothing was published, otherwise release a new patch version (see "If
   something is wrong").

**`publish`** (`environment: release`, runs only on a tag push):

1. **Waits for manual approval** of the `release` environment. Review the run,
   the tagged commit and the unsigned artifacts first. The environment secrets
   are released only after approval.
2. Checks out the tagged commit (it supplies the signing script), downloads the
   unsigned artifacts, sets up JDK 17 (for `keytool`) and locates `apksigner`
   and `zipalign` (build-tools `37.0.0`).
3. Decodes the keystore from `BOREALIS_KEYSTORE_BASE64` into a temporary file.
4. Runs `.github/scripts/sign-apk.sh`, the script the dry run also uses. It
   signs the APK with `apksigner` (APK Signature Scheme v2 and v3; v1 and v4
   off, because minSdk is 28) and verifies it: `zipalign -c -P 16 4`,
   `apksigner verify --min-sdk-version 28` (the v3 signature must verify; v2 is
   also written but apksigner skips it for API 28 and up), and that the APK
   signer's certificate SHA-256 equals the keystore's. It then writes
   `SHA256SUMS` over the signed APK and both SBOMs and checks it.
5. Removes the keystore file with `shred -u`, always, also when an earlier step
   failed.
6. Creates build-provenance attestations (`actions/attest-build-provenance`)
   for the APK, both SBOMs and `SHA256SUMS`.
7. Creates a **draft** GitHub Release for the tag (`gh release create --draft`)
   with the release notes and these assets: `borealis-android-X.Y.Z.apk`,
   `SHA256SUMS`, `borealis-android-X.Y.Z.cdx.json` (Gradle dependencies of the
   release APK) and `borealis-android-native-X.Y.Z.cdx.json` (native
   components).

The release notes are the git-cliff output followed by the compatibility note:
the tested LuminalShine version, the Moonlight base, the moonlight-common-c base
and the known host issues.

Do not approve if the run, the commit or the artifacts look wrong.

### Distribution

- **Channels:** GitHub Releases, followed by Obtainium. Nothing else until
  Phase 5.
- **Universal APK only.** The one APK contains all four ABIs. Per-ABI APKs are
  deferred.
- **No AAB.** The Android App Bundle and Google Play wait for Phase 5, when a
  separate Play upload key exists (ADR 0026). Until then the pipeline does not
  build, sign or publish an AAB.

### One-time setup (owner)

1. **Tag ruleset.** Add a repository ruleset (Settings, Rules, Rulesets) that
   targets tags matching `android-v*` and restricts creation, update and
   deletion to the owner. Only the owner can then start a release, and a
   published tag cannot be moved or removed.
2. **Environment.** Create the GitHub Environment `release` (Settings,
   Environments) with required reviewers. Under "Deployment branches and
   tags", select "Selected branches and tags" and allow only the tag pattern
   `android-v*`, so no branch or other tag can reach the signing secrets.
3. **Self-review.** With a single maintainer, leave "Prevent self-review"
   **off** on the environment. If it is on, nobody can approve the run.
4. **Secrets.** Add these **environment secrets** (not repository secrets, so
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

## 6. Verify

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

## 7. Obtainium and stores

**Obtainium** follows the GitHub Releases of the repository. To add Borealis:

1. In Obtainium, add an app and enter the repository URL
   `https://github.com/magalz/Borealis`.
2. Set "Filter APKs by regular expression" to
   `^borealis-android-[0-9.]+\.apk$`. Each release has one APK asset, named
   `borealis-android-X.Y.Z.apk`; the filter makes Obtainium ignore
   `SHA256SUMS` and the SBOMs.
3. Set "Version string extraction regex" to `[0-9]+\.[0-9]+\.[0-9]+`. The tag is
   `android-vX.Y.Z` while the installed versionName is `X.Y.Z`, so Obtainium
   must compare only the numbers. Confirm this on the first real update.
4. Leave pre-releases off. Obtainium does not see draft releases, so publish the
   draft first.

Stores come in Phase 5. Uploads are manual at first; automation comes later.

- **Google Play:** upload an AAB signed with the separate upload key; Play App
  Signing re-signs for distribution. Use a staged rollout.
- **Amazon Appstore:** upload the signed APK.

Publish to GitHub Releases before any store.

## 8. After the release

- Confirm the release notes and the changelog match.
- Update docs that mention the "latest" version if needed.
- Close the related milestone or issues.

## If something is wrong

- Do not move or re-use a published tag. Fix on `main` and release a patch
  version.
- For a security problem follow [SECURITY.md](../../SECURITY.md); publish an
  advisory once the fixed release is available.
