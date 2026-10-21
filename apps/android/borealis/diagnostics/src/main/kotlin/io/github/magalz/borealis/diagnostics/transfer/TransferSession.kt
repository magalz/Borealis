package io.github.magalz.borealis.diagnostics.transfer

import android.content.Context
import android.net.ConnectivityManager
import com.google.zxing.common.BitMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A QR code: [size] by [size] modules, quiet margin included; true is dark. */
class QrMatrix internal constructor(val size: Int, modules: BooleanArray) {
    private val modules = modules.copyOf()

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && size == other.size && modules.contentEquals(other.modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()

    companion object {
        /**
         * The QR code of [text], at error correction M and the smallest size that holds it, with
         * the margin of the transfer link.
         */
        fun encode(text: String): QrMatrix = TransferQr.encode(text).toQrMatrix()
    }
}

private fun BitMatrix.toQrMatrix(): QrMatrix =
    QrMatrix(width, BooleanArray(width * height) { this[it % width, it / width] })

/** Where a one-shot transfer stands. Everything after [Waiting] is final. */
sealed interface TransferState {
    /** No transfer was started. */
    data object Idle : TransferState

    /** There is no private IPv4 address a phone could reach, so no link was started. */
    data object NoNetwork : TransferState

    /**
     * The link is open: [link] is the text to show, [qr] its QR code, and [remainingMillis] the
     * time before it expires.
     */
    data class Waiting(
        val link: String,
        val qr: QrMatrix,
        val fileName: String,
        val sizeBytes: Long,
        val remainingMillis: Long,
    ) : TransferState {
        /** Without the link: it holds the one-time token, which must not reach a log. */
        override fun toString(): String =
            "Waiting(fileName=$fileName, sizeBytes=$sizeBytes, remainingMillis=$remainingMillis)"
    }

    /** The file was downloaded and the link is closed. */
    data object Downloaded : TransferState

    /** The link expired before a download and is closed. */
    data object Expired : TransferState

    /** The transfer was stopped before a download. */
    data object Stopped : TransferState

    /** The link could not be started; the cause is in the log. */
    data object Failed : TransferState
}

/**
 * One "Send to phone" transfer (ADR 0051): serves a file once on the private network and reports
 * its [state], with the countdown refreshed every second. [start] and [stop] are meant for one
 * caller at a time, such as the dialog that shows the state. [address] is the private IPv4 address
 * to bind (null when there is none), and [clock] is a monotonic millisecond clock that drives the
 * expiry and the countdown. The ticker runs in [scope].
 */
@Singleton
class TransferSession
internal constructor(
    private val address: () -> InetAddress?,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        address = {
            context.getSystemService(ConnectivityManager::class.java)?.let(::activeLocalIpv4)
        },
        clock = ::monotonicMillis,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val lock = Any()
    private var server: OneShotFileServer? = null
    private var ticker: Job? = null
    private val mutableState = MutableStateFlow<TransferState>(TransferState.Idle)

    val state: StateFlow<TransferState>
        get() = mutableState

    /**
     * Closes the earlier link, if any, and opens a new one that serves [file] once. The state goes
     * from the old one through [TransferState.Idle] to [TransferState.Waiting],
     * [TransferState.NoNetwork] or [TransferState.Failed]; the closed link is not reported as
     * stopped, and its final state is not shown again.
     */
    fun start(file: File) {
        closeCurrent()
        mutableState.value = TransferState.Idle
        mutableState.value =
            runCatching { open(file) }
                .getOrElse { error ->
                    BorealisLog.e(LogTag.APP, "could not start the diagnostic transfer", error)
                    TransferState.Failed
                }
    }

    /** Closes the link at once. A download or expiry that already happened stays as it is. */
    fun stop() {
        val closing = detach()
        if (closing != null) {
            closing.stop()
            mutableState.value = finished(closing.status)
        }
    }

    private fun closeCurrent() {
        detach()?.stop()
    }

    /** Takes the current server and its ticker away from the session; the caller closes it. */
    private fun detach(): OneShotFileServer? {
        val closing: OneShotFileServer?
        val job: Job?
        synchronized(lock) {
            closing = server
            job = ticker
            server = null
            ticker = null
        }
        job?.cancel()
        return closing
    }

    /** The state for [file]; throws when the link cannot be started. */
    private fun open(file: File): TransferState {
        if (!file.isFile || !file.canRead()) error("The bundle cannot be read")
        val local = address() ?: return TransferState.NoNetwork
        val opened = OneShotFileServer(file, local, clock)
        var handedOver = false
        try {
            val link = opened.start()
            val waiting =
                TransferState.Waiting(
                    link = link,
                    qr = QrMatrix.encode(link),
                    fileName = file.name,
                    sizeBytes = file.length(),
                    remainingMillis = opened.remainingMillis(),
                )
            synchronized(lock) {
                server = opened
                mutableState.value = waiting
                ticker = scope.launch { tick(opened, waiting) }
                handedOver = true
            }
            return waiting
        } finally {
            if (!handedOver) opened.stop()
        }
    }

    /** Refreshes the countdown, and the final state, once a second until the transfer is over. */
    private suspend fun tick(opened: OneShotFileServer, waiting: TransferState.Waiting) {
        var over = false
        while (!over) {
            delay(TICK_MILLIS)
            val status = opened.status
            over = status != TransferStatus.WAITING
            val next =
                if (over) finished(status)
                else waiting.copy(remainingMillis = opened.remainingMillis())
            synchronized(lock) { if (server === opened) mutableState.value = next }
        }
    }

    private fun finished(status: TransferStatus): TransferState =
        when (status) {
            TransferStatus.DOWNLOADED -> TransferState.Downloaded
            TransferStatus.EXPIRED -> TransferState.Expired
            TransferStatus.WAITING,
            TransferStatus.STOPPED -> TransferState.Stopped
        }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
