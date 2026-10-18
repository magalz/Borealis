package io.github.magalz.borealis.settings

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.store.StoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Applies the verbose-logging and address-masking switches of the [SettingsRepository] to
 * [BorealisLog], once at startup and again on every change. The audit trail shares the redactor of
 * [BorealisLog], so address masking reaches it too.
 *
 * With the repository of the app the switches are applied inside its write path, before the new
 * snapshot is published: whoever reacts to a change of "Mask addresses" already finds the redactor
 * switched (the audit trail viewer masks its rows with it). Any other repository is followed
 * through its snapshot flow instead.
 *
 * Verbose logging lowers the minimum level to DEBUG; without it the level is the build's
 * [baseLevel] (INFO in release builds, DEBUG in debug builds).
 */
class LogSettings
private constructor(private val repository: SettingsRepository, private val baseLevel: LogLevel) {
    // Unconfined, so that a change applies on the thread that made it, before that call returns.
    @Suppress("InjectDispatcher") // That is the contract, not a seam a test replaces.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun begin() {
        if (repository is StoreSettingsRepository) {
            repository.beforePublish = ::apply
        } else {
            scope.launch { repository.snapshot.collect(::apply) }
        }
        apply(repository.snapshot.value)
    }

    private fun apply(snapshot: SettingsSnapshot) {
        val verbose = snapshot[Settings.VERBOSE_LOGGING]
        BorealisLog.setMinLevel(if (verbose) minOf(baseLevel, LogLevel.DEBUG) else baseLevel)
        BorealisLog.setMaskAddresses(snapshot[Settings.MASK_ADDRESSES])
    }

    /** Stops following the repository. The last applied switches stay in force. */
    fun stop() {
        if (repository is StoreSettingsRepository) repository.beforePublish = null
        scope.cancel()
    }

    companion object {
        /**
         * Starts applying the switches of [repository]. The caller must keep the result referenced
         * (for example in a field of the Application).
         */
        fun start(repository: SettingsRepository, baseLevel: LogLevel): LogSettings =
            LogSettings(repository, baseLevel).also { it.begin() }
    }
}
