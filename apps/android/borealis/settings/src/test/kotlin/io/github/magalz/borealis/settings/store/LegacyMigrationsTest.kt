package io.github.magalz.borealis.settings.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The keys of older versions are moved to the keys of the catalog, once. */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class LegacyMigrationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    private fun overlay(): String? = preferences.getString(Settings.PERF_OVERLAY.key, null)

    @Test
    fun `the old performance overlay switch that is on becomes detailed`() {
        preferences.edit().putBoolean(Settings.PERF_OVERLAY_LEGACY.key, true).commit()

        LegacyMigrations.run(preferences)

        assertThat(overlay()).isEqualTo(Settings.PERF_OVERLAY_DETAILED)
    }

    @Test
    fun `the performance overlay is migrated once`() {
        preferences.edit().putBoolean(Settings.PERF_OVERLAY_LEGACY.key, true).commit()
        LegacyMigrations.run(preferences)
        preferences.edit().putString(Settings.PERF_OVERLAY.key, Settings.PERF_OVERLAY_OFF).commit()

        LegacyMigrations.run(preferences)

        assertThat(overlay()).isEqualTo(Settings.PERF_OVERLAY_OFF)
    }

    @Test
    fun `a performance overlay that the user chose is not replaced`() {
        preferences
            .edit()
            .putBoolean(Settings.PERF_OVERLAY_LEGACY.key, true)
            .putString(Settings.PERF_OVERLAY.key, Settings.PERF_OVERLAY_COMPACT)
            .commit()

        LegacyMigrations.run(preferences)

        assertThat(overlay()).isEqualTo(Settings.PERF_OVERLAY_COMPACT)
    }

    @Test
    fun `an old performance overlay switch that is off or absent stores nothing`() {
        LegacyMigrations.run(preferences)
        assertThat(overlay()).isNull()

        preferences.edit().putBoolean(Settings.PERF_OVERLAY_LEGACY.key, false).commit()
        LegacyMigrations.run(preferences)

        assertThat(overlay()).isNull()
    }

    @Test
    fun `the old bitrate in Mbps becomes the bitrate in kbps and its key is removed`() {
        preferences.edit().putInt("seekbar_bitrate", 20).commit()

        LegacyMigrations.run(preferences)

        assertThat(preferences.getInt(Settings.BITRATE_KBPS.key, -1)).isEqualTo(20_000)
        assertThat(preferences.contains("seekbar_bitrate")).isFalse()
    }

    @Test
    fun `a bitrate in kbps wins over the old one`() {
        preferences
            .edit()
            .putInt("seekbar_bitrate", 20)
            .putInt(Settings.BITRATE_KBPS.key, 8_000)
            .commit()

        LegacyMigrations.run(preferences)

        assertThat(preferences.getInt(Settings.BITRATE_KBPS.key, -1)).isEqualTo(8_000)
    }

    @Test
    fun `an old bitrate of zero stays automatic`() {
        preferences.edit().putInt("seekbar_bitrate", 0).commit()

        LegacyMigrations.run(preferences)

        assertThat(preferences.contains(Settings.BITRATE_KBPS.key)).isFalse()
    }
}
