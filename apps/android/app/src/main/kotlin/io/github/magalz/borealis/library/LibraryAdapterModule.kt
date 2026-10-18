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
internal interface LibraryAdapterModule {
    @Binds fun hostRepository(impl: ComputerHostRepository): HostRepository

    @Binds fun pairingService(impl: NvPairingService): PairingService

    @Binds fun appRepository(impl: ComputerAppRepository): AppRepository

    @Binds fun streamLauncher(impl: GameStreamLauncher): StreamLauncher

    @Binds fun legacyBridge(impl: UpstreamLegacyBridge): LegacyBridge

    @Binds fun cmsConnector(impl: ServiceCmsConnector): CmsConnector

    @Binds fun cryptoWarmUp(impl: AndroidCryptoWarmUp): CryptoWarmUp

    @Binds fun hostClientFactory(impl: NvHostClientFactory): HostClientFactory

    @Binds fun hostTexts(impl: ResourceHostTexts): HostTexts

    @Binds fun pairingErrorMapper(impl: UpstreamPairingErrorMapper): PairingErrorMapper

    @Binds fun wakeSender(impl: UpstreamWakeSender): WakeSender

    @Binds fun boxArtCache(impl: DiskBoxArtCache): BoxArtCache

    @Binds fun appListCache(impl: DiskAppListCache): AppListCache

    @Binds fun localNetworks(impl: DeviceLocalNetworks): LocalNetworks

    @Binds fun hostShortcuts(impl: UpstreamHostShortcuts): HostShortcuts

    @Binds fun shortcutCleanup(impl: UpstreamHostShortcuts): ShortcutCleanup

    @Binds fun hiddenAppShortcuts(impl: UpstreamHostShortcuts): HiddenAppShortcuts

    @Binds fun boxArt(impl: BoxArtLoader): BoxArt

    @Binds fun upstreamStartup(impl: UpstreamLauncherStartup): UpstreamStartup

    @Binds fun glRendererProbe(impl: GlSurfaceRendererProbe): GlRendererProbe

    companion object {
        @Suppress("InjectDispatcher") // The one place in :app that names it; the rest inject it.
        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
