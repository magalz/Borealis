package io.github.magalz.borealis.stream

/**
 * The enqueue times of the frames in the decoder, oldest first, so each frame is timed on its way
 * out without the decoder's help: some low-latency decoders hand the frames back without the
 * presentation timestamp the measure used to rely on. The streams have no B-frames, so the frames
 * leave in the order they went in.
 *
 * The input thread calls [push] and [discardNewest], the renderer thread [pop], and [clear] runs
 * during codec recovery, when both are quiesced. A frame comes in or out about once per frame
 * period, so a lock costs less than the lock-free ring a drop-oldest policy would need.
 */
class DecodeTimeFifo {
    private val times = LongArray(CAPACITY)
    private var head = 0
    private var size = 0

    /** The frames dropped because the decoder kept more than [CAPACITY] of them. */
    @get:Synchronized
    var dropped = 0
        private set

    /** Records that a frame is about to go into the decoder at [uptimeMs]. */
    @Synchronized
    fun push(uptimeMs: Long) {
        if (size == CAPACITY) {
            head = (head + 1) % CAPACITY
            size--
            dropped++
        }
        times[(head + size) % CAPACITY] = uptimeMs
        size++
    }

    /** Takes back the last [push], for a frame the decoder refused. */
    @Synchronized
    fun discardNewest() {
        if (size > 0) size--
    }

    /** The time since the oldest frame went in, or [NONE] when no frame is in the decoder. */
    @Synchronized
    fun pop(nowMs: Long): Long {
        if (size == 0) return NONE
        val enqueuedMs = times[head]
        head = (head + 1) % CAPACITY
        size--
        return nowMs - enqueuedMs
    }

    /** Forgets the frames in the decoder, as a flush, a restart or a reset does. */
    @Synchronized
    fun clear() {
        head = 0
        size = 0
    }

    companion object {
        const val CAPACITY = 64

        /**
         * What [pop] returns with nothing in the decoder; the renderer's 0..1000 guard skips it.
         */
        const val NONE = -1L
    }
}
