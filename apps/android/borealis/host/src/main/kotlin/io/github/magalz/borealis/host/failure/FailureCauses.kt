package io.github.magalz.borealis.host.failure

import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/** Turns what a failed start threw into the facts the classifier needs. */
object FailureCauses {
    private val RETRIED_STATUSES = setOf(409, 503)

    /**
     * The cause of a start failure. [hostHttpCode] is the status of the upstream
     * `HostHttpResponseException`, or null when [error] is not one (the class is Java, in `:app`).
     */
    @JvmStatic
    fun of(error: Throwable, hostHttpCode: Int?): CauseKind =
        when {
            generateSequence(error) { it.cause }.any { it is CertificateMismatchException } ->
                CauseKind.CERT_MISMATCH
            hostHttpCode != null -> CauseKind.HTTP
            // SocketTimeoutException is an InterruptedIOException.
            error is InterruptedIOException -> CauseKind.TIMEOUT
            error is ConnectException ||
                error is NoRouteToHostException ||
                error is UnknownHostException -> CauseKind.UNREACHABLE
            else -> CauseKind.NONE
        }

    /**
     * Whether the 409 and 503 launch retries ran out: the host asked to retry, [retried] says the
     * client did, and the last answer was still [httpCode].
     */
    @JvmStatic
    fun retriesExhausted(retried: Boolean, httpCode: Int?): Boolean =
        retried && httpCode in RETRIED_STATUSES
}
