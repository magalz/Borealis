# Device validation runbook

This runbook checks Borealis Phase 1 and Sprints 2 to 10 on two devices against
LuminalShine 26.09.1 and 26.10.0-beta.1 running on the owner's Windows PC. It is
for the owner and for the orchestrator who runs the checks. Run the steps in
order and record the results as a new section with a table, like sections 7 to
10. Sections 6 to 15 hold the results of Phase 1 and Sprints 2 to 10, in that
order.

Sources: [host contract](../host-contract/luminalshine.md) (cited as "contract"),
[Borealis Android README](../../apps/android/borealis/README.md),
[building guide](building.md) and the source files named in each step. Anything
that the repository does not confirm is marked `TODO(verify)`; check it on the
device before relying on it.

Placeholders: `<host>` is the LAN address of the PC, `<host-id>` is the unique
ID of a host (section 3.21 step 7), `<serial-s22>` is the ADB serial of the S22,
`<serial-tv>` is the serial that `adb devices` shows for the Google TV Streamer
(`<ip>:<port>` or an mDNS name), `<serial>` is the serial of whichever of the
two devices a command runs on, and `<apk>` is the debug APK. Never type real
addresses, PINs, certificates or pairing codes into this file or into any
capture that is committed.

## 1. Prerequisites

### 1.1 Device setup

1. On the S22, open Settings, About phone, Software information and tap Build
   number seven times to enable Developer options. `TODO(verify)`: menu names
   depend on the One UI version.
2. On the S22, open Developer options and turn on USB debugging. Connect the
   phone with a USB cable and accept the debugging prompt on the phone.
3. On the PC, run `adb devices` and note the S22 serial as `<serial-s22>`. The
   S22 can use Wireless debugging instead of USB, as the Streamer does in steps
   5 to 9. Its serial is then `<ip>:<port>` or an mDNS name, as for the
   Streamer.
4. On the Google TV Streamer, connect it to the same LAN as the PC. Open
   Settings, System, About and select Android TV OS build seven times to enable
   Developer options. `TODO(verify)`: menu names depend on the Google TV version.
5. On the Streamer, open Developer options and turn on Wireless debugging.
6. Open Pair device with pairing code. The screen shows an IP address, a
   pairing port and a six-digit code. Do not copy them into any file. On the
   PC, run `adb pair <ip>:<pairing-port>` and type the code when asked.
7. Back on the Wireless debugging screen, read the connect port. It differs
   from the pairing port. On the PC, run `adb connect <ip>:<connect-port>`.
8. Run `adb devices` and note the Streamer as `<serial-tv>`, the serial that
   `adb devices` shows: `<ip>:<connect-port>`, or an mDNS name such as
   `adb-<id>._adb-tls-connect._tcp`. Pass it to `-s` exactly as shown.
9. Reconnect after the Streamer sleeps or reboots, or after Wireless debugging
   is switched off and on. The connect port changes each time, and a plain
   `adb connect <ip>:5555` no longer answers (as of 2026-10-18). Run
   `adb mdns services`: it lists the Streamer with its current address and
   connect port. Connect to that address, or use the mDNS serial that
   `adb devices` shows, and note `<serial-tv>` again. If the list is empty,
   read the new connect port on the Wireless debugging screen, as in step 7.

### 1.2 Build and install the debug APK

1. Follow the toolchain setup of [building.md](building.md) (JDK 17 and the
   Android SDK).
2. From the repository root, run:

   ```sh
   cd apps/android
   ./gradlew assembleDebug
   ```

