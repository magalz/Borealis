package io.github.magalz.borealis.settings.options

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the device-specific options of the settings screen. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SettingsOptionsModule {
    @Binds fun settingsOptions(impl: DefaultSettingsOptions): SettingsOptions
}
