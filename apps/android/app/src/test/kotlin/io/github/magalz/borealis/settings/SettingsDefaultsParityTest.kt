package io.github.magalz.borealis.settings

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The defaults of the Kotlin catalog (ADR 0055) equal what the inherited
 * `PreferenceConfiguration.readPreferences` reads from empty SharedPreferences.
 *
 * Three defaults are computed, not stored, and are tested on their own:
 * - the bitrate: absent means automatic, so Java reads [PreferenceConfiguration.getDefaultBitrate];
 * - the motion sensors: Java writes off on Android 12 exactly, and the catalog default stays on;
 * - the performance overlay: the new [Settings.PERF_OVERLAY] has no Java reader, and the old
 *   switch that Java reads is [Settings.PERF_OVERLAY_LEGACY].
 *
 * The language list and the small icon mode are dropped from the catalog and are not compared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class SettingsDefaultsParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @Before
    fun emptyPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun read(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(context)

    /** The Java field that holds each catalog setting, as a catalog-typed value. */
    private fun javaValues(c: PreferenceConfiguration): Map<Setting<*>, Any> =
        mapOf(
            Settings.RESOLUTION to "${c.width}x${c.height}",
            Settings.FRAME_RATE to c.fps.toString(),
            Settings.FRAME_PACING to
                when (c.framePacing) {
                    PreferenceConfiguration.FRAME_PACING_MIN_LATENCY -> "latency"
                    PreferenceConfiguration.FRAME_PACING_BALANCED -> "balanced"
                    PreferenceConfiguration.FRAME_PACING_CAP_FPS -> "cap-fps"
                    else -> "smoothness"
                },
            Settings.STRETCH_VIDEO to c.stretchVideo,
            Settings.AUDIO_CONFIG to
                when (c.audioConfiguration.channelCount) {
                    STEREO_CHANNELS -> "2"
                    SURROUND_51_CHANNELS -> "51"
                    else -> "71"
                },
            Settings.AUDIO_FX to c.enableAudioFx,
            Settings.DEADZONE to c.deadzonePercentage,
            Settings.MULTI_CONTROLLER to c.multiController,
            Settings.USB_DRIVER to c.usbDriver,
            Settings.USB_BIND_ALL to c.bindAllUsb,
            Settings.MOUSE_EMULATION to c.mouseEmulation,
            Settings.ANALOG_SCROLLING to
                when (c.analogStickForScrolling) {
                    PreferenceConfiguration.AnalogStickForScrolling.RIGHT -> "right"
                    PreferenceConfiguration.AnalogStickForScrolling.LEFT -> "left"
                    else -> "none"
                },
            Settings.VIBRATE_FALLBACK to c.vibrateFallbackToDevice,
            Settings.VIBRATE_FALLBACK_STRENGTH to c.vibrateFallbackToDeviceStrength,
            Settings.FLIP_FACE_BUTTONS to c.flipFaceButtons,
            Settings.GAMEPAD_TOUCHPAD_AS_MOUSE to c.gamepadTouchpadAsMouse,
            Settings.GAMEPAD_MOTION_SENSORS to c.gamepadMotionSensors,
            Settings.GAMEPAD_MOTION_FALLBACK to c.gamepadMotionSensorsFallbackToDevice,
            Settings.TOUCHSCREEN_TRACKPAD to c.touchscreenTrackpad,
            Settings.MOUSE_NAV_BUTTONS to c.mouseNavButtons,
            Settings.ABSOLUTE_MOUSE_MODE to c.absoluteMouseMode,
            Settings.SHOW_ONSCREEN_CONTROLS to c.onscreenController,
            Settings.VIBRATE_OSC to c.vibrateOsc,
            Settings.ONLY_L3_R3 to c.onlyL3R3,
            Settings.SHOW_GUIDE_BUTTON to c.showGuideButton,
            Settings.OSC_OPACITY to c.oscOpacity,
            Settings.ENABLE_SOPS to c.enableSops,
            Settings.HOST_AUDIO to c.playHostAudio,
            Settings.ENABLE_PIP to c.enablePip,
            Settings.UNLOCK_FPS to c.unlockFps,
            Settings.REDUCE_REFRESH_RATE to c.reduceRefreshRate,
            Settings.DISABLE_WARNINGS to c.disableWarnings,
            Settings.VIDEO_FORMAT to
                when (c.videoFormat) {
                    PreferenceConfiguration.FormatOption.AUTO -> "auto"
                    PreferenceConfiguration.FormatOption.FORCE_AV1 -> "forceav1"
                    PreferenceConfiguration.FormatOption.FORCE_HEVC -> "forceh265"
                    else -> "neverh265"
                },
            Settings.ENABLE_HDR to c.enableHdr,
            Settings.FULL_RANGE to c.fullRange,
            Settings.POST_STREAM_TOAST to c.enableLatencyToast,
        )

    @Test
    fun `every default of the catalog is what Java reads from empty preferences`() {
        val java = javaValues(read())

        for ((setting, javaValue) in java) {
            assertThat(setting.default).isEqualTo(javaValue)
        }
    }

    @Test
    fun `every catalog entry is compared, or is computed or new and has its own test`() {
        val compared = javaValues(read()).keys

        val uncompared = Settings.all.filter { it !in compared }

        assertThat(uncompared)
            .containsExactly(
                Settings.BITRATE_KBPS,
                Settings.PERF_OVERLAY,
                Settings.PERF_OVERLAY_LEGACY,
                Settings.VERBOSE_LOGGING,
                Settings.MASK_ADDRESSES,
            )
    }

    @Test
    fun `the snapshot of an empty store reads the Java values for every compared setting`() {
        val java = javaValues(read())

        for ((setting, javaValue) in java) {
            assertThat(SettingsSnapshot.DEFAULTS[setting]).isEqualTo(javaValue)
        }
    }

    @Test
    fun `an absent bitrate is automatic and Java computes it from the default resolution and frame rate`() {
        val snapshot = SettingsSnapshot.DEFAULTS

        assertThat(Settings.BITRATE_KBPS.default).isNull()
        assertThat(snapshot.isBitrateAutomatic).isTrue()
        assertThat(read().bitrate)
            .isEqualTo(PreferenceConfiguration.getDefaultBitrate("1280x720", "60"))
        assertThat(snapshot.effectiveBitrateKbps()).isEqualTo(read().bitrate)
        assertThat(read().bitrate).isEqualTo(AUTOMATIC_720P60_KBPS)
    }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowMoonBridge::class])
    fun `on Android 12 Java turns motion sensors off while the catalog default is on`() {
        assertThat(Settings.GAMEPAD_MOTION_SENSORS.default).isTrue()
        assertThat(read().gamepadMotionSensors).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_13], shadows = [ShadowMoonBridge::class])
    fun `on Android 13 and later the motion sensors default is on in Java as in the catalog`() {
        assertThat(read().gamepadMotionSensors).isEqualTo(Settings.GAMEPAD_MOTION_SENSORS.default)
    }

    @Test
    fun `the new performance overlay starts off`() {
        assertThat(Settings.PERF_OVERLAY.default).isEqualTo(Settings.PERF_OVERLAY_OFF)
    }

    @Test
    fun `the automatic bitrate equals Java over every resolution the old screen offered and every frame rate`() {
        for (resolution in RESOLUTIONS) {
            for (fps in FRAME_RATES) {
                assertThat(DefaultBitrate.kbps(resolution, fps.toString()))
                    .isEqualTo(PreferenceConfiguration.getDefaultBitrate(resolution, fps.toString()))
            }
        }
    }

    @Test
    fun `the automatic bitrate of the width, height and frame rate overload equals the string overload`() {
        assertThat(DefaultBitrate.kbps(1920, 1080, 60)).isEqualTo(DefaultBitrate.kbps("1920x1080", "60"))
    }

    private companion object {
        const val STEREO_CHANNELS = 2
        const val SURROUND_51_CHANNELS = 6
        const val ANDROID_12 = 31
        const val ANDROID_13 = 33

        /** 720p at 60 fps: the resolution factor 5 times the frame rate factor 2. */
        const val AUTOMATIC_720P60_KBPS = 10_000

        /** The old screen's list (360p to 4K), a native-like odd size, and sizes beyond both ends. */
        val RESOLUTIONS =
            listOf(
                "640x360",
                "854x480",
                "1280x720",
                "1920x1080",
                "2560x1440",
                "3840x2160",
                "2400x1080",
                "2340x1080",
                "1600x900",
                "1080x1080",
                "320x240",
                "5120x2880",
            )
        val FRAME_RATES = listOf(30, 60, 90, 120, 144)
    }
}
