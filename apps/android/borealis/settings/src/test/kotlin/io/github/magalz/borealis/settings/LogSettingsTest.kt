package io.github.magalz.borealis.settings

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class LogSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val messages = CopyOnWriteArrayList<String>()
    private val sink =
        object : LogSink {
            override fun write(record: LogRecord) {
                messages += record.message
            }
        }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    private fun startLogging(baseLevel: LogLevel) {
        BorealisLog.initialize(listOf(sink), minLevel = baseLevel)
        LogSettings.start(preferences, baseLevel)
    }

    private fun logDebugAndAddress() {
        BorealisLog.d(LogTag.APP, "debug line")
        BorealisLog.i(LogTag.APP, "to 10.0.0.5")
    }

    @Test
    fun `by default a release build logs info and above with addresses visible`() {
        startLogging(LogLevel.INFO)

        logDebugAndAddress()

        assertThat(messages).containsExactly("to 10.0.0.5")
    }

    @Test
    fun `a stored verbose switch lowers the level to debug at startup`() {
        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, true).commit()
        startLogging(LogLevel.INFO)

        logDebugAndAddress()

        assertThat(messages).containsExactly("debug line", "to 10.0.0.5").inOrder()
    }

    @Test
    fun `a stored masking switch masks addresses at startup`() {
        preferences.edit().putBoolean(SettingsKeys.MASK_ADDRESSES, true).commit()
        startLogging(LogLevel.INFO)

        logDebugAndAddress()

        assertThat(messages).containsExactly("to [IP]")
    }

    @Test
    fun `the verbose switch takes effect while the app runs and can be turned off again`() {
        startLogging(LogLevel.INFO)

        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, true).commit()
        BorealisLog.d(LogTag.APP, "while on")
        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, false).commit()
        BorealisLog.d(LogTag.APP, "while off")

        assertThat(messages).containsExactly("while on")
    }

    @Test
    fun `the masking switch takes effect while the app runs and can be turned off again`() {
        startLogging(LogLevel.INFO)

        preferences.edit().putBoolean(SettingsKeys.MASK_ADDRESSES, true).commit()
        BorealisLog.i(LogTag.APP, "on 10.0.0.5")
        preferences.edit().putBoolean(SettingsKeys.MASK_ADDRESSES, false).commit()
        BorealisLog.i(LogTag.APP, "off 10.0.0.5")

        assertThat(messages).containsExactly("on [IP]", "off 10.0.0.5").inOrder()
    }

    @Test
    fun `a debug build keeps debug when the verbose switch is off`() {
        startLogging(LogLevel.DEBUG)

        BorealisLog.d(LogTag.APP, "debug line")
        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, true).commit()
        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, false).commit()
        BorealisLog.d(LogTag.APP, "still debug")

        assertThat(messages).containsExactly("debug line", "still debug").inOrder()
    }

    @Test
    fun `starting from a context reads the default preferences`() {
        preferences.edit().putBoolean(SettingsKeys.VERBOSE_LOGGING, true).commit()
        BorealisLog.initialize(listOf(sink), minLevel = LogLevel.INFO)

        LogSettings.start(context, LogLevel.INFO)
        BorealisLog.d(LogTag.APP, "debug line")

        assertThat(messages).containsExactly("debug line")
    }
}
