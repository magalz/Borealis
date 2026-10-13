# Privacy and logging policy

The rules that code, logs, the audit trail and crash reporting must follow.
The user-facing statement is [PRIVACY.md](../../PRIVACY.md).

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
| IP addresses and host names | Logged as-is locally by default; optional setting masks them (for example `192.168.x.x` or a stable host alias). Always masked in the diagnostic bundle when masking is on, and never sent in crash reports. |
| Stream content, controller input, window titles | Never logged. |

Implementation requirements:

- One redaction layer sits in front of the log sink and the audit trail;
  features do not roll their own.
- Redaction is covered by tests, including a test that known secret patterns
  do not reach the sink.
- URLs are logged without query strings or credentials.

## Local logs

- Logs are stored in the app's private storage.
- Retention: rotating files, 5 files of 1 MB each (5 MB maximum); the oldest
  file is overwritten.
- Log levels are conservative by default; verbose logging is user-enabled.
- Logs are not uploaded automatically.

## Diagnostic bundle

- Created only on user request, from the redacted logs plus basic non-personal
  information (app version, Android version, device model, LuminalShine
  version).
- The user chooses where to share it. The bundle must not contain secrets; the
  bug report template warns users to check before posting.

## Audit trail

A local, in-app record of security-relevant events that users can view,
export and erase.

Events: pairing (start, success, failure), certificate changes, TLS failures,
launches, sessions (start, end, reason), and REST actions against the host.

Fields per entry:

| Field | Content |
| --- | --- |
| Timestamp | UTC time. |
| Event type | One of the event names above. |
| Host | Internal host identifier and display name (address masked per setting). |
| Outcome | Success, failure or cancelled. |
| Detail | Short redacted reason or error code. Never secrets. |
| App version | Borealis version that wrote the entry. |

The audit trail is size-bounded and never uploaded.

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
