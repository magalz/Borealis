package io.github.magalz.borealis.settings.repository

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only writer of the settings from Kotlin (ADR 0055). It reads and writes through the store of
 * each [Setting]; the inherited Java readers keep reading the default SharedPreferences file.
 *
 * Writes apply at once and there is no Save step. A write that fails leaves the previous value and
 * is logged; it is not thrown to the caller.
 *
 * Changing [Settings.RESOLUTION] or [Settings.FRAME_RATE] does not touch the bitrate. The caller
 * that changes either one also calls [clear] on [Settings.BITRATE_KBPS], which returns the bitrate
 * to automatic (UC-009, A4).
 */
interface SettingsRepository {
    /**
     * Every setting now. It always holds a value, so a read is synchronous and never blocks, and it
     * is primed when the app starts. It emits a new snapshot for each change.
     */
    val snapshot: StateFlow<SettingsSnapshot>

    /** The value of [setting] now and after each change of it. Repeated values are not emitted. */
    fun <T> observe(setting: Setting<T>): Flow<T>

    /**
     * Stores [value] for [setting] and updates [snapshot] when the write is done. Storing `null`
     * for [Settings.BITRATE_KBPS] is the same as [clear].
     */
    suspend fun <T> set(setting: Setting<T>, value: T)

    /**
     * A write-through for Java wiring: it updates [snapshot] before it returns and stores the value
     * in the background. Kotlin code uses [set].
     */
    fun <T> setNow(setting: Setting<T>, value: T)

    /** Removes the stored value of each of [settings], so that each reads as its default. */
    suspend fun clear(vararg settings: Setting<*>)
}
