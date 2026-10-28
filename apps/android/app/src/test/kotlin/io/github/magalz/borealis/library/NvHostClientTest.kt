package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A host on the loopback interface that answers `GET <path>` with a fixed body, over plain HTTP.
 */
private class FakeHostServer(private val answers: Map<String, String>) : Closeable {
    private val server = ServerSocket(0)
    val port: Int
        get() = server.localPort

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { respond(it) }
                } catch (ignored: IOException) {
                    // The server was closed, or the client went away.
                    if (server.isClosed) break
                }
            }
        }
    }

    private fun respond(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        val path = reader.readLine().split(' ')[1].substringBefore('?')
        while (!reader.readLine().isNullOrEmpty()) {
            // The request headers are not needed.
        }
        val body = (answers[path] ?: """<root status_code="404"/>""").toByteArray()
        socket.getOutputStream().apply {
            write(
                ("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n")
                    .toByteArray()
            )
            write(body)
            flush()
        }
    }

    override fun close() = server.close()
}

/** [NvHostClient] against a host that really answers HTTP. */
@RunWith(RobolectricTestRunner::class)
class NvHostClientTest {
    private val closeables = mutableListOf<Closeable>()

    private val crypto =
        object : LimelightCryptoProvider {
            override val clientCertificate: X509Certificate? = null

            override val clientPrivateKey: PrivateKey? = null

            override val pemEncodedClientCertificate: ByteArray = byteArrayOf(1, 2, 3)

            override fun encodeBase64String(data: ByteArray): String = ""
        }

    @After fun tearDown() = closeables.forEach { it.close() }

    private fun clientFor(answers: Map<String, String>): NvHostClient {
        val host = FakeHostServer(answers).also { closeables += it }
        return NvHostClient(NvHTTP(AddressTuple("127.0.0.1", host.port), 0, "id", null, crypto))
    }

    private fun serverInfo(paired: Boolean) =
        """<root status_code="200"><appversion>7.1.431.0</appversion>""" +
            "<PairStatus>${if (paired) 1 else 0}</PairStatus><HttpsPort>47984</HttpsPort></root>"

    @Test
    fun `the pair state is what the host reports`() {
        assertThat(clientFor(mapOf("/serverinfo" to serverInfo(true))).pairState())
            .isEqualTo(PairState.PAIRED)
        assertThat(clientFor(mapOf("/serverinfo" to serverInfo(false))).pairState())
            .isEqualTo(PairState.NOT_PAIRED)
    }

    @Test
    fun `a host that refuses to pair gives a failed pairing without a certificate`() {
        val client =
            clientFor(
                mapOf(
                    "/serverinfo" to serverInfo(false),
                    "/pair" to """<root status_code="200"><paired>0</paired></root>""",
                )
            )

        val result = client.pair("1234")

        assertThat(result.state).isEqualTo(PairState.FAILED)
        assertThat(result.serverCert).isNull()
    }

    @Test
    fun `a host that confirms the unpair is unpaired`() {
        val client = clientFor(mapOf("/unpair" to """<root status_code="200"/>"""))

        assertThat(client.unpair()).isTrue()
    }

    @Test
    fun `a host that does not route the unpair does not confirm it`() {
        val client = clientFor(mapOf("/unpair" to """<root status_code="404"/>"""))

        assertThat(client.unpair()).isFalse()
    }

    @Test
    fun `cancelling with nothing pending does nothing`() {
        clientFor(emptyMap()).cancelPendingRequests()
    }
}
