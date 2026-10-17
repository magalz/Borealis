package io.github.magalz.borealis.stream

/**
 * The counters of a stats window, as `VideoStats` holds them. Host latency is in tenths of a
 * millisecond, as the host sends it.
 */
data class PerfWindow(
    val totalFrames: Int,
    val framesReceived: Int,
    val framesRendered: Int,
    val framesLost: Int,
    val decoderTimeUs: Long,
    val hostLatencyFrames: Int,
    val hostLatencyMinTenthsMs: Int,
    val hostLatencyMaxTenthsMs: Int,
    val hostLatencyTotalTenthsMs: Int,
    val bytes: Long,
    val elapsedMs: Long,
)

/** Builds the [PerfSnapshot] the overlay shows from a stats window. */
object PerfSnapshots {
    private const val MILLIS_PER_SECOND = 1000f
    private const val MICROS_PER_MS = 1000f
    private const val TENTHS_PER_MS = 10f
    private const val BITS_PER_BYTE = 8
    private const val BITS_PER_MEGABIT = 1_000_000f
    private const val PERCENT = 100f

    /**
     * [rttInfo] is `MoonBridge.getEstimatedRttInfo()`: the RTT in the high half, its variance in
     * the low.
     */
    @JvmStatic
    fun from(window: PerfWindow, rttInfo: Long): PerfSnapshot {
        val seconds = window.elapsedMs / MILLIS_PER_SECOND
        val perSecond = { frames: Int -> if (seconds > 0f) frames / seconds else 0f }
        return PerfSnapshot(
            totalFps = perSecond(window.totalFrames),
            receivedFps = perSecond(window.framesReceived),
            renderedFps = perSecond(window.framesRendered),
            lossPercent =
                if (window.totalFrames > 0)
                    window.framesLost.toFloat() / window.totalFrames * PERCENT
                else 0f,
            rttMs = (rttInfo shr Int.SIZE_BITS).toInt(),
            rttVarianceMs = rttInfo.toInt(),
            hostLatency = hostLatency(window),
            decodeMs = decodeMs(window),
            bitrateMbps =
                if (seconds > 0f) {
                    window.bytes * BITS_PER_BYTE / BITS_PER_MEGABIT / seconds
                } else {
                    null
                },
        )
    }

    // No frames, or frames without any decoder time: nothing was measured, and a zero would pass
    // for a decode that took no time. The time is in microseconds, so a decode under a millisecond
    // still shows.
    private fun decodeMs(window: PerfWindow): Float? =
        if (window.framesReceived > 0 && window.decoderTimeUs > 0) {
            window.decoderTimeUs / MICROS_PER_MS / window.framesReceived
        } else {
            null
        }

    private fun hostLatency(window: PerfWindow): LatencyRange? =
        if (window.hostLatencyFrames > 0) {
            LatencyRange(
                min = window.hostLatencyMinTenthsMs / TENTHS_PER_MS,
                avg =
                    window.hostLatencyTotalTenthsMs.toFloat() /
                        window.hostLatencyFrames /
                        TENTHS_PER_MS,
                max = window.hostLatencyMaxTenthsMs / TENTHS_PER_MS,
            )
        } else {
            null
        }
}
