package io.github.magalz.borealis.library

import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** [PairingService] over upstream `PairingManager`, with the audit and the errors of PcView. */
@Singleton
internal class NvPairingService
@Inject
constructor(
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    private val texts: HostTexts,
    private val errors: PairingErrorMapper,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PairingService {
    override suspend fun prepare(hostId: String): PairingInstructions? = TODO()

    override suspend fun pair(hostId: String, pin: String, restarted: Boolean): PairingOutcome =
        TODO()

    override fun recordExpired(hostId: String): Unit = TODO()
}
