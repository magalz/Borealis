package io.github.magalz.borealis.stream

/** One reading of the stream's performance figures, for the overlay. */
data class PerfSnapshot(
    val totalFps: Float,
    val receivedFps: Float,
    val renderedFps: Float,
    val lossPercent: Float,
    val rttMs: Int,
    val rttVarianceMs: Int,
    val hostLatency: LatencyRange?,
    /** Null when the decoder reports no decode time, which some low-latency decoders do not. */
    val decodeMs: Float?,
    val bitrateMbps: Float?,
)

/** The host processing time over the sample window, in ms. */
data class LatencyRange(val min: Float, val avg: Float, val max: Float)

/** Receives a [PerfSnapshot] about once a second. */
fun interface PerfSnapshotSink {
    fun onPerf(snapshot: PerfSnapshot)
}
