package io.github.magalz.borealis.settings

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.testing.ANDROID_12
import io.github.magalz.borealis.testing.ANDROID_13
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The defaults of the Kotlin catalog (ADR 0055) equal what the inherited
 * `PreferenceConfiguration.readPreferences` reads from empty SharedPreferences.
 *
 * Two defaults are computed, not stored, and are tested on their own:
 * - the bitrate: absent means automatic, so Java reads [PreferenceConfiguration.getDefaultBitrate];
 * - the motion sensors: Java writes off on Android 12 exactly, and the catalog default stays on.
 *
 * Java does not read the overlay settings ([Settings.PERF_OVERLAY],
 * [Settings.PERF_OVERLAY_LEGACY]), the two logging switches or the background
 * ([Settings.BACKGROUND]), so they are not compared. The language list and the small icon mode are
 * dropped from the catalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class SettingsDefaultsParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @Before
    fun emptyPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun read(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(context)

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
                Settings.BACKGROUND,
                Settings.GLASS_TINT,
                Settings.GLASS_BLUR,
                Settings.REDUCE_MOTION,
                Settings.TRANSPARENCY,
                Settings.THICK_FOCUS,
                Settings.TEXT_SIZE,
                Settings.HIGH_CONTRAST,
                Settings.COLOUR_VISION,
                Settings.FONT,
                Settings.ACCENT,
                Settings.BACKGROUND_INTENSITY,
                Settings.CARD_DENSITY,
                Settings.TEXT_SPACING,
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
    @Config(sdk = [ANDROID_12], shadows = [ShadowRecordingBridge::class])
    fun `on Android 12 Java turns motion sensors off while the catalog default is on`() {
        assertThat(Settings.GAMEPAD_MOTION_SENSORS.default).isTrue()
        assertThat(read().gamepadMotionSensors).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_13], shadows = [ShadowRecordingBridge::class])
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
                    .isEqualTo(
                        PreferenceConfiguration.getDefaultBitrate(resolution, fps.toString())
                    )
            }
        }
    }

    @Test
    fun `the automatic bitrate of the width, height and frame rate overload equals the string overload`() {
        assertThat(DefaultBitrate.kbps(1920, 1080, 60))
            .isEqualTo(DefaultBitrate.kbps("1920x1080", "60"))
    }

    private companion object {

        /** 720p at 60 fps: the resolution factor 5 times the frame rate factor 2. */
        const val AUTOMATIC_720P60_KBPS = 10_000

        /**
         * The old screen's list (360p to 4K), a native-like odd size, and sizes beyond both ends.
         */
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
