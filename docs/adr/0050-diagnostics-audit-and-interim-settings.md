---
status: accepted
date: 2026-10-14
decision-makers: João Pedro Magalhães
---

# 0050 Record diagnostics, audit trail, interim settings and the first release channel

## Context and Problem Statement

Sprint 2 must let users send useful diagnostics, keep a local record of security-relevant events, and change settings before the Phase 2 UI exists. [0020](0020-logging.md) and [0021](0021-audit.md) set the goals but leave the details open. The privacy policy also needs the same details. How should the diagnostic bundle, the audit trail, logging, interim settings, network endpoints, backup and the first release channel work in Sprint 2?

## Decision Drivers

* Users can send diagnostics without leaking secrets, addresses or host names
* Frame-loss bursts must not evict pairing and launch history from the log files
* The app contacts only the host that the user chooses
* The module graph stays acyclic
* The interim settings must move cleanly to DataStore in Phase 2

## Considered Options

The owner approved the items below as one set on 2026-10-14. The source notes do not record the alternatives considered for each item. The earlier ADRs do record some of them, for example the storage options in [0011](0011-datastore-room-and-keystore-for-new-persistence.md) and the logging options in [0020](0020-logging.md).

* Diagnostic bundle created on request, with a size bound
* Audit trail in Room, local only, as decided in [0011](0011-datastore-room-and-keystore-for-new-persistence.md)
* Interim settings in SharedPreferences with a `borealis_` key prefix until Phase 2
* Logging at INFO in release builds, with a "Verbose logging" switch to DEBUG
* Disable the inherited STUN and connection-test endpoints
* Exclude logs and the audit trail from Android backup
* Bundle `NOTICE` and `LICENSE` as assets and show them in the app
* Ship `android-v0.1.0` as a universal APK on GitHub Releases

TODO(verify): the owner should add the alternatives considered for each item, if any, before this ADR is merged.

## Decision Outcome

Chosen option: "The Sprint 2 set below", because it fixes the contract for the code tasks and closes the gaps between [0020](0020-logging.md), [0021](0021-audit.md) and the privacy policy.

Decision details:

1. **Diagnostic bundle (refines [0020](0020-logging.md)).**
   * Created only on request from the Borealis settings screen.
   * The bundle is a zip with:
     * `manifest.json`: app version, versionCode, git SHA, build type, Android version and SDK, manufacturer, model, ABIs, UTC creation time and the log settings;
     * `logs/`: the rotating `borealis.log*` files and the `crash-*.txt` files, redacted again line by line with the current address-masking setting;
     * `decoders.json`: the video decoders from `MediaCodecList` for H.264, HEVC and AV1, including Main10 and HDR10 profiles, marked as hardware or software, and the display HDR types;
     * one file per diagnostic section, such as the audit trail as JSON Lines.
   * The bundle is shared through a FileProvider. On devices with no share target (Google TV), it is saved to the app's external files directory and the path is shown.
   * The bundle has a size bound.
   * Host details come from the "host capabilities" log line. There is no separate hosts file.
   * Vulkan capabilities are deferred to Phase 4 (PyroWave).
2. **Audit trail (refines [0021](0021-audit.md)).**
   * Storage: a Room database at `noBackupFilesDir/audit.db`. It is local only and never uploaded.
   * Fields: UTC timestamp, event type, host (name and address masked per setting), outcome (success, failure or cancelled), redacted detail and app version.
   * Events:
     * pairing start, success, failure and automatic restart;
     * unpair, confirmed or not confirmed;
     * host certificate change, TLS failures and pinning failures;
     * launch attempts, retries and the user's choice in the running-app conflict;
     * session start (codec, resolution, fps and HDR) and session end (duration, reason or error code).
   * REST and token events arrive with Phase 3.
   * Retention: the newest 2,000 entries or 90 days, whichever is smaller.
   * Deduplication: the same type, host and detail is stored at most once per 10 minutes, because `/serverinfo` is polled every 1.5 s.
   * The user can export the audit trail in the diagnostic bundle and erase it from the Borealis settings screen. The in-app viewer arrives with the Phase 2 UI.
   * Architecture: the facade (`BorealisAudit`, `AuditEvent` and `AuditSink`) lives in `:borealis:logging`, so the module graph stays acyclic. The Room implementation lives in `:borealis:diagnostics`. Hilt provides it, and it is injected into the Application.
3. **Interim settings (amends [0011](0011-datastore-room-and-keystore-for-new-persistence.md)).**
   * A Borealis settings screen lives in its own module, `:borealis:settings`. It uses the framework preference screen, as upstream does. One custom Preference in the upstream `preferences.xml` opens it.
   * Values stay in SharedPreferences with the `borealis_` key prefix until the Phase 2 UI moves them to DataStore.
