package io.github.magalz.borealis.preferences

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.testing.ANDROID_12
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins where in `readPreferences` each migration sits among the reads (Sprint 18a, BORE-79): a
 * value stored under the wrong type makes its read fail, and what the read had written before it
 * failed stays in the file. The migrations are the legacy resolution and frame rate, the Android 12
 * override of the motion sensors and the legacy frame pacing switch. The order of the edits among
 * themselves is in [PreferenceConfigurationMigrationTest].
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
class PreferenceConfigurationReadOrderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    /**
     * Reads with [wrongKey] stored as [wrongValue], where a different type is read, beside the
     * legacy switch of the frame pacing and, when asked, the legacy resolution and frame rate.
     * Answers which migrations the read had applied when it failed.
     */
    private fun migrationsBeforeTheFailure(
        wrongKey: String,
        wrongValue: Any,
        legacyResolution: Boolean,
    ): Set<String> {
        preferences.edit().clear().commit()
        val editor = preferences.edit().putBoolean("checkbox_disable_frame_drop", true)
        if (legacyResolution) editor.putString("list_resolution_fps", "720p60")
        when (wrongValue) {
            is Int -> editor.putInt(wrongKey, wrongValue)
            is String -> editor.putString(wrongKey, wrongValue)
            else -> error("$wrongKey has no stored type for $wrongValue")
        }
        editor.commit()

        assertThrows(ClassCastException::class.java) {
            PreferenceConfiguration.readPreferences(context)
        }

        return buildSet {
            if (legacyResolution && !preferences.contains("list_resolution_fps")) add(RESOLUTION)
            if (preferences.contains("checkbox_gamepad_motion_sensors")) add(MOTION)
            if (!preferences.contains("checkbox_disable_frame_drop")) add(PACING)
        }
    }

    @Test
    fun `a read that fails on the first key has applied no migration`() {
        for ((key, wrong) in mapOf("list_resolution" to 1, "list_fps" to 1)) {
            assertWithMessage("$key stored as a number")
                .that(migrationsBeforeTheFailure(key, wrong, legacyResolution = false))
                .isEmpty()
        }
    }

    @Test
    fun `the legacy resolution and the motion override come before the bitrate, audio and format reads`() {
        for ((key, wrong) in
            mapOf(
                "seekbar_bitrate_kbps" to "x",
                "seekbar_bitrate" to "x",
                "list_audio_config" to 1,
                "video_format" to 1,
            )) {
            assertWithMessage("$key stored as the wrong type")
                .that(migrationsBeforeTheFailure(key, wrong, legacyResolution = true))
                .containsExactly(RESOLUTION, MOTION)
        }
    }

    @Test
    fun `the motion override comes after the resolution and frame rate reads and before the bitrate read`() {
        assertThat(migrationsBeforeTheFailure("list_fps", 1, legacyResolution = false)).isEmpty()
        assertThat(migrationsBeforeTheFailure("seekbar_bitrate_kbps", "x", false))
            .containsExactly(MOTION)
    }

    @Test
    fun `the frame pacing migration comes before the reads that follow the video format`() {
        for ((key, wrong) in
            mapOf(
                "analog_scrolling" to 1,
                "seekbar_deadzone" to "x",
                "seekbar_gamepad_mouse_speed" to "x",
                "seekbar_osc_opacity" to "x",
                "checkbox_disable_warnings" to "x",
                "checkbox_enable_hdr" to "x",
                "checkbox_full_range" to "x",
                "checkbox_gamepad_motion_fallback" to "x",
            )) {
            assertWithMessage("$key stored as the wrong type")
                .that(migrationsBeforeTheFailure(key, wrong, legacyResolution = true))
                .containsExactly(RESOLUTION, MOTION, PACING)
        }
    }

    private companion object {
        const val RESOLUTION = "legacy resolution and frame rate"
        const val MOTION = "motion sensors override"
        const val PACING = "frame pacing switch"
    }
}
