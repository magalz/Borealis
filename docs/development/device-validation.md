# Device validation runbook

This runbook checks Borealis Phase 1 and Sprints 2 to 6 on two devices against
LuminalShine 26.09.1 running on the owner's Windows PC. It is for the owner and
for the orchestrator who runs the checks. Run the steps in order and record the
results as a new section with a table, like sections 7 to 10. Sections 6 to 11
hold the results of Phase 1 and Sprints 2 to 6, in that order.

Sources: [host contract](../host-contract/luminalshine.md) (cited as "contract"),
[Borealis Android README](../../apps/android/borealis/README.md),
[building guide](building.md) and the source files named in each step. Anything
that the repository does not confirm is marked `TODO(verify)`; check it on the
device before relying on it.

Placeholders: `<host>` is the LAN address of the PC, `<serial-s22>` is the ADB
serial of the S22, `<serial-tv>` is `<ip>:<port>` of the Google TV Streamer and
`<apk>` is the debug APK. Never type real addresses, PINs, certificates or
pairing codes into this file or into any capture that is committed.

## 1. Prerequisites

### 1.1 Device setup

1. On the S22, open Settings, About phone, Software information and tap Build
   number seven times to enable Developer options. `TODO(verify)`: menu names
   depend on the One UI version.
2. On the S22, open Developer options and turn on USB debugging. Connect the
   phone with a USB cable and accept the debugging prompt on the phone.
3. On the PC, run `adb devices` and note the S22 serial as `<serial-s22>`.
4. On the Google TV Streamer, connect it to the same LAN as the PC. Open
   Settings, System, About and select Android TV OS build seven times to enable
   Developer options. `TODO(verify)`: menu names depend on the Google TV version.
5. On the Streamer, open Developer options and turn on Wireless debugging.
6. Open Pair device with pairing code. The screen shows an IP address, a
   pairing port and a six-digit code. Do not copy them into any file. On the
   PC, run `adb pair <ip>:<pairing-port>` and type the code when asked.
7. Back on the Wireless debugging screen, read the connect port. It differs
   from the pairing port. On the PC, run `adb connect <ip>:<connect-port>`.
8. Run `adb devices` and note the Streamer as `<serial-tv>`, which is
   `<ip>:<connect-port>`.

### 1.2 Build and install the debug APK

1. Follow the toolchain setup of [building.md](building.md) (JDK 17 and the
   Android SDK).
2. From the repository root, run:

   ```sh
   cd apps/android
   ./gradlew assembleNonRootDebug
   ```

3. The debug APK is
   `apps/android/app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`
   (checked on the 2026-10-14 phase branch build); it is `<apk>` below.
4. The debug build has the application id `io.github.magalz.borealis.debug` and
   the label `Borealis Debug` (`build-logic`, `BorealisAndroidApplicationPlugin`).
5. Install on the S22:

   ```sh
   adb -s <serial-s22> install -r <apk>
   ```

6. Install on the Streamer:

   ```sh
   adb -s <serial-tv> install -r <apk>
   ```

7. Check the installed build on each device. Record the `versionName` and the
   git commit of the tested tree:

   ```sh
   adb -s <serial> shell dumpsys package io.github.magalz.borealis.debug
   ```

### 1.3 Host

1. Start LuminalShine 26.09.1 on the PC. The tested build is the one that
   carries the fixtures in `core/tests/fixtures/luminalshine/26.09.1/`, with
   PyroWave off (default configuration, see its `PROVENANCE.md`).
2. Record the LuminalShine release from the host installation. `TODO(verify)`:
   `/serverinfo` does not carry the release version (contract section 1 and
   proposal P1), so the version in the PC details dialog is the protocol
   version `7.1.431.-1`, not `26.09.1`.
3. Open `https://<host>:47990` (the Web UI) in a browser on the PC and check
   that it loads. Accept a self-signed certificate warning if the browser shows
   one. `TODO(verify)`: whether the browser warning appears.
4. Make sure the PC firewall lets the devices reach the ports of contract
   section 2: TCP 47984 (HTTPS), 47989 (HTTP), 47990 (Web UI) and 48010 (RTSP),
   and UDP 47998 (video), 47999 (control) and 48000 (audio). If the PC has
   firewall block rules for the Web UI ports scoped to the `Internet` remote
   address keyword, the Wi-Fi network must use the Private profile: on a
   Public profile Windows applied them to the LAN, and pairing failed at the
   HTTPS phase with a connect timeout on TCP 47984.
5. Keep the host log open. Its location is the owner's choice; this runbook
   refers to it as "the host log" only.
6. At the start of each scenario, no app runs on the host unless the scenario
   says so. Use the Web UI or the app list to quit a running app.

### 1.4 Logcat capture

Use one capture per device and per scenario. Clear the buffer before each
scenario so that the lines belong to it:

```sh
adb -s <serial> logcat -c
adb -s <serial> logcat -v time Borealis.APP:V Borealis.HOST:V Borealis.PAIR:V Borealis.LAUNCH:V Borealis.RTSP:V Borealis.VIDEO:V Borealis.PYROWAVE:V Borealis.INPUT:V Borealis.REST:V Borealis.CORE:V Borealis.UPSTREAM:V Borealis.COMMONC:V moonlight-common-c:V *:S > <capture-outside-repo>.log
```

Borealis logs use the tag `Borealis.<TAG>` (`LogcatSink`). The debug build logs
at DEBUG and above; release logs at INFO (`BorealisApplication.onCreate`). The
upstream native library (moonlight-common-c) logs under `Borealis.COMMONC`:
`BridgeClLogMessage` in `callbacks.c` hands each line to `BorealisLog`, so it is
redacted and also reaches the log file and the diagnostic bundle. `COMMONC` is rate
limited (a burst of 100 lines, then 10 lines per second); a line `N lines suppressed`
marks what was left out, so a frame-loss burst shows as that line plus a counter,
not as every loss. The old logcat tag `moonlight-common-c` still appears for lines
that are not forwarded: those logged before `BorealisLog` is initialised, those where
forwarding fails, and the direct `__android_log_print` calls in `simplejni.c`; keep
both tags in the filter. The upstream Java logger `LimeLog` is
forwarded to `Borealis.UPSTREAM` (`JulBridge`).

Log lines are `message key=value`, for example
`host asked to retry category=PENDING attempt=1 max=K delay_ms=N`
(`LogFormatter.formatBody`). Values with spaces are quoted.

## 2. Instrumented tests on each device

These tests need a device or an emulator. They run the packaged native libraries
through JNI and check the golden vectors of `core/tests/fixtures/vectors/`.

1. From `apps/android`, select one device with `ANDROID_SERIAL` and run the
   tests:

   ```sh
   cd apps/android
   ANDROID_SERIAL=<serial-s22> ./gradlew :borealis:native:connectedDebugAndroidTest :app:connectedNonRootDebugAndroidTest
   ```

2. Run the same command with `ANDROID_SERIAL=<serial-tv>`.
3. AGP honours `ANDROID_SERIAL`: the connected tasks run only on that device
   (checked 2026-10-14). In PowerShell, set it with
   `$env:ANDROID_SERIAL = "<serial>"`. On Windows, a device connected over
   ADB Wi-Fi has an `<ip>:<port>` serial, and the connected task then reports
   `There were failing tests` even when every test passed (it cannot write
   the result file named after that serial). For such a device, install the
   test APKs and run the instrumentation directly, then check that every test
   reports `INSTRUMENTATION_STATUS_CODE: 0` and the run ends with
   `INSTRUMENTATION_CODE: -1`:

   ```sh
   adb -s <serial-tv> shell am instrument -r -w -e expectedCoreVersion 0.1.0 io.github.magalz.borealis.nativecore.test/androidx.test.runner.AndroidJUnitRunner
   adb -s <serial-tv> shell am instrument -r -w -e expectedCoreVersion 0.1.0 io.github.magalz.borealis.debug.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Expected: all tests pass. They are:
   - `BorealisCoreInstrumentedTest.versionIsTheCoreProjectVersion`: the core
     version equals the one in `core/CMakeLists.txt` (`0.1.0` at this revision).
   - `GoldenVectorsInstrumentedTest`: `hostCapsVectorsMatch`,
     `launchDecisionVectorsMatch` and `launchFpsVectorsMatch` replay
     `host_caps.tsv`, `launch_decide.tsv` and `launch_fps.tsv` through
     `NativeHostRules`.
   - `JniGuardsInstrumentedTest`: `parseCapsRejectsAnAppVersionOver64Bytes`
     and `decideLaunchRejectsAMessageOver256Bytes` (the JNI length guards).
   - `NativeBridgeInstrumentedTest` in `:app`:
     `ndkBuildBridgeSeesTheCoreProjectVersion` and `bridgeAndModuleApiAgree`.
5. Record the Gradle result and the serial. If a test fails, stop the scenarios
   on that device and report the failure first.

## 3. End-to-end scenarios

Each scenario lists the steps, the expected result and the evidence to record.
Run each one on both devices unless it says otherwise. Record the logcat capture
of section 1.4 for each scenario.

### 3.1 Pairing

Pairing runs on the Pairing screen of the Library (Compose). The 12.2 PC menu
entry is gone.

1. On the device, open Hosts and choose the PC. Its card shows "Found on this
   network" and the Pair action. Choose Pair.
2. Read the Pairing screen. It shows the PIN, the note "The PIN stays valid for
   5 minutes." and four numbered steps:
   - "Open the LuminalShine Web UI at this address." The address,
     `https://<host>:47990`, is shown under the step. When the address is not
     known, the step reads "Open the LuminalShine Web UI on your PC." The
     address is the HTTP port plus 1 (`PairingHints.webUiUrl`).
   - "Go to the Clients page."
   - "Enter this PIN and a device name, for example "`<name>`". The suggested
     name is the device model without "xbox" and "webos", or `Borealis`
     (`PairingHints.suggestedDeviceName`).
   - "Avoid "xbox" or "webos" in the name. The host treats those devices
     differently." The name must not contain "xbox" or "webos" (contract 10.2).
   Do not copy the PIN into any file.
3. On the PC, open the Web UI at the address of step 2, go to the Clients page,
   and enter the PIN and the device name. `TODO(verify)`: the Web UI page and
   control names on the 26.09.1 build.
4. Expected: after the PIN is accepted, the device pairs and the Apps of the PC
   open. The Web UI lists the device under the name that was typed (contract
   4.1: the host takes the name from the Web UI, not from the device). The
   Sprint 5 full pairing on the S22 showed this result.
5. Evidence: a screenshot of the Pairing screen with the PIN cropped out, a Web
   UI screenshot with the PIN hidden, and the logcat capture.

### 3.2 Wrong PIN

1. Start pairing as in 3.1 and read the PIN on the device.
2. In the Web UI, enter a different four-digit PIN and any name.
3. Expected: the Pairing screen shows "The PIN was wrong." (the `PIN_WRONG`
   failure). It must not show the 404 text of the host or the generic "Pairing
   failed.".
4. Expected in logcat: a WARN line `unpair not confirmed status=404` on
   `Borealis.PAIR`. The best-effort `/unpair` after a wrong PIN ignores its
   outcome, so the 404 does not replace "The PIN was wrong.".
   `TODO(verify)`: that this line still appears on the Compose pairing path.
   Expected in the audit trail (3.15): a `PAIRING_FAILURE` entry with
   `reason=wrong_pin` (`PairingAudit`).
5. Start pairing again as in 3.1. The PC may answer 400 `Out of order call to
   getservercert`, because it keeps the pairing session after the wrong PIN
   (section 6). Expected: the Pairing screen shows the note "The PC cleared an
   unfinished pairing. Use this new PIN." once, with a new PIN. Enter the new
   PIN in the Web UI. Expected: the device pairs.
6. If the restarted attempt fails too, expected: a normal failure message and no
   second new PIN. The scenario must not loop. Record the message.
7. If the PC answers another pairing error, expected: a specific message, not
   the generic "Pairing failed.". Record the message.
8. If the device is not paired at the end of the scenario, run 3.1 with the
   correct PIN.
9. Evidence: a screenshot of each message, the logcat capture and the audit
   entry of each rejected pairing (scrubbed).

