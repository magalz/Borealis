package io.github.magalz.borealis.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.OscLayoutReset

/**
 * Binds the settings contracts that the app implements. The device facts are not scoped: a caller
 * asks again when the display changes.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsAdapterModule {
    @Binds abstract fun deviceFactsSource(impl: AndroidDeviceFactsSource): DeviceFactsSource

    @Binds abstract fun decoderProbe(impl: MediaCodecDecoderProbe): DecoderProbe

    @Binds abstract fun oscLayoutReset(impl: AndroidOscLayoutReset): OscLayoutReset
}
