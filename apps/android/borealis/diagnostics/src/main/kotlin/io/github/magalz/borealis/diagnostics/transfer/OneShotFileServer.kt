package io.github.magalz.borealis.diagnostics.transfer

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Where a transfer stands. Everything but [WAITING] is final. */
internal enum class TransferStatus {
    WAITING,
    DOWNLOADED,
    EXPIRED,
    STOPPED,
}

/**
 * Serves one file once over plain HTTP, so a phone on the same network can fetch it after scanning
 * a QR code (ADR 0051). The link is `http://<address>:<port>/<token>`; the token is 128 random
 * bits.
 *
 * - `GET /<token>` is a small page with a download link. It does not use up the download, because
 *   camera and QR apps may fetch a link to preview it.
 * - `GET /<token>/<file name>` is the zip. After the first complete response the link is used up:
 *   later requests get 410 and the server stops.
 * - Anything else is 404 (wrong token or path), 405 (not GET), 400, 408 or 431.
 *
 * One thread accepts and serves one connection at a time, so a second connection waits in the
 * backlog. It stops at the first of a complete download, [lifetimeMillis] (on [clock]) and [stop].
 */
internal class OneShotFileServer(
    private val file: File,
    private val bindAddress: InetAddress,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
) {
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    private var activeClient: Socket? = null
    private var link: String? = null
    private var token: ByteArray = ByteArray(0)
    private var expiresAtMillis = 0L

    @Volatile
    var status: TransferStatus = TransferStatus.WAITING
        private set

    val isRunning: Boolean
        get() = synchronized(lock) { thread?.isAlive == true }

    /**
     * Binds an ephemeral port on the bind address and starts serving; returns the link to show.
     * Calling it again returns the same link. Throws if the address cannot be bound.
     */
    fun start(): String =
        synchronized(lock) {
            link?.let {
                return it
            }
            check(status == TransferStatus.WAITING) { "The transfer was stopped" }
            val socket = ServerSocket(0, BACKLOG, bindAddress)
            val tokenText = newToken()
            token = tokenText.toByteArray(Charsets.US_ASCII)
            expiresAtMillis = clock() + lifetimeMillis
            serverSocket = socket
            val url = "http://${bindAddress.hostAddress}:${socket.localPort}/$tokenText"
            link = url
            thread =
                Thread({ serve(socket) }, THREAD_NAME).apply {
                    isDaemon = true
                    start()
                }
            BorealisLog.i(
                LogTag.APP,
                "diagnostic transfer started",
                "port" to socket.localPort,
                "expiresInSeconds" to lifetimeMillis / MILLIS_PER_SECOND,
            )
            url
        }

    /** Time left before the link expires, never negative. */
    fun remainingMillis(): Long =
        synchronized(lock) {
            if (link == null) lifetimeMillis else maxOf(0L, expiresAtMillis - clock())
        }

    /**
     * Stops the server: closes the listening socket and the connection being served, which frees
     * the thread wherever it is blocked, and waits for it. Safe to call again, from any thread.
     */
    fun stop() {
        val toClose =
            synchronized(lock) {
                finish(TransferStatus.STOPPED)
                listOfNotNull(serverSocket, activeClient)
            }
        toClose.forEach { runCatching { it.close() } }
        val running = synchronized(lock) { thread }
        if (running != null && running !== Thread.currentThread()) {
            try {
                running.join(JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun finish(final: TransferStatus) {
        synchronized(lock) { if (status == TransferStatus.WAITING) status = final }
    }

    /** False once the server was stopped or its lifetime ran out. */
    private fun keepServing(): Boolean {
        if (clock() >= expiresAtMillis) finish(TransferStatus.EXPIRED)
        return status == TransferStatus.WAITING || status == TransferStatus.DOWNLOADED
    }

    private fun serve(socket: ServerSocket) {
        try {
            while (keepServing()) {
                // Once the file is gone, only connections already queued are answered (with 410).
                val downloaded = status == TransferStatus.DOWNLOADED
                socket.soTimeout = if (downloaded) DRAIN_MILLIS else POLL_MILLIS
                val client = socket.acceptOrNull()
                if (client != null) handle(client) else if (downloaded) break
            }
        } catch (_: IOException) {
            // The socket was closed by stop(), or it failed; either way the server is over.
        } finally {
            runCatching { socket.close() }
            finish(TransferStatus.STOPPED)
            BorealisLog.i(LogTag.APP, "diagnostic transfer stopped", "reason" to status.name)
        }
    }

    private fun handle(client: Socket) {
        val accepted =
            synchronized(lock) {
                (status != TransferStatus.STOPPED).also { if (it) activeClient = client }
            }
        try {
            if (accepted) {
                client.soTimeout = readTimeoutMillis
                respond(client)
            }
        } catch (_: IOException) {
            // The peer went away; nothing to answer.
        } finally {
            synchronized(lock) { activeClient = null }
            // FIN rather than RST, so the last bytes of a reply are not lost.
            runCatching { client.shutdownOutput() }
            runCatching { client.close() }
        }
    }

    private fun respond(client: Socket) {
        try {
            val input = BufferedInputStream(client.getInputStream(), HEAD_BUFFER)
            TransferHttp.readRequest(input, readTimeoutMillis)?.let { route(client, it.path) }
        } catch (rejected: HttpRejection) {
            reply(client, rejected.status, listOfNotNull(rejected.header))
        } catch (_: SocketTimeoutException) {
            reply(client, HttpStatus.REQUEST_TIMEOUT)
        }
    }

    private fun route(client: Socket, path: String) {
        val segments = path.removePrefix("/").split('/')
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val isPage = segments.size == 1
        val isZip = segments.size == 2 && segments[1] == encodedName
        when {
            !path.startsWith("/") || !segments[0].matchesToken(token) || !(isPage || isZip) ->
                reply(client, HttpStatus.NOT_FOUND)
            status == TransferStatus.DOWNLOADED -> reply(client, HttpStatus.GONE)
            isPage -> page(client, encodedName)
            else -> zip(client)
        }
    }

    private fun page(client: Socket, encodedName: String) {
        val tokenText = String(token, Charsets.US_ASCII)
        val html = TransferHttp.pageHtml(file.name, file.length(), "/$tokenText/$encodedName")
        reply(
            client,
            HttpStatus.OK,
            listOf("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'"),
            body = html.toByteArray(),
            contentType = "text/html; charset=utf-8",
        )
    }

    private fun zip(client: Socket) {
        val input =
            try {
                FileInputStream(file)
            } catch (error: IOException) {
                BorealisLog.e(LogTag.APP, "diagnostic transfer could not read the bundle", error)
                return reply(client, HttpStatus.SERVER_ERROR)
            }
        input.use {
            val out = client.getOutputStream()
            out.write(
                TransferHttp.head(
                    HttpStatus.OK,
                    listOf(
                        "Content-Type: application/zip",
                        "Content-Length: ${file.length()}",
                        "Content-Disposition: attachment; filename=\"${headerSafeName(file.name)}\"",
                    ),
                )
            )
            it.copyTo(out)
            out.flush()
        }
        finish(TransferStatus.DOWNLOADED)
        BorealisLog.i(LogTag.APP, "diagnostic transfer download", "status" to HttpStatus.OK.code)
    }

    companion object {
        const val THREAD_NAME = "borealis-diagnostic-transfer"
        const val DEFAULT_LIFETIME_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000

        private const val HEAD_BUFFER = 1024
        private const val BACKLOG = 4
        private const val POLL_MILLIS = 250
        private const val DRAIN_MILLIS = 500
        private const val JOIN_MILLIS = 2_000L
        private const val TOKEN_BYTES = 16
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MILLIS_PER_SECOND = 1_000L

        private val RANDOM = SecureRandom()

        /** 128 random bits as lowercase hex: URL-safe without any encoding. */
        private fun newToken(): String =
            ByteArray(TOKEN_BYTES).also(RANDOM::nextBytes).joinToString("") { "%02x".format(it) }
    }
}

private fun ServerSocket.acceptOrNull(): Socket? =
    try {
        accept()
    } catch (_: SocketTimeoutException) {
        null
    }

/** Compares in constant time, so the answer does not tell how much of a guess was right. */
private fun String.matchesToken(token: ByteArray): Boolean =
    MessageDigest.isEqual(toByteArray(Charsets.US_ASCII), token)

private fun reply(
    client: Socket,
    status: HttpStatus,
    extraHeaders: List<String> = emptyList(),
    body: ByteArray = status.reason.toByteArray(),
    contentType: String = "text/plain; charset=utf-8",
) {
    TransferHttp.reply(client.getOutputStream(), status, extraHeaders, body, contentType)
    BorealisLog.i(LogTag.APP, "diagnostic transfer request", "status" to status.code)
}
