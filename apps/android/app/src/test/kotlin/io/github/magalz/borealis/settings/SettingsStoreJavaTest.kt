package io.github.magalz.borealis.settings

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.store.SettingsSnapshots
import io.github.magalz.borealis.testing.ANDROID_12
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repository and the inherited Java code share the default SharedPreferences file: what the
 * repository writes is what `PreferenceConfiguration.readPreferences` reads, and what Java writes
 * on its own reaches the snapshot through the change listener.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class SettingsStoreJavaTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    private fun readByJava(): PreferenceConfiguration =
        PreferenceConfiguration.readPreferences(context)

    @Test
    fun `values that the repository wrote are what Java reads, for every stored type`() =
        runBlocking {
            val repository = SettingsSnapshots.repository(context)

            repository.set(Settings.RESOLUTION, "1920x1080")
            repository.set(Settings.FRAME_RATE, "120")
            repository.set(Settings.STRETCH_VIDEO, true)
            repository.set(Settings.DEADZONE, 12)
            repository.set(Settings.BITRATE_KBPS, 25_000)
            repository.set(Settings.VIDEO_FORMAT, "forceav1")

            val java = readByJava()
            assertThat(java.width).isEqualTo(1920)
            assertThat(java.height).isEqualTo(1080)
            assertThat(java.fps).isEqualTo(120)
            assertThat(java.stretchVideo).isTrue()
            assertThat(java.deadzonePercentage).isEqualTo(12)
            assertThat(java.bitrate).isEqualTo(25_000)
            assertThat(java.videoFormat).isEqualTo(PreferenceConfiguration.FormatOption.FORCE_AV1)
        }

    @Test
    fun `clearing the bitrate makes Java compute the automatic bitrate again`() =
        runBlocking<Unit> {
            val repository = SettingsSnapshots.repository(context)
            repository.set(Settings.BITRATE_KBPS, 25_000)

            repository.clear(Settings.BITRATE_KBPS)

            assertThat(readByJava().bitrate)
                .isEqualTo(PreferenceConfiguration.getDefaultBitrate("1280x720", "60"))
            assertThat(preferences.contains(Settings.BITRATE_KBPS.key)).isFalse()
        }

    @Test
    fun `a stored default is stored, so Java sees the key`() =
        runBlocking<Unit> {
            SettingsSnapshots.repository(context).set(Settings.GAMEPAD_MOTION_SENSORS, true)

            assertThat(preferences.contains(Settings.GAMEPAD_MOTION_SENSORS.key)).isTrue()
        }

    @Test
    fun `an old bitrate in Mbps that Java still reads is in the snapshot in kbps`() {
        preferences.edit().putInt("seekbar_bitrate", 20).commit()

        val snapshot = SettingsSnapshots.current(context)

        assertThat(snapshot[Settings.BITRATE_KBPS]).isEqualTo(20_000)
        assertThat(readByJava().bitrate).isEqualTo(20_000)
    }

    @Test
    fun `a reset that Java makes on its own reaches the snapshot`() =
        runBlocking<Unit> {
            val repository = SettingsSnapshots.repository(context)
            repository.set(Settings.FRAME_RATE, "120")
            repository.set(Settings.BITRATE_KBPS, 25_000)

            PreferenceConfiguration.resetStreamingSettings(context)

            val snapshot = SettingsSnapshots.current(context)
            assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("60")
            assertThat(snapshot.isBitrateAutomatic).isTrue()
        }

    @Test
    @Config(sdk = [ANDROID_12], shadows = [ShadowMoonBridge::class])
    fun `the motion sensors that Java turns off on Android 12 reach the snapshot`() {
        val before = SettingsSnapshots.current(context)
        assertThat(before[Settings.GAMEPAD_MOTION_SENSORS]).isTrue()

        readByJava()

        assertThat(SettingsSnapshots.current(context)[Settings.GAMEPAD_MOTION_SENSORS]).isFalse()
    }
}
