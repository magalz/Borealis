package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore

/** The default SharedPreferences file, which the inherited Java code reads. */
internal class PrefsBacking(private val preferences: SharedPreferences) : SettingsBacking {
    override val store = SettingStore.PREFS

    override suspend fun read(): Map<Setting<*>, Any?> = emptyMap()

    override suspend fun write(setting: Setting<*>, value: Any?) = Unit

    override fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit) = Unit
}
