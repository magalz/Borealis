package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** The `borealis_settings` DataStore file. */
internal class DataStoreBacking(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SettingsBacking {
    override val store = SettingStore.DATASTORE

    override suspend fun read(): Map<Setting<*>, Any?> = emptyMap()

    override suspend fun write(setting: Setting<*>, value: Any?) = Unit

    override fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit) = Unit

    companion object {
        const val FILE_NAME = "borealis_settings"

        /** The one DataStore of [file], which takes over the switches of [preferences] once. */
        fun create(
            preferences: SharedPreferences,
            scope: CoroutineScope,
            file: File,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }
}
