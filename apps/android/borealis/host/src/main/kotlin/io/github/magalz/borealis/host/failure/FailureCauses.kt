package io.github.magalz.borealis.host.failure

/** Turns what a failed start threw into the facts the classifier needs. */
object FailureCauses {
    /**
     * The cause of a start failure. [hostHttpCode] is the status of the upstream
     * `HostHttpResponseException`, or null when [error] is not one (the class is Java, in `:app`).
     */
    @JvmStatic
    fun of(error: Throwable, hostHttpCode: Int?): CauseKind {
        TODO("not implemented")
    }

    /**
     * Whether the 409 and 503 launch retries ran out: the host asked to retry, [retried] says the
     * client did, and the last answer was still [httpCode].
     */
    @JvmStatic
    fun retriesExhausted(retried: Boolean, httpCode: Int?): Boolean {
        TODO("not implemented")
    }
}
