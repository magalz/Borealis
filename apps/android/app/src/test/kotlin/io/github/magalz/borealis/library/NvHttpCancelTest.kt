package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import java.net.ServerSocket
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pairing request waits for the user to type the PIN on the host, with no read timeout. This
 * pins that `cancelPendingRequests` ends such a request, which is what a cancelled pairing uses.
 */
@RunWith(RobolectricTestRunner::class)
class NvHttpCancelTest {
    private val silentHost = ServerSocket(0).also { it.soTimeout = WAIT_MS.toInt() }

    @After
    fun tearDown() {
        silentHost.close()
    }

    private val crypto =
        object : LimelightCryptoProvider {
            override val clientCertificate: X509Certificate? = null

            override val clientPrivateKey: PrivateKey? = null

            override val pemEncodedClientCertificate: ByteArray? = null

            override fun encodeBase64String(data: ByteArray): String = ""
        }

    @Test
    fun `a request that the host never answers ends when the pending requests are cancelled`() {
        // The HTTPS port is the silent socket: the request has no read timeout and waits for the host.
        val http =
            NvHTTP(AddressTuple("127.0.0.1", silentHost.localPort), silentHost.localPort, "id", null, crypto)
        val failure = AtomicReference<Throwable?>()
        val finished = CountDownLatch(1)
        val request = Thread {
            try {
                http.quitApp()
            } catch (e: Exception) {
                failure.set(e)
            } finally {
                finished.countDown()
            }
        }
        request.isDaemon = true
        request.start()
        silentHost.accept().use {
            assertThat(finished.await(SETTLE_MS, TimeUnit.MILLISECONDS)).isFalse()

            http.cancelPendingRequests()

            assertThat(finished.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
        }
        assertThat(failure.get()).isInstanceOf(java.io.IOException::class.java)
    }

    private companion object {
        const val SETTLE_MS = 300L
        const val WAIT_MS = 5_000L
    }
}
