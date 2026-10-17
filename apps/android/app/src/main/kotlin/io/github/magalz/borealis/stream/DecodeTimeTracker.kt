package io.github.magalz.borealis.stream

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

/** Stub, replaced by the implementation. */
class DecodeTimeTracker {
    fun onInput(timestampUs: Long) = Unit

    fun onOutput(presentationTimeUs: Long): Long = NO_MATCH

    fun clear() = Unit

    fun drainWindow(): DecodeTimeCounts = DecodeTimeCounts()

    companion object {
        const val NO_MATCH = Long.MIN_VALUE
    }
}

/** Stub, replaced by the implementation. */
object DecodeTimeLog {
    @JvmStatic fun window(codec: String?, counts: DecodeTimeCounts) = Unit
}
