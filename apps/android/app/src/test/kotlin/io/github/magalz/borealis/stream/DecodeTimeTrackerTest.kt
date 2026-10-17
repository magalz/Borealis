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

    private fun input(vararg timestamps: Long) = timestamps.forEach(tracker::onInput)

    @Test
    fun `outputs that echo the input timestamps match in order`() {
        input(BASE + 1, BASE + 2, BASE + 3)

        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(BASE + 1)
        assertThat(tracker.onOutput(BASE + 2)).isEqualTo(BASE + 2)
        assertThat(tracker.onOutput(BASE + 3)).isEqualTo(BASE + 3)
    }

    @Test
    fun `a dropped frame does not shift the matches after it`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(BASE + 1)
        // BASE + 2 never comes out.
        assertThat(tracker.onOutput(BASE + 3)).isEqualTo(BASE + 3)
        assertThat(tracker.onOutput(BASE + 4)).isEqualTo(BASE + 4)
    }

    @Test
    fun `a matched output prunes the older inputs, so the late one finds nothing`() {
        input(BASE + 1, BASE + 2, BASE + 3)

        assertThat(tracker.onOutput(BASE + 2)).isEqualTo(BASE + 2)
        // Reordered by one: BASE + 1 was pruned together with BASE + 2.
        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.onOutput(BASE + 3)).isEqualTo(BASE + 3)
    }

    @Test
    fun `a timestamp cut to 32 bits matches and returns the full input timestamp`() {
        val wide = BASE + 0x1_2345_6789L
        input(wide)

        assertThat(tracker.onOutput(wide and LOW_32_BITS)).isEqualTo(wide)
    }

    @Test
    fun `an exact match wins over a 32-bit match of an older input`() {
        val exact = 5L
        val olderWide = exact + (1L shl 32)
        // The wide input is older and has the same low 32 bits as the exact one.
        input(olderWide, exact)

        assertThat(tracker.onOutput(exact)).isEqualTo(exact)
    }

    @Test
    fun `an unknown timestamp inside the range returns NO_MATCH and keeps the ring`() {
        input(BASE + 1, BASE + 3)

        assertThat(tracker.onOutput(BASE + 2)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(BASE + 1)
        assertThat(tracker.onOutput(BASE + 3)).isEqualTo(BASE + 3)
        assertThat(tracker.drainWindow().unmatched).isEqualTo(1)
    }

    @Test
    fun `every output lands in exactly one class`() {
        input(BASE + 1, BASE + 2, BASE + 3, BASE + 4)

        tracker.onOutput(BASE + 1) // exact
        tracker.onOutput((BASE + 2) and LOW_32_BITS) // truncated
        tracker.onOutput(0) // zero
        tracker.onOutput(BASE + 99) // ahead of every input
        tracker.onOutput(BASE - 5) // older than every input
        tracker.onOutput(BASE + 4) // exact again
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

        tracker.onOutput(BASE + 3)

        assertThat(tracker.drainWindow().unmatched).isEqualTo(1)
    }

    @Test
    fun `an output with nothing in the ring is not matched`() {
        assertThat(tracker.onOutput(BASE)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.drainWindow().outputs).isEqualTo(1)
    }

    @Test
    fun `clear empties the ring`() {
        input(BASE + 1, BASE + 2)

        tracker.clear()

        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(DecodeTimeTracker.NO_MATCH)
    }

    @Test
    fun `the ring holds 128 inputs and drops the oldest`() {
        for (i in 1..130L) tracker.onInput(BASE + i)

        assertThat(tracker.onOutput(BASE + 1)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.onOutput(BASE + 2)).isEqualTo(DecodeTimeTracker.NO_MATCH)
        assertThat(tracker.onOutput(BASE + 3)).isEqualTo(BASE + 3)
        assertThat(tracker.onOutput(BASE + 130)).isEqualTo(BASE + 130)
    }

    @Test
    fun `drainWindow returns the counts since the last drain and resets them`() {
        input(BASE + 1, BASE + 2)
        tracker.onOutput(BASE + 1)

        val first = tracker.drainWindow()
        val second = tracker.drainWindow()

        assertThat(first.inputs).isEqualTo(2)
        assertThat(first.outputs).isEqualTo(1)
        assertThat(first.exact).isEqualTo(1)
        assertThat(second.inputs).isEqualTo(0)
        assertThat(second.outputs).isEqualTo(0)
        assertThat(second.exact).isEqualTo(0)
    }

    @Test
    fun `the first window carries up to 8 raw timestamps in and out, later windows none`() {
        for (i in 1..10L) tracker.onInput(BASE + i)
        for (i in 1..10L) tracker.onOutput(BASE + i)

        val first = tracker.drainWindow()
        tracker.onInput(BASE + 11)
        tracker.onOutput(BASE + 11)
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

        tracker.onOutput(BASE + 1)
        tracker.onInput(BASE + 3)
        val second = tracker.drainWindow()
        tracker.onOutput(BASE + 2)
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
        tracker.onOutput(BASE + 2)
        val counts = tracker.drainWindow()

        assertThat(counts.firstInputs).containsExactly(BASE + 2)
        assertThat(counts.firstOutputs).containsExactly(BASE + 2)
    }

    @Test
    fun `a decoder that echoes the timestamps reports the delta of the output timestamp`() {
        // Today the delta is uptime - presentationTimeUs / 1000. With echoed timestamps the matched
        // input timestamp is the output timestamp, so the delta does not change.
        val uptimeMs = 123_456L
        for (offsetUs in listOf(2_000L, 5_500L, 9_000L)) {
            val timestampUs = uptimeMs * 1000 - offsetUs
            tracker.onInput(timestampUs)

            val matched = tracker.onOutput(timestampUs)

            assertThat(uptimeMs - matched / 1000).isEqualTo(uptimeMs - timestampUs / 1000)
        }
    }

    @Test
    fun `the window line names the codec and the counts`() {
        BorealisLog.initialize(listOf(Recorder(records)), minLevel = LogLevel.DEBUG)
        input(BASE + 1, BASE + 2)
        tracker.onOutput(BASE + 1)
        tracker.onOutput(0)

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