4. **Logging (refines [0020](0020-logging.md) and the [privacy policy](../policies/privacy-and-logging.md)).**
   * Release builds write INFO and above to the log file. A "Verbose logging" setting raises the level to DEBUG.
   * A "Mask addresses" setting masks IPv4 addresses (octets of at most 255), IPv6 addresses and host names. It applies when lines are written and again at export.
   * URL query values are redacted, except for an allowlist of non-identifying protocol parameters such as `phrase` and `mode`. The implementer finalizes the exact list, and the policy names it. TODO(verify): the final allowlist.
   * moonlight-common-c log lines reach `BorealisLog` under the `COMMONC` tag. They pass through a bounded asynchronous queue with a rate limit. Suppressed lines are summarized as "N lines suppressed", so frame-loss bursts cannot evict pairing and launch history from the 5 × 1 MB file ring.
5. **Network endpoints.**
   * The inherited STUN lookup (`stun.moonlight-stream.org`) and the connection test (`android.conntest.moonlight-stream.org`) are disabled at their JNI entry points. The "Test network connection" menu item is hidden.
   * Effect: no automatic external-address discovery and no automatic port test.
   * Reason: the privacy policy says the app talks only to the host the user chooses.
6. **Android backup.**
   * Logs and the audit trail live in `noBackupFilesDir` and are excluded from backup.
   * The client pairing identity and the host list keep upstream behaviour. They follow Android backup, encrypted where the platform supports it (`disableIfNoEncryptionCapabilities`).
   * [PRIVACY.md](../../PRIVACY.md) states this plainly.
7. **Licences screen (fulfils the NOTICE screen in [0021](0021-audit.md)).** `NOTICE` and `LICENSE` are bundled as assets and shown in the app, so they work on TV without a browser.
8. **First release channel (notes on [0023](0023-distribution-channels.md) and [0026](0026-signing-keys.md)).**
   * `android-v0.1.0` ships on GitHub Releases as a universal APK. Obtainium follows GitHub.
   * No AAB until Phase 5 (stores). Then a separate Play upload key exists, as [0026](0026-signing-keys.md) requires.
   * Per-ABI APKs are deferred.
9. **Out of scope now.** Crash reporting with Sentry stays accepted but not implemented ([0022](0022-crash-reporting-and-privacy.md)). Native crash capture is also out of scope.

### Consequences

* Good, because the diagnostic bundle, the audit trail and the logs have a fixed, testable contract.
* Good, because the app makes no automatic external connection, and the policy says so.
* Good, because bursts of native log lines cannot evict pairing and launch history.
* Good, because the licence texts work on TV without a browser.
* Bad, because two settings mechanisms (SharedPreferences and, later, DataStore) exist for a while.
* Bad, because the automatic external-address discovery and port test are gone, so users who relied on them must configure their network by hand.
* Bad, because pairing credentials follow Android backup, so they can leave the device through a backup, encrypted where the platform supports it.
* Bad, because the first release is a universal APK only, so the APK is larger than per-ABI builds would be.

This ADR refines [0020](0020-logging.md) and [0021](0021-audit.md), amends [0011](0011-datastore-room-and-keystore-for-new-persistence.md), and adds notes to [0023](0023-distribution-channels.md) and [0026](0026-signing-keys.md).

## Pros and Cons of the Options

### Diagnostic bundle created on request

* Good, because nothing is collected without the user's action.
* Good, because the user can check the bundle before sharing it.
* Bad, because the user must act to report a problem.

### Audit trail in Room, local only

* Good, because history is queryable and has schema migrations.
* Good, because the record never leaves the device.
* Bad, because Room adds KSP to the build.

### Interim settings in SharedPreferences

* Good, because the upstream code that reads these keys keeps working.
* Good, because no migration is needed before Phase 2.
* Bad, because there are two settings mechanisms until Phase 2.

### Disable the STUN and connection-test endpoints

* Good, because the app contacts only the host the user chooses.
* Bad, because there is no automatic external-address discovery and no automatic port test.

### Exclude logs and the audit trail from backup

* Good, because logs and audit records are not copied to backups.
* Bad, because a restored device starts with an empty audit trail.

### Universal APK on GitHub Releases

* Good, because one file serves all devices, including TVs sideloaded through Obtainium.
* Bad, because the file is larger than per-ABI APKs, which are deferred.

## More Information

* Related: [0020](0020-logging.md), [0021](0021-audit.md), [0011](0011-datastore-room-and-keystore-for-new-persistence.md), [0022](0022-crash-reporting-and-privacy.md), [0023](0023-distribution-channels.md) and [0026](0026-signing-keys.md).
* Policy: [privacy-and-logging.md](../policies/privacy-and-logging.md). User statement: [PRIVACY.md](../../PRIVACY.md).
