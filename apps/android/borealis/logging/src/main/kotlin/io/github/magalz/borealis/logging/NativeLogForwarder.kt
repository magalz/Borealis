package io.github.magalz.borealis.logging

/**
 * Receives the log lines of moonlight-common-c from the native side (through `MoonBridge`) and logs
 * them under [LogTag.COMMONC].
 *
 * The lines arrive on the video, audio, control and RTSP threads at the rate the library emits
 * them, which for frame loss can be hundreds per second. A token bucket of [burst] lines refilled
 * at [linesPerSecond] bounds what reaches [BorealisLog]; lines over the limit are only counted. The
 * first line accepted after a suppression is preceded by one `N lines suppressed` line. The default
 * burst covers the lines of a normal stream start, so the RTSP handshake is not cut, and the refill
 * caps a long loss burst at about 1.3 KB/s so it cannot evict the pairing and launch history from
 * the file ring. The summary is only written when another line arrives; a burst at the very end of
 * a stream can stay uncounted.
 *
 * Redaction is left to [BorealisLog], which every line passes. It runs on the calling thread, which
 * is acceptable because the bucket limits it to [linesPerSecond] lines of at most a few hundred
 * bytes.
 */
class NativeLogForwarder(
    private val ready: () -> Boolean = { BorealisLog.isInitialized },
    private val emit: (String) -> Unit = { BorealisLog.i(LogTag.COMMONC, it) },
    private val nanoClock: () -> Long = System::nanoTime,
    private val burst: Int = DEFAULT_BURST,
    private val linesPerSecond: Int = DEFAULT_LINES_PER_SECOND,
) {
    private val lock = Any()
    private var tokens = burst.toDouble()
    private var lastRefillNanos = nanoClock()
    private var suppressed = 0

    /**
     * Logs [bytes] (UTF-8, possibly malformed, usually ending in a newline) unless the rate limit
     * suppresses it. Returns false only when nothing could handle the line, so that the caller
     * falls back to logcat; a suppressed line still counts as handled.
     */
    fun accept(bytes: ByteArray): Boolean {
        if (!ready()) return false
        val line = String(bytes, Charsets.UTF_8).trimEnd('\n', '\r')
        if (line.isNotEmpty()) {
            val summary = takeToken()
            if (summary > 0) emit("$summary lines suppressed")
            if (summary >= 0) emit(line)
        }
        return true
    }

    /** -1 when the line is suppressed (and counted), else the number of lines suppressed before. */
    private fun takeToken(): Int =
        synchronized(lock) {
            refill()
            if (tokens < 1.0) {
                suppressed++
                -1
            } else {
                tokens -= 1.0
                suppressed.also { suppressed = 0 }
            }
        }

    private fun refill() {
        val now = nanoClock()
        val elapsedNanos = (now - lastRefillNanos).coerceAtLeast(0L)
        lastRefillNanos = now
        tokens = minOf(burst.toDouble(), tokens + elapsedNanos * linesPerSecond / NANOS_PER_SECOND)
    }

    companion object {
        const val DEFAULT_BURST = 100
        const val DEFAULT_LINES_PER_SECOND = 10
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        private val shared = NativeLogForwarder()

        /** Entry point for the JNI bridge (`MoonBridge.bridgeClLogMessage`). */
        @JvmStatic fun forward(bytes: ByteArray): Boolean = shared.accept(bytes)
    }
}
