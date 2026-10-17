package io.github.magalz.borealis.settings

import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.settings.repository.SettingsRepository

/** Applies the logging switches of the settings repository to the logging. */
class LogSettings private constructor() {
    fun stop() = Unit

    companion object {
        fun start(repository: SettingsRepository, baseLevel: LogLevel): LogSettings = LogSettings()
    }
}
