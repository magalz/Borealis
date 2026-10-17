package io.github.magalz.borealis.settings.store

import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * The logging switches as the repository holds them. A read is a read of the snapshot, so it never
 * waits for the disk and the diagnostic bundle sees what the user last chose.
 */
internal class SnapshotLogSwitches(private val snapshot: StateFlow<SettingsSnapshot>) :
    LogSwitches {
    override val verboseLogging: Boolean
        get() = snapshot.value[Settings.VERBOSE_LOGGING]

    override val maskAddresses: Boolean
        get() = snapshot.value[Settings.MASK_ADDRESSES]
}
