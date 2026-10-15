package io.github.magalz.borealis.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.WakeResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** [HostRepository] over [ComputerManagerConnection]. */
@Singleton
@Suppress("LongParameterList")
internal class ComputerHostRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    private val wake: WakeSender,
    private val subnet: SubnetCheck,
    private val hiddenApps: HiddenApps,
    private val boxArt: BoxArtCache,
    @IoDispatcher private val io: CoroutineDispatcher,
) : HostRepository {
    override val hosts: Flow<List<Host>>
        get() = TODO()

    override suspend fun addHost(address: HostAddress): AddHostResult = TODO()

    override suspend fun wake(hostId: String): WakeResult = TODO()

    override suspend fun forget(hostId: String): Unit = TODO()

    override suspend fun details(hostId: String): String? = TODO()
}
