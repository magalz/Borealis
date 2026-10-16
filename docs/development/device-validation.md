# Device validation runbook

This runbook checks Borealis Phase 1 and Sprints 2 to 5 on two devices against
LuminalShine 26.09.1 running on the owner's Windows PC. It is for the owner and
for the orchestrator who runs the checks. Run the steps in order and record the
results as a new section with a table, like sections 7 to 10. Sections 6 to 10
hold the results of Phase 1 and Sprints 2 to 5, in that order.

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

### 3.1 Pairing with the PIN hint

1. On the device, open the PC list, select the PC and choose "Pair with PC"
   (`pcview_menu_pair_pc`).
2. Read the pairing dialog. It shows a four-digit PIN and the hint. Type the
   PIN in step 3 and do not copy it anywhere else.
3. On the PC, open the Web UI at the address of the hint and enter the PIN and
   the device name in the pairing section. `TODO(verify)`: the menu name in the
   Web UI.
4. Expected: the hint reads "On the host, open the LuminalShine Web UI at
   `https://<host>:47990`, enter the PIN there and name this device "`<name>`".
   Names that contain "xbox" or "webos" make the host hold the stream start, so
   do not use them." (`pair_pin_hint`, `strings_pairing.xml`). The URL is the
   HTTP port plus 1 (`PairingHints.webUiUrl`). The suggested name is the device
   model with "xbox" and "webos" removed, falling back to `Borealis`
   (`PairingHints.suggestedDeviceName`). The name must not contain "xbox" or
   "webos" (contract 10.2).
5. Expected: after the PIN is accepted, the PC is shown as paired on the device
   and the Web UI lists the device under the name that was typed (contract 4.1:
   the host takes the name from the Web UI, not from the device).
6. Evidence: a screenshot of the hint with the PIN cropped out, a Web UI
   screenshot with the PIN hidden, and the logcat capture.

### 3.2 Wrong PIN

1. Start pairing as in 3.1 and read the PIN on the device.
2. In the Web UI, enter a different four-digit PIN and any name.
3. Expected: the device shows "Incorrect PIN" (`pair_incorrect_pin`). It must
   not show the 404 text (`error_404`) or the generic "Pairing failed"
   (`pair_fail`).
4. Expected in logcat: a WARN line `unpair not confirmed status=404` on
   `Borealis.PAIR`. `PairingManager` calls the best-effort `/unpair` after a
   wrong PIN and `BestEffortUnpair` logs the XML `status_code` that the host
   returns (`404`, see `core/tests/fixtures/luminalshine/26.09.1/unpair-http.response`).
   The client ignores the outcome, so the 404 does not replace "Incorrect PIN".
5. Start pairing again as in 3.1. The host may answer 400 `Out of order call to
   getservercert`, because it keeps the pairing session after the wrong PIN
   (section 6). Expected: the device shows "The host had an unfinished pairing
   attempt and has cleared it. Enter the new PIN." (`pair_restart_cleared`) and
   opens a new PIN dialog once. Enter the new PIN in the Web UI. Expected: the
   device pairs.
6. If the restarted attempt fails too, expected: a normal failure message and no
   second new PIN dialog. The scenario must not loop. Record the message.
7. If the host answers another pairing error, expected: a specific message from
   `apps/android/borealis/host/src/main/res/values/strings_pairing.xml`, not the
   generic "Pairing failed". Record the message.
8. If the device is not paired at the end of the scenario, run 3.1 with the
   correct PIN.
9. Evidence: a screenshot of each message, the logcat capture and the host log
   line for each rejected pairing.

### 3.3 Unpair from the PC menu

1. Moonlight 12.2 no longer offers "Unpair" in the PC menu (long press on the
   PC shows "Delete PC" and the other entries; checked on the Galaxy S22 on
   2026-10-14). Unpair the device in the host Web UI instead, then use
   "Delete PC" on the device if needed. Steps 2 to 4 apply only to builds
   that still show the entry; the hint is also reached after a wrong PIN
   (3.2), where `unpair not confirmed status=404` is logged.
