package io.github.magalz.borealis.nvhttp

import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.TestIdentity
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

private const val BACKLOG = 50
private const val HOST_ALIAS = "host"

/** One request as the host read it. */
internal class SeenRequest(
    val scheme: String,
    val target: String,
    val headers: Map<String, String>,
    val clientCertificates: List<X509Certificate>,
    val connection: Int,
) {
    val path: String
        get() = target.substringBefore('?')

    /** The query as the client sent it: names and values in order, not decoded. */
    val query: List<Pair<String, String>> =
        target
            .substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to it.substringAfter('=', "") }

    val names: List<String>
        get() = query.map { it.first }

    fun value(name: String): String? = query.firstOrNull { it.first == name }?.second

    /** `scheme target`, as in `https /serverinfo?uniqueid=...`. */
    override fun toString(): String = "$scheme $target"
}

/** The bytes a host writes for one request. */
internal class HostReply private constructor(val bytes: ByteArray?, val closeAfter: Boolean) {
    companion object {
        /** A well-framed answer; the connection stays open unless the client asks to close it. */
        fun status(
            code: Int,
            reason: String,
            body: ByteArray,
            contentType: String = "text/xml",
        ): HostReply =
            HostReply(
                ("HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\n" +
                        "Content-Length: ${body.size}\r\n\r\n")
                    .toByteArray(Charsets.ISO_8859_1) + body,
                closeAfter = false,
            )

        fun xml(body: String): HostReply = status(200, "OK", body.toByteArray(Charsets.UTF_8))

        /** `<root status_code="200">inner</root>`, the shape of every answer of a healthy host. */
        fun root(inner: String): HostReply = xml("""<root status_code="200">$inner</root>""")

        /** `<root status_code="code" status_message="message"/>`, an answer that reports a failure. */
        fun failure(code: String, message: String? = null): HostReply =
            xml(
                """<root status_code="$code"""" +
                    message?.let { """ status_message="$it"""" }.orEmpty() +
                    "/>"
            )

        /** Exactly these bytes, then the connection closes. */
        fun raw(bytes: ByteArray): HostReply = HostReply(bytes, closeAfter = true)

        /** The request is read and never answered. */
        val HOLD = HostReply(null, closeAfter = true)

        val NOT_FOUND: HostReply = failure("404")
    }
}

/**
 * A host on the loopback interface that answers over plain HTTP and over TLS, with the certificate
 * of [identity] (which a test can change between two requests). It records every request as it
 * arrived, including the order of the query parameters, keeps a connection open between requests
 * and counts the connections.
 *
 * Answers come from [routes] (the path, for one scheme or for both) or from [handler].
 */
internal class NvFakeHost(@Volatile var identity: TestIdentity = TestIdentities.hostRsa) :
    Closeable {
    /** Certificates that follow the one of [identity] in the chain that the host sends. */
    @Volatile var chainExtras: List<X509Certificate> = emptyList()

    /** False: the host has no certificate for the handshake, which ends with an alert. */
    @Volatile var offersCertificate: Boolean = true

    private val http = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
    private val https: SSLServerSocket = tlsServer()
    private val closed = CountDownLatch(1)
    private val accepted = AtomicInteger()
    private val routes = ConcurrentHashMap<String, HostReply>()

    val httpPort: Int
        get() = http.localPort

    val httpsPort: Int
        get() = https.localPort

    val requests = CopyOnWriteArrayList<SeenRequest>()

    /** The handshakes that failed and the connections that broke, on the host side. */
    val failures = CopyOnWriteArrayList<Throwable>()

    val connections: Int
        get() = accepted.get()

    @Volatile
    var handler: (SeenRequest) -> HostReply = { request ->
        routes["${request.scheme} ${request.path}"]
            ?: routes["* ${request.path}"]
            ?: HostReply.NOT_FOUND
    }

    /** Answers [path] with [reply], over [scheme] (`http` or `https`) or over both. */
    fun answer(path: String, reply: HostReply, scheme: String = "*") {
        routes["$scheme $path"] = reply
    }

    init {
        serve(http, "http")
        serve(https, "https")
    }

    private fun tlsServer(): SSLServerSocket {
        val keys =
            object : X509ExtendedKeyManager() {
                override fun chooseServerAlias(
                    keyType: String?,
                    issuers: Array<out Principal>?,
                    socket: Socket?,
                ): String? = HOST_ALIAS.takeIf { offersCertificate }

                override fun chooseEngineServerAlias(
                    keyType: String?,
                    issuers: Array<out Principal>?,
                    engine: javax.net.ssl.SSLEngine?,
                ): String? = HOST_ALIAS.takeIf { offersCertificate }

                override fun getCertificateChain(alias: String?): Array<X509Certificate> =
                    (listOf(identity.certificate) + chainExtras).toTypedArray()

                override fun getPrivateKey(alias: String?): PrivateKey = identity.keyPair.private

                override fun getServerAliases(
                    keyType: String?,
                    issuers: Array<out Principal>?,
                ): Array<String> = arrayOf(HOST_ALIAS)

                override fun chooseClientAlias(
                    keyType: Array<out String>?,
                    issuers: Array<out Principal>?,
                    socket: Socket?,
                ): String? = null

                override fun getClientAliases(
                    keyType: String?,
                    issuers: Array<out Principal>?,
                ): Array<String>? = null
            }
        // Any client certificate is accepted: the tests look at which one was presented.
        val clients =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    Unit

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw UnsupportedOperationException()
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val context =
            SSLContext.getInstance("TLS").apply { init(arrayOf(keys), arrayOf(clients), null) }
        return (context.serverSocketFactory.createServerSocket(
                0,
                BACKLOG,
                InetAddress.getLoopbackAddress(),
            ) as SSLServerSocket)
            .also { it.needClientAuth = true }
    }

    private fun serve(server: ServerSocket, scheme: String) {
        thread(isDaemon = true, name = "nv-fake-host-$scheme") {
            while (!server.isClosed) {
                val socket =
                    try {
                        server.accept()
                    } catch (ignored: IOException) {
                        // The host was closed.
                        break
                    }
                val connection = accepted.incrementAndGet()
                thread(isDaemon = true, name = "nv-fake-host-$scheme-$connection") {
                    socket.use { handle(it, scheme, connection) }
                }
            }
        }
    }

    private fun handle(socket: Socket, scheme: String, connection: Int) {
        try {
            val certificates =
                if (socket is SSLSocket) {
                    socket.startHandshake()
                    try {
                        socket.session.peerCertificates.filterIsInstance<X509Certificate>()
                    } catch (ignored: SSLPeerUnverifiedException) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            while (true) {
                val request = readRequest(input, scheme, certificates, connection) ?: return
                requests += request
                val reply = handler(request)
                val bytes = reply.bytes
                if (bytes == null) {
                    closed.await()
                    return
                }
                socket.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
                if (reply.closeAfter || request.headers["connection"] == "close") return
            }
        } catch (e: IOException) {
            // A handshake that the client refused, or a client that went away.
            failures += e
        } catch (e: RuntimeException) {
            failures += e
        }
    }

    private fun readRequest(
        input: java.io.BufferedReader,
        scheme: String,
        certificates: List<X509Certificate>,
        connection: Int,
    ): SeenRequest? {
        val target = input.readLine()?.split(' ')?.get(1) ?: return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: return null
            if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        return SeenRequest(scheme, target, headers, certificates, connection)
    }

    override fun close() {
        closed.countDown()
        http.close()
        https.close()
    }
}
