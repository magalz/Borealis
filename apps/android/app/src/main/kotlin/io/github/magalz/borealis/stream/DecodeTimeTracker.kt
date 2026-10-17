package io.github.magalz.borealis.stream

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/** What the decoder did in a stats window; the sample lists are filled in the first window only. */
data class DecodeTimeCounts(
    val inputs: Int = 0,
    val outputs: Int = 0,
    val exact: Int = 0,
    val truncated: Int = 0,
    val zero: Int = 0,
    val ahead: Int = 0,
    val older: Int = 0,
    val unmatched: Int = 0,
    val firstInputs: List<Long> = emptyList(),
    val firstOutputs: List<Long> = emptyList(),
)

/**
 * Pairs each decoder output with the input it came from by timestamp, to time the decoder. Some
 * decoders hand the frames back with a truncated or zeroed timestamp, so the output timestamp alone
 * does not say when the frame went in.
 *
 * Not thread-safe: the caller guards it, as the input and the output run on different threads.
 */
class DecodeTimeTracker {
    private val ring = ArrayDeque<Long>(RING_SIZE)
    private var counts = DecodeTimeCounts()
    private val sampleInputs = ArrayList<Long>(SAMPLE_SIZE)
    private val sampleOutputs = ArrayList<Long>(SAMPLE_SIZE)
    private var sampled = false

    /** Records the input timestamp of a frame (not codec config); the oldest entry goes at 128. */
    fun onInput(timestampUs: Long) {
        if (ring.size == RING_SIZE) ring.removeFirst()
        ring.addLast(timestampUs)
        counts = counts.copy(inputs = counts.inputs + 1)
        if (!sampled && sampleInputs.size < SAMPLE_SIZE) sampleInputs += timestampUs
    }

    /**
     * Returns the input timestamp the output [presentationTimeUs] belongs to, or [NO_MATCH]. A
     * match drops that input and every older one, so a frame the decoder dropped cannot shift the
     * pairing. Without a match the ring is left alone.
     */
    fun onOutput(presentationTimeUs: Long): Long {
        if (!sampled && sampleOutputs.size < SAMPLE_SIZE) sampleOutputs += presentationTimeUs
        counts = counts.copy(outputs = counts.outputs + 1)

        var index = ring.indexOf(presentationTimeUs)
        if (index >= 0) {
            counts = counts.copy(exact = counts.exact + 1)
        } else {
            index = ring.indexOfFirst { (it and LOW_32_BITS) == presentationTimeUs }
            if (index >= 0) counts = counts.copy(truncated = counts.truncated + 1)
        }
        if (index < 0) {
            classifyMiss(presentationTimeUs)
            return NO_MATCH
        }
        val matched = ring[index]
        repeat(index + 1) { ring.removeFirst() }
        return matched
    }

    private fun classifyMiss(presentationTimeUs: Long) {
        counts =
            when {
                presentationTimeUs == 0L -> counts.copy(zero = counts.zero + 1)
                ring.isEmpty() -> counts.copy(unmatched = counts.unmatched + 1)
                presentationTimeUs > ring.last() -> counts.copy(ahead = counts.ahead + 1)
                presentationTimeUs < ring.first() -> counts.copy(older = counts.older + 1)
                else -> counts.copy(unmatched = counts.unmatched + 1)
            }
    }

    /** Forgets the frames in the decoder, and starts the samples of the next decoder. */
    fun clear() {
        ring.clear()
        sampleInputs.clear()
        sampleOutputs.clear()
        sampled = false
    }

    /**
     * The counts since the last drain, which are reset. The first drain that has any raw timestamps
     * of a decoder carries them (8 of each at most), later ones none.
     */
    fun drainWindow(): DecodeTimeCounts {
        val drained =
            if (!sampled && (sampleInputs.isNotEmpty() || sampleOutputs.isNotEmpty())) {
                sampled = true
                counts.copy(
                    firstInputs = sampleInputs.toList(),
                    firstOutputs = sampleOutputs.toList(),
                )
            } else {
                counts
            }
        counts = DecodeTimeCounts()
        return drained
    }

    companion object {
        /** Returned by [onOutput] when no input matches. */
        const val NO_MATCH = Long.MIN_VALUE

        private const val RING_SIZE = 128
        private const val SAMPLE_SIZE = 8
        private const val LOW_32_BITS = 0xFFFF_FFFFL
    }
}

/** Writes what the decoder did in a window to the verbose log, for the device checks. */
object DecodeTimeLog {
    @JvmStatic
    fun window(codec: String?, counts: DecodeTimeCounts) {
        val fields =
            mutableListOf(
                "codec" to (codec ?: "unknown"),
                "in" to counts.inputs,
                "out" to counts.outputs,
                "exact" to counts.exact,
                "truncated" to counts.truncated,
                "zero" to counts.zero,
                "ahead" to counts.ahead,
                "older" to counts.older,
                "unmatched" to counts.unmatched,
            )
        if (counts.firstInputs.isNotEmpty() || counts.firstOutputs.isNotEmpty()) {
            fields += "first_in" to counts.firstInputs.joinToString(",")
            fields += "first_out" to counts.firstOutputs.joinToString(",")
        }
        BorealisLog.v(LogTag.VIDEO, "Decoder window", *fields.toTypedArray())
    }
}
