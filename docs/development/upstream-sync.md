# Upstream cherry-picks

Procedure for bringing an upstream fix into Borealis. The rules are in
[../policies/upstream-sync.md](../policies/upstream-sync.md). The decision is in
[ADR 0054](../adr/0054-declare-independence-from-moonlight.md) (2026-10-15).

The subtree pull, the hook inventory and the moonlight-common-c fork-tag
procedure ended with ADR 0054. This page no longer describes them.

Upstream fixes arrive only by cherry-pick. There are two parts: moonlight-android
(the Android app under `apps/android/`) and moonlight-common-c (the native
protocol library under `core/third_party/moonlight-common-c/`).

An area that the Java to Kotlin migration has converted no longer takes
upstream cherry-picks directly. Its upstream fixes are ported by hand into the
Kotlin code. See the [roadmap](../roadmap.md#kotlin-migration-sprints-12-to-14).
The conversion rules are in [ADR 0059](../adr/0059-convert-the-inherited-java-to-kotlin.md).

Sprint 12 converted or removed these Java paths under `apps/android/app/src/main/java/`.
A cherry-pick of an upstream commit that touches one of them does not apply to the
Borealis code, so port the fix by hand into the Kotlin file. The converted code is in
`apps/android/app/src/main/kotlin/`, at the same package path:

- `com/limelight/LimeLog.java`, `PosterContentProvider.java`, `binding/PlatformBinding.java`
- `binding/crypto/AndroidCryptoProvider.java`
- `computers/ComputerDatabaseManager.java`, `ComputerManagerListener.java`,
  `ComputerManagerService.java`, `IdentityManager.java`
- `computers/LegacyDatabaseReader.java`, `LegacyDatabaseReader2.java`,
  `LegacyDatabaseReader3.java` (removed, not converted)
- `discovery/DiscoveryService.java`
- `grid/assets/DiskAssetLoader.java`
- `nvstream/ConnectionContext.java`, `NvConnection.java`, `NvConnectionListener.java`,
  `StreamConfiguration.java`
- `nvstream/av/ByteBufferDescriptor.java` (removed, not converted),
  `nvstream/av/audio/AudioRenderer.java`, `nvstream/av/video/VideoDecoderRenderer.java`
- `nvstream/http/ComputerDetails.java`, `HostHttpResponseException.java`,
  `LimelightCryptoProvider.java`, `NvApp.java`, `NvHTTP.java`, `PairingManager.java`
- `nvstream/input/ControllerPacket.java`, `KeyboardPacket.java`, `MouseButtonPacket.java`
- `nvstream/mdns/JmDNSDiscoveryAgent.java`, `MdnsComputer.java`, `MdnsDiscoveryAgent.java`,
  `MdnsDiscoveryListener.java`, `NsdManagerDiscoveryAgent.java`
- `nvstream/wol/WakeOnLanSender.java`
- `preferences/GlPreferences.java`, `PreferenceConfiguration.java`
- `ui/GameGestures.java`, `StreamView.java`
- `utils/CacheHelper.java`, `Dialog.java`, `NetHelper.java`, `ServerHelper.java`,
  `ShortcutHelper.java`, `TvChannelHelper.java`, `UiHelper.java`, `Vector2d.java`

Sprint 13 converted these Java paths under `apps/android/app/src/main/java/`. No whole
file was removed. The removals of the sprint are methods, fields and constants (see the
roadmap). The converted code is in `apps/android/app/src/main/kotlin/`, at the same
package path:

- `com/limelight/binding/input/ControllerHandler.java`, `KeyboardTranslator.java`
- `binding/input/capture/AndroidNativePointerCaptureProvider.java`,
  `AndroidPointerIconCaptureProvider.java`, `InputCaptureManager.java`,
  `InputCaptureProvider.java`
- `binding/input/driver/AbstractController.java`, `AbstractXboxController.java`,
  `UsbDriverListener.java`, `UsbDriverService.java`, `Xbox360Controller.java`,
  `Xbox360WirelessDongle.java`, `XboxOneController.java`
- `binding/input/touch/AbsoluteTouchContext.java`, `RelativeTouchContext.java`,
  `TouchContext.java`
- `binding/input/virtual_controller/AnalogStick.java`, `DigitalButton.java`,
  `DigitalPad.java`, `LeftAnalogStick.java`, `LeftTrigger.java`, `RightAnalogStick.java`,
  `RightTrigger.java`, `VirtualController.java`, `VirtualControllerConfigurationLoader.java`,
  `VirtualControllerElement.java`

The Java that remains is in the Sprint 14 area of the roadmap. Upstream fixes
for those files still come by cherry-pick until their area is converted.

## 1. Prepare

1. Make sure `main` is up to date and your working tree is clean.
2. Keep the `moonlight-android` remote configured. Add it once if it is missing:

   ```sh
   git remote add moonlight-android https://github.com/moonlight-stream/moonlight-android.git
   ```

3. Fetch and look at what changed since the last harvest:

   ```sh
   git fetch moonlight-android --tags
   git log --oneline <last-harvested>..<upstream-ref>
   ```

   The last harvested commit is `b48494cb` (moonlight-android 12.2, the same
   commit as the Borealis subtree at the time of ADR 0054).
4. Read the upstream release notes. Note changes to dependencies, Gradle or SDK
   versions, and to the moonlight-common-c version.
5. Check the [compatibility table](#compatibility-table). Add a row if the
   harvest changes what was tested.

## 2. moonlight-android

1. Create a branch for the harvest from `main`.
2. Pick each commit with the subtree strategy option:

   ```sh
   git cherry-pick -Xsubtree=apps/android <sha>
   ```

   If a commit does not apply cleanly, port the change by hand into the
   Borealis file.
3. Resolve conflicts by keeping the Borealis code and taking the fix. Do not
   reformat or restyle whole files, so the diff stays reviewable.
4. Keep the duplicated toolchain values in step. When a harvest changes one of
   them, change the other in the same PR:
   - AGP: `apps/android/build.gradle` and `agp` in
     `apps/android/gradle/libs.versions.toml`.
   - `compileSdk`: `apps/android/app/build.gradle` and `BorealisBuild` in
     `apps/android/build-logic`.
   - NDK: `ndkVersion` in `apps/android/app/build.gradle`, `NDK_VERSION` in
     `BorealisBuild` in `apps/android/build-logic`, and the `ndk;<version>`
     package in `.github/workflows/ci.yml` and `.github/workflows/release.yml`,
     and the `libc++_shared` entry of `apps/android/sbom/native-components.cdx.json`,
     which carries the NDK version. If it changes, update the SDK install
     command in [building](building.md) too.
5. If new upstream code adds Android Lint warnings, fix them. The lint baseline
   follows the one rule in [building](building.md#lint-baseline).
6. Run the checks from `apps/android/` (see [building](building.md)):

   ```sh
   ./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test assembleDebug licensee koverXmlReportBorealis
   ```

7. Open one PR per harvest. The description lists the upstream commits, what
   was ported by hand, conflicts and how they were resolved, and the test
   results. The owner reviews and merges it.
8. If a dependency or licence changed, update [NOTICE](../../NOTICE) and check
   the [dependency policy](../policies/dependencies.md).

## 3. moonlight-common-c

The vendored copy lives at `core/third_party/moonlight-common-c/`. Its README,
[core/third_party/README.md](../../core/third_party/README.md), records the
source commit, the licences, the Borealis patch and the upstream commits that
were and were not harvested.

1. Add the upstream remote once:

   ```sh
   git remote add upstream-common-c https://github.com/moonlight-stream/moonlight-common-c.git
   git fetch upstream-common-c
   ```

2. Export the commits to harvest as patches. The base is the last commit
   recorded in `core/third_party/README.md`:

   ```sh
   git format-patch -o <patch-dir> <base>..<sha>
   ```

3. Apply the patches into the vendored path. This gives one commit per upstream
   commit, with the upstream author and subject kept (no `sync:` prefix):

   ```sh
   git am --directory=core/third_party/moonlight-common-c <patch-dir>/*.patch
   ```

4. Append `Harvested from moonlight-stream/moonlight-common-c@<full sha>.` to the
   message of each commit, so that every commit names its upstream SHA.
5. Compare the exported symbols of `libmoonlight-core.so` before and after the
   harvest (`llvm-nm -D --defined-only`) and explain every difference in the
   commit that causes it.
6. Update `core/third_party/README.md`, which is the record of harvests: the
   harvested commits, the source commit and the licence lines if they changed.
7. Build and test `borealis-core` from `core/`:

   ```sh
   cmake --preset dev && cmake --build --preset dev && ctest --preset dev
   ```

8. Check the stream path on a device, if the change touches it (see
   [device-validation](device-validation.md)).
9. Update [NOTICE](../../NOTICE) and the native SBOM entry for moonlight-common-c
   if its version or licence changed.

## Compatibility table

Record each harvest or tested pairing here. Rows before ADR 0054 name the
moonlight-common-c fork tag, which is now an archive. From 2026-10-15 the
moonlight-common-c column names the vendored source commit.

| Borealis tree | Moonlight Android | moonlight-common-c | LuminalShine versions tested | Notes |
| --- | --- | --- | --- | --- |
| initial import | 12.2 | fork `magalz/moonlight-common-c` `borealis`, upstream base `874ac95` plus the first full frame deadline patch (tag `borealis/874ac95-1`) | not yet tested | Baseline import. |
| `phase/1-luminalshine-compat` at `d6b3116e` | 12.2 | tag `borealis/874ac95-1` | 26.09.1 | Galaxy S22 (Android 16) and Google TV Streamer (Android 14); results in [device-validation.md](device-validation.md) section 6. |