3. The debug APK is
   `apps/android/app/build/outputs/apk/debug/app-debug.apk`
   (checked on the 2026-10-17 Sprint 8 branch build); it is `<apk>` below.
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
   proposal P1), so the version in the Host details dialog is the protocol
   version `7.1.431.-1`, not `26.09.1`. On 26.10.0-beta.1 the HTTPS
   `/serverinfo` carries `LuminalShineVersion` (#199; verified in the source at
   `46f6936d`, see the [host contract](../host-contract/luminalshine.md)). The client does not show it yet
   (Sprint 17), so Host details still shows `7.1.431.-1` there too, and must
   show no error because of the new field. The Sprint 10 checks (section 3.23)
   run against 26.10.0-beta.1; its plain-HTTP captures are in
   `core/tests/fixtures/luminalshine/26.10.0-beta.1/`.
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
   says so. Use the Web UI or Apps to quit a running app.

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
   ANDROID_SERIAL=<serial-s22> ./gradlew :borealis:native:connectedDebugAndroidTest :app:connectedDebugAndroidTest
   ```

2. Run the same command with `ANDROID_SERIAL=<serial-tv>`.
3. AGP honours `ANDROID_SERIAL`: the connected tasks run only on that device
   (checked 2026-10-14). In PowerShell, set it with
   `$env:ANDROID_SERIAL = "<serial>"`. On Windows, a device connected over
   ADB Wi-Fi with an `<ip>:<port>` serial reports `There were failing tests`
   from the connected task even when every test passed (it cannot write the
   result file named after that serial). `TODO(verify)`: whether the task
   passes for an mDNS serial, which has no port. For such a device, install the
   test APKs and run the instrumentation directly, then check that every test
   reports `INSTRUMENTATION_STATUS_CODE: 0` and the run ends with
   `INSTRUMENTATION_CODE: -1`. The connected task of step 1 builds the test
   APKs. From the repository root, they are (the app tests also need the debug
   APK of 1.2 on the device):
   - native module:
     `apps/android/borealis/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk`
   - app:
     `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

   ```sh
   adb -s <serial-tv> install -r apps/android/borealis/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
   adb -s <serial-tv> install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb -s <serial-tv> shell am instrument -r -w -e expectedCoreVersion 0.1.0 io.github.magalz.borealis.nativecore.test/androidx.test.runner.AndroidJUnitRunner
   adb -s <serial-tv> shell am instrument -r -w -e expectedCoreVersion 0.1.0 io.github.magalz.borealis.debug.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Expected: all 10 tests pass. They are:
   - `BorealisCoreInstrumentedTest.versionIsTheCoreProjectVersion`: the core
     version equals the one in `core/CMakeLists.txt` (`0.1.0` at this revision).
   - `GoldenVectorsInstrumentedTest`: `hostCapsVectorsMatch`,
     `launchDecisionVectorsMatch`, `pairClassifyVectorsMatch` and
     `launchFpsVectorsMatch` replay `host_caps.tsv`, `launch_decide.tsv`,
     `pair_classify.tsv` and `launch_fps.tsv` through `NativeHostRules`.
   - `JniGuardsInstrumentedTest`: `parseCapsRejectsAnAppVersionOver64Bytes`,
     `decideLaunchRejectsAMessageOver256Bytes` and
     `classifyPairingRejectsAMessageOver256Bytes` (the JNI length guards).
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
5. Evidence: a screenshot of the Pairing screen with the PIN and the Web UI
   address cropped out (the screen shows both), a Web UI screenshot with the PIN
   and the address hidden, and the logcat capture.

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

Two places remove the pairing. The Forget option in the host Options of the PC
on the device removes the PC from this device. Removing the device in the
LuminalShine Web UI (Clients page) ends the pairing on the PC. LuminalShine does
not route `/unpair` (contract sections 3 and 18, item 4), so the device must be
removed in the Web UI.

1. Open Hosts and open the host Options of the PC: long-press its card on a
   phone; on a TV, hold OK, or press X or Menu on a pad. Choose Forget and read
   the dialog.
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

1. The PC details dialog went away with `PcView` in Sprint 5. Its content is
   back in the Host details dialog. With the device paired, open Hosts, open
   the host Options of the PC (long-press its card on a phone; on a TV, hold OK,
   or press X or Menu on a pad) and choose Details. The card itself shows the
   address and the codec and HDR tags.
2. The dialog starts with plain lines that `HostDetailsText` builds: Name,
   Status, Address, Other addresses, Paired, MAC address (for wake) and Running.
   A line whose value is not known is left out, except Status, which reads
   "Checking…" until the host has answered. An address shows its port only when
   it is not the default HTTP port. The lines hold no UUID and no HTTPS port. The
   capability block, which `HostInfoText` builds, follows after a blank line. The
   diagnostic bundle does not hold either: it has only the `host capabilities`
   log line of step 4.
3. Expected for the 26.09.1 capture (`serverinfo-http.xml`, codec mask
   `0x001C0301`, codec flags `0x13` in `host_caps.tsv`), in the block of the
   dialog:
   - `Host software: Sunshine-compatible, version 7.1.431.-1`
     (`host_info_software_version`; the version is the `appversion` of
     `/serverinfo`, not the LuminalShine release)
   - `Video: HEVC, HEVC 10-bit, 4:4:4` (no AV1 entry)
   - `PyroWave: off` (`host_info_pyrowave_off`)
   - `Ports: HTTP 47989, HTTPS 47984` (`host_info_ports`; the HTTP port is the
     one the client stores, the HTTPS port comes from `HttpsPort`)
4. Expected in logcat: one INFO line `host capabilities` on `Borealis.HOST` with
   the fields `family`, `version`, `hevc`, `hevc_main10`, `av1`, `av1_main10`,
   `yuv444`, `pyrowave`, `http_port` and `https_port` (`HostInfoReader.log`).
   The reader runs on every `/serverinfo` poll and logs a host only when its
   information changes, so the line does not repeat while nothing changes.
5. Evidence: a screenshot of the dialog and the logcat capture. The dialog
   shows the name, the addresses and the MAC address of the PC: hide them
   (section 5).

### 3.5 Two devices launching at the same time

1. Pair both devices with the host (3.1). Make sure no app runs on the host.
2. Start the same app on the S22 from Apps.
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
   Cancel (`apps/android/borealis/stream/src/main/res/values/strings_conflict.xml`).
   Focus starts on Cancel. The text warns that quitting ends the session on the
   other device (since Sprint 9 the warning differs for Desktop and for a game; see
   section 3.22, step 5).
7. Run the three answers, each in a new attempt. Start the same app on the
   Streamer while the S22 runs it, and answer the dialog as follows:
   - Join: the device joins the running session and streams.
   - Quit and relaunch: the S22 session ends and the app starts on the Streamer.
   - Cancel: the device returns to Apps with no error dialog.
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
2. Quit the app on the PC, in one of two ways:
   - in the in-game menu, choose "Quit `<app>`" (`stream_menu_quit`) and
     confirm; the screen shows "Quitting `<app>`…" until the host answers;
   - or leave the stream, open Apps, open the actions of the running app, choose
     "Quit `<app>`" (`apps_action_quit`), confirm, and wait for the message "App
     quit." (`apps_message_quit_done`).
3. Immediately launch the same app again from Apps.
4. Expected: either the launch succeeds at once, or the device shows "The host is
   still restoring the display after the previous stream. Retrying in N s (retry
   M of K)." (`launch_retry_restoring_display`) and then streams. The host answers
   503 "The previous stream is still restoring the display; retry the connection."
   after up to 8 s of waiting (contract 7.5).
5. The 503 depends on timing. If no retry happens, record the time between the
   quit and the launch and mark the scenario "not reproduced". This is not a
   failure.
6. Evidence: the logcat line `host asked to retry category=RESTORING_DISPLAY ...`,
   the time between quit and launch, and a screenshot of the retry text on the
   Starting screen (no stream picture, section 5).

### 3.7 Frame pacing "cap FPS" at the display rate

1. In Settings, set "Frame pacing" to "Balanced with fps limit"
   (value `cap-fps`, `settings_choice_pacing_balanced_cap`).
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

1. Produce a retry as in 3.5 or 3.6, so that the Starting screen shows the retry
   text. The screen shows the four steps of the start, a "Host busy" line (shown
   in capitals, "HOST BUSY") with the retry text, and Cancel with the focus.
2. Press Back while the Starting screen is shown.
3. Expected: the launch ends without a stream. No further `host asked to retry`
   line appears in logcat after Back. The client sends no `/cancel`, as in 3.5
   step 6. `Game` calls `cancelLaunch()` from `onStop` and `onDestroy`
   (`Game.java`, README `host.launch`).
4. Launch the same app again from Apps when the host is free.
5. Expected: the second launch proceeds and reaches the first frame (3.9). A
   launch that hangs on the Starting screen with no retry line and no stream is
   a failure. It would point to a permit that was not released.
6. Evidence: the logcat capture across both launches, with the first-frame line
   of 3.9 for the second one, and a screenshot of the Starting screen with the
   "HOST BUSY" line. Take no screenshot of the first frame: it shows the PC
   screen (section 5).

### 3.9 First frame

1. Start a stream from a host with no app running. Note the start time.
2. Expected: the Starting screen covers the stream from the start until the
   first video frame. Its steps say where the start is, and its step "Preparing
   the display" (`stream_starting_step_display`) is the only place that says so.
   No overlay text "The host is preparing the display." shows at any time:
   Sprint 9 removed it (issue #31), because it was never visible under the
   Starting screen.
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
   Starting screen (not of the stream, section 5) and the logcat capture.

### 3.10 Stream end and reconnect

1. Stream an app for about one minute.
2. End the stream from the device: open the in-game menu and choose "Disconnect"
   (`stream_menu_disconnect`). The app keeps running on the PC.
3. Wait until the Library is shown, then launch the same app again.
4. Expected: the app still runs on the host, so the client resumes it (`/resume`)
   and the stream starts. If the host answers "No running app to resume" (503),
   the client falls back to `/launch` (contract 7.5, `FALLBACK_TO_LAUNCH`).
5. Evidence: the logcat capture and the first-frame line of 3.9. Take no
   screenshot of the stream (section 5).

### 3.11 H.264 and HEVC

1. In Settings, Video, set the row "Codec" to "H.264" (`neverh265`) and stream
   for one minute. Then set "HEVC" (`forceh265`) and stream for one minute. Use
   the same app. The choices are "Automatic" (summary "Recommended", the default,
   `auto`), "AV1" (summary "Experimental", `forceav1`), "HEVC" and "H.264".
2. Expected for H.264: the stream plays. Logcat has no `HEVC SPS` line.
3. Expected for HEVC: the stream plays. Logcat has one line in the form
   `HEVC SPS: profile=<name> chroma=<name> depth=<luma>/<chroma>` on
   `Borealis.VIDEO` (`HevcSpsInfo.describe` and `HevcSpsMonitor`). For an 8-bit SDR
   stream the line is `HEVC SPS: profile=Main chroma=4:2:0 depth=8/8`.
4. AV1: not applicable to this host. The 26.09.1 capture does not advertise AV1
   (codec mask `0x001C0301` has no AV1 bits, and the Host details dialog has no
   AV1 entry). Do not test the Codec choice "AV1" ("Experimental") against this
   host and record it as "not applicable".
5. Evidence: the logcat lines of step 3. Take no screenshot of the playing stream
   (section 5); a crop of the performance overlay or of the in-game menu is
   enough if you want one.

### 3.12 HDR and HEVC Main10

S22 only. The "HDR" row is hidden on a display that does not report HDR10, as on
the TV attached to the Streamer (section 11, item A5). Record the Streamer as
"not applicable" unless its row shows.

1. In Settings, Video, turn on "HDR" (`settings_hdr_title`) and set Codec to
   "HEVC". Stream an HDR-capable app. `TODO(verify)`: the app and the
   HDR-capable display of the S22. The 26.09.1 capture advertises HEVC Main10
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
6. Evidence: the logcat lines of steps 2 to 4 and the device model. Take no
   screenshot of the stream (section 5).

### 3.13 Settings

1. Phone: on Hosts, tap the gear, then Settings. TV and handheld: in the rail,
   choose Settings. The groups are on the left and their rows on the right.
2. Expected: the groups are Stream, Video, Input, Gamepad, On-screen controls,
   Audio, Advanced, Appearance, Privacy and diagnostics, and About. The Appearance
   group holds the Background row (3.22). The On-screen controls
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
5. Expected with "Mask addresses in logs and diagnostics" on: no IP address
   appears in any entry. Host names are masked only in the host part of a URL and
   in the fields `host`, `hostname`, `computer` and `computerName`
   ([privacy-and-logging.md](../policies/privacy-and-logging.md)), so the PC's
   host name can still appear in free text, such as an upstream `LimeLog` line.
   Search for it and record where it appears.
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

1. Start the logcat capture of 1.4. Choose Add host and enter the address of the
   PC. Let the add finish or fail.
2. Expected: no "blocked ports" connection test result and no delay beyond the
   normal timeout. The lookup of an external address and the connection test
   return at once, because their JNI entry points are disabled
   (`simplejni.c`, README `:borealis:settings`).
3. Expected: the host Options of the PC are Details, Wake (for an offline host)
   and Forget, with no "Test network connection" entry.
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
   (the `versionName` and the commit), the logcat capture and a screenshot (see
   section 5).

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
5. Visual refinement. Run on the Google TV Streamer, and on the S22 where a
   step says so. Record each item as `pass`, `fail` or `not applicable`.
   - Dialogs share one frame: Compact 420 dp, Standard 560 dp and Wide 720 dp
     on a TV. On a phone the width is the window less 16 dp on each side, when
     that is narrower. Open Forget, Erase (in Privacy and diagnostics), Saved
     with Send to phone, About, Host details and App details, one at a time.
     Expected: each dialog has one of the three widths. Simple confirmations are
     Compact, with the focus on the safe action. For Erase, the safe action is
     Cancel, and Erase is in the danger tone.
   - Settings gear and rail: on the TV, the Hosts rail is 220 dp wide and shows
     the Settings gear. Expected: the gear opens Settings.
   - Back arrows and chevrons: open a screen with a back arrow and a Settings row
     that opens something. Expected: the back arrow and the chevron look the
     same on every screen.
   - Host cards: a host card is one focus stop, with no action button. OK, or a
     tap, runs its action: open when the host is online and paired, pair when it
     is not paired, and wake when it is offline. Hold OK, or long-press the card,
     to open its options. On the TV, also press X or Menu on a pad: the options
     open. Expected: each action runs as described, and the options open only
     from the hold, the long press, X or Menu. On the TV, the cards have equal height
     (at least 176 dp, `size.host-card.height-tv` in `design/tokens.json`), so a
     row lines up. On a handheld and on a phone, the cards size to their content
     (Sprint 9, issue #25; checked in 3.22 step 10). With a pad connected in
     landscape, the S22 is a handheld (step 7).
   - Aurora loader: open Hosts, Apps and Pairing while each one loads. Expected:
     the same aurora loader on all three.
   - Pairing check: on Pairing, the PIN is centred with its line below it. When
     the pairing succeeds, "Paired" is centred and the check is drawn once.
   - Error actions: force a connection error. Expected: on the S22 the actions
     stack, in two rows. On the TV they stay in one row.
   - TV keyboard row: open the keyboard from the Add host field. Expected: the
     command row under the keys is centred.
   - Held keys, on the TV. Hold OK on an app tile in Library, Apps, and keep it
     held while the app actions sheet opens. Expected: the sheet stays open when
     OK is let go, and nothing runs. A fresh press of OK then chooses an option.
     Then, in a stream, open the in-game menu and hold OK in the open menu, then
     let it go. Expected: nothing runs on the release. A fresh press of OK acts
     on the focused row.
   - Held keys on a host card: on an online, paired host card, press OK. Expected:
     the app list opens. Then hold OK on a host card. Expected: only the host
     options open, and the release runs nothing.
   - Held keys are not consumed for the system keys (volume, media, channel,
     mute, headset). Press one while a sheet is open. Expected: the key still
     works.
6. The USB dialog on the Streamer (R5a). Fixed in Sprint 7 (issue #16, merge
   `0ad99565`). On a TV the attach dialog is not offered. The permission dialog
   is asked once, only after the stream shows its first frame. Check each step
   with the USB driver on.
   - Clear the logcat buffer and start the capture of 1.4 on the Streamer, with
     the USB driver on.
   - In the Library, plug in the 8BitDo receiver, or switch the pad on and off.
     Expected: no attach dialog appears. The receiver changes its identity by
     itself (idle `2dc8:6013`, active `2dc8:310b` XInput). The attach lines can
     still appear in logcat. They are logged by Android's system server with the
     tag `UsbHostManager` ("USB device attached", "Removed device at"); it is not
     a Borealis class.
   - Start a stream. Expected: the USB permission dialog appears only once the
     picture shows, not before.
   - During the stream, switch the pad off and on. Expected: one dialog, the
     permission dialog. Accept it. Expected: the pad works after the accept.
   - End the stream. Expected: no attach or remove line appears at the end of the
     stream.
   - On a phone, the attach dialog is unchanged. Note what it shows, if it does.
7. The handheld with a Bluetooth pad, on the S22 in landscape. A window 600 dp
   or wider is a handheld while a game controller is connected. Use a pad that
   has a hat D-pad, such as the 8BitDo Pro 3 or the 8BitDo Ultimate 2, paired
   over Bluetooth. A Bluetooth pad is always a system pad: the USB driver
   setting does not apply to it.
   - Layout: with the pad off, open the Library. Switch the pad on. Expected:
     the Library switches to the handheld layout, with a rail of Hosts, Add
     host and Settings, and host cards that size to their content (Sprint 9).
     Switch the pad off.
     Expected: the phone layout returns. Neither change needs the app to be
     reopened.
   - Stream: start a stream with the pad only, on a host card, then Desktop.
     Expected: the stream starts. Hold Select and Start: the menu opens.
     Expected: Gamepad mouse on shows the Pointer speed row, with Slow, Normal
     and Fast. Move the pointer with the stick and hold RB: the pointer moves
     at a lower speed while RB is held. Scroll with the right stick in a PC app
     that scrolls. Expected: the page scrolls. Open the keyboard. Expected: it
     shows over the video, not docked under it.
   - Quit: press Start, Select, LB and RB together, then let all four go.
     Expected: the stream ends. No capture is taken during the stream, because
     it would show the PC screen.

### 3.21 Sprint 8 checks

Run these with the Sprint 8 build on the S22 phone and the Google TV Streamer.
Sprint 8 removed and reworked code that these paths use, so this is a smoke
round, not a full pass. Record each item as `pass`, `fail` or `not applicable`,
with a short note for each `fail`. Where a step names one device, run it there
only.

1. Library. Open Hosts and Apps on each device. Expected: both screens load,
   and the host cards and app tiles respond as they did in Sprint 7.
2. Start and end a stream. Start an app from the Library, wait for the first
   frame, then end the stream from the in-game menu. Expected: the stream starts
   and ends as in 3.9 and 3.10.
3. Pads. Stream with a system pad, then with a USB-driver pad (turn USB driver
   for Xbox controllers on in Settings, Gamepad). Press Start, Select, LB and RB
   together, then let all four go. Expected: the stream ends for both pads, as
   in 3.20 step 3.
4. Mouse capture. On a stream with a mouse, move the pointer. Expected: the
   pointer is captured and moves the PC pointer. End the stream. Expected: the
   pointer is released. Only the native pointer capture provider remains.
5. On-screen keyboard. In a stream, open the Borealis keyboard from the in-game
   menu, and type a few keys. Type only into a test file on the PC, for example
   an open Notepad file (section 5). Expected: the keys reach the PC.
6. Decode overlay. Turn the Performance overlay on, start a stream, and read the
   decode time. Expected: a number in milliseconds, as in 3.20 step 4.
7. TV channel and box art (Streamer only). The Apps screen shows initials until
   Phase 3, so check the posters through the provider. `<host-id>` below is the
   unique ID of the host, not its address (`<host>` is the address), and
   `<app-id>` is the number of an app. The debug build has the package
   `io.github.magalz.borealis.debug`.
   - Find the IDs of the cached posters. The first command lists the host IDs,
     the second lists the files `<app-id>.png` of one host:

     ```sh
     adb -s <serial-tv> shell run-as io.github.magalz.borealis.debug ls cache/boxart
     adb -s <serial-tv> shell run-as io.github.magalz.borealis.debug ls cache/boxart/<host-id>
     ```

   - Read the poster of a cached app through the provider. Expected: the PNG of
     the cache file, byte for byte. Check it by comparing the md5 of what
     `content read` returns with the md5 of the cache file read through
     `run-as`. The two sums must be equal:

     ```sh
     adb -s <serial-tv> shell "content read --uri content://poster.io.github.magalz.borealis.debug/boxart/<host-id>/<app-id> | md5sum"
     adb -s <serial-tv> shell run-as io.github.magalz.borealis.debug md5sum cache/boxart/<host-id>/<app-id>.png
     ```

   - Expected: a `FileNotFoundException` for an app ID that is not a number.
   - Google TV home screen: section 11 found that it shows no third-party
     channels, so no Borealis channel is expected there. If one shows, its
     posters load too.

   `PosterContentProvider` and `DiskAssetLoader` changed in Sprint 8.
8. Decoder crash notice. There is no safe way to make the decoder crash on
   purpose, so this step runs only if the notice appears by itself. Expected: it
   has an OK button and no Help button. `UpstreamSeamsTest` covers it in the
   unit tests.
9. Debug APK. Install the debug APK over the installed build with
   `adb -s <serial> install -r <apk>`, without uninstalling. Expected: the
   install succeeds, the package is `io.github.magalz.borealis.debug`, and the
   launcher label is `Borealis Debug`, as in 1.2 step 4.
10. Evidence: for each item, the note above. A screenshot is taken only where
    it shows no address, host name, PIN or PC screen (section 5), and the logcat
    capture of 1.4.

### 3.22 Sprint 9 checks

Run these with the Sprint 9 build on the Google TV Streamer and on the S22 in
landscape with a pad. The S22 with a pad is the owner's handheld. Sprint 9
reworked Settings, added the background and changed Quit and the pad rules, so
this is a full pass of those paths. Record each item as `pass`, `fail` or `not
applicable`, with a short note for each `fail`. Where a step names one device, run
it there only. The Streamer shows nine Settings groups, because it has no
touchscreen and so no On-screen controls group. The S22 shows ten.

A debug build that was just installed runs slower until the JIT has warmed up.
Before the Settings steps, open Settings and move through every group a few
times. Section 14 says how the group switch was measured.

Steps 1 to 9 start on the Streamer; steps 5 and 9 also use the S22. Steps 10 to
17 run on the S22 in landscape with a Bluetooth pad, which is a system pad,
except where a step says otherwise.

1. Settings, opening and groups.
   - Open Settings from the rail with the D-pad. Expected: focus lands on the
     Stream group, not on the rail item.
   - Expected: the list shows Stream, Video, Input, Gamepad, Audio, Advanced,
     Appearance, Privacy and diagnostics and About, all whole inside the window,
     with no scrolling. Moving down the groups does not scroll the list.
   - Press Left on the group. Expected: focus returns to the Settings item of the
     rail.
2. Settings, moving between groups.
   - Press Down and Up over the groups. Expected: the pane changes together with
     focus, and a switch feels instant. There is no wait.
   - Hold Down over the groups, through Privacy and diagnostics to About, and
     back with Up. Expected: no stutter. Privacy and diagnostics may take a
     moment the first time, because its pane is built when it is selected (about
     230 ms in section 14).
   - Press Down once and then Right at once. Expected: Right enters the first row
     of the new group, not of the group left behind, and focus is not lost.
   - In a group with several rows, press Right and scroll down with the D-pad.
     Move to another group and back. Expected: the group opens at its top.
   - Change a switch in one group and move to another group. Expected: the
     switches of the other group show their own state, and no thumb slides.
   - Left from a row returns to the selected group.
3. Appearance, Background.
   - Select the Appearance group, then the Background row. Expected: the row shows
     Aurora curtains. Its list offers, in this order, Aurora curtains, Soft glow,
     Northern lights, Light mist, Flowing light, Starfield and Off, with Aurora
     curtains focused.
   - Choose each of the seven styles in turn. Expected: the background behind
     Settings changes at once, with no restart. Off is the plain page colour. The
     four clips (Northern lights, Light mist, Flowing light and Starfield) play and
     loop without a visible seam. Their still shows until the clip plays. Aurora
     curtains and Soft glow move slowly. Record any stutter, and the style.
   - Choose Starfield, leave Settings and come back, then stop the app (`adb -s
     <serial-tv> shell am force-stop io.github.magalz.borealis.debug`) and open it
     again. Expected: the choice is kept.
   - Set the style back to Aurora curtains.
4. The background across the screens, and not in a stream.
   - Choose Northern lights. Open Hosts, Apps, Add host, Settings and Privacy and
     diagnostics, then the Audit trail. Expected: the background shows behind
     each of them. The rail keeps its own colour. No screen shows a page colour of
     its own.
   - Move between the screens. Expected: the screen that is left goes at once and
     the next one fades in over about 150 ms, with no frame that shows two
     screens one over the other.
   - Start a stream. Expected: no background shows on the Starting screen or in
     the stream, and the stream runs as before. During the stream the Library is
     stopped, so no player of the background runs (`adb -s <serial-tv> shell
     dumpsys activity activities` shows `LibraryActivity` as stopped;
     `TODO(verify)`: the exact line). End the stream. Expected: the background
     plays again.
   - Turn the system animations off (Developer options, Animator duration scale
     off, or `adb -s <serial-tv> shell settings put global animator_duration_scale
     0`). Stop the app and open it again. Expected: Aurora curtains is one still
     frame, and a clip style shows its still, with no movement. Turn the
     animations back on (`1`).
5. Quit texts. Run the game part first, then the Desktop part. The owner picks
   the game (an app that is not Desktop) and ends it in their own way, saving
   first, before the Desktop part.
   - Game, in the stream: start the game on the Streamer and open the in-game
     menu. Expected: "Quit `<game>`" with "Closes `<game>` on the PC." and
     "Unsaved progress in it is lost." on two lines. Choose it. Expected: the
     confirmation is titled "Quit `<game>`?" with the same two lines as its body,
     and Cancel has the focus. Choose Cancel; do not confirm. Disconnect is
     unchanged ("`<game>` keeps running on the PC"). Leave with Disconnect.
   - Game, in Apps: open the actions of the running game. Expected: Quit has the
     two game lines. Then open the actions of another app. Expected: "Quit
     `<game>` and start `<other>`" says "Closes `<game>` on the PC first." and
     "Unsaved progress in it is lost." Its confirmation is titled "Quit
     `<game>`?" with the consequence as its body. Choose Cancel.
   - Game, conflict dialog: with the game still running, start another app of the
     same host on the S22. Expected: "An app is already running", and the warning
     reads "Quitting ends the stream on the other device and closes the app." and,
     on a second line, "Unsaved progress in it is lost." Cancel has the focus.
     Choose Cancel. The owner then ends the game.
   - Desktop, in the stream: start Desktop on the Streamer and open the in-game
     menu. Expected: "Quit Desktop" with "Ends the stream." and "The display and
     controls go back to the PC." on two lines. The confirmation is titled "Quit
     Desktop?" with the same body. Choose Cancel, then leave with Disconnect.
   - Desktop, in Apps: open the actions of Desktop. Expected: Quit has the two
     Desktop lines. Open the actions of another app. Expected: "Quit Desktop and
     start `<other>`" says "Ends the stream first." and "Nothing is lost." Choose
     Cancel.
   - Desktop, conflict dialog: with Desktop still running, start another app on
     the S22. Expected: the warning reads "Quitting ends the stream on the other
     device." only. Choose Cancel.
   - Finally, on the Streamer, quit Desktop and confirm. Expected: "Quitting
     Desktop…", then the Library with "Nothing running".
6. Host details. Open the host options of the PC and choose Details. Expected:
   - plain lines: `Name`, `Status`, `Address`, `Other addresses` when the host has
     any besides the one in use, `Paired`, `MAC address (for wake)` and
     `Running` while an app runs. A line whose value is not known is missing. The
     status is `Online`, `Offline` or `Checking…`;
   - the address has no port when it is the default HTTP port;
   - no UUID, and no `null` or `OFFLINE` text;
   - the capability block follows after a blank line, with its ports (3.4).

   The dialog shows addresses and the MAC address of the PC: hide them in any
   capture (section 5).
7. The Starting screen. Start a stream. Expected: the Starting screen shows its
   steps, including "Preparing the display", and no "The host is preparing the
   display." text appears over it or over the stream at any time (3.9 step 2).
8. Send to phone, the two states that no round has run. Export the bundle (3.14
   step 7) and choose Send to phone, then do not download.
   - Expired: wait 5 minutes. Expected: "The link expired. Try again for a new
     one."
   - No network: the owner turns the Wi-Fi of the Streamer off, then opens Send to
     phone again. Expected: the dialog says "This device is not on a local network,
     so a phone cannot reach it. Use the adb command below instead." and shows the
     adb command. ADB runs over Wi-Fi, so the connection drops: read the
     result on the screen, then turn the Wi-Fi on and connect again (1.1 step 9).
     Do not change any other network setting.
9. The rows of section 12 that no round recorded (F15 and F16). Run the visual
   items of 3.20 step 5 for them: the dialog frames of Forget, Saved with Send to
   phone, About, Host details and App details, on the Streamer; and the Aurora
   loader, the PIN and "Paired", the error actions, the TV keyboard row and the
   phone host cards at their content size, on the Streamer and the S22.
10. Host cards and the phone bar gap.
    - Connect the pad and open Hosts. Expected: the layout is the handheld layout,
      with the rail, and the host cards follow their content: no card has empty
      space under its last line. Compare with the Streamer, where the cards of a
      row have equal height (at least 176 dp).
    - Switch the pad off and turn the phone to portrait. Open Apps, Settings,
      Privacy and diagnostics and the Audit trail. Expected: the back arrow, the
      title and the actions have the same 4 dp between them on all four. The title
      starts at the same distance from the arrow on each.
11. Fn 123. In a stream, open Keyboard from the in-game menu. Expected: the
    bottom row reads Ctrl, Win, Alt, Space, Tab and "Fn 123". Tap it. Expected: the
    digits 1 to 0, F1 to F10 and the arrows with Home, End, PgUp, PgDn and Del
    show, and "Fn 123" shows as on. Tap it again. Expected: the letters return. Type
    only into a test file on the PC (section 5).
12. A keyboard alone. Pair the AULA F75Max over Bluetooth and keep the pad off, in
    landscape.
    - Open the Library. Expected: the phone layout stays, with no rail. Connect and
      disconnect the keyboard: the layout does not change.
    - Clear logcat, start a stream and end it. Expected: no line `Creating
      controller context for device: AULA` with the tag `Borealis.UPSTREAM`.
    - Switch the pad on. Expected: the layout becomes the handheld layout, and a
      stream creates a controller context for the pad only.
13. The quit combo, in the order that Sprint 8 found. Turn Gamepad mouse on in
    Settings, Input, so that a long Start hold could toggle it. Start a stream
    with the pad, a system pad. Press Start, Select, LB and RB together.
    - Let go of LB and RB first, keep Start and Select held for more than a
      second, then let go of them. Expected: the stream ends once all four are
      let go. The menu does not open. Steam does not open on the PC. "Mouse
      emulation is: ON" does not show.
    - Repeat, and let go of Start and Select soon after the bumpers, one before
      the other. Expected: no Guide tap reaches the PC, so Steam does not open.
    - Repeat three more times with different orders of letting go. Expected: the
      same.
    - Hold Select and Start alone for one second. Expected: the menu still opens.
14. The Gamepad mouse speed slider. In Settings, Input, set Gamepad mouse speed
    to 10 %, start a stream, and move the pointer with the stick. Repeat at 200 %.
    Expected: the pointer is slow at 10 % and fast at 200 %. A change made during a
    stream does not change that stream (3.20 step 1).
15. The quit combo on a pad that the app's USB driver holds, on the final build.
    Plug the 8BitDo receiver into the S22, with ADB over Wi-Fi, and turn USB driver
    for Xbox controllers on in Settings, Gamepad. Press Start, Select, LB and RB
    together, then let all four go, once with the bumpers first. Expected: the
    stream ends, the menu does not open, Steam does not open, and the gamepad mouse
    does not turn on or off.
16. The battery saver. Turn the battery saver on and open the Library with a
    clip style chosen. Expected: the clip does not play and its still shows. Turn
    the saver off. Expected: the clip plays. With Aurora curtains, the saver stops
    the movement, and turning it off starts it again.
17. Evidence: for each item, the note above. A screenshot is taken only where it
    shows no address, host name, PIN or PC screen (section 5), and the logcat
    capture of 1.4. Take no capture during a stream, because it would show the PC
    screen.

### Re-check after the follow-ups

Run these on the final build, `5025614e`, and record the results in
[section 14](#14-results-sprint-9). Record each
item as `pass`, `fail` or `not applicable`, as in section 3.22.

1. S22 in landscape, with the pad or the receiver named in each item.
   - (a) With a pad on the USB driver (receiver in XInput mode, the USB driver on):
     open the in-game menu with Select and Start held. Down lands on Keyboard.
     Open it with A. Expected: the covering keyboard opens, and D-pad, A and B
     work. The row ends wrap. B closes it.
   - (b) With the receiver in its other mode (a system pad), switched on after the
     stream started: open the keyboard from the menu. Expected: the keyboard opens
     covering, and the pad navigates it.
   - (c) With the Bluetooth pad: Right on the last key of a letter row, of an Fn
     123 row and of the bottom row goes to the first key. Left on the first key
     goes to the last key. Expected: the same in each row.
   - (d) With the phone in portrait and a pad connected: the keyboard covers the
     stream. While the covering keyboard is open, touches do not reach the stream.
   - (e) The AULA F75Max alone, in landscape: the phone layout, as at `83a6aa66`.
2. Streamer.
   - (f) Settings, About, Open-source licences: Up from Close focuses the text and
     shows its outline. Down and Up scroll it. Down at the end returns to Close.
   - (g) atrace with Aurora curtains (the method of section 14): the work frames
     come about every 33 ms.
   - (h) Settings group switch: still one frame of about 50 to 60 ms.
3. End with `adb -s <serial-tv> shell input keyevent KEYCODE_SLEEP` on the
   Streamer.

### 3.23 Sprint 10 checks

Run these with the Sprint 10 build on the Google TV Streamer and on the S22,
against LuminalShine 26.10.0-beta.1. Sprint 10 added the glass, the Accessibility
group, the phone Settings sections, OK entering a pane, the rail footer, the quit
notice, the About fixes, the focus after Back from Apps and TalkBack support. The
Streamer shows 10 Settings groups, because it has no touchscreen and so no
On-screen controls group. The S22 shows 11, as sections in portrait and as groups
in landscape with a pad. For the rail, the S22 with a pad is the handheld.
Record each item as `pass`, `fail`, `owner check` or `not applicable`, with a short
note for each `fail`. Where a step names one device, run it there only.

Not runnable: API 28 to 30 (no device), where the halo and the glass with a tint
and no blur have been drawn in tests only. A visual item (the look of the glass,
the halo, the dot shapes, the thickness of an outline) needs a capture or the
owner's eyes. A capture is taken only where it shows no address, host name, PIN
or PC screen (section 5). If a capture is refused, mark the item `owner check`.

A debug build that was just installed runs slower until the JIT has warmed up.
Before the frame-time steps, open Settings and move through every group a few
times. After a fresh install, walk the groups about 12 times (some 200 moves)
before reading the group-move figures of step 2. The morning smoke of section 15
had a median of about 100 ms after two walks, and it settled near 65 ms only
after about 12.

**Frame-time method.** The steps that name a frame rate or a frame time use
`gfxinfo` on the debug package, `io.github.magalz.borealis.debug`:

1. Put the app on the screen to measure, and run
   `adb -s <serial> shell dumpsys gfxinfo io.github.magalz.borealis.debug reset`.
2. Leave the screen at rest for about 8 s, or press the keys of the move.
3. Run `adb -s <serial> shell dumpsys gfxinfo io.github.magalz.borealis.debug framestats`.
   Android keeps only the last 120 frames of a window, so for a sequence of moves
   read it every few moves (the round of section 15 read it every 3 moves, 1.1 s
   apart) and merge the readings by `IntendedVsync`.
4. Parse the output. Each window is a `Window:` line, then a `---PROFILEDATA---`
   line, a header of column names separated by commas, one row of integers (in
   nanoseconds) per frame, and a closing `---PROFILEDATA---` line. Keep the rows
   that have as many fields as the header and `Flags` equal to 0.
   - The duration of a frame is `FrameCompleted` minus `IntendedVsync`. Report its
     p50 (and the p90 and the maximum), in milliseconds.
   - The rate is the number of rows less one over the time from the first to the
     last `IntendedVsync`. On a 60 Hz output, 24 fps alternates frame intervals of
     33.3 ms and 50 ms, so the median interval flips between them: report the rate
     and the mean interval instead.
   - The longest frame after a key is the longest duration among the frames that
     start within 700 ms of a frame that handled input (`InputEventId` not 0; a
     cluster of such frames within 400 ms is one move).
5. The reference figures are in
   [ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md): on the
   Streamer with the glass at Standard, Hosts 34.8 ms and Settings 37.0 ms, against
   the budget of 41.7 ms at 24 fps. No script is committed; any parser that does
   the above will do.

Steps:

1. Glass on all seven styles, at each level (Streamer; the S22 for a quick look).
   - Set the background to each of Aurora curtains, Soft glow, Northern lights,
     Light mist, Flowing light, Starfield and Off, and for each look at Hosts and
     Settings with Transparency Standard, Reduced and Off, and with High
     contrast. Expected: the cards, the panels and the rail show the blurred
     background under a tint at Standard and Reduced. Under Off, under High
     contrast and on the plain page (the background Off) they are solid. The
     new panels (the Settings panes, the Privacy card, the Audit panel and the
     Privacy row) are solid `surface` there too.
   - Expected: the Glass tint and Glass blur rows are disabled with "High
     contrast is on in Accessibility", or "Transparency is set to Reduced in
     Accessibility" or "...to Off...".
   - Text is readable on every style. Record the style of any text that is not.
2. Frame time and the pace of the drawn background (Streamer).
   - Aurora curtains at Standard, on Hosts and on Settings at rest. Expected: 24
     fps, with a frame p50 under the budget of 41.7 ms (reference 34.8 and 37.0
     ms).
   - Set Transparency to Off, and then to Reduced. Expected: Off is 30 fps (no
     blur is drawn); Reduced blurs at 48 dp, so it is 24 fps.
   - A clip plays at its own rate, also with Transparency Off, so the 30 fps pace
     of Off applies to the drawn styles only (Aurora curtains and Soft glow). The
     clips of Northern lights and Light mist are 24 fps, and those of Flowing
     light and Starfield are 30 fps, so those two run at 30 fps at Standard too
     ([design/backgrounds](../../design/backgrounds/README.md)).
   - Move between Settings groups with Down and Up, 6 and 6, 1.1 s apart.
     Expected: the longest frame after a move is about the Sprint 9 figure of 47
     to 60 ms. Section 15 measured 64 ms at the median in the night round, and
     about 65 ms at Standard in the morning smoke, against 50 to 54 ms with
     Transparency Off.
3. Dialogs and sheets over the blurred window (Streamer, S22).
   - Open the options of a host (hold OK, or long-press). Expected: no scrim, the
     window behind it is blurred, the panel is in the glass tint, and the
     background is frozen: `gfxinfo` shows no frames while the sheet is open.
     Back closes it, the focus returns to the card, and the frames resume.
4. The Settings groups (Streamer, S22).
   - Streamer: 10 groups, Stream to About, all whole inside the window with no
     scrolling, and no On-screen controls group.
   - S22 in landscape with a pad: 11 groups. In portrait, 11 sections.
5. OK enters the pane (Streamer; the S22 with a pad).
   - On Video press OK, then Enter, then the A button of a gamepad. Expected: each
     moves the focus to the first row, Codec. Left returns to the group.
   - A touch tap on a group (S22) selects it and leaves the focus where it is.
   - Back on a row of the pane leaves Settings for Hosts. It does not return to the
     group. This is by design (`SettingsTvTest`: "Back leaves the screen from the
     rows and from the groups").
6. The rail footer (Streamer, S22 with a pad).
   - Expected: the logo, the name and the version at the bottom of the rail. It
     takes no focus: Down at the end of the rail stays on Settings. In a window too
     short for it, it is not shown. TalkBack reads one sentence, "Borealis, version
     `<version>`".
7. About (Streamer).
   - The Version row has no chevron, and OK on it starts nothing and logs nothing.
   - Open Privacy policy. Expected: it opens in a task of its own, and Back returns
     to Borealis where the browser lets Back leave. The Streamer has only the
     third-party Downloader app as a handler, which keeps Back: use Home and open
     Borealis again, which comes back on the About pane. This is a limit of that
     environment.
8. The licence text in a short window (S22 in split screen).
   - Open Settings, About, Open-source licences. Expected: the text stays above
     Close, and scrolls with the D-pad on a TV.
9. Back from Apps (Streamer, S22).
   - Streamer: open the apps of the second host and press Back. Expected: that card
     has the focus. Repeat after pairing a host, after a rotation of the S22 with a
     pad, after Cancel on Add host (it focuses the card that had the focus), and
     when the host was forgotten meanwhile (the first card).
   - S22, handheld: a tap on a card is enough to focus it on Back. On a phone in
     touch mode no card is focused.
10. Notices (Streamer, S22).
    - A snackbar is as wide as its text, centred, not full width.
    - Start Desktop and open the menu. Quit is the last row of the menu, which
      opens with the focus on "Back to stream": reach Quit with repeated DOWN, or
      select it by its label, and do not count on a fixed number of presses. Then
      OK. The confirmation opens with Cancel focused, so RIGHT and OK confirm.
      Expected: "Quitting Desktop…" shows, the stream ends, and the Library shows
      "App quit." once. It is shown once and not again. "Quitting `<app>`…" stays
      on screen until `/cancel` answers: about 17 s once in the night round of
      2026-10-21, and 5.5 to 6.7 s in the morning smoke (5 to 7 s on 26.09.1).
11. The phone sections (S22, portrait).
    - Settings opens with all 11 sections collapsed. Open two, go into Privacy and
      diagnostics and press Back. Expected: the same scroll position, and the two
      sections still open. Rotate the phone. Expected: they are still open.
12. The Accessibility settings, live (Streamer, S22).
    - Change each of Reduce motion, Transparency, Thicker focus outline, Text
      size, High contrast and Colour-blind palette in turn. Expected: the Library
      and Settings change at once. High contrast disables Transparency, Thicker
      focus outline, Glass tint and Glass blur with a note. Text size 130 % grows
      the text and the lists scroll or wrap, and no text runs out of its card.
      Reduce motion On stops the background (0 frames in 8 s).
    - Loose text over the background (titles, section labels, counts, hints) has the
      halo and stays readable. The icons on the background (the back arrow, the
      Hosts bar actions, the show-hidden toggle) have it too. The Address field of
      Add host is a glass field with its outline.
    - Set every setting back as it was.
13. The High contrast dim over a clip (Streamer).
    - Choose Northern lights, then turn High contrast on. Expected: the clip keeps
      playing, dimmed, and the text over it is readable.
14. TalkBack (S22 and Streamer; the owner).
    - Headings: the titles of Hosts, Apps, Add host, Pairing, the Audit trail and
      Settings, and the section labels, are read as headings and TalkBack's heading
      navigation reaches them.
    - A sheet, a dialog, the in-game menu and the keyboard say what they are when
      they open; the options of a host say its name.
    - A host card is read as one sentence, with its status and "Running Desktop"
      when an app runs. A pairing step is read as one thing. A wrong PIN is
      announced when it appears.
    - The two Off segments of the menu are told apart by their row.
    - Large text: set the system font size to the largest that the device offers
      (the tests use a scale of 2.0). Expected: no text of the Library, Settings,
      Privacy or the Audit trail is cut off.
    - Touch targets on the S22 are at least 48 dp. The keys of the keyboard on the
      phone are an accepted exception.
    - The segments and the keys are still lifted when focused, so the order in which
      TalkBack goes through a row of them may differ from the drawn order. Record
      what it does.
15. LuminalShine 26.10.0-beta.1 (Streamer, S22).
    - Pairing: a new device pairs, with a wrong PIN once. Expected: the new request
      identities and the exact 4-digit PIN check do not change what the user sees.
    - A second device during a launch (#203): start an app on the Streamer, and
      open the same host on the S22 while the launch is setting up. Expected: the
      S22 lists the apps, and neither device logs a retry, the 3 s wait before
      `/resume` or a 503. Record whether the `/serverinfo` retry (3 attempts in
      about 25 s) and the 3 s wait still trigger on this host.
    - Teardown and reconnect (#198, #204): end a stream with Disconnect and with
      Quit, and start again at once. Record any hang, and whether the host process
      stays up.
    - Forget a host that is paired. Expected: Forget completes, and the client still
      handles the host's answer to `/unpair` for an unknown path (according to its
      release notes, #200 makes the host send one framed response; 26.09.1 sent
      stray bytes after it).
    - Host details: unchanged (Status, Paired, `Host software: Sunshine-compatible,
      version 7.1.431.-1`, the codecs), with no error because of
      `LuminalShineVersion`.
16. End with `adb -s <serial-tv> shell input keyevent KEYCODE_SLEEP` on the
    Streamer.

### 3.24 Sprint 11 checks

Run these with the Sprint 11 build on the Google TV Streamer and on the S22. Sprint 11
changed the TV text size, the fonts, the accent colour, the Online dot, the background
intensity, the About links on a TV, the text spacing, the card density and the Sprint 10
follow-ups. Steps 8 and 9 cover the text spacing and the card density.
Record each item as `pass`, `fail`, `owner check` or `not applicable`, with a short
note for each `fail`. Where a step names one device, run it there only.

Not runnable: API 28 to 30 (no device). A visual item (the look of a font, the accent,
the dot shadow, the dimming of the background) needs a capture or the owner's eyes. A
capture is taken only where it shows no address, host name, PIN or PC screen (section
5). If a capture is refused, mark the item `owner check`. The frame-time method of
section 3.23 applies to any step that names a frame rate.

Steps:

1. TV text size (Streamer).
   - Expected: on the TV, body text is 16 sp and the smallest text is 13 sp. Hosts,
     Settings and the rail read at those sizes.
   - Settings on the TV: the list scrolls with the focus where the groups do not fit
     the window, and the focused group stays in view. Down from Stream to About moves
     through every group.
   - Handheld (S22 in landscape with a pad): the text keeps the handheld sizes, 15 sp
     for body text and 12 sp for the smallest. Owner check.
2. Font choice (Streamer, S22).
   - Settings, Appearance, Font. Expected: seven choices, IBM Plex Sans (the default),
     Atkinson Hyperlegible Next, Luciole, Lexend, Andika, B612 and the system font.
     Each has a purpose line under its name, and each name is drawn in its own font.
   - Choose each font in turn. Expected: the Library and Settings change font at once,
     with no restart, and no text is cut or ends in an ellipsis.
   - The codes use the font's monospaced companion (Atkinson Hyperlegible Mono,
     B612 Mono, or IBM Plex Mono for the others): the PIN on Pairing, the host
     addresses, the chips, the performance overlay and the adb command. Owner check.
   - The on-screen keyboard (S22, in a stream): the keys use the text font, and the key
     shortcut chips use the codes font.
   - Open About, Open-source licences. Expected: the licence of every font is listed,
     and NOTICE credits them.
   - Set the font back to IBM Plex Sans.
3. Accent colour (Streamer, S22).
   - Settings, Appearance, Accent colour. Expected: Violet (the default), Teal and
     White. Choose Teal, then White, then Violet.
   - Expected: the selected labels (segment, chip, rail item, Settings group, choice
     sheet), the text button label, the current step number, the latched key label and
     the focus outline follow the accent. The drawn backgrounds keep the violet.
   - White: the selected item has a 3 dp bar at its start edge (left in left-to-right
     layouts, right in right-to-left). Owner check.
   - Where a snackbar with an action shows, its action is in `accent-inverse`. Owner
     check.
   - Set the accent back to Violet.
4. The Online dot shadow (Streamer, S22).
   - Hosts with an online host: the dot has a soft dark shadow round the dot and on
     the card glass. On the S22 it is in the page colour and shows on the glass, and
     not on the Apps title, where the dot sits on the page. On the Streamer it is
     black and a little wider (10.75 dp from the centre of the dot), because the TV
     glass is darker, and it shows faintly on the Apps title over the page. Expected
     on the Streamer: a dark ring about as clear as the S22's, that ends before the
     "Online" text. The shadow does not move. The owner confirms the look by eye.
   - Set High contrast on. Expected: the shadow is not drawn. Set it off again. Owner
     check.
   - Known limitation to note, not a fail: the Apps title dot on the animated background
     has no glass, and its 3:1 there is not measured.
5. Background intensity (Streamer, S22).
   - Settings, Appearance, Background intensity: 100, 75, 50 and 25 %. Expected: the
     background darkens at each step, at once, with no restart.
   - At 25 %, text on the background and on the glass is readable. Owner check.
   - With High contrast on, the steps change nothing: its dim of 0.85 is stronger than
     every veil.
   - Set the intensity back to 100 %.
6. About links on a TV (Streamer).
   - About, then a link (Source code or Privacy policy). Expected: a dialog opens with
     the URL and a QR code. No browser opens on the TV.
   - Read the QR code with a phone camera (S22). Expected: it opens the same URL.
   - Back closes the dialog, and the focus returns to the link.
   - Owner check for the size and the quiet zone of the code.
7. Sprint 10 follow-ups (Streamer, S22).
   - The ATF check of Hosts with a snackbar is in the test suite. It has no device step.
   - The rail footer with the version: section 3.23, step 6.
   - The repeated paths in `design/icon/generate.py` are build tooling. They have no
     device step.
8. Text spacing (Streamer, S22).
   - Settings, Accessibility, Text spacing: Normal, Wide and Wider. Choose Wide, then
     Wider. Expected: the words of Hosts, Settings and Privacy sit further apart than
     their letters, and the chips, the step numbers, the performance overlay, the rail
     version and the font picker preview take the spacing too. No text is cut or ends in
     an ellipsis. Owner check.
   - Expected at Wider: a value row of Settings stacks its value under the text when the
     longest word does not fit, and a slider value takes at most 40 % of its row.
   - Stream on a narrow phone at Wider, at the largest text: the keyboard's chips go under
     the shortcuts when they would leave less than a quarter of the row, and the
     performance overlay scales down to the room it has. Both stay readable. Owner check.
   - Expected: the text fields show no word spacing while typing; their labels do.
   - Set Text spacing back to Normal.
9. Card density (Streamer, S22).
   - Settings, Appearance, Card density: Comfortable, Compact and Dense. Expected: Hosts
     and Apps show one column more at Compact and at Dense: 4 hosts and 6 apps on the
     TV, against 3 and 5 at Comfortable.
   - Dense: the card padding is smaller, the Continue cover on Hosts is 44 dp, and the
     cards of a row on the TV have the same height. The gutter of the screen is the same.
     Owner check.
   - Change the density with a card focused. Expected: the screen re-lays out at once and
     the focus stays on the same card.
   - S22 at the largest text in Dense: the host detail line wraps, and a Running badge
     with no room for its label shows its dot alone. Known limitation, not a fail: an app
     name under a cover stays one line with an ellipsis, so a long name is cut at the
     largest text on a 4-column phone.
   - Set Card density back to Comfortable.
10. End with `adb -s <serial-tv> shell input keyevent KEYCODE_SLEEP` on the Streamer.

### 3.25 Sprint 12 checks

Run these on the Google TV Streamer, with the owner's OK, and on the S22. Sprint 12
converted the inherited Java to Kotlin for the network, host, support and stream
connection code. A conversion keeps behaviour (ADR 0059), so the steps check that
the behaviour kept. Record each item as `pass`, `fail`, `owner check` or `not applicable`, with a
short note for each `fail`.

Not runnable: nothing in this section needs a device that is not listed. Add host
manual entry and the pairing and Wake-on-LAN steps need the owner, because they need
the host and its PIN. Do not record a host name, an address, a MAC address or a PIN.

Steps:

1. Upgrade in place (Streamer, S22). Do this first, and it matters most.
   - Install the `main` build, with data. Pair one host and pin one shortcut, and
     note the settings. Install the phase build over it without clearing the data.
   - Expected: the saved hosts are still there and still paired, with no PIN asked.
   - Expected: the client id is the same, as seen in Diagnostics or in the pairing
     state on the host.
   - Expected: the settings (resolution, bitrate, frame pacing and the others) are
     unchanged.
   - Expected: the pinned shortcuts and the TV channel entries still start the right
     game. The Google TV Streamer shows no app channels (section 11), so on it the
     channel entries do not apply, and only the posters are checked, through the
     provider (3.21, step 7).
2. Discovery (Streamer, S22).
   - Expected: mDNS finds the host on the local network.
   - Add host manual entry: owner check.
3. App list and box art (Streamer, S22).
   - Expected: the app list of a paired host loads, and the box art shows for each app.
     Until Phase 3 the Apps grid shows each app's initials instead (owner, 2026-10-24).
4. Short stream (Streamer, S22).
   - Launch an app, then Resume it, then Quit. Expected: each step works, with no error
     dialog.
   - Expected: the performance overlay shows the chosen resolution, fps and bitrate.
   - Expected: input works in the stream from a keyboard, from a pad and from touch.
   - To check audio, play a sound on the PC during the stream: the host sends no audio
     while the PC is silent.
5. Logs (Streamer, S22).
   - Expected: the output of `LimeLog` still reaches `BorealisLog`, as seen in the
     logcat capture of section 1.4.
   - Expected: the log has no PIN, token, key, certificate, address or host name.
     Owner, 2026-10-24: local addresses and host names may appear; public and overlay
     addresses may not, and are masked from Sprint 16.
6. Owner UAT (owner).
   - Pairing, including a wrong PIN on `26.10.0-beta.1`.
   - Forget, then pair again.
   - Wake-on-LAN.
7. End with `adb -s <serial-tv> shell input keyevent KEYCODE_SLEEP` on the Streamer.

### 3.26 Sprint 13 checks

Run these on the Samsung Galaxy S22 and on the Google TV Streamer, with the owner's OK for
the TV. Sprint 13 converted the inherited input code to Kotlin: the keyboard and mouse
capture, the touch contexts, the USB drivers, the on-screen controller and
`ControllerHandler` (ADR 0059). A conversion keeps behaviour, so the steps check that the
behaviour kept. Record each item as `pass`, `fail`, `owner check` or `not applicable`, with
a short note for each `fail`.

The physical actions need the owner: the pads, the keyboard and the touch. Do not record a
host name, an address, a MAC address or a PIN.

Two corrections from the round are applied below:

- The touch mode defaults to the trackpad, because the setting is on by default.
- The trackpad drag is a press and hold of about 650 ms without moving, then a move
  (`DRAG_TIME_THRESHOLD` in `RelativeTouchContext.kt`). It is not a double tap and hold.

Steps:

1. Update in place (S22, TV).
   - Install the phase build over the Sprint 12 build, with the data kept.
   - Expected: the Library opens, and the startup log reads `commit=` with the revision
     of the build.
2. Touch, absolute (direct) mode (S22).
   - Set Touch to direct in Settings, not in the stream menu. A choice in the stream menu
     lasts for that session only. Leave the setting, then start a new stream.
   - Expected: tap is a left click, long press is a right click, two-finger scroll works,
     and a drag moves the pointer.
3. Touch, trackpad mode (S22).
   - This is the default. Expected: tap, two-finger scroll and drag work. A drag is a
     press and hold without moving for about 650 ms, then a move. Check the pointer speed.
4. On-screen controller (S22).
   - Expected: shown in the stream. A, B, X, Y (the sign-extension case: the host sees Y),
     the D-pad in 8 directions, both sticks (move, centre, click), LT and RT, LB and RB,
     Start, Select and Guide all work. A long hold (3 s) on a button works.
5. On-screen controller edit (S22).
   - Move and resize one element, leave, and reopen the stream. Expected: the layout is
     kept. The opacity setting changes the look.
6. Keyboard and mouse (S22, if available).
   - Expected: typing in a host text field works, and so do Esc, the arrows and the
     modifiers. Mouse capture, the buttons, the wheel and the release with the menu work.
7. 8BitDo Pro 3 (Switch mode, `057E:2009`), on the S22 and the TV.
   - Expected: every button, both sticks, the triggers and the D-pad work. Rumble works
     (a host test, for example Steam's controller test). Motion works if the host shows
     it. The arrival, with its controller type, is in the host log if it is visible.
8. 8BitDo Ultimate 2 (`2DC8:6012` in D-input over BLE on the S22; `2DC8:310B` through the
   receiver on the TV, through the USB driver, with the permission held until the first
   frame).
   - Expected: buttons, sticks, triggers, Guide and rumble work.
9. Gamepad mouse (S22).
   - Expected: toggle it from the menu row. Check the pointer speed, the RB precision and
     right-stick scroll. A pad that joins while it is on joins silently.
10. Menu chord (Select and Start held for 1 s), on the S22.
    - Expected: the menu opens, the capture is released and restored, and the quit combo
      works.
11. Two pads at once (S22, TV).
    - Expected: the two controllers are numbers 0 and 1 on the host. Unplug one during the
      stream. Expected: the other keeps working.
12. Connect a pad during the stream, then disconnect and reconnect it (S22, TV).
13. Logs (S22, TV).
    - Watch the app's logcat for exceptions from `com.limelight.binding.input`. Keep the
      captures masked, as in [section 17](#17-results-sprint-12).
14. End (TV, S22).
    - Quit the app on the S22. Then end with `adb -s <serial-tv> shell input keyevent KEYCODE_SLEEP`
      on the Streamer.

## 4. Results templates

Copy one table per device. Use `pass`, `fail` or `not applicable`, and add a
short note for each `fail`. The tables list sections 2 to 3.12. Add a row for
every scenario you ran, such as 3.13 to 3.23 (one row per item for the sprint
checks 3.19 to 3.23).

### 4.1 Samsung Galaxy S22 (USB or wireless ADB)

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
[upstream-sync.md](upstream-sync.md) after the run. The Moonlight Android value
is that of the existing row of that table. The moonlight-common-c value is the
vendored source commit, as the table of that file asks from 2026-10-15: the
revision that [core/third_party/README.md](../../core/third_party/README.md)
records for `moonlight-common-c/` (`591bddf` at this revision), not the fork
tag.

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| `<commit tested>` | 12.2 | `<vendored source commit>` | 26.09.1 (and 26.10.0-beta.1 where the Sprint 10 round of section 15 ran on it) | Samsung Galaxy S22 (USB or wireless ADB) and Google TV Streamer (ADB over Wi-Fi) on `<date>`; instrumented tests `<pass/fail>`; scenarios `<n pass, n fail, n not applicable>`; see the results of this runbook. |

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
5. A capture must not keep the PC screen. Capture only the overlay or the menu
   of the app, crop or shrink the image so that nothing on the PC is readable,
   and delete it after you have read it. Take no screenshot of a playing stream:
   the logcat lines and the notes are the evidence.
6. Type only into a test file on the PC, for example a Notepad file, so that a
   test never sends keys to another window.

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
  Fixed in Sprint 7 (merge `0ad99565`); see [section 12](#12-results-sprint-7).
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

## 12. Results: Sprint 7

This section holds the Sprint 7 results: the setup, the USB spike, the decode
check made in the middle of the sprint, the final round, and the Bluetooth pad
round with the handheld fix. The checks follow the steps of
[section 3.20](#320-sprint-7-checks).

### Setup

- Builds:
  - `7cbc60ea`: the decode check, with verbose logging on;
  - `903e399d`: the quit combo, checked by the owner;
  - `60d5bfd5`: the decode fix, the held OK on an app tile, and the S22 host
    details;
  - `fe6273c7`: the final round, for the visual checks and the pointer. The round
    notes record the build gate and the Roborazzi verify as green on this build.
  - `4726c010` (the phase head, with the host card and the TV USB merges): the
    final checks of the host card and the USB dialog.
  - `975bee9f`: the Bluetooth pad probe, before the handheld fix.
  - `dd3d383f`: the handheld fix for a connected game controller, merged as
    `89b46c5b` (same tree). Checked on the S22 in landscape.
- Devices:
  - Google TV Streamer: ADB over Wi-Fi, at an address that changes with DHCP.
  - Galaxy S22: USB.
- Host: LuminalShine 26.09.1 on the PC, the same version throughout. The owner
  did not update it during the sprint.
- Pads:
  - 8BitDo Pro 3, a system pad: the first pointer round.
  - 8BitDo Ultimate 2 for PC, XInput on the app's USB driver: the second pointer
    round, and the quit combo.
  - 8BitDo Ultimate 2 over Bluetooth LE, and 8BitDo Pro 3 over classic
    Bluetooth, both as system pads on the S22: the Bluetooth pad round.
- Screen captures: each capture was read and then deleted. Some showed part of
  the PC screen. The host details capture showed host addresses and an ID. No
  capture is kept in the repository.

### Spike: scrolling and the USB dialog (R5a), 2026-10-17

- Scrolling: right-stick scrolling works on both pads in a PC app that scrolls.
  The owner found it good. The earlier report that scrolling did not work was
  not a defect. The high-resolution scroll deltas are kept, and the speed
  setting scales them.
- USB identity: the 8BitDo Ultimate 2 receiver changes its USB identity by
  itself. It shows as `2dc8:6013` while idle, and as `2dc8:310b` (XInput) when a
  pad connects. Each change is a new attachment with a new device number. The
  Pro 3 receiver does the same.
- Dialogs: each attachment shows Android's attach dialog while the USB driver is
  on. At 12:06 the owner declined it, because accepting looked as if it would
  close the current app. The stream start then showed the USB permission dialog,
  which the owner accepted. The grant lasts one attachment.
- Double dialog during a stream: a pad that woke during a stream showed two
  dialogs. At 17:08:06.588 the Ultimate 2 changed from `2dc8:6013` to
  `2dc8:310b`. At 17:08:06.673 the attach dialog (`TvUsbConfirmActivity`) came
  up. At 17:08:07.652, one second later, the USB driver service asked for
  permission (`TvUsbPermissionActivity`). The build was `fe6273c7`.
- Fix: merge `0ad99565` (issue #16). On a TV the attach activity is not offered,
  because `UsbAttachGate` also requires a device that is not a leanback device.
  The USB permission dialog is held until the stream shows its first frame. A
  pad that wakes during the stream asks at once, so it shows one dialog. Phones
  are unchanged.
- Stream end: the hypothesis that ending the stream makes the receiver
  re-enumerate is not confirmed in this run. No attach or remove line appeared
  during the stream, at its end, or in the 30 seconds after it.
- The follow-up is closed by the fix above. See the
  [roadmap](../roadmap.md#sprint-7-follow-ups-and-backlog) (Sprint 7, item 3).

### Decode time check, 2026-10-17

Before the fix (build `7cbc60ea`, verbose logging on):

| Device | Decoder | Input and output | Result |
| --- | --- | --- | --- |
| Google TV Streamer | `c2.mtk.hevc.decoder.lowlatency`, 720p | Every window: in = out = exact. Truncated, zero, ahead, older and unmatched are all 0. | Pairing works. The overlay still showed "Decode —". |
| Galaxy S22 | `c2.qti.hevc.decoder.low_latency` | 353 in, 353 exact, nothing else | Pairing works. Only the measure needs fixing. |

Finding: the pairing of inputs and outputs is correct on both decoders. The dash
comes from the measure. The delta is computed in whole milliseconds from a
timestamp based on the first frame's submit time (`baseTimestampUs`). A decode
under one millisecond, or a base that runs slightly ahead, gives a delta of 0 or
less. That leaves the decode time at 0, and the overlay shows "—". This closes
the MediaTek follow-up of [section 11](#11-results-sprint-6) (D5).

Fix: merge `967487ea` (issue #14). Each input keeps its queue time in
nanoseconds. The decode time is the output time less the queue time, in
nanoseconds, and the fraction reaches the overlay in microseconds.

After the fix (build `60d5bfd5`):

| Device | Decoder | Input and output | Result |
| --- | --- | --- | --- |
| Google TV Streamer | MediaTek decoder, 720p | Every window exact. `decode_avg_us` is about 4200 in steady state. The first window is about 25 ms, at the stream start. | Pass. The overlay shows "Decode 4.2 ms". The decode is about 4 ms, not under 1 ms. |
| Galaxy S22 | `c2.qti.hevc.decoder.low_latency`, 720p | Every window exact. `decode_avg` 4.7 to 5.4 ms. | Pass. The overlay shows "Decode 5.2 ms". The old measure showed "0.2 ms", an underestimate from the same timestamp base. |

The overlay corners were captured, read and deleted. The S22 capture showed part
of the PC screen.

### Final round, builds `fe6273c7` and `4726c010`

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| F1 | Hosts rail at 220 dp, with the Settings gear | TV | pass | Build `fe6273c7`. |
| F2 | Host cards of equal height | TV | pass | Build `fe6273c7`. The card's action button was removed in `4726c010`; see F17. |
| F3 | Settings in two panes, with the chevrons from BorealisIcons | TV | pass | Build `fe6273c7`. |
| F4 | Erase audit trail: Compact dialog, Cancel focused, Erase in the danger tone | TV | pass | Build `fe6273c7`. Back cancelled it, and nothing was erased. |
| F5 | Host details as the Standard dialog. The scrim dims the app content. | S22, landscape | pass | Build `60d5bfd5`. The system bar icons stay bright, as with any app dim. No undimmed strip was seen. |
| F6 | Gamepad mouse at 50 %: smooth from the first move | TV, Pro 3, then Ultimate 2 | pass | Build `fe6273c7`. The owner rated the default speed great on the first pad, and the second pad was "perfect" too. |
| F7 | RB precision | TV, both pads | pass | Build `fe6273c7`. The owner rated it perfect on the first pad, and the second pad was perfect too. |
| F8 | Pointer speed row in the menu: Slow, Normal, Fast | TV, Pro 3 | pass | Build `fe6273c7`. All three speeds were good to the owner. The notes do not record this row for the second pad. |
| F9 | Right-stick scrolling | TV, both pads | pass | Build `fe6273c7`. |
| F10 | Held OK in the open in-game menu, then released | TV, remote | pass | Build `fe6273c7`. Nothing ran on the release. A fresh press worked. |
| F11 | Held OK on the Desktop tile in Library, Apps | TV, remote | pass | Build `60d5bfd5`. The app actions sheet stayed open, and nothing ran on the release. A fresh press picked an option. |
| F12 | Quit combo (Start, Select, LB, RB) on the USB-driver pad | TV, Ultimate 2 | pass | Build `903e399d`, checked by the owner. The stream ended. Not repeated on `fe6273c7`. |
| F13 | Quit combo on the system pad | TV, Pro 3 | not run in Sprint 7 | The system-pad path did not change; Sprint 6 (§11, C3) covered it. |
| F14 | Movement just past the deadzone and a slow diagonal; the slider at 10 % and 200 % | TV, both pads | pass, slider extremes not run | The owner moved the stick lightly, diagonally and fully with both pads: "Tudo 10". The slider was left at its 50 % default; only the menu row's three speeds were tried. |
| F15 | Dialog frames for Forget, Saved with Send to phone, About, Host details and App details | TV | not recorded | Only F4 and the S22 check of F5 are in the notes. |
| F16 | Aurora loader, PIN and "Paired", connection error actions, TV keyboard row, phone host cards at their content size | TV, S22 | not recorded | Not in the round notes. |
| F17 | Host card is one focus stop: OK opens the app list, holding OK opens only the options | TV | pass | Build `4726c010`, checked by the owner. The card has no Open button to hold. |
| F18 | Ultimate 2 off and on in the Library: no USB dialog | TV | pass | Build `4726c010`, checked by the owner. `UsbAttachActivity` is disabled on the TV. |
| F19 | Stream start: the USB permission dialog appears only once the picture shows | TV | pass | Build `4726c010`, checked by the owner. |
| F20 | Pad off and on during the stream: one dialog (permission). The pad works after accepting. | TV | pass | Build `4726c010`, checked by the owner. |

### Bluetooth pad round and handheld fix, 2026-10-17

Before the fix, on the S22 in landscape (`w780dp`, build `975bee9f`), each pad
was probed with `dumpsys input` (the device sources), `am get-config` (the
configuration's touch and navigation) and a capture of the Library for each
mode. Each capture was read and then deleted, because it showed host addresses.

| Pad and mode | Link | ID | Android sources | Configuration | Layout |
| --- | --- | --- | --- | --- | --- |
| 8BitDo Ultimate 2, Start+A, B, X and Y | Bluetooth LE (HID over GATT) | `2dc8:6012` every time | KEYBOARD, GAMEPAD, JOYSTICK | finger, nonav | Phone |
| 8BitDo Pro 3, switch on D and on S | Classic Bluetooth | `057e:2009` (Nintendo Switch Pro Controller), class EXTERNAL | KEYBOARD, GAMEPAD, JOYSTICK | finger, nonav | Phone |

- On the S22, the Ultimate 2 reconnected after each combo with the same
  identity. Its mode combos change nothing over Bluetooth there.
- The Pro 3's kernel device has D-pad keys (`BTN_DPAD_*`), but Android's key
  layout for it (`Vendor_057e_Product_2009.kl`) gives it no DPAD source.
- Focus navigation with both pads worked: the hat D-pad moved the focus. Only
  the form factor missed them.

Cause: the form factor read only the configuration. Android sets
`navigation = dpad` only for a device with the DPAD source, so a pad whose
D-pad is a hat axis never sets it. The owner decided to fix this in Sprint 7.

Fix (merge `89b46c5b`, task commits `748f688c`, the test first, and `dd3d383f`):

- A window 600 dp or wider is a handheld while a game controller is connected.
  The controller counts when it is not virtual and has the GAMEPAD source, or
  the JOYSTICK source with X and Y axes.
- The layout goes back to the phone layout when the controller disconnects.
- At the stream start, the docked keyboard follows the same rule
  (`StreamChromeHost.isPhone`; since Sprint 9, `StreamChromeHost.dockedKeyboard`,
  decided when the keyboard opens).
- Limitation: a pad claimed by the app's USB driver is no Android input device
  while it is claimed, so the form factor cannot see it.

After the fix, on build `dd3d383f` (same tree as merge `89b46c5b`), checked with
the owner on the S22 in landscape. The steps are in
[section 3.20, step 7](#320-sprint-7-checks).

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| F21 | The Library switches to the handheld layout when the pad connects, without reopening the app | S22, landscape, Pro 3 | pass | Build `dd3d383f`. Rail with Hosts, Add host and Settings, and host cards of equal height. |
| F22 | The Library returns to the phone layout when the pad disconnects | S22, landscape, Pro 3 | pass | Build `dd3d383f`. |
| F23 | Stream start with the pad only: a host card, then Desktop | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. The notes do not record which of the two pads was used. |
| F24 | Menu opens by holding Select and Start | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. |
| F25 | Pointer speed row: Slow, Normal, Fast | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. Shows with Gamepad mouse on. |
| F26 | Pointer and RB precision | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. |
| F27 | Right-stick scrolling | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. |
| F28 | Keyboard shows over the video, not docked under it | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. |
| F29 | Quit combo (Start, Select, LB, RB) on a Bluetooth pad, a system pad | S22, landscape, Bluetooth pad | pass | Build `dd3d383f`. The stream ended. The notes do not record which pad was used. |

The notes do not record rows F21 and F22 for the Ultimate 2. No capture was
taken during the stream, because it would show the PC screen.

### Not run

- Handheld: no dedicated handheld device. The S22 in landscape was checked as a
  handheld with a Bluetooth pad, in rows F21 to F29 above.
- Send to phone, the Expired and No network states: only unit and screenshot
  tests cover them.
- Items F13 to F16 and the other items of [section 3.20](#320-sprint-7-checks)
  that are not in the table above. The round notes do not record them.

### Follow-ups and backlog

- Sprint 7 item 3, the USB dialog, is fixed in this sprint (merge `0ad99565`).
  See the spike above.
- Backlog, from this round: the native pad modes, the smoother D-pad moves in
  Settings on a TV, the quit warning for Desktop, and the host details text. See
  the [roadmap, Sprint 9](../roadmap.md#sprint-9-ux-polish-and-the-handheld). The
  native pad modes are documented in Sprint 17, not in Sprint 13
  ([roadmap, Sprint 17](../roadmap.md#sprint-17-release-preparation)).
- The Sprint 6 quit combo follow-up for USB-driver pads is done in `5416a79c`.
  See the [roadmap](../roadmap.md#sprint-7-follow-ups-and-backlog), item 2.
- Backlog, from the Bluetooth pad round: the handheld host card height. On the
  S22 as a handheld (360 dp tall), the equal host card height (176 dp, from the
  TV token) leaves a lot of empty space. The owner wants the card adapted for a
  handheld in a later refinement session. See the
  [roadmap, Sprint 9](../roadmap.md#sprint-9-ux-polish-and-the-handheld).

## 13. Results: Sprint 8

This section holds the Sprint 8 results. The checks follow the steps of
[section 3.21](#321-sprint-8-checks).

### Setup

- Build: the phase debug APK built from `50854eda`. The later phase commits
  change only docs and the Memtrace ignore file. It was installed over the
  previous build with `adb install -r`, without uninstalling.
- Host: LuminalShine 26.09.1.
- Devices: the Google TV Streamer over wireless ADB, and the Galaxy S22 over
  wireless ADB in part 1b and over USB in part 3.
- Rounds, all on 2026-10-18:
  - **Part 1:** the TV, without a controller, while the owner was away. Only
    steps that send nothing to the PC's foreground app.
  - **Part 1b:** the S22, without a controller.
  - **Part 2:** the TV, with the owner, for the pads, the mouse and typing.
  - **Part 3:** the S22, with the owner, for a Bluetooth pad, the mouse and a
    Bluetooth keyboard.
- Evidence: logcat and `dumpsys input` over ADB. Captures were cropped to the
  menu or overlay, or reduced to unreadable thumbnails, so the PC screen was not
  kept. All captures were deleted after use.

### Results

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| 1 | Library | TV, S22 | pass | Rail, two host cards and the online host on the TV. The phone layout on the S22, where Apps uses the shared phone bar (back, title, eye). |
| 2 | Start and end a stream | TV, S22 | pass | Desktop started on the TV (first video packet after 1.4 s) and on the S22. Quit Desktop from the menu: `/cancel` 200, back to the Library with "Nothing running". |
| 3 | System pad and USB-driver pad: quit combo | TV, S22 | pass | TV: the 8BitDo Ultimate 2 in four set-ups, below. The combo ended the stream in every set-up where the pad works. S22: the Ultimate 2 over Bluetooth, twice; the combo ended the stream both times. Once, Steam opened on the PC as the stream ended (see the follow-ups). The S22 has no USB-driver case, because its port held the ADB cable. |
| 4 | Mouse capture | TV, S22 | pass | Logi M650 L over Bluetooth. During the stream `dumpsys input` showed "Pointer Capture: Enabled" for the stream window, and the owner moved and clicked on the PC. The S22 released the capture while the menu was open and took it back after. After the stream, on both devices: "Pointer Capture: Disabled", no window holding it, and the mouse worked in the Library. |
| 5 | On-screen keyboard | TV, S22 | pass | It opens from the menu on both devices; docked under the stream on the phone. The owner typed on both devices and the keys reached the PC. The digits are on the Fn layer of the keyboard and the owner did not find them, so they are hard to discover (see the follow-ups). |
| 6 | Decode overlay | TV, S22 | pass | "Decode 4.5 ms" on the TV (`c2.mtk.hevc.decoder.lowlatency`) and "Decode 5.5 ms" on the S22 (`c2.qti.hevc.decoder.low_latency`). Log windows with `unmatched=0`. The overlay was set back to Off. |
| 7 | TV channel and box art | TV | pass | Checked through the provider. `content read` on `content://poster.io.github.magalz.borealis.debug/boxart/<host>/<app>` returned both cached PNGs, byte for byte equal to the cache files. These requests failed with `FileNotFoundException` and returned 0 bytes: the app IDs `abc`, `-1` and `..%2F..%2Fshared_prefs`, a malformed host ID, a missing app and an extra path segment. The Google TV home screen was not checked. The Apps screen shows initials until Phase 3, as designed. |
| 8 | Decoder crash notice has no Help button | — | not applicable | It cannot be triggered safely; `UpstreamSeamsTest` covers it. |
| 9 | Debug APK: same ID and label | TV, S22 | pass | Installed over the previous build on both devices: same package, label and version (`0.0.0-dev`). |

