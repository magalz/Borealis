package io.github.magalz.borealis.logging

/** Placeholder until the forwarder exists. */
class NativeLogForwarder(
    private val ready: () -> Boolean = { BorealisLog.isInitialized },
    private val emit: (String) -> Unit = { BorealisLog.i(LogTag.COMMONC, it) },
    private val nanoClock: () -> Long = System::nanoTime,
    private val burst: Int = DEFAULT_BURST,
    private val linesPerSecond: Int = DEFAULT_LINES_PER_SECOND,
) {
    fun accept(bytes: ByteArray): Boolean = false

    companion object {
        const val DEFAULT_BURST = 100
        const val DEFAULT_LINES_PER_SECOND = 10

        @JvmStatic fun forward(bytes: ByteArray): Boolean = false
    }
}
