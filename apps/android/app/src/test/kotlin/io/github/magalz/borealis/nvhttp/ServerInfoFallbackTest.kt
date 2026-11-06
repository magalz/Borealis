package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.crypto.TestIdentities
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The fallback of `serverinfo` for a client with a pinned certificate when the host answers 401 in
 * the HTTP status, at the HTTPS request and at the read of the HTTPS port: both end in the plain
 * HTTP request, like the 401 in the XML of an answer (`CertificatePinningTest`).
 */
@RunWith(RobolectricTestRunner::class)
class ServerInfoFallbackTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private val pinned = TestIdentities.hostRsa.certificate

    private val unauthorized = HostReply.status(401, "Unauthorized", ByteArray(0))

    @Test
    fun `a 401 of the HTTPS serverinfo falls back to plain HTTP`() {
        val host = host()
        host.answer("/serverinfo", unauthorized, "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "http")
        val http = clientOf(host, serverCert = pinned)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("https /serverinfo", "http /serverinfo")
            .inOrder()
        assertThat(http.sawCertificateMismatch()).isFalse()
    }

    @Test
    fun `a 401 when the HTTPS port is read falls back to plain HTTP`() {
        val host = host()
        val asked = AtomicInteger()
        host.handler = {
            if (asked.getAndIncrement() == 0) unauthorized else HostReply.xml(serverInfoBody())
        }
        val http = clientOf(host, serverCert = pinned, httpsKnown = false)

        assertThat(http.getServerInfo(true)).isEqualTo(serverInfoBody())

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo", "http /serverinfo")
            .inOrder()
    }
}
