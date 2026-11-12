package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * Characterizes what needs two threads to happen: the codec that does not give an input buffer for
 * a long time, the recovery that waits for the choreographer, and a thread that is interrupted
 * while it waits for the others. The input thread of the renderer, which calls `submitDecodeUnit`,
 * is a thread of its own in these tests, and the test thread plays the others, so that a test that
 * goes wrong ends by itself: its input thread is released when the renderer is told to stop.
 */
class RendererThreadsTest : RendererTestBase() {
    private fun running(
        pacing: Int = PreferenceConfiguration.FRAME_PACING_MIN_LATENCY
    ): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer(prefs { framePacing = pacing })
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.begin()
        return renderer
    }

    private fun onInputThread(block: () -> Int) = InputCall(block)

    private fun awaitStarved() {
        await("the input thread to wait for an input buffer") {
            ShadowRecordingVideoCodec.dequeueInputCalls.get() >= 1
        }
    }

    // The decoder that gives no input buffer

    @Test
    fun `an input buffer that takes five seconds is a hung decoder that crashes the renderer`() {
        val renderer = running()
        ShadowRecordingVideoCodec.starveInput = true
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitStarved()

        advanceClock(5_000)
        renderer.prepareForStop()

        val thrown = call.failure()
        assertThat(thrown.javaClass.simpleName).isEqualTo("RendererException")
        val lines = thrown.toString().split("\n")
        assertThat(lines.first()).isEqualTo("PreSPSErrorFormat: 1")
        assertThat(lines.takeLast(2))
            .containsExactly(
                "Hang time: 5000 ms",
                "com.limelight.binding.video.MediaCodecDecoderRenderer\$DecoderHungException",
            )
            .inOrder()
        assertThat(crashes).hasSize(1)
        assertThat(crashes.single().javaClass.simpleName).isEqualTo("DecoderHungException")
        assertThat(crashes.single().toString().split("\n").first()).isEqualTo("Hang time: 5000 ms")
        assertThat(log.lines()).contains("WARNING: Dequeue input buffer ran long: 5000 ms")
    }

    @Test
    fun `an input buffer that takes a little less than five seconds is only a lost frame`() {
        val renderer = running()
        ShadowRecordingVideoCodec.starveInput = true
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitStarved()

        advanceClock(4_999)
        renderer.prepareForStop()

        assertThat(call.result()).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(crashes).isEmpty()
        assertThat(log.lines()).contains("WARNING: Dequeue input buffer ran long: 4999 ms")
    }

    @Test
    fun `a hung decoder at the IDR frame is a pre IFrame error`() {
        val renderer = running()
        renderer.submit(h264Pps())
        ShadowRecordingVideoCodec.starveInput = true
        val call = onInputThread { renderer.submit(idrSlice()) }
        awaitStarved()

        advanceClock(5_000)
        renderer.prepareForStop()

        assertThat(call.failure().toString().split("\n").first())
            .isEqualTo("PreIFrameErrorFormat: 1")
    }

    @Test
    fun `an input buffer that comes after 20 milliseconds is reported as slow`() {
        val renderer = running()
        ShadowRecordingVideoCodec.starveInput = true
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitStarved()

        advanceClock(20)
        ShadowRecordingVideoCodec.starveInput = false

        assertThat(call.result()).isEqualTo(MoonBridge.DR_OK)
        assertThat(log.lines()).contains("WARNING: Dequeue input buffer ran long: 20 ms")
    }

    @Test
    fun `an input buffer that comes after 19 milliseconds is not reported`() {
        val renderer = running()
        ShadowRecordingVideoCodec.starveInput = true
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitStarved()

        advanceClock(19)
        ShadowRecordingVideoCodec.starveInput = false

        assertThat(call.result()).isEqualTo(MoonBridge.DR_OK)
        assertThat(log.lines().filter { it.contains("ran long") }).isEmpty()
    }

    // Recovery that needs the choreographer

    @Test
    fun `with balanced pacing a recovery also waits for the choreographer`() {
        val renderer = running(PreferenceConfiguration.FRAME_PACING_BALANCED)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1) {
            IllegalStateException("codec state")
        }
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitQuiesced(INPUT_THREAD or RENDERER_THREAD)
        assertThat(events<CodecEvent.Reset>()).isEmpty()

        // The choreographer comes with its frame time, and is the last: it does the recovery.
        renderer.doFrame(FRAME_TIME)

        assertThat(call.result()).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        // Both threads had come, and the choreographer (4) had not.
        assertThat(log.lines()).contains("INFO: Waiting to quiesce decoder threads: 3")
    }

    @Test
    fun `a frame whose release fails twice is recovered with the other threads`() {
        val renderer = running(PreferenceConfiguration.FRAME_PACING_BALANCED)
        renderer.submit(pFrame(1))
        // The renderer thread asks for more only after it has put the frame in the queue.
        val polls = ShadowRecordingVideoCodec.dequeueOutputCalls.get()
        await("the frame to wait for its frame time") {
            ShadowRecordingVideoCodec.dequeueOutputCalls.get() > polls
        }
        ShadowRecordingVideoCodec.failNext(CodecPoint.RELEASE_OUTPUT, 2) {
            IllegalStateException("surface gone")
        }
        // The input thread comes once the frame time, which runs on the test thread, waits.
        val input = onInputThread {
            try {
                awaitQuiesced(CHOREOGRAPHER)
                renderer.submit(pFrame(2))
            } catch (e: Throwable) {
                // Let the frame time go, or the test thread would wait for ever.
                renderer.prepareForStop()
                throw e
            }
        }

        renderer.doFrame(FRAME_TIME)

        input.result()
        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(log.lines()).contains("INFO: Decoder requires reset for IllegalStateException")
    }

    // A recovery that fails

    @Test
    fun `a recovery that throws lets go the threads that wait for it and stops the renderer`() {
        val renderer = running(PreferenceConfiguration.FRAME_PACING_BALANCED)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1) {
            IllegalStateException("codec state")
        }
        val call = onInputThread { renderer.submit(pFrame(1)) }
        awaitQuiesced(INPUT_THREAD or RENDERER_THREAD)
        // The choreographer is the last and does the recovery: the reset is refused, then all four
        // tries of a new codec.
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 5) {
            IllegalStateException("refused for good")
        }

        val thrown = assertThrows(RuntimeException::class.java) { renderer.doFrame(FRAME_TIME) }

        assertThat(thrown.javaClass.simpleName).isEqualTo("RendererException")
        assertThat(crashes).hasSize(1)
        // The input thread waited for a recovery that was never going to end.
        assertThat(call.result()).isEqualTo(MoonBridge.DR_NEED_IDR)
        // No codec is left: the renderer stops, and the frames that still come are dropped.
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    // An interrupted wait

    @Test
    fun `a thread that is interrupted while it waits for the others gives up the recovery`() {
        val renderer = running(PreferenceConfiguration.FRAME_PACING_BALANCED)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1) {
            IllegalStateException("codec state")
        }
        val call = onInputThread { renderer.submit(pFrame(1)) }
        // With balanced pacing the choreographer never comes, so both wait for it.
        awaitQuiesced(INPUT_THREAD or RENDERER_THREAD)

        call.interrupt()

        val outcome = call.outcome()
        assertThat(outcome.result.getOrThrow()).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(outcome.interrupted).isTrue()
        assertThat(events<CodecEvent.Reset>()).isEmpty()
    }

    // An interrupted renderer thread

    /** An output queue whose take finds its thread interrupted, as it does after a stop. */
    private class QueueThatTakesInterrupted : LinkedBlockingQueue<Int>() {
        override fun take(): Int {
            Thread.currentThread().interrupt()
            return super.take()
        }
    }

    @Test
    fun `a renderer thread interrupted while it drops the oldest frame ends after the recovery it owes`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer =
            renderer(prefs { framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED })
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        ReflectionHelpers.setField(renderer, "outputBufferQueue", QueueThatTakesInterrupted())
        for (number in 1..3) assertThat(renderer.submit(pFrame(number))).isEqualTo(0)
        renderer.beginStepped()
        // The change of the output format, then the first two frames: the queue is full.
        repeat(3) {
            ShadowRecordingVideoCodec.stepOutput()
            ShadowRecordingVideoCodec.awaitParked()
        }
        // The other two threads wait for a recovery that only the renderer thread is missing.
        ReflectionHelpers.setField(
            renderer,
            "codecRecoveryThreadQuiescedFlags",
            INPUT_THREAD or CHOREOGRAPHER,
        )
        ReflectionHelpers.getField<AtomicInteger>(renderer, "codecRecoveryType").set(RESTART)

        // The third frame meets the full queue and the interrupt.
        ShadowRecordingVideoCodec.stepOutput()

        val thread = ReflectionHelpers.getField<Thread>(renderer, "rendererThread")
        thread.join(JOIN_MS)
        assertThat(thread.isAlive).isFalse()
        assertThat(events<CodecEvent.Output>()).isEmpty()
        assertThat(events<CodecEvent.Stopped>()).hasSize(1)
        assertThat(log.lines()).contains("INFO: Codec recovery attempt: 1")
    }

    private companion object {
        const val FRAME_TIME = 1_000_000_000L
        const val JOIN_MS = 10_000L
        const val RESTART = 2
    }
}
