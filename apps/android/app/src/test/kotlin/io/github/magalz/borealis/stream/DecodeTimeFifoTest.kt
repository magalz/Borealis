package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DecodeTimeFifoTest {
    private val fifo = DecodeTimeFifo()

    @Test
    fun `a frame comes out with the time it spent in the decoder`() {
        fifo.push(1_000)

        assertThat(fifo.pop(1_007)).isEqualTo(7)
    }

    @Test
    fun `frames come out in the order they went in`() {
        fifo.push(1_000)
        fifo.push(1_016)
        fifo.push(1_033)

        assertThat(fifo.pop(1_040)).isEqualTo(40)
        assertThat(fifo.pop(1_041)).isEqualTo(25)
        assertThat(fifo.pop(1_042)).isEqualTo(9)
    }

    @Test
    fun `an empty fifo has no time to give`() {
        assertThat(fifo.pop(1_000)).isEqualTo(DecodeTimeFifo.NONE)

        fifo.push(990)
        fifo.pop(1_000)

        assertThat(fifo.pop(1_001)).isEqualTo(DecodeTimeFifo.NONE)
    }

    @Test
    fun `clearing forgets the frames that were in the decoder`() {
        fifo.push(1_000)
        fifo.push(1_016)

        fifo.clear()

        assertThat(fifo.pop(1_020)).isEqualTo(DecodeTimeFifo.NONE)
        fifo.push(1_100)
        assertThat(fifo.pop(1_105)).isEqualTo(5)
    }

    @Test
    fun `a frame that was not queued after all can be taken back`() {
        fifo.push(1_000)
        fifo.push(1_016)

        fifo.discardNewest()

        assertThat(fifo.pop(1_020)).isEqualTo(20)
        assertThat(fifo.pop(1_021)).isEqualTo(DecodeTimeFifo.NONE)
    }

    @Test
    fun `a full fifo drops the oldest frame and counts it`() {
        repeat(DecodeTimeFifo.CAPACITY + 1) { fifo.push(1_000L + it) }

        assertThat(fifo.dropped).isEqualTo(1)
        // Frame 0 is gone: the oldest left is the one pushed at 1_001.
        assertThat(fifo.pop(1_101)).isEqualTo(100)
    }

    @Test
    fun `a decoder that swallows frames cannot grow the fifo without bound`() {
        repeat(DecodeTimeFifo.CAPACITY * 10) { fifo.push(it.toLong()) }

        assertThat(fifo.dropped).isEqualTo(DecodeTimeFifo.CAPACITY * 9)
        var left = 0
        while (fifo.pop(10_000) != DecodeTimeFifo.NONE) left++
        assertThat(left).isEqualTo(DecodeTimeFifo.CAPACITY)
    }

    // The renderer adds a frame's time to the window when 0 <= delta < 1000, and the overlay shows
    // the window's total over the frames it received.
    private fun windowDecodeMs(deltas: List<Long>, framesReceived: Int): Float? {
        val total = deltas.filter { it in 0 until 1_000 }.sum()
        val window = PerfWindow(framesReceived, framesReceived, framesReceived, 0, total, 0, 0, 0, 0, 0, 1_000)
        return PerfSnapshots.from(window, 0).decodeMs
    }

    @Test
    fun `the renderer's order of pushes, pops and a flush gives the decode time of the window`() {
        val deltas = mutableListOf<Long>()
        // Two frames in, two frames out.
        fifo.push(1_000)
        fifo.push(1_016)
        deltas += fifo.pop(1_004)
        deltas += fifo.pop(1_021)
        // A frame is in flight when the decoder is flushed; its output never comes.
        fifo.push(1_033)
        fifo.clear()
        // The next two frames are timed from their own enqueue, not the lost one's.
        fifo.push(1_050)
        fifo.push(1_066)
        deltas += fifo.pop(1_053)
        deltas += fifo.pop(1_071)
        // An output with nothing in the fifo adds nothing to the total.
        deltas += fifo.pop(1_080)

        assertThat(deltas.take(4)).containsExactly(4L, 5L, 3L, 5L).inOrder()
        assertThat(windowDecodeMs(deltas, framesReceived = 4)).isWithin(0.001f).of(4.25f)
    }
}
