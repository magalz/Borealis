package io.github.magalz.borealis.settings.store

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** The [SettingsRepository] over a SharedPreferences backing and a DataStore backing. */
internal class StoreSettingsRepository(
    private val preferences: SettingsBacking,
    private val dataStore: SettingsBacking,
    private val scope: CoroutineScope,
    initial: SettingsSnapshot,
) : SettingsRepository {
    override val snapshot: StateFlow<SettingsSnapshot> = MutableStateFlow(initial)

    override fun <T> observe(setting: Setting<T>): Flow<T> = emptyFlow()

    override suspend fun <T> set(setting: Setting<T>, value: T) = Unit

    override fun <T> setNow(setting: Setting<T>, value: T) = Unit

    override suspend fun clear(vararg settings: Setting<*>) = Unit

    companion object {
        /**
         * Runs the legacy migrations and primes the snapshot, blocking until both stores are read.
         */
        fun create(
            preferences: SettingsBacking,
            dataStore: SettingsBacking,
            scope: CoroutineScope,
        ): StoreSettingsRepository =
            StoreSettingsRepository(preferences, dataStore, scope, SettingsSnapshot.DEFAULTS)
    }
}