2. Expected: the device shows "The host did not confirm the unpair request. Open
   the host Web UI at `https://<host>:47990` and unpair this device there."
   (`unpair_webui_hint`). It must not show "Unpaired successfully"
   (`unpair_success`).
3. Expected on the host: the device is still paired. LuminalShine does not route
   `/unpair` (contract section 3 and 18, item 4), so the unpair happens only in
   the Web UI.
4. Expected in logcat: a WARN line `unpair not confirmed status=404` on
   `Borealis.PAIR`.
5. Unpair the device in the Web UI and record whether the PC menu on the device
   still shows the PC as paired. `TODO(verify)`: the device-side state after a
   Web UI unpair.
6. Evidence: a screenshot of the message, a Web UI screenshot, and the logcat
   capture.

### 3.4 PC details dialog

1. With the device paired, open the PC menu and choose "View Details"
   (`pcview_menu_details`).
2. Read the "Host capabilities" block (`host_info_header`).
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

1. In the Borealis settings, set "Video frame pacing" to "Balanced with FPS limit"
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

### 3.13 Borealis settings screen

1. Open Settings and then "Borealis settings" (`borealis_settings_title`).
2. Expected in the category "Privacy and diagnostics": the switches "Verbose
   logging" and "Mask addresses in logs and diagnostics". Both are off by
   default. With "Verbose logging" on, the new log lines include DEBUG lines in
   the file. With "Mask addresses" on, new log lines show `[IP]` and `[HOST]` in
   place of addresses and host names (`Redactor`, README `:borealis:logging`).
3. Expected in the category "About":
   - the version name with the commit;
   - "Source code" opens `https://github.com/magalz/Borealis`;
   - "Licence: GPL-3.0" is plain text;
   - "Open-source licences" shows the NOTICE and LICENSE texts in a dialog;
   - "Privacy policy" opens `https://github.com/magalz/Borealis/blob/main/PRIVACY.md`.
4. On the Streamer: every entry is reachable with the D-pad. The licences text
   scrolls with the D-pad. A link that no browser can open shows its URL as text
   in a dialog.
5. Evidence: screenshots without addresses, and the version line.
   `TODO(verify)`: the exact version text on each device.

### 3.14 Export diagnostics

Steps 1 to 6 are for the phone, steps 7 and 8 for the Streamer, and steps 9 to 11
for both. Never put a bundle from a real run into the repository.

1. On the S22, open Settings, then "Borealis settings", then "Export diagnostics". A toast
   "Creating the diagnostic bundle…" appears, then the share sheet.
2. Send the zip to a test target (Files or Drive). Unzip it on the PC.
3. Expected: the layout is `manifest.json`, `decoders.json`, `audit.jsonl` and
   `logs/*`.
4. Expected: no PIN, token or key appears in any entry. Search for the PIN of 3.1
   and for any `token`, `key` or `cert` value.
5. Expected with "Mask addresses in logs and diagnostics" on: no IP address and no
   host name appears in any entry.
6. Expected: `decoders.json` lists the H.264, HEVC and AV1 decoders with profile
   names, and the display HDR types.
7. On the Streamer, export. A toast "Creating the diagnostic bundle…" appears, and
   then the "Diagnostics saved" dialog, even though apps such as VLC accept shared
   files. The share sheet must not open by itself. The dialog shows
   `Download/borealis-diagnostics-<timestamp>.zip` and the matching `adb pull`
   command. Expected: there is no "Share…" button, only Close, which has the focus,
   and the D-pad scrolls the text. On a phone with no receiver the dialog does have
   a "Share…" button.
