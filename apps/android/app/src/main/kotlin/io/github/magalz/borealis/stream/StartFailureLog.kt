package io.github.magalz.borealis.stream

/** Logs why a start failed; the exception text never reaches the screen. */
object StartFailureLog {
    /** Logs the [error] of the start [step], with the addresses masked per the privacy setting. */
    @JvmStatic
    fun failed(step: String, error: Throwable) {
        // Not written yet.
    }
}
