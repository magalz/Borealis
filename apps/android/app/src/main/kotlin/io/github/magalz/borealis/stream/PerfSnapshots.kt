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
    val decoderTimeMs: Long,
    val hostLatencyFrames: Int,
    val hostLatencyMinTenthsMs: Int,
    val hostLatencyMaxTenthsMs: Int,
    val hostLatencyTotalTenthsMs: Int,
    val bytes: Long,
    val elapsedMs: Long,
)

/** Builds the [PerfSnapshot] the overlay shows from a stats window. */
object PerfSnapshots {
    /**
     * [rttInfo] is `MoonBridge.getEstimatedRttInfo()`: the RTT in the high half, its variance in
     * the low.
     */
    @JvmStatic
    fun from(window: PerfWindow, rttInfo: Long): PerfSnapshot {
        TODO("not implemented")
    }
}
