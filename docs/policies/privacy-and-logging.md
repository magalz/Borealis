# Privacy and logging policy

The rules that code, logs, the audit trail and crash reporting must follow.
The user-facing statement is [PRIVACY.md](../../PRIVACY.md). The decisions
behind these rules are in [0020](../adr/0020-logging.md),
[0021](../adr/0021-audit.md) and
[0050](../adr/0050-diagnostics-audit-and-interim-settings.md).

## Principles

- **No telemetry.** No usage analytics, no advertising or tracking SDKs.
- **Local first.** Logs, the diagnostic bundle and the audit trail stay on the
  device. Nothing leaves unless the user exports it (including the local
  link of "Send to phone" on a TV) or opts in to crash reporting.
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
| IP addresses and host names | Logged as-is when the "Mask addresses" setting is off. When it is on, they are masked when lines are written, and again in the diagnostic bundle. Masking covers IPv4 addresses (octets of at most 255), written as `[IP]`, IPv6 addresses, also `[IP]`, and host names, written as `[HOST]`. Host names are recognised only in the host part of a URL and in the values of fields named `host`, `hostname`, `computer` or `computerName`. A host name in free text, including the upstream `LimeLog` lines, is not recognised and so is not masked. Local crash reports follow the same setting. Never sent in crash reports to a service. |
| URL query values | Replaced by `[REDACTED]`, except for this allowlist of non-identifying GameStream parameters (compared without regard to case): `phrase`, `updateState`, `mode`, `additionalStates`, `sops`, `hdrMode`, `clientHdrCapVersion`, `clientHdrCapSupportedFlagsInUint32`, `clientHdrCapMetaDataId`, `clientHdrCapDisplayData`, `localAudioPlayMode`, `surroundAudioInfo`, `remoteControllersBitmap`, `gcmap`, `gcpersist`, `corever`, `AssetType` and `AssetIdx`. Query keys stay. `uniqueid`, `uuid`, `devicename`, `appid`, `rikey`, `rikeyid`, `salt`, `clientcert` and every pairing parameter are never in the list. Credentials in a URL (`user:password@`) are redacted too. |
| Stream content, controller input, typed keys and typed text, window titles | Never logged. The keyboard and the text path of the stream write no log lines, and the audit trail holds no key or text. |

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

### Log lines of the stream (Sprint 15)

Sprint 15 added these lines. Each goes through `BorealisLog` and so through the redaction
layer. None of them carries an IP address, a host name or the name of an app: their fields are
counts, sizes, times, a code, a choice and a fixed reason word.

| Tag | Line | Fields | Level |
| --- | --- | --- | --- |
| `APP` | `quit accepted`, `disconnect accepted` | none | info |
| `APP` | `quit stream ended`: the end that the user's own Quit caused, which is error -1 while the quit is pending | `code`, `since_quit_ms` | info |
| `APP` | `connection stop done` | `ms` | info |
| `APP` | `activity finished`: every exit asks the system once | none | info |
| `HOST` | `quit cancel sent`, `quit cancel answered`, `quit check answered` | `ms` on the last two | info |
| `LAUNCH` | `running app joined on the user's choice` | none | info |
| `VIDEO` | `Frozen frame requested`: the copy of the last frame of a stream that failed | `width`, `height` of the copy | info |
| `VIDEO` | `Frozen frame copied` | `ms` | info |
| `VIDEO` | `Frozen frame failed` | `reason`: `no_surface` or `copy` | warn |
| `VIDEO` | `Menu glass copies`: once a second while the menu glass runs | `copies`, `skipped`, `failed`, `no_surface`, `slowest_ms` | debug |

- The three `HOST` lines come from `NvHTTP.quitApp`, so every Quit writes them: the Quit of the
  in-game menu, the Quit of the Apps sheet of the Library and the quit before a quit and
  relaunch.
- `LaunchConflicts` also logs `running app conflict answered` with the `choice`, as before, now
  for every answer to the question, on both paths.
