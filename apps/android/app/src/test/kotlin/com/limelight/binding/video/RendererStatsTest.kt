package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.LatencyRange
import io.github.magalz.borealis.stream.PerfSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * Characterizes what `submitDecodeUnit` counts: the timestamps it gives the codec, the window of
 * statistics that it closes about every second, and what the overlay and the end of the stream read
 * from those windows. There is no renderer thread in these tests: the decode time, which only the
 * renderer thread measures, is in [RendererDecodeTimeTest].
 */
class RendererStatsTest : RendererTestBase() {
    private val snapshots = CopyOnWriteArrayList<PerfSnapshot>()

    /** A renderer for H.264 whose overlay feed collects into [snapshots]. */
    private fun counting(): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.setPerfEnabled(true)
        renderer.setPerfSink { snapshots += it }
        return renderer
    }

    /** Submits [unit] and gives the codec's output back, as a renderer thread would. */
    private fun MediaCodecDecoderRenderer.feed(unit: DecodeUnit): Int =
        submit(unit).also { ShadowRecordingVideoCodec.freeOutputs() }

    private fun times(latency: Int = 0, receive: Long = 0, enqueue: Long = 0) =
        DecodeUnit.Times(latency, receive, enqueue)

    private fun queued() = events<CodecEvent.Queued>()

    // Timestamps

    @Test
    fun `the first frame is stamped with the uptime in microseconds`() {
        val renderer = counting()

        renderer.feed(pFrame(1, times(enqueue = 5_000)))

        assertThat(queued().single().ptsUs).isEqualTo(100_000)
    }

    @Test
    fun `the later frames keep the offset of the first, whatever the clock says`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(enqueue = 5_000)))
        advanceClock(400)

        renderer.feed(pFrame(2, times(enqueue = 22_000)))

        // 100,000 - 5,000 is the offset: 95,000.
        assertThat(queued().map { it.ptsUs }).containsExactly(100_000L, 117_000L).inOrder()
    }

    @Test
    fun `a frame that is not newer than the last gets the last timestamp plus one`() {
        val renderer = counting()

        for (number in 1..4) renderer.feed(pFrame(number, times(enqueue = 5_000)))
        renderer.feed(pFrame(5, times(enqueue = 4_000)))
        renderer.feed(pFrame(6, times(enqueue = 9_000)))

        assertThat(queued().map { it.ptsUs })
            .containsExactly(100_000L, 100_001L, 100_002L, 100_003L, 100_004L, 104_000L)
            .inOrder()
    }

    @Test
    fun `the config buffer of an IDR frame is stamped zero and the frame is stamped normally`() {
        val renderer = counting()

        renderer.submit(parameterSet(MoonBridge.BUFFER_TYPE_PPS, "00000001 68ce3c80"))
        renderer.submit(idrSlice(1, times(enqueue = 7_000)))

        assertThat(queued().map { it.ptsUs }).containsExactly(0L, 100_000L).inOrder()
    }

    @Test
    fun `a frame that cannot get an input buffer is received but neither counted in nor stamped`() {
        val renderer = counting()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT) {
            codecException(errorCode = -3, actionCode = ACTION_TRANSIENT)
        }

        val result =
            renderer.submit(pFrame(1, times(latency = 20, receive = 1_000, enqueue = 5_000)))

        // The host statistics come before the fetch; the frame count, the timestamp base and the
        // last timestamp come after it.
        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        val open = renderer.open()
        assertThat(open.int("totalFramesReceived")).isEqualTo(1)
        assertThat(open.int("totalHostProcessingLatency")).isEqualTo(20)
        assertThat(open.long("totalTimeMs")).isEqualTo(4L)
        assertThat(ReflectionHelpers.getField<Int>(renderer, "numFramesIn")).isEqualTo(0)
        assertThat(ReflectionHelpers.getField<Long>(renderer, "baseTimestampUs")).isEqualTo(0L)
        assertThat(ReflectionHelpers.getField<Long>(renderer, "lastTimestampUs")).isEqualTo(0L)
    }

    @Test
    fun `the offset of the timestamps is taken from the first frame that gets an input buffer`() {
        val renderer = counting()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT) {
            codecException(errorCode = -3, actionCode = ACTION_TRANSIENT)
        }
        renderer.submit(pFrame(1, times(enqueue = 5_000)))
        advanceClock(400)

        renderer.feed(pFrame(2, times(enqueue = 22_000)))

        // The frame that failed set no offset: this one is stamped with the uptime, 500,000.
        assertThat(queued().single().ptsUs).isEqualTo(500_000)
    }

    @Test
    fun `a frame that fails to queue was counted in and stamped`() {
        val renderer = counting()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) {
            codecException(errorCode = -3, actionCode = ACTION_TRANSIENT)
        }

        val result = renderer.submit(pFrame(1, times(enqueue = 5_000)))
        renderer.feed(pFrame(2, times(enqueue = 5_000)))

        // The second frame is not newer than the first, which took 100,000 though it failed.
        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(queued().single().ptsUs).isEqualTo(100_001)
        assertThat(ReflectionHelpers.getField<Int>(renderer, "numFramesIn")).isEqualTo(2)
    }

    // The overlay feed

    @Test
    fun `no snapshot is pushed unless the window is on`() {
        val renderer = counting()
        renderer.setPerfEnabled(false)
        renderer.feed(pFrame(1))
        advanceClock(2_000)

        renderer.feed(pFrame(2))

        assertThat(snapshots).isEmpty()
    }

    @Test
    fun `turning the window on without a sink pushes nothing and fails nowhere`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        renderer.setPerfEnabled(true)
        renderer.feed(pFrame(1))
        advanceClock(1_000)

        val result = renderer.feed(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
    }

    @Test
    fun `the window closes when a second has passed since its first frame`() {
        val renderer = counting()
        renderer.feed(pFrame(1))

        advanceClock(999)
        renderer.feed(pFrame(2))
        assertThat(snapshots).isEmpty()

        advanceClock(1)
        renderer.feed(pFrame(3))
        assertThat(snapshots).hasSize(1)
    }

    @Test
    fun `a snapshot reports the frames, the losses and the bytes of the window`() {
        ShadowRecordingBridge.answers["getEstimatedRttInfo"] = (12L shl 32) or 3L
        val renderer = counting()
        renderer.feed(pFrame(1, times(30, receive = 1_000, enqueue = 3_000)))
        renderer.feed(pFrame(2, times(10, receive = 2_000, enqueue = 5_500)))
        renderer.feed(pFrame(3, times(0, receive = 3_000, enqueue = 3_000)))
        renderer.feed(pFrame(4, times(20, receive = 4_000, enqueue = 4_500)))
        // Two frames never came.
        renderer.feed(pFrame(7))
        advanceClock(1_000)

        renderer.feed(pFrame(8))

        val snapshot = snapshots.single()
        // Seven frames counted with the lost ones, five received, over exactly one second.
        assertThat(snapshot.totalFps).isWithin(EPSILON).of(7f)
        assertThat(snapshot.receivedFps).isWithin(EPSILON).of(5f)
        assertThat(snapshot.renderedFps).isWithin(EPSILON).of(0f)
        assertThat(snapshot.lossPercent).isWithin(EPSILON).of(2f / 7f * 100f)
        assertThat(snapshot.rttMs).isEqualTo(12)
        assertThat(snapshot.rttVarianceMs).isEqualTo(3)
        assertThat(snapshot.hostLatency).isEqualTo(LatencyRange(min = 1f, avg = 2f, max = 3f))
        assertThat(snapshot.decodeMs).isNull()
        assertThat(snapshot.bitrateMbps).isWithin(EPSILON).of(5 * 9 * 8 / 1_000_000f)
    }

    @Test
    fun `the first frame number is not counted as a loss however high it is`() {
        val renderer = counting()
        renderer.feed(pFrame(500))
        advanceClock(1_000)

        renderer.feed(pFrame(501))

        assertThat(snapshots.single().lossPercent).isEqualTo(0f)
        assertThat(snapshots.single().receivedFps).isWithin(EPSILON).of(1f)
    }

    @Test
    fun `a stream that starts at frame number zero closes its window after a second`() {
        val renderer = counting()
        renderer.feed(pFrame(0))
        advanceClock(1_000)

        renderer.feed(pFrame(1))

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots.single().lossPercent).isEqualTo(0f)
        assertThat(snapshots.single().receivedFps).isWithin(EPSILON).of(1f)
    }

    @Test
    fun `a frame number repeated or a step of one is not a loss`() {
        val renderer = counting()
        for (number in listOf(1, 1, 2, 2, 2, 3)) renderer.feed(pFrame(number))
        advanceClock(1_000)

        renderer.feed(pFrame(4))

        assertThat(snapshots.single().lossPercent).isEqualTo(0f)
    }

    @Test
    fun `each gap in the frame numbers is a loss event and adds what it skipped`() {
        val renderer = counting()
        // 1, 2, a gap of one, 4 again, 5, a gap of three
        for (number in listOf(1, 2, 4, 4, 5, 9)) renderer.feed(pFrame(number))
        advanceClock(1_000)

        renderer.feed(pFrame(10))

        // 6 received and 4 lost.
        assertThat(snapshots.single().lossPercent).isWithin(EPSILON).of(4f / 10f * 100f)
        assertThat(snapshots.single().totalFps).isWithin(EPSILON).of(10f)
    }

    @Test
    fun `a gap in the frame numbers is one loss event at the end of the stream, a step is none`() {
        val renderer = counting()
        // Steps and repeats, a gap of one (3 to 5), a repeat, a gap of three (6 to 10)
        for (number in listOf(1, 2, 2, 3, 5, 5, 6, 10)) renderer.feed(pFrame(number))
        advanceClock(1_000)

        renderer.feed(pFrame(11))

        val global = ReflectionHelpers.getField<Any>(renderer, "globalVideoStats")
        assertThat(ReflectionHelpers.getField<Int>(global, "frameLossEvents")).isEqualTo(2)
        assertThat(ReflectionHelpers.getField<Int>(global, "framesLost")).isEqualTo(4)
    }

    @Test
    fun `the unit that closes a window is counted in the window it closes`() {
        val renderer = counting()
        renderer.feed(pFrame(1))
        advanceClock(1_000)

        renderer.feed(pFrame(2))

        // Two units of 9 bytes: the second closed the window and its bytes are in it, but it is
        // not a received frame of it yet.
        val closed = ReflectionHelpers.getField<Any>(renderer, "lastWindowVideoStats")
        val open = ReflectionHelpers.getField<Any>(renderer, "activeWindowVideoStats")
        assertThat(ReflectionHelpers.getField<Long>(closed, "totalBytes")).isEqualTo(18L)
        assertThat(ReflectionHelpers.getField<Int>(closed, "totalFramesReceived")).isEqualTo(1)
        assertThat(ReflectionHelpers.getField<Long>(open, "totalBytes")).isEqualTo(0L)
        assertThat(ReflectionHelpers.getField<Int>(open, "totalFramesReceived")).isEqualTo(1)
    }

    /** The stats of the open window, by field name as the other tests of the renderer read them. */
    private fun MediaCodecDecoderRenderer.open(): Any =
        ReflectionHelpers.getField(this, "activeWindowVideoStats")

    private fun Any.long(field: String) = ReflectionHelpers.getField<Long>(this, field)

    private fun Any.int(field: String) = ReflectionHelpers.getField<Int>(this, field)

    /** The SPS and the PPS of an IDR frame, each unit with a host latency and a receive time. */
    private fun MediaCodecDecoderRenderer.sendParameterSetsWithTimes() {
        val withTimes = times(latency = 30, receive = 1_000, enqueue = 5_000)
        for ((type, data) in
            listOf(
                MoonBridge.BUFFER_TYPE_SPS to H264Sps().nal(),
                MoonBridge.BUFFER_TYPE_PPS to bytes("00000001 68ce3c80"),
            )) {
            submit(DecodeUnit(data, type, 1, MoonBridge.FRAME_TYPE_IDR, withTimes))
        }
    }

    @Test
    fun `a parameter set is not a received frame, but its bytes are counted`() {
        val renderer = counting()

        renderer.sendParameterSetsWithTimes()

        val open = renderer.open()
        assertThat(open.long("totalBytes")).isEqualTo(H264Sps().nal().size + 8L)
        assertThat(open.int("totalFramesReceived")).isEqualTo(0)
        assertThat(open.int("totalFrames")).isEqualTo(0)
        assertThat(open.int("framesWithHostProcessingLatency")).isEqualTo(0)
        assertThat(open.int("totalHostProcessingLatency")).isEqualTo(0)
        assertThat(open.long("totalTimeMs")).isEqualTo(0L)
    }

    @Test
    fun `the picture data of an IDR frame is the unit that is received`() {
        val renderer = counting()
        renderer.sendParameterSetsWithTimes()

        renderer.submit(idrSlice(1, times(latency = 20, receive = 1_000, enqueue = 4_000)))

        val open = renderer.open()
        assertThat(open.int("totalFramesReceived")).isEqualTo(1)
        assertThat(open.int("totalFrames")).isEqualTo(1)
        assertThat(open.int("framesWithHostProcessingLatency")).isEqualTo(1)
        assertThat(open.int("totalHostProcessingLatency")).isEqualTo(20)
        assertThat(open.long("totalTimeMs")).isEqualTo(3L)
    }

    @Test
    fun `an IDR frame that cannot get an input buffer for its config is not received`() {
        val renderer = counting()
        renderer.sendParameterSetsWithTimes()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT) {
            codecException(errorCode = -3, actionCode = ACTION_TRANSIENT)
        }

        val result =
            renderer.submit(idrSlice(1, times(latency = 20, receive = 1_000, enqueue = 4_000)))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        val open = renderer.open()
        assertThat(open.int("totalFramesReceived")).isEqualTo(0)
        assertThat(open.int("totalHostProcessingLatency")).isEqualTo(0)
        assertThat(open.long("totalTimeMs")).isEqualTo(0L)
        // The unit itself was counted by the bytes, as it reached the renderer.
        assertThat(open.long("totalBytes")).isEqualTo(H264Sps().nal().size + 8L + 10L)
    }

    @Test
    fun `an IDR frame whose config cannot be queued is not received`() {
        val renderer = counting()
        renderer.sendParameterSetsWithTimes()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) {
            codecException(errorCode = -3, actionCode = ACTION_TRANSIENT)
        }

        val result =
            renderer.submit(idrSlice(1, times(latency = 20, receive = 1_000, enqueue = 4_000)))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        val open = renderer.open()
        assertThat(open.int("totalFramesReceived")).isEqualTo(0)
        assertThat(open.int("totalHostProcessingLatency")).isEqualTo(0)
        assertThat(open.long("totalTimeMs")).isEqualTo(0L)
    }

    @Test
    fun `a unit of a new frame that is not an IDR frame leaves the parameter sets alone`() {
        val renderer = counting()
        val sps = H264Sps()
        renderer.submit(parameterSet(MoonBridge.BUFFER_TYPE_SPS, hex(sps.nal()), 1))
        renderer.submit(h264Pps(1))

        // Only a new IDR frame forgets them: this P frame is not one, so the first picture data
        // of the IDR frame under its number still sends them as config.
        renderer.submit(pFrame(2))
        renderer.submit(idrSlice(2))

        val patched =
            sps.copy(
                numRefFrames = 1,
                vui =
                    sps.vui?.copy(restriction = H264Sps.restrictionAdded(maxDecFrameBuffering = 1)),
            )
        val pps = bytes("00000001 68ce3c80")
        val config = queued().single { it.flags == CODEC_CONFIG }
        assertThat(config.hex).isEqualTo(hex(patched.nal() + pps))
    }

    @Test
    fun `the host latency of a window ignores the frames that report none`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(latency = 40)))
        renderer.feed(pFrame(2, times(latency = 0)))
        renderer.feed(pFrame(3, times(latency = 25)))
        advanceClock(1_000)

        renderer.feed(pFrame(4))

        assertThat(snapshots.single().hostLatency)
            .isEqualTo(LatencyRange(min = 2.5f, avg = 3.25f, max = 4f))
    }

    @Test
    fun `a window without host latency reports none`() {
        val renderer = counting()
        renderer.feed(pFrame(1))
        advanceClock(1_000)

        renderer.feed(pFrame(2))

        assertThat(snapshots.single().hostLatency).isNull()
    }

    @Test
    fun `the largest host latency is kept whole`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(latency = 0xFFFF)))
        renderer.feed(pFrame(2, times(latency = 1)))
        advanceClock(1_000)

        renderer.feed(pFrame(3))

        val range = checkNotNull(snapshots.single().hostLatency)
        assertThat(range.max).isWithin(EPSILON).of(6_553.5f)
        assertThat(range.min).isWithin(EPSILON).of(0.1f)
    }

    @Test
    fun `a snapshot covers the closed window and the one before it`() {
        val renderer = counting()
        renderer.feed(pFrame(1))
        renderer.feed(pFrame(2))
        advanceClock(1_000)
        renderer.feed(pFrame(3))
        renderer.feed(pFrame(4))
        renderer.feed(pFrame(5))
        advanceClock(1_000)

        renderer.feed(pFrame(6))

        // The first snapshot counted 2 frames over 1 s; the second 2 + 3 frames over 2 s.
        assertThat(snapshots.map { it.receivedFps }).containsExactly(2f, 2.5f).inOrder()
    }

    @Test
    fun `the lowest host latency is kept when the newer window has none`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(latency = 30)))
        renderer.feed(pFrame(2, times(latency = 10)))
        advanceClock(1_000)
        renderer.feed(pFrame(3))
        advanceClock(1_000)

        renderer.feed(pFrame(4))

        // The windows hold 30 and 10, then none: the merged lowest stays 1.0, where the first
        // alone says it. The average and the largest come from the first window only.
        assertThat(snapshots[0].hostLatency).isEqualTo(LatencyRange(min = 1f, avg = 2f, max = 3f))
        assertThat(snapshots[1].hostLatency).isEqualTo(LatencyRange(min = 1f, avg = 2f, max = 3f))
    }

    @Test
    fun `the lowest host latency is right when only the newer window has one`() {
        val renderer = counting()
        renderer.feed(pFrame(1))
        advanceClock(1_000)
        renderer.feed(pFrame(2, times(latency = 30)))
        advanceClock(1_000)

        renderer.feed(pFrame(3))

        assertThat(snapshots[1].hostLatency).isEqualTo(LatencyRange(min = 3f, avg = 3f, max = 3f))
    }

    @Test
    fun `the lowest host latency of two windows is the lowest of the two`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(latency = 30)))
        advanceClock(1_000)
        renderer.feed(pFrame(2, times(latency = 20)))
        advanceClock(1_000)

        renderer.feed(pFrame(3))

        assertThat(snapshots[1].hostLatency).isEqualTo(LatencyRange(min = 2f, avg = 2.5f, max = 3f))
    }

    // What the end of the stream reads

    @Test
    fun `the average latencies are zero before any window has closed`() {
        val renderer = counting()

        renderer.feed(pFrame(1, times(receive = 1_000, enqueue = 9_000)))

        assertThat(renderer.getAverageEndToEndLatency()).isEqualTo(0)
        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(0)
    }

    @Test
    fun `the average end to end latency is the time from receive to enqueue per frame`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(receive = 1_000, enqueue = 3_000)))
        renderer.feed(pFrame(2, times(receive = 2_000, enqueue = 5_500)))
        renderer.feed(pFrame(3, times(receive = 4_000, enqueue = 9_999)))
        advanceClock(1_000)
        renderer.feed(pFrame(4, times(receive = 0, enqueue = 90_000)))

        // 2 ms, 3 ms and 5 ms (5.999 truncated): 10 ms over 3 frames is 3, truncated.
        assertThat(renderer.getAverageEndToEndLatency()).isEqualTo(3)
    }

    @Test
    fun `a negative time from receive to enqueue is truncated toward zero`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(receive = 2_500, enqueue = 1_000)))
        advanceClock(1_000)
        renderer.feed(pFrame(2))

        assertThat(renderer.getAverageEndToEndLatency()).isEqualTo(-1)
    }

    @Test
    fun `the decoder latency stays zero while no frame has been decoded`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(receive = 1_000, enqueue = 3_000)))
        advanceClock(1_000)
        renderer.feed(pFrame(2))

        assertThat(renderer.getAverageDecoderLatency()).isEqualTo(0)
    }

    @Test
    fun `the averages cover every window that has closed`() {
        val renderer = counting()
        renderer.feed(pFrame(1, times(receive = 0, enqueue = 4_000)))
        advanceClock(1_000)
        renderer.feed(pFrame(2, times(receive = 0, enqueue = 8_000)))
        advanceClock(1_000)
        renderer.feed(pFrame(3))

        // 4 ms over frame 1, then 8 ms over frame 2: 12 ms over 2 frames.
        assertThat(renderer.getAverageEndToEndLatency()).isEqualTo(6)
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
