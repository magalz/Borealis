# Dependency policy

Rules for adding, pinning and updating third-party code. Borealis is
GPL-3.0, so every dependency must be licence-compatible with it.

## Licence allowlist

Dependencies that are linked into, bundled with, or shipped alongside a
release must use one of these licences (SPDX identifiers):

| Licence | Notes |
| --- | --- |
| Apache-2.0 | Compatible with GPL-3.0 (not with GPL-2.0-only). |
| MIT | |
| BSD-2-Clause, BSD-3-Clause | |
| ISC | |
| Zlib | |
| OFL-1.1 | Fonts and font-like assets only. |
| GPL-3.0-only, GPL-3.0-or-later | Same licence as the project. |
| LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later | Dynamic or replaceable linking only; check the specific terms. |
| MPL-2.0 | File-level copyleft; allowed. |
| GPL-2.0 with Classpath Exception | Only where required by the Android toolchain. |

Anything else, including GPL-2.0-only, AGPL, "non-commercial" licences,
unlicensed code and code with a custom or unclear licence, needs an explicit
decision by the owner recorded in an ADR before it is added.

Development-only tools that are not shipped (formatters, linters, test
libraries) are not subject to the allowlist but must not be incompatible with
running in CI.

The licence check (licensee, `./gradlew licensee`, also part of `check`) runs in CI and compares the detected
licences against this allowlist. The generated third-party list and the
CycloneDX SBOM are the authoritative inventory; [NOTICE](../../NOTICE) is a
summary.

## Adding a dependency

Every new dependency needs, in the PR description:

1. **Reason:** what problem it solves and why a small amount of own code or an
   existing dependency does not.
2. **Licence:** the SPDX identifier and that it is on the allowlist.
3. **Maintenance:** last release, activity, and who maintains it.
4. **Footprint:** size, transitive dependencies, permissions, native code.
5. **Alternatives considered.**

Prefer fewer dependencies. Do not add a dependency for a single helper
function. Dependencies for security-sensitive areas (crypto, TLS, parsing
untrusted input) need extra care; prefer platform or already-present
libraries.

## Pinning

- Gradle: exact versions in the version catalog; no dynamic versions (`+`,
  `latest`, ranges). Use dependency verification or locking where the
  tooling supports it.
- GitHub Actions: pin third-party actions to a full commit SHA with a version
  comment.
- C++ (`core/`): pin tool and library versions in CMake or the lockfile used;
  fetch third-party sources only at pinned tags or hashes.
- Submodules and subtrees point at a specific commit or tag.
- Prebuilt native libraries (for example OpenSSL, Opus) record where and how
  they were built.

## Updates (Dependabot)

- Dependabot is enabled weekly for Borealis-owned Gradle dependencies and GitHub
  Actions. Until the independence milestone ([ADR 0047](../adr/0047-harvest-upstream-then-emancipate.md)),
  it also updates the Gradle wrapper, Kotlin and the dependencies inherited from
  upstream, which the version catalog declares since Sprint 8. AGP stays ignored
  until it is declared in one place. Kotlin may lead the version bundled with
  AGP when CI passes. The vendored moonlight-common-c (`core/third_party/`, [ADR 0054](../adr/0054-declare-independence-from-moonlight.md)) is not
  tracked by Dependabot; it changes only by a reviewed Borealis commit or an upstream fix cherry-picked on purpose (see
  [core/third_party/README.md](../../core/third_party/README.md)).
- Security updates are raised immediately.
- Updates arrive as PRs and go through the normal CI. Group low-risk updates;
  review major updates individually.
- The dependency review check blocks PRs that introduce a known-vulnerable or
  disallowed-licence dependency.
- CodeQL runs in CI, and the CycloneDX SBOM (Gradle `:app:cyclonedxDirectBom` plus the hand-maintained `apps/android/sbom/native-components.cdx.json`) is
  regenerated on every release.

## Removing and replacing

Remove dependencies that are no longer used in the same PR that drops their
last use. Replacing one dependency with another follows the same rules as
adding one.

## Upstream Moonlight dependencies

Dependencies imported with the Moonlight subtree are listed in
[NOTICE](../../NOTICE). Version bumps that come with an upstream sync follow
[upstream-sync.md](upstream-sync.md). Dependencies that came with upstream (in
the version catalog `apps/android/gradle/libs.versions.toml`, the root
`build.gradle` and the jni libraries)
are updated by Dependabot as described under Updates, per
[ADR 0047](../adr/0047-harvest-upstream-then-emancipate.md). Upstream syncs
resolve conflicts with a later update by taking the newer version.
