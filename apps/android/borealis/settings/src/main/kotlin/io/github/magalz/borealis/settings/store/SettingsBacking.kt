package io.github.magalz.borealis.settings.store

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.SettingStore

/**
 * One place that stores the settings of one [SettingStore]. The repository owns the order of the
 * writes and the snapshot; a backing only reads, writes and reports changes that it did not make.
 */
internal interface SettingsBacking {
    val store: SettingStore

    /** The stored value of every setting of [store]; a key that is absent maps to `null`. */
    suspend fun read(): Map<Setting<*>, Any?>

    /** Stores [value] under the key of [setting]; `null` removes the key. Throws when it fails. */
    suspend fun write(setting: Setting<*>, value: Any?)

    /**
     * Calls [onChanged] with the stored values after a change of the store, whoever made it. It is
     * also called for the writes of [write]. Called once, when the repository is created.
     */
    fun observe(onChanged: (Map<Setting<*>, Any?>) -> Unit)
}
