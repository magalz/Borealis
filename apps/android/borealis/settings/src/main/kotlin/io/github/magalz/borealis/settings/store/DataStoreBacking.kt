package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.catalog.OptionalIntSetting
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The `borealis_settings` DataStore file: the settings of [SettingStore.DATASTORE]. */
internal class DataStoreBacking(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SettingsBacking {
    override val store = SettingStore.DATASTORE

    private val settings = Settings.all.filter { it.store == store }

    override suspend fun read(): Map<Setting<*>, Any?> = valuesOf(dataStore.data.first())

    override suspend fun write(setting: Setting<*>, value: Any?) {
        val key = keyOf(setting)
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(key)
            } else {
                @Suppress("UNCHECKED_CAST")
                preferences[key as Preferences.Key<Any>] = value
            }
        }
    }

    override fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit) {
        scope.launch {
            dataStore.data
                .map(::valuesOf)
                .catch {
                    BorealisLog.w(
                        LogTag.APP,
                        "settings store unreadable",
                        "error" to it.javaClass.simpleName,
                    )
                }
                .collect(onChanged)
        }
    }

    private fun valuesOf(preferences: Preferences): Map<Setting<*>, Any?> {
        val stored = preferences.asMap().mapKeys { it.key.name }
        return settings.associateWith { setting -> stored[setting.key]?.takeIf(setting::accepts) }
    }

    private fun keyOf(setting: Setting<*>): Preferences.Key<*> =
        when (setting) {
            is BooleanSetting -> booleanPreferencesKey(setting.key)
            is IntSetting,
            is OptionalIntSetting -> intPreferencesKey(setting.key)
            is StringSetting -> stringPreferencesKey(setting.key)
        }

    companion object {
        const val FILE_NAME = "borealis_settings"

        private fun keysToMigrate(): Set<String> =
            Settings.all.filter { it.store == SettingStore.DATASTORE }.map { it.key }.toSet()

        /**
         * The one DataStore of [file]. It takes over the switches of [preferences] the first time
         * it is read: the values are kept, the old keys are removed, and a second run finds nothing
         * left to move. A corrupt file is replaced by an empty one.
         */
        fun create(
            preferences: SharedPreferences,
            scope: CoroutineScope,
            file: File,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(SharedPreferencesMigration({ preferences }, keysToMigrate())),
                scope = scope,
                produceFile = { file },
            )
    }
}
