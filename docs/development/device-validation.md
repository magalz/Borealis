# Phase 1 device validation runbook

This runbook checks Borealis Phase 1 on two devices against LuminalShine 26.09.1
running on the owner's Windows PC. It is for the owner and for the orchestrator
who runs the checks. Run the steps in order and record the results in the
templates of section 4.

Sources: [host contract](../host-contract/luminalshine.md) (cited as "contract"),
[Borealis Android README](../../apps/android/borealis/README.md),
[building guide](building.md) and the source files named in each step. Anything
that the repository does not confirm is marked `TODO(verify)`; check it on the
device before relying on it.

Placeholders: `<host>` is the LAN address of the PC, `<serial-s21>` is the ADB
serial of the S21, `<serial-tv>` is `<ip>:<port>` of the Google TV Streamer and
`<apk>` is the debug APK. Never type real addresses, PINs, certificates or
pairing codes into this file or into any capture that is committed.

## 1. Prerequisites

### 1.1 Device setup

1. On the S21, open Settings, About phone, Software information and tap Build
   number seven times to enable Developer options. `TODO(verify)`: menu names
   depend on the One UI version.
2. On the S21, open Developer options and turn on USB debugging. Connect the
   phone with a USB cable and accept the debugging prompt on the phone.
3. On the PC, run `adb devices` and note the S21 serial as `<serial-s21>`.
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
5. Install on the S21:

   ```sh
   adb -s <serial-s21> install -r <apk>
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
   and UDP 47998 (video), 47999 (control) and 48000 (audio).
5. Keep the host log open. Its location is the owner's choice; this runbook
   refers to it as "the host log" only.
6. At the start of each scenario, no app runs on the host unless the scenario
   says so. Use the Web UI or the app list to quit a running app.

### 1.4 Logcat capture

Use one capture per device and per scenario. Clear the buffer before each
scenario so that the lines belong to it:

```sh
adb -s <serial> logcat -c
adb -s <serial> logcat -v time Borealis.APP:V Borealis.HOST:V Borealis.PAIR:V Borealis.LAUNCH:V Borealis.RTSP:V Borealis.VIDEO:V Borealis.PYROWAVE:V Borealis.INPUT:V Borealis.REST:V Borealis.CORE:V Borealis.UPSTREAM:V moonlight-common-c:V *:S > <capture-outside-repo>.log
```

Borealis logs use the tag `Borealis.<TAG>` (`LogcatSink`). The debug build logs
at DEBUG and above; release logs at INFO (`BorealisApplication.onCreate`). The
upstream native library logs under the tag `moonlight-common-c`
(`callbacks.c`, `BridgeClLogMessage`). The upstream Java logger `LimeLog` is
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
   ANDROID_SERIAL=<serial-s21> ./gradlew :borealis:native:connectedDebugAndroidTest :app:connectedNonRootDebugAndroidTest
   ```

2. Run the same command with `ANDROID_SERIAL=<serial-tv>`.
3. `TODO(verify)`: that AGP honours `ANDROID_SERIAL` for the connected tasks.
   Without it, the connected tasks run on every attached device. In PowerShell,
   set the variable with `$env:ANDROID_SERIAL = "<serial>"`.
4. Expected: all tests pass. They are:
   - `BorealisCoreInstrumentedTest.versionIsTheCoreProjectVersion`: the core
     version equals the one in `core/CMakeLists.txt` (`0.1.0` at this revision).
   - `GoldenVectorsInstrumentedTest`: `hostCapsVectorsMatch`,
     `launchDecisionVectorsMatch` and `launchFpsVectorsMatch` replay
     `host_caps.tsv`, `launch_decide.tsv` and `launch_fps.tsv` through
     `NativeHostRules`.
   - `JniGuardsInstrumentedTest`: `loadLibrary`,
     `parseCapsRejectsAnAppVersionOver64Bytes` and
     `decideLaunchRejectsAMessageOver256Bytes` (the JNI length guards).
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
5. Then run 3.1 with the correct PIN so that the device ends the scenario paired.
6. Evidence: a screenshot of the message, the logcat capture and the host log
   line for the rejected pairing.

### 3.3 Unpair from the PC menu

1. With the device paired (3.1), open the PC menu and choose "Unpair"
   (`pcview_menu_unpair_pc`). `TODO(verify)`: the gesture that opens the PC menu.
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
2. Start the same app on the S21 from its app list.
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
6. Expected: the client sends no `/cancel` request during the retries. The retry
   code never calls it (README, `host.launch`). `TODO(verify)`: the host log line
   that a `/cancel` request writes, so that the owner can check for it.
7. Caveat: if the second device asks for a different app while the first one
   streams, Moonlight cancels and then launches (contract 7.6). That is the
   upstream behaviour and not a retry. Record it as a separate observation.
8. Evidence: the logcat line `host asked to retry category=PENDING attempt=1 max=K
   delay_ms=N` (fields as in `LaunchRetryRunner`), the final result, and the host
   log.

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
   tag `moonlight-common-c` (`VideoStream.c`, the `Limelog` call). Note the N.
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

## 4. Results templates

Copy one table per device. Use `pass`, `fail` or `not applicable`, and add a
short note for each `fail`.

### 4.1 Samsung S21 (USB ADB)

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
| `<commit tested>` | 12.2 | fork `magalz/moonlight-common-c` `borealis`, upstream base `874ac95` plus the first full frame deadline patch (tag `borealis/874ac95-1`) | 26.09.1 | Samsung S21 (USB ADB) and Google TV Streamer (ADB over Wi-Fi) on `<date>`; instrumented tests `<pass/fail>`; scenarios `<n pass, n fail, n not applicable>`; see the results of this runbook. |

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
