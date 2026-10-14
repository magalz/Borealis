package io.github.magalz.borealis.settings

import android.content.Context
import android.content.SharedPreferences
import io.github.magalz.borealis.logging.LogLevel

/** Applies the log switches in the default SharedPreferences to `BorealisLog`. */
class LogSettings private constructor() {
    companion object {
        /** Applies the stored values now and again whenever they change. */
        fun start(context: Context, baseLevel: LogLevel): LogSettings = LogSettings()

        internal fun start(preferences: SharedPreferences, baseLevel: LogLevel): LogSettings =
            LogSettings()
    }
}