### 3.3 Unpair

Two places remove the pairing. The Forget action of the PC's card on the device
removes the PC from this device. Removing the device in the LuminalShine Web UI
(Clients page) ends the pairing on the PC. LuminalShine does not route `/unpair`
(contract sections 3 and 18, item 4), so the device must be removed in the Web
UI.

1. Open Hosts, choose Forget on the PC's card (`TODO(verify)`: how the Forget
   action opens on the TV) and read the dialog.
2. Expected: the dialog reads "Forget `<name>` on this device? The PC may keep
   trusting this device until you remove it in the LuminalShine Web UI (Clients
   page)." Choose Forget. Expected: the PC
   is no longer listed on this device.
3. Expected on the PC: the device is still paired, because `/unpair` is not
   routed. Sprint 5 (S22 full pairing) recorded the host answer 404 and the audit
   entry `UNPAIR_NOT_CONFIRMED` with `reason=host_status_404`.
4. Expected in logcat: a WARN line `unpair not confirmed status=404` on
   `Borealis.PAIR`. `TODO(verify)`: that the line still appears after Forget.
5. Remove the device in the LuminalShine Web UI (Clients page). Record whether the
   PC still shows as paired after the Web UI removal. `TODO(verify)`: the
   device-side state after a Web UI removal. To pair again, add the PC by its
   address (Add host). Expected: the card shows "Not paired" and the Pair action.
6. Evidence: a screenshot of the Forget dialog, a Web UI screenshot with the
   device list hidden, and the logcat capture.

### 3.4 PC details dialog

1. The PC details dialog went away with `PcView` in Sprint 5. With the device
   paired, read the PC's card on Hosts instead: it shows the address and the
   codec and HDR tags (`HostInfoText`).
2. The full capability text below is still built by `HostInfoText` and is in
   the diagnostic bundle.
3. Expected for the 26.09.1 capture (`serverinfo-http.xml`, codec mask
   `0x001C0301`, codec flags `0x13` in `host_caps.tsv`):
   - `Host software: Sunshine-compatible, version 7.1.431.-1`
     (`host_info_software_version`; the version is the `appversion` of
     `/serverinfo`, not the LuminalShine release)
   - `Video: HEVC, HEVC 10-bit, 4:4:4` (no AV1 entry)
   - `PyroWave: off` (`host_info_pyrowave_off`)
   - `Ports: HTTP 47989, HTTPS 47984` (`host_info_ports`; the HTTP port is the
     one the client stores, the HTTPS port comes from `HttpsPort`)
4. Expected in logcat: one INFO line `host capabilities` on `Borealis.HOST` with
   a `family` field (`HostInfoReader`). `TODO(verify)`: the full field list in
   `HostInfoReader.kt`.
5. Evidence: a screenshot of the dialog and the logcat capture.

### 3.5 Two devices launching at the same time

1. Pair both devices with the host (3.1). Make sure no app runs on the host.
2. Start the same app on the S22 from its app list.
3. Within about 2 s, start the same app on the Streamer. The host holds a pending
   launch until it is claimed by RTSP or until `ping_timeout` (10 s by default,
   contract 7.5), so the second request must arrive inside that window to get
   the 409.
4. Expected for the second request: the host answers 409 "Another client launch
   is already pending; retry the connection." (contract 7.5). The device shows
   "Another device is starting a stream on this host. Retrying in N s (retry M of
   K)." (`launch_retry_pending`). The retries have the category `PENDING`.
5. Expected end of the retries, one of:
   - the device streams, or
   - the device shows a clear failure after the retry budget (60 s,
     `DEFAULT_BUDGET_MS` in `LaunchRetryRunner`). `TODO(verify)`: the exact text
     of the final failure dialog.
6. Expected for a 400 "An app is already running on this host" (the Phase 1
   result on the Streamer, section 6): Borealis re-reads `/serverinfo` for up to
   3 s. If no app runs any more, it launches again. Otherwise it shows the dialog
   "An app is already running" with the buttons Join, Quit and relaunch, and
   Cancel (`apps/android/app/src/main/res/values/strings_borealis_conflict.xml`).
   Focus starts on Cancel. The text warns that quitting ends the session on the
   other device.
7. Run the three answers, each in a new attempt. Start the same app on the
   Streamer while the S22 runs it, and answer the dialog as follows:
   - Join: the device joins the running session and streams.
   - Quit and relaunch: the S22 session ends and the app starts on the Streamer.
   - Cancel: the device returns to the app list with no error dialog.
8. Expected: the client sends no `/cancel` request during the retries. The retry
   code never calls it (README, `host.launch`). `TODO(verify)`: the host log line
   that a `/cancel` request writes, so that the owner can check for it.
9. Caveat: if the second device asks for a different app while the first one
   streams, Moonlight cancels and then launches (contract 7.6). That is the
   upstream behaviour and not a retry. Record it as a separate observation.
10. Evidence: the logcat line `host asked to retry category=PENDING attempt=1 max=K
    delay_ms=N` (fields as in `LaunchRetryRunner`), the final result of each
    answer in step 7, and the host log.

### 3.6 Relaunch right after quitting

1. Stream an app on the device (3.9 steps 1 and 2 apply).
2. In the app list of the PC, choose "Quit Session" (`applist_menu_quit`) and
   wait for "Successfully quit" (`applist_quit_success`).
3. Immediately launch the same app again from the PC's app list.
4. Expected: either the launch succeeds at once, or the device shows "The host is
   still restoring the display after the previous stream. Retrying in N s (retry
   M of K)." (`launch_retry_restoring_display`) and then streams. The host answers
   503 "The previous stream is still restoring the display; retry the connection."
   after up to 8 s of waiting (contract 7.5).
5. The 503 depends on timing. If no retry happens, record the time between the
   quit and the launch and mark the scenario "not reproduced". This is not a
   failure.
6. Evidence: the logcat line `host asked to retry category=RESTORING_DISPLAY ...`,
   the time between quit and launch, and a screenshot.

### 3.7 Frame pacing "cap FPS" at the display rate

1. In Settings, set "Frame pacing" to "Balanced with FPS limit"
   (value `cap-fps`, `pacing_balanced_alt`).
2. Set the stream frame rate to the refresh rate of the device. Record that rate.
   The mismatch of contract 7.4 comes from a selected rate at or up to 3 Hz above
   the refresh rate.
3. Launch an app and stream for at least 30 s.
4. Expected in the host log: `Requested display mode reconciled: HTTP and RTSP
   agree at <width>x<height>@<fps>Hz.` (INFO, `rtsp.cpp:80`). This line needs a
   virtual display and no per-client display mode override (`rtsp.cpp:78-79`).
5. Expected in the host log: no line that starts with `Requested display mode
   changed between HTTP launch (` (WARN, `rtsp.cpp:86`). That line means the
   client sent two different modes and the host rebuilt the virtual display.
6. If the host uses a physical display, neither line appears. Record the scenario
   as "not applicable" with that note.
7. Evidence: the host log lines with their time, and the device refresh rate.

### 3.8 Cancel during a launch retry with Back, then launch again

1. Produce a retry as in 3.5 or 3.6, so that the spinner shows the retry text.
2. Press Back while the spinner is shown.
3. Expected: the launch ends without a stream. No further `host asked to retry`
   line appears in logcat after Back. The client sends no `/cancel`, as in 3.5
   step 6. `Game` calls `cancelLaunch()` from `onStop` and `onDestroy`
   (`Game.java`, README `host.launch`).
4. Launch the same app again from the PC's app list when the host is free.
5. Expected: the second launch proceeds and reaches the first frame (3.9). A
   launch that hangs on the spinner with no retry line and no stream is a
   failure. It would point to a permit that was not released.
6. Evidence: the logcat capture across both launches and a screenshot of the
   first frame.

### 3.9 First frame

1. Start a stream from a host with no app running. Note the start time.
2. Expected: the overlay shows "The host is preparing the display."
   (`stream_preparing_display`) from the start until the first video frame
   (`PreparingMessage`), then the overlay hides.
3. Expected in logcat: one line `Received first video packet after N ms` with
   tag `Borealis.COMMONC` (`VideoStream.c`, the `Limelog` call). Note the N.
   `N` is measured inside moonlight-common-c, so it is not the same as the
   wall-clock time that you note.
4. The contract says the first frame can need about 18 s on a slow host
   (contract 10.1). The watchdog ends a stream with no frame after 45 s (README,
   `host.video`). If it does, the message gets the hint "LuminalShine may still be
   preparing its display or encoder. Try again. If it keeps happening, restart
   the host or check its virtual display settings." (`stream_luminalshine_hint`).
5. Evidence: the N value, the wall-clock time to the picture, a screenshot of the
   overlay and the logcat capture.

### 3.10 Stream end and reconnect

1. Stream an app for about one minute.
2. End the stream from the device. `TODO(verify)`: the in-stream menu item.
3. Wait until the PC list is shown, then launch the same app again.
4. Expected: the app still runs on the host, so the client resumes it (`/resume`)
   and the stream starts. If the host answers "No running app to resume" (503),
   the client falls back to `/launch` (contract 7.5, `FALLBACK_TO_LAUNCH`).
5. Evidence: the logcat capture, the first-frame line of 3.9 and a screenshot.

### 3.11 H.264 and HEVC

1. Set the video format to "Prefer H.264" (`neverh265`) and stream for one minute.
   Then set "Prefer HEVC" (`forceh265`) and stream for one minute. Use the same
   app. "Automatic (Recommended)" is the default (`auto`).
2. Expected for H.264: the stream plays. Logcat has no `HEVC SPS` line.
3. Expected for HEVC: the stream plays. Logcat has one line in the form
   `HEVC SPS: profile=<name> chroma=<name> depth=<luma>/<chroma>` on
   `Borealis.VIDEO` (`HevcSpsInfo.describe` and `HevcSpsMonitor`). For an 8-bit SDR
   stream the line is `HEVC SPS: profile=Main chroma=4:2:0 depth=8/8`.
4. AV1: not applicable to this host. The 26.09.1 capture does not advertise AV1
   (codec mask `0x001C0301` has no AV1 bits, and the PC details dialog has no AV1
   entry). Do not test "Prefer AV1 (Experimental)" against this host and record
   it as "not applicable".
5. Evidence: the logcat lines of step 3 and the screenshot.

### 3.12 HDR and HEVC Main10

1. Enable "Enable HDR (Experimental)" (`title_enable_hdr`) and set the video format
   to "Prefer HEVC". Stream an HDR-capable app. `TODO(verify)`: the app and the
   HDR-capable display of each device. The 26.09.1 capture advertises HEVC Main10
   (codec flag `0x02`).
2. Expected: a line `HEVC SPS: profile=Main10 chroma=4:2:0 depth=10/10` on
   `Borealis.VIDEO` (or the chroma name that the host sends).
3. Expected: no WARN line `the stream is 10-bit but the decoder does not declare
   HEVC Main10: expect a black screen or wrong colours`. If it appears, the device
   does not declare Main10 and HDR fails on that device.
4. Expected with HDR off: an INFO line `the session did not request HDR but the
   HEVC stream is 10-bit (the host may be sending 10-bit SDR)` appears only when
   the host sends 10-bit SDR (contract 9.2). Record it when it appears.
5. Check the colours on screen. Borealis logs do not show the HDR state from the
   host (contract 9.3 says to trust control message `0x010e`). `TODO(verify)`:
   how to check the state on the device.
6. Evidence: the logcat lines of steps 2 to 4, a screenshot and the device model.

### 3.13 Settings

1. Phone: on Hosts, tap the gear, then Settings. TV and handheld: in the rail,
   choose Settings. The groups are on the left and their rows on the right.
2. Expected: the groups are Stream, Video, Input, Gamepad, On-screen controls,
   Audio, Advanced, Privacy and diagnostics, and About. The On-screen controls
   group is shown only on a phone (a device with a touchscreen). A group the
   device does not use is not in the list. On a phone, Privacy and diagnostics
   is a link row.
3. Expected: each row shows its current value. A row that another setting
   disables can be focused on a TV. It cannot be changed, and its note says why.
