package io.github.magalz.borealis.library

import android.os.Build
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.host.pairing.PairingAudit
import io.github.magalz.borealis.host.pairing.PairingHints
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

/**
 * [PairingService] over upstream `PairingManager`, with the audit and the errors of the 12.2 host
 * list.
 */
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
    override suspend fun prepare(hostId: String): PairingInstructions? {
        val details = connection.lookup(hostId) ?: return null
        val address = details.activeAddress
        return PairingInstructions(
            pin = PairingManager.generatePinString(),
            webUiUrl = address?.let { PairingHints.webUiUrl(it.address, it.port) },
            deviceName = PairingHints.suggestedDeviceName(Build.MODEL),
        )
    }

    override suspend fun pair(hostId: String, pin: String, restarted: Boolean): PairingOutcome {
        val details = connection.lookup(hostId)
        val address = details?.activeAddress
        if (details == null || details.state == ComputerDetails.State.OFFLINE || address == null) {
            return PairingOutcome.HostOffline
        }
        // Read once: the polling thread may clear the address while the pairing runs.
        val host = BorealisAudit.hostLabel(details.name, address.address)
        return connection.pausePolling { pairPaused(details, pin, restarted, host) }
    }

    override fun recordExpired(hostId: String) {
        val details = connection.updates.value[hostId]
        PairingAudit.expired(
            BorealisAudit.hostLabel(details?.name, details?.activeAddress?.address)
        )
    }

    /** Runs with polling paused. Cancelling it aborts the request that waits for the PIN. */
    private suspend fun pairPaused(
        details: ComputerDetails,
        pin: String,
        restarted: Boolean,
        host: String?,
    ): PairingOutcome = coroutineScope {
        val client =
            try {
                clients.create(details, connection.binder().uniqueId)
            } catch (e: IOException) {
                return@coroutineScope failure(e, restarted, host)
            }
        val work = async(io) { exchange(client, details, pin, restarted, host) }
        try {
            work.await()
        } catch (e: CancellationException) {
            // The request blocks until the user types the PIN: abort it, and again until the
            // exchange has really ended, in case it was between two requests.
            withContext(NonCancellable) {
                while (!work.isCompleted) {
                    client.cancelPendingRequests()
                    delay(ABORT_RETRY_MS)
                }
            }
            throw e
        }
    }

    /** The exchange of the 12.2 pairing. Blocks; the work is cancelled together with its caller. */
    private suspend fun exchange(
        client: HostClient,
        details: ComputerDetails,
        pin: String,
        restarted: Boolean,
        host: String?,
    ): PairingOutcome =
        try {
            if (client.pairState() == PairState.PAIRED) {
                PairingOutcome.Paired
            } else {
                PairingAudit.started(host, restarted)
                val result = client.pair(pin)
                // A cancelled pairing stores nothing, even if the host had just accepted it.
                currentCoroutineContext().ensureActive()
                PairingAudit.finished(host, result.state.name)
                BorealisLog.i(LogTag.PAIR, "pairing ended", "state" to result.state.name)
                outcomeOf(result, details)
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            failure(e, restarted, host)
        } catch (e: XmlPullParserException) {
            currentCoroutineContext().ensureActive()
            failure(e, restarted, host)
        }

    private suspend fun outcomeOf(result: PairResult, details: ComputerDetails): PairingOutcome =
        when (result.state) {
            PairState.PAIRED -> {
                connection.pinCertificate(checkNotNull(details.uuid), result.serverCert)
                PairingOutcome.Paired
            }
            PairState.PIN_WRONG -> PairingOutcome.PinWrong
            PairState.ALREADY_IN_PROGRESS -> PairingOutcome.AlreadyInProgress
            PairState.FAILED ->
                PairingOutcome.Failed(
                    if (details.runningGameId != 0) texts.pairInGame() else texts.pairFailed()
                )
            PairState.NOT_PAIRED -> PairingOutcome.Failed(null)
        }

    private fun failure(error: Exception, restarted: Boolean, host: String?): PairingOutcome =
        when (error) {
            is UnknownHostException -> PairingOutcome.Failed(texts.unknownHost())
            is FileNotFoundException -> PairingOutcome.Failed(texts.notFound())
            else -> {
                val hostError =
                    (error as? HostHttpResponseException)?.let {
                        HostError(it.errorCode, it.errorMessage)
                    }
                val described = errors.describe(hostError, error.message, restarted, host)
                if (described.restart) {
                    PairingOutcome.RestartNeeded
                } else {
                    PairingOutcome.Failed(described.message)
                }
            }
        }

    private companion object {
        const val ABORT_RETRY_MS = 50L
    }
}
