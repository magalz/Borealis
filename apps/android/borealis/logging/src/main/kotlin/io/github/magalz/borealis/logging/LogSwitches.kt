package io.github.magalz.borealis.logging

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * The two logging switches the user controls: verbose logging and address masking. Whoever stores
 * them (SharedPreferences today, DataStore later, ADR 0055) implements this, so the diagnostic
 * bundle reads them without knowing where they live.
 */
interface LogSwitches {
    val verboseLogging: Boolean
    val maskAddresses: Boolean
}

/** Reads the switches from the default SharedPreferences, under the keys of [LogStorage]. */
class PreferencesLogSwitches(private val preferences: SharedPreferences) : LogSwitches {
    @Suppress("DEPRECATION")
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))

    override val verboseLogging: Boolean
        get() = preferences.getBoolean(LogStorage.VERBOSE_LOGGING_KEY, false)

    override val maskAddresses: Boolean
        get() = preferences.getBoolean(LogStorage.MASK_ADDRESSES_KEY, false)
}
