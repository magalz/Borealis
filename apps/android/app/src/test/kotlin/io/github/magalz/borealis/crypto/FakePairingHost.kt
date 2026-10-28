package io.github.magalz.borealis.crypto

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val AES_BLOCK = 16
private const val SECRET_LENGTH = 16
private const val BACKLOG = 50

internal fun upperHex(bytes: ByteArray): String = hexOf(bytes, HEX_DIGITS)

internal fun lowerHex(bytes: ByteArray): String = hexOf(bytes, HEX_DIGITS.lowercase())

private fun hexOf(bytes: ByteArray, digits: String): String = buildString {
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        append(digits[value ushr 4])
        append(digits[value and 0x0F])
    }
}

/** Strict, case-insensitive hex decoding: the reference that the code under test is held against. */
internal fun unhex(text: String): ByteArray {
    require(text.length % 2 == 0) { "odd hex length" }
    return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** What the host saw: one request, with the certificates that the client presented over TLS. */
internal class RecordedRequest(
    val scheme: String,
    val path: String,
    val query: Map<String, String>,
    val clientCertificates: List<X509Certificate>,
) {
    /** The pairing phase of a `/pair` request, `unpair` for `/unpair`, else the path. */
    val phase: String
        get() =
            when {
                path == "/unpair" -> "unpair"
                path != "/pair" -> path
                "phrase" in query -> query.getValue("phrase")
                else ->
                    listOf("clientchallenge", "serverchallengeresp", "clientpairingsecret")
                        .firstOrNull { it in query } ?: "unknown"
            }

    /** `scheme phase`: the sequence of these says what the client did. */
    override fun toString(): String = "$scheme $phase"
}

internal class PairingReply(val status: Int, val reason: String, val body: String) {
    companion object {
        /** A host answer with `status_code="200"` around [inner]. */
        fun root(inner: String) =
            PairingReply(200, "OK", """<root status_code="200">$inner</root>""")
    }
}

/**
 * A host that runs the four phases of the GameStream pairing with its own implementation of the
 * protocol (`javax.crypto`, `java.security`), over HTTP for the first four phases and over TLS,
 * with a client certificate, for the challenge. It checks what a real host checks: the AES key from
 * the salt and the PIN, the challenge hashes and the client signature. What it saw is in [requests].
 *
 * [intercept] decides each answer; its `default` argument computes the answer of a correct host.
 */
internal class FakePairingHost(
    private val hostIdentity: TestIdentity = TestIdentities.hostRsa,
    private val tlsIdentity: TestIdentity = hostIdentity,
    var sha256: Boolean = true,
    val pin: String = "1234",
) : Closeable {
    private val lock = Any()
    private val random = SecureRandom()
    private val http = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
    private val https: SSLServerSocket
    private var lastClientChain: List<X509Certificate> = emptyList()

    val httpPort: Int
        get() = http.localPort

    val httpsPort: Int
        get() = https.localPort

    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** The failures of the host itself and of TLS handshakes that the client refused. */
    val hostFailures = CopyOnWriteArrayList<Throwable>()

    var intercept: (RecordedRequest, () -> PairingReply) -> PairingReply = { _, default ->
        default()
    }

    /** How the host writes bytes in its answers. */
    var hex: (ByteArray) -> String = ::upperHex

    /** What the host appends to the salt before it hashes it into the AES key. */
    var pinBytes: ByteArray = pin.toByteArray(Charsets.UTF_8)

    /** The secret that the host signs; the first bytes can be chosen by the test. */
    var serverSecret: ByteArray = ByteArray(SECRET_LENGTH).also { random.nextBytes(it) }

    /** How many bytes of the server challenge the host sends after its hash. */
    var serverChallengeBytesSent: Int = SECRET_LENGTH

    /** Flips the last byte of the hash in the client challenge answer; everything else is right. */
    var flipLastHashByte: Boolean = false

    /** The verdicts of the host about the client, after the last phase. */
    var clientHashMatched: Boolean? = null
        private set

    var clientSignatureValid: Boolean? = null
        private set

    var clientCertificate: X509Certificate? = null
        private set

    private var salt = ByteArray(0)
    private var aesKey = ByteArray(0)
    private var serverChallenge = ByteArray(0)
    private var clientHash = ByteArray(0)

    init {
        https = tlsServer()
        serve(http, "http")
        serve(https, "https")
    }

    private fun hash(data: ByteArray): ByteArray =
        MessageDigest.getInstance(if (sha256) "SHA-256" else "SHA-1").digest(data)

    private fun aes(encrypt: Boolean, data: ByteArray): ByteArray {
        val padded = data.copyOf((data.size + AES_BLOCK - 1) / AES_BLOCK * AES_BLOCK)
        return Cipher.getInstance("AES/ECB/NoPadding")
            .apply {
                init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    SecretKeySpec(aesKey, "AES"),
                )
            }
            .doFinal(padded)
    }

    private fun tlsServer(): SSLServerSocket {
        val password = UUID.randomUUID().toString().toCharArray()
        val store =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(
                    "host",
                    tlsIdentity.keyPair.private,
                    password,
                    arrayOf(tlsIdentity.certificate),
                )
            }
        val keys =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, password)
            }
        val clients =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    lastClientChain = chain.toList()
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw UnsupportedOperationException()
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val context =
            SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(clients), null) }
        val server =
            context.serverSocketFactory.createServerSocket(
                0,
                BACKLOG,
                InetAddress.getLoopbackAddress(),
            ) as SSLServerSocket
        server.needClientAuth = true
        return server
    }

    private fun serve(server: ServerSocket, scheme: String) {
        thread(isDaemon = true, name = "fake-pairing-host-$scheme") {
            while (!server.isClosed) {
                val socket =
                    try {
                        server.accept()
                    } catch (ignored: IOException) {
                        // The host was closed.
                        break
                    }
                thread(isDaemon = true, name = "fake-pairing-host-$scheme-connection") {
                    socket.use { handle(it, scheme) }
                }
            }
        }
    }

    private fun handle(socket: Socket, scheme: String) {
        try {
            if (socket is SSLSocket) socket.startHandshake()
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val target = reader.readLine()?.split(' ')?.get(1) ?: return
            while (!reader.readLine().isNullOrEmpty()) {
                // The request headers are not needed.
            }
            val query =
                target
                    .substringAfter('?', "")
                    .split('&')
                    .filter { it.isNotEmpty() }
                    .associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val reply =
                synchronized(lock) {
                    val request =
                        RecordedRequest(
                            scheme,
                            target.substringBefore('?'),
                            query,
                            if (scheme == "https") lastClientChain else emptyList(),
                        )
                    requests += request
                    intercept(request) { defaultReply(request) }
                }
            val body = reply.body.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().apply {
                write(
                    ("HTTP/1.1 ${reply.status} ${reply.reason}\r\n" +
                            "Content-Type: text/xml; charset=utf-8\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                        .toByteArray(Charsets.ISO_8859_1)
                )
                write(body)
                flush()
            }
        } catch (e: IOException) {
            // A TLS handshake that the client refused, or a client that went away.
            hostFailures += e
        } catch (e: RuntimeException) {
            hostFailures += e
        }
    }

    private fun defaultReply(request: RecordedRequest): PairingReply =
        when (request.phase) {
            "getservercert" -> getServerCert(request.query)
            "clientchallenge" -> clientChallenge(request.query.getValue("clientchallenge"))
            "serverchallengeresp" ->
                serverChallengeResponse(request.query.getValue("serverchallengeresp"))
            "clientpairingsecret" ->
                clientPairingSecret(request.query.getValue("clientpairingsecret"))
            "pairchallenge" -> PairingReply.root("<paired>1</paired>")
            "unpair" -> PairingReply(200, "OK", """<root status_code="200"/>""")
            else -> PairingReply(404, "Not Found", "")
        }

    private fun getServerCert(query: Map<String, String>): PairingReply {
        salt = unhex(query.getValue("salt"))
        clientCertificate =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(unhex(query.getValue("clientcert"))))
                as X509Certificate
        aesKey = hash(salt + pinBytes).copyOf(AES_BLOCK)
        return PairingReply.root("<paired>1</paired><plaincert>${hex(hostIdentity.pem)}</plaincert>")
    }

    private fun clientChallenge(encrypted: String): PairingReply {
        val challenge = aes(false, unhex(encrypted))
        val sentChallenge = ByteArray(SECRET_LENGTH).also { random.nextBytes(it) }
        // What the client reads back is the part that was sent, padded with zeros.
        serverChallenge = sentChallenge.copyOf(serverChallengeBytesSent).copyOf(SECRET_LENGTH)
        val answerHash = hash(challenge + hostIdentity.certificate.signature + serverSecret)
        if (flipLastHashByte) {
            answerHash[answerHash.lastIndex] = (answerHash.last().toInt() xor 1).toByte()
        }
        val response = answerHash + sentChallenge.copyOf(serverChallengeBytesSent)
        return PairingReply.root(
            "<paired>1</paired><challengeresponse>${hex(aes(true, response))}</challengeresponse>"
        )
    }

    private fun serverChallengeResponse(encrypted: String): PairingReply {
        clientHash = aes(false, unhex(encrypted)).copyOf(hash(ByteArray(0)).size)
        val signature =
            Signature.getInstance(signatureAlgorithmOf(hostIdentity.keyPair.private))
                .apply {
                    initSign(hostIdentity.keyPair.private)
                    update(serverSecret)
                }
                .sign()
        return PairingReply.root(
            "<paired>1</paired><pairingsecret>${hex(serverSecret + signature)}</pairingsecret>"
        )
    }

    private fun clientPairingSecret(encoded: String): PairingReply {
        val bytes = unhex(encoded)
        val secret = bytes.copyOfRange(0, SECRET_LENGTH)
        val signature = bytes.copyOfRange(SECRET_LENGTH, bytes.size)
        val client = checkNotNull(clientCertificate)
        clientSignatureValid =
            Signature.getInstance(signatureAlgorithmOf(client.publicKey))
                .apply {
                    initVerify(client.publicKey)
                    update(secret)
                }
                .verify(signature)
        clientHashMatched =
            hash(serverChallenge + client.signature + secret).contentEquals(clientHash)
        val paired = clientSignatureValid == true && clientHashMatched == true
        return PairingReply.root("<paired>${if (paired) 1 else 0}</paired>")
    }

    override fun close() {
        http.close()
        https.close()
    }
}
