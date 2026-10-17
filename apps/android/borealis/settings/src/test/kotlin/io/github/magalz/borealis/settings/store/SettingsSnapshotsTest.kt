package io.github.magalz.borealis.settings.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.eventually
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The process-wide repository: primed once from both stores, then read without waiting. */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class SettingsSnapshotsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun `the first read primes the snapshot from the SharedPreferences file`() {
        preferences.edit().putString(Settings.FRAME_RATE.key, "120").commit()

        val snapshot = SettingsSnapshots.current(context)

        assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("120")
        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("1280x720")
    }

    @Test
    fun `the first read primes the switches that an older version kept in SharedPreferences`() {
        preferences
            .edit()
            .putBoolean(Settings.VERBOSE_LOGGING.key, true)
            .putBoolean(Settings.MASK_ADDRESSES.key, true)
            .commit()

        val snapshot = SettingsSnapshots.current(context)

        assertThat(snapshot[Settings.VERBOSE_LOGGING]).isTrue()
        assertThat(snapshot[Settings.MASK_ADDRESSES]).isTrue()
        assertThat(preferences.contains(Settings.MASK_ADDRESSES.key)).isFalse()
    }

    @Test
    fun `a legacy performance overlay that is on is detailed after the prime`() {
        preferences.edit().putBoolean(Settings.PERF_OVERLAY_LEGACY.key, true).commit()

        assertThat(SettingsSnapshots.current(context)[Settings.PERF_OVERLAY])
            .isEqualTo(Settings.PERF_OVERLAY_DETAILED)
    }

    @Test
    fun `the repository is one per process and current is its snapshot`() {
        val repository = SettingsSnapshots.repository(context)

        assertThat(SettingsSnapshots.repository(context.applicationContext))
            .isSameInstanceAs(repository)
        assertThat(SettingsSnapshots.current(context)).isSameInstanceAs(repository.snapshot.value)
    }

    @Test
    fun `a read straight after setNow sees the new value`() {
        SettingsSnapshots.repository(context).setNow(Settings.DEADZONE, 12)

        assertThat(SettingsSnapshots.current(context)[Settings.DEADZONE]).isEqualTo(12)
    }

    @Test
    fun `a stream setting that setNow wrote reaches the SharedPreferences file in the background`() {
        SettingsSnapshots.repository(context).setNow(Settings.FRAME_RATE, "90")

        eventually(idleMainLooper = true) {
            preferences.getString(Settings.FRAME_RATE.key, null) == "90"
        }
    }

    @Test
    fun `a switch that set wrote is in the snapshot and not in the SharedPreferences file`() =
        runBlocking {
            SettingsSnapshots.repository(context).set(Settings.MASK_ADDRESSES, true)

            assertThat(SettingsSnapshots.current(context)[Settings.MASK_ADDRESSES]).isTrue()
            assertThat(preferences.contains(Settings.MASK_ADDRESSES.key)).isFalse()
        }

    @Test
    fun `a write that bypasses the repository reaches the snapshot through the change listener`() {
        SettingsSnapshots.repository(context)

        preferences.edit().putString(Settings.RESOLUTION.key, "1920x1080").commit()

        assertThat(SettingsSnapshots.current(context)[Settings.RESOLUTION]).isEqualTo("1920x1080")
    }
}
