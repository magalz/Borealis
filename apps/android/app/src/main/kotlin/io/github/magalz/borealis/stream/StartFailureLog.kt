package io.github.magalz.borealis.stream

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/**
 * Logs why a start failed. The exception text can hold the address of the PC, so it goes to the
 * log, which masks addresses per the privacy setting, and never to the screen.
 */
object StartFailureLog {
    /** Logs the [error] of the start [step]. */
    @JvmStatic
    fun failed(step: String, error: Throwable) {
        BorealisLog.w(
            LogTag.LAUNCH,
            "start failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            "step" to step,
        )
    }
}