8. On the PC, run the `adb pull` command of the dialog, or:

   ```sh
   adb -s <serial-tv> pull /sdcard/Download/borealis-diagnostics-<timestamp>.zip
   ```

   Expected: the zip opens and has the layout of step 3. On Android 9 (API 28) the
   file is in the app's files directory instead, and the dialog shows that
   `adb pull /sdcard/Android/data/<package>/files/diagnostics/...` command. The
   package is `io.github.magalz.borealis.debug` for debug builds and
   `io.github.magalz.borealis` for release builds.
9. Export twice. Expected: only the newest bundle remains (in `cacheDir` and in
   Downloads). Files of other apps in Downloads are not touched.
10. Tap "Export diagnostics" twice quickly. Expected: the second tap does nothing
    while the first bundle is built.
11. Leave the screen (Back) while the bundle is built. Expected: no crash in the
    logcat capture (no `FATAL EXCEPTION` line).
12. Evidence: the unzipped layout (a listing, no contents), the logcat capture and
    a screenshot of the dialog or the share sheet.

### 3.15 Audit trail

1. Pair the S22 with the host (3.1), launch an app (3.9 steps 1 and 2), stream it
   for about one minute, and then quit it from the PC's app list with "Quit
   Session" (`applist_menu_quit`).
2. Export a bundle (3.14, steps 1 and 2).
3. Expected in `audit.jsonl`: entries of the types PAIRING_*, LAUNCH_* and
   SESSION_START and SESSION_END, with the fields documented in
   [privacy-and-logging.md](../policies/privacy-and-logging.md) (section "Audit
   trail").
4. Expected: no PIN, token, key or certificate in any entry. With "Mask addresses"
   on, no IP address and no host name.
5. Open "Erase audit trail" (Privacy and diagnostics). Expected: a confirmation
   dialog with Cancel focused. Choose Cancel first: nothing is erased. Then start
   it again and choose "Erase": the message "Audit trail erased" appears.
6. Export a bundle. Expected: `audit.jsonl` has no entries from before the erase.
   The bundle exported in step 2 is not changed.
7. Evidence: the unzipped `audit.jsonl` (scrubbed), and screenshots of the dialog.

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

## 4. Results templates

Copy one table per device. Use `pass`, `fail` or `not applicable`, and add a
short note for each `fail`.

### 4.1 Samsung Galaxy S22 (USB ADB)

| Scenario | Result | Evidence | Notes |
| --- | --- | --- | --- |
| 2. Instrumented tests | | Gradle result, serial | |
| 3.1 Pairing with the PIN hint | | | |
| 3.2 Wrong PIN | | | |
| 3.3 Unpair from the PC menu | | | |
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
| 3.1 Pairing with the PIN hint | | | |
| 3.2 Wrong PIN | | | |
| 3.3 Unpair from the PC menu | | | |
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

Not run:

- Gamepad Select+Start chord (UC-004-T2) and USB-driver pads: no real
  controller. adb-injected events do not count as a gamepad.
- Picture-in-picture with the menu open, and rotation with the on-screen
  controller: not tried.
- Error screen states on a device (host offline, certificate changed, blocked
  ports): not reproducible without powering off the PC or changing firewall
  settings. They are covered by unit and screenshot tests. The Quit defect (D2)
  showed the generic error path before its fix.
- Full pairing and Send to phone at 720p (Sprint 4 follow-ups).

Follow-ups:

- The gamepad Select+Start chord and USB-driver pads: need a real controller.
- Picture-in-picture with the menu open.
- Rotation with the on-screen controller.
- The error screen states on a device.
- Full pairing and Send to phone at 720p (from Sprint 4).
- The MediaTek decoder decode time, shown as "—".
- The host's slow `/resume` after a disconnect (NortheBridge/luminalshine#198).
- The host ends the stream before it answers `/cancel`. The client now shows
  "Quitting" (D2).
- The TV channel prompt after pairing.
- The pinned app shortcut icon.
- `PairingHints` dead code: `pinHint` and `unpairHint` have no caller since
  `PcView` was removed (see the Borealis Android README).
