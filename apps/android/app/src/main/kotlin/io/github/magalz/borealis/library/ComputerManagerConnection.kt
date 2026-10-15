package io.github.magalz.borealis.library

import com.limelight.computers.ComputerManagerListener
import com.limelight.nvstream.http.ComputerDetails
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val state = MutableStateFlow<Map<String, ComputerDetails>>(emptyMap())

    /**
     * Runs on a polling thread of the service: stores a copy, which the service goes on mutating.
     */
    private val listener = ComputerManagerListener { details ->
        details.uuid?.let { id -> state.update { it + (id to ComputerDetails(details)) } }
    }

    // The gate, the pollers and the binder are only changed with the lock held.
    private val lock = Mutex()
    private val gate = PollingGate(listener)
    private val pollers = SharedAppListPollers()
    @Volatile private var bound: CmsBinder? = null

    /** The last details of every host seen, by id. Copies: the service mutates its own objects. */
    val updates: StateFlow<Map<String, ComputerDetails>> = state.asStateFlow()

    /** The binder when the service is already connected; for callers that cannot suspend. */
    val connectedBinder: CmsBinder?
        get() = bound

    suspend fun binder(): CmsBinder = bound ?: locked { connected() }

    /** [updates] while collected; the first collector starts polling, the last one stops it. */
    fun hostUpdates(): Flow<Map<String, ComputerDetails>> = flow {
        locked { gate.addCollector(connected()) }
        try {
            emitAll(state)
        } finally {
            withContext(NonCancellable) { locked { gate.removeCollector(connected()) } }
        }
    }

    /** The details of [uuid] while collected; also polls the host's app list. */
    fun appListUpdates(uuid: String): Flow<ComputerDetails?> = flow {
        val sharing = locked {
            val binder = connected()
            gate.addCollector(binder)
            pollers.start(binder, uuid)
        }
        try {
            emitAll(state.map { it[uuid] })
        } finally {
            withContext(NonCancellable) {
                locked {
                    if (sharing) pollers.stop(uuid)
                    gate.removeCollector(connected())
                }
            }
        }
    }

    /** Stops polling and waits for it to stop around [block], then resumes it if still needed. */
    suspend fun <T> pausePolling(block: suspend () -> T): T {
        val binder = binder()
        locked { gate.pause(binder) }
        try {
            withContext(io) { binder.waitForPollingStopped() }
            return block()
        } finally {
            withContext(NonCancellable) { locked { gate.resume(binder) } }
        }
    }

    /** A copy of the details of [hostId], from the last update or else from the service. */
    suspend fun lookup(hostId: String): ComputerDetails? =
        state.value[hostId]
            ?: binder().let { binder ->
                withContext(io) { binder.getComputer(hostId)?.let { ComputerDetails(it) } }
            }

    /** Polls the host in [details] once, adds it when it answers; true when it did. */
    suspend fun addComputer(details: ComputerDetails): Boolean {
        val binder = binder()
        val added = runInterruptible(io) { binder.addComputerBlocking(details) }
        if (added) listener.notifyComputerUpdated(details)
        return added
    }

    suspend fun remove(details: ComputerDetails) {
        val binder = binder()
        withContext(io) { binder.removeComputer(details) }
        details.uuid?.let { id -> state.update { it - id } }
    }

    /** Pins [cert] for [uuid] and makes the next poll read the pairing state again. */
    suspend fun pinCertificate(uuid: String, cert: X509Certificate?) {
        val binder = binder()
        withContext(io) {
            if (cert != null) binder.getComputer(uuid)?.serverCert = cert
            binder.invalidateStateForComputer(uuid)
        }
    }

    /** Runs [block] on the IO dispatcher while holding [lock]: it may call the service. */
    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock {
        withContext(io) { block() }
    }

    private suspend fun connected(): CmsBinder = bound ?: connector.connect().also { bound = it }
}
