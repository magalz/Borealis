package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one connection to the computer manager service. The service keeps a single polling listener,
 * so nothing else may call `startPolling`: this class owns it, counts who needs polling and fans
 * the updates out as copies.
 */
@Singleton
internal class ComputerManagerConnection
@Inject
constructor(
    private val connector: CmsConnector,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The last details of every host seen, by id. Copies: the service mutates its own objects. */
    val updates: StateFlow<Map<String, ComputerDetails>> = MutableStateFlow(emptyMap())

    /** The binder when the service is already connected; for callers that cannot suspend. */
    val connectedBinder: CmsBinder?
        get() = TODO()

    suspend fun binder(): CmsBinder = TODO()

    /** [updates] while collected; the first collector starts polling, the last one stops it. */
    fun hostUpdates(): Flow<Map<String, ComputerDetails>> = TODO()

    /** The details of [uuid] while collected; also polls the host's app list. */
    fun appListUpdates(uuid: String): Flow<ComputerDetails?> = TODO()

    /** Stops polling and waits for it to stop around [block], then resumes it if still needed. */
    suspend fun <T> pausePolling(block: suspend () -> T): T = TODO()

    /** A copy of the details of [hostId], from the last update or else from the service. */
    suspend fun lookup(hostId: String): ComputerDetails? = TODO()

    /** Polls the host in [details] once, adds it when it answers; true when it did. */
    suspend fun addComputer(details: ComputerDetails): Boolean = TODO()

    suspend fun remove(details: ComputerDetails): Unit = TODO()

    /** Pins [cert] for [uuid] and makes the next poll read the pairing state again. */
    suspend fun pinCertificate(uuid: String, cert: X509Certificate?): Unit = TODO()
}
