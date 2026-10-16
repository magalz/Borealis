package io.github.magalz.borealis.host.failure

/** What caused a failed connection, when the cause is known. */
enum class CauseKind {
    NONE,
    CERT_MISMATCH,
    UNREACHABLE,
    TIMEOUT,
    HTTP,
}

/** The raw facts about a failed stream start or a stream that ended, for the classifier. */
data class FailureFacts(
    /** The connection stage that failed, as the stack reports it. */
    val stage: String?,
    val errorCode: Int,
    val portFlags: Int,
    val terminated: Boolean,
    val cause: CauseKind = CauseKind.NONE,
    val httpCode: Int? = null,
    val resumeCollision: Boolean = false,
    val retriesExhausted: Boolean = false,
)

/** The kinds of failure the connection error screen tells apart. */
enum class FailureKind {
    HOST_BUSY,
    HOST_OFFLINE,
    CERTIFICATE_CHANGED,
    GENERIC,
}

/** A classified failure. [rawCode] is the support text, for example `Error -1 · RTSP handshake`. */
data class StreamFailure(val kind: FailureKind, val rawCode: String, val portHint: String?)