No crash or ANR appeared in logcat in any part.

### Item 3: the pad set-ups

| Case | Link and mode | USB driver | Android sees | Result |
| --- | --- | --- | --- | --- |
| A | Receiver, XInput (`2dc8:310b`) | on | Nothing. The USB driver holds the pad. | pass. The combo ended the stream from the client; Desktop kept running. |
| B | Receiver, XInput (`2dc8:310b`) | off | Only the receiver's HID "Keyboard" and "Mouse" (KEYBOARD, DPAD, STYLUS, JOYSTICK; no GAMEPAD). | Not applicable, as expected. The pad does nothing in the stream, because Android on the Streamer has no driver for this XInput device. |
| C | Receiver, the other mode (`2dc8:6012`) | on | A system pad (GAMEPAD, JOYSTICK). The USB driver leaves it alone. | pass. |
| D | Bluetooth LE ("8BitDo Ultimate 2 Wireless"), receiver idle (`2dc8:6013`) | on | A system pad (GAMEPAD, JOYSTICK, battery). | pass. |

- The USB permission prompt appeared once, after the first frame, and the owner
  accepted it. The next stream on the same attachment did not ask again: the
  grant lasts one attachment, as in Sprint 7.
- Each combo exit logged a harmless `Duplicate finish request` for the stream
  activity in `ActivityTaskManager`.
