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

The owner chose among these alternatives on 2026-10-14. Storage and logging libraries were already chosen in [0011](0011-datastore-room-and-keystore-for-new-persistence.md) and [0020](0020-logging.md).

* Network endpoints: disable the STUN lookup and the connection test; keep them and disclose them; make them an opt-in setting, off by default; or replace STUN with another public STUN server and disable the test
* Interim settings: SharedPreferences with a `borealis_` prefix until Phase 2; DataStore now, bridged to the framework preference screen; or no new settings until the Phase 2 UI
* moonlight-common-c log lines: forward them into `BorealisLog` through a bounded queue; copy the app's own logcat into the bundle at export time; or leave them in logcat only
* Audit trail access: export and erase now, with the in-app viewer in Phase 2; or a simple viewer now in the upstream screen style
* First release channel: a universal APK on GitHub Releases, followed by Obtainium; or also Google Play internal testing now

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
   * Deduplication applies only to the events that `/serverinfo` polling (every 1.5 s) can repeat: a host certificate change and a TLS failure. The same type, host and detail is stored at most once per 24 hours, so a lasting mismatch adds about one entry a day and cannot push the rest of the trail out. Every other event comes from a user action and is always stored.
   * The user can export the audit trail in the diagnostic bundle and erase it from the Borealis settings screen. The in-app viewer arrives with the Phase 2 UI.
   * Architecture: the facade (`BorealisAudit`, `AuditEvent` and `AuditSink`) lives in `:borealis:logging`, so the module graph stays acyclic. The Room implementation lives in `:borealis:diagnostics`. Hilt provides it, and it is injected into the Application.
3. **Interim settings (amends [0011](0011-datastore-room-and-keystore-for-new-persistence.md)).**
   * A Borealis settings screen lives in its own module, `:borealis:settings`. It uses the framework preference screen, as upstream does. One custom Preference in the upstream `preferences.xml` opens it.
   * Values stay in SharedPreferences with the `borealis_` key prefix until the Phase 2 UI moves them to DataStore.
4. **Logging (refines [0020](0020-logging.md) and the [privacy policy](../policies/privacy-and-logging.md)).**
   * Release builds write INFO and above to the log file. A "Verbose logging" setting raises the level to DEBUG.
   * A "Mask addresses" setting masks IPv4 addresses (octets of at most 255), IPv6 addresses and host names. It applies when lines are written and again at export.
   * URL query values are redacted, except for an allowlist of non-identifying protocol parameters such as `phrase` and `mode`. The implementer finalizes the exact list, and the policy names it. S2-T1 records the final list in the policy.
   * moonlight-common-c log lines reach `BorealisLog` under the `COMMONC` tag. They pass through a bounded asynchronous queue with a rate limit. Suppressed lines are summarized as "N lines suppressed", so frame-loss bursts cannot evict pairing and launch history from the 5 × 1 MB file ring.
5. **Network endpoints.**
   * The inherited STUN lookup (`stun.moonlight-stream.org`) and the connection test (`android.conntest.moonlight-stream.org`) are disabled at their JNI entry points. The "Test network connection" menu item is hidden.
   * Effect: no automatic external-address discovery and no automatic port test.
   * Reason: the privacy policy says the app talks only to the host the user chooses.
6. **Android backup.**
   * Logs and the audit trail live in `noBackupFilesDir` and are excluded from backup.
   * The client pairing identity and the host list keep upstream behaviour: they follow Android backup and device-to-device transfer. On Android 12 and later, cloud backup runs only when the device can encrypt it end to end (`disableIfNoEncryptionCapabilities`). On Android 9 to 11, Android encrypts cloud backups end to end when a screen lock is set.
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

### Network endpoints

* Disable both (chosen). Good, because the app contacts only the host the user chooses. Bad, because external-address discovery and the automatic port test are gone.
* Keep and disclose. Good, because no code changes. Bad, because the app contacts a third party without being asked, using Moonlight's infrastructure under another name.
* Opt-in setting, off by default. Good, because users who want the features can turn them on. Bad, because it adds a setting and more upstream hooks.
* Another public STUN server. Good, because external-address discovery stays. Bad, because it only swaps one third party for another.

### Interim settings

* SharedPreferences with a prefix (chosen). Good, because it fits the upstream preference screen with no bridge code. Bad, because two settings mechanisms exist until Phase 2.
* DataStore now. Good, because it follows [0011](0011-datastore-room-and-keystore-for-new-persistence.md) at once. Bad, because the framework preference screen is synchronous, so the bridge must block on DataStore.
* No new settings until Phase 2. Good, because there is no interim code. Bad, because the 0.1.0 release could not offer verbose logging, address masking, export or erase.

### moonlight-common-c log lines

* Forward through a bounded queue (chosen). Good, because the lines survive app restarts, pass through redaction and reach the bundle. Bad, because it needs a small native hook and a rate limit.
* Copy the app's logcat at export. Good, because no native hook is needed. Bad, because logcat holds only the current process, its buffer is short and shared, and the lines skip redaction until export.
* Logcat only. Good, because nothing changes. Bad, because stream-level evidence never reaches the bundle.

### Audit trail access

* Export and erase now, viewer in Phase 2 (chosen). Good, because no screen is built twice. Bad, because the user reads the trail through the exported bundle until Phase 2.
* Viewer now. Good, because the trail is visible at once. Bad, because the screen would be rebuilt with the Phase 2 UI.

### First release channel

* Universal APK on GitHub Releases (chosen). Good, because one file serves phones and TVs, and Obtainium follows it. Bad, because the file is larger than per-ABI APKs.
* Also Google Play internal testing. Good, because it starts the store path early. Bad, because it needs the separate upload key, a store listing and the data safety form, which the roadmap places in Phase 5.

## More Information

* Related: [0020](0020-logging.md), [0021](0021-audit.md), [0011](0011-datastore-room-and-keystore-for-new-persistence.md), [0022](0022-crash-reporting-and-privacy.md), [0023](0023-distribution-channels.md) and [0026](0026-signing-keys.md).
* Policy: [privacy-and-logging.md](../policies/privacy-and-logging.md). User statement: [PRIVACY.md](../../PRIVACY.md).

Amended by [0051](0051-send-the-diagnostic-bundle-to-a-phone-with-a-qr-code.md).
