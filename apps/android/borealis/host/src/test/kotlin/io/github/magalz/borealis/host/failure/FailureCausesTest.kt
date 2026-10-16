package io.github.magalz.borealis.host.failure

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import io.github.magalz.borealis.host.pairing.ReplayServer
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class FailureCausesTest {
    private fun of(error: Throwable, httpCode: Int? = null) = FailureCauses.of(error, httpCode)

    @Test
    fun `a certificate mismatch is a certificate mismatch`() {
        assertThat(of(CertificateMismatchException())).isEqualTo(CauseKind.CERT_MISMATCH)
    }

    @Test
    fun `a certificate mismatch wrapped by another exception is still found`() {
        assertThat(of(IOException("wrapped", CertificateMismatchException())))
            .isEqualTo(CauseKind.CERT_MISMATCH)
    }

    private fun frame(className: String, method: String) =
        StackTraceElement(className, method, null, -1)

    /** A timeout as the JVM raises it, with the frames of the call that was waiting. */
    private fun timeout(message: String, vararg frames: StackTraceElement) =
        SocketTimeoutException(message).apply { stackTrace = arrayOf(*frames) }

    private val connecting =
        arrayOf(
            frame("sun.nio.ch.NioSocketImpl", "timedFinishConnect"),
            frame("sun.nio.ch.NioSocketImpl", "connect"),
            frame("java.net.Socket", "connect"),
            frame("okhttp3.internal.platform.Platform", "connectSocket"),
            frame("okhttp3.internal.connection.ConnectPlan", "connectTcp"),
        )

    private val reading =
        arrayOf(
            frame("sun.nio.ch.NioSocketImpl", "timedRead"),
            frame("java.net.Socket\$SocketInputStream", "read"),
            frame("okio.InputStreamSource", "read"),
            frame("okhttp3.internal.http1.Http1ExchangeCodec", "readResponseHeaders"),
            frame("okhttp3.internal.http.CallServerInterceptor", "intercept"),
            frame("okhttp3.internal.connection.ConnectInterceptor", "intercept"),
        )

    @Test
    fun `a read timeout after the connection was made is a timeout`() {
        assertThat(of(timeout("Read timed out", *reading))).isEqualTo(CauseKind.TIMEOUT)
        assertThat(of(timeout("timeout", *reading))).isEqualTo(CauseKind.TIMEOUT)
    }

    @Test
    fun `a real OkHttp read timeout against a host that never answers is a timeout`() {
        ReplayServer(ReplayServer.Reply(ByteArray(0), closeDelayMillis = READ_WAIT_MS)).use { server
            ->
            val client =
                OkHttpClient.Builder().readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()

            val error = runCatching {
                client
                    .newCall(Request.Builder().url("http://127.0.0.1:${server.port}/").build())
                    .execute()
            }
                .exceptionOrNull()

            assertThat(error).isInstanceOf(SocketTimeoutException::class.java)
            assertThat(of(error!!)).isEqualTo(CauseKind.TIMEOUT)
        }
    }

    @Test
    fun `a connect timeout means no connection was made and is unreachable`() {
        assertThat(of(timeout("Connect timed out", *connecting))).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `a connect timeout is found by its frames when the message does not say so`() {
        assertThat(of(timeout("timeout", *connecting))).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `a connect timeout is found by its message on Android`() {
        val message =
            "failed to connect to /192.168.1.5 (port 47989) from /192.168.1.2 (port 40000) after 10000ms"

        assertThat(of(timeout(message))).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `a thread interrupt is not a timeout`() {
        assertThat(of(InterruptedIOException("interrupted"))).isEqualTo(CauseKind.NONE)
    }

    @Test
    fun `a refused connection is unreachable`() {
        assertThat(of(ConnectException())).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `no route to the host is unreachable`() {
        assertThat(of(NoRouteToHostException())).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `an unknown host is unreachable`() {
        assertThat(of(UnknownHostException())).isEqualTo(CauseKind.UNREACHABLE)
    }

    @Test
    fun `an answer of the host is an HTTP cause`() {
        assertThat(of(IOException("Host PC returned error"), httpCode = 503))
            .isEqualTo(CauseKind.HTTP)
    }

    @Test
    fun `any other failure has no cause`() {
        assertThat(of(EOFException())).isEqualTo(CauseKind.NONE)
        assertThat(of(SSLHandshakeException("handshake"))).isEqualTo(CauseKind.NONE)
        assertThat(of(IllegalStateException())).isEqualTo(CauseKind.NONE)
    }

    @Test
    fun `retries are exhausted when the client retried and the host still said 409 or 503`() {
        assertThat(FailureCauses.retriesExhausted(retried = true, httpCode = 409)).isTrue()
        assertThat(FailureCauses.retriesExhausted(retried = true, httpCode = 503)).isTrue()
    }

    @Test
    fun `retries are not exhausted without a retry or for another answer`() {
        assertThat(FailureCauses.retriesExhausted(retried = false, httpCode = 503)).isFalse()
        assertThat(FailureCauses.retriesExhausted(retried = true, httpCode = 500)).isFalse()
        assertThat(FailureCauses.retriesExhausted(retried = true, httpCode = null)).isFalse()
    }

    private companion object {
        const val READ_TIMEOUT_MS = 200L
        const val READ_WAIT_MS = 3_000L
    }
}
