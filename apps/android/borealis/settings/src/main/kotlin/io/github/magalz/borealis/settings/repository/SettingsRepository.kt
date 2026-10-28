package io.github.magalz.borealis.settings.repository

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only writer of the settings from Kotlin (ADR 0055). It reads and writes through the store of
 * each [Setting]; the inherited readers keep reading the default SharedPreferences file.
 *
 * Writes apply at once and there is no Save step. Writes are applied in the order of the calls, per
 * repository.
 *
 * [set] and [setNow] always persist the key, even when the value equals the default: the inherited
 * code writes its own value when a key is absent (motion sensors on Android 12), so dropping a
 * stored default would lose the user's choice. Only [clear], and [set] of `null` for
 * [Settings.BITRATE_KBPS], remove a key. The rule of [SettingsSnapshot] that a default equals an
 * absent key is about equality of snapshots only.
 *
 * A write that fails is logged and never thrown to the caller. [set] keeps the previous value.
 * [setNow] is optimistic: it updates the snapshot before it returns, and when the background store
 * fails, the backing rolls that key back to the value that is stored and logs.
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
     * Stores [value] for [setting], also when it equals the default, and updates [snapshot] when
     * the write is done. On failure the previous value stays. Storing `null` for
     * [Settings.BITRATE_KBPS] is the same as [clear].
     */
    suspend fun <T> set(setting: Setting<T>, value: T)

    /**
     * A synchronous write-through for callers that cannot suspend. It is optimistic: it updates
     * [snapshot] before it returns and stores the value in the background, also when it equals the
     * default. If the store fails, the backing rolls the key back to the stored value, updates
     * [snapshot] and logs. Code that can suspend uses [set].
     */
    fun <T> setNow(setting: Setting<T>, value: T)

    /** Removes the stored value of each of [settings], so that each reads as its default. */
    suspend fun clear(vararg settings: Setting<*>)
}
