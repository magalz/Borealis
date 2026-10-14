---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0026 Use one owner-generated app signing key for all channels

## Context and Problem Statement

Users must be able to switch between distribution channels (GitHub, Amazon, Google Play) without reinstalling, which requires the same signing certificate on each channel. How should app signing keys be generated, held and used in CI?

## Considered Options

* One owner-generated app signing key for every channel (GitHub, Amazon, and Play App Signing with "use your own key"; separate Play upload key)
* Google-generated key on Play, own key elsewhere
* Local signing only
* Remote signing (KMS/HSM)

## Decision Outcome

Chosen option: "One owner-generated app signing key for every channel", because the same certificate is used everywhere, and users can switch channels without reinstalling.

Details:

* The Play App Signing "use your own key" option is used, with a separate Play upload key.
* Two offline backups of the key are kept.
* CI secrets are stored in a GitHub Environment named `release`, with manual approval before signing.

### Consequences

* Good, because users can switch channels without reinstalling.
* Good, because CI signing requires a manual approval step.
* Bad, because the owner must never lose or leak the key, which is a single point of risk, mitigated by two offline backups.

## Pros and Cons of the Options

### One owner-generated app signing key for every channel

* Good, because users can switch channels without reinstalling.
* Good, because the certificate is the same on every channel.
* Good, because CI signing has a manual approval step in a `release` environment.
* Bad, because the owner must never lose or leak the key.

### Google-generated key on Play, own key elsewhere

* Good, because Google holds the Play key.
* Bad, because the certificates are incompatible across channels, and reinstalling loses paired hosts and settings.

### Local signing only

* Good, because the key never leaves the PC.
* Bad, because releases are manual and there is no CI provenance attestation.

### Remote signing (KMS/HSM)

* Good, because the key is not exportable.
* Bad, because it costs more and is more complex.

## More Information

See [0023](0023-distribution-channels.md), [0021](0021-audit.md) (build provenance) and [0045](0045-store-publishing-automation.md).

Amended by [0050](0050-diagnostics-audit-and-interim-settings.md).
