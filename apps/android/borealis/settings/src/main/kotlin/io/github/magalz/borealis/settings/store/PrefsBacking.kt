package io.github.magalz.borealis.settings.store

import android.content.SharedPreferences
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import java.io.IOException

/**
 * The default SharedPreferences file, which the inherited code reads. Keys and stored types are
 * those of the catalog, so `PreferenceConfiguration.readPreferences` sees what is written here.
 */
internal class PrefsBacking(private val preferences: SharedPreferences) : SettingsBacking {
    override val store = SettingStore.PREFS

    private val settings = Settings.all.filter { it.store == store }

    // SharedPreferences keeps its listeners weakly, so this field keeps the registration alive.
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override suspend fun read(): Map<Setting<*>, Any?> = readNow()

    // A value of the wrong stored type counts as absent, as it would for a typed read of the file.
    private fun readNow(): Map<Setting<*>, Any?> {
        val stored = preferences.all
        return settings.associateWith { setting -> stored[setting.key]?.takeIf(setting::accepts) }
    }

    override suspend fun write(setting: Setting<*>, value: Any?) {
        val editor = preferences.edit()
        when (value) {
            null -> editor.remove(setting.key)
            is Boolean -> editor.putBoolean(setting.key, value)
            is Int -> editor.putInt(setting.key, value)
            is String -> editor.putString(setting.key, value)
            else -> throw IllegalArgumentException("$setting cannot store a ${value.javaClass}")
        }
        if (!editor.commit()) throw IOException("The preferences file did not accept the write")
    }

    override fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit) {
        val registered = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            onChanged(readNow())
        }
        listener = registered
        preferences.registerOnSharedPreferenceChangeListener(registered)
    }
}
