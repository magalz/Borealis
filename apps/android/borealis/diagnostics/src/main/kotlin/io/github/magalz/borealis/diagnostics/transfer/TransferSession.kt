package io.github.magalz.borealis.diagnostics.transfer

import android.content.Context
import android.net.ConnectivityManager
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.File
import java.io.IOException
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

/** The QR code of the link: [size] by [size] modules, quiet margin included; true is dark. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && size == other.size && modules.contentEquals(other.modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
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
    ) : TransferState

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

    /** Closes the earlier link, if any, and opens a new one that serves [file] once. */
    fun start(file: File) {
        stop()
        val local = address()
        if (local == null) {
            mutableState.value = TransferState.NoNetwork
            return
        }
        val opened = OneShotFileServer(file, local, clock)
        val waiting =
            try {
                open(opened, file)
            } catch (error: IOException) {
                BorealisLog.e(LogTag.APP, "could not start the diagnostic transfer", error)
                null
            } catch (error: WriterException) {
                BorealisLog.e(LogTag.APP, "could not draw the diagnostic transfer code", error)
                null
            }
        if (waiting == null) {
            opened.stop()
            mutableState.value = TransferState.Failed
            return
        }
        synchronized(lock) {
            server = opened
            ticker = scope.launch { tick(opened, waiting) }
        }
        mutableState.value = waiting
    }

    /** Closes the link at once. A download or expiry that already happened stays as it is. */
    fun stop() {
        val closing: OneShotFileServer?
        val job: Job?
        synchronized(lock) {
            closing = server
            job = ticker
            server = null
            ticker = null
        }
        job?.cancel()
        if (closing != null) {
            closing.stop()
            mutableState.value = finished(closing.status)
        }
    }

    private fun open(opened: OneShotFileServer, file: File): TransferState.Waiting {
        val link = opened.start()
        return TransferState.Waiting(
            link = link,
            qr = TransferQr.encode(link).toQrMatrix(),
            fileName = file.name,
            sizeBytes = file.length(),
            remainingMillis = opened.remainingMillis(),
        )
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
