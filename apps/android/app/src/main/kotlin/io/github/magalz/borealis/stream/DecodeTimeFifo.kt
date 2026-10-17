package io.github.magalz.borealis.stream

/** Enqueue times of the frames in the decoder, oldest first, to time each one on its way out. */
class DecodeTimeFifo {
    /** The frames dropped because the decoder kept more than [CAPACITY] of them. */
    val dropped: Int
        get() = 0

    fun push(uptimeMs: Long) = Unit

    fun discardNewest() = Unit

    fun pop(nowMs: Long): Long = NONE

    fun clear() = Unit

    companion object {
        const val CAPACITY = 64
        const val NONE = -1L
    }
}