4. On a TV, the D-pad Right enters the rows of the selected group and Left
   returns to the groups. Back leaves the screen from the rows and from the
   groups.
5. Expected in "Privacy and diagnostics": on a TV the group shows its rows in the
   right pane. On a phone, the link row opens the Privacy screen. Its Audit trail
   row opens the viewer (3.15). The rows are described in 3.14.
6. Expected in "About":
   - the Version row shows the version name and the commit, and cannot be clicked;
   - "Source code" opens `https://github.com/magalz/Borealis`;
   - "Licence: GPL-3.0" is plain text;
   - "Open-source licences" shows the NOTICE and LICENSE texts in a dialog, which
     a D-pad can scroll;
   - "Privacy policy" opens `https://github.com/magalz/Borealis/blob/main/PRIVACY.md`.
   A link that no app can open shows its address as text in a dialog.
7. Evidence: screenshots without addresses, and the version line.
   `TODO(verify)`: the exact version text on each device.

### 3.14 Export diagnostics

The screen is "Privacy and diagnostics": Settings, then the link row on a phone
or the group on a TV, then the row "Export diagnostic bundle". Steps 1 to 6 are
for the phone, steps 7 and 8 for the TV, steps 9 to 11 for both, and step 12
for the TV. Never put a bundle from a real run into the repository.

