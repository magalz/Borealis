package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Test

class DecodeTimeTrackerTest {
    private val tracker = DecodeTimeTracker()
    private val records = CopyOnWriteArrayList<LogRecord>()

    @After
    fun stopRecording() {
        BorealisLog.shutdown()
    }

    // The queue time of an input is its timestamp in nanoseconds, so a test says how long a frame
    // took with [out] and does not repeat the queue time.
    private fun input(vararg timestamps: Long) = timestamps.forEach {
        tracker.onInput(it, it * 1000)
    }

    private fun out(timestampUs: Long, decodeUs: Long = 0, outputUs: Long = timestampUs) =
        tracker.onOutput(outputUs, timestampUs * 1000 + decodeUs * 1000)

    @Test
    fun `an output that echoes its input returns the decode time in microseconds`() {
        input(BASE + 1, BASE + 2, BASE + 3)

        assertThat(out(BASE + 1, decodeUs = 400)).isEqualTo(400)
        assertThat(out(BASE + 2, decodeUs = 3_000)).isEqualTo(3_000)
        assertThat(out(BASE + 3, decodeUs = 0)).isEqualTo(0)
    }

    @Test
    fun `the decode time is the nanoseconds since the queue, cut to microseconds`() {
        tracker.onInput(BASE, 7_000_000_000L)

        assertThat(tracker.onOutput(BASE, 7_000_000_000L + 3_999)).isEqualTo(3)
    }

    @Test
    fun `a decoder that echoes the timestamps reads a 3 ms decode as 3000 microseconds`() {
        // The queue time, not the timestamp, is the start: the timestamp is fixed by the first
        // frame and can be ahead of the real submit time of a later one.
        for (frame in 1..3L) {
            val timestampUs = BASE + frame * 16_667
            val queueNs = 50_000_000_000L + frame * 16_667_000
            tracker.onInput(timestampUs, queueNs)

            val decodeUs = tracker.onOutput(timestampUs, queueNs + 3_000_000)

            assertThat(decodeUs).isEqualTo(3_000)
        }
    }

