package io.github.magalz.borealis.library

import com.limelight.computers.ComputerManagerListener
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
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
     * The host poll of the service copies a fresh result without an app list over the list that the
     * app list poller stored (`ComputerDetails.update`), so the last list received is kept here;
     * upstream's `AppView` ignores such a null for the same reason.
     */
    private val listener = ComputerManagerListener { details ->
        details.uuid?.let { id -> publish(id) { details.snapshot() } }
    }

    /** Stores the copy [make] returns for [id], with the last app list when the copy has none. */
    private inline fun publish(id: String, make: () -> ComputerDetails) {
        state.update { known ->
            val copy = make()
            if (copy.rawAppList == null) copy.rawAppList = known[id]?.rawAppList
            known + (id to copy)
        }
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
        // Set inside the locked block, so that a cancellation that arrives after the block
        // finished but before this coroutine resumed still undoes what the block did.
        val added = AtomicBoolean(false)
        try {
            locked {
                gate.addCollector(connected())
                added.set(true)
            }
            emitAll(state)
        } finally {
            if (added.get()) {
                withContext(NonCancellable) { locked { gate.removeCollector(connected()) } }
            }
        }
    }

    /** The details of [uuid] while collected; also polls the host's app list. */
    fun appListUpdates(uuid: String): Flow<ComputerDetails?> = flow {
        val added = AtomicBoolean(false)
        val sharing = AtomicBoolean(false)
        try {
            locked {
                val binder = connected()
                gate.addCollector(binder)
                added.set(true)
                sharing.set(pollers.start(binder, uuid))
            }
            emitAll(state.map { it[uuid] })
        } finally {
            if (added.get()) {
                withContext(NonCancellable) {
                    locked {
                        if (sharing.get()) pollers.stop(uuid)
                        gate.removeCollector(connected())
                    }
                }
            }
        }
    }

    /** Stops polling and waits for it to stop around [block], then resumes it if still needed. */
    suspend fun <T> pausePolling(block: suspend () -> T): T {
        val binder = binder()
        val paused = AtomicBoolean(false)
        try {
            locked {
                gate.pause(binder)
                paused.set(true)
            }
            withContext(io) { binder.waitForPollingStopped() }
            return block()
        } finally {
            if (paused.get()) withContext(NonCancellable) { locked { gate.resume(binder) } }
        }
    }

    /** A copy of the details of [hostId], from the last update or else from the service. */
    suspend fun lookup(hostId: String): ComputerDetails? =
        state.value[hostId]
            ?: binder().let { binder ->
                withContext(io) { binder.getComputer(hostId)?.snapshot() }
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

    /**
     * Pins [cert] for [uuid] after a successful pairing. The service reads the pairing state again
     * on its next poll; until then the host is published as paired with its state unknown, so that
     * a screen opened right after the pairing does not see it as unpaired.
     */
    suspend fun pinCertificate(uuid: String, cert: X509Certificate?) {
        val binder = binder()
        val paired =
            withContext(io) {
                val live = binder.getComputer(uuid)
                if (cert != null && live != null) live.serverCert = cert
                binder.invalidateStateForComputer(uuid)
                live?.snapshot()
            }
        if (paired != null && cert != null) {
            paired.pairState = PairState.PAIRED
            paired.state = ComputerDetails.State.UNKNOWN
            paired.serverCert = cert
            publish(uuid) { paired }
        }
    }

    /** Runs [block] on the IO dispatcher while holding [lock]: it may call the service. */
    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock {
        withContext(io) { block() }
    }

    private suspend fun connected(): CmsBinder = bound ?: connector.connect().also { bound = it }
}
