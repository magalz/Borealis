package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.stream.PerfSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.floor
import org.junit.Test

/**
 * Characterizes how the renderer thread measures the time a frame spent in the decoder, and how
 * that time reaches the overlay and the averages of the end of the stream.
 *
 * The time of a frame that the renderer can pair with its input comes from `System.nanoTime()`,
 * which a test cannot move, so those are tested through what must hold for any time. The renderer
 * falls back on the clock of `SystemClock` when a decoder returns a frame under another timestamp
 * than the input had; the fake codec can do that on request, and then the time is known exactly.
 *
 * The rounding of the time of a frame that was paired with its input is therefore a soft pin: see
 * the test that spreads the delays of the fake over the fractions of a millisecond.
 */
class RendererDecodeTimeTest : RendererTestBase() {
    private val snapshots = CopyOnWriteArrayList<PerfSnapshot>()

    private fun decoding(): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.setPerfEnabled(true)
        renderer.setPerfSink { snapshots += it }
        return renderer
    }

    /**
     * Sends [frames] to the decoder, lets the renderer thread take what comes out and finish with
     * it, then closes the window of statistics with one more frame.
     */
    private fun MediaCodecDecoderRenderer.decodeAndClose(
        frames: List<DecodeUnit>,
        shownBefore: Int = 0,
    ) {
        frames.forEach { assertThat(submit(it)).isEqualTo(MoonBridge.DR_OK) }
        begin()
        await("the frames to be shown") {
            events<CodecEvent.Output>().size >= shownBefore + frames.size
        }
        val polls = ShadowRecordingVideoCodec.dequeueOutputCalls.get()
        await("the renderer thread to finish") {
            ShadowRecordingVideoCodec.dequeueOutputCalls.get() > polls
        }
        advanceClock(1_000)
        submit(pFrame(frames.size + 1))
    }

    private fun frames(count: Int, times: DecodeUnit.Times = DecodeUnit.Times()) =
        (1..count).map { pFrame(it, times) }

    @Test
    fun `the time of a frame paired with its input is rounded to milliseconds half up`() {
        val renderer = decoding()

        renderer.decodeAndClose(frames(1))

        val decodeMs = checkNotNull(snapshots.single().decodeMs)
        assertThat(decodeMs).isAtLeast(0f)
        // One frame, so the average in whole milliseconds is the time rounded half up.
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(floor(decodeMs + 0.5f).toInt())
    }

    /**
     * A soft pin of the rounding half up of [MediaCodecDecoderRenderer], `(decodeUs + 500) / 1000`.
     * `System.nanoTime()` is real, so the time of a frame is its delay in the fake plus what the
     * threads add, which is not known, and the assertion cannot name the rounded value. It is an
     * identity that holds for any time (one frame, so the average is the time rounded half up), and
     * it never fails by chance. A renderer that cuts the time instead of rounding it differs only
     * on a frame whose time ends in 0.5 ms or more. The delays are spread over the whole
     * millisecond, 0.25 ms apart and twice each, so that whatever the threads add, half of the
     * frames end there.
     */
    @Test
    fun `the time of a frame is rounded half up whatever fraction of a millisecond it ends in`() {
        for ((trial, delayUs) in SPREAD_DELAYS_US.withIndex()) {
            ShadowRecordingVideoCodec.outputDelayNanos = delayUs * NANOS_PER_US
            val renderer = decoding()

            renderer.decodeAndClose(frames(1), shownBefore = trial)

            val decodeMs = checkNotNull(snapshots[trial].decodeMs)
            assertThat(decodeMs).isAtLeast(delayUs / 1000f)
            assertThat(renderer.getAverageDecoderLatency())
                .isEqualTo(floor(decodeMs + 0.5f).toInt())
            renderer.stop()
        }
    }

    @Test
    fun `the decoder time also counts toward the end to end latency`() {
        val renderer = decoding()

        renderer.decodeAndClose(frames(1))

        // The frame has no time between receive and enqueue, so the whole latency is the decode.
        assertThat(renderer.getAverageEndToEndLatency())
            .isEqualTo(renderer.getAverageDecoderLatency())
    }

    @Test
    fun `a frame that comes back with another timestamp is timed from the uptime clock`() {
        ShadowRecordingVideoCodec.outputTimestamp = { 0 }
        val renderer = decoding()
        advanceClock(200)

        renderer.decodeAndClose(frames(1))

        // 300 ms of uptime less the timestamp 0.
        assertThat(snapshots.single().decodeMs).isEqualTo(300f)
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(300)
    }

    @Test
    fun `the time from the uptime clock adds to the time between receive and enqueue`() {
        ShadowRecordingVideoCodec.outputTimestamp = { 0 }
        val renderer = decoding()
        advanceClock(200)

        renderer.decodeAndClose(frames(1, DecodeUnit.Times(receiveUs = 1_000, enqueueUs = 4_000)))

        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(300)
        assertThat(renderer.getAverageEndToEndLatency()).isEqualTo(303)
    }

    @Test
    fun `a time just under a second is counted`() {
        ShadowRecordingVideoCodec.outputTimestamp = { 0 }
        val renderer = decoding()
        advanceClock(899)

        renderer.decodeAndClose(frames(1))

        assertThat(snapshots.single().decodeMs).isEqualTo(999f)
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(999)
    }

    @Test
    fun `a time of a second or more is taken for an outlier and not counted`() {
        ShadowRecordingVideoCodec.outputTimestamp = { 0 }
        val renderer = decoding()
        advanceClock(900)

        renderer.decodeAndClose(frames(1))

        assertThat(snapshots.single().decodeMs).isNull()
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(0)
    }

    @Test
    fun `a negative time is taken for an outlier and not counted`() {
        // The decoder says the frame is from 200 ms in the future.
        ShadowRecordingVideoCodec.outputTimestamp = { 500_000 }
        val renderer = decoding()
        advanceClock(200)

        renderer.decodeAndClose(frames(1))

        assertThat(snapshots.single().decodeMs).isNull()
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(0)
    }

    @Test
    fun `of frames taken from the decoder together only the newest is timed`() {
        ShadowRecordingVideoCodec.outputTimestamp = { 0 }
        val renderer = decoding()
        advanceClock(200)

        renderer.decodeAndClose(frames(2))

        // Two frames received and one time of 300 ms: 150 ms a frame.
        assertThat(snapshots.single().decodeMs).isEqualTo(150f)
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(150)
    }

    private companion object {
        const val NANOS_PER_US = 1_000L

        // Two rounds of four delays, a quarter of a millisecond apart.
        val SPREAD_DELAYS_US =
            listOf(2_000L, 2_250L, 2_500L, 2_750L, 2_000L, 2_250L, 2_500L, 2_750L)
    }
}
