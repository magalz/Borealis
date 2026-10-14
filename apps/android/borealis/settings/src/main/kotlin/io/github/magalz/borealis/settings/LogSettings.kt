package io.github.magalz.borealis.settings

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel

/**
 * Applies the verbose-logging and address-masking switches of the default SharedPreferences to
 * [BorealisLog], once at startup and again on every change.
 *
 * Verbose logging lowers the minimum level to DEBUG; without it the level is the build's
 * [baseLevel] (INFO in release builds, DEBUG in debug builds).
 */
class LogSettings
private constructor(private val preferences: SharedPreferences, private val baseLevel: LogLevel) {
    // SharedPreferences keeps its listeners weakly, so this field keeps the registration alive for
    // as long as the LogSettings object is referenced.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }

    private fun begin() {
        apply()
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun apply() {
        val verbose = preferences.getBoolean(SettingsKeys.VERBOSE_LOGGING, false)
        BorealisLog.setMinLevel(if (verbose) minOf(baseLevel, LogLevel.DEBUG) else baseLevel)
        BorealisLog.setMaskAddresses(preferences.getBoolean(SettingsKeys.MASK_ADDRESSES, false))
    }

    companion object {
        /**
         * Starts applying the settings. The caller must keep the result referenced (for example in
         * a field of the Application).
         */
        @Suppress("DEPRECATION")
        fun start(context: Context, baseLevel: LogLevel): LogSettings =
            start(PreferenceManager.getDefaultSharedPreferences(context), baseLevel)

        internal fun start(preferences: SharedPreferences, baseLevel: LogLevel): LogSettings =
            LogSettings(preferences, baseLevel).also { it.begin() }
    }
}
