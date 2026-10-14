# Privacy

Borealis is a game-streaming client. This page states what it does with data
today and what is planned. It describes the code in this repository; it is not
a legal contract.

Status: Borealis is in early development and currently equals the imported
Moonlight Android 12.2 code base. The local log redaction, diagnostic bundle
and audit trail below are design requirements that are being built; until a
release includes them, only the stock Moonlight behaviour applies (no
telemetry, pairing data stored locally). This page will be kept in step with
the code.

## Summary

- **No telemetry.** Borealis does not collect usage analytics and contains no
  advertising or tracking SDKs.
- **No account.** Borealis has no Borealis account and no Borealis server.
  Streaming goes directly between your device and your LuminalShine (or other
  Moonlight-compatible) host.
- **Logs, the diagnostic bundle and the audit trail stay on your device** unless
  you export and share them yourself.
- **Crash reporting is planned, opt-in and off by default.** It is not
  implemented yet.

## What the app stores on your device

- The list of hosts you added or discovered, and the pairing credentials
  needed to talk to them (client certificate and private key, host
  certificate). These are stored in the app's private storage and are never
  uploaded by Borealis.
- Your settings.
- Local logs, with redaction (see below).
- A local audit trail of security-relevant events: pairing, certificate
  changes, TLS failures, launches, sessions and REST actions. You can view,
  export and erase it in the app.

## What never leaves the device

Borealis never sends any of these anywhere on its own initiative:

- Pairing PINs, pairing secrets, client private keys and certificates.
- Host addresses, host names, MAC addresses and your network details.
- Game, app and stream content, controller and input data.
- Logs, the diagnostic bundle and the audit trail.

Network traffic the app does produce goes to the host you choose (discovery on
the local network, pairing, REST calls and the stream itself).

## Logs, diagnostic bundle and audit trail

- Logs are local, size-limited and rotated, and are redacted before they are
  written: PINs, tokens and keys or certificates are removed, and IP addresses
  can optionally be masked.
- The diagnostic bundle is created only when you ask for it. You decide where
  it goes (for example attaching it to a GitHub issue). Check it before you
  share it.
- The audit trail is local and under your control; you can erase it at any
  time.

The exact rules are in
[docs/policies/privacy-and-logging.md](docs/policies/privacy-and-logging.md).

## Planned: opt-in crash reporting

Crash reporting through Sentry is planned but not implemented. When it ships it
will follow these rules:

- Off by default; enabled only by an explicit choice in settings; revocable at
  any time.
- EU data region.
- No personal data: no IP addresses, host names, PINs, keys, certificates or
  user identifiers; the same redaction as local logs is applied before anything
  leaves the device.
- This page and the release notes will be updated in the same change that
  introduces it.

## Store-specific notes

- **GitHub Releases / Obtainium:** nothing beyond the download itself, which
  GitHub handles under its own privacy policy.
- **Google Play:** Google may provide Android vitals (crash and ANR data) for
  installs from Google Play according to your device settings and Google's
  privacy policy. Borealis itself does not send this data; the maintainer can
  see aggregated vitals in the Play Console.
- **Amazon Appstore:** Amazon may collect usage data under its own policy.

## Children

Borealis is not directed at children and does not knowingly collect data from
anyone.

## Contact

Questions or concerns: open an issue at
[github.com/magalz/Borealis](https://github.com/magalz/Borealis/issues) or
contact the maintainer, [@magalz](https://github.com/magalz), through GitHub.
For a security problem use [SECURITY.md](SECURITY.md).

## Upstream

Borealis is based on Moonlight. See the Moonlight project for its own
documentation of the code it contributes. Borealis changes that affect privacy
will be called out in the release notes.
