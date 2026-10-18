# Security Policy

## Supported versions

Borealis is pre-1.0. Only the latest release receives security fixes. Older
releases are not patched; update to the latest release.

| Version | Supported |
| --- | --- |
| Latest release (`android-vX.Y.Z`) | Yes |
| Anything older | No |

## Reporting a vulnerability

Do not open a public issue or pull request for a security problem.

Private vulnerability reporting is enabled in the repository settings. Report
it privately through GitHub Security Advisories:

1. Open the [Security tab](https://github.com/magalz/Borealis/security) of the repository.
2. Choose **Report a vulnerability**.
3. Fill in the form.

If you cannot use Security Advisories, open a public issue that only asks the
maintainer ([@magalz](https://github.com/magalz)) for a private contact
channel. Do not include any details of the vulnerability in that issue.

### What to include

- The affected component (Android client, `borealis-core`, build or release
  tooling) and the version or commit.
- Device, Android version, and LuminalShine host version if relevant.
- Clear steps to reproduce, or a proof of concept.
- The impact you expect (for example: pairing bypass, certificate validation
  bypass, secret disclosure, remote code execution).
- Whether the issue is already public or has a disclosure deadline.

Do not include real PINs, pairing keys, certificates, private keys, tokens or
signing material in a report. Use throwaway values.

### Response targets

These are targets from a single maintainer, not guarantees:

| Step | Target |
| --- | --- |
| Acknowledge the report | 7 days |
| Initial assessment (accepted, needs info, or out of scope) | 14 days |
| Fix or mitigation for confirmed high-severity issues | 90 days |

Fixes are developed in a private advisory fork where needed, released as a new
version, and credited to the reporter unless anonymity is requested. A
[GitHub Security Advisory](https://docs.github.com/en/code-security/security-advisories)
is published once a fixed release is available.

## Scope

In scope:

- The Borealis Android client (`apps/android/`), including Borealis code in
  `apps/android/borealis/`.
- `borealis-core` (`core/`).
- The build, CI and release tooling in this repository (workflows, scripts,
  signing and publishing steps).

Out of scope here:

- Vulnerabilities in the **LuminalShine host** (or Sunshine/Vibeshine). Report
  those to [LuminalShine](https://github.com/NortheBridge/luminalshine).
- Vulnerabilities that exist unchanged in upstream
  [moonlight-android](https://github.com/moonlight-stream/moonlight-android) or
  [moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c).
  Report those upstream. If Borealis ships an affected version, you may also
  tell us so that we can sync the fix.
- Issues that need a rooted or already-compromised device, or physical access
  to an unlocked device.
- Denial of service by flooding a host you control.

## Signing keys and secrets

- The release signing key is held by the owner only. It is never stored in the
  repository or in CI logs. Release signing happens in the GitHub Environment
  `release`, which requires manual approval. A separate upload key is planned
  for Google Play, which comes in Phase 5.
- Secrets are never committed. If you find a leaked secret in the repository
  history, report it as a vulnerability so that it can be rotated.
- Release artifacts come with SHA-256 checksums, a CycloneDX SBOM and GitHub
  build-provenance attestations so that you can verify what you install. See
  [docs/policies/release.md](docs/policies/release.md).

## Safe harbor

Good-faith research that follows this policy, avoids privacy violations and
service disruption, and gives us reasonable time to fix the issue will not be
pursued by the maintainer.
