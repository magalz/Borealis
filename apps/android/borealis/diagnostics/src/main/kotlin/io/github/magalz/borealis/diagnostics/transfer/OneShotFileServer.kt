package io.github.magalz.borealis.diagnostics.transfer

import java.io.File
import java.net.InetAddress

/** Where a transfer stands; the first four are final except [WAITING]. */
internal enum class TransferStatus {
    WAITING,
    DOWNLOADED,
    EXPIRED,
    STOPPED,
}

/** Serves one file once, over plain HTTP, behind a random token. */
internal class OneShotFileServer(
    private val file: File,
    private val bindAddress: InetAddress,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
) {
    @Volatile var status: TransferStatus = TransferStatus.WAITING

    val isRunning: Boolean
        get() = TODO()

    /** Binds an ephemeral port and starts serving; returns the link to show. */
    fun start(): String = TODO()

    fun remainingMillis(): Long = TODO()

    fun stop(): Unit = TODO()

    companion object {
        const val THREAD_NAME = "borealis-diagnostic-transfer"
        const val DEFAULT_LIFETIME_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

internal fun htmlEscape(text: String): String = TODO()

internal fun headerSafeName(name: String): String = TODO()
