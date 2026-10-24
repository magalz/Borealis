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
import io.github.magalz.borealis.settings.ShadowMoonBridge
import java.lang.reflect.Modifier
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes what `PreferenceConfiguration.readPreferences` reads from SharedPreferences: every
 * field on empty preferences, the enum readers and their fallbacks, the resolution, the frame rate
 * and the bitrate. It records the behaviour before the Kotlin conversion.
 *
 * The settings parity tests already pin that each catalog key and type reaches its Java field. The
 * writes made while reading, and the migrations, are in [PreferenceConfigurationMigrationTest].
 */
@Suppress("DEPRECATION", "LargeClass")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class PreferenceConfigurationReadTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    private fun read(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(context)

    @Test
    fun `empty preferences give the documented value in every field`() {
        val config = read()

        val actual =
            PreferenceConfiguration::class
                .java
                .fields
                .filterNot { Modifier.isStatic(it.modifiers) }
                .associate { it.name to it.get(config) }

        assertThat(actual).containsExactlyEntriesIn(EMPTY_PREFERENCES_VALUES)
    }

    @Test
    fun `empty preferences give the stereo audio configuration constant itself`() {
        assertThat(read().audioConfiguration).isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
    }

    @Test
    fun `reading empty preferences changes nothing in the file and asks only for the default file`() {
        val recording = RecordingContext(context)

        PreferenceConfiguration.readPreferences(recording)

        assertThat(recording.log).isEmpty()
        assertThat(recording.files.map { it.first }.toSet())
            .containsExactly("${context.packageName}_preferences")
        assertThat(recording.files.map { it.second }.toSet()).containsExactly(Context.MODE_PRIVATE)
        assertThat(preferences.all).isEmpty()
    }

    @Test
    fun `the video format reads each of its four strings`() {
        for ((stored, expected) in
            mapOf(
                "auto" to FormatOption.AUTO,
                "forceav1" to FormatOption.FORCE_AV1,
                "forceh265" to FormatOption.FORCE_HEVC,
                "neverh265" to FormatOption.FORCE_H264,
            )) {
            preferences.edit().putString("video_format", stored).commit()

            assertWithMessage("video_format = $stored").that(read().videoFormat).isEqualTo(expected)
        }
    }

    @Test
    fun `an unknown video format string reads as auto`() {
        for (stored in listOf("", "AUTO", "av1", "h264", "forceh264", " auto")) {
            preferences.edit().putString("video_format", stored).commit()

            assertWithMessage("video_format = '$stored'")
                .that(read().videoFormat)
                .isEqualTo(FormatOption.AUTO)
        }
    }

    @Test
    fun `the frame pacing reads each of its four strings`() {
        for ((stored, expected) in
            mapOf(
                "latency" to PreferenceConfiguration.FRAME_PACING_MIN_LATENCY,
                "balanced" to PreferenceConfiguration.FRAME_PACING_BALANCED,
                "cap-fps" to PreferenceConfiguration.FRAME_PACING_CAP_FPS,
                "smoothness" to PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS,
            )) {
            preferences.edit().putString("frame_pacing", stored).commit()

            assertWithMessage("frame_pacing = $stored").that(read().framePacing).isEqualTo(expected)
        }
    }

    @Test
    fun `an unknown frame pacing string reads as the minimum latency`() {
        for (stored in listOf("", "Latency", "BALANCED", "cap_fps", "smooth", "never-drop")) {
            preferences.edit().putString("frame_pacing", stored).commit()

            assertWithMessage("frame_pacing = '$stored'")
                .that(read().framePacing)
                .isEqualTo(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY)
        }
    }

    @Test
    fun `the analog stick for scrolling reads right, left and none`() {
        for ((stored, expected) in
            mapOf(
                "right" to AnalogStickForScrolling.RIGHT,
                "left" to AnalogStickForScrolling.LEFT,
                "none" to AnalogStickForScrolling.NONE,
            )) {
            preferences.edit().putString("analog_scrolling", stored).commit()

            assertWithMessage("analog_scrolling = $stored")
                .that(read().analogStickForScrolling)
                .isEqualTo(expected)
        }
    }

    @Test
    fun `an unknown analog stick string reads as none while the default string is right`() {
        assertThat(read().analogStickForScrolling).isEqualTo(AnalogStickForScrolling.RIGHT)

        for (stored in listOf("", "RIGHT", "Left", "both", "up")) {
            preferences.edit().putString("analog_scrolling", stored).commit()

            assertWithMessage("analog_scrolling = '$stored'")
                .that(read().analogStickForScrolling)
                .isEqualTo(AnalogStickForScrolling.NONE)
        }
    }

    @Test
    fun `the audio configuration reads stereo, 5,1 and 7,1 as the constants of the bridge`() {
        for ((stored, expected) in
            mapOf(
                "2" to MoonBridge.AUDIO_CONFIGURATION_STEREO,
                "51" to MoonBridge.AUDIO_CONFIGURATION_51_SURROUND,
                "71" to MoonBridge.AUDIO_CONFIGURATION_71_SURROUND,
            )) {
            preferences.edit().putString("list_audio_config", stored).commit()

            assertWithMessage("list_audio_config = $stored")
                .that(read().audioConfiguration)
                .isSameInstanceAs(expected)
        }
    }

    @Test
    fun `an unknown audio configuration string reads as stereo`() {
        for (stored in listOf("", "6", "5.1", "stereo", "512", " 71")) {
            preferences.edit().putString("list_audio_config", stored).commit()

            assertWithMessage("list_audio_config = '$stored'")
                .that(read().audioConfiguration)
                .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        }
    }

    @Test
    fun `a stored resolution and frame rate are split into width, height and fps`() {
        preferences
            .edit()
            .putString("list_resolution", "2560x1440")
            .putString("list_fps", "144")
            .commit()

        val config = read()

        assertThat(config.width).isEqualTo(2560)
        assertThat(config.height).isEqualTo(1440)
        assertThat(config.fps).isEqualTo(144)
    }

    @Test
    fun `a resolution that is not a number fails with a number format exception`() {
        for (stored in listOf("axb", "1280xabc", "x720")) {
            preferences.edit().putString("list_resolution", stored).commit()

            assertWithMessage("list_resolution = $stored").that(runCatching { read() }.exceptionOrNull())
                .isInstanceOf(NumberFormatException::class.java)
        }
    }

    @Test
    fun `a resolution without a height fails with an array index exception`() {
        for (stored in listOf("1280x", "1280xx")) {
            preferences.edit().putString("list_resolution", stored).commit()

            assertThrows(ArrayIndexOutOfBoundsException::class.java) { read() }
        }
    }

    @Test
    fun `a frame rate that is not a number fails with a number format exception`() {
        for (stored in listOf("", "abc", "60.0", " 60")) {
            preferences.edit().putString("list_fps", stored).commit()

            assertWithMessage("list_fps = '$stored'").that(runCatching { read() }.exceptionOrNull())
                .isInstanceOf(NumberFormatException::class.java)
        }
    }

    @Test
    fun `a frame rate that is not a number fails even when a bitrate is stored`() {
        // With no stored bitrate the automatic one parses the frame rate a second time and fails
        // too, so this pins the first parse on its own.
        preferences
            .edit()
            .putInt("seekbar_bitrate_kbps", 10_000)
            .putString("list_fps", "abc")
            .commit()

        assertThrows(NumberFormatException::class.java) { read() }
    }

    @Test
    fun `a value stored under another type fails with a class cast exception`() {
        preferences.edit().putInt("list_fps", 60).commit()

        assertThrows(ClassCastException::class.java) { read() }
    }

    @Test
    fun `a stored bitrate in kbps is read as it is`() {
        preferences.edit().putInt("seekbar_bitrate_kbps", 25_000).commit()

        assertThat(read().bitrate).isEqualTo(25_000)
    }

    @Test
    fun `a negative stored bitrate is kept, only zero means automatic`() {
        preferences.edit().putInt("seekbar_bitrate_kbps", -5).commit()

        assertThat(read().bitrate).isEqualTo(-5)
    }

    @Test
    fun `the legacy bitrate in megabits is multiplied by a thousand when the new key is absent`() {
        preferences.edit().putInt("seekbar_bitrate", 20).commit()

        assertThat(read().bitrate).isEqualTo(20_000)
    }

    @Test
    fun `the legacy bitrate is read but not migrated`() {
        preferences.edit().putInt("seekbar_bitrate", 20).commit()
        val recording = RecordingContext(context)

        PreferenceConfiguration.readPreferences(recording)

        assertThat(recording.log).isEmpty()
        assertThat(preferences.contains("seekbar_bitrate")).isTrue()
        assertThat(preferences.contains("seekbar_bitrate_kbps")).isFalse()
    }

    @Test
    fun `the new bitrate key wins over the legacy one`() {
        preferences.edit().putInt("seekbar_bitrate_kbps", 15_000).putInt("seekbar_bitrate", 20).commit()

        assertThat(read().bitrate).isEqualTo(15_000)
    }

    @Test
    fun `a legacy bitrate of zero means automatic`() {
        preferences.edit().putInt("seekbar_bitrate", 0).commit()

        assertThat(read().bitrate).isEqualTo(10_000)
    }

    @Test
    fun `a new bitrate of zero means automatic even when a legacy bitrate is stored`() {
        preferences.edit().putInt("seekbar_bitrate_kbps", 0).putInt("seekbar_bitrate", 20).commit()

        assertThat(read().bitrate).isEqualTo(10_000)
    }

    @Test
    fun `an automatic bitrate is computed from the stored resolution and frame rate`() {
        preferences
            .edit()
            .putString("list_resolution", "1920x1080")
            .putString("list_fps", "120")
            .commit()

        assertThat(read().bitrate)
            .isEqualTo(PreferenceConfiguration.getDefaultBitrate("1920x1080", "120"))
        assertThat(read().bitrate).isEqualTo(28_000)
    }

    @Test
    fun `the default bitrate of a context reads the stored resolution and frame rate`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate(context)).isEqualTo(10_000)

        preferences
            .edit()
            .putString("list_resolution", "3840x2160")
            .putString("list_fps", "60")
            .commit()

        assertThat(PreferenceConfiguration.getDefaultBitrate(context)).isEqualTo(80_000)
    }

    @Test
    fun `the default bitrate of a context does not convert a legacy resolution name`() {
        preferences.edit().putString("list_resolution", "1080p").commit()

        assertThrows(NumberFormatException::class.java) {
            PreferenceConfiguration.getDefaultBitrate(context)
        }
    }

    private companion object {
        /** The value of every public field after reading empty preferences, on the default SDK. */
        val EMPTY_PREFERENCES_VALUES: Map<String, Any?> =
            mapOf(
                "width" to 1280,
                "height" to 720,
                "fps" to 60,
                "bitrate" to 10_000,
                "videoFormat" to FormatOption.AUTO,
                "deadzonePercentage" to 7,
                "gamepadMouseSpeed" to 50,
                "oscOpacity" to 90,
                "stretchVideo" to false,
                "enableSops" to true,
                "playHostAudio" to false,
                "disableWarnings" to false,
                "multiController" to true,
                "usbDriver" to true,
                "flipFaceButtons" to false,
                "onscreenController" to false,
                "onlyL3R3" to false,
                "showGuideButton" to true,
                "enableHdr" to false,
                "enablePip" to false,
                "enableLatencyToast" to false,
                "bindAllUsb" to false,
                "mouseEmulation" to true,
                "analogStickForScrolling" to AnalogStickForScrolling.RIGHT,
                "mouseNavButtons" to false,
                "unlockFps" to false,
                "vibrateOsc" to true,
                "vibrateFallbackToDevice" to false,
                "vibrateFallbackToDeviceStrength" to 100,
                "touchscreenTrackpad" to true,
                "audioConfiguration" to MoonBridge.AudioConfiguration(2, 0x3),
                "framePacing" to PreferenceConfiguration.FRAME_PACING_MIN_LATENCY,
                "absoluteMouseMode" to false,
                "enableAudioFx" to false,
                "reduceRefreshRate" to false,
                "fullRange" to false,
                "gamepadMotionSensors" to true,
                "gamepadTouchpadAsMouse" to false,
                "gamepadMotionSensorsFallbackToDevice" to false,
            )
    }
}
