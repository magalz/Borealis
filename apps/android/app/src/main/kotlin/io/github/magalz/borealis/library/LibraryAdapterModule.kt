package io.github.magalz.borealis.library

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.library.domain.StreamLauncher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Binds the Library contracts and their seams to the adapters over the upstream services. */
@Suppress("TooManyFunctions") // One declarative binding per interface.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryAdapterModule {
    @Binds abstract fun hostRepository(impl: ComputerHostRepository): HostRepository

    @Binds abstract fun pairingService(impl: NvPairingService): PairingService

    @Binds abstract fun appRepository(impl: ComputerAppRepository): AppRepository

    @Binds abstract fun streamLauncher(impl: GameStreamLauncher): StreamLauncher

    @Binds abstract fun legacyBridge(impl: UpstreamLegacyBridge): LegacyBridge

    @Binds abstract fun cmsConnector(impl: ServiceCmsConnector): CmsConnector

    @Binds abstract fun cryptoWarmUp(impl: AndroidCryptoWarmUp): CryptoWarmUp

    @Binds abstract fun hostClientFactory(impl: NvHostClientFactory): HostClientFactory

    @Binds abstract fun hostTexts(impl: ResourceHostTexts): HostTexts

    @Binds abstract fun pairingErrorMapper(impl: UpstreamPairingErrorMapper): PairingErrorMapper

    @Binds abstract fun wakeSender(impl: UpstreamWakeSender): WakeSender

    @Binds abstract fun boxArtCache(impl: DiskBoxArtCache): BoxArtCache

    @Binds abstract fun localNetworks(impl: DeviceLocalNetworks): LocalNetworks

    @Binds abstract fun hostShortcuts(impl: UpstreamHostShortcuts): HostShortcuts

    @Binds abstract fun shortcutCleanup(impl: UpstreamHostShortcuts): ShortcutCleanup

    @Binds abstract fun upstreamStartup(impl: UpstreamLauncherStartup): UpstreamStartup

    @Binds abstract fun glRendererProbe(impl: GlSurfaceRendererProbe): GlRendererProbe

    companion object {
        @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
