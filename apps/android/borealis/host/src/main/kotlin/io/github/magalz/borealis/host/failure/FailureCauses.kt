package io.github.magalz.borealis.host.failure

import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns what a failed start threw into the facts the classifier needs. */
object FailureCauses {
    private val RETRIED_STATUSES = setOf(409, 503)

    // The frames of the socket layers that only run while a TCP connection is being made.
    private val CONNECT_FRAMES = setOf("connect", "connect0", "timedFinishConnect", "finishConnect")

    /**
     * The cause of a start failure. A timeout is told apart by what it waited for: while the TCP
     * connection was being made nobody answered, so the PC is off, asleep or elsewhere
     * ([CauseKind.UNREACHABLE]); after the connection was made the host is there but slow
     * ([CauseKind.TIMEOUT], the exhausted `/serverinfo` retry of a busy host). An interrupt is no
     * timeout. [hostHttpCode] is the status of the upstream `HostHttpResponseException`, or null
     * when [error] is not one (the class is in `:app`).
     */
    @JvmStatic
    fun of(error: Throwable, hostHttpCode: Int?): CauseKind =
        when {
            generateSequence(error) { it.cause }.any { it is CertificateMismatchException } ->
                CauseKind.CERT_MISMATCH
            hostHttpCode != null -> CauseKind.HTTP
            error is SocketTimeoutException ->
                if (timedOutConnecting(error)) CauseKind.UNREACHABLE else CauseKind.TIMEOUT
            // A thread interrupt (the user left) is no timeout; only SocketTimeoutException is one.
            error is InterruptedIOException -> CauseKind.NONE
            error is ConnectException ||
                error is NoRouteToHostException ||
                error is UnknownHostException -> CauseKind.UNREACHABLE
            else -> CauseKind.NONE
        }

    /**
     * Whether [error] came from waiting for the TCP connection itself, not for an answer on one.
     * Two signals, either is enough: the JVM and Android word it "Connect timed out" or "failed to
     * connect to ...", and the stack holds a socket `connect` frame that a read never has.
     */
    private fun timedOutConnecting(error: SocketTimeoutException): Boolean =
        error.message?.contains("connect", ignoreCase = true) == true ||
            error.stackTrace.any { it.methodName in CONNECT_FRAMES }

    /**
     * Whether the 409 and 503 launch retries ran out: the host asked to retry, [retried] says the
     * client did, and the last answer was still [httpCode].
     */
    @JvmStatic
    fun retriesExhausted(retried: Boolean, httpCode: Int?): Boolean =
        retried && httpCode in RETRIED_STATUSES
}
