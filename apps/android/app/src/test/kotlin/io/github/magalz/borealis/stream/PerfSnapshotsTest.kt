package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerfSnapshotsTest {
    /** A window of two seconds at about 60 fps; a test changes the counters it is about. */
    private val window =
        PerfWindow(
            totalFrames = 120,
            framesReceived = 118,
            framesRendered = 116,
            framesLost = 2,
            decoderTimeUs = 590_000,
            hostLatencyFrames = 118,
            hostLatencyMinTenthsMs = 20,
            hostLatencyMaxTenthsMs = 80,
            hostLatencyTotalTenthsMs = 5900,
            bytes = 2_500_000,
            elapsedMs = 2_000,
        )

    private fun rtt(rtt: Int, variance: Int) = (rtt.toLong() shl 32) or variance.toLong()

    @Test
    fun `frames per second are the counters over the window duration`() {
        val snapshot = PerfSnapshots.from(window, rtt(12, 3))

        assertThat(snapshot.totalFps).isWithin(0.001f).of(60f)
        assertThat(snapshot.receivedFps).isWithin(0.001f).of(59f)
        assertThat(snapshot.renderedFps).isWithin(0.001f).of(58f)
    }

    @Test
    fun `the loss is the lost frames over all frames in percent`() {
        val snapshot = PerfSnapshots.from(window.copy(totalFrames = 200, framesLost = 5), rtt(1, 1))

        assertThat(snapshot.lossPercent).isWithin(0.001f).of(2.5f)
    }

    @Test
    fun `the round trip time and its variance come from the two halves`() {
        val snapshot = PerfSnapshots.from(window, rtt(12, 3))

        assertThat(snapshot.rttMs).isEqualTo(12)
        assertThat(snapshot.rttVarianceMs).isEqualTo(3)
    }

    @Test
    fun `the host latency is in milliseconds as minimum, average and maximum`() {
        val latency = PerfSnapshots.from(window, rtt(1, 1)).hostLatency

        assertThat(latency).isNotNull()
        assertThat(latency!!.min).isWithin(0.001f).of(2.0f)
        assertThat(latency.max).isWithin(0.001f).of(8.0f)
        assertThat(latency.avg).isWithin(0.001f).of(5.0f)
    }

    @Test
    fun `the host latency is null when no frame carried it`() {
        val snapshot =
            PerfSnapshots.from(
                window.copy(
                    hostLatencyFrames = 0,
                    hostLatencyMinTenthsMs = 0,
                    hostLatencyMaxTenthsMs = 0,
                    hostLatencyTotalTenthsMs = 0,
                ),
                rtt(1, 1),
            )

        assertThat(snapshot.hostLatency).isNull()
    }

    @Test
    fun `the decode time is the decoder time per received frame`() {
        val snapshot =
            PerfSnapshots.from(
                window.copy(framesReceived = 100, decoderTimeUs = 450_000),
                rtt(1, 1),
            )

        assertThat(snapshot.decodeMs).isWithin(0.001f).of(4.5f)
    }

    @Test
    fun `a window with frames but no decoder time has no decode time, not zero`() {
        // Some low-latency decoders hand the frames back without the timestamp the measure needs.
        val snapshot =
            PerfSnapshots.from(window.copy(framesReceived = 118, decoderTimeUs = 0), rtt(1, 1))

        assertThat(snapshot.decodeMs).isNull()
    }

    @Test
    fun `a sub-millisecond decode shows as a fraction of a millisecond`() {
        val snapshot =
            PerfSnapshots.from(window.copy(framesReceived = 30, decoderTimeUs = 12_000), rtt(1, 1))

        assertThat(snapshot.decodeMs).isWithin(0.0001f).of(0.4f)
    }

    @Test
    fun `a window whose decoder time is a few microseconds is still a measure`() {
        val snapshot =
            PerfSnapshots.from(window.copy(framesReceived = 100, decoderTimeUs = 3), rtt(1, 1))

        assertThat(snapshot.decodeMs).isWithin(0.00001f).of(0.00003f)
    }

    @Test
    fun `the bitrate is the bytes in megabits over the window duration`() {
        val snapshot =
            PerfSnapshots.from(window.copy(bytes = 2_500_000, elapsedMs = 2_000), rtt(1, 1))

        assertThat(snapshot.bitrateMbps).isNotNull()
        assertThat(snapshot.bitrateMbps!!).isWithin(0.001f).of(10f)
    }

    @Test
    fun `a window without frames gives zeros, no decode time and no division by zero`() {
        val snapshot =
            PerfSnapshots.from(
                window.copy(
                    totalFrames = 0,
                    framesReceived = 0,
                    framesRendered = 0,
                    framesLost = 0,
                    decoderTimeUs = 0,
                    hostLatencyFrames = 0,
                    hostLatencyMinTenthsMs = 0,
                    hostLatencyMaxTenthsMs = 0,
                    hostLatencyTotalTenthsMs = 0,
                    bytes = 0,
                ),
                rtt(0, 0),
            )

        assertThat(snapshot.totalFps).isEqualTo(0f)
        assertThat(snapshot.receivedFps).isEqualTo(0f)
        assertThat(snapshot.renderedFps).isEqualTo(0f)
        assertThat(snapshot.lossPercent).isEqualTo(0f)
        assertThat(snapshot.decodeMs).isNull()
        assertThat(snapshot.hostLatency).isNull()
        assertThat(snapshot.bitrateMbps).isEqualTo(0f)
    }

    @Test
    fun `a window with no duration has no fps and no bitrate`() {
        val snapshot = PerfSnapshots.from(window.copy(elapsedMs = 0), rtt(1, 1))

        assertThat(snapshot.totalFps).isEqualTo(0f)
        assertThat(snapshot.receivedFps).isEqualTo(0f)
        assertThat(snapshot.renderedFps).isEqualTo(0f)
        assertThat(snapshot.bitrateMbps).isNull()
    }
}
