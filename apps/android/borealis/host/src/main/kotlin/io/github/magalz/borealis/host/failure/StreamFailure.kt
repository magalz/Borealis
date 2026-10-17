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
    /**
     * The connection stage that failed: a [StartSteps] word for a step of the HTTP start, or the
     * name moonlight-common-c gives to a stage of the stream.
     */
    val stage: String?,
    val errorCode: Int,
    val portFlags: Int,
    val terminated: Boolean,
    val cause: CauseKind = CauseKind.NONE,
    val httpCode: Int? = null,
    val resumeCollision: Boolean = false,
    val retriesExhausted: Boolean = false,
    /**
     * The RTSP handshake failed with error -1: the PC answered, but its reply could not be
     * decrypted or parsed. The ports are open, so the firewall is not the cause.
     */
    val rtspReplyUnreadable: Boolean = false,
    /** The connection reached the first frame before it ended. */
    val streamed: Boolean = false,
    /** A fixed text the start path meant for the user, for example why the host refused. */
    val note: String? = null,
)

/** The kinds of failure the connection error screen tells apart. */
enum class FailureKind {
    HOST_BUSY,
    HOST_OFFLINE,
    CERTIFICATE_CHANGED,
    GENERIC,

    /** A stream that reached the first frame and then ended with a generic failure. */
    STOPPED,
}

/** A classified failure. [rawCode] is the support text, for example `Error -1 · RTSP handshake`. */
data class StreamFailure(val kind: FailureKind, val rawCode: String, val portHint: String?)