    @Test
    fun `a dropped frame does not shift the matches after it`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        assertThat(out(BASE + 1, decodeUs = 100)).isEqualTo(100)
        // BASE + 2 never comes out.
        assertThat(out(BASE + 3, decodeUs = 300)).isEqualTo(300)
        assertThat(out(BASE + 4, decodeUs = 400)).isEqualTo(400)
    }

    @Test
    fun `a matched output prunes the older inputs, so the late one finds nothing`() {
        input(BASE + 1, BASE + 2, BASE + 3)

        assertThat(out(BASE + 2, decodeUs = 200)).isEqualTo(200)
        // Reordered by one: BASE + 1 was pruned together with BASE + 2.
        assertThat(out(BASE + 1, decodeUs = 100)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(out(BASE + 3, decodeUs = 300)).isEqualTo(300)
    }

    @Test
    fun `a timestamp cut to 32 bits matches and returns the decode time of its own input`() {
        val wide = BASE + 0x1_2345_6789L
        input(wide)

        assertThat(out(wide, decodeUs = 2_500, outputUs = wide and LOW_32_BITS)).isEqualTo(2_500)
    }

    @Test
    fun `an exact match wins over a 32-bit match of an older input`() {
        val exact = 5L
        val olderWide = exact + (1L shl 32)
        // The wide input is older and has the same low 32 bits as the exact one.
        tracker.onInput(olderWide, 9_000_000_000L)
        tracker.onInput(exact, 1_000_000_000L)

        // Measured from the exact input's queue time, not from the older one's.
        assertThat(tracker.onOutput(exact, 1_000_000_000L + 700_000)).isEqualTo(700)
    }

    @Test
    fun `an unknown timestamp inside the range returns NO_MATCH and keeps the ring`() {
        input(BASE + 1, BASE + 3)

        assertThat(out(BASE + 2)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(out(BASE + 1, decodeUs = 100)).isEqualTo(100)
        assertThat(out(BASE + 3, decodeUs = 300)).isEqualTo(300)
        assertThat(tracker.drainWindow().unmatched).isEqualTo(1)
    }

    @Test
    fun `every output lands in exactly one class`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        out(BASE + 1) // exact
        out(BASE + 2, outputUs = (BASE + 2) and LOW_32_BITS) // truncated
        tracker.onOutput(0, 0) // zero
        tracker.onOutput(BASE + 99, 0) // ahead of every input
        tracker.onOutput(BASE - 5, 0) // older than every input
        out(BASE + 4) // exact again
        val counts = tracker.drainWindow()

        assertThat(counts.inputs).isEqualTo(4)
        assertThat(counts.outputs).isEqualTo(6)
        assertThat(counts.exact).isEqualTo(2)
        assertThat(counts.truncated).isEqualTo(1)
        assertThat(counts.zero).isEqualTo(1)
        assertThat(counts.ahead).isEqualTo(1)
        assertThat(counts.older).isEqualTo(1)
        assertThat(counts.unmatched).isEqualTo(0)
    }

    @Test
    fun `an output between two inputs that matches neither is counted as no match`() {
        input(BASE + 1, BASE + 5)

        out(BASE + 3)

        assertThat(tracker.drainWindow().unmatched).isEqualTo(1)
    }

    @Test
    fun `an output with nothing in the ring is not matched`() {
        assertThat(out(BASE)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.drainWindow().outputs).isEqualTo(1)
    }

    @Test
    fun `clear empties the ring`() {
        input(BASE + 1, BASE + 2)

        tracker.clear()

        assertThat(out(BASE + 1)).isEqualTo(DecodeTimeTracker.NO_MATCH)
    }

    @Test
    fun `the ring holds 128 inputs and drops the oldest`() {
        input(*LongArray(130) { BASE + it + 1L })

        assertThat(out(BASE + 1)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(out(BASE + 2)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(out(BASE + 3, decodeUs = 30)).isEqualTo(30)
        assertThat(out(BASE + 130, decodeUs = 1_300)).isEqualTo(1_300)
    }

    @Test
    fun `drainWindow returns the counts since the last drain and resets them`() {
        input(BASE + 1, BASE + 2)
        out(BASE + 1, decodeUs = 500)

        val first = tracker.drainWindow()
        val second = tracker.drainWindow()

        assertThat(first.inputs).isEqualTo(2)
        assertThat(first.outputs).isEqualTo(1)
        assertThat(first.exact).isEqualTo(1)
        assertThat(first.decoded).isEqualTo(1)
        assertThat(second.inputs).isEqualTo(0)
        assertThat(second.outputs).isEqualTo(0)
        assertThat(second.exact).isEqualTo(0)
        assertThat(second.decoded).isEqualTo(0)
        assertThat(second.decodeMaxUs).isEqualTo(0)
    }

    @Test
    fun `the window counts the matched frames and their average, minimum and maximum in microseconds`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        out(BASE + 1, decodeUs = 400)
        out(BASE + 2, decodeUs = 1_200)
        out(BASE + 3, decodeUs = 800)
        val counts = tracker.drainWindow()

        assertThat(counts.decoded).isEqualTo(3)
        assertThat(counts.decodeAvgUs).isEqualTo(800)
        assertThat(counts.decodeMinUs).isEqualTo(400)
        assertThat(counts.decodeMaxUs).isEqualTo(1_200)
    }

    @Test
    fun `the decode statistics start again in each window`() {
        input(BASE + 1, BASE + 2)
        out(BASE + 1, decodeUs = 9_000)
        tracker.drainWindow()

        out(BASE + 2, decodeUs = 100)
        val counts = tracker.drainWindow()

        assertThat(counts.decoded).isEqualTo(1)
        assertThat(counts.decodeMinUs).isEqualTo(100)
        assertThat(counts.decodeMaxUs).isEqualTo(100)
    }

    @Test
    fun `a negative or one second decode time is matched but not counted as decoded`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        assertThat(out(BASE + 1, decodeUs = -5)).isEqualTo(-5)
        assertThat(out(BASE + 2, decodeUs = 1_000_000)).isEqualTo(1_000_000)
        assertThat(out(BASE + 3, decodeUs = 999_999)).isEqualTo(999_999)
        assertThat(out(BASE + 4, decodeUs = 0)).isEqualTo(0)
        val counts = tracker.drainWindow()

        assertThat(counts.exact).isEqualTo(4)
        assertThat(counts.decoded).isEqualTo(2)
        assertThat(counts.decodeMinUs).isEqualTo(0)
        assertThat(counts.decodeMaxUs).isEqualTo(999_999)
    }

    @Test
    fun `a window without a decode time reports zeros`() {
        input(BASE + 1)
        out(BASE + 1, decodeUs = -1)

        val counts = tracker.drainWindow()

        assertThat(counts.decoded).isEqualTo(0)
        assertThat(counts.decodeAvgUs).isEqualTo(0)
        assertThat(counts.decodeMinUs).isEqualTo(0)
        assertThat(counts.decodeMaxUs).isEqualTo(0)
    }

    @Test
    fun `the first window carries up to 8 raw timestamps in and out, later windows none`() {
        for (i in 1..10L) input(BASE + i)
        for (i in 1..10L) out(BASE + i)

        val first = tracker.drainWindow()
        input(BASE + 11)
        out(BASE + 11)
        val second = tracker.drainWindow()

        assertThat(first.firstInputs).isEqualTo((1..8L).map { BASE + it })
        assertThat(first.firstOutputs).isEqualTo((1..8L).map { BASE + it })
        assertThat(second.firstInputs).isEmpty()
        assertThat(second.firstOutputs).isEmpty()
    }

    @Test
    fun `a first window with inputs only does not lose the output samples`() {
        input(BASE + 1, BASE + 2)
        val first = tracker.drainWindow()

        out(BASE + 1)
        input(BASE + 3)
        val second = tracker.drainWindow()
        out(BASE + 2)
        val third = tracker.drainWindow()

        assertThat(first.firstInputs).containsExactly(BASE + 1, BASE + 2).inOrder()
        assertThat(first.firstOutputs).isEmpty()
        assertThat(second.firstInputs).isEmpty()
        assertThat(second.firstOutputs).containsExactly(BASE + 1)
        assertThat(third.firstInputs).isEmpty()
        assertThat(third.firstOutputs).isEmpty()
    }

    @Test
    fun `after clear the next window samples the new decoder again`() {
        input(BASE + 1)
        tracker.drainWindow()

        tracker.clear()
        input(BASE + 2)
        out(BASE + 2)
        val counts = tracker.drainWindow()

        assertThat(counts.firstInputs).containsExactly(BASE + 2)
        assertThat(counts.firstOutputs).containsExactly(BASE + 2)
    }

    @Test
    fun `the window line names the codec and the counts`() {
        BorealisLog.initialize(listOf(Recorder(records)), minLevel = LogLevel.DEBUG)
        input(BASE + 1, BASE + 2)
        out(BASE + 1, decodeUs = 400)
        tracker.onOutput(0, 0)

        DecodeTimeLog.window("c2.mtk.avc.decoder", tracker.drainWindow())

        assertThat(records).hasSize(1)
        val record = records.single()
        assertThat(record.level).isEqualTo(LogLevel.DEBUG)
        assertThat(record.tag).isEqualTo(LogTag.VIDEO)
        val fields = record.fields.toMap()
        assertThat(fields["codec"]).isEqualTo("c2.mtk.avc.decoder")
        assertThat(fields["in"]).isEqualTo("2")
        assertThat(fields["out"]).isEqualTo("2")
        assertThat(fields["exact"]).isEqualTo("1")
        assertThat(fields["zero"]).isEqualTo("1")
        assertThat(fields["decoded"]).isEqualTo("1")
        assertThat(fields["decode_avg_us"]).isEqualTo("400")
        assertThat(fields["decode_min_us"]).isEqualTo("400")
        assertThat(fields["decode_max_us"]).isEqualTo("400")
        assertThat(fields["first_in"]).isEqualTo("${BASE + 1},${BASE + 2}")
        assertThat(fields["first_out"]).isEqualTo("${BASE + 1},0")
    }

    @Test
    fun `later window lines carry no timestamp samples`() {
        BorealisLog.initialize(listOf(Recorder(records)), minLevel = LogLevel.DEBUG)
        input(BASE + 1)
        DecodeTimeLog.window("dec", tracker.drainWindow())

        DecodeTimeLog.window("dec", tracker.drainWindow())

        val fields = records.last().fields.toMap()
        assertThat(fields).doesNotContainKey("first_in")
        assertThat(fields).doesNotContainKey("first_out")
    }

    private class Recorder(private val into: MutableList<LogRecord>) : LogSink {
        override fun write(record: LogRecord) {
            into += record
        }
    }

    private companion object {
        const val BASE = 10_000_000_000L
        const val LOW_32_BITS = 0xFFFF_FFFFL
    }
}