- A Bluetooth keyboard (AULA F75Max-2) moves the Library focus with its arrow
  keys.

### Follow-ups

- **Digits on the Borealis keyboard are hard to discover.** The owner did not
  find them in the round. They are behind the Fn key, on the Fn layer: 1 to 0,
  F1 to F10, the arrows, and Home, End, PgUp, PgDn and Del (`KeyboardLayout`,
  `functionRows`; the layer exists since Sprint 5). The problem is
  discoverability, not a missing feature. Added to Sprint 9.
- **Keyboards that count as game controllers.** The AULA F75Max keyboard
  reports a JOYSTICK source with X and Y axes (on the S22, through a "System
  Multi Axis" part; it also uses Apple's vendor ID, `05ac:024f`). The Sprint 7
  controller rule (GAMEPAD, or JOYSTICK with X and Y) therefore counts it as a
  game controller. Confirmed on the S22 in landscape: with only the keyboard
  connected, the Library switches to the handheld layout, and it switches back
  when the keyboard disconnects. In the stream, the inherited controller code
  created a controller context for the keyboard on each device change, but gave
  it no controller number, so the PC saw no extra pad. Added to Sprint 9.
- **Steam opened after a quit combo.** On the S22, once in two tries, Steam
  opened on the PC as the combo ended the stream. The likely cause is the menu
  chord of Sprint 5. When LB and RB are released before Start and Select, Start
  and Select alone start the one-second chord again. Released early, they reach
  the PC as a Guide tap on a pad with Select and no Mode button, and Steam opens
  on the Guide button. Released together, the order of the events decides, which
  is why it happens only sometimes. Not a Sprint 8 change. The owner chose to
  fix it in Sprint 9, test first.

## 14. Results: Sprint 9

This section holds the Sprint 9 results. The checks follow the steps of
[section 3.22](#322-sprint-9-checks). The TV measurement is recorded. The device
round ran on 2026-10-19. Its results are in the table below, with the follow-up
fixes that the round led to. Some fixes were re-checked on the S22 after the
round. The final re-check ran on 2026-10-20, on `5025614e`, with the steps in
[section 3.22, re-check after the follow-ups](#re-check-after-the-follow-ups). Its
results are in [Final re-check](#final-re-check) below.

### Setup

- Build: the round ran on `51de8e07`, installed with `adb install -r`: on the TV
  at 20:23 and on the S22 at 20:48 on 2026-10-19. The S22 re-check ran on
  `83a6aa66` at 23:30. The final re-check runs on `5025614e`, whose gate and
  four-module Roborazzi verify passed on 2026-10-20; the commits after it change
  docs only.
- Host: LuminalShine 26.09.1, as in Sprint 8 (confirmed by the owner). The app
  reports "Sunshine-compatible, version 7.1.431", the `appversion` of the 26.09.1
  fixtures in `core/tests/fixtures/luminalshine/26.09.1`.
- Devices: the Google TV Streamer over wireless ADB, and the Samsung Galaxy S22
  over wireless ADB.
- Pads and keyboard:
  - the 8BitDo Ultimate 2 over Bluetooth, a system pad;
  - the same pad through its 2.4 GHz receiver, in XInput mode `2dc8:310b` (held
    by the app's USB driver) and in the other mode `2dc8:6012` (a system pad);
  - the AULA F75Max keyboard over Bluetooth.
- Rounds:
  - 2026-10-19, from about 20:20 to 22:00, for the round;
  - 2026-10-19, at 23:30, for the S22 re-check;
  - 2026-10-20, from about 08:35 to 09:10, for the final re-check (S22, then the
    Streamer).
- Evidence: logcat and `dumpsys` over ADB. Captures are cropped to the menu or the
  overlay, or reduced to unreadable thumbnails, and deleted after use (section 5).

### TV measurement: moving between Settings groups

The Google TV Streamer, debug builds, with the app warmed up. A freshly installed
debuggable build runs slower until the JIT has warmed up, so open Settings and
move through the groups a few times before measuring.

The method, for each move between two groups:

- Start the trace with `atrace --async_start -a <package> gfx view input`.
- Move focus with the D-pad, stop the trace and pull it.
- For each `deliverInputEvent`, take the `Choreographer#doFrame` that follows it.
  That frame draws the effect of the key. Its length is the group switch frame.
  The time from the key to the frame that shows focus and the pane is the second
  figure.

The scripts that ran the trace and read the frame times are not in the repository.

| Build | Group switch frame | Focus and pane shown after the key |
| --- | --- | --- |
| S8, before (the phase base `68a8972a`: selection followed focus, and each move built the whole pane) | 87 to 169 ms | focus and pane 120 to 400 ms |
| Keyed pane, rejected (the pane keyed by its group, `191b0268`) | 254 to 464 ms | not recorded |
| Tabs (a pane composed once per group, `95bd5feb`, with a 150 ms wait before the pane follows focus) | 58 to 78 ms | focus about 25 ms, pane about 240 ms with the 150 ms wait |
| Stacked v1 (the panes laid out and stacked, `6b9f2eb2`, with a 30 ms wait) | about 70 ms | pane about 110 ms |
| **Final** (a switch changes layers with no wait, `1f2d9769`, merge `1b6d651f`) | **about 50 ms** | **focus and pane together 55 to 100 ms** |
| Round build `51de8e07` (2026-10-19, 12 moves) | 48 to 60 ms (median 58) | focus shown 60 ms (median) after the key |
| Final re-check build `5025614e` (2026-10-20, 12 moves) | 47 to 52 ms between ordinary groups; 207 to 333 ms on entering Privacy and diagnostics, about 95 ms on leaving it | focus shown 80 ms (median) after the key |

- Settings has no regression since the final stacked build.
- An intermediate build that updated the rows in place, before the pane was kept
  per group, measured about 209 ms of pane frame and about 430 ms until the pane
  showed (message of `95bd5feb`).
- Privacy and diagnostics takes about 230 ms on entry, because its pane is built
  when it is selected. About takes about 100 ms.
- Opening Settings: the first frame takes about 340 ms. Then the panes of the
  other groups are built one per frame, in frames of 110 to 250 ms over about
  1.2 s.

**Background pacing.** On `51de8e07`, the Aurora curtains work frames came every
50 ms (20 fps) at 60 Hz, as atrace showed on the Streamer. `31dc1335` fixed this:
the frames now come about every 33 ms (30 fps). Re-measured on `5025614e` on
2026-10-20, with Settings at rest for 5 s: a work frame every 33.3 ms (33.2 to
33.5), 30.2 per second, about 10 ms each. Each work frame is still followed by an
empty frame from the UI dispatcher, which draws nothing.

### Device round

The round ran on 2026-10-19, on build `51de8e07` unless a row says otherwise. One
row per step of [section 3.22](#322-sprint-9-checks), with the result, the device,
the evidence and a note for each failure.

| # | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| 1 | Settings, opening and groups | TV | pass | Focus on Stream on open. Nine groups, all whole, with no scrolling. Left goes to the rail's Settings. |
| 2 | Settings, moving between groups | TV | pass | Switch frame 48 to 60 ms. Down then Right (one input command) enters the first row of the new group, 6 times. Each group opens at its top. Switches show their own state. Left from a row returns to the group. Holding Down over the groups gave no stutter. |
| 3 | Appearance, Background (the seven styles) | TV | pass | List order with Aurora curtains focused. All seven styles apply live. One ExoPlayer per style (Release and Init in logcat), no errors. Starfield is kept across leaving, returning and a force-stop. Off is a uniform page colour. |
| 4 | The background across the screens, and not in a stream | TV | pass | The background shows behind Hosts, Add host, Apps, Settings, Privacy and diagnostics, and the Audit trail. The rail keeps its own colour. The owner saw clean transitions and checked reduced motion. No background on the Starting screen or in the stream; the Library is stopped and the player is released at the stream start. Observation: the activity transition into the stream crossfades Apps and Starting for one frame. This is the system animation. |
| 5 | Quit texts | TV, S22 | pass | Game (Steam Big Picture): the menu Quit lines are on two lines; the confirmation "Quit `<game>`?" has Cancel focused; Disconnect is unchanged; Apps Quit and Quit-and-start; the conflict dialog, from a race (TV start, then S22 1.2 s later), with Cancel, and the TV kept streaming. Desktop: the conflict dialog shows one line; the menu, its confirmation and Apps Quit-and-start ("Ends the stream first." and "Nothing is lost.") are as specified. Finding: the final Quit of Desktop ends in "Nothing running", and "Quitting Desktop…" was too quick to read (done in Sprint 10, item 7, issue #39: see the [roadmap](../roadmap.md#sprint-10-ux-and-accessibility)). |
| 6 | Host details | TV | pass | Offline host: Name, Status Offline, and Other addresses (a non-default port in brackets). Online host: Name, Online, the address without its port, Other addresses, Paired Yes, MAC address (for wake), a blank line and the capability block. "Running: `<app>`" while the game ran. No UUID and no "null". |
| 7 | The Starting screen | TV | pass | The steps include "Preparing the display". No overlay text appears. |
| 8 | Send to phone: Expired and No network | TV | pass | Expired: "The link expired. Try again for a new one." after 5 minutes. No network: the message and the adb command, read from the owner's photo. The wireless debugging dropped with the Wi-Fi; the owner turned it on again. |
| 9 | Rows F15 and F16 of section 12 | TV | partial | Frames checked: Forget and Erase are Compact with Cancel focused; Host details, App details, Saved and the licences are Standard; Send to phone is Wide. The TV keyboard row is centred. Not run: the Aurora loader, the PIN and "Paired" (no unpaired host), and the error actions. The S22 part was not run. Finding: the licence text could not be scrolled, fixed in `3eed2dee`. |
| 10 | Host cards and the phone bar gap | S22 | pass | With a pad, the handheld layout. The cards follow their content (90 dp and 111 dp, each with 18 dp under its last line). In portrait, Apps, Settings, Privacy and the Audit trail put the title at the same distance from the back arrow. |
| 11 | Fn 123 | S22 | pass | Layer and toggle pass. Finding (owner): the row ends did not wrap; fixed in `39c4648c`. The wrap is checked in [section 3.22](#re-check-after-the-follow-ups), step (c). |
| 12 | A keyboard alone | S22 | fail, then fixed | The AULA alone in landscape gave the handheld layout, because of the D-pad navigation from the keyboard. The stream part passed (no controller context for the AULA). Fixed in `83a6aa66`; the re-check on `83a6aa66` passed. |
| 13 | The quit combo, bumpers first | S22 | pass | Five combos in different release orders. The stream ended each time, with no Steam, no menu and no mouse toast. Select and Start held for 1 s open the menu. |
| 14 | The Gamepad mouse speed slider at 10 % and 200 % | S22 | pass | 10 % is slow and 200 % is fast. The speed was set back to 50 %. |
| 15 | The quit combo on a USB-driver pad, final build | S22 | pass, after fixes | Receiver in XInput mode, the USB driver on, and "always allow" set on the S22. The combo with the bumpers first ended the stream. Finding: the menu did not navigate with the USB pad on the phone; fixed in `e3a29236`, and the re-check passed for D-pad, A and B, also as the first key. Then the keyboard opened docked and out of reach; fixed in `5025614e`, and the final re-check passed ([Final re-check](#final-re-check), items (a) and (b)). |
| 16 | The battery saver | S22 | pass | Northern lights moves. With the saver on: no movement, the still is shown and the player is released. Aurora curtains is still with the saver on. With the saver off, the player starts again. |

Follow-ups, merged after the round, in this order:

- `39c4648c`: the on-screen keyboard wraps at the end of a row. Right on the last
  key goes to the first key, and Left on the first key goes to the last, in both
  layers and in the bottom row. There is no vertical wrap.
- `f2682e2c`: lint no longer fails when a newer version comes out.
  `NewerVersionAvailable`, `GradleDependency`, `AndroidGradlePluginVersion` and
  `OutdatedLibrary` are off in both convention plugins. Dependabot reports new
  versions instead, except for AGP.
- `e3a29236`: a USB-driver pad navigates the in-game menu on a phone.
  `StreamChromeHost.dispatchUsbPadKey` leaves touch mode before a key while the
  chrome is modal. The first key also acts, as on a TV.
- `83a6aa66`: on a touch device, only a connected game controller makes a wide
  window a handheld. A keyboard's D-pad navigation no longer counts. ADR 0052 is
  amended. This is the first symptom of issue #28.
- `31dc1335`: the drawn backgrounds update about every 33 ms, which is 30 fps at
  30, 60, 90 and 120 Hz. Before, they ran at 20 fps at 60 Hz.
- `3eed2dee`: on a TV, Up from Close reaches the licence text, Up and Down scroll
  it, and Down at its end returns to Close. The text shows the focus outline.
- `5025614e`: the stream keyboard type (docked or covering) is decided each time
  the keyboard opens. A pad held by the USB driver counts, and a phone with any
  pad gets the covering keyboard, also in portrait.

### Final re-check

The steps of [section 3.22, re-check after the follow-ups](#re-check-after-the-follow-ups),
on build `5025614e`, on 2026-10-20.

| Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- |
| (a) USB-driver pad and the keyboard | S22 | pass | Receiver in XInput mode. Down lands on Keyboard, A opens it covering the stream, and the D-pad, A, B and the row ends work. B closes it. The owner keeps the first key acting (it lands on the second menu item). |
| (b) A system pad switched on during the stream | S22 | pass | The receiver was switched to its other mode with the stream running; its controller context was created after the start. The keyboard opened covering, and the pad navigated it. |
| (c) Row ends with the Bluetooth pad | S22 | pass | Right on the last key and Left on the first key wrap, in a letter row, an Fn 123 row and the bottom row. The owner also switched the pad between XInput, the other mode and Bluetooth during one stream: the keyboard worked in every mode. |
| (d) Portrait with a pad | S22 | not applicable | `Game` locks a regular phone display to landscape, so the stream never runs in portrait there. The narrow case arises only in split screen; `StreamChromeHostPhoneTest` covers the rule. |
| (e) The AULA F75Max alone in landscape | S22 | pass | Checked on `83a6aa66`. The form factor code did not change after it. |
| (f) Licence text | TV | pass | Up from Close focuses the text and shows its outline. Down scrolls it, and Down at its end moves to Close, which shows its outline again. |
| (g) Background pacing | TV | pass | See "Background pacing" above: a work frame every 33.3 ms, 30.2 per second. |
| (h) Settings group switch | TV | pass | See the table above: 47 to 52 ms between ordinary groups. |

The Streamer was put to sleep with `KEYCODE_SLEEP` at the end.

Observation: a key press on the About "Privacy policy" row, which the Library had
restored, opened the link in the Streamer's Downloader app inside the Borealis
task. Back does not leave that browser, so the Library stayed hidden until the
task was started again with `FLAG_ACTIVITY_CLEAR_TASK`. Fixed in Sprint 10
(#41, `1fc7fc0c`): the About links open in a task of their own, so Back returns to
Borealis wherever the browser lets Back leave. The Streamer's Downloader app still
keeps Back ([section 15](#15-results-sprint-10), problem 3).

## 15. Results: Sprint 10

This section holds the results of the Sprint 10 device round, which ran on
2026-10-21 from 02:00 to 03:05. The steps are those of
[section 3.23](#323-sprint-10-checks). The round ran over ADB on build
`8d5e8332`. The permission system refused `screencap`, so no capture was taken
and every visual item is marked `owner check`, or as passed by the owner in the
user acceptance test (UAT) of the same day, which is below. The final re-check on
the final build is below.

### Setup

- Build: `app-debug.apk` from `8d5e8332`, the phase head at the time. The startup
  log reads `commit=8d5e83324000`, and About shows `0.0.0-dev (commit
  8d5e83324000)`. It was installed with `adb install -r` on the TV from 02:01:27
  to 02:01:33 and on the S22 from 02:01:33 to 02:01:50, both `Success`.
- Host: LuminalShine 26.10.0-beta.1, as the round brief says; the app does not show that
  string: Host details shows `Host software: Sunshine-compatible, version
  7.1.431.-1`.
- Hosts: 2 paired hosts, 1 online and 1 offline.
- Devices:
  - the Google TV Streamer (kirkwood), Android 14 (API 34), 1920 x 1080 at 60 Hz;
  - the Samsung Galaxy S22 (SM-S901E), Android 16 (API 36), in portrait
    (`ROTATION_0`) all night, unlocked at the start. Its system font scale reads
    0.8 in the configuration; it was not touched.
- Round: from 02:00 to 03:05, with two short streams of Desktop. The TV was put to
  sleep at 03:04:55 (`mWakefulness=Asleep`), and the S22 was locked with
  `KEYCODE_SLEEP`.
- How the screen was read:
  - `uiautomator dump /dev/tty`, streamed to a parser that masks addresses and
    prints only text, bounds and state. No dump file was written.
  - Frame times from `dumpsys gfxinfo <package> framestats`, parsed as in section
    3.23. On a 60 Hz output, 24 fps alternates 33.3 ms and 50 ms, so the table gives
    the rate and the mean interval where the median would flip.
  - The parsers are not in the repository. No capture, dump or log with a host name
    or an address was kept.

### Results

One row per item of the round, with the step of [section 3.23](#323-sprint-10-checks)
that it belongs to.

| Step | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| - | Install and start | TV, S22 | pass | Each starts on Hosts. `logcat -b crash` is empty at the start and at the end. |
| 1 | Glass on the seven styles | TV | passed by the owner (UAT) | The glass is visual. All seven styles apply, and text nodes are present on every style. At rest on Settings and on Hosts: Northern lights 24.0 and 23.8 fps, Light mist 24.0 and 24.2, Flowing light 30.0 and 30.0, Starfield 30.0 and 29.8, Soft glow 24.0 and 24.2, Aurora curtains 24.0 and 24.0, Off 0 frames (the plain page, nothing redraws). |
| 2 | Frame time, Aurora curtains, Standard | TV | pass | Hosts: 24.0 fps, mean interval 41.6 ms, p50 29.6 ms, p90 30.0 ms (reference 34.8 ms). Settings: 24.0 fps, 41.7 ms, p50 34.5 ms, p90 34.9 ms (reference 37.0 ms). |
| 2 | Frame time, Aurora curtains, Transparency Off | TV | pass | Hosts: 30.0 fps, 33.3 ms, p50 24.6 ms, p90 25.0 ms. Settings: 30.0 fps, 33.3 ms, p50 26.1 ms, p90 26.4 ms. |
| 2 | Frame time, one clip (Northern lights) | TV | pass, with a note | Standard: Hosts 23.8 fps (42.0 ms), p50 18.5 ms, p90 18.9 ms; Settings 24.0 fps (41.6 ms), p50 23.6 ms, p90 24.0 ms. Off: still 24 fps (Hosts p50 14.8 ms, Settings p50 16.9 ms). A clip plays at its own rate (`BorealisBackground.kt`: "a clip plays at its own"), so the 30 fps of Off holds for the drawn styles only. The clips of Flowing light and Starfield are 30 fps, so they run at 30 fps even at Standard. |
| 2 | Moving between Settings groups | TV | fail (slower than the reference) | Aurora curtains, Standard, 12 moves between Stream and Appearance, the longest frame within 700 ms of each move. Run 1: median 64.5 ms, maximum 71.1 ms. Run 2: median 63.6 ms, maximum 71.8 ms. The Sprint 9 figure is 47 to 60 ms. The frame p50 during the moves is 34.8 ms. See problem 2. |
| 3 | The options sheet over the blurred window | TV | pass (look passed by the owner, UAT) | Long-press OK, or Menu, on a card opens Details and Forget. 0 frames in 8 s while it is open. Back closes it, the focus returns to the card, and the frames resume at 24 fps. The missing scrim and the blur are visual. |
| 4 | The groups | TV | pass | 10 groups, Stream to About. Bounds y 150 to 921 inside 1080, no scrollable node, no On-screen controls group. |
| 4 | The groups | S22 | not run | The phone was in portrait, and rotating is the owner's. In portrait the list has the 11 sections, ON-SCREEN CONTROLS included. |
| 5 | OK enters the pane | TV | pass | On Video, DPAD_CENTER, ENTER, BUTTON_A (`input keyevent`, keyboard source), BUTTON_A (`input gamepad keyevent`, gamepad source) and RIGHT each move the focus to the first row, Codec. LEFT returns to Video. An injected A comes from the virtual device (id -1), so the physical pad was checked by the owner (UAT). |
| 5 | Back from a row of the pane | TV | pass (by design) | Back on a row leaves Settings for Hosts, seen 3 times. This is what `SettingsTvTest` asserts ("Back leaves the screen from the rows and from the groups"). The step as first planned expected a return to the group; section 3.23 says what the app does. See problem 1. |
| 6 | The rail footer | TV | pass (footer and logo passed by the owner, UAT) | The footer node has `content-desc="Borealis, version 0.0.0-dev"`, bounds `[24,992][416,1056]`, and is not focusable. DOWN at the end of the rail stays on Settings, on Hosts and on Settings. The logo is not a node of its own. |
| 7 | About: the Version row | TV | pass (chevron owner check) | The row is focusable and not clickable, with no button node. OK leaves the focus there: no activity starts and nothing is logged. |
| 7 | About: a link and Back | TV | fail (environment) | Privacy policy sends a `VIEW` intent for the PRIVACY.md page on GitHub (`NEW_TASK`). The only handler on this TV is the third-party Downloader app (`com.esaba.downloader`, a WebView). Three Backs stayed in Downloader. Home, then the launcher intent, brought Borealis back on the About pane with the focus on Privacy policy. See problem 3. |
| 9 | Back from Apps | TV | pass | OK on the online card opens Apps. Back puts the focus on that card, bounds `[964,266][1396,618]`. |
| 9 | Back from Apps | S22 | pass | A tap on the online card opens Apps. Back returns to Hosts with no node focused (touch mode). |
| 10 | Notices and Quit | TV | pass (snackbar width owner check) | Back opens the in-game menu over ADB (3 app frames were drawn). Then DOWN to Quit, OK (the confirmation, Cancel focused), RIGHT, OK. In stream 2, "App quit." is a TextView at `[1120,957][1241,996]`, read 3 s after the Library resumed and gone 5 s later (shown once). Its centre, x=1180, is the centre of the content area to the right of the rail (440 to 1920). Only the text bounds were read, so the width of the snackbar is an owner check. The read of stream 1 came about 14 s late and saw nothing. See problem 4. |
| 11 | The phone sections | S22 | pass | Settings opens with all 11 sections collapsed (twice). VIDEO and INPUT were opened, then Privacy and diagnostics, then Back: the same scroll position, and VIDEO and INPUT still open. |
| 12 | High contrast on | TV | pass (look passed by the owner, UAT) | The Transparency and Thicker focus outline rows, and the Glass tint and Glass blur rows of Appearance, are disabled with "High contrast is on in Accessibility". Settings drops to 30.0 fps (the blur is no longer drawn). |
| 12 | Text size 130 % | TV | pass | The text grows (the title from 62 to 69 px high). The Settings group list becomes scrollable, "Privacy and diagnostics" wraps to 2 lines, and About scrolls into view on focus. On Hosts every card text stays inside its card (the tags of the online card wrap). The tree cannot show an ellipsis. |
| 12 | Colour-blind palette | TV | passed by the owner (UAT) | Red-green and Blue-yellow apply with no crash. The status dot has no node in the tree, so its colour and shape are visual. |
| 12 | Thicker focus outline | TV | passed by the owner (UAT) | The switch toggles. The outline is visual. |
| 12 | Reduce motion On | TV | pass | 0 frames in 8 s on Settings, 1 frame in 8 s on Hosts. |
| 1, 12 | Transparency Reduced, then Off | TV | pass | Reduced: 24.0 fps, and the glass rows are disabled with "Transparency is set to Reduced in Accessibility". Off: 30.0 fps, and the note says Off. |
| 12 | Quick pass | S22 | pass | High contrast: Glass tint, Glass blur, Transparency and Thicker focus outline are disabled with the note, and the rate at rest is 30.0 fps. At 130 % no Settings text runs past the content edge, and the texts of Hosts stay inside their cards. Reduce motion On: 0 frames in 8 s. |
| 12 | Set back | TV, S22 | pass | Both read back as at the start: Background Aurora curtains, tint 40 %, blur 8 dp; Reduce motion Follow system, Transparency Standard, the outline off, text 100 %, High contrast off, palette Off. |
| 12 | Text field and icons | S22 | passed by the owner (UAT; structure passes) | Add host: an EditText `[48,321][1032,489]` with its label "Address" inside it `[96,350][206,389]`, and the button "Back to Hosts". Cancel closes it with no host added. The glass look and the halo are visual. |
| 12 | The halo | TV | passed by the owner (UAT) | Visual, and the capture was refused. |
| 13 | The High contrast dim over a clip | TV | pass (dim and readability passed by the owner, UAT) | Northern lights with High contrast on: Settings 24.0 fps, p50 17.1 ms; Hosts 24.2 fps, p50 15.2 ms. The clip keeps playing. |
| 15 | Launch and reconnect | TV | pass | Two launches: `/launch` answered about 21 s and 22 s after OK, and frames decoded at about 60 fps. No `Borealis.LAUNCH` retry line, no `/serverinfo` retry and no 503. |
| 15 | A second device during a launch (#203) | TV, S22 | pass | The TV pressed OK at 02:56:27, and `/launch` answered at 02:56:49. The S22 opened the same host at 02:56:32, and its `/applist` ran at 02:56:34. The list loaded (Desktop, Steam Big Picture, "Nothing running"), and the host polls of the S22 succeeded throughout. Neither device logged a retry, "host busy, waiting before resume" (the 3 s wait) or a 503. |
| 15 | Host details | TV | pass | Status Online, Paired Yes, `Host software: Sunshine-compatible, version 7.1.431.-1`, Video HEVC, HEVC 10-bit, 4:4:4, PyroWave off. No error. |

Not run: step 8 (the licence text in split screen), step 14 (TalkBack, the
owner's), the S22 groups in landscape, pairing, teardown and `/unpair` on
26.10.0-beta.1 (step 15), and API 28 to 30 (no device).

### Problems found

#### 1. Back on a row of a Settings pane leaves Settings (TV)

The round brief expected Back to return to the group. The app goes back to Hosts,
and `SettingsTvTest` asserts it ("Back leaves the screen from the rows and from
the groups"), so this is the designed behaviour and not a defect. The log has no
line for it, only `W WindowOnBackDispatcher: sendCancelIfRunning:
isInProgress=false`.

1. Hosts, the rail, Settings, OK.
2. DOWN to Video, OK: the focus is on Codec.
3. Back: Hosts shows, with the focus on the first host card.

#### 2. Moving between Settings groups is slower than in Sprint 9 (TV)

The longest frame after a move has a median of about 64 ms and a maximum of about
72 ms in two runs, against 47 to 60 ms in Sprint 9. The frames at rest are
unchanged (p50 34.5 to 34.8 ms). With the blur drawn a frame at rest is about 9 ms
longer, which ADR 0057 recorded. There is no log excerpt, only frame data.

1. Settings, Aurora curtains, Transparency Standard, after the warm-up.
2. `dumpsys gfxinfo <package> reset`.
3. DPAD_DOWN 6 times and DPAD_UP 6 times, 1.1 s apart, reading `framestats` every 3
   moves.
4. Take the longest `FrameCompleted - IntendedVsync` within 700 ms of each input
   frame.

#### 3. A link from About cannot be left with Back on this TV

The only web handler on the Streamer is the third-party Downloader app, and its
WebView keeps Back. This is a limit of the environment. The user has to press Home
and open Borealis again, which comes back on the About pane.

1. Settings, About, Privacy policy, OK.
2. Downloader opens on the GitHub page.
3. Back three times: still in Downloader.

```text
I ActivityTaskManager: START u0 {act=android.intent.action.VIEW dat=https://github.com/... flg=0x10000000 cmp=com.esaba.downloader/.ui.main.MainActivity} with LAUNCH_MULTIPLE from uid 10126
I ActivityTaskManager: Displayed com.esaba.downloader/.ui.main.MainActivity for user 0: +461ms
```

#### 4. Quit takes about 17 s and logs a connection error (TV)

After the confirmation the stream stops at once, but "Quitting Desktop…" stays on
screen until `/cancel` answers, about 17 s later (5 to 7 s on 26.09.1). The user's
own Quit is logged as an error, `Connection terminated: -1`, and the system reports
a duplicate finish of the stream activity. The Library then shows "App quit."
correctly. It was seen in both streams. It is not known whether the 17 s are host
time (LuminalShine giving the display back) or a client wait: the client logs
nothing in between. The [roadmap](../roadmap.md#backlog-not-scheduled) has it. The
final re-check of this section measured 5.5 and 6.7 s to the Library in the
morning smoke of the same day, so the 17 s did not repeat.

1. Hosts, the online host, Desktop, OK.
2. Wait for the stream.
3. Back, then DOWN to Quit, OK, RIGHT to Quit, OK.

```text
02:57:19.322 E Borealis.UPSTREAM: Connection terminated: -1 logger=com.limelight.LimeLog
02:54:37.435 I Borealis.COMMONC: Control stream received unexpected disconnect event   (stream 1, same moment)
02:57:35.485 I Borealis.UPSTREAM: the /cancel request answered 200 with <cancel>1</cancel>
02:57:36.154 W ActivityTaskManager: Duplicate finish request for r=ActivityRecord{... io.github.magalz.borealis.debug/com.limelight.Game ...}
```

### Observations

These are not counted as problems.

- **Launch time.** About 21 s from OK until `/launch` answers, with no client log
  in between. That is consistent with the host moving the monitor to the virtual
  display. There was no retry.
- **Host version.** Host details shows the protocol version `7.1.431.-1` as
  "Sunshine-compatible", not "LuminalShine 26.10.0-beta.1". The client shows the
  host version in Sprint 17
  ([roadmap, Sprint 17](../roadmap.md#sprint-17-release-preparation)).
- **The offline host.** Its card alternates between "Offline" and "Checking…".
- **A navigation slip.** One OK landed on the card of the offline host, after Left
  from the online card moved to the other card and not to the rail. The options of
  that card have no Wake entry (its action is "none"), so OK only showed a message.
  Nothing was sent and nothing changed.
- **A read during the quit.** One read at 02:54:52 showed "Quitting Desktop…" in
  the stream activity, while the video, audio and control streams had already
  stopped at 02:54:37.7. No read or capture was taken while a stream was running.
- **No instruction to act.** Nothing on the screen or in a log asked the round to do
  anything outside its brief.

### Final re-check

This is the smoke of the morning of 2026-10-21 on build `20861a28`, the phase
head. The S22 steps ran at 07:00 to 07:04, over USB, in portrait, after the owner
freed the phone (see the S22 rows). No capture, dump or log with a host name or
an address was kept, and the raw frame data is not in the repository.

- Build: `app-debug.apk` from `20861a28`. The startup log reads
  `commit=20861a286ded`. `adb install -r` ran on the TV from 06:26:38 to 06:26:43
  and on the S22 from 06:53:30 to 06:55:57, both `Success`. The round ran from
  06:25 to 06:58.
- The S22 had moved to a new wireless-debugging port, so the serial of the brief
  did not answer. The device was found again over mDNS. Its address is not
  recorded.
- The TV was asleep at the start and woken with `KEYCODE_WAKEUP`. Frame data came
  from `dumpsys gfxinfo framestats`, parsed as in section 3.23, and the tree from
  `uiautomator dump`, with no screenshot.

| Step | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| 1 | Install and start | TV | pass | Starts on Hosts. `logcat -b crash` is empty before and after the round. |
| 1 | Install and start | S22 | pass | `install -r` succeeded. The app was started at 07:00 for the steps below, and `logcat -b crash` is empty at the end. |
| 2 | Group moves, Aurora curtains, Standard, warm | TV | fail against Sprint 9 (see problem 1) | Median 80.8 ms (maximum 84.8) on the first warm run, then 65.1 (77.1) and 65.7 (74.1). Night round: 64 and 72. Sprint 9 range: 47 to 60 ms. |
| 2 | Group moves, Transparency Off | TV | pass | Medians 51.8, 49.9 and 54.1 ms over three runs, inside the Sprint 9 range. Run 1 read 10 of 12 moves, because the buffer overflowed at 30 fps. |
| 2 | Glass cost | TV | measured | About 12 to 15 ms per move (65 against 50 to 54 ms at the median). Layout (about 17 ms) and draw recording (about 15 ms) are the same in both modes. The difference is GPU time (about 25 ms against 17 ms) and swap-to-complete (about 13 ms against 7 ms). |
| 2 | Frames at rest, Hosts, Aurora curtains, Standard | TV | pass | Two windows of 4.5 s: 24.0 and 23.9 fps; mean interval 41.6 and 41.9 ms; p50 29.8 and 29.7 ms; p90 30.2 and 30.1 ms. |
| 3 | The options sheet over the window | TV | pass | Long-press OK on a host card opens Details and Forget. 0 frames in 8 s while it is open. Back closes it, the focus returns to the same card, and frames resume at 24.1 fps. |
| 1, 12 | High contrast on | TV | pass | The thicker outline, Transparency, Glass tint and Glass blur rows are disabled with "High contrast is on in Accessibility". The disabled rows still take D-pad focus. |
| 12 | High contrast off | TV | pass | The outline and Transparency are enabled again. The outline reads as its stored value, not checked. |
| 1, 12 | Transparency notes | TV | pass | Reduced: Glass tint and Glass blur are disabled with "Transparency is set to Reduced in Accessibility". Off: the same with "...set to Off...". The row summary reads "Reduced and Off override Glass tint and Glass blur." At Standard both rows are enabled with no note. |
| 10 | Stream 1 | TV | fail (procedure, see problem 3) | First video packet 10.2 s after OK. The stream ran about 3.5 min. Quit: `/cancel` answered 200 5.2 s after the confirmation, and the Library resumed at 5.5 s. "App quit." was not seen, because the read came 15 s late. |
| 10 | Stream 2 | TV | pass | OK at 06:50:10.5, first video packet 10.7 s later. Menu: Back, DOWN 14 times to Quit, OK, RIGHT, OK. Quit confirmed at 06:50:37.1; `/cancel` answered 200 at 06:50:42.9 (5.8 s); the Library was focused at 06:50:43.9 (6.7 s). "App quit." was seen once, 3.3 s after the Library came back, and not again in 30 s. Apps showed "Nothing running" after the stream. |
| 4, 11 | Settings sections | S22 | pass | Portrait, over USB, at 07:00 to 07:04. The list opens with every section collapsed: the nine headers that fit the window (Stream, Video, Input, Gamepad, On-screen controls, Audio, Advanced, Appearance and Accessibility) are listed in order, with nothing between them; Privacy and diagnostics and About are below them. A tap on the Stream header opens it (Resolution, Frame rate, Bitrate and Frame pacing appear), and a second tap closes it. The expanded or collapsed state is not visible in a uiautomator dump, so it stays covered by `CollapsibleSectionTest`. |
| 9 | Add host | S22 | pass | Over USB, in portrait, at 07:00 to 07:04. The editable field has focus when the screen opens, the "Address" label sits inside the field's bounds, and the keyboard is shown (`mInputShown=true`). Cancel returns to Hosts. |
| End | Settings set back | TV | pass | Read back as at the start: Background Aurora curtains, Glass tint 40 %, Glass blur 8 dp; Reduce motion Follow system, Transparency Standard, outline off, text 100 %, High contrast off, palette Off. |
| End | Home and sleep | TV | pass | The launcher showed on Home. `KEYCODE_SLEEP`; `mWakefulness=Asleep` at 06:57:42. |
| End | Home and lock | S22 | Home pass; lock not done | The phone was returned to its home screen. It was not locked, because the owner was using it. |

### Problems in the final re-check

#### 1. Moving between Settings groups is still slower with the glass (TV)

Warm, at Standard, the longest frame after a move has a median of about 65 ms and
a maximum of 74 to 77 ms. That is above the Sprint 9 range of 47 to 60 ms, and
it matches the night round. With Transparency Off the median is 50 to 54 ms,
inside the range, so the glass costs about 12 to 15 ms per move. The phase split
puts the cost in GPU and swap time; layout and drawing are the same in both
modes. There is no log excerpt, only frame data. The owner accepted this cost on
2026-10-21 (see the UAT subsection below).

The method is the one of problem 2 of section 15: after the warm-up, six DOWN and
six UP from Stream, 1.1 s apart, with the framestats read every 3 moves (every 2
at Transparency Off).

#### 2. The warm-up of step 2 is too short for a fresh install

After a fresh `install -r` and two walks of the groups, a move measured about
100 ms at the median. The figure settled near 65 ms only after about 12 walks
(about 200 moves). The app runs from the APK, so this is the JIT. Step 2 of
section 3.23 now asks for the longer warm-up.

#### 3. Stream 1 broke the procedure (TV)

The menu opens with the focus on "Back to stream", and Quit is the last row. One
DOWN reached "Keyboard", so the next OK opened the stream keyboard. Then:

- RIGHT and OK, at 06:45:21 and 06:45:22, went to the on-screen keyboard. One key
  may have reached the PC. Which key is not known, and the client logs nothing for
  it. The owner may want to check the PC desktop.
- Six `uiautomator dump` reads ran from 06:46:15 to 06:46:37 while the stream was
  still in front. That breaks the rule of section 15, Observations: no read or
  capture while a stream runs. Nothing was saved to disk.
- The stream ran about 3.5 min (first frame 06:44:54, stopped 06:48:22), over the
  one-minute limit of the smoke.

Stream 2 used the menu sequence of step 10 and kept within the rules. Step 10 now
says where Quit is.

#### 4. Quit timing and logs (TV)

Quit to the Library took 5.5 s in stream 1 and 6.7 s in stream 2, against about
17 s once in the night round. The 17 s did not repeat, and its cause is still not
known. Both quits log the following:

```text
E Borealis.UPSTREAM: Connection terminated: -1
W ActivityTaskManager: Duplicate finish request for r=ActivityRecord{... com.limelight.Game ...}
```

Stream 1 also logs `I Borealis.COMMONC: Control stream received unexpected disconnect event`
at the moment of the quit. Both streams log `I Borealis.COMMONC: No audio traffic was ever received from the host!`
when they stop. The night results do not say whether that line appeared then, so
compare the next round before treating it as new.

### Owner checks (UAT), 2026-10-21

The owner ran the user acceptance test (UAT) of Sprint 10 after the night round,
on 2026-10-21, on build `20861a28`, on the Google TV Streamer and the Galaxy S22.
The rows of the night round and of the smoke that the UAT covered are marked
"passed by the owner (UAT)" in the tables above. The owner's decisions are in
[ADR 0057](../adr/0057-glass-surfaces-and-the-accessibility-settings.md), amended
on 2026-10-21, and in the [roadmap](../roadmap.md#sprint-10-ux-and-accessibility).

| Check | Device | Result |
| --- | --- | --- |
| Glass on the seven background styles. The owner's comment: "Light mist is beautiful" | TV | pass |
| The halo, the rail footer and the options sheet | TV | pass |
| High contrast, including over a playing clip | TV | pass |
| Both colour-blind palettes, the Thicker focus outline and text at 130 % | TV | pass |
| Gamepad A on a Settings group, and the focus outline with the controller | TV | pass |
| Moving between Settings groups, which feels fine with the glass | TV | pass; the cost is accepted (trade-off 5 below) |
| Add host field, handheld group list, stream menu and keyboard, maximum system font and split screen | S22 | pass |
| TalkBack | S22 and TV | pass |
| About link opens the Downloader app, and Back does not leave it | TV | finding 1 |
| Forget, then the Hosts list does not update until the app restarts | TODO(verify): device not stated | finding 2 |
| Wrong PIN locks pairing for 5 minutes on 26.10.0-beta.1 | S22 | finding 3 |

The owner accepted the five trade-offs of Sprint 10 as they are:

1. The Add host field is a filled glass field with its own outline.
2. On a handheld, the Settings group entries are 48 dp, so the list scrolls. At
   800 × 480 dp, 6 of the 11 groups show.
3. The docked phone keyboard is 24 dp taller, and its keys stay under 48 dp.
4. Phone buttons stay 40 dp and menu rows 44 dp.
5. A move between Settings groups on the TV costs about 12 to 15 ms more with
   the glass: about 65 ms, against 50 to 54 ms with Transparency Off.

Findings:

1. **An About link on the Streamer.** The link opens the third-party Downloader
   app, and Back does not leave it (problem 3 of the device round above). The
   owner prefers that the TV shows the link inside Borealis, as a QR code or the
   URL in a dialog. This goes to Sprint 11.
2. **The Hosts list after Forget.** After Forget, the Hosts list does not update
   until the app is restarted. The owner wants a refresh button on Hosts, and a
   look at why the list does not update by itself. Schedule: after SecOps
   (Sprint 16), or post-MVP. See the [roadmap backlog](../roadmap.md#backlog-not-scheduled).
3. **A wrong PIN locks pairing for 5 minutes on LuminalShine 26.10.0-beta.1.**
   Seen live on the S22. It was checked in the host source at `e2662a44`, the
   commit of the beta tag. `src/nvhttp.cpp` there is the same as upstream `main`
   at `236aff9a`.
   - **What happens:** the client runs phases 1 to 3, detects the wrong PIN after
     phase 3 (`serverchallengeresp`), and stops, as every Moonlight client does.
     It then sends `/unpair`, which LuminalShine does not route, so it gets a 404.
   - **Evidence, client log:** `pairing ended state=PIN_WRONG`, then 409
     `IN_PROGRESS` on each retry.
   - **Evidence, host log:** the log ends at "Pairing PIN delivered; awaiting
     client proof" (`src/nvhttp.cpp:2499`).
   - **Effect:** every new phase 1 from the same client gets 409 `Pairing exchange
     already in progress` at once (`src/nvhttp.cpp:2227-2250`), until the session
     expires 5 minutes after its last update (`kPairingSessionTtl`,
     `src/nvhttp.cpp:918`). Restarting the app does not help; restarting
     LuminalShine clears it.
   - **What Borealis shows:** "the host holds another session (it expires after
     5 minutes)", with no restart.
   - **Decision:** the owner chose to leave Borealis as it is, and to fix the host
     in Sprint 16 (proposed), as a contribution to LuminalShine. Fix A and Fix C
     are in the [roadmap](../roadmap.md#sprint-16-secops). An independent review
     recommended against a `/unpair` cancel route. Option D, sending phase 4 with
     an invalid secret after a wrong PIN, is not chosen.
   - **Not filed upstream yet.** The host-side table of the roadmap has the row.

## 16. Results: Sprint 11

This section holds the results of the Sprint 11 device round, which ran on
2026-10-22 from 05:16 to 07:20. The steps are those of
[section 3.24](#324-sprint-11-checks), numbered as in the table below. The round
ran over ADB on build `d5af7bfc`, on the Google TV Streamer only. The S22 was not on
ADB, so every S22 item is left for the owner. The paired host showed as Offline on
the TV, so the stream, the Online dot and the cover count did not run (P1). Every
visual verdict is `owner check`. The final checks of the phase ran at `fc6a64ae`,
after the round; see [Final checks](#final-checks).

### Setup

- Build: `app-debug.apk` from `d5af7bfc`, the phase head at the time. The About pane
  on the TV shows `0.0.0-dev (commit d5af7bfc89a5)`. The package is
  `io.github.magalz.borealis.debug`, and `adb install -r` on the TV at 05:17:51
  returned `Success`.
- Host: LuminalShine 26.10.0-beta.1, as the round brief says. The host process ran
  on the PC. The app does not show that string.
- Devices:
  - the Google TV Streamer, Android 14 (API 34), 1920 x 1080 at 320 dpi, on ADB for
    the whole round;
  - the Samsung Galaxy S22 (SM-S901E), API 36 per the brief, not on ADB. Its connects
    were refused at 05:17 and at 07:17. It was not installed, its settings were not
    changed and it was not locked by the round.
- Hosts on the TV: the paired host card reads Offline, so the app shows no version for
  it. The same PC shows as a second card, "Found on this network / Not paired". Its
  Host details read `Host software: Sunshine-compatible, version 7.1.431.-1`,
  `Status: Online`, `Paired: No` and `Ports: HTTP 47989, HTTPS 47984`.
- Start values on the TV, restored at the end and read back from the panes:
  - Appearance: font IBM Plex Sans; accent Violet; background Aurora curtains at
    100 %; card density Comfortable; glass tint 40 %; glass blur 8 dp.
  - Accessibility: Reduce motion Follow system; Transparency Standard; thicker focus
    outline off; text size 100 %; text spacing Normal; High contrast off;
    colour-blind palette off.
- Warm-up: every group and pane of Settings was walked twice before anything was
  measured.
- How the screen was read:
  - `uiautomator dump`, parsed to text, bounds and state. Compose gives the full text,
    not the text as drawn, so the "ends in ..." check only catches literal ellipses.
    The round therefore also took screenshots of the panes and of the host cards for
    each font and spacing. They are kept outside the repository.
  - Frame times from `dumpsys gfxinfo <package> framestats`, with 120 frames per
    window, the most that `framestats` keeps. On a 60 Hz output the intervals alternate
    between 33.3 and 50 ms, so the table gives the mean interval where the median would
    flip.

### Numbering

The round numbered its own rows 1 to 12. Those numbers do not match section 3.24, so
the table below uses the numbers of section 3.24:

- round 2 (text size) is step 1; round 3 (fonts) is step 2; round 5 (background
  intensity) is step 5; round 6 (text spacing) is step 8; round 7 (card density) is
  step 9; round 8 (About on a TV) is step 6; round 12 (Online dot) is step 4;
- round 4 (accent colour) is step 3, except for the segmented control and the
  snackbar, which are in round 9 (stream chrome) and which need a stream;
- round 1 (install and start), round 10 (frame time) and round 11 (the icon) have no
  step in section 3.24, so the table shows them as `-`;
- section 3.24 step 7 (the Sprint 10 follow-ups) was not run in the round, and step 10
  (sleep) is the end state of the round.

### Results

One row per item, with the step of [section 3.24](#324-sprint-11-checks) that it
belongs to.

| Step | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| - | Install and start | TV | pass | Cold start 5226 ms (`am start -W`). `logcat -b crash` is empty after the start and at the end of the round (0 lines). |
| - | Install and start | S22 | not run | Not on ADB. The connects were refused at 05:17 and 07:17. |
| 1 | Body text size | TV | pass (bounds); look owner check | All 10 groups and their panes were walked: 0 text nodes outside their parent and 0 overlaps. Body text nodes are 42 px tall at 320 dpi, the 16 sp line box (15 sp would give 39 px). Secondary values are 38 px (14.5 sp). The 13 sp smallest text was not measured. Input, Gamepad, Appearance and Accessibility scroll with the focus. The 10 groups fit with no scrolling. The Stream focus outline is not clipped at the top (screenshot). |
| 1 | Handheld text sizes | S22 | not run | Not on ADB. Owner check, in landscape with a pad. |
| 2 | Font choice | TV | pass (bounds); look owner check | The picker lists 7 fonts, each with a purpose line: IBM Plex Sans, Atkinson Hyperlegible Next, Luciole, Lexend, Andika, B612 and System font. For each font: Hosts 5 of 5 focus targets reached, Settings 10 of 10 groups, Appearance 7 rows, About 4 rows; 0 text nodes outside their parent, 0 overlaps. Nothing is cut in the screenshots. The only text that ends in "..." is the "Checking..." status label, which is not a truncation. See O1 for Luciole. |
| 2 | Licences of the fonts | TV | pass | The round recorded the OFL texts for IBM Plex, Atkinson Hyperlegible Next and Mono, Lexend, Andika, and B612 and B612 Mono, and the CC BY 4.0 credit of Luciole. The NOTICE file is not a device check: the phase review at `d5af7bfc` confirmed that it credits every bundled font. |
| 2 | Monospaced codes font | TV | not run | Not in the round's recorded checks. The look is an owner check: PIN, addresses, chips, the performance overlay and the adb command. |
| 2 | Font choice | S22 | not run | Not on ADB. |
| 3 | Accent colour, rail and font picker | TV | pass; the white bar is an owner check | Violet, Teal and White each give a clear selection. The rail's Settings item and the selected group carry the accent tint. In the font picker the selected row has an accent fill, its name in the accent colour and a check mark, and stays distinct from the focused row. With White, a white bar marks the selected rail item and the selected group (screenshot). The background aurora stays violet with Teal and White. |
| 3 | Segmented control and snackbar | TV | not run | The only segmented control is in the in-game menu, which needs a stream. A stream needs a paired online host, which is P1. The snackbar was not raised: no trigger was available without a side effect. |
| 3 | Accent colour | S22 | not run | Not on ADB. |
| 4 | Online dot shadow | TV | not run | No paired host is online on the TV (P1). |
| 4 | Online dot shadow | S22 | not run | Not on ADB. |
| 5 | Background intensity, brightness | TV | pass | Mean luma of the background beside the panes, with the 99th percentile in brackets, at 100, 75, 50 and 25 %. Aurora curtains: 17.6 (36), 17.0 (31), 16.8 (26), 16.3 (21). Northern lights clip: 20.9 (76), 19.8 (64), 18.0 (47), 16.9 (30). The clip kept playing: 120 frames in each 5 s window at every step. With High contrast on, over the clip: 16.7 (25) at all four steps, so the steps change nothing. |
| 5 | Readability at 25 % | TV | owner check | The round kept screenshots of the glass panels at 100 % over the clip and at 25 %. The readability verdict is the owner's. |
| 5 | Background intensity | S22 | not run | Not on ADB. |
| 6 | About: Source code and Privacy policy | TV | pass | Each opens a dialog with the title, a QR code, "Scan with your phone to open this link.", the URL and Close, with the focus on Close (screenshot). Back and Close both return the focus to the link. `topResumedActivity` stays Borealis's LibraryActivity, so no other app came to the front. Open-source licences opens an in-app screen, and Back returns to its link. |
| 6 | QR code size and quiet zone | TV | owner check | Visual. The round did not judge the code at a distance. |
| 6 | QR code scanned with the S22 camera | S22 | not run | Not on ADB, and the scan needs the owner's phone. |
| 7 | Sprint 10 follow-ups (rail footer) | TV | not run | Not in the round's steps. Section 3.23, step 6, covers the footer. |
| 8 | Text spacing, Wide and Wider | TV | pass (bounds); look owner check | B612 at Wide and at Wider, then Andika at Wider: Hosts 5 of 5, Settings 10 of 10, Appearance 7, About 4, with 0 problems. The gaps between words are clearly wider than the gaps between letters (crops of Source code and Open-source licences). At Wider, "Privacy and diagnostics" wraps to two lines. With Andika and Wider the group list no longer fits: it scrolls with the focus, and every group stays reachable. See P2 for Wider with Compact or Dense. |
| 8 | Value rows, slider values and text fields at Wider | TV | not run | Not in the round's recorded checks. |
| 8 | Stream keyboard, performance overlay and narrow phone at Wider | S22 | not run | Not on ADB. |
| 8 | Text spacing | S22 | not run | Not on ADB. |
| 9 | Card density, host cards | TV | pass (widths and padding); look owner check | Host card width: Comfortable 432 px and Compact 318 px, with a 24 px gap, so 3 and then 4 columns. Dense also gives 4. Dense card padding is 24 px (12 dp), against 36 px (18 dp) at Compact. The grid starts at the same place in both (left 508, top 296). The focus outline is not clipped on either card at Dense (screenshot). |
| 9 | Covers per row | TV | not run | No paired host is online (P1), so the TV shows no app list. Expected from section 3.24: 3 hosts and 5 apps at Comfortable, 4 hosts and 6 apps at Compact and Dense. |
| 9 | Dense cover size, row heights and focus kept on a density change | TV | not run | Not in the round's recorded checks. |
| 9 | Card density | S22 | not run | Not on ADB. The Running badge at the largest text in Dense is an owner check. |
| 10 | TV asleep at the end | TV | pass | The TV is on Home and asleep at 07:17 (`mWakefulness=Asleep`). |
| - | Frame time, default look | TV | pass | Two 8 s windows of 120 frames each. Hosts p50 29.5 / 29.5 ms, p90 30.1 / 29.9 ms; Settings p50 34.5 / 34.5 ms, p90 34.8 / 34.9 ms. With Andika, Wider and Dense: Hosts p50 29.3 / 29.4 ms, p90 29.7 / 29.8 ms; Settings p50 34.3 / 34.4 ms, p90 34.6 / 34.7 ms. Mean interval 41.3 to 41.7 ms (24 fps). The Sprint 10 references are 34.8 ms (Hosts) and 37.0 ms (Settings) at p50; both are lower now. |
| - | The round icon | TV | owner check | "Borealis Debug" shows as a round icon in "Your apps": a deep violet sky with stars, two aurora bands and a dark mountain line. The upper band is teal (sampled #4e908e, hue about 178 degrees, so not green) and the lower band is violet (screenshot). The home screen shows no banner, only the round icon. |

The stream steps (the in-game menu of step 3 and the stream checks in the owner
items), the Online dot on the TV (step 4), the covers (step 9) and every S22 item
were not run in the round. Each of them is an owner item below, or waits for the
host to be fixed (P1).

### Problems found

#### P1. TV: the paired host shows Offline while the host runs

This blocks the Online dot (step 4), the covers (step 9) and the stream menu (step 3).
It is a finding of the round, not a check of section 3.24.

- What: the paired host card on the TV stays Offline, and its polls fail every time.
  The same PC shows as a separate card, "Found on this network / Not paired", which
  the app reports as Online.
- Details:
  - The Host details of the paired card list its stored addresses, one of them an IPv6
    address on port 65050.
  - The host process listens on TCP 47984, 47989, 47990 and 48010. Its
    `sunshine.conf` sets no port and no name.
  - The discovered card reads "Paired: No" and "Ports: HTTP 47989, HTTPS 47984".
  - So the pairing record on the TV no longer matches the host as it runs now. A
    port or identity change on the host since pairing, or the 26.10.0-beta.1 update,
    would explain it. The round did not check further, because that would mean
    changing pairing or host settings.
- Steps to reproduce: wake the TV, open Borealis and look at Hosts. Open the menu on
  each card (Menu key), then Details.
- Logcat (masked; `Borealis.UPSTREAM`, `logger=com.limelight.LimeLog`):

  ```text
  I Borealis.UPSTREAM: Starting parallel poll for <host> (<ip>, null, null, [<ipv6>]:65050)
  I Borealis.UPSTREAM: Parallel poll for <host> returned address: null
  W Borealis.UPSTREAM: <host> is offline (try 2)
  I Borealis.UPSTREAM: Starting parallel poll for <host> (<ip>, null, null, [<ipv6>]:47989)
  I Borealis.UPSTREAM: Parallel poll for <host> returned address: <ip>
  ```

  The first three lines are the paired card; the last two are the discovered card.
- Owner: check the host and its paired clients in the LuminalShine web UI, and pair
  the TV again from the "Not paired" card if needed. The expected result is one card,
  Online.

#### P2. TV: with Wider at Compact or Dense, words on the host cards break in the middle

- What: at Compact and at Dense with Wider text spacing, the second card's host name
  breaks inside a word. That name is 14 characters with one hyphen.
  - With Andika and Wider at Compact, the IPv4 address also wraps before its last
    digit, and the "HEVC 10-bit" chip wraps to two lines inside its pill.
  - Nothing is cut, and every line stays visible, but a split address reads wrong.
  - Not seen with IBM Plex at Normal and Compact, nor with B612 at Wider and
    Comfortable. Seen with IBM Plex at Wider and Compact (name only), and with Andika
    at Wider and Dense (name only).
- Steps to reproduce: Settings, Accessibility, Text spacing, Wider. Then Appearance,
  Card density, Compact (or Dense). Open Hosts. Optionally set Appearance, Font, Andika.
- Logcat: none. It is a layout effect, and nothing is logged.
- Verdict: the owner's. It may be the accepted cost of Wider in a 4-column card.
- Status at `fc6a64ae`: not re-run (see Final checks).

### Observations

These are not counted as problems.

- O1. Luciole draws tight lines. In Luciole the text lines are 28 to 31 px tall, against
  42 px in IBM Plex at the same size. Two-line descriptions in the panes sit close
  together. Nothing overlaps or is cut. The owner judges this at 3 m (section 3.24,
  step 2).

### Notes on the round

These are not app problems.

- At about 06:00 the TV went to sleep through HDMI (CEC, `mLastSleepReason=hdmi`). The
  round woke it with `KEYCODE_WAKEUP`, and the app process stayed alive.
- Between 06:42 and 06:51 Back presses on the Hosts screen closed Borealis, and the
  home screen then started the ambient screensaver. The round relaunched Borealis and
  left Hosts through the rail from then on. The two density settings applied in that
  time were set again and measured again.

### Owner items

The owner ran a 22-step script of the round, which is not in the repository. These
items are the ones the round did not run, with the step of section 3.24 they match.

- Host (P1): check the host and pair the TV again if needed, so that one card shows
  Online. The stream and Online dot items below depend on it.
- TV, fonts at 3 m with Wider (steps 2 and 8): all 7 fonts, and the Luciole line
  spacing (O1).
- TV, TalkBack on the font picker (not in section 3.24): the owner turns TalkBack on
  and off, and each font reads its name and purpose line once.
- TV, stream with an online host (not in section 3.24): the "Starting" screen in B612
  with Wider; then the in-game menu and Quit, which must end the host app and leave
  nothing running on the PC. Keep the stream under one minute.
- TV, the in-game menu with each accent (step 3): the segmented control with Violet,
  Teal and White, and a snackbar if one is easy to raise.
- TV, the Online dot shadow with High contrast on and off (step 4).
- TV, the covers per row at each density (step 9): 5 apps at Comfortable, 6 at Compact
  and Dense.
- TV, the verdict on P2 (steps 8 and 9).
- TV, the round icon: the upper band is teal, not green (no step in section 3.24).
- S22, steps 1 to 9 of section 3.24 in portrait, over USB or wireless ADB as the owner
  chooses. The owner script gives these portrait counts: 1 host card and 3 covers per
  row at Comfortable, then 2 and 4 at Compact and Dense. These are the values of
  `hostGridColumns` and `appGridColumns` for a phone narrower than 600 dp.
- S22, the Online dot and the Running badge at 4 columns (steps 4 and 9).
- S22, TalkBack on the font picker (not in section 3.24).
- S22 and TV, the QR codes of the About dialog, scanned with the S22 camera (step 6).
- S22, the system font at its largest size with Wider in Borealis, on Hosts, Settings
  and About (not in section 3.24; the owner checks it).
- Both devices, at the end: set Borealis back to the start values listed in Setup,
  press Home, put the TV to sleep (step 10), and lock the S22.

### Final checks

- The final checks of the phase ran at `fc6a64ae`. The build changed after the round
  at `d5af7bfc` only by the review fixes:
  - `09325b4d5` keeps the ink of a host card off its border at Wide and Wider, by
    keeping room at the end of the card's text (`HostCard.kt`, Wide and Wider only),
    with `6dee8f401` as its test;
  - `d1c51da30` and `d4764781d` correct KDocs and documentation.
- The host card change was not measured on a device. The TV host-card checks at Wide
  and Wider (steps 8 and 9) were taken at `d5af7bfc`. They are repeated with the
  owner's check on the final build.
- The change keeps the ink off the border. It does not change how a word breaks, so
  P2 is not fixed. It waits for the owner's decision.
- The results of the final checks at `fc6a64ae`, on Windows:
  - the Android gate (`spotlessCheck detekt lint testDebugUnitTest :build-logic:test
    assembleDebug licensee koverXmlReportBorealis`): BUILD SUCCESSFUL;
  - the four-module Roborazzi verify: BUILD SUCCESSFUL;
  - `python -I -m unittest discover -s design/accessibility`: 327 tests OK;
  - markdownlint with the CI globs: 0 issues in 124 files.

### Part 2, morning of 2026-10-22, build `257357fb`

This part ran after part 1 on the same day, on the TV and the S22, on build
`257357fb`. The owner's UAT of that build followed it, and its decisions are in the
next subsection. Part 3 (build `29b40741`) and the owner's final UAT come after that.
The labels P1, P2 and O1 to O4 keep the numbering of part 1, so P1 here is the
update of part 1's P1.

#### Setup

- Build: `257357fb`, `app-debug.apk` (SHA-256 starts `723b4e75cdbf31a8`), package
  `io.github.magalz.borealis.debug`, versionName `0.0.0-dev`, versionCode 1. About on the
  S22 reads `0.0.0-dev (commit 257357fb0a4b)`.
- Install with `adb install -r`, both `Success`: TV 07:59:41 to 07:59:49, S22 07:59:49
  to 07:59:53.
- Devices:
  - the Google TV Streamer, API 34, 1920 x 1080 at 320 dpi, over wireless debugging;
  - the Samsung Galaxy S22 (SM-S901E), API 36, 1080 x 2340 at 480 dpi, over USB as
    `<serial-s22>`, in portrait (`ROTATION_0`) for every action.
- S22 lock: the phone dozed after the install and showed its lock screen. The keyguard
  is secure, but the trust agent kept the device unlocked. The round ran
  `wm dismiss-keyguard`, which opened it with no PIN, pattern or biometric. Nothing was
  typed.
- Host state, as the app shows it, the same on both devices:
  - the paired host is Online in one card, with "Paired: Yes" and
    `Host software: Sunshine-compatible, version 7.1.431.-1` (the app does not show the
    26.10.0-beta.1 string);
  - a second card is the old pairing record, Offline or "Checking…" (P1).
- Start values, the same on both devices, restored at the end and read back from the
  panes:
  - Appearance: font IBM Plex Sans; accent Violet; background Aurora curtains at 100 %;
    card density Comfortable; glass tint 40 %; glass blur 8 dp;
  - Accessibility: Reduce motion Follow system; Transparency Standard; thicker focus
    outline off; text size 100 %; text spacing Normal; High contrast off; colour-blind
    palette off.
- Warm-up: the TV walked every Settings group and pane twice by keys. The S22 read the
  Appearance, Accessibility and About sections in every pass. No frame time was measured.
- Method notes:
  - Compose gives uiautomator the full text, so the screenshots were checked for "…" and
    for cut text. Every screenshot was deleted after it was viewed.
  - Logcat from both streams is kept masked, and a check found no host name, IPv4, IPv6
    or MAC address in it.
- The step numbers in the table are the round's own. They are not the numbers of
  [section 3.24](#324-sprint-11-checks).

#### Results

| Step | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| 1 | Install and start | TV | pass | Cold start 5503 ms (`am start -W`). `logcat -b crash` is empty at the start and at the end. No `FATAL EXCEPTION` or ANR for the package since 07:59. |
| 2 | Host state (P1) | TV and S22 | pass, with a leftover | The paired host is Online in one card. The old pairing record is a second card, Offline. Nothing was re-paired or forgotten. |
| 3 | Stream chrome, B612 and Wider | TV | pass for the text; look owner check; stream too long (D1) | One dump of the Starting screen at 08:30:06, before the first frame at 08:30:14.9: "Starting Desktop", the four steps, the 15 s note and Cancel. At Wider with B612, "Preparing the display" is 436 px wide and "Waiting for the first frame" 576 px, and both fit. At Normal the same texts are about 300 and 370 px. In-game menu, then Quit and its confirmation: `/cancel` answered `cancel=1`, the Library came back at 08:31:18.9 and Apps read "Nothing running". Font and spacing were set back afterwards. |
| 4 | Segmented control, each accent | TV | pass for the menu and Quit; look owner check | Teal (stream A) and White (stream B): the menu opened, Quit and its confirmation worked, and `/cancel` answered `cancel=1` both times. Both devices then read "Nothing running". No capture was taken. |
| 5 | Online dot | TV | owner check (described) | The dot is 20 px (10 dp). The shadow takes the glass from about 22 luma to 17 at the dot's edge, and the glass level returns about 8 px (4 dp) out: a thin, faint dark ring. With High contrast on, the ring is flat, as designed. High contrast was set back to Off. |
| 6 | P2 on this build | TV | still present; verdict owner | IBM Plex at Wider: the name breaks inside a word after 10 characters at Compact and after 11 at Dense. Andika at Wider and Compact: the name breaks after 9 characters, the IPv4 address wraps before its last digit, and the "HEVC 10-bit" chip takes two lines. No ink comes near the card border; the text ends 28 to 41 px from the card's edge. The fix of `09325b4d5` holds. |
| 7 | Covers per row | TV | pass | Comfortable: covers 250 px with a 24 px gap, 5 per row. Compact and Dense: 204 px, 6 per row. The host has two apps, so the right edge of a row was not tested. The focus outline is not clipped at any density. "Steam Big Picture" shows as "Steam Big P…" at Compact and Dense (known limitation). |
| 8 | Install and start | S22 | pass | Cold start 1144 ms (LAUNCHER intent). `logcat -b crash` is empty at the start and at the end. |
| 9 | Fonts | S22 | pass | All 7 fonts. For each: Appearance 7 rows, Accessibility 7 rows and the Privacy link, About 4 rows, Hosts 2 cards with Add host and Settings. 0 text outside its parent and 0 overlaps. Nothing is cut; only "Checking…" ends in "…". "Atkinson Hyperlegible Next" wraps to two lines as a value. Luciole draws tight lines (O1). See O2. |
| 10 | Accent, font picker | S22 | pass for the picker row; White bar not run | The selected row of the font picker shows its accent fill, name and check mark for Teal, Violet and White. The White bar is drawn only on the rail, the two-pane Settings groups and the segmented controls. In portrait none of them shows outside a stream, so the bar was not checked. |
| 11 | Text spacing | S22 | pass | Normal, Wide and Wider on Hosts, Settings and About: 0 problems, and every row was reached. Word gaps are clearly wider than letter gaps at Wide and Wider. Nothing is cut. At Wider, the Text size description ends close to its "100 %" value. |
| 12 | Card density, portrait | S22 | pass | Host cards: 1 per row at Comfortable (984 px); 2 per row at Compact and Dense (474 px, gap 36). Covers: 3 per row at Comfortable (304 px); 4 per row at Compact and Dense (219 px, 73 dp). Text inset of a card: 42 px at Comfortable and Compact, 30 px at Dense. The two cards of a row differ in height (229 and 351 px at Compact), see O4. |
| 13 | Running badge at 4 columns | S22 | pass for the badge; the "running" state is not in the dump | On the host's Apps at Compact, the S22 header reads "Desktop running" while the TV streams. A capture of the Desktop cover shows the dot in its ring in the bottom-start corner, uncut. The state description "Running" is not exported by uiautomator, so TalkBack has to confirm it. TV stream B: started 09:16:40, first frame 09:16:52, Quit sent 09:17:12, Library back 09:17:20. |
| 14 | Online dot | S22 | owner check (described) | The dot is 30 px (10 dp). The shadow takes the glass from about 30 luma to 18 or 19 at the dot's edge, and the glass level returns about 15 px (5 dp) out. It is clearly more visible than on the TV. |
| End | Settings and state | Both | pass | Settings restored and read back. Home on both. TV `mWakefulness=Asleep` at 09:22. S22 locked (`KEYCODE_SLEEP`, `Dozing`, `isKeyguardShowing=true`). |

#### Problems found

##### P1 (update). The host is Online, but the old pairing record stays as a second card

- What: both devices list two cards for the PC. The current pairing is Online. The record
  from before the host changed has another, shorter name and the same IPv4 address. It
  polls a stored IPv6 address on port 65050 and stays Offline or "Checking…".
- Effect: a dead card stays on Hosts. Its menu has Forget.
- Steps to reproduce: open Hosts, then each card's menu (Menu key on the TV), then
  Details.
- Logcat (masked; `Borealis.UPSTREAM`):

  ```text
  I Borealis.UPSTREAM: Starting parallel poll for <host> (<ip>, null, null, [<ip6>]:65050)
  I Borealis.UPSTREAM: Parallel poll for <host> returned address: null
  W Borealis.UPSTREAM: <host> is offline (try 1)
  ```

- Owner: decide whether to Forget the old card on each device. The round did not do it,
  because Forget removes pairing data. Whether it was done later is not recorded.

##### P2. With Wider at Compact or Dense, host names still break inside a word (`257357fb`)

- What:
  - IBM Plex at Wider: at Compact the name breaks after 10 of its 14 characters, and at
    Dense after 11. It never breaks at its hyphen.
  - Andika at Wider and Compact: the name breaks after 9 characters, the IPv4 address
    wraps before its last digit, and the "HEVC 10-bit" chip takes two lines.
  - No ink comes near the card border.
- Steps to reproduce: Settings, Accessibility, Text spacing, Wider. Then Appearance, Card
  density, Compact (or Dense). Open Hosts. Optionally, Appearance, Font, Andika.
- Logcat: none. It is a layout effect.
- Verdict: the owner's. The decision is in the UAT below. Part 3 fixed it on the offline
  cards at `29b40741` (its P2).

##### D1. Round deviation: stream A ran about 76 s, over the one-minute limit

- The driver waited for the first-frame line with a `logcat -T` time filter that was
  quoted wrongly. Every poll failed, so it waited through 40 polls before it opened the
  menu.
- Timeline: the stream started at 08:30:03, the first frame came at 08:30:14.9, Quit was
  sent at 08:31:12 and the Library was back at 08:31:18.9. That is about 57 s of video.
- The driver was fixed before stream B, which took 40 s.
- Both streams ended with the in-game menu's Quit. The host answered `cancel=1` both
  times, and nothing was left running.

#### Observations

These are not counted as problems.

- O1 (part 1, also on the S22). Luciole draws tight lines. Its two-line descriptions in
  Settings nearly touch the row title above them. Nothing overlaps in the bounds, and
  nothing is cut.
- O2. On the S22, choosing a font collapses the other open Settings sections
  (Accessibility and About), and only Appearance stays open. The UAT below records that
  it was not reproduced, and a guard test was added.
- O3. After Quit, the stream screen stays up about 6 to 7 s before the Library returns.
  The host ends the stream at once (logged as "Connection terminated: -1"), but it answers
  `/cancel` about 6.5 s later. The app holds the Quitting phase until that answer, as its
  code intends. The owner's later UAT raised no issue with it.
- O4. The S22's host cards in one row had different heights at Compact and Dense. The
  rule that the cards of a row share a height covered the TV only. The UAT below extends
  it to every form factor.

#### Notes on the round

- The screenshots taken for P2 and for the look passes show host names and addresses.
  They were deleted after they were viewed, and none is kept in the repository.
- My first TV dump printed the old card's name raw, because its status "Checking…" was not
  yet recognised by the masker. The masker was fixed at once. No output of it is in a file.
- Old dump files from earlier rounds remain on the devices and were left untouched. They
  may hold host names. This round's own dumps were removed after each read.
- End state: both devices on Home. The TV is asleep, and the S22 is locked. The Borealis
  settings are back at the start values on both.

### Owner UAT of build `257357fb`, 2026-10-22

The owner ran the user acceptance test (UAT) of build `257357fb` on the TV and the S22
after part 2. The decisions below came from it. Each was built and merged before part 3.
They are recorded in [ADR 0058](../adr/0058-appearance-and-reading-options.md).

1. The accent adapts to the colour-blind palettes. Violet becomes a pale rose under
   Red-green and a lighter violet under Blue-yellow. White's soft accent gets a trace of
   cyan under Blue-yellow. Teal does not change.
2. With Wider, Compact and Dense keep the Comfortable columns (P2).
3. The host cards of a row share the tallest height on every form factor (O4).
4. The TV's Online shadow is black and a little wider.
5. Key captions at Wider draw at Wide.
6. Wider is drawn as Wide from a combined font scale of 2.0, with a dialog. Layout fixes on
   Pairing (TV), the rail footer and the Audit row came first.
7. The keyboard chip threshold stays at a quarter.
8. O2 was not reproduced, in Robolectric or on the S22. A guard test was added.

### Part 3, evening of 2026-10-22, build `29b40741`

This part ran after the UAT above, on the phase head `29b40741`. The host showed Offline on
both devices for the whole part, while the host PC was on a Wi-Fi network with the Windows
"Public" profile (P3). No stream was started, as the round's brief asked, so the stream and
covers checks that need an online host were not run here.

#### Setup

- Build: `29b40741`, the phase head, `app-debug.apk` (SHA-256 starts `075b7a28a324f4fe`),
  package `io.github.magalz.borealis.debug`, versionName `0.0.0-dev`, versionCode 1.
  Nothing was built for the round.
- Install with `adb install -r`, both `Success`: TV 21:51:55 to 21:52:05; S22 22:38:18 to
  22:38:24. The S22 was not touched before 22:37.
- Devices:
  - the Google TV Streamer, API 34, 1920 x 1080 at 320 dpi, over wireless debugging, system
    `font_scale` 1.0;
  - the Samsung Galaxy S22 (SM-S901E), API 36, 1080 x 2340 at 480 dpi, over USB as
    `<serial-s22>`, system `font_scale` 0.8, in portrait (`ROTATION_0`). The scripts
    checked the rotation and the focused window before every tap, key press and capture,
    and refused to act when Borealis was not focused.
- S22 lock: at 22:37 the phone was awake on its launcher with no keyguard showing. Nothing
  was dismissed and nothing was typed.
- Start values on the TV, read from the panes at 21:53:
  - Appearance: font IBM Plex Sans; accent Teal; background Aurora curtains at 100 %;
    card density Dense; glass tint 40 %; glass blur 8 dp;
  - Accessibility: Reduce motion Follow system; Transparency Standard; thicker focus outline
    off (its own value, which the row shows as on while High contrast is on); text size
    100 %; text spacing Normal; High contrast on; colour-blind palette off.
- Start values on the S22, read at 22:39:
  - Appearance: font System font; accent Violet; background Aurora curtains at 100 %; card
    density Comfortable; glass tint 40 %; glass blur 8 dp;
  - Accessibility: Reduce motion Follow system; Transparency Standard; thicker focus outline
    on; text size 100 %; text spacing Normal; High contrast off; colour-blind palette off.
- Warm-up: on the TV, every Settings group and pane was walked twice by keys (22:01 to
  22:06), and the values were read back unchanged. On the S22, Appearance and Accessibility
  were read in full before the checks. No frame time was measured.
- Method notes:
  - Columns come from the bounds: (right edge minus the left of the first card, plus the
    gap) divided by (card width plus gap). On the TV the right edge is that of the "2 hosts"
    label, at 1852 px.
  - Colours are sampled from `screencap` images: the colour of the strongest ink in the
    label's box, and the near-white column of the White bar. Every full screenshot was
    deleted after it was viewed. The six kept images show no host name and no address.
  - High contrast was set Off on the TV for check 5, because it was On at the start.

#### Results

| Step | Item | Device | Result | Evidence and notes |
| --- | --- | --- | --- | --- |
| 1 | Install and start | TV | pass | Cold start 5115 ms (`am start -W`, LEANBACK_LAUNCHER). `logcat -b crash` is empty at the start and at 22:37:14. No `FATAL EXCEPTION` or ANR for the package. |
| 2 | Columns follow Wider, Hosts | TV | pass | Host cards: Normal and Dense 318 px, gap 24, so 4 per row. Normal and Compact 318 px, 4 per row. Wider and Compact 432 px, 3 per row. Wider and Dense 432 px, 3 per row. Andika, Wider and Compact 432 px, 3 per row. The grid starts at 508 px in every case, and the cards of a row have the same height. |
| 2 | Columns follow Wider, Apps | TV | not run (P3) | The covers need the Apps of an online host. The host was still Offline at 23:43. |
| 3 | P2 again | TV | pass for names and addresses; codec chip not run (P3) | IBM Plex at Wider, Compact and Dense, and Andika at Wider and Compact: each name and each address is on one line, and the longer name ends 52 to 87 px from the card's right edge. No word breaks in the screenshots. An offline card draws no codec chips, so the chip was not checked. |
| 4 | Online dot on a TV | TV | not run (P3) | No card was Online and no Apps title could be opened, from 21:52 to 22:37 and again at 23:43. |
| 5 | Accents under the palettes | TV | pass against the tokens; look owner check (O5) | Rail item, group and font picker row. Violet under Red-green and Blue-yellow, White under Blue-yellow, and Teal under all three palettes. In each case the three labels have the same ink. The values are in O5 and match `AccentPaletteVariantsTest`. Crops are kept outside the repository. |
| 6 | No limit dialog at the TV's sizes | TV | pass | `font_scale` 1.0. Text spacing Wider, then Text size 130 %: no dialog. Then Wide and Wider again at 130 %: no dialog. The focused window stayed `LibraryActivity`, no text with "limit" or "drawn as" appeared, and the Text spacing row reads "Wider" with no note. Text size was set back to 100 %. |
| 7 | Equal heights | S22 | pass; weak evidence | Compact: 2 cards per row (474 px, gap 36), heights 217 and 217 px. Dense: 2 per row, heights 193 and 193 px. Both cards were offline with the same one-line texts, so the rule was not tested with uneven content here. At Compact with Wider and 130 %, the S22 shows 1 card per row (984 px), heights 252 and 252 px, so the Wider fallback to the Comfortable columns applies on the phone too. |
| 8 | Accents under the palettes | S22 | pass against the tokens (O5) | Font picker, selected row ("System font"): Violet under Red-green, Violet under Blue-yellow, White under Blue-yellow, Teal under Blue-yellow, Red-green and Off, and Violet with no palette. The values are the same as on the TV. |
| 9 | No limit dialog at the S22's sizes | S22 | pass | `font_scale` 0.8. Text spacing Wider, then Text size 130 %: no dialog. Then Wide and Wider again at 130 %: no dialog. The focused window stayed `LibraryActivity`, with no "limit" or "drawn as" text, and the Text spacing row reads "Wider" with no note. |
| End | Settings and state | S22 | pass | Settings read back at 22:54, all at the start values. `logcat -b crash` is empty, with no `FATAL EXCEPTION` or ANR for the package. Home, then `KEYCODE_SLEEP`: `mWakefulness=Dozing` and `isKeyguardShowing=true` (22:55). |
| End | Settings and state | TV | pass | Settings set back and read back from the panes at 23:49, all at the start values. `logcat -b crash` is empty, and no `FATAL EXCEPTION` or ANR for the package. Home, then `KEYCODE_SLEEP`: `mWakefulness=Asleep` at 23:49:30. |

#### Problems found

##### P3. Both host cards are Offline on both devices, caused by the PC's firewall profile

- What: from 21:52 to 22:55 on the S22 and the TV, and again on the TV at 23:43, both cards
  on Hosts showed "Checking…" and then "Offline".
  - The first card is the old pairing record of part 2 (P1). It still polls a stored IPv6
    address on port 65050.
  - The second card is the current pairing. It was Online on both devices in part 2. Its
    Details now read "Status: Offline" and still list its other addresses and a MAC address
    for wake.
- What the round checked, read only: the host process ran on the PC and listened on TCP
  47984, 47989, 47990 and 48010. From the TV, a TCP connection to port 47989 opened, and a
  plain `GET /serverinfo` answered `HTTP/1.1 200 OK`. The host also answered a ping from
  the TV. The paired card's poll still returned no address.
- The round's own guess was a failure of the HTTPS poll with the client certificate, or a
  changed host certificate. That guess was not the cause.
- Cause, found from the host log, the TV's logcat, the Windows firewall rules and the network profile log: the host PC had moved to a Wi-Fi network with the Windows
  "Public" profile. On that profile, the PC's existing firewall rules that block the host's
  web UI from the Internet also blocked TCP 47984 from the local network. So the clients
  showed the host Offline, and pairing stopped at the HTTPS `pairchallenge` step. This is
  the case that [section 1.3](#13-host), step 4 describes.
- Fix, by the owner: the PC was moved back to a Private network. TCP 47984 and 47989 were
  then reachable from the TV, and the hosts came back Online without re-pairing (see the
  final UAT).
- Logcat (masked; `Borealis.UPSTREAM`, `logger=com.limelight.LimeLog`, TV, 21:53):

  ```text
  I Borealis.UPSTREAM: Starting parallel poll for <host> (<ip>, null, null, [<ip6>]:47989)
  I Borealis.UPSTREAM: Parallel poll for <host> returned address: null
  W Borealis.UPSTREAM: <host> is offline (try 0)
  I Borealis.UPSTREAM: Starting parallel poll for <host> (<ip>, null, null, [<ip6>]:65050)
  I Borealis.UPSTREAM: Parallel poll for <host> returned address: null
  W Borealis.UPSTREAM: <host> is offline (try 0)
  W Borealis.UPSTREAM: Auto-discovered PC failed to respond: Name: <host> State: OFFLINE ...
  ```

##### P2. Fixed on offline cards (`29b40741`)

- With Wider at Compact and at Dense, the TV now uses the Comfortable width (432 px, 3 per
  row). No name breaks inside a word, and no address wraps, with IBM Plex and with Andika
  (step 3 above).
- The codec chip could not be checked here, because an offline card draws no chips. The
  final UAT covered it.

#### Observations

- O5. The selection draws in the soft tone of each variant, not in the `accent` hex that
  the brief names.
  - The selected labels and the check mark use `accentSoft`: Violet under Red-green is
    `#FCC3C8`, and Violet under Blue-yellow is `#C8BBFE`. The variant's `accent` shows
    only in the tint of the selected fill.
  - White under Blue-yellow matches the brief: the soft tone is `#F3FCFF`, and the bar is
    `#EFF3FF`. Teal does not change, and its soft tone is `#A2FCE8`.
  - Violet under Blue-yellow (`#C8BBFE`) is close to Violet with no palette (`#C4B5FD`).
    On screen the labels look almost the same, and the pinker tone shows mainly in the
    faint fill.
  - All values match `AccentPaletteVariantsTest`. The owner's final UAT accepted this as
    it is.
- O6 (round note). The first pass of Violet under Blue-yellow on the TV, at about 22:27,
  sampled the Teal colours everywhere, so the accent had not been applied when it was
  sampled. Either the driver's timing or a remote-control input caused it. The second pass,
  at 22:34 to 22:36, measured `#C8BBFE`, and the kept crop comes from it.

#### Notes on the round

- The TV pause and release:
  - At 22:36:29 Borealis left the foreground, and the owner used the TV. Borealis was
    stopped, not finished. The round's driver never sends Home.
  - The accent script's next case then pressed Back when Borealis was not in front, up to
    four times between about 22:36:30 and 22:36:45, before it stopped with an error. Some
    of those presses may have reached the owner's home screen or app.
  - Four app settings were left changed: Accent Violet (start Teal), Colour-blind palette
    Blue-yellow (start Off), High contrast Off (start On) and Card density Compact (start
    Dense).
  - At 23:42 the owner's other app was in front. The round brought Borealis back with the
    launcher intent, and at 23:44, before any change, read the same four values. Nothing
    else had moved, so the Back presses changed no app setting.
  - All the values were set back at 23:49 and read back from the panes. Every Appearance
    and Accessibility value equals its start value.
- Logcat buffers: `logcat -c` ran on the TV at 21:52 and on the S22 at 22:38, before the
  launches. On the S22 it also cleared an older crash record of another app, which was not
  Borealis.
- Host names appear in the round's session transcript only, not in any file. The
  screenshots viewed for step 3 showed names and an address, and every one was deleted. A
  leak check of this file and of the owner's script found no host name, IPv4, IPv6 or MAC
  address. The name hashes that the masker used were deleted afterwards.
- Dump files: the driver removed `/sdcard/x.xml` after every read. It is gone on both
  devices.

### Owner's final UAT on build `29b40741`, 2026-10-22

The owner ran the final UAT script on build `29b40741`, on the TV and the S22. Every item
of the script passed:

| Item | Device | Result |
| --- | --- | --- |
| The hosts are Online again, without re-pairing (P3) | TV and S22 | pass |
| Pairing at Text size 130 % with Wider | TV | pass |
| Covers per row at Wider, with Compact and Dense | TV and S22 | pass |
| The codec chip on one line (part 3, P2) | TV and S22 | pass |
| The black Online shadow on the card and on the Apps title | TV and S22 | pass |
| Equal heights with an online and an offline card | S22 | pass |
| The limit dialog at the S22's largest system font | S22 | pass |
| Key captions at Wider in a stream | TV and S22 | pass |

- The O5 observation was accepted as it is. Violet's selected label under Blue-yellow
  (`#C8BBFE`) looks almost like Violet's own `#C4B5FD`, and the variant shows mostly in the
  fill.
- The owner ran the script on both devices ("tested on both, all OK"), so the table shows
  "TV and S22" where an item applies to both.
- Whether the old pairing card (P1 of part 2) was forgotten is not recorded.

## 17. Results: Sprint 12

This section holds the results of the Sprint 12 device round, which ran on
2026-10-23: on the Samsung Galaxy S22 from 21:04 to 21:17, and on the Google TV
Streamer from 21:23 to 21:55. A follow-up audio test ran on the Streamer from
22:10 to 22:16 the same evening. The steps are those of
[section 3.25](#325-sprint-12-checks). The round tested build `9fe2e6b05`, the
phase commit (the startup log reads `commit=9fe2e6b059fe`). The head at the
time, `0fedee3d0`, adds only docs and `apps/android/borealis/README.md`. The
round used wireless ADB on both devices. The S22 dropped off ADB at 21:17, so its
later items are left for the owner (`owner check`).

### Setup

- Build: `app-debug.apk` built 2026-10-23 20:52 from `9fe2e6b05` (SHA-256 starts
  `e885cfe36949634c`). The package is `io.github.magalz.borealis.debug`,
  `versionName` `0.0.0-dev`, `versionCode` 1. The round did not build anything.
- Old build on the devices: the Sprint 11 build of 2026-10-22, replaced in place.
- Devices:
  - Samsung Galaxy S22 (SM-S901E), API 36. The keyguard was not locked
    (trusted), so `wm dismiss-keyguard` was used. Nothing was typed.
  - Google TV Streamer, API 34.
- Host: LuminalShine 26.10.0-beta.1 on the owner's PC. The app shows the protocol
  version `7.1.431.-1`, not the LuminalShine release. The host binary is dated
  2026-10-20.
- Scope: reduced by the owner during the round. On the S22: the update in place,
  the shortcut counts and one touch stream. On the TV: the full brief, plus other
  stream settings, frame pacing and codec, refresh and reload, the box art cache
  and a cold start after a force-stop.
- Method:
  - Private files were compared by name, size and SHA-256 through `run-as`. The
    full hashes were kept outside the repository.
  - The `Computers` rows were counted in memory, because `sqlite3` is not
    available through `run-as`. Nothing was written to disk and no value was
    printed.
  - Shared preferences: key names with value hashes. Values are shown only for
    the stream settings.
  - UI: masked `uiautomator` dumps, taken outside streams and deleted after
    reading. Screenshots: crops of the TV covers only, deleted after viewing.
  - Logs: the app's logcat lines, masked into the kept files (host names and
    IPv4, IPv6 and MAC addresses replaced). See P2 for what the raw captures
    hold.
- Files and identity, before and after the update:
  - `files/uniqueid`, `files/client.crt`, `files/client.key`: the same on both
    devices, before and after the install.
  - The default preferences file, `GlPreferences.xml`, `BorealisStream.xml`,
    `HiddenApps.xml` and the datastore: the same. The cache folders, read on the TV
    only: the same. On the TV the
    default preferences file was still byte-identical to the baseline at the end
    of the round, after the settings were changed and set back.
  - `databases/computers4.db`: 2 rows in `Computers` on both devices. On the S22 the
    hash differs from the baseline, which was taken before the A4 run; that run
    rewrites host state. On the TV the hash is the same across the install.
  - Shortcuts (dynamic / pinned): 1 / 0 before and after on both devices. No
    shortcut was pinned.

### Results

One row per item, with the step of [section 3.25](#325-sprint-12-checks) that it
belongs to.

| Step | Device | Result | Evidence and notes |
| --- | --- | --- | --- |
| 1 Upgrade in place | TV | pass | `adb install -r`: Success, 21:26:01 to 21:26:11. Identity and settings unchanged. Still 2 hosts; the paired host is Online, and its app list opened with no PIN asked. |
| 1 Upgrade in place | S22 | pass | `adb install -r`: Success, 21:06:25 to 21:06:48. Identity and settings unchanged. Still 2 hosts; the paired host is Online, and its app list opened with no PIN asked. |
| - First start | TV | pass | Cold start after the install. Crash buffer 0. The startup log reads `commit=9fe2e6b059fe`. |
| - First start | S22 | pass | Cold start 947 ms. Crash buffer 0. |
| 1 Pinned shortcut and TV channel | TV, S22 | owner check | No shortcut was pinned on either device, so the pinned-shortcut item was not tested. The TV channel row was not read (no permission to query it). |
| 2 Discovery (mDNS) | TV | pass | On each cold start: `NSD: Service discovery started`, the machine appeared and resolved, and the mDNS address lines, all on `Borealis.UPSTREAM` (`logger=com.limelight.LimeLog`), as on the old build. |
| 2 Discovery (mDNS) | S22 | not run | The S22 showed the same 2 hosts at start. Its discovery lines were not read (reduced S22 scope). |
| 2 Add host manual entry | TV, S22 | owner check | Not run: it needs the host's address. |
| 3 App list | TV | pass | `/applist` answered 200 with two apps. No parse error. |
| 3 App list | S22 | pass | The app list of the paired host opened with no PIN asked. |
| 3 Box art | TV | pass, as designed | The Apps grid draws monogram tiles on both builds; the grid does not use box art. The first stream cached a 600 x 800 PNG (8231 B) in `cache/boxart`. Step 3 expects box art for each app, but the owner confirmed on 2026-10-24 that the grid keeps the initials until Phase 3 (roadmap, Phase 3). |
| 3 Box art | S22 | not run | Not read (reduced S22 scope). |
| 4 Short stream: launch, Resume, Quit | TV | pass | Launch 21:29:12, first frame 21:29:34.5, Quit ended with `cancel=1` at 21:30:11.4, and the Library came back. Resume: OK on Desktop at 21:33:34.7, `<resume>1</resume>` at 21:33:50.6, first frame 21:33:52.6, with no 409 and no conflict screen; Quit at 21:34:02. No crash in the crash buffer. |
| 4 Short stream: launch, Resume, Quit | S22 | owner check | Stream 1 (21:09:54) reached its first frame at 21:10:09.98, and ended with Home at 21:12:32, not with Quit (D1). Stream 2 (21:15:58) had no first frame before the S22 left ADB (D2). Resume and Quit were not run on the S22; the menu, Resume and Quit are owner items. |
| 4 Input: keyboard, pad, touch | TV | owner check | Keys sent by `adb` (`DPAD_RIGHT`, `SHIFT_LEFT`) caused no crash, and the stream went on. A physical keyboard and a pad were not tested. |
| 4 Input: keyboard, pad, touch | S22 | owner check | No touch was sent (D1, D2). |
| 4 Performance overlay | TV | owner check | Logcat matches the set 1280x720 at 59 fps (cap FPS on a 60 Hz display). The bitrate is not in logcat, and the overlay was not read on screen. |
| 4 Performance overlay | S22 | owner check | Not read (D1, D2). |
| - 1080p, 30 fps, 25 Mbps | TV | pass | mode=1920x1080x30, decoder 1920x1080 at 30 fps, about 31 frames a second received. The bitrate is not in logcat. Set back to 720p and 60 fps, which returned the bitrate to Automatic. |
| - Frame pacing "Lowest latency", codec H.264 | TV | pass | mode=1280x720x60 with no "Adjusting FPS" line; `video/avc` on the low-latency decoder. Set back to "Balanced with fps limit" and Automatic. |
| - Refresh and reload | TV | pass | On Apps, `/applist` was fetched again every 30 s. Hosts were polled, and NSD discovery restarted after a stream. |
| - Cold start after a force-stop | TV | pass | COLD, 5374 ms. Both hosts back, the paired one Online, discovery lines present, crash buffer 0. |
| 4 Audio in the stream (added note) | TV | pass in the follow-up | The round's streams had a silent PC and received no audio. In the follow-up, with a sound playing on the PC, the first audio packet arrived after 6300 ms from the stream start. Not a regression: see P1. The audible output is an owner check. |
| 4 Audio in the stream (added note) | S22 | owner check | The round's stream had a silent PC and logged no `Received first audio packet` line. Audio with a sound playing was not tested on the S22. See P1. |
| 5 Logs: LimeLog reaches BorealisLog | TV | pass | `Borealis.UPSTREAM` lines with `logger=com.limelight.LimeLog`, and `Borealis.APP`, `Borealis.CORE` and `Borealis.HOST` at process start. |
| 5 Logs: LimeLog reaches BorealisLog | S22 | not run | The S22 lines were scanned for secrets (next row), but the bridge was not checked on its own. |
| 5 Logs: no PIN, token, key or certificate | TV, S22 | pass | A scan of 1457 `Borealis.*` lines found no unredacted `rikey`, `rikeyid`, `salt`, `clientcert`, pairing challenge, `uniqueid`, PIN or PEM block. They show `[REDACTED]`. |
| 5 Logs: no address or host name | TV, S22 | fail, P2 | Addresses and host names appear in clear in `Borealis.UPSTREAM` lines. The old build does the same. See P2. |
| 6 Owner UAT: pairing, Forget, Wake-on-LAN | one device | owner check | Not run in the round. |
| 7 End: TV asleep | TV | pass | Home, then `KEYCODE_SLEEP`: `mWakefulness=Asleep` at 21:55:37. Crash buffer 0. |
| 7 End: S22 | S22 | owner check | Unreachable over ADB since 21:17, so not locked or sent Home by the round (D3). |

### Problems found

#### P1. No audio in the round: not a regression

- What: no stream of the round received audio. On the TV, all five streams logged
  `Borealis.COMMONC: No audio traffic was ever received from the host!`. On the S22,
  its stream of about 2.5 minutes logged no `Received first audio packet` line.
  The video worked in every stream that reached a first frame: all five TV streams
  and S22 stream 1.
- Launch parameters: the same as in the Sprint 11 capture (`localAudioPlayMode=0`,
  `surroundAudioInfo=196610`, audio port 48000, audio channel config `0xC`).
- Cause: the host sends audio only while something plays on the PC, and the PC was
  silent during the round's streams.
  - In LuminalShine, a capture timeout writes silence only when the client asks for
    `continuousAudio` (`src/platform/windows/audio.cpp:569`). `/launch` reads that
    argument with a default of 0 (see the [host contract](../host-contract/luminalshine.md#14-audio),
    section 14), and silent frames are
    skipped (`src/audio.cpp:276`). Neither the Sprint 11 build nor the Sprint 12
    build sends `continuousAudio`.
- A/B test on the TV, 2026-10-23 22:10 to 22:16:
  - With the PC silent, neither the Sprint 11 build nor the Sprint 12 build
    received audio.
  - With a Windows sound playing on the PC, both received their first audio packet
    about 1 s after the sound started: "Received first audio packet after 6200 ms"
    (Sprint 11) and "after 6300 ms" (Sprint 12), counted from the stream start.
  - The launch parameters were byte-identical. Both builds logged about 35
    `Too much pending audio data` lines when the sound started.
  - The identity was unchanged across all five snapshots: `uniqueid`,
    `client.crt`, `client.key`, the preferences, the datastore, the host database
    rows (2) and the shortcuts (1 dynamic, 0 pinned).
- Verdict: not a regression. The Sprint 12 build behaves as the Sprint 11 build
  does. Asking for `continuousAudio` is an optional feature in the backlog. To check
  audio, play a sound on the PC during the
  stream (see the note in [section 3.25](#325-sprint-12-checks), step 4).
- Steps to reproduce: start Desktop on the TV, play a sound on the PC, then quit.
  Check logcat for `Received first audio packet`.
- Audio on the S22 with a sound playing: checked in the follow-up of 2026-10-24
  (pass).

#### P2. The upstream logger writes host addresses and host names in clear

- What: `Borealis.UPSTREAM` lines (LimeLog, bridged by `JulBridge`) carry IPv4 and
  IPv6 addresses and host names. For example:
  - `Starting parallel poll for <host> (<ip>, null, null, [<ipv6>]:47989)`;
  - `https://<ip>/launch?...`;
  - `NSD: Machine appeared: <host>`.
- Scale: in the round's log captures, scanned before they were masked, IPv4 or IPv6
  addresses appear in clear in 519 `Borealis.UPSTREAM` lines, and host names in 632
  lines. The old build does the same, so this is not a regression of the conversion.
  Secrets are redacted.
- Against the checklist: [section 3.25](#325-sprint-12-checks), step 5, expects no
  address or host name in the log. The logging README
  (`apps/android/borealis/README.md`) says address masking is off by default, and
  that host names in free text, the upstream `LimeLog` lines included, are not
  masked even with "Mask addresses" on.
- Decision (owner, 2026-10-24): local addresses may stay in the logs; public
  addresses, and those of internet overlays such as Tailscale, may not. Host names
  stay visible. The change is planned in Sprint 16 (roadmap, Sprint 16).

### Deviations

- D1 (S22 stream 1):
  - The round's rotation check read `ROTATION_90` as `ROTATION_9`, so no touch was sent.
  - The TV's menu key sequence (Back, 14 times Down, OK, Right, OK) did not quit on
    the phone. The keys reached the window, but the stream went on; its OK, Right,
    OK went to an unknown menu item.
  - Checked afterwards: no persisted setting changed. Whether a key reached the PC
    (for example through "Send keys") is not known.
  - The stream ended with Home at 21:12:32, not with Quit. It ran 2 min 40 s, over
    the one-minute limit.
  - The host restarted 20 s after the client disconnected. That is the known host
    behaviour after a client disconnect (NortheBridge/luminalshine issue 198).
    Apps then read "Nothing running".
- D2 (S22 touch): the second stream (21:15:58) showed no first frame within 30 s.
  The S22 then dropped off ADB, and no key was sent. At 21:22 the host had no
  session. Touch on the S22 is untested: owner script.
- D3 (S22 end): the S22 was not locked by the round. Its last known state, at 21:16,
  was Borealis with the keyguard dismissed. The owner should check it and lock it.
- Audio follow-up: the A/B test of P1 played a Windows sound on the PC during its
  streams, on 2026-10-23 from 22:10 to 22:16. The round itself had the PC silent.
  For the A/B, the Sprint 11 build was installed over the Sprint 12 build on the TV,
  then the Sprint 12 build was installed again (22:13), with the data kept and the
  identity snapshots compared at each step. The TV ended on the Sprint 12 build and
  asleep.
- D4 (S22 in-game menu by keys): see the follow-up on the S22 below.

### Observations

- O1: the host takes 16 to 20 s to answer `/launch` or `/resume` (for example, start
  21:29:12, answer 21:29:32.6). The Sprint 11 round took about 11 s from start to
  first frame. The first video packet then comes after 1.1 to 1.4 s.
- O2: the first TV stream (720p59, static desktop) received about 20 frames a second.
  Later streams received 59 to 61 (720p60) and 31 (1080p30). This is likely the host
  skipping unchanged frames. It is not a client error.
- O3: each cold start logs `Starting parallel poll for null` and `Parallel poll for
  null returned address: null` for the mDNS-discovered host. This is upstream
  behaviour, not a failure: `pollComputer` logs `activeAddress` before it copies the
  polled details in, in the old Java and in the Kotlin. No "Auto-discovered PC failed
  to respond" line follows.
- O4: after Quit, the host answers `/cancel` 15 to 17 s after it ends the stream. The
  Library returns only then. In the Sprint 11 round this took about 6.5 s.

### Owner items

The follow-up on the S22 of 2026-10-24 ran the S22 items of this list; the items
left after it are listed at its end. The owner has a script for these items, which is
not in the repository. These are
the items the round did not run, with the step of section 3.25 they match.

- S22 (owner check): wireless ADB may need to be re-enabled. Look at the screen, and
  leave any stream with Back. Expected: no stream running, and the paired host
  Online (step 1 and D3). Then lock the phone.
- S22 touch (step 4, D2): in a short stream, with the phone in landscape, tap and
  drag. Expected: the PC cursor moves, and taps become clicks. Quit from the in-game
  menu.
- S22 in-game menu (step 4, D1): open the menu with Back. Expected: "Back to stream"
  has the focus, and Quit ends the stream.
- Gamepad (step 4): a short stream with a pad, if one is at hand. Expected: buttons
  and the D-pad work, and B closes the menu.
- Overlay, S22 and TV (step 4): in a stream, menu, Overlay, Detailed. Expected:
  720p, about 60 fps, and the bitrate of the setting. Set the overlay back to Off
  before Quit.
- Audio, S22 and TV (P1): play a sound on the PC during a stream on each device.
  Expected: the `Received first audio packet` line, and the sound on the device.
- TV channel (step 1): the Borealis entry on the Google TV home screen should open
  the Desktop stream on the right host. The round could not read the provider.
- Pinned shortcut (step 1): pin a game shortcut on the S22 (press and hold the
  Borealis icon in the launcher), open it, and check that it starts the right
  stream. No shortcut is pinned on either device today.
- Keyboard (step 4): a short stream with a physical keyboard, if one is at hand.
- S22 Resume (step 4): start a stream, press Home, reopen the app and resume the
  running app. Expected: the stream comes back with no conflict screen.
- Pairing on one device (section 3.25, step 6): a wrong PIN first, then the right one, on
  `26.10.0-beta.1`. Forget, then pair again. Add host by address. Wake-on-LAN with
  the PC asleep.
- Clean-up: put back any setting that was changed. The TV's values at the end of the
  round match the baseline. Press Home on both devices and put the TV to sleep (the
  TV was left asleep after the audio follow-up).

### Follow-up on the S22 (2026-10-24)

The owner turned wireless debugging on again, and the S22 items of the round ran on
2026-10-24 from 10:12 to 10:28, on the same build (`9fe2e6b05`). The PC was not silent:
the audio check played a Windows sound. A small test window on the PC, with one button
that logs each click, was the target of the touch check: before each tap the PC cursor
was moved onto the button. In the first stream the button's position was out of date,
so that one tap clicked once at another spot of the PC's screen. The phone's clock ran
about 2.4 s ahead of the PC's; the times below are the phone's.

| Step | Result | Evidence and notes |
| --- | --- | --- |
| 1 Identity | pass | `uniqueid`, `client.crt`, `client.key`, the preferences and the datastore are the same as after the update and across the follow-up. `computers4.db` and two framework files changed (host state and framework writes). |
| 1 Pinned shortcut | pass for the pin; owner check to open it | "Add to home screen" in the app's actions, then the launcher's confirmation: the package has 1 pinned and 1 dynamic shortcut. The new icon was not on the visible home page, so opening it is left to the owner. |
| - First start | pass | Cold start 685 ms. The startup log reads `commit=9fe2e6b059fe`. Crash buffer 0. |
| 2 Discovery (mDNS) | pass | `NSD: Service discovery started`, the machine appeared and resolved, and the mDNS address counts, all on `Borealis.UPSTREAM` with `logger=com.limelight.LimeLog`. |
| 3 App list | pass | Desktop and Steam Big Picture. After a stream left with Home: "Desktop running", with the Running badge. |
| 4 Short stream: launch | pass | Desktop: the host answered `/launch` 11 s after the tap, faster than in O1, and the first frame came 13 s after the tap. HEVC on the low-latency decoder, in landscape. |
| 4 Input: touch (trackpad) | pass | A one-finger drag moved the PC cursor. In the resumed stream, two taps each logged a click on the test button. A tap in the first stream missed the button and clicked once elsewhere on the PC's screen: the host had added a virtual display for the S22, and the button's position read before the stream was out of date. |
| 4 Audio in the stream | pass | With a Windows sound playing on the PC, `Received first audio packet after 11800 ms` from the stream start, about 0.4 s after the sound began. |
| 4 Resume | pass | `/resume` answered "Resumed existing game session" 10 s after the tap, and the first frame came 2 s later, with no conflict screen. |
| 4 Quit from the in-game menu by keys | fail, D4 | The keys did not reach Quit. See D4. |
| 4 Quit from the Apps screen | pass | A long press on Desktop, "Quit Desktop", then Quit: `/cancel` answered, and Apps read "Nothing running". |
| 4 Performance overlay | owner check | Not read: it shows only on screen, and no capture is taken during a stream. |
| 5 Logs | pass for the bridge and secrets; P2 for addresses | 605 of the 823 `Borealis.*` lines are `Borealis.UPSTREAM` with `logger=com.limelight.LimeLog`. No unredacted secret. IPv4 addresses in 348 and IPv6 addresses in 207 upstream lines (P2). |
| - No crash | pass | Crash buffer 0; no `FATAL EXCEPTION` or ANR. |

- D4 (S22, first stream): the in-game menu was opened with Back and driven with keys
  (25 times Down, OK, Right, OK; the TV takes the same keys with 14 Downs). The keys
  reached the stream window, but the stream did not end. It was left with Home 1 min
  41 s after its first frame, over the one-minute limit. The host ended the session and
  did not restart. On the TV these keys reach Quit; on the phone they did not, here and
  in D1. Whether a pad or a keyboard on the
  phone reaches Quit is an owner check.

Owner items left after the follow-up (the evening results below close most of them):

- The S22 in-game menu with a pad, if one is at hand (D4).
- The performance overlay on the TV.
- The TV channel.
- A physical keyboard and a pad in a stream.
- Hearing the sound on each device: the logs show the audio packets.
- Pairing, Forget, Add host and Wake-on-LAN (step 6).

End of the follow-up: the S22 was left on its home screen, unlocked, with the owner at
hand; the test window on the PC had closed itself. Crash buffer 0.

Owner results on the S22, 2026-10-24 (11:06 to 11:07, phone clock, read from logcat
afterwards):

- The performance overlay: pass (owner).
- The in-game menu by touch: Back, then Quit and Quit, ended the stream; the host
  answered `/cancel`. Pass (owner).
- The pinned Desktop shortcut:
  - Opened with Borealis not running, it showed the toast "Computer is offline" and
    started nothing. The shortcut's launch request fires once the app list loads from
    the cache, about 0.6 s after the app started (0.9 s after the tap), before the
    first poll gives the host an address: `ServerHelper.doStart` then sees no active
    address. The first poll answered 0.7 s later. A second try inside the app started
    the stream.
  - Opened with Borealis running, it started the stream screen at once. Pass.
  - The first case is a bug of the Library's launch request path, not of the Sprint 12
    conversion. The request came in `636fb7a6b` (2026-10-15), and `7b0ab7380`
    (2026-10-16) seeds the app list from the cache, so the list is ready before the
    first poll. The Java at `2f321dc77` read the hosts from the database with the same
    unknown state and no address (read in the code, not run on that build). The owner
    put the fix in Sprint 15 (roadmap, Sprint 15).
- Leaving a stream while it starts: about 2 s after the second shortcut start, the
  stream activity closed and the phone went to Recents and another app. About 7 s
  later the system destroyed the app's sockets, so the `/launch` still waiting for
  the host failed with "Software caused connection abort". The host rolled the
  launch back after its timeout. On this phone Samsung's freezer froze the app from
  then on, and `dumpsys netpolicy` read afterwards showed its network blocked as
  `APP_BACKGROUND`. This is the system's background rule; no client defect was found
  in this round.

Results of the evening of 2026-10-24 (owner and orchestrator, same build):

- Performance overlay on the TV: pass (owner).
- Sound: heard on both devices (owner). On the S22 during the follow-up's sound. On the
  TV in a stream at 23:18 with a Windows sound playing on the PC: the first audio
  packet after 5700 ms, the stream left with Quit, crash buffer 0. As in the A/B of
  P1, the log has many `Too much pending audio data` lines when the sound starts.
- A physical keyboard and a pad in a stream: pass, with one finding below (owner).
  The owner answered the item "the S22 in-game menu with a pad"; the device and
  whether the pad reached Quit in the menu were not recorded.
- TV channel: not applicable on this device. The Google TV Streamer shows no
  third-party preview-channel rows (section 11, the TV channel spike of Sprint 6).
  The posters were checked through the provider instead, as in the Sprint 8 checks
  (3.21, step 7), all pass, and the crash buffer stayed at 0:
  - the poster read through `PosterContentProvider` has the same md5 as the cache file
    read with `run-as`;
  - an app ID that is not a number gives `FileNotFoundException: not an app id`;
  - a host ID with `../` in it gives `FileNotFoundException: not a host id`;
  - an app with no cached poster gives `FileNotFoundException`.
- Add host by hand on the S22: pass (orchestrator). The address of the paired host was entered
  without showing it. The app recognised the known host: no duplicate card, and the
  paired host stayed Online. The message "`<host>` added. Pair it to see its apps."
  shows for any added host, a paired one too. The owner put the wording in Sprint 15.
- Wrong PIN on 26.10.0-beta.1 (owner): the same as before Sprint 12. After a wrong
  PIN, a retry shows a new PIN for a moment, then the "try again" screen. This is
  the same symptom as before Sprint 12, and it matches the host keeping the failed
  pairing session (409 on a retry; roadmap, Sprint 16, the LuminalShine wrong-PIN
  fix). The logcat of this try was not read.
- New finding (owner): in the in-game menu, Left or Right, on the D-pad or the
  analog stick, makes the focus outline disappear, and it does not come back until
  the menu is closed (B or Back) and opened again. It happens often with the analog
  stick. The owner put it in Sprint 15, with the in-game menu work. D4 may have the
  same cause (its key sequence had Right in it); where the focus goes is not yet
  known.

Owner items left: Forget and pair again, Wake-on-LAN with the PC asleep, and whether a
pad on the S22 reaches Quit in the in-game menu (D4).

Results of the night of 2026-10-24 (owner, with the S22 logs read afterwards):

- A pad on the S22 reaches Quit through the in-game menu (owner). D4 is closed for a
  pad; the keys sent by `adb` stay as recorded.
- Unpair (the owner's word), then pair again, on the TV: pass (owner).
- Pairing on the S22 after the wrong PIN. The owner stopped trying while the S22 was
  not paired, and found the host paired a few minutes later. Three records were read
  afterwards: the app's own log (`no_backup/logs/borealis.log`, through `run-as`,
  masked), the system's activity history, and the host log. Times are local, on the
  phone's clock unless marked "host"; that night the host's clock ran about 1.4 s
  behind the phone's.
  - 23:22:04 to 23:22:16: an attempt waits for the PIN; a PIN is entered in the host's
    Web UI; the attempt ends as `PIN_WRONG` (the test). The `/unpair` in its teardown
    answers 404, as the host contract says (no `/unpair` route).
  - 23:22:19 to 23:23:37: twelve attempts, each answered 409 `IN_PROGRESS` within
    0.1 s, about 0.7 to 21 s apart, the last four within about 2.3 s. Who started each is not
    recorded.
  - Host 23:23:24: an unpair request is posted to the Web UI's API. Host 23:23:48 to
    23:23:58: LuminalShine quits from the system tray and starts again. Who did it is
    not recorded. This ended the failed pairing session (inferred: the next attempt was
    not answered 409).
  - 23:24:07 to 23:24:23: the phone's screen goes off and on, and the app comes back
    (its polling restarts at 23:24:23.07).
  - 23:24:23: an attempt starts; the host logs "Pairing request awaiting PIN" from the
    S22's address. 13 s later the Web UI posts a PIN ("Pairing PIN delivered"), and at
    23:24:37 all five pairing requests answer 200: `pairing ended state=PAIRED`. The
    app makes a new random PIN for each attempt, so the PIN delivered was the one the
    S22 made for this attempt.
  - 23:24:41 to 23:25:12: the stream screen is open; `/launch` answers at 23:24:50, the
    connection ends at 23:25:06, and `/cancel` answers 200 at 23:25:12. Who started
    it is not recorded.

  So the S22 paired because a PIN was entered in the host's Web UI for its pending
  request (host 23:24:35). By the code, an attempt starts only when the pairing screen
  opens, on "Get new PIN", or once more by itself after `RestartNeeded` (not taken
  here). The log shows one app process through the window, so no state was restored.
  What started the 23:24:23 attempt, 0.8 s after the app came back, is not recorded.
  The owner does not recall entering that PIN. A try the same night, with logcat
  recorded from 23:52 to 00:17, did not reproduce it: after a screen off and on at
  23:59, no attempt started until the next one at 00:04:39. It stays open for a later
  try.
- New findings (owner):
  - Back from the Apps screen to the Hosts screen plays a "shrinking" animation
    (TV and phone), while other screens just fade.
  - After Forget, the host does not come back as "Found on this network" until the
    app is closed and opened again.

  Both are in Sprint 15 (roadmap).

Owner items left: Wake-on-LAN with the PC asleep.

## 18. Results: Sprint 13

This section holds the results of the Sprint 13 device round, which ran on 2026-10-26: on
the Samsung Galaxy S22 from about 10:33 to 12:27, and on the Google TV Streamer from 12:08
to 12:14. The steps are those of [section 3.26](#326-sprint-13-checks). The round tested
build `9af270c73` (the startup log reads `commit=9af270c7390a`). It found no regression of
Sprint 13. Four items went to the backlog, see
[roadmap, Backlog, not scheduled](../roadmap.md#backlog-not-scheduled), item (d).

### Setup

- Build: `app-debug.apk` built 2026-10-26 09:04 from `9af270c73` (SHA-256 starts
  `a4b7c5f6a8459d9c`). The package is `io.github.magalz.borealis.debug`, `versionName`
  `0.0.0-dev`, `versionCode` 1. The round did not build anything.
- Old build on the devices: the Sprint 12 build of 2026-10-23 (section 17), replaced in
  place.
- Comparison build: at 12:25 the main build (`ded969bdb`, Sprint 12 code with Dependabot
  #79) was installed on the S22 for the Alt+Tab check (finding 4). The Sprint 13 build was
  installed again afterwards.
- Devices:
  - Samsung Galaxy S22 (SM-S901E), API 36, connected over USB.
  - Google TV Streamer, API 34, connected over wireless ADB.
- Host: the host version is not recorded in the round's notes. TODO(verify).
- Method:
  - The owner operated the pads, the keyboard and the touch. The app's logcat was read
    after each item.
  - The raw logs were deleted after the round, because they held host addresses. The
    results below are the owner's results and what the logs showed, with times.
- Corrections to [section 3.26](#326-sprint-13-checks), found in the round:
  - The touch mode defaults to the trackpad, because the setting is on by default. The
    owner's first touch check ran in trackpad mode.
  - The trackpad drag is a press and hold of about 650 ms without moving, then a move. It
    is not a double tap and hold.

### Results

One row per item, with the step of [section 3.26](#326-sprint-13-checks) that it belongs
to. The round's notes numbered the pad items differently (3 to 7 on the S22, 8 to 10 on the
TV). The rows here use the steps of section 3.26, matched by content.

| Step | Device | Result | Evidence and notes |
| --- | --- | --- | --- |
| 1 Update in place | S22 | pass | Installed in place at 10:33. The Library opened. The startup log reads `commit=9af270c7390a`. |
| 1 Update in place | TV | pass | Installed in place at 10:33 (round notes). Launched at 12:08:40 with the `LEANBACK_LAUNCHER` intent. The startup log reads `commit=9af270c7390a`, SDK 34. |
| 2 Touch, absolute (direct) | S22 | fail, backlog 2 | Direct touch set in Settings was used by a new stream (clean run, 12:20, finding 1). Long press and move select text on the PC instead of dragging. Tap, long press and two-finger scroll are not recorded separately. |
| 3 Touch, trackpad (default) | S22 | pass, backlog 1 | Tap, move and two-finger scroll work. The drag is a press and hold of about 0.65 s, then a move. Pointer speed is not recorded. |
| 4 On-screen controller | S22 | pass | Owner: all buttons, the D-pad, the sticks, the triggers, the bumpers, Start, Select, Guide and the 3 s hold work. No app exception in the log. |
| 5 On-screen controller edit | S22 | pass | Owner: the layout edit was kept, and the opacity changed the look. |
| 6 Keyboard and mouse | S22 | pass, backlog 3 | Owner: keyboard and mouse work. The first Tab of Alt+Tab is lost (finding 4). |
| 7 8BitDo Pro 3 (Switch mode) | S22 | pass | Owner: finished. Log: "Nintendo Switch Pro Controller" counted, "Enumerated 1 gamepads", stream launched at 11:09:46, no input error or exception. Details of buttons, rumble and motion: TODO(verify). |
| 7 8BitDo Pro 3 (Switch mode) | TV | pass | Owner: passed (round items 8 to 10, see Open points). Log: the Pro 3 was assigned controller 1 at 12:13:04. |
| 8 8BitDo Ultimate 2 | S22 | pass | Owner: "flawless" in the four modes. Four connects, each assigned controller 0 and released on removal. Rumble reached the pad. No exception. |
| 8 8BitDo Ultimate 2 | TV | pass | Owner: passed (round items 8 to 10). The USB driver counted the receiver and assigned controller 0 at 12:12:20. |
| 9 Gamepad mouse | S22 | pass | Works on both pads, and with both pads in mouse mode at once. A pad that joins while it is on joins (owner). Pointer speed and right-stick scroll: TODO(verify). |
| 9 Gamepad mouse | TV | owner check | Not recorded per step. The owner said round items 8 to 10 all passed (see Open points). |
| 10 Menu chord and quit combo | S22 | pass | Owner: work in every mode. |
| 10 Menu chord and quit combo | TV | owner check | Not recorded per step (see step 9, TV). |
| 11 Two pads at once | S22 | pass | Ultimate 2 took controller 0 and Pro 3 took controller 1. Each number was released on its disconnect, and the other pad kept working (owner: "perfeitamente"). Rumble reached controller 0. |
| 11 Two pads at once | TV | owner check | Controller 0 (receiver) and controller 1 (Pro 3) were both connected at 12:13:04. The unplug of one pad during the stream is not recorded. |
| 12 Connect and disconnect | S22 | pass | Four Ultimate 2 connects. Each disconnect logged "Controller number 0 is now available". A connect during a stream: TODO(verify). |
| 12 Connect and disconnect | TV | owner check | The receiver left at 12:13:51, after the stream had ended at 12:13:45 (observation 1). The reconnect is not recorded. |
| 13 Logs: exceptions | S22 | pass | No exception from `com.limelight.binding.input`, and no `FATAL` line. The app process restarted between items (deviation 2). |
| 13 Logs: exceptions | TV | pass | No exception. |
| 14 End: TV asleep | TV | pass | The TV returned home and was put to sleep. |
| 14 End: S22 | S22 | owner check | The end state of the S22 is not recorded. TODO(verify). |

### Findings

#### 1. Touch setting: a mistake in the test, not a defect

- The first report was that the touch setting did not reach the stream. The stored value
  read at 11:08 was `true` (trackpad), which matched the owner's last choice in Settings.
- The owner's report came from a mistake in the test: another option had been changed.
- The clean run at 12:20 set Touch to Direct in Settings and left it there. The stored
  `checkbox_touchscreen_trackpad` was `false`, and the new stream started in direct touch.
  This is not a defect.

#### 2. Backlog 1: the trackpad drag is a press and hold (S22)

- Device: S22, touch in trackpad mode (the default).
- What happens: a drag starts when the finger stays still for about 650 ms
  (`DRAG_TIME_THRESHOLD` in `RelativeTouchContext.kt`), and then moves. Tap, move and
  two-finger scroll work.
- The owner expected the usual trackpad gesture, a double tap and hold. This is filed as
  a backlog item, not as a bug.

#### 3. Backlog 2: direct touch does not drag like a phone (S22)

- Device: S22, touch in direct mode, set in Settings.
- What happens: a long press and a move select text on the PC instead of dragging, as they
  do on a phone.
- The owner found this hard to reproduce on a desktop. It is filed as "full mobile
  behaviour".
- A choice made in the stream menu lasts for that session only. A new stream returns to the
  trackpad mode, unless Settings was changed (see finding 1).

#### 4. Backlog 3: the first Tab of Alt+Tab is lost (S22)

- Device: S22, keyboard, in a stream.
- What happens: with Alt held, the first Tab is lost, and Windows opens its Alt menu. Tabs
  pressed later, with Alt still held, work. The WindowManager log shows no system window
  taking focus during the streams.
- Comparison, 12:25: the main build (`ded969bdb`) showed the same problem on the S22. The
  problem is therefore not from Sprint 13. The Sprint 13 build was installed again after
  the check.

#### 5. Backlog 4: Home held on the Ultimate 2 acts as Win+Tab (S22)

- Device: S22, 8BitDo Ultimate 2, with or without the gamepad mouse.
- What happens: holding Home makes the PC act as if Win+Tab was pressed. The app sends the
  Guide button for as long as the pad's Mode or Home button is held (`GuideChord`). Nothing
  in the input code maps it to Win+Tab.
- Probably Windows' own long press of the Xbox button. To verify on the host.

### Deviations

1. The S22 stream ended at 12:07:05. The log reads "Control stream received unexpected
   disconnect event", then "Connection terminated: -1". It came right after two Back presses
   and taps in the menu. Whether that was Quit is not recorded. TODO(verify).
2. The S22 app process restarted between items (process 16603, then 849). The logs show no
   `FATAL` line. The cause is not recorded. TODO(verify).

### Observations

1. TV, at 12:13:51, six seconds after the stream ended: "Detected device I/O error" and
   "Rumble transfer failed: -1", then "Removed controller: 0" and "Controller number 0 is now
   available". The USB receiver went away or changed identity. The pad stopped and released
   its number. No exception.
2. With both pads off, the Touch row came back in the stream menu. The row is shown only
   without a pad, which is expected.
3. The S22 log still reads "Using Android O+ native mouse capture". This text is already in
   the Sprint 13 backlog, item (b).
4. S22 rumble on the Ultimate 2: "Rumble on gamepad 0: 0000 7f00", then "0000 0000".

### Owner items and open points

- The owner's details of the Pro 3's buttons, rumble and motion were asked for, but are not
  in the notes. TODO(verify).
- The round's notes numbered the TV items 8 to 10 and the owner said they all passed. The
  steps 7 to 10 on the TV are matched by content. Per-step results for the gamepad mouse and
  the menu chord on the TV are not recorded. TODO(verify).
- Not recorded: pointer speed and right-stick scroll (step 3 and step 9), the unplug of one
  pad on the TV (step 11), a connect during a stream on the S22 (step 12), and the end state
  of the S22 (step 14). TODO(verify).
- The host version is not recorded. TODO(verify).
