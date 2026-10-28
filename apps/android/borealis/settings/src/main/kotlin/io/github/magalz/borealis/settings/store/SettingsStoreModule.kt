package io.github.magalz.borealis.settings.store

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Singleton

/**
 * Binds the settings repository, and the [LogSwitches] that the diagnostic bundle reads from it
 * (ADR 0055). The repository is the one of [SettingsSnapshots], so Hilt and the code that Hilt does
 * not reach share a single instance and a single DataStore on the file.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsStoreModule {
    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsSnapshots.repository(context)

    @Provides
    @Singleton
    fun logSwitches(repository: SettingsRepository): LogSwitches =
        SnapshotLogSwitches(repository.snapshot)
}
