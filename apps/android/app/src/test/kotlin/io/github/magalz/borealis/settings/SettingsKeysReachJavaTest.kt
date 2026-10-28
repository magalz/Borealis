package io.github.magalz.borealis.settings

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.catalog.OptionalIntSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A guard on the keys and types of the catalog (ADR 0055): a value stored under the key and type of
 * a catalog entry arrives in the Java field that `PreferenceConfiguration.readPreferences` fills. A
 * typo in a catalog key, or a wrong stored type, leaves Java on its default and fails here.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class SettingsKeysReachJavaTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun emptyPreferences() {
        preferences.edit().clear().commit()
    }

    private fun read(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(context)

    private fun write(setting: Setting<*>, value: Any) {
        val editor = preferences.edit()
        when (setting) {
            is BooleanSetting -> editor.putBoolean(setting.key, value as Boolean)
            is IntSetting -> editor.putInt(setting.key, value as Int)
            is StringSetting -> editor.putString(setting.key, value as String)
            is OptionalIntSetting -> editor.putInt(setting.key, value as Int)
        }
        editor.commit()
    }

    /** A valid value that differs from the default, so that Java's default cannot pass for it. */
    private fun changed(setting: Setting<*>): Any =
        when (setting) {
            is BooleanSetting -> !setting.default
            is IntSetting -> setting.default + 1
            is StringSetting -> CHANGED_STRINGS.getValue(setting)
            is OptionalIntSetting -> error("${setting.key} is the bitrate, tested on its own")
        }

    @Test
    fun `a value stored under the catalog key and type arrives in its Java field`() {
        for (setting in javaValues(read()).keys) {
            preferences.edit().clear().commit()
            val value = changed(setting)
            assertThat(value).isNotEqualTo(setting.default)

            write(setting, value)

            assertWithMessage("the Java field of ${setting.key}")
                .that(javaValues(read())[setting])
                .isEqualTo(value)
        }
    }

    @Test
    fun `a bitrate stored under the catalog key and type arrives in the Java bitrate`() {
        write(Settings.BITRATE_KBPS, BITRATE_KBPS)

        assertThat(read().bitrate).isEqualTo(BITRATE_KBPS)
    }

    @Test
    fun `every setting of the preferences file is checked above or has no Java reader`() {
        val checked = javaValues(read()).keys + Settings.BITRATE_KBPS

        val unchecked =
            Settings.all.filter { it.store == SettingStore.PREFS && it !in checked }.toSet()

        assertThat(unchecked).isEqualTo(NO_JAVA_READER)
    }

    private companion object {
        const val BITRATE_KBPS = 25_000

        /**
         * The settings in the preferences file that Java does not read: the overlay choice is read
         * by `StreamSessionFactory`, and the legacy overlay switch only by `LegacyMigrations`.
         */
        val NO_JAVA_READER = setOf(Settings.PERF_OVERLAY, Settings.PERF_OVERLAY_LEGACY)

        val CHANGED_STRINGS =
            mapOf(
                Settings.RESOLUTION to "1920x1080",
                Settings.FRAME_RATE to "120",
                Settings.FRAME_PACING to "balanced",
                Settings.AUDIO_CONFIG to "51",
                Settings.ANALOG_SCROLLING to "left",
                Settings.VIDEO_FORMAT to "forceav1",
            )
    }
}
