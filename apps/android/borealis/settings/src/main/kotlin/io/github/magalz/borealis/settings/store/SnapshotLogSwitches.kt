package io.github.magalz.borealis.settings.store

import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.coroutines.flow.StateFlow

/** The logging switches as the repository holds them. */
internal class SnapshotLogSwitches(private val snapshot: StateFlow<SettingsSnapshot>) :
    LogSwitches {
    override val verboseLogging: Boolean = false
    override val maskAddresses: Boolean = false
}
