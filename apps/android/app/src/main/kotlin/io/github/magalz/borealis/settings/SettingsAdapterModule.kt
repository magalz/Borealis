package io.github.magalz.borealis.settings

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.OscLayoutReset
import javax.inject.Singleton

/**
 * Binds the settings contracts that the app implements. The device facts are not scoped: a caller
 * asks again when the display changes. The decoder probe is a singleton, because the codecs of a
 * device do not change while the app runs.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsAdapterModule {
    @Binds abstract fun deviceFactsSource(impl: AndroidDeviceFactsSource): DeviceFactsSource

    @Binds abstract fun oscLayoutReset(impl: AndroidOscLayoutReset): OscLayoutReset

    companion object {
        @Provides
        @Singleton
        fun decoderProbe(probe: MediaCodecDecoderProbe): DecoderProbe = CachedDecoderProbe(probe)
    }
}
