package io.github.magalz.borealis.stream

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/**
 * What the decoder did in a stats window; the sample lists are filled in the first window only. The
 * decode times are in microseconds, over the matched frames that took under a second.
 */
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
    val decoded: Int = 0,
    val decodeAvgUs: Long = 0,
    val decodeMinUs: Long = 0,
    val decodeMaxUs: Long = 0,
)

/**
 * Pairs each decoder output with the input it came from by timestamp, to time the decoder from the
 * moment the frame was queued. Some decoders hand the frames back with a truncated or zeroed
 * timestamp, so the output timestamp alone does not say when the frame went in; and the input
 * timestamp is not that moment either, as it is fixed by the first frame of the stream.
 *
 * Not thread-safe: the caller guards it, as the input and the output run on different threads.
 */
class DecodeTimeTracker {
    private val ring = LongArray(RING_SIZE)
    private val queueRing = LongArray(RING_SIZE) // System.nanoTime() of the input at the same index
    private var head = 0 // index of the oldest entry
    private var size = 0

    private var inputs = 0
    private var outputs = 0
    private var exact = 0
    private var truncated = 0
    private var zero = 0
    private var ahead = 0
    private var older = 0
    private var unmatched = 0
    private var decoded = 0
    private var decodeSumUs = 0L
    private var decodeMinUs = Long.MAX_VALUE
    private var decodeMaxUs = 0L

    // The raw timestamps of the first frames of a decoder. A list is reported once, in the first
    // window that has any of it, so a first window with no outputs yet keeps the output samples.
    private val sampleInputs = LongArray(SAMPLE_SIZE)
    private var sampleInputCount = 0
    private var inputsReported = false
    private val sampleOutputs = LongArray(SAMPLE_SIZE)
    private var sampleOutputCount = 0
    private var outputsReported = false

    /**
     * Records the input timestamp of a frame (not codec config) and the [queueNs]
     * (`System.nanoTime()`) taken just before it was queued; the oldest entry goes at 128.
     */
    fun onInput(timestampUs: Long, queueNs: Long) {
        if (size == RING_SIZE) {
            head = (head + 1) % RING_SIZE
            size--
        }
        val slot = (head + size) % RING_SIZE
        ring[slot] = timestampUs
        queueRing[slot] = queueNs
        size++
        inputs++
        if (!inputsReported && sampleInputCount < SAMPLE_SIZE) {
            sampleInputs[sampleInputCount++] = timestampUs
        }
    }

    /**
     * Returns the decode time in microseconds of the input the output [presentationTimeUs] belongs
     * to, from its queue time to [nowNs] (`System.nanoTime()`), or [NO_MATCH]. A match drops that
     * input and every older one, so a frame the decoder dropped cannot shift the pairing. Without a
     * match the ring is left alone.
     */
    fun onOutput(presentationTimeUs: Long, nowNs: Long): Long {
        outputs++
        if (!outputsReported && sampleOutputCount < SAMPLE_SIZE) {
            sampleOutputs[sampleOutputCount++] = presentationTimeUs
        }

        var index = indexOf { it == presentationTimeUs }
        if (index >= 0) {
            exact++
        } else {
            index = indexOf { (it and LOW_32_BITS) == presentationTimeUs }
            if (index >= 0) truncated++
        }
        if (index < 0) {
            classifyMiss(presentationTimeUs)
            return NO_MATCH
        }
        val decodeUs = (nowNs - queueRing[(head + index) % RING_SIZE]) / NANOS_PER_MICRO
        head = (head + index + 1) % RING_SIZE
        size -= index + 1
        if (decodeUs >= 0 && decodeUs < MAX_DECODE_US) {
            decoded++
            decodeSumUs += decodeUs
            if (decodeUs < decodeMinUs) decodeMinUs = decodeUs
            if (decodeUs > decodeMaxUs) decodeMaxUs = decodeUs
        }
        return decodeUs
    }

    private fun at(index: Int) = ring[(head + index) % RING_SIZE]

    private inline fun indexOf(predicate: (Long) -> Boolean): Int {
        for (i in 0 until size) if (predicate(at(i))) return i
        return -1
    }

    private fun classifyMiss(presentationTimeUs: Long) {
        when {
            presentationTimeUs == 0L -> zero++
            size == 0 -> unmatched++
            presentationTimeUs > at(size - 1) -> ahead++
            presentationTimeUs < at(0) -> older++
            else -> unmatched++
        }
    }

    /** Forgets the frames in the decoder, and starts the samples of the next decoder. */
    fun clear() {
        head = 0
        size = 0
        sampleInputCount = 0
        inputsReported = false
        sampleOutputCount = 0
        outputsReported = false
    }

    /**
     * The counts since the last drain, which are reset. The raw timestamps of the first frames (8
     * at most) of each kind ride on the first drain that has any of that kind, later ones carry
     * none.
     */
    fun drainWindow(): DecodeTimeCounts {
        val firstInputs = if (inputsReported) emptyList() else sampleInputs.take(sampleInputCount)
        val firstOutputs =
            if (outputsReported) emptyList() else sampleOutputs.take(sampleOutputCount)
        if (firstInputs.isNotEmpty()) inputsReported = true
        if (firstOutputs.isNotEmpty()) outputsReported = true
        val drained =
            DecodeTimeCounts(
                inputs,
                outputs,
                exact,
                truncated,
                zero,
                ahead,
                older,
                unmatched,
                firstInputs,
                firstOutputs,
                decoded,
                if (decoded > 0) decodeSumUs / decoded else 0,
                if (decoded > 0) decodeMinUs else 0,
                decodeMaxUs,
            )
        inputs = 0
        outputs = 0
        exact = 0
        truncated = 0
        zero = 0
        ahead = 0
        older = 0
        unmatched = 0
        decoded = 0
        decodeSumUs = 0
        decodeMinUs = Long.MAX_VALUE
        decodeMaxUs = 0
        return drained
    }

    companion object {
        /** Returned by [onOutput] when no input matches. */
        const val NO_MATCH = Long.MIN_VALUE

        private const val RING_SIZE = 128
        private const val SAMPLE_SIZE = 8
        private const val LOW_32_BITS = 0xFFFF_FFFFL
        private const val NANOS_PER_MICRO = 1000L
        private const val MAX_DECODE_US = 1_000_000L // a longer one is an outlier, not a decode
    }
}

/**
 * Writes what the decoder did in a window at DEBUG, which the verbose switch enables, for the
 * device checks.
 */
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
                "decoded" to counts.decoded,
                "decode_avg_us" to counts.decodeAvgUs,
                "decode_min_us" to counts.decodeMinUs,
                "decode_max_us" to counts.decodeMaxUs,
            )
        if (counts.firstInputs.isNotEmpty())
            fields += "first_in" to counts.firstInputs.joinToString(",")
        if (counts.firstOutputs.isNotEmpty()) {
            fields += "first_out" to counts.firstOutputs.joinToString(",")
        }
        BorealisLog.d(LogTag.VIDEO, "Decoder window", *fields.toTypedArray())
    }
}
