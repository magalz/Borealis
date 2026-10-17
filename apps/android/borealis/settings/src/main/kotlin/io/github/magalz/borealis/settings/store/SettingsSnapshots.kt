package io.github.magalz.borealis.settings.store

import android.content.Context
import android.preference.PreferenceManager
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** The process-wide settings repository and its snapshot. */
object SettingsSnapshots {
    @JvmStatic fun current(context: Context): SettingsSnapshot = SettingsSnapshot.DEFAULTS

    @Suppress("DEPRECATION")
    @JvmStatic
    fun repository(context: Context): SettingsRepository {
        val backing = PrefsBacking(PreferenceManager.getDefaultSharedPreferences(context))
        return StoreSettingsRepository(
            backing,
            backing,
            CoroutineScope(Dispatchers.IO),
            SettingsSnapshot.DEFAULTS,
        )
    }
}
