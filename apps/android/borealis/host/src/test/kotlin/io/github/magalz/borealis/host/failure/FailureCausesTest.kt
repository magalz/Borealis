package io.github.magalz.borealis.host.failure

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
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

    @Test
    fun `a socket timeout is a timeout`() {
        assertThat(of(SocketTimeoutException())).isEqualTo(CauseKind.TIMEOUT)
    }

    @Test
    fun `an interrupted IO is a timeout`() {
        assertThat(of(InterruptedIOException())).isEqualTo(CauseKind.TIMEOUT)
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
}
