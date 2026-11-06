package io.github.magalz.borealis.preferences

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.lang.reflect.Modifier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the name, the type and the field of every preference that `readPreferences` reads (Sprint
 * 18a, BORE-79). Each key is stored alone, under its literal name and not through the settings
 * catalog, and the read must change its own field and no other. The defaults are those of
 * [PreferenceConfigurationReadTest]; the order of the reads and the migrations is in
 * [PreferenceConfigurationReadOrderTest].
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class PreferenceConfigurationEveryKeyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    private fun fieldValues(config: PreferenceConfiguration): Map<String, Any?> =
        PreferenceConfiguration::class
            .java
            .fields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .associate { it.name to it.get(config) }

    private fun store(key: String, value: Any) {
        val editor = preferences.edit().clear()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
            else -> error("$key has no stored type for $value")
        }
        editor.commit()
    }

    @Test
    fun `each key stored alone under its own name changes its own field and no other`() {
        val defaults = fieldValues(PreferenceConfiguration.readPreferences(context))

        for (case in CASES) {
            store(case.key, case.value)

            val actual = fieldValues(PreferenceConfiguration.readPreferences(context))

            assertWithMessage("${case.key} = ${case.value}")
                .that(actual)
                .containsExactlyEntriesIn(defaults + case.changes)
        }
    }

    @Test
    fun `every field of the configuration is changed by one of the keys`() {
        val defaults = fieldValues(PreferenceConfiguration.readPreferences(context))

        assertThat(CASES.flatMap { it.changes.keys }.toSet())
            .containsExactlyElementsIn(defaults.keys)
    }

    /** A key, a value that differs from its default, and the fields that the value changes. */
    private class Case(val key: String, val value: Any, val changes: Map<String, Any?>)

    private companion object {
        fun flag(key: String, field: String, value: Boolean) =
            Case(key, value, mapOf(field to value))

        fun number(key: String, field: String, value: Int) = Case(key, value, mapOf(field to value))

        /**
         * The resolution and the frame rate also change the automatic bitrate: it is that of the
         * stored size and frame rate, 1280x720 at 60 frames per second when one is not stored.
         */
        val CASES =
            listOf(
                Case(
                    "list_resolution",
                    "2560x1440",
                    mapOf("width" to 2560, "height" to 1440, "bitrate" to 40_000),
                ),
                Case("list_fps", "144", mapOf("fps" to 144, "bitrate" to 15_000)),
                number("seekbar_bitrate_kbps", "bitrate", 31_337),
                // The legacy key holds megabits.
                Case("seekbar_bitrate", 20, mapOf("bitrate" to 20_000)),
                Case(
                    "video_format",
                    "forceh265",
                    mapOf("videoFormat" to FormatOption.FORCE_HEVC),
                ),
                Case(
                    "frame_pacing",
                    "smoothness",
                    mapOf("framePacing" to PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS),
                ),
                Case(
                    "analog_scrolling",
                    "left",
                    mapOf("analogStickForScrolling" to AnalogStickForScrolling.LEFT),
                ),
                Case(
                    "list_audio_config",
                    "71",
                    mapOf("audioConfiguration" to MoonBridge.AUDIO_CONFIGURATION_71_SURROUND),
                ),
                number("seekbar_deadzone", "deadzonePercentage", 13),
                number("seekbar_gamepad_mouse_speed", "gamepadMouseSpeed", 77),
                number("seekbar_osc_opacity", "oscOpacity", 33),
                number("seekbar_vibrate_fallback_strength", "vibrateFallbackToDeviceStrength", 42),
                flag("checkbox_stretch_video", "stretchVideo", true),
                flag("checkbox_enable_sops", "enableSops", false),
                flag("checkbox_host_audio", "playHostAudio", true),
                flag("checkbox_disable_warnings", "disableWarnings", true),
                flag("checkbox_multi_controller", "multiController", false),
                flag("checkbox_usb_driver", "usbDriver", false),
                flag("checkbox_flip_face_buttons", "flipFaceButtons", true),
                flag("checkbox_show_onscreen_controls", "onscreenController", true),
                flag("checkbox_only_show_L3R3", "onlyL3R3", true),
                flag("checkbox_show_guide_button", "showGuideButton", false),
                flag("checkbox_enable_hdr", "enableHdr", true),
                flag("checkbox_enable_pip", "enablePip", true),
                flag("checkbox_enable_post_stream_toast", "enableLatencyToast", true),
                flag("checkbox_usb_bind_all", "bindAllUsb", true),
                flag("checkbox_mouse_emulation", "mouseEmulation", false),
                flag("checkbox_mouse_nav_buttons", "mouseNavButtons", true),
                flag("checkbox_unlock_fps", "unlockFps", true),
                flag("checkbox_vibrate_osc", "vibrateOsc", false),
                flag("checkbox_vibrate_fallback", "vibrateFallbackToDevice", true),
                flag("checkbox_touchscreen_trackpad", "touchscreenTrackpad", false),
                flag("borealis_touchscreen_native", "touchscreenNative", true),
                flag("checkbox_absolute_mouse_mode", "absoluteMouseMode", true),
                flag("checkbox_enable_audiofx", "enableAudioFx", true),
                flag("checkbox_reduce_refresh_rate", "reduceRefreshRate", true),
                flag("checkbox_full_range", "fullRange", true),
                flag("checkbox_gamepad_motion_sensors", "gamepadMotionSensors", false),
                flag("checkbox_gamepad_touchpad_as_mouse", "gamepadTouchpadAsMouse", true),
                flag(
                    "checkbox_gamepad_motion_fallback",
                    "gamepadMotionSensorsFallbackToDevice",
                    true,
                ),
            )
    }
}