1. On the phone, choose "Export diagnostic bundle" (its summary reads "Logs,
   decoders and the audit trail, already redacted"). A progress dialog
   "Creating the diagnostic bundle…" appears, then the share sheet. Back closes
   the progress dialog, and the export goes on.
2. Send the zip to a test target (Files or Drive). Unzip it on the PC.
3. Expected: the layout is `manifest.json`, `decoders.json`, `audit.jsonl` and
   `logs/*`.
4. Expected: no PIN, token or key appears in any entry. Search for the PIN of 3.1
   and for any `token`, `key` or `cert` value.
5. Expected with "Mask addresses in logs and diagnostics" on: no IP address and no
   host name appears in any entry.
6. Expected: `decoders.json` lists the H.264, HEVC and AV1 decoders with profile
   names, and the display HDR types.
7. On the TV, choose "Export diagnostic bundle" (its summary reads "Saves it to
   Downloads and offers Send to phone"). The progress dialog appears, then the
   "Diagnostics saved" dialog. It shows the file name, the location in Downloads
   and the `adb pull` command under "To copy it to a computer, run". The share
   sheet must not open by itself. Close has the focus.
8. On the PC, run the `adb pull` command of the dialog, or:

   ```sh
   adb -s <serial-tv> pull /sdcard/Download/borealis-diagnostics-<timestamp>.zip
   ```

   Expected: the zip opens and has the layout of step 3. On Android 9 (API 28) the
   file is in the app's files directory instead, and the dialog shows the
   matching `adb pull /sdcard/Android/data/<package>/files/...` command. Use the
   package name of the build that you installed.
9. Export twice. Expected: only the newest bundle remains (in `cacheDir` and in
   Downloads). Files of other apps in Downloads are not touched.
10. Choose "Export diagnostic bundle" twice quickly. Expected: the second choice
    does nothing while the first bundle is built.
11. Leave the screen (Back) while the bundle is built. Expected: no crash in the
    logcat capture (no `FATAL EXCEPTION` line).
12. Send to phone (TV only). In the "Diagnostics saved" dialog, choose "Send to
    phone". Expected: a QR code and the link text, the file name and size, and a
    countdown. Scan the QR code with the phone, and download the zip. Expected on
    the TV: "Downloaded. The link is closed." Then check the two failure states:
    the link expired ("The link expired. Try again for a new one.") and a device
    that is not on a local network ("...Use the adb command below instead.").
    The phone has no Send to phone entry: export on a phone shares the zip.
13. Evidence: the unzipped layout (a listing, no contents), the logcat capture, and
    a screenshot of the dialog or the share sheet.

### 3.15 Audit trail

The viewer is in Settings, then "Privacy and diagnostics", then "Audit trail".

1. Pair the S22 with the PC (3.1), launch an app (3.9 steps 1 and 2), stream it
   for about one minute, and then quit it from the actions of the running app in
   Apps. Quit asks for confirmation.
2. Export a bundle (3.14, steps 1 and 2).
3. Expected in `audit.jsonl`: entries of the types PAIRING_*, LAUNCH_* and
   SESSION_START and SESSION_END, with the fields documented in
   [privacy-and-logging.md](../policies/privacy-and-logging.md) (section "Audit
   trail").
4. Expected: no PIN, token, key or certificate in any entry. With "Mask addresses"
   on, no IP address and no host name.
5. Open the Audit trail on the device. Expected: the entries are listed newest
   first. Each entry shows its time, its title in plain words, its event code and
   a short detail. A host certificate change has its title in the warning colour.
   An empty trail says "No events recorded yet.". On a TV, the first entry has
   the focus, Down and Up move from entry to entry, and OK opens the entry. On a
   phone, a tap opens the entry. The opened entry shows its time, title, code,
   result, host and detail. Back closes the opened entry before it leaves the
   screen.
6. With "Mask addresses in logs and diagnostics" on, the host and the detail of
   each entry show no full IP address.
7. Open "Erase audit trail" (Privacy and diagnostics). Expected: a confirmation
   "Erase the audit trail?" with Cancel focused. Choose Cancel first: nothing is
   erased. Then open it again and choose "Erase": "Audit trail erased" appears.
8. Export a bundle. Expected: `audit.jsonl` has no entries from before the erase.
   The bundle exported in step 2 is not changed.
9. Evidence: the unzipped `audit.jsonl` (scrubbed), and screenshots of the list,
   an opened entry and the erase dialog, without addresses.

### 3.16 Stream logs

1. Start a stream as in 3.9 steps 1 and 2, with the logcat capture of 1.4.
2. Expected in logcat: lines with the tag `Borealis.COMMONC`, such as
   `Received first video packet after N ms`. A line `N lines suppressed` can
   appear during frame loss, because `COMMONC` is rate limited.
3. Export a bundle (3.14, steps 1 and 2). Expected: `logs/borealis.log` contains
   the `COMMONC` lines.
4. Evidence: the logcat lines and the matching lines of the log file, scrubbed.

### 3.17 Network privacy

1. Start the logcat capture of 1.4. Add the PC by its address (`TODO(verify)`:
   the menu entry for this). Let the add finish or fail.
2. Expected: no "blocked ports" connection test result and no delay beyond the
   normal timeout. The lookup of an external address and the connection test
   return at once, because their JNI entry points are disabled
   (`simplejni.c`, README `:borealis:settings`).
3. Expected: the PC menu has no "Test network connection" entry.
4. Connect to a PC that is off or blocked. Expected: the normal error message, with
   no "blocked ports" result.
5. `TODO(verify)`: how the owner can confirm on the PC that no DNS or HTTP request
   reaches `moonlight-stream.org`, for example with a firewall or DNS log.
6. Evidence: the logcat capture, the screenshots and the check of step 5.

### 3.18 Release build smoke test

1. Get the R8 release APK from the release dry run (artifact
   `DRY-RUN-not-a-release`, [release.md](release.md) section 3). It is signed with a
   throwaway key. Uninstall it before you install any real release. The package is
   `io.github.magalz.borealis`, with the label "Borealis". Call the file
   `<release-apk>` below.
2. Install it next to Moonlight (`com.limelight`) and the debug build
   (`io.github.magalz.borealis.debug`):

   ```sh
   adb -s <serial> install <release-apk>
   ```

3. Expected: the install succeeds, the three apps are installed side by side, and
   Borealis starts.
4. With the release build, pair (3.1), stream (3.9) and export diagnostics (3.14).
   Expected: each step works as in the debug build.
5. `TODO(verify)`: whether this smoke test runs on both devices.
6. Evidence: `adb -s <serial> shell dumpsys package io.github.magalz.borealis`
   (the `versionName` and the commit), the logcat capture and a screenshot.

### 3.19 Sprint 6 checks

Run these on the S22 phone and the Google TV Streamer with the Sprint 6 build.
Record each item as `pass`, `fail` or `not applicable`, with a short note for
each `fail`.

1. Settings, every row: the D-pad two-pane layout on the TV, and the phone list.
   - The Resolution and Frame rate lists match the device.
   - Changing Resolution or Frame rate returns Bitrate to "Automatic".
   - Turning Unlock all frame rates on and off changes the Frame rate list.
   - HDR shows only on HDR displays.
   - The On-screen controls group appears only on the phone: check Opacity, and
     that Reset layout asks first.
   - Codec, Flip face buttons, Deadzone and Play audio on the PC take effect in a
     stream.
2. Upgrade: Verbose logging and Mask addresses, switched on in the Sprint 5 build,
   are still on after installing the Sprint 6 build.
3. Privacy and diagnostics:
   - phone: Export opens the share sheet;
   - TV: Export saves, and the "Diagnostics saved" dialog shows the location and
     the `adb pull` command;
   - Send to phone: scan the QR code with the phone and download, and check the
     Expired and No network states;
   - Audit trail: lists pairing, launch and stream events, masks addresses with
     Mask addresses on, and Erase asks first.
4. Follow-ups:
   - (a) On the TV, the Add host keyboard opens on OK, not when the field gets
     focus. The comment of `AddHostScreen` says so. Sprint 5 (section 10, R1)
     saw it open on focus. Whether `SoftwareKeyboardController.show()` opens the
     Google TV keyboard is checked on the device. UC-008 wants the keyboard to
     open on OK on a TV.
   - (b) An RTSP -1 failure shows no firewall hint.
   - (d) With the USB driver setting off, the USB attach dialog does not appear.
   - (e) The pinned app shortcut icon: the app's box art when the PC sent it,
     otherwise the Borealis monitor icon.
   - (f) A pinned app shows its box art.
   - (g) The performance overlay shows a decode time on the Streamer.
   - (h) An 8BitDo pad on the TV, with the Windows game controller panel (joy.cpl)
     open on the PC:
     - Start or Select alone reaches the PC about 60 ms late;
     - holding Select and Start opens the menu, and no stray press reaches the PC;
     - the quit combo and the Start-hold gamepad mouse still work.
   - (i) Two pads: the menu's Gamepad mouse row shows On when either pad emulates
     the mouse.

The UI is in English on a phone set to Portuguese, because translations were
removed (ADR 0055). Expected: no Portuguese text.

### 3.20 Sprint 7 checks

Run these with the Sprint 7 build on the S22 phone and the Google TV Streamer.
Where a step names one device, run it there only. Record each item as `pass`,
`fail` or `not applicable`, with a short note for each `fail`. For a
USB-driver pad, turn USB driver for Xbox controllers on in Settings, Gamepad.
For a system pad, turn it off. Say which kind each run used.

1. Pointer speed, on the Streamer with a pad. Turn Gamepad mouse on. Keep
   Gamepad mouse speed at 50 % for the first runs. Open the PC's Windows game
   controller panel (joy.cpl) when a step checks that no button reaches the PC.
   - Default: move the left stick slowly, then to full deflection. Expected: the
     pointer moves smoothly from the first move, with no jump at the start.
   - Deadzone: push the stick just past the deadzone and hold it. Expected: the
     pointer moves slowly, and does not stay still.
   - Slow diagonal: hold the stick at a slow diagonal. Expected: the pointer
     moves on both axes and keeps the diagonal.
   - RB precision: hold the stick at full deflection, then hold RB as well.
     Expected: the pointer moves at about a quarter of the speed while RB is
     held. No button press shows in joy.cpl.
   - Slider: in Settings, Input, set Gamepad mouse speed to 10 %, then to 200 %,
     and start a stream each time. Expected: the pointer is slow at 10 % and
     fast at 200 %. A change made during a stream does not change that stream.
   - Menu row: open the menu with Back. Expected: Pointer speed shows under
     Gamepad mouse. Choose Slow, then Normal, then Fast, and move the stick at
     each. Expected: about half, the setting, and twice the speed. Turn Gamepad
     mouse off in the menu: the row goes. End the stream and start a new one:
     the row shows Normal.
2. Right-stick scrolling, with Analog scrolling at Right (the default). In a
   PC app that scrolls, such as a browser page, hold the right stick up and
   down. Expected: the page scrolls. Compare Slow and Fast in the menu row.
   Expected: Fast scrolls faster than Slow, as the pointer does.
3. The quit combo.
   - USB-driver pad: press Start, Select, LB and RB together, then let all four
     go. Expected: the stream ends once all four are let go. Hold Start alone
     for the combo: the gamepad mouse must not turn on. Check on the PC that the
     app still runs; the combo leaves the stream only.
   - System pad: the same four buttons, then let all four go. Expected: the
     stream ends. Then check the Start and Select taps of 3.19 (h).
4. The decode time.
   - On the Google TV Streamer (MediaTek decoder) and on the S22: turn the
     Performance overlay on, in Settings or in the menu, and start a stream.
     Expected: the decode time shows a number in milliseconds with one
     decimal ("Decode 0.4 ms"), not "—". Record the number.
   - Verbose logging: in Settings, Privacy and diagnostics, turn Verbose logging
     on. Start the logcat capture of 1.4 and run a stream for about 10 seconds.
     Expected: a `Decoder window` line at DEBUG, about once a second, with the
     tag `Borealis.VIDEO`. Its fields are `codec`, `in`, `out`, `exact`,
     `truncated`, `zero`, `ahead`, `older`, `unmatched`, `decoded`,
     `decode_avg_us`, `decode_min_us` and `decode_max_us`. Record `in`, `out`,
     `exact` and the three decode values. A decoder that echoes the timestamps
     has `exact` equal to `out`.
5. Visual refinement (placeholder). The steps for these items are not written
   yet. A later task fills them in from the owner's contact sheet of every
   screen.
   - Dialogs.
   - Icons.
   - Host cards.
   - Loader.
   - Pairing check.
   - Error actions on the phone.
   - TV keyboard row.
6. The USB dialog on the Streamer (R5a). This is an observation only; no code
   changes.
   - Clear the logcat buffer and start the capture of 1.4 on the Streamer, with
     the USB driver on.
   - Plug in the 8BitDo receiver and note the attach lines, and the vendor and
     product IDs that they show. The receiver changes its identity by itself
     (idle `2dc8:6013`, active `2dc8:310b` XInput), and each change is a new
     attachment. Note whether Android's "open with Borealis" dialog appears.
     Android's system server logs these lines with the tag `UsbHostManager`
     ("USB device attached", "Removed device at"); it is not a Borealis class.
   - Decline the dialog, then start a stream. Expected: the stream start asks for
     USB permission again.
   - End the stream. Expected: no attach or remove line appears at the end of the
     stream.

## 4. Results templates

Copy one table per device. Use `pass`, `fail` or `not applicable`, and add a
short note for each `fail`.

### 4.1 Samsung Galaxy S22 (USB ADB)

| Scenario | Result | Evidence | Notes |
| --- | --- | --- | --- |
| 2. Instrumented tests | | Gradle result, serial | |
| 3.1 Pairing | | | |
| 3.2 Wrong PIN | | | |
| 3.3 Unpair | | | |
| 3.4 PC details dialog | | | |
| 3.5 Two devices launching at the same time | | | |
| 3.6 Relaunch right after quitting | | | |
| 3.7 Frame pacing "cap FPS" at the display rate | | | |
| 3.8 Cancel during a launch retry with Back, then launch again | | | |
| 3.9 First frame | | | |
| 3.10 Stream end and reconnect | | | |
| 3.11 H.264 and HEVC | | | |
| 3.12 HDR and HEVC Main10 | | | |

### 4.2 Google TV Streamer (ADB over Wi-Fi)

| Scenario | Result | Evidence | Notes |
| --- | --- | --- | --- |
| 2. Instrumented tests | | Gradle result, serial | |
| 3.1 Pairing | | | |
| 3.2 Wrong PIN | | | |
| 3.3 Unpair | | | |
| 3.4 PC details dialog | | | |
| 3.5 Two devices launching at the same time | | | |
| 3.6 Relaunch right after quitting | | | |
| 3.7 Frame pacing "cap FPS" at the display rate | | | |
| 3.8 Cancel during a launch retry with Back, then launch again | | | |
| 3.9 First frame | | | |
| 3.10 Stream end and reconnect | | | |
| 3.11 H.264 and HEVC | | | |
| 3.12 HDR and HEVC Main10 | | | |

### 4.3 Compatibility row

Copy this row into the compatibility table of
[upstream-sync.md](upstream-sync.md) after the run. The Moonlight Android and
moonlight-common-c values are those of the existing row of that table.

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| `<commit tested>` | 12.2 | fork `magalz/moonlight-common-c` `borealis`, upstream base `874ac95` plus the first full frame deadline patch (tag `borealis/874ac95-1`) | 26.09.1 | Samsung Galaxy S22 (USB ADB) and Google TV Streamer (ADB over Wi-Fi) on `<date>`; instrumented tests `<pass/fail>`; scenarios `<n pass, n fail, n not applicable>`; see the results of this runbook. |

## 5. Data hygiene

1. Before a capture goes into the repository, replace the host name with
   `LUMINALSHINE-HOST`, the `uniqueid` with `00000000-0000-0000-0000-000000000001`,
   the MAC address with `00:00:00:00:00:00`, IPv4 addresses with an address in
   `192.0.2.0/24` and IPv6 addresses with an address in `2001:db8::/32`
   (`core/tests/fixtures/README.md`).
2. Also replace the Web UI address, the ADB serials and the Streamer's IP and
   port in every log and screenshot. The Borealis `Redactor` masks PINs, tokens and
   certificates, but IP masking is opt-in (README, `:borealis:logging`).
3. Never paste PINs, certificates, pairing secrets, `rikey` values or session
   URLs. Crop them out of screenshots.
4. Keep raw captures outside the repository. Commit only scrubbed excerpts and
   the filled templates.

## 6. Results: Phase 1, 2026-10-14

Tested tree: `phase/1-luminalshine-compat` at `d6b3116e` (debug APK), against
LuminalShine 26.09.1 on Windows 11 25H2 with an NVIDIA RTX 3080 Ti, per-client
virtual display, PyroWave off. Devices: Samsung Galaxy S22 (SM-S901E, Android 16,
USB and later ADB Wi-Fi) and Google TV Streamer (Android 14, ADB Wi-Fi, 32-bit
`armeabi-v7a` user space).

| Scenario | Galaxy S22 | Google TV Streamer | Evidence and notes |
| --- | --- | --- | --- |
| 2 Instrumented tests | Pass, 8 of 8 | Pass, 8 of 8 (direct `am instrument`) | Golden vectors and JNI guards through the packaged libraries on both ABIs in use. |
| 3.1 Pairing with the PIN hint | Pass | Pass | Needed the Private network profile (section 1.3). |
| 3.2 Wrong PIN | Pass | Not run | "Incorrect PIN"; `Borealis.PAIR` logged `unpair not confirmed status=404`. The next attempt got 400 `Out of order call to getservercert`: the host keeps the pairing session after the client aborts on a wrong PIN, because `/unpair` is not routed; the attempt after that succeeds (NortheBridge/luminalshine discussion 196). |
| 3.4 PC details dialog | Pass | Pass | Sunshine-compatible 7.1.431.-1; HEVC, HEVC 10-bit, 4:4:4; PyroWave off; HTTP 47989, HTTPS 47984. |
| 3.5 Two devices at once | Started | Failed with 400 `An app is already running on this host` | The host queued the second `/launch` until the first session had started, then answered 400, not 409. No retry is expected for 400. The 409 window (between the `/launch` answer and the RTSP claim) is too short to hit by hand. |
| 3.6 Relaunch right after quitting | Pass, several times | Not run | 503 `restoring the display` was not reproduced. |
| 3.7 Cap FPS | Pass | Not run | Host: `raw_http='1280x720x59'` and `Requested display mode reconciled: HTTP and RTSP agree at 1280x720@59Hz`. |
| 3.8 Cancel during a retry | Not reached | Not reached | No 409 or 503 occurred; covered by `LaunchRetryRunnerTest` and the instrumented golden vectors. |
| 3.9 First frame | Pass | Pass | Preparing message shown until the first frame; first video packet 900 ms after the video stream started (S22, 720p60). |
| 3.11 HEVC | Pass | Pass | `HEVC SPS: profile=Main chroma=4:2:0 depth=8/8`. |
| 3.12 HDR and Main10 | Not run | Not run | Next validation round. |

Host findings, reported upstream:

- Every session that ended with a client disconnect (Back, not "Quit Session")
  wedged in teardown phase "post-join cleanup (5)", and the host restarted 30 to
  60 s later (`Fatal: Hang detected!`); "Quit Session" ended cleanly
  (NortheBridge/luminalshine issue 198).
- Unknown GET paths such as `/unpair` get a second, header-less response on the
  same connection (NortheBridge/luminalshine issue 194).

Follow-ups for the next sprint: a specific message (or one automatic restart)
for the 400 `Out of order call` pairing answer; an option to join the running
session after 400 `An app is already running on this host`; HDR and Main10;
audio check on a longer S22 session (the first 7 s session logged no audio
traffic, while the TV received audio).

## 7. Results: Sprint 2, 2026-10-14

Builds:

- **Galaxy S22:** debug APK built from `3583ffeb`. Release dry-run APK `0.1.0` (versionCode 1000, R8, throwaway signing key) from release workflow run 38651807419 at `a7e0017b`.
- **Google TV Streamer:** debug APK built from `20cfaf09` and, for the final export check, from `26b81a70`.

Host: LuminalShine 26.09.1, as in section 6. Both devices used ADB over Wi-Fi.

| Scenario | Galaxy S22 | Google TV Streamer | Evidence and notes |
| --- | --- | --- | --- |
| 2 Instrumented tests | pass, 10 of 10 | pass, 10 of 10 | Direct `am instrument` on both devices. Includes the new `pairClassifyVectorsMatch` and `classifyPairingRejectsAMessageOver256Bytes`. |
| 3.2 Wrong PIN (changed) | pass | not run | Wrong PIN, then the next attempt got 400 `Out of order call to getservercert`. Logged `category=STALE_SESSION action=RESTART_ONCE`. The pairing reopened with a new PIN and paired. The release build behaved the same. |
| 3.3 Unpair from the PC menu | not applicable | not applicable | Moonlight 12.2 shows no "Unpair" entry; see 3.3. |
| 3.5 Two devices launching at the same time (changed) | pass (first device) | partial | The second device joined through `/resume`, and both streamed at the same time. The 400 conflict dialog could not be reached by hand: while the host builds the first device's virtual display (about 14 s) it does not answer the second device's HTTPS `/serverinfo`, so the second device timed out after 7 s with a connection error (code 0). The dialog branches are covered by unit tests. Host findings below. |
| 3.6 Relaunch right after quitting | - | pass | No 503. Each `/launch` takes about 12 to 14 s because the host builds the virtual display before answering. |
| 3.7 Frame pacing "cap FPS" at the display rate | - | pass | `mode=1280x720x59`. Host: `HTTP and RTSP agree at 1280x720@59Hz`. |
| 3.12 HDR and HEVC Main10 | pass | not applicable | `hdrMode=1`, `HEVC SPS: profile=Main10 chroma=4:2:0 depth=10/10`, `Display HDR mode: enabled`. The picture looked right. The TV attached to the Streamer reports no HDR types (`decoders.json`). |
| 3.13 Borealis settings screen | pass | pass | Switches, version with commit, licences text, and privacy link (browser on the S22, URL text on the TV). The Streamer was driven by the D-pad only. |
| 3.14 Export diagnostics | pass | pass after fix | S22: share sheet, and the zip was saved to Downloads. Streamer: the first build offered the zip to VLC, because VLC accepts any `ACTION_SEND`. Fixed: a TV now saves to `Download/` and shows the `adb pull` command, with no Share button. `adb pull /sdcard/Download/<name>` worked. |
| 3.15 Audit trail | pass | pass | S22 trail: wrong PIN, `STALE_SESSION` restart, pairing success, launches and sessions with codec, resolution and duration. "Erase audit trail" removed entries 1 to 17; only the later sessions remained. |
| 3.16 Stream logs | pass | pass | `Borealis.COMMONC` lines, including "Received first video packet after N ms", in logcat and in the exported `logs/borealis.log`. |
| 3.17 Network privacy | pass | pass | No STUN, WAN-address or connection-test lines in logcat or in either bundle. The "Test network connection" menu entry is gone. |
| 3.18 Release build smoke test | pass | not run | Installed next to the debug build. It started, paired (again through the automatic restart), streamed HEVC and exported diagnostics. |

Secret scan of both exported bundles: no PEM block, salt, rikey, client certificate, pairing secret, uniqueid value or PIN.

Host findings (LuminalShine 26.09.1):

- A second device that resumes a running app joins the first device's per-client virtual display. When either device leaves, the host tears that display down and ends both sessions. On the first join the host also dropped both sessions about 6 s after the second device connected, with `Control stream connection failed: 4` and "No audio traffic was ever received from the host" on both clients.
- While the host builds a virtual display for one `/launch`, about 14 s, it does not answer another client's HTTPS `/serverinfo`.
- The teardown hang of NortheBridge/luminalshine issue 198 recurred after these sessions ended, and the host restarted each time.

Follow-ups for the next sprint:

- Transfer the diagnostic bundle from a TV to a phone with a QR code and a temporary local link.
- Retry `/serverinfo` when it times out while another client's launch holds the host.
- Touch trackpad and mouse input did not work on the S22 with the release build. Compare with stock Moonlight.
- The Streamer has hardware AV1 decoders (`c2.mtk.av1.decoder.lowlatency`), but the upstream decoder selection logged "No AV1 decoder found".
- The first HDR frame on the S22 logged `Trying to restart decoder after CodecException` before the stream settled. Handled in S3-T3: the first IDR now goes to the restarted decoder instead of being dropped.
- The audit `SESSION_START` records the requested frame rate (60), not the negotiated one (59).

## 8. Results: Sprint 3, 2026-10-15

Builds: debug APKs from `a30cc3d4` (instrumented tests and the first transfer
check) and `589b22dd` (all other rows). Host: LuminalShine 26.09.1 as before.
On 2026-10-15 the owner enabled the host's `mouse` setting, which had been
`disabled`; `keyboard` is still `disabled`. Both devices used ADB over Wi-Fi.

| Scenario | Galaxy S22 | Google TV Streamer | Evidence and notes |
| --- | --- | --- | --- |
| 2 Instrumented tests | pass, 10 of 10 | pass, 10 of 10 | Direct `am instrument` at `a30cc3d4`. |
| Touch and trackpad (Sprint 2 follow-up) | pass | not applicable | Works once the host's `mouse` setting is enabled. The Borealis input code is the same as Moonlight 12.2; see [host contract, section 13](../host-contract/luminalshine.md#13-input). The keyboard was not tested, because the host's `keyboard` is disabled. |
| HDR start (S3-T3) | pass, 3 of 3 | not applicable | `Restarting the decoder to apply HDR metadata`, then the first output 155, 107 and 137 ms after the first video packet, with no `Requesting IDR frame on behalf of DR` and a real picture. Before the change (`fce19dc4`) the same start stalled 2.2 s, and 7 s in Sprint 2. An SDR start is unchanged: output 53 ms after the first packet, no restart. |
| Cap FPS in the audit (S3-T2) | not run | pass | "Balanced with FPS limit" on the 60 Hz Streamer: `SESSION_START` detail `codec=HEVC hdr=false resolution=1280x720 fps=59`. |
| Two devices, `/serverinfo` retry (S3-T2) | first device streams | partial | The Streamer opened the same app 1 s after the S22 started it. It logged `server info timed out, retrying attempt=2 max=3` and then sent `/resume` instead of failing with error 0. The host then rejected its RTSP handshake (host `Failed to verify RTSP message tag`; client `Failed to decrypt RTSP response`, RTSP error -1). With a 4 s gap, `/serverinfo` answered after 6.7 s without a retry, and the same RTSP failure followed. The conflict dialog was not reached. |
| Send to phone (S3-T4) | pass (phone side) | pass | The S22 camera read the code. The page opened, the zip downloaded, and the Streamer showed "Downloaded". The phone's file has the same SHA-256 as the copy in the Streamer's `Download/` (72,359 bytes). Chrome's preconnect produced one 408 after the download, which is harmless. From a PC on the same network: the page answers 200 without consuming the link, a wrong token gets 404, HEAD gets 405, the zip 200, the next request 410, and then the port closes. The token never appears in logcat, and the bundle holds only redacted secret fields. The first build clipped the status line at 1080p; fixed in `f401d7b8` and checked on the device. 720p was checked only in Robolectric. |
| AV1 (Sprint 2 follow-up) | not applicable | not applicable | No client change. Moonlight 12.2 uses AV1 only when "force AV1" is set, and the test host's GPU has no AV1 encoder (`av1_nvenc` is not supported); see [host contract, section 9](../host-contract/luminalshine.md#9-codec-advertisement). |

Host findings (LuminalShine 26.09.1):

- The host process crashed right after the virtual display cleanup of a
  session that had just ended: `Fatal: CRASH: unhandled exception 0x20474343
  ... KERNELBASE.dll`, minidump written. It restarted about 9 s later. A
  `/resume` sent in that window got a connection reset. The two other session
  ends in this round did not crash.
- A second client whose `/resume` is accepted while the first client's
  `/launch` is still setting up fails its RTSP handshake, as described in the
  two-device row.
- While the host builds a virtual display for one client, other HTTPS requests
  still wait: 6.7 s and more than 7 s in this round (luminalshine#202).

Follow-ups:

- A second device that joins during another device's launch: after the
  `/serverinfo` retry, the RTSP handshake fails. The host side needs an issue.
  On the client, consider waiting until the first session is streaming before
  `/resume`, or retrying the start once.
- In that case the RTSP error -1 text points to firewall ports, which is
  misleading.
- Check the Send to phone dialog on the Streamer at 720p output.

## 9. Results: Sprint 4, 2026-10-15

Build: the debug APK from the phase branch at `f84d89c5`, installed on both
devices with `adb install -r` (data kept). Earlier smoke tests used `c21276fd`
and the builds of the two fixes listed under "Defects found and fixed". Host:
LuminalShine 26.09.1 on the PC, with the `mouse` and `keyboard` settings
enabled. The owner enabled `keyboard` on 2026-10-15. Both devices used ADB over
Wi-Fi.

| Scenario | Galaxy S22 | Google TV Streamer | Evidence and notes |
| --- | --- | --- | --- |
| Launcher (Sprint 4, S4-T7) | pass | pass | The package manager resolves the launcher activity to `LibraryActivity`: MAIN and LAUNCHER on the S22, LEANBACK_LAUNCHER on the TV. One icon on each device. The TV banner is unchanged. `am start -W` total time is 2.4 s on the S22 and about 4.0 s on the TV. |
| Decoder setup kept (GL probe) | not checked | pass | TV logcat shows `GL renderer cached` with the renderer name on the first launch of the new launcher. |
| Dark system bars | pass | not applicable | The S22 status bar icons are light on the dark page. |
| Hosts | pass | pass | Both show the paired PC as online and openable. Both also show an unpaired host with a Pair action, a warning mark and no running-app mark. TV: the rail is on the left, focus starts on the first card, and D-pad Right and OK open the host. |
| Cold start loading state | not checked | pass | The TV shows a progress indicator first, then the hosts at about 4.6 s from process start. Before the crypto warm-up moved off the connect path, this took about 9.2 s. No focusable Add host button is shown while loading. |
| Apps | pass | pass | The app list shows within one poll on both devices. TV: a five-column grid beside the rail, with the first tile focused. |
| Start a stream from Apps | pass | pass | Tapping or pressing OK on a desktop app sends `/launch`, and the host reports a new session. The picture appears. |
| Continue row and resume | pass | not run | After leaving a stream with Back, the Continue row on Hosts shows the running app. Resume sends `/resume`, which the host accepts, and the picture returns. Apps marks the app as running. |
| App actions and Quit | pass | not run | A long-press on the running app opens Resume, Quit and Details, each with a line that states the consequence. Quit asks for confirmation. After Quit, the host reports that nothing is running. |
| Hide and show hidden apps | pass | not run | A hidden app leaves the list. Show hidden apps lists it dimmed, and it returns to the list when restored. |
| Add host (UC-008) | pass | not run | The address field has focus and the keyboard opens at once (UC-008-T1). An address on another subnet is rejected with the wrong-subnet message. An address on the local subnet is added, and the screen returns to Hosts. The unreachable-host case was not run. |
| Pairing screen (UC-001) | pass (screen and Cancel only) | not run | Pair on the unpaired host opens the pairing screen. It shows the PIN digits, the 5-minute validity, and four steps, including the Web UI address and the note about `xbox` and `webos`. Cancel returns to Hosts. A full pairing was not run, because there was no unpaired host that the owner wanted to pair. Expiry is covered by unit tests only. |
| Settings | pass | not run | The Settings action opens the upstream stream settings. Back returns to Hosts. |
| Scenario 3.5: second device joins during another device's launch | first device streams | pass | With a 1 s gap, the TV logged `server info timed out, retrying attempt=2 max=3`, then `host busy, waiting before resume serverinfo_ms=9429 wait_ms=3000`, then `/resume`. The RTSP handshake completed and the picture appeared. The host logged `Virtual display already active`, `[active sessions: 2]` and `CLIENT CONNECTED` twice, and no `Failed to verify RTSP message tag`. With a 4 s gap, the TV waited 3 s (`serverinfo_ms=6255`), then resumed. The RTSP handshake completed, and the host logged two active sessions. No restart was needed in either run. The automatic restart and the new error text were not exercised on a device; unit tests cover them. |
| Send to phone at 720p (Sprint 3 follow-up) | not applicable | not run | Needs the TV output at 720p. Covered only by Robolectric tests. |
| Keyboard input in a stream | not run | not run | The host keyboard is enabled. Not tested, to avoid typing into the PC's foreground app unattended. |
| Handheld layout | not run | not run | No handheld device. Covered by the Roborazzi `*_handheld_*` screenshots. |

Defects found and fixed:

- The Apps screen stayed on its loading indicator on the TV. Upstream
  `ComputerDetails.update()` resets the stored app list on each host poll. The
  adapter now keeps the last list the service published for the host
  (`7d8484e4`).
- On a cold start, the empty "looking for hosts" state with a focusable Add
  host button showed for 7 to 10 s. An unpaired host showed a running-app mark
  for a fake app. The connect path also waited about 4.5 s for the client
  keypair (BouncyCastle) before it listed the known hosts. The Hosts screen now
  shows a loading state until the first report (`dbd44015`), and the known
  hosts are reported without waiting for the crypto warm-up (`ced6afc0`).

Follow-ups:

- Test the unreachable add-host case, and a full pairing from start to end, on
  a device.
- Test keyboard input in a stream, Send to phone at 720p, and a handheld
  device.
- Done in Sprint 5: the launcher shortcuts open the Library
  (`ShortcutTrampoline` starts `LibraryActivity`), and `PcView` and `AppView`
  are removed.
- The host card does not yet show the detail line (version and address) or the
  codec and HDR chips. The state does not hold them yet.
- The app start time on the TV (about 4 s to the first frame of the activity)
  is unchanged. It is outside this sprint.

## 10. Results: Sprint 5, 2026-10-16

Build: the debug APK from the phase branch at `c1c4455a`, installed with
`adb install -r` (data kept). The code is the same as `1ce73b9f`; only docs
changed after it. The re-check ran on build `df093832`. The fixes for O1 and D6
came later and are covered by unit tests. Host: LuminalShine 26.09.1 on the PC,
with the `mouse` and `keyboard` settings enabled. Keyboard tests typed into a
test file open in Notepad on the PC. The first round used ADB over Wi-Fi on both
devices. The re-check used USB on the S22 and ADB over Wi-Fi on the TV.

The final round, the fixes after it and the later re-checks ran on newer
builds. The subsections "Final round", "Fixes after the final round" and
"Re-checks" at the end of this section name the build of each part.

TV (Google TV Streamer, Android 14):

| Scenario | Result | Evidence and notes |
| --- | --- | --- |
| Hosts cards | pass | The paired PC shows its address and the tags HEVC 10-bit and 4:4:4. An unpaired host shows "Found on this network" and Pair. |
| Starting stream screen | pass | Four stages: "Connecting" done, "Starting Desktop" current, the 15 s note, and Cancel focused. First video packet about 1.6 s after the request. |
| First-run hint | pass | "Press Back for the menu" appears over the stream and then goes away. |
| Back opens the menu (UC-004-T1) | pass | Remote Back opened the side panel. The stream kept playing behind the scrim, with focus on "Back to stream". |
| Overlay Detailed (UC-004-T6) | pass | Real figures: 1280x720, 59 fps, HEVC, decoder name, host processing min, avg and max, network RTT with variance, loss and bitrate. The overlay stays after the menu closes. Decode time read 0.0 ms on the MediaTek low-latency decoder (D5). |
| Refresh the picture | pass | The log shows "IDR frame request sent" and the menu closes. |
| Borealis keyboard | pass | Focus starts on Q. D-pad and OK typed letters. A latched Shift and then A typed "A". An adb-injected Y deleted a character, and an adb-injected B closed the keyboard. |
| Disconnect (UC-004-T4) | pass | Back to the Library. Desktop still runs on the PC. The audit has SESSION_END with reason=disconnect. |
| Quit with confirmation (UC-004-T5) | partial | The confirmation "Quit Desktop? Unsaved work is lost." opened with Cancel focused, and Quit ended the app (Library shows "Nothing running"). Defect D2: the host ends the stream with -1 right after `/cancel` and answers `/cancel` 7 s later. The error screen showed for about 7 s, and the audit read reason=error code=-1 instead of reason=quit. |
| Gamepad menu chord (Select+Start held) | not run | adb-injected events come from a virtual device that the client does not treat as a gamepad. A real controller is needed. |
| Resume after Disconnect | note | The next `/resume` waited about 11 s at `/serverinfo`. This is host teardown after a disconnect (NortheBridge/luminalshine#198). Back during Starting cancelled correctly. |
| Old PC shortcut (trampoline) | pass | A launcher-style intent (NEW_TASK and CLEAR_TASK flags) with the 12.2 PC shortcut extras opened Apps for that PC. Back went to Hosts. Without the flags `am start` only brought the existing task to the front. |
| Old game shortcut | pass | The 12.2 game shortcut extras launched Desktop through the Library (`/launch`, first video 1.6 s later). |

Galaxy S22:

| Scenario | Result | Evidence and notes |
| --- | --- | --- |
| Hosts cards | pass | Address and tags as on the TV. |
| Continue row on a cold start | defect | The row showed "An app is running" instead of the app name, although the cached app list on disk names it (D4). |
| Stream hint and handle | pass | "Pull the handle down or press Back for the menu". A downward drag on the handle opened the menu (UC-004-T3). |
| Back opens the menu | pass | KEYCODE_BACK during the stream opened the side panel. |
| Touch mode switch | pass | Direct mode was chosen in the menu. A tap moved the PC cursor to the touched point and placed the caret. |
| Docked Borealis keyboard (UC-005-T5) | pass | Typed "hi" into the test file. The stream stayed visible above the panel. |
| System keyboard and UTF-8 | pass | The Android keyboard opened over the stream. Accented text and an emoji typed into the test file arrived correctly on the PC. |
| Add host, unreachable address (S4 follow-up) | pass | An address on the LAN that no device uses (`192.0.2.x` in this file) gave "Unable to connect to the specified computer. Make sure the required ports are allowed through the firewall." |
| App actions | pass | Resume, Quit Desktop, Details and Add to home screen. Quit asked for confirmation and ended the app. Pinning the shortcut was not tried. |
| Scenario 3.5 with the TV | pass | The S22 `/launch` took about 10 s on the host. The TV started one second later and logged `server info timed out, retrying attempt=2`, then `host busy, waiting before resume serverinfo_ms=9390 wait_ms=3000`, then `/resume`. The RTSP handshake completed and both devices showed first video. |

Re-check after the fixes (build `df093832`; S22 over USB, TV over ADB Wi-Fi):

| Defect | Result | Evidence and notes |
| --- | --- | --- |
| D1 Back in a segmented control | fixed | TV: Overlay set to Detailed, and one Back closed the menu. |
| D2 Quit | fixed | TV: "Quitting Desktop..." with a spinner for about 5 s, no error screen, then the Library. The audit has SESSION_END with reason=quit. |
| D3 placeholder name | fixed | Resume from Continue shows "Desktop" with the PC's name in the menu on the TV and the S22, not the placeholder. |
| D4 cold start | fixed | TV and S22 after a cold start: Continue shows "Desktop" and "Running on" the PC's name. The card shows HEVC 10-bit, HDR, 4:4:4 and the running app. |
| D5 decode time | fixed (display) | The Detailed overlay shows "Decode — ms" on the MediaTek decoder. The decoder likely does not report usable timestamps. |
| O1 rail on Add host | fixed (TV, rail-fix build) | Fixed in S5-T17. A screen recording of Hosts to Add host shows the rail fixed in place for the whole transition, with "Add host" selected. Only the content changes, about 0.6 s after OK. |
| D6 starting steps | seen, not re-checked | Seen on the TV in a resume recording: the steps went from "Preparing the display" back to "Starting Desktop". Fixed in S5-T17 (`3173152b`). Not re-checked on a device. |
| D6 starting steps | not in the re-check | Fixed in S5-T17 and covered by unit tests. Pending a device check. |

Defects found and fixed:

- D1: Back with focus inside a segmented control in the menu first moved the
  focus out of the control (Compose focus exit). A second Back closed the menu.
  Fix: Back closes the in-game menu and the keyboard at once from any focus
  (`e2b84755`, S5-T16).
- D2: Quit showed the error screen for about 7 s, and the audit read
  reason=error. The host ends the stream with -1 before it answers `/cancel`.
  Fix: while a quit is pending, the session shows a quiet "Quitting" screen
  and the audit records reason=quit for any termination code. A quit that then
  fails shows the failure screen (`7ab51daa`, S5-T16).
- D3: a resume started from the Continue row with an unknown app name showed
  the internal placeholder in the menu header and in "Quit app". Fix: the
  stream never shows the placeholder name of a resumed app (`dfd3f850`,
  S5-T16).
- D4: on a cold start the Hosts screen had no app list until the Apps screen
  polled it. The Continue row said "An app is running", and the HDR tag was
  missing, although the cached list on disk had the name and HDR support. Fix:
  a host first seen without an app list is seeded from the cached list
  (`7b0ab738`, S5-T16).
- D5: the Detailed overlay showed "Decode 0.0 ms" on the MediaTek decoder. Fix:
  the overlay shows a dash when the decoder reports no decode time
  (`c01fe7cb`, S5-T16). The value is shown as "—" (see Follow-ups).
- O1: on the TV, choosing Add host made the rail disappear first, and the
  content re-laid out full width. The Add host screen appeared about 1.2 s
  later, without the rail, so the movement looked truncated. Fix: the rail stays
  on Hosts, Apps, Add host and Pairing on the TV and the handheld layout. The
  content cross-fades in 150 ms instead of 700 ms (`8a17989d`, S5-T17).
- D6: the starting steps could go backwards. A TV recording of a resume showed
  the steps go from "Preparing the display" back to "Starting Desktop". Cause:
  the app stage was reported before `/launch` and mapped to the display stage.
  Fix: the app-name stage is the start of the app, and the later stream stages
  prepare the display. `StreamSession.onStage` ignores a step before the current
  one (`3173152b`, S5-T17). Not re-checked on a device.

Not run (after the final round):

- A8, the conflict dialog during a stream (Game side): the race did not happen
  on the devices. The dialog is covered by the Compose tests only.
- O8 on a device: the transfer line after "Downloaded" is fixed and covered by
  tests. It was not checked on a device.
- The gyro: no pad exposed motion sensors to Android, so the gyro zeroing was
  not checked (C9).
- Error states carried over from the first round: certificate changed and
  blocked ports. They were not reproduced without changing the PC's settings;
  unit and screenshot tests cover them.

Follow-ups:

- The TV's USB attach dialog has no "use by default" option (R5a). The dialog
  returns on each re-attach.
- A system pad can leak the first of Start or Select for a few ms before the
  chord holds it back, because its two key-downs arrive as separate events. The
  review of the D17 fix found it, and the tests pin the packets. It was not seen
  on the device.
  Done in Sprint 6: a system pad's Select or Start waits up to 60 ms for the
  other button, so a lone press no longer reaches the PC (section 11, C1).
- On the TV the system keyboard opens when the Add host field gets focus (R1),
  while the comment in `AddHostScreen` says it opens on OK. Align the comment or
  the behaviour.
  Done in Sprint 6: on the TV, OK opens the keyboard on the Add host field, and
  focus alone does not (section 11, A12 and D1).
- The menu's Gamepad mouse row follows the last pad that toggled the mouse
  (D18), so with two pads it shows the state of the last toggle.
  Done in Sprint 6: the row shows On while either pad emulates the mouse
  (section 11, C2).
- Done in Sprint 6: with the USB driver setting off, the USB attach dialog does
  not appear (section 11, C4).
- The host items stay: the slow `/resume` after a disconnect
  (NortheBridge/luminalshine#198), and the `/cancel` delay after a quit. The
  host ends the stream before it answers `/cancel`, so the Quitting screen waits
  about 7 s.
- The MediaTek decoder decode time, shown as "—" (D5).
- Done in Sprint 6: the TV channel prompt after pairing was dropped, because
  Google TV shows no app channels (section 11).
- Done in Sprint 6: the pinned app shortcut icon (box art, or the Borealis
  monitor icon).
- Done in Sprint 6: the `PairingHints` dead code. `pinHint` and `unpairHint` are
  removed, and `PairingHints` keeps `webUiUrl` and `suggestedDeviceName`, which
  the Library pairing uses (see the Borealis Android README).

### Final round

Build: the debug APK from `b32b8678`. Its code is identical to the phase head
`65958388`. The S22 used USB and the TV used ADB over Wi-Fi. Recordings were
timed with the real frame timestamps (ffprobe), because `screenrecord` writes
variable-rate video.

#### Block A

| # | Scenario | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| A1 | D6, starting steps only move forward: launch | TV | pass | Connecting done, Starting Desktop (about 11 s while the host answers `/launch`), Preparing the display. First video 2.1 s after `/launch`. |
| A1 | D6, resume | TV | pass | Starting Desktop, Preparing the display, Waiting for the first frame, then the stream. No step went back. |
| A1 | D6, resume | S22 | pass | Same order, after the rotation to landscape. |
| A1 | D6, launch | S22 | pass | Starting Desktop (about 10 s), Waiting for the first frame, then the stream. |
| A2 | Add host field and rail | TV | pass | Filled field: 12 Left presses only moved the caret to the start. Empty field: Left reached the rail. OK on the rail's Add host while on Add host: no transition; the typed text stayed. Up and Down did not leave the field (D7). |
| A3 | Disconnect from the menu | S22 | pass | Library with Continue showing Desktop as running. Audit `SESSION_END reason=disconnect`. |
| A3 | Quit from the menu | S22 | pass | Confirmation "Quit Desktop? Unsaved work is lost." with Cancel and Quit. "Quitting Desktop…" with a spinner for about 7.5 s (the host answered `/cancel` after 7.3 s), no error screen, then the Library. Audit `reason=quit`. |
| A4 | Old PC shortcut | S22 | pass | A launcher-style start with the 12.2 PC shortcut extras opened Apps for that PC. Back went to Hosts. |
| A4 | Old game shortcut | S22 | pass | Shortcut, then Library, then the stream (`/resume`, first video 2.1 s later). |
| A5 | Real back gesture | S22 | pass | Gesture navigation. In the stream's immersive mode the first edge swipe only shows the system bars (system behaviour). The second is a real back and opens the menu. With the menu open, one edge swipe closes it. |
| A6 | Picture-in-picture with the menu open | S22 | pass | App PiP option on. In the PiP window only the stream shows (no menu, no on-screen controller, no handle). Expand: the same session (no `SESSION_END`), the menu open again, and the scrim dims the stream and the controller as a normal menu does. |
| A7 | Rotation with the on-screen controller | S22 | pass | Rotated 90°, 270°, 90°, 270°, 90°. The handle stayed on top of the controller, and the menu panel covered it. A tap on a controller button under the scrim only closed the menu. |
| A8 | Conflict dialog in the stream | TV and S22 | not reproduced | Two races (the S22 2 s and 1.4 s after the TV). LuminalShine holds `/serverinfo` while it builds the first display, then reports Desktop as running. The second device joined instead of launching, and the TV kept streaming. The first S22 attempt was also blocked by a system panel. Covered by the Compose tests only. |

Also seen: the Game window appears 1.5 s after OK for a launch and 0.8 s for a
resume on the TV, so the Library stays unchanged for that long. Inherited
decoder probing runs in `onCreate`.

#### Block B

| # | Scenario | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| B1 | Host offline | TV | pass | LuminalShine was stopped, and a watcher pressed OK on Desktop when the HTTP port closed. Three 5 s connect timeouts (about 19 s), then the title "Can't reach" with the host name and "The PC is off, asleep, or on another network.", the raw line, the port hint, and the buttons Try again (focused), Wake PC, Diagnostics and Close. After the restart, Try again went through the Library and started the stream (`/launch`, first video). The raw line named the app at this run (O5). |
| B2 | Full pairing | S22 | pass | Forget sent `/unpair`. The host answered 404, because LuminalShine has no such route (audit `UNPAIR_NOT_CONFIRMED reason=host_status_404`). The owner removed the S22 in the Web UI. Add host with the PC's address gave the card "Not paired". Pair opened the PIN screen (the PIN read digit by digit for accessibility, "valid for 5 minutes", the Web UI steps, and the `xbox` and `webos` warning). The owner entered the PIN: `pairing ended state=PAIRED`, audit `PAIRING_START attempt=1` and `PAIRING_SUCCESS`, and Apps opened. The TV stayed paired. |
| B3 | Send to phone at 720p | TV and S22 | pass | adb could not change the TV's mode (`cmd display` refused with a SecurityException, and nothing changed). The owner set 720p in the TV menu. "Diagnostics saved" and "Send to phone" fit at 1280x720. The status line showed in full ("Expires in", "Waiting for the phone…"), then "Downloaded". The S22 has the zip, the same size as the TV copy. |

Findings in Block B are in "Fixes after the final round": D9, O4 to O8.

#### Block C

Hardware: two 8BitDo controllers, each with its own 2.4 GHz receiver. The 8BitDo
Pro 3 was in Switch mode, where it reports the Switch Pro USB id. It is an
8BitDo pad, not a Nintendo controller. The 8BitDo Ultimate 2 Wireless for PC was
tested in XInput and D-input mode. The Windows game controller panel (joy.cpl)
on the PC showed what reached the host.

| # | Scenario | Result | Evidence and notes |
| --- | --- | --- | --- |
| C1 | Select+Start held 1 s opens the menu | pass | Both pads (Pro 3, and Ultimate 2 in D-input mode). Nothing was left held in joy.cpl. |
| C2 | Quick Select+Start passes through | pass | Back+Start reached the PC, and the menu did not open. Both pads have a Guide button, so the Guide-tap path (a pad with Select and no Guide button) is covered by unit tests only. |
| C3 | Nothing reaches the PC while the menu is open | pass | Sticks, A, B, X, Y and triggers on both pads: joy.cpl stayed still, and nothing stayed held after B closed the menu. The D-pad and A work in the menu. |
| C4 | Gamepad mouse from the menu | pass | On: the stick moves the PC's mouse. Off: the pad is a gamepad again. Holding Start also toggles it ("Mouse emulation is: ON", inherited). |
| C5 | Borealis keyboard with the pad | pass | A types, Y deletes, B closes (typed into the test file). |
| C6 | Two pads | pass | Both pads open the menu, and neither reaches the PC while it is open. |
| C7 | Exit combo Start+Select+LB+RB | pass | The stream ended. Audit `SESSION_END reason=user`. |
| C8 | USB-driver pad (Ultimate 2 in XInput mode, "Override native Xbox gamepad support" on) | partial | Android asked for USB permission, and the owner accepted. The driver took the pad as controller 1. The chord opened the menu and the D-pad navigated, but A, B, X and Y did nothing in the menu, the stick did not navigate, and holding Start did not toggle the mouse. See D10, D12 and F2. |
| C9 | Gyro zeroing | not applicable | Neither pad exposes motion sensors to Android here. See Not run. |

Findings in Block C are in "Fixes after the final round": D10 to D13, D15, O9
and F2. O10 (the Ultimate 2 in Switch mode did not work while the Pro 3 receiver
was also in Switch mode) was not investigated.

Also in Block C, the owner asked for a menu row with options to change its
option on OK or A, without moving into the segments (D11).

### Fixes after the final round

The task column gives the branch and the merge or commit on the phase branch.
The `S5-T` number is given only where the round notes give it.

| ID | Found | Fix | Task |
| --- | --- | --- | --- |
| D7 | Add host field with text and the keyboard closed: Up and Down only moved the caret, so the buttons and the rail could not be reached by remote. | Up and Down leave the field, Left at caret 0 goes to the rail, and the text is kept. | `task/s5-libfix` (239fbb5b), S5-T21 |
| D8 | A stream that ran and then ended with -1 showed "Couldn't start". | Such a stream shows "`<app>` stopped" with its explanation. The audit still records `reason=error code=-1`. | `task/s5-errorfix` (0198716a), S5-T20 |
| D9 | A raw Android toast with an address and the exception text showed over the error screen. | Start messages stay off the screen, and the stack trace goes to the log. | `task/s5-errorfix` (0198716a, 4929a2bb), S5-T20 |
| D10 | With the menu open, the Select+Start chord did not close it. Start activated the focused row. | The chord toggles the menu on every pad. Start and Select never activate a row. | `task/s5-padmenu` (aa7313d6), S5-T18 |
| D11 | Touch, Gamepad mouse and Overlay rows did nothing on OK or A. | OK or A on an option row changes its option, as a tap does. The row's click label says "Change" and the option's title. | `task/s5-menurows` (4f3fa2bc), S5-T19 |
| D12 | USB-driver pads: A, B, Y and Start did not act in the menu, and the stick did not navigate. | Their buttons and the stick send the same keys as a system pad (`UsbChromeInput`). | `task/s5-padmenu` (aa7313d6), S5-T18 |
| D13 | The PC's foreground window seemed to lose focus when the menu opened with joy.cpl open. | Not reproduced, and no fix. Watched in the re-checks (R4). | none |
| D15 | Holding Start on a USB-driver pad toggled the gamepad mouse on the driver thread, and the inherited toast crashed the app. | The toggle is posted to the main thread. | `task/s5-padmenu` (bedb8a2c, d7d584dc), S5-T18 |
| D17 | Holding Select+Start with mouse emulation off opened Steam on the PC and took its focus. | Start and Select are held back from the PC on every pad while the chord is pending. A quick tap is sent on release. | `task/s5-padmenu` (09ba717b), S5-T18 |
| D18 | The Gamepad mouse row showed the opposite state after a Start-hold toggle. | The row follows each toggle. | `task/s5-padmenu` (09ba717b), S5-T18 |
| F1 | A different app running at start could be quit without asking when the Library had not seen it. | A different app running at start always asks: Cancel, Join, and Quit and start. A restart never quits another device's app. | `task/s5-launchfix` (1510bb2a), S5-T22 |
| F2 | By default the USB driver did not claim the Ultimate 2 dongle in XInput mode. Its keyboard interface has the same VID and PID, so it counted as native support. | Native support counts only a game controller for Android (a gamepad, or a joystick with X and Y axes) with the same VID and PID. The dongle's keyboard and mouse interfaces do not block the driver. Joystick axes were added in 93ea9331. | `task/s5-launchfix` (1510bb2a, 93ea9331), S5-T22 |
| O3 | D-pad Left into the rail focused the nearest item (Settings, from the Apps grid or the Add host field), not the selected one. | The rail is entered on the selected item. | `task/s5-libfix` (239fbb5b, 923646fb), S5-T21 |
| O4 | While `/serverinfo` retried against an unreachable host, the steps showed "Connecting" done and "Starting Desktop" current, with the 15 s note. | "Connecting" stays current until the host answers, and the 15 s note does not show during the retries. | `task/s5-errorfix` (dbdbfa8f, 0198716a), S5-T20 |
| O5 | The raw line used the app name as the step ("Error 0 · Desktop"). | The raw line names the step: `serverinfo`, `launch`, `resume` or `quit`. | `task/s5-errorfix` (7b5c1390, 4929a2bb), S5-T20 |
| O6 | Forget said the device would need to pair again, but LuminalShine keeps trusting it. | The Forget text says the PC may keep trusting the device until it is removed in the LuminalShine Web UI (Clients page). | `task/s5-libfix` (239fbb5b), S5-T21 |
| O7 | Export diagnostics showed no progress for about 30 s before "Diagnostics saved". | A progress dialog shows "Creating the diagnostic bundle…". Back closes it, and the export goes on. | `task/s5-libfix` (239fbb5b, 923646fb), S5-T21 |
| O8 | After "Downloaded", the transfer dialog kept an empty line where "Expires in" was. | The status line is shown whole. Not checked on a device (see Not run). | `task/s5-libfix` (239fbb5b), S5-T21 |
| O9 | The keyboard always showed uppercase labels, so the case of the next letter was not visible. | Letters are lowercase unless Shift is latched. The keyboard has no Caps key. | `task/s5-menurows` (4f3fa2bc), S5-T19 |

D16 is not an app defect. The S22 stayed in landscape after a stream. The cause
is `uiautomator dump`, which runs as the shell (uid 2000) and freezes the current
rotation on a phone. It wrote `user_rotation` through the system server while
the stream was in landscape. The same trace showed a `wm_user_rotation_changed`
event for that process. A stream without the tool returns to portrait. Never run
`uiautomator dump` while a stream is in landscape.

### Re-checks

#### Build aa7313d6

S22 over USB, TV over ADB Wi-Fi. Items R1 to R4 use this build.

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| R1 | O3 rail entry | TV | pass | Left from the Hosts cards and from the Apps grid focuses Hosts (the selected item), not Settings. |
| R1 | D7 Add host field | TV | pass | With text and the keyboard closed, Down focuses the Add host button. Left moves the caret, and at caret 0 goes to the rail's Add host item. The text is kept. On the TV the system keyboard opened when the field got focus, also when focus came back to it with Up; the screen's comment says it opens on OK (see Follow-ups). |
| R1 | O7 export progress | TV | pass | "Export diagnostics" dialog with "Creating the diagnostic bundle…". Back closes it, and the export goes on. "Diagnostics saved" appeared 34 s later. |
| R1 | O6 Forget copy | TV | pass | The Forget text says: the PC may keep trusting this device until it is removed in the LuminalShine Web UI (Clients page). |
| R2 | F1 different app | TV and S22 | pass | The TV launched Desktop. 1.4 s later the S22 started another app and showed "An app is already running", with Cancel, Join, and Quit and start. Cancel went back to the Library. Audit `LAUNCH_CONFLICT choice=cancel`. The TV kept streaming, and nothing was quit or started. |
| R2 | D8 stopped | TV | pass | The S22 quit Desktop while the TV streamed. The TV showed "Desktop stopped", "The stream ended. The PC may have quit the app, or the connection dropped.", and the raw line "Error -1". The audit still reads `reason=error code=-1`. |
| R3 | D9, O4, O5 host offline | TV | pass | LuminalShine stopped. The steps kept "Connecting" current, with no 15 s note, during the retries. Then the title "Can't reach" with the host name, and the raw line "Error 0 · serverinfo". No toast in a 30 s recording. The log has the start failure with its stack trace. |
| R4 | F2 claim | TV | pass | Ultimate 2 in XInput mode (Start+A), with the override OFF. The log counts two gamepads (Pro 3 and Ultimate 2), and the dongle is counted once. The Android USB prompt appeared. |
| R4 | D10 chord in menu | TV | pass | Pro 3: with focus on Quit Desktop, the chord closed the menu with no confirmation. Holding on did not reopen it. No mouse toggle. |
| R4 | D11 option rows | TV | pass | Pro 3: A on the Overlay row cycles Off, Compact, Detailed, Off. Focus stays on the row. |
| R4 | D12 USB pad in the menu | TV | pass | Ultimate 2 (USB driver): the chord opens the menu, the left stick and the D-pad move focus, and B closes it. |
| R4 | D12 USB Start hold | TV | crash, then fixed | Holding Start on the USB pad crashed the app (D15). Fixed in `bedb8a2c`, with the toggle posted to the main thread. Re-checked in R5. |
| R4 | D13 watch | PC | not reproduced | With joy.cpl open, the foreground switches were the owner's Alt+Tab between joy.cpl and another app. |

#### Re-check R5, build 97328e42

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| R5a | USB attach dialog (S5-T23) | TV | partial | Switching the Ultimate 2 to Start+A shows the TV's own dialog for the pad, with OK and Cancel only. This Google TV has no "use by default" option. After OK, UsbAttachActivity runs and finishes, and the stream starts with no permission prompt. |
| R5b | USB pad keyboard | TV | pass | A opens the keyboard from the menu and types. Y deletes, and B closes it. |
| R5b | USB pad Start hold | TV | pass | No crash (D15 fixed). The stick moves the PC's mouse. Closing the menu with the chord does not toggle the mouse. |
| R5c | Win+Tab (S5-T24) | TV | pass | Task View opens. A third window is reachable with the arrow keys and Enter. |

#### Final check, build 09ba717b

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| R5d | Confirm-key guard after a system window (S5-T24) | TV | pass | After a reinstall, the USB permission prompt appeared over the start. The owner accepted it and pressed OK once more. The start continued (one Game, no finish), and the first video came 2 s after `/launch`. |
| Pad | Pad attached during a stream (S5-T23) | TV | pass | The dongle re-attached mid-stream. Android launched UsbAttachActivity, and the stream kept running. |
| D17 | Chord hold | TV | pass | Pro 3 and Ultimate 2: the 1 s hold opens the menu, and Steam on the PC does not open. |
| D17 | Quick tap | TV | pass | joy.cpl shows Back+Start briefly, and the menu does not open. |
| D18 | Gamepad mouse row | TV | pass | After a Start hold the row shows On. After the next one it shows Off. |
| Quit | Quit from the TV menu | TV | pass | Audit `SESSION_END reason=quit`. Nothing was running on the PC afterwards. |

The captures were deleted from the TV and locally. Diagnostic zips stay in the
Download folders of both devices for the owner to delete.

## 11. Results: Sprint 6

This section holds the Sprint 6 results. It starts with the TV channel spike,
run with the Sprint 5 debug build that was on the TV (code of `09ba717b`). The
device round of 2026-10-17 follows it.

### TV channel spike, 2026-10-16

Device: Google TV Streamer, Android 14, launcher `launcherx` 1.0.994451607.

- The launcher handles the "add channel" request (`REQUEST_CHANNEL_BROWSABLE`)
  with its own activity.
- No third-party preview-channel row appears on the Home or Apps tabs. Only
  Google-curated rows and YouTube's appear. VLC and Globoplay, which publish
  channels, have no row.
- Decision: no channel prompt after pairing. The existing channel code stays for
  Android TV launchers that show channels.

### Device round, 2026-10-17

#### Setup

- Builds:
  - the debug APK from phase head `e9e66849` first;
  - then `c02fdafb` (decode-time FIFO reverted), on the S22 only;
  - then `c3f1ce75` (merge of the device fixes, with the revert). The re-checks
    ran on `c3f1ce75`, and the final keyboard check on the TV on `02f7b18b`.
- Install: `install -r`, keeping data.
- Devices:
  - Galaxy S22: USB, phone language pt-BR.
  - Google TV Streamer: ADB over Wi-Fi, pt-BR, Android 14, Gboard for TV.
- Host: LuminalShine 26.09.1 on the PC.
- Pads on the TV:
  - 8BitDo Pro 3 in Switch mode (a system pad);
  - 8BitDo Ultimate 2 for PC in XInput mode (the app's USB driver).
- The Windows game controller panel (joy.cpl) was open on the PC.

#### Block A (no owner action)

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| A1 | Upgrade: the two logging switches move to DataStore | TV | pass | Set `borealis_mask_addresses=true` in the Sprint 5 build (verbose was already on). After installing the Sprint 6 build, `files/datastore/borealis_settings.preferences_pb` holds verbose=1 and mask=1, and SharedPreferences has no `borealis_` keys left. Mask was set back off afterwards, to the owner's value. |
| A2 | Settings opens from the rail, two panes, TV groups | TV | pass | Groups: Stream, Video, Input, Gamepad, Audio, Advanced, Privacy and diagnostics, About. No On-screen controls group, because there is no touchscreen. The motion fallback and vibration rows are hidden. |
| A3 | Resolution list, bitrate automatic | TV | pass | 360p to 4K. The TV reports one 1920x1080 mode with no HDR, but its 4K decoder raises the limit, as in upstream. No native entries on a TV. Picking 1080p gives "Automatic (20 Mbps)" with no bitrate key stored. |
| A4 | Frame rates and Unlock all frame rates | TV | pass | 30 and 60 fps at 60 Hz. Unlock gives 30, 60, 90 and 120. Picking 120 gives "Automatic (28 Mbps)". Turning Unlock off steps the frame rate to 60 and returns the bitrate to automatic (the review fix). |
| A5 | HDR row | TV, S22 | pass | Hidden on the TV (`supportedHdrTypes=[]`). Shown on the S22. |
| A6 | Phone list | S22 | pass | Complete list with headers. "Native (2259x1080)" and "Native fullscreen (2340x1080)". The On-screen controls group, vibration rows, USB rows, Privacy link row and About are shown. |
| A7 | Frame rates on the S22 | S22 | pass | With One UI "Motion smoothness: Standard", only 30 and 60 fps. After the owner switched to Adaptive (up to 120 Hz): 30, 60, 90 and 120. Same rule as upstream. |
| A8 | Privacy pane on TV | TV | pass | It renders inside the two panes with no crash, and the switches show the migrated values. |
| A9 | Export on TV | TV | pass | Progress shows on the Export row (about 25 s), then "Diagnostics saved" with the file, size, location and `adb pull` command. Close has the focus, and Send to phone is offered. |
| A10 | Export on phone | S22 | pass | The system share sheet opened with the zip, and it was cancelled. |
| A11 | Audit trail | TV, S22 | pass | Newest first, with plain-words titles and codes. With Mask on, the host shows as `[HOST]`. With Mask off, the host name and address show. Switching re-maps without a database change. |
| A12 | Add host keyboard on focus | TV | pass | Focusing the Address field does not open the keyboard. |
| A13 | English UI on a pt-BR device | S22, TV | pass after D2 | All texts are English. Only the audit weekday was in Portuguese ("sex."): D2. |

#### Defects found and fixed

Task `s6-devfix` (S6-T23) was merged as `c3f1ce75`. D5 was a regression from
the decode-time FIFO (S6-T7).

| # | Defect | Fix | Re-check |
| --- | --- | --- | --- |
| D1 | TV: OK on Add host never opened the keyboard. `onStartInput` had `inputType=0`: the state-based field with `showKeyboardOnFocus=false` starts no input session, so Gboard refused (`onFailed at PHASE_IME_ON_SHOW_SOFT_INPUT_TRUE`). The first fix (`showSoftInput`) failed the same way. | On TV and handheld, the D-pad focus lands on a wrapper that draws the field. OK moves the focus into the real field, which opens the keyboard through the normal path. Done returns to the wrapper. | Pass: Gboard shows (`inputType=Uri`, `onShown`) and "abc" was typed. Back closes the keyboard, Down goes to Add host, and Up returns to the field without the keyboard. |
| D2 | The audit weekday was in the device language. | The audit times are formatted in English, with the device time zone. | Pass: "Fri 19:33". |
| D3 | TV: Back from the Audit trail left the focus on the rail. | The focus returns to the "Audit trail" row. | Pass. |
| D4 | One bundle showed "196 KB" in Send to phone and "201 kB" in "Diagnostics saved". | One formatter (`Formatter.formatShortFileSize`). | Code and test, recorded. |
| D5 regression | The decode-time FIFO (S6-T7) gave "Decode — ms" on both the TV (MediaTek) and the S22 (Qualcomm). | Reverted on the phase (`c02fdafb`). | The S22 shows a number again ("Decode 0.2 ms"). The MediaTek "—" stays a known limitation. |

#### Block B (with the owner)

| # | Item | Result | Evidence and notes |
| --- | --- | --- | --- |
| B1 | Send to phone | pass | The QR code was scanned with the S22 camera. The zip downloaded (200,866 bytes). The TV log: request 200, download 200, `stopped reason=DOWNLOADED`. "Downloaded" stayed until the owner pressed Close. |
| B2 | A setting takes effect in a stream | pass | Codec H.264. The overlay shows "H.264" and `c2.mtk.avc.decoder.lowlatency`. |
| B3 | Overlay Detailed | pass | TV and S22, three-state setting. |
| B4 | Pinned app shortcut | pass | "Add to home screen" on Desktop, confirmed by the owner. The shortcut icon is a bitmap (the box art from the PC). The host shortcut uses the Borealis monitor icon (`ic_pc_scut`). |
| B5 | Hide an app disables its shortcut | pass | Hide gives `Disabled: by app`. Show again enables it. |

#### Block C (pads, joy.cpl)

| # | Item | Result | Evidence and notes |
| --- | --- | --- | --- |
| C1 | Lone Start or Select reaches the PC; Select and Start held opens the menu, and nothing reaches the PC | pass | Pro 3 (system pad) and Ultimate 2 (USB driver), as the owner watched in joy.cpl. |
| C2 | Mouse row with two pads | pass | The Pro 3 held Start (mouse on), and the Ultimate 2 chord opened the menu. The "Gamepad mouse" row showed On. |
| C3 | Quit combo Start, Back, LB and RB | partial | It ends the stream on the Pro 3 (system pad). Not on the Ultimate 2 (USB driver): the combo is detected only in the system-pad key path (`pendingExit`), never in `reportControllerState`. This is upstream behaviour, not a regression. Sprint 5 C7 was on the Pro only. Follow-up for the next sprint. |
| C4 | USB driver off: no attach dialog | pass | `UsbAttachActivity` is in disabledComponents. The owner re-plugged the dongle and no dialog appeared. The driver was set back on afterwards, and the component is enabled again. |
| C5 | After the stream with the USB driver on, Android asked for USB access again | known | The R5a limitation: Google TV's dialog has no "use by default" option. |

#### Not run

- (b) The RTSP -1 hint: it needs a host failure, so only unit tests cover it.
- Handheld: no device.
- The Expired and No network Send to phone states on a device: only unit and
  screenshot tests cover them.

#### Follow-ups

- The MediaTek decode time still shows "—" (D5). Investigate it next sprint
  with input and output counters. The FIFO approach failed on both decoders.
- The quit combo is not detected for USB-driver pads (`reportControllerState`).
  This is an upstream limitation.
- The Google TV USB dialog repeats while the USB driver is on (R5a, section 10).
- Feature request from the owner, for the new-features phase: a setting for
  the pointer speed of the gamepad mouse. Today the pointer moves so fast that
  it jumps across the screen, and precise control needs a very light touch on
  the stick.

#### Clean-up

- The round's zips on the TV were deleted.
- The settings changed for the round were restored on the TV: 720p, codec auto,
  overlay off, USB driver on, stretch off, unlock off.
- The S22 overlay was set back off, and the zip the S22 downloaded was deleted.
- `/sdcard/ui.xml` was deleted from the S22.
- The local screenshots were deleted.
