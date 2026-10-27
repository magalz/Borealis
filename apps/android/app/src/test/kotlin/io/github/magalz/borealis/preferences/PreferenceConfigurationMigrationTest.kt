package io.github.magalz.borealis.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.ANDROID_11
import io.github.magalz.borealis.testing.ANDROID_12
import io.github.magalz.borealis.testing.ANDROID_13
import java.lang.reflect.Method
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Characterizes what `PreferenceConfiguration` writes while it reads, and the migrations and
 * overrides behind those writes: the legacy resolution, frame rate, audio and frame pacing keys,
 * the Android 12 motion sensor override, the Shield firmware check and the streaming reset. A
 * recording context logs each edit with its keys, values and order, and whether it was applied or
 * committed, so a conversion that changes any of those fails here.
 */
@Suppress("DEPRECATION", "LargeClass", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class PreferenceConfigurationMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val recording = RecordingContext(context)
    private val manufacturer: String = Build.MANUFACTURER
    private val fingerprint: String = Build.FINGERPRINT

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    @After
    fun restoreBuild() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", fingerprint)
    }

    private fun read(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(recording)

    private val log: MutableList<String>
        get() = recording.log

    @Test
    fun `a legacy 5,1 surround switch that is on becomes the audio configuration 51 and is removed`() {
        preferences.edit().putBoolean("checkbox_51_surround", true).commit()

        val config = read()

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(checkbox_51_surround)",
                "putString(list_audio_config=51)",
                "apply()",
            )
            .inOrder()
        assertThat(preferences.contains("checkbox_51_surround")).isFalse()
        assertThat(preferences.getString("list_audio_config", null)).isEqualTo("51")
        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
    }

    @Test
    fun `a legacy 5,1 surround switch that is on replaces a stored audio configuration`() {
        preferences
            .edit()
            .putBoolean("checkbox_51_surround", true)
            .putString("list_audio_config", "71")
            .commit()

        val config = read()

        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
        assertThat(preferences.getString("list_audio_config", null)).isEqualTo("51")
    }

    @Test
    fun `a legacy 5,1 surround switch that is off writes nothing and stays in the file`() {
        preferences.edit().putBoolean("checkbox_51_surround", false).commit()

        val config = read()

        assertThat(log).isEmpty()
        assertThat(preferences.contains("checkbox_51_surround")).isTrue()
        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
    }

    @Test
    fun `each legacy resolution and frame rate string sets the fields and is replaced by the new keys`() {
        for ((legacy, expected) in LEGACY_RESOLUTION_FPS) {
            preferences.edit().clear().putString("list_resolution_fps", legacy).commit()
            log.clear()

            val config = read()

            assertWithMessage("fields for $legacy")
                .that(listOf(config.width, config.height, config.fps))
                .containsExactly(expected.width, expected.height, expected.fps)
                .inOrder()
            assertWithMessage("writes for $legacy")
                .that(log)
                .containsExactly(
                    "edit",
                    "remove(list_resolution_fps)",
                    "putString(list_resolution=${expected.size})",
                    "putString(list_fps=${expected.fps})",
                    "apply()",
                )
                .inOrder()
            assertThat(preferences.contains("list_resolution_fps")).isFalse()
        }
    }

    @Test
    fun `an unknown legacy resolution and frame rate string means 720p at 60 and is written as such`() {
        for (legacy in listOf("", "480p60", "4k60", "720P60", "1080p120", "garbage")) {
            preferences.edit().clear().putString("list_resolution_fps", legacy).commit()
            log.clear()

            val config = read()

            assertWithMessage("fields for '$legacy'")
                .that(listOf(config.width, config.height, config.fps))
                .containsExactly(1280, 720, 60)
                .inOrder()
            assertWithMessage("writes for '$legacy'")
                .that(log)
                .containsExactly(
                    "edit",
                    "remove(list_resolution_fps)",
                    "putString(list_resolution=1280x720)",
                    "putString(list_fps=60)",
                    "apply()",
                )
                .inOrder()
        }
    }

    @Test
    fun `a legacy resolution and frame rate string replaces the stored resolution and frame rate`() {
        preferences
            .edit()
            .putString("list_resolution_fps", "1080p30")
            .putString("list_resolution", "3840x2160")
            .putString("list_fps", "not a number")
            .commit()

        val config = read()

        assertThat(listOf(config.width, config.height, config.fps))
            .containsExactly(1920, 1080, 30)
            .inOrder()
        assertThat(preferences.getString("list_resolution", null)).isEqualTo("1920x1080")
        assertThat(preferences.getString("list_fps", null)).isEqualTo("30")
        // The automatic bitrate uses what the migration has just written.
        assertThat(config.bitrate).isEqualTo(10_000)
    }

    @Test
    fun `each legacy resolution name becomes a width and height string, whatever its case`() {
        for ((legacy, expected) in LEGACY_RESOLUTION_NAMES) {
            preferences.edit().clear().putString("list_resolution", legacy).commit()
            log.clear()

            val config = read()

            assertWithMessage("fields for $legacy")
                .that(config.width to config.height)
                .isEqualTo(expected.width to expected.height)
            assertWithMessage("writes for $legacy")
                .that(log)
                .containsExactly("edit", "putString(list_resolution=${expected.size})", "apply()")
                .inOrder()
            assertThat(preferences.getString("list_resolution", null)).isEqualTo(expected.size)
        }
    }

    @Test
    fun `an unknown resolution name without an x becomes 720p`() {
        for (legacy in listOf("", "Native", "native", "8K", "720")) {
            preferences.edit().clear().putString("list_resolution", legacy).commit()
            log.clear()

            val config = read()

            assertWithMessage("fields for '$legacy'")
                .that(config.width to config.height)
                .isEqualTo(1280 to 720)
            assertWithMessage("writes for '$legacy'")
                .that(log)
                .containsExactly("edit", "putString(list_resolution=1280x720)", "apply()")
                .inOrder()
        }
    }

    @Test
    fun `a resolution that has an x is never converted or written back`() {
        preferences.edit().putString("list_resolution", "1920x1080").commit()

        read()

        assertThat(log).isEmpty()
    }

    @Test
    fun `a legacy never drop frames switch that is on becomes balanced frame pacing and is removed`() {
        preferences.edit().putBoolean("checkbox_disable_frame_drop", true).commit()

        val config = read()

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(checkbox_disable_frame_drop)",
                "putString(frame_pacing=balanced)",
                "apply()",
            )
            .inOrder()
        assertThat(config.framePacing).isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
        assertThat(preferences.contains("checkbox_disable_frame_drop")).isFalse()
    }

    @Test
    fun `a legacy never drop frames switch that is off becomes latency frame pacing and is removed`() {
        preferences.edit().putBoolean("checkbox_disable_frame_drop", false).commit()

        val config = read()

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(checkbox_disable_frame_drop)",
                "putString(frame_pacing=latency)",
                "apply()",
            )
            .inOrder()
        assertThat(config.framePacing).isEqualTo(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY)
        assertThat(preferences.contains("checkbox_disable_frame_drop")).isFalse()
    }

    @Test
    fun `a legacy never drop frames switch replaces a stored frame pacing`() {
        preferences
            .edit()
            .putBoolean("checkbox_disable_frame_drop", true)
            .putString("frame_pacing", "smoothness")
            .commit()

        val config = read()

        assertThat(config.framePacing).isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
        assertThat(preferences.getString("frame_pacing", null)).isEqualTo("balanced")
    }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
    fun `on Android 12 the motion sensors are written off when the user never chose`() {
        val config = read()

        assertThat(log)
            .containsExactly("edit", "putBoolean(checkbox_gamepad_motion_sensors=false)", "apply()")
            .inOrder()
        assertThat(config.gamepadMotionSensors).isFalse()
        assertThat(preferences.getBoolean("checkbox_gamepad_motion_sensors", true)).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
    fun `on Android 12 a stored motion sensors choice is kept`() {
        preferences.edit().putBoolean("checkbox_gamepad_motion_sensors", true).commit()

        val config = read()

        assertThat(log).isEmpty()
        assertThat(config.gamepadMotionSensors).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_11], shadows = [ShadowRecordingBridge::class])
    fun `below Android 12 the motion sensors stay on and nothing is written`() {
        val config = read()

        assertThat(log).isEmpty()
        assertThat(config.gamepadMotionSensors).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_13], shadows = [ShadowRecordingBridge::class])
    fun `above Android 12 the motion sensors stay on and nothing is written`() {
        val config = read()

        assertThat(log).isEmpty()
        assertThat(config.gamepadMotionSensors).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
    fun `the edits of one read come in the order audio, resolution and frame rate, motion, frame pacing`() {
        preferences
            .edit()
            .putBoolean("checkbox_51_surround", true)
            .putString("list_resolution_fps", "720p60")
            .putBoolean("checkbox_disable_frame_drop", true)
            .commit()

        read()

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(checkbox_51_surround)",
                "putString(list_audio_config=51)",
                "apply()",
                "edit",
                "remove(list_resolution_fps)",
                "putString(list_resolution=1280x720)",
                "putString(list_fps=60)",
                "apply()",
                "edit",
                "putBoolean(checkbox_gamepad_motion_sensors=false)",
                "apply()",
                "edit",
                "remove(checkbox_disable_frame_drop)",
                "putString(frame_pacing=balanced)",
                "apply()",
            )
            .inOrder()
    }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
    fun `a legacy resolution name is converted before the motion override and the frame pacing migration`() {
        preferences
            .edit()
            .putBoolean("checkbox_51_surround", true)
            .putString("list_resolution", "1080p")
            .putBoolean("checkbox_disable_frame_drop", false)
            .commit()

        read()

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(checkbox_51_surround)",
                "putString(list_audio_config=51)",
                "apply()",
                "edit",
                "putString(list_resolution=1920x1080)",
                "apply()",
                "edit",
                "putBoolean(checkbox_gamepad_motion_sensors=false)",
                "apply()",
                "edit",
                "remove(checkbox_disable_frame_drop)",
                "putString(frame_pacing=latency)",
                "apply()",
            )
            .inOrder()
    }

    @Test
    fun `a second read finds the migration done and writes nothing`() {
        preferences
            .edit()
            .putBoolean("checkbox_51_surround", true)
            .putString("list_resolution_fps", "4K30")
            .putBoolean("checkbox_disable_frame_drop", true)
            .commit()
        read()
        log.clear()

        val config = read()

        assertThat(log).isEmpty()
        assertThat(listOf(config.width, config.height, config.fps))
            .containsExactly(3840, 2160, 30)
            .inOrder()
        assertThat(config.framePacing).isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
    }

    @Test
    fun `reading never touches the preferences it did not migrate`() {
        preferences
            .edit()
            .putString("list_resolution", "1920x1080")
            .putString("list_fps", "90")
            .putInt("seekbar_bitrate_kbps", 30_000)
            .putBoolean("checkbox_enable_hdr", true)
            .putString("video_format", "forceh265")
            .commit()
        val before = preferences.all.toMap()

        read()

        assertThat(log).isEmpty()
        assertThat(preferences.all).isEqualTo(before)
    }

    @Test
    fun `the streaming reset removes its nine keys in one applied edit and keeps the others`() {
        preferences
            .edit()
            .putInt("seekbar_bitrate_kbps", 25_000)
            .putInt("seekbar_bitrate", 20)
            .putString("list_resolution_fps", "720p60")
            .putString("list_resolution", "1920x1080")
            .putString("list_fps", "120")
            .putString("video_format", "forceav1")
            .putBoolean("checkbox_enable_hdr", true)
            .putBoolean("checkbox_unlock_fps", true)
            .putBoolean("checkbox_full_range", true)
            .putString("frame_pacing", "balanced")
            .putString("list_audio_config", "71")
            .putBoolean("checkbox_stretch_video", true)
            .putInt("seekbar_deadzone", 12)
            .commit()

        PreferenceConfiguration.resetStreamingSettings(recording)

        assertThat(log)
            .containsExactly(
                "edit",
                "remove(seekbar_bitrate_kbps)",
                "remove(seekbar_bitrate)",
                "remove(list_resolution_fps)",
                "remove(list_resolution)",
                "remove(list_fps)",
                "remove(video_format)",
                "remove(checkbox_enable_hdr)",
                "remove(checkbox_unlock_fps)",
                "remove(checkbox_full_range)",
                "apply()",
            )
            .inOrder()
        assertThat(preferences.all.keys)
            .containsExactly(
                "frame_pacing",
                "list_audio_config",
                "checkbox_stretch_video",
                "seekbar_deadzone",
            )
    }

    @Test
    fun `after the streaming reset the next read gives the defaults again`() {
        preferences
            .edit()
            .putInt("seekbar_bitrate_kbps", 25_000)
            .putString("list_resolution", "1920x1080")
            .putString("list_fps", "120")
            .putString("video_format", "forceav1")
            .putBoolean("checkbox_enable_hdr", true)
            .commit()

        PreferenceConfiguration.resetStreamingSettings(context)
        val config = read()

        assertThat(listOf(config.width, config.height, config.fps))
            .containsExactly(1280, 720, 60)
            .inOrder()
        assertThat(config.bitrate).isEqualTo(10_000)
        assertThat(config.videoFormat).isEqualTo(PreferenceConfiguration.FormatOption.AUTO)
        assertThat(config.enableHdr).isFalse()
    }

    @Test
    fun `the streaming reset on empty preferences still opens one edit and applies it`() {
        PreferenceConfiguration.resetStreamingSettings(recording)

        assertThat(log.first()).isEqualTo("edit")
        assertThat(log.last()).isEqualTo("apply()")
        assertThat(log).hasSize(RESET_KEYS + 2)
    }

    @Test
    fun `the Shield firmware with the broken HDR is recognized by manufacturer and fingerprint`() {
        for (maker in listOf("NVIDIA", "nvidia", "Nvidia")) {
            buildAs(maker, BROKEN_FIRMWARE_FINGERPRINT)

            assertWithMessage("manufacturer $maker")
                .that(PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr())
                .isTrue()
        }
    }

    @Test
    fun `other manufacturers and other firmware are not the Shield with the broken HDR`() {
        buildAs("Google", BROKEN_FIRMWARE_FINGERPRINT)
        assertThat(PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()).isFalse()

        buildAs(
            "NVIDIA",
            "NVIDIA/foster_e/foster:9/PPR1.180610.011/4079208_2235.1396:user/release-keys",
        )
        assertThat(PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()).isFalse()

        buildAs("NVIDIA", "robolectric")
        assertThat(PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()).isFalse()

        buildAs("NVIDIA ", BROKEN_FIRMWARE_FINGERPRINT)
        assertThat(PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()).isFalse()
    }

    @Test
    fun `HDR that the user turned on is read as off on the Shield firmware that crashes with it`() {
        preferences.edit().putBoolean("checkbox_enable_hdr", true).commit()
        buildAs("NVIDIA", BROKEN_FIRMWARE_FINGERPRINT)

        val config = read()

        assertThat(config.enableHdr).isFalse()
        assertThat(preferences.getBoolean("checkbox_enable_hdr", false)).isTrue()
        assertThat(log).isEmpty()
    }

    @Test
    fun `HDR that the user turned on is read as on elsewhere and off when the user did not`() {
        preferences.edit().putBoolean("checkbox_enable_hdr", true).commit()
        buildAs("Google", BROKEN_FIRMWARE_FINGERPRINT)
        assertThat(read().enableHdr).isTrue()

        preferences.edit().putBoolean("checkbox_enable_hdr", false).commit()
        assertThat(read().enableHdr).isFalse()
    }

    @Test
    fun `the resolution string of a height is the 16 by 9 resolution, and 720p for an unknown height`() {
        val resolutionString = privateResolutionString()

        for ((height, expected) in
            mapOf(
                360 to "640x360",
                480 to "854x480",
                720 to "1280x720",
                1080 to "1920x1080",
                1440 to "2560x1440",
                2160 to "3840x2160",
            )) {
            assertWithMessage("height $height")
                .that(resolutionString(height * 2, height))
                .isEqualTo(expected)
        }
        for (height in listOf(0, 1, 361, 719, 721, 1200, 4320, -1)) {
            assertWithMessage("height $height")
                .that(resolutionString(1920, height))
                .isEqualTo("1280x720")
        }
    }

    /**
     * `getResolutionString` is private and only the legacy migration calls it, with the heights of
     * the legacy names. The test calls it directly to pin its fall-through for any other height.
     * The height is the last argument and the width, which the function ignores, the one before it.
     */
    private fun privateResolutionString(): (Int, Int) -> String {
        val (method, receiver) = resolutionStringMethod()
        method.isAccessible = true
        return { width, height ->
            val arguments = listOf<Any>(width, height).takeLast(method.parameterCount)
            method.invoke(receiver, *arguments.toTypedArray()) as String
        }
    }

    /** The method and its receiver: none for a static method, the companion for a Kotlin one. */
    private fun resolutionStringMethod(): Pair<Method, Any?> {
        val outer = PreferenceConfiguration::class.java
        declaredResolutionString(outer)?.let {
            return it to null
        }
        val companionType = Class.forName("${outer.name}\$Companion")
        val companion =
            outer.getDeclaredField("Companion").also { it.isAccessible = true }.get(null)
        return checkNotNull(declaredResolutionString(companionType)) to companion
    }

    private fun declaredResolutionString(type: Class<*>): Method? =
        type.declaredMethods.firstOrNull { it.name == "getResolutionString" }

    private fun buildAs(maker: String, build: String) {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", maker)
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", build)
    }

    private class Resolution(val width: Int, val height: Int, val fps: Int = 0) {
        val size: String
            get() = "${width}x$height"
    }

    private companion object {
        const val RESET_KEYS = 9
        const val BROKEN_FIRMWARE_FINGERPRINT =
            "NVIDIA/foster_e/foster:9/PPR1.180610.011/4079208_2235.1395:user/release-keys"

        val LEGACY_RESOLUTION_FPS =
            mapOf(
                "360p30" to Resolution(640, 360, 30),
                "360p60" to Resolution(640, 360, 60),
                "720p30" to Resolution(1280, 720, 30),
                "720p60" to Resolution(1280, 720, 60),
                "1080p30" to Resolution(1920, 1080, 30),
                "1080p60" to Resolution(1920, 1080, 60),
                "4K30" to Resolution(3840, 2160, 30),
                "4K60" to Resolution(3840, 2160, 60),
            )

        val LEGACY_RESOLUTION_NAMES =
            mapOf(
                "360p" to Resolution(640, 360),
                "480p" to Resolution(854, 480),
                "720p" to Resolution(1280, 720),
                "1080p" to Resolution(1920, 1080),
                "1440p" to Resolution(2560, 1440),
                "4K" to Resolution(3840, 2160),
                "4k" to Resolution(3840, 2160),
                "1080P" to Resolution(1920, 1080),
                "720P" to Resolution(1280, 720),
            )
    }
}
