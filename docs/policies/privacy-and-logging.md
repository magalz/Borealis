# Privacy and logging policy

The rules that code, logs, the audit trail and crash reporting must follow.
The user-facing statement is [PRIVACY.md](../../PRIVACY.md). The decisions
behind these rules are in [0020](../adr/0020-logging.md),
[0021](../adr/0021-audit.md) and
[0050](../adr/0050-diagnostics-audit-and-interim-settings.md).

## Principles

- **No telemetry.** No usage analytics, no advertising or tracking SDKs.
- **Local first.** Logs, the diagnostic bundle and the audit trail stay on the
  device. Nothing leaves unless the user exports it or opts in to crash
  reporting.
- **Redact at the source.** Sensitive values are removed before they are
  written, not when they are exported.
- **Least data.** If a value is not needed to diagnose a problem, do not log
  it.

## Redaction rules

Never write the following to logs, the audit trail, exceptions that may be
reported, fixtures, or screenshots, except where a row says otherwise:

| Data | Rule |
| --- | --- |
| Pairing PIN | Never logged. |
| Tokens, passwords, session and API secrets | Never logged. |
| Private keys, client keys, key material | Never logged. |
| Certificates (client and host PEM/DER) | Never logged in full. A short SHA-256 fingerprint prefix may be logged. |
| IP addresses and host names | Logged as-is when the "Mask addresses" setting is off. When it is on, they are masked when lines are written, and again in the diagnostic bundle. Masking covers IPv4 addresses (octets of at most 255), IPv6 addresses and host names, for example `192.168.x.x` or a stable host alias. Never sent in crash reports. |
| URL query values | Redacted, except an allowlist of non-identifying protocol parameters such as `phrase` and `mode`. Query keys may stay. TODO(verify): the final allowlist, which the implementer sets and this row must then list. |
| Stream content, controller input, window titles | Never logged. |

Implementation requirements:

- One redaction layer sits in front of the log sink and the audit trail;
  features do not roll their own.
- Redaction is covered by tests, including a test that known secret patterns
  do not reach the sink.
- URLs are logged without credentials. Query values follow the URL row above.

## Local logs

- Logs are stored in `noBackupFilesDir`, the app's private storage. They are
  excluded from Android backup.
- Retention: rotating files `borealis.log*`, 5 files of 1 MB each (5 MB
  maximum); the oldest file is overwritten.
- Release builds write INFO and above. The "Verbose logging" setting raises the
  level to DEBUG. Verbose logging is off by default.
- Native log lines from moonlight-common-c reach `BorealisLog` under the
  `COMMONC` tag. They pass through a bounded asynchronous queue with a rate
  limit. Suppressed lines are summarized as "N lines suppressed", so a burst of
  frame-loss lines cannot evict pairing and launch history.
- Logs are not uploaded automatically.

## Diagnostic bundle

- Created only on user request, from the Borealis settings screen.
- The bundle is a zip with:
  - `manifest.json`: app version, versionCode, git SHA, build type, Android
    version and SDK, manufacturer, model, ABIs, UTC creation time and the log
    settings;
  - `logs/`: the rotating `borealis.log*` files and the `crash-*.txt` files,
    redacted again line by line with the current "Mask addresses" setting;
  - `decoders.json`: the video decoders from `MediaCodecList` for H.264, HEVC
    and AV1, including Main10 and HDR10 profiles, marked as hardware or
    software, and the display HDR types;
  - one file per diagnostic section, such as the audit trail as JSON Lines.
- Host details come from the "host capabilities" log line. There is no
  separate hosts file.
- Vulkan capabilities are deferred to Phase 4 (PyroWave).
- The bundle has a size bound.
- It is shared through a FileProvider. On devices with no share target (Google
  TV), it is saved to the app's external files directory and the path is shown.
- The user chooses where to share it. The bundle must not contain secrets; the
  bug report template warns users to check before posting.

## Audit trail

A local, in-app record of security-relevant events. Users can export it in the
diagnostic bundle and erase it from the Borealis settings screen. The in-app
viewer arrives with the Phase 2 UI.

Storage: a Room database at `noBackupFilesDir/audit.db`. It is excluded from
Android backup and is never uploaded.

Events:

- pairing start, success, failure and automatic restart;
- unpair, confirmed or not confirmed;
- host certificate change, TLS failures and pinning failures;
- launch attempts, retries and the user's choice in the running-app conflict;
- session start (codec, resolution, fps and HDR) and session end (duration,
  reason or error code).

REST and token events arrive with Phase 3.

Fields per entry:

| Field | Content |
| --- | --- |
| Timestamp | UTC time. |
| Event type | One of the event names above. |
| Host | Host name and address, masked per the "Mask addresses" setting. |
| Outcome | Success, failure or cancelled. |
| Detail | Short redacted reason or error code. Never secrets. |
| App version | Borealis version that wrote the entry. |

Bounds:

- Retention: the newest 2,000 entries or 90 days, whichever is smaller.
- Deduplication: the same event type, host and detail is stored at most once
  per 10 minutes. `/serverinfo` is polled every 1.5 s, so without this the
  trail would fill with repeats.

## Backup

- Logs and the audit trail live in `noBackupFilesDir` and are excluded from
  Android backup.
- The client pairing identity and the host list follow Android backup. They
  are encrypted where the platform supports it (`disableIfNoEncryptionCapabilities`).

## Crash reporting (planned, not implemented)

When Sentry crash reporting is implemented it must meet all of these:

- Opt-in, off by default; revocable at any time; the choice is explicit.
- EU data region.
- No PII: no IP addresses, host names, user identifiers, PINs, keys,
  certificates or tokens. The same redaction is applied in `beforeSend`
  and to breadcrumbs; automatic IP and user collection is disabled.
- Reports contain only stack traces and technical context, with redaction
  tests.
- [PRIVACY.md](../../PRIVACY.md) and the release notes are updated in the same
  PR that introduces it.

Google Play Android vitals may apply to Play installs; they are controlled by
Google and the user's device settings, not by Borealis.

## For contributors

- New log lines and audit events are part of the Definition of Done where
  applicable ([CONTRIBUTING.md](../../CONTRIBUTING.md)).
- Review every new log statement for the data listed above.
- Test fixtures use throwaway values, never real credentials.
