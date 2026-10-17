package io.github.magalz.borealis.settings.testing

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [SettingsRepository] that keeps the snapshot in memory. [set], [setNow] and [clear] update
 * [snapshot] at once. Nothing is stored anywhere, and no write fails or rolls back.
 *
 * The snapshot compares a value equal to the default as an absent key, which is only how snapshots
 * are equal. A real store keeps the key that [set] wrote, and [writes] still records that write.
 *
 * Like the real repository it does not clear the bitrate when the resolution changes.
 */
class FakeSettingsRepository(initial: SettingsSnapshot = SettingsSnapshot.DEFAULTS) :
    SettingsRepository {
    private val state = MutableStateFlow(initial)
    private val recorded = mutableListOf<Write>()

    /** One write that the repository received: [value] is `null` for a [clear]. */
    data class Write(val setting: Setting<*>, val value: Any?)

    /** Every write received, in order, whichever of [set], [setNow] and [clear] made it. */
    val writes: List<Write>
        get() = recorded.toList()

    override val snapshot: StateFlow<SettingsSnapshot> = state.asStateFlow()

    override fun <T> observe(setting: Setting<T>): Flow<T> =
        state.map { it[setting] }.distinctUntilChanged()

    override suspend fun <T> set(setting: Setting<T>, value: T) = setNow(setting, value)

    override fun <T> setNow(setting: Setting<T>, value: T) {
        recorded += Write(setting, value)
        state.update { it.with(setting, value) }
    }

    override suspend fun clear(vararg settings: Setting<*>) {
        settings.forEach { recorded += Write(it, null) }
        state.update { it.without(*settings) }
    }
}
