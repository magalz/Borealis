package io.github.magalz.borealis.settings.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.catalog.OptionalIntSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The SharedPreferences backing keeps the keys and the stored types that the inherited code reads.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class PrefsBackingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val backing = PrefsBacking(preferences)
    private val prefsSettings = Settings.all.filter { it.store == SettingStore.PREFS }

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    /** A value of the stored type of [setting] that is not its default. */
    private fun sample(setting: Setting<*>): Any =
        when (setting) {
            is BooleanSetting -> !setting.default
            is IntSetting -> setting.default + 1
            is StringSetting -> "${setting.default}-changed"
            is OptionalIntSetting -> 5_000
        }

    @Test
    fun `an empty file reads as absent for every setting of the store`() =
        runBlocking<Unit> {
            val read = backing.read()

            assertThat(read.keys).containsExactlyElementsIn(prefsSettings)
            assertThat(read.values.filterNotNull()).isEmpty()
        }

    @Test
    fun `every setting reads back the value that was written, under its own key and type`() =
        runBlocking {
            for (setting in prefsSettings) {
                backing.write(setting, sample(setting))
            }

            val read = backing.read()
            for (setting in prefsSettings) {
                assertThat(read[setting]).isEqualTo(sample(setting))
                assertThat(preferences.all[setting.key]).isEqualTo(sample(setting))
            }
        }

    @Test
    fun `a value that equals the default is stored`() =
        runBlocking<Unit> {
            backing.write(Settings.STRETCH_VIDEO, false)
            backing.write(Settings.FRAME_RATE, "60")

            assertThat(preferences.contains(Settings.STRETCH_VIDEO.key)).isTrue()
            assertThat(preferences.getString(Settings.FRAME_RATE.key, null)).isEqualTo("60")
        }

    @Test
    fun `a null removes the key`() =
        runBlocking<Unit> {
            backing.write(Settings.BITRATE_KBPS, 20_000)

            backing.write(Settings.BITRATE_KBPS, null)

            assertThat(preferences.contains(Settings.BITRATE_KBPS.key)).isFalse()
            assertThat(backing.read()[Settings.BITRATE_KBPS]).isNull()
        }

    @Test
    fun `a stored bitrate of zero is read as stored`() =
        runBlocking<Unit> {
            preferences.edit().putInt(Settings.BITRATE_KBPS.key, 0).commit()

            assertThat(backing.read()[Settings.BITRATE_KBPS]).isEqualTo(0)
        }

    @Test
    fun `a value of the wrong stored type reads as absent`() =
        runBlocking<Unit> {
            preferences.edit().putString(Settings.DEADZONE.key, "ten").commit()

            assertThat(backing.read()[Settings.DEADZONE]).isNull()
        }

    @Test
    fun `a write that the file refuses throws`() {
        val refusing =
            object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor =
                    object : SharedPreferences.Editor by preferences.edit() {
                        override fun commit(): Boolean = false
                    }
            }

        assertThrows(IOException::class.java) {
            runBlocking { PrefsBacking(refusing).write(Settings.FRAME_RATE, "30") }
        }
    }

    @Test
    fun `a write from elsewhere is reported with the stored values`() {
        val reported = mutableListOf<Map<Setting<*>, Any?>>()
        backing.observe { reported += it }

        preferences.edit().putString(Settings.FRAME_RATE.key, "120").commit()

        assertThat(reported.last()[Settings.FRAME_RATE]).isEqualTo("120")
    }

    @Test
    fun `the keys of the two logging switches are not part of this store`() =
        runBlocking<Unit> {
            preferences.edit().putBoolean(Settings.VERBOSE_LOGGING.key, true).commit()

            assertThat(backing.read().keys.map { it.key })
                .containsNoneOf(Settings.VERBOSE_LOGGING.key, Settings.MASK_ADDRESSES.key)
        }
}