- The end of the user's own Quit is no longer logged as an error, as `Connection terminated:
  -1` on the `UPSTREAM` tag. It is the `quit stream ended` line above, and no connectivity
  test runs for it. Any other end is logged and tested as before.
- The copies of the video stay in memory. The live copies of the menu glass are bitmaps a
  quarter of the video, in a pool of three, drawn behind the menu and dropped with it. The one
  copy of the last frame is held by the activity and let go when it is destroyed. No copy is
  written to a file, logged, put in the audit trail or put in the diagnostic bundle, and the
  lines above hold only the size of a copy and the time it took.

## Settings storage

- The "Verbose logging" and "Mask addresses in logs and diagnostics" switches
  are stored in the app's private DataStore file, `borealis_settings`. The file
  is excluded from Android backup.
- The Appearance setting "Background" (`borealis_background`) is stored in the
  same DataStore file, so the file is excluded from backup too. It holds the id
  of a background style, such as `aurora_curtains` or `off`, and nothing
  personal ([ADR 0056](../adr/0056-add-an-animated-background-and-the-appearance-group.md)).
- The glass and accessibility settings are stored in the same DataStore file too,
  so the file is excluded from backup: Glass tint and Glass blur
  (`borealis_glass_tint`, `borealis_glass_blur`), Reduce motion
  (`borealis_reduce_motion`), Transparency (`borealis_transparency`), Thicker
  focus outline (`borealis_thick_focus`), Text size (`borealis_text_size`), High
  contrast (`borealis_high_contrast`) and Colour-blind palette
  (`borealis_colour_vision`). They hold stable ids and switches, and nothing
  else ([ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md)).
- The five Appearance settings of Sprint 11 are stored in the same file. Together with
  the eleven above, the file holds 16 settings, 17 with the stream option of Sprint 15 below,
  and it is excluded from backup too:
  Font (`borealis_font`), Accent colour (`borealis_accent`), Background intensity
  (`borealis_background_intensity`), Card density (`borealis_card_density`) and Text
  spacing (`borealis_text_spacing`). They hold stable ids and nothing personal
  ([ADR 0058](../adr/0058-appearance-and-reading-options.md)). They are not uploaded,
  and they are not put in diagnostics: no value of them is in a log line, in the audit
  trail or in the diagnostic bundle.
- The stream option of Sprint 15 is stored in the same file, so it holds 17 settings
  and is excluded from backup: Stream menu style (`borealis_stream_menu_glass`: `blur`
  or `solid`). It holds a stable id and nothing personal
  ([ADR 0061](../adr/0061-glass-and-the-animated-background-in-the-stream.md)). It
  follows the same rule: it is never uploaded, and no value of it is in a log line, in the
  audit trail or in the diagnostic bundle. A second option, Background before and after the
  stream (`borealis_stream_background`), was added in the same sprint and dropped on
  2026-10-30. It is no longer a setting, so the key is not read and not written. A value
  already stored under it stays in the file of the user, unused, until the app data is
  cleared. It was a stable id too (`moving`, `still` or `off`), and nothing personal.
- Accessibility choices can reveal a disability, so they are never uploaded and
  never put in diagnostics: no value of them is in a log line, in the audit trail or in
  the diagnostic bundle. The `manifest.json` of the bundle lists only the two
  logging switches.
- A failed write of any setting logs the key name and the error's class name
  (`settings write failed`, in `StoreSettingsRepository`), never the value.
- The other settings stay in the app's private SharedPreferences, as before.
  They are not backed up either: the app's backup rules exclude its
  SharedPreferences from cloud backup and from device transfer.
- Nothing leaves the device. Settings are never uploaded.

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
  TV), it is saved to the Downloads collection (the app's external files
  directory on Android 9) and the path is shown.
- On a TV, "Send to phone" serves the bundle once over a local link, as
  [0051](../adr/0051-send-the-diagnostic-bundle-to-a-phone-with-a-qr-code.md)
  decides:
  - the server is plain HTTP and binds only the private IPv4 address of the
    active Wi-Fi or Ethernet network, never `0.0.0.0`, on an ephemeral port;
  - the URL path holds a 128-bit random token (compared in constant time); the
    token and the full URL are never logged;
  - the QR code holds the URL of a static page; the zip is a separate path, so a
    link preview does not use up the download;
  - one connection at a time, a read timeout of 10 seconds and a request head
    capped at 8 KiB; no request body is read;
  - the server stops after the first complete download, after 5 minutes (a
    watchdog thread closes a connection still in flight at that point, so a
    stalled client cannot extend it), or
    when the dialog is dismissed or the settings screen is closed;
  - the file is the bundle already built and redacted; nothing is added to it and
    nothing is uploaded elsewhere.
  - Residual risk: HTTP is not encrypted, so a device on the same network that
    can see the link can fetch the file once, before the intended phone does.
    Use it on a trusted network.
- The user chooses where to share it. The bundle must not contain secrets; the
  bug report template warns users to check before posting.

## Audit trail

A local, in-app record of security-relevant events. Users can export it in the
diagnostic bundle and erase it from the Borealis settings screen. The in-app
viewer, opened from Settings, Privacy and diagnostics, Audit trail
([UC-007](../use-cases/0007-review-and-erase-the-audit-trail.md)), exists since
Sprint 6.

Storage: a Room database at `noBackupFilesDir/audit.db`. It is excluded from
Android backup and is never uploaded.

Events:

- pairing start, success, failure and automatic restart;
- unpair, confirmed or not confirmed;
- host certificate change, TLS failures and pinning failures;
- launch attempts, retries and the user's choice in the running-app conflict. The
  `LAUNCH_CONFLICT` detail is `choice=<join|quit|cancel> running_app=<id> entry=<asked|chosen>`.
  `asked` is the user's answer to the question about the running app, on the `/serverinfo`
  path and on the path of a refused launch. `chosen` is a join that the user's earlier choice
  made with no question (`choice=join`, outcome success): Resume, a tap on the app that runs
  or Continue in the Library, or Try again on the error popup. The detail holds the id of the
  running app and never its name;
  a start restarted after an RTSP failure that followed an accepted `/resume`
  is recorded as `LAUNCH_RETRY` with `reason=rtsp_after_resume`, so one tap can
  produce two `LAUNCH_ATTEMPT` entries;
- session start (codec, resolution, fps and HDR) and session end (duration,
  reason or error code). The session end reason is one of:
  - `user`: the user left the stream screen;
  - `disconnect`: the user chose Disconnect in the in-game menu or pressed
    Ctrl+Alt+Shift+Q, and the app keeps running on the host;
  - `quit`: the user chose Quit in the in-game menu, and the app ended on the
    host;
  - `host_ended`: the host ended the stream;
  - `error` with the error code: the connection failed.

REST and token events arrive with Phase 3.

Fields per entry:

| Field | Content |
| --- | --- |
| Timestamp | UTC time. |
| Event type | One of the event names above. |
| Host | Host name and address, masked per the "Mask addresses" setting. It is the one place of this trail that holds a host: the log lines of Sprint 15 hold none. |
| Outcome | Success, failure or cancelled. |
| Detail | Short redacted reason or error code. Never secrets. |
| App version | Borealis version that wrote the entry. |

Bounds:

- Retention: the newest 2,000 entries or 90 days, whichever is smaller.
- Deduplication: only host certificate changes and TLS failures are
  deduplicated, because `/serverinfo` polling (every 1.5 s) can repeat them.
  The same type, host and detail is then stored at most once per 24 hours.
  Every other event comes from a user action and is always stored.

## Backup

- Logs and the audit trail live in `noBackupFilesDir` and are excluded from
  Android backup.
- The client pairing identity and the host list follow Android backup and
  device-to-device transfer, as upstream does. On Android 12 and later, cloud
  backup runs only when the device can encrypt it end to end
  (`disableIfNoEncryptionCapabilities`). On Android 9 to 11, Android encrypts
  cloud backups end to end when a screen lock is set.

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
