package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Test

/**
 * Characterizes what needs two threads to happen: the codec that does not give an input buffer for a
 * long time, the recovery that waits for the choreographer, and a thread that is interrupted while
 * it waits for the others. The input thread of the renderer, which calls `submitDecodeUnit`, is a
 * thread of its own in these tests, and the test thread plays the others, so that a test that
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

    /** What a call on the input thread ended with, and whether that thread was interrupted then. */
    private class Outcome<T>(val result: Result<T>, val interrupted: Boolean)

    /** A call that runs on a thread of its own, the input thread. */
    private class InputCall<T>(block: () -> T) {
        private val outcome = AtomicReference<Outcome<T>>()
        private val input =
            thread(name = "input") {
                val result = runCatching(block)
                outcome.set(Outcome(result, Thread.currentThread().isInterrupted))
            }

        fun interrupt() = input.interrupt()

        /** Waits for the call to end; fails if it does not end within half a minute. */
        fun outcome(): Outcome<T> {
            input.join(JOIN_MS)
            check(!input.isAlive) { "The input thread is still blocked" }
            return outcome.get()
        }

        fun result(): T = outcome().result.getOrThrow()

        fun failure(): Throwable = checkNotNull(outcome().result.exceptionOrNull())
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
        await("the input and renderer threads to wait") { waiting() >= 2 }
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
                await("the frame time to wait") { waiting() >= 1 }
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

    // An interrupted wait

    @Test
    fun `a thread that is interrupted while it waits for the others gives up the recovery`() {
        val renderer = running(PreferenceConfiguration.FRAME_PACING_BALANCED)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1) {
            IllegalStateException("codec state")
        }
        val call = onInputThread { renderer.submit(pFrame(1)) }
        // With balanced pacing the choreographer never comes, so both wait for it.
        await("the input and renderer threads to wait") { waiting() >= 2 }

        call.interrupt()

        val outcome = call.outcome()
        assertThat(outcome.result.getOrThrow()).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(outcome.interrupted).isTrue()
        assertThat(events<CodecEvent.Reset>()).isEmpty()
    }

    private fun waiting() = log.lines().count { it.startsWith("INFO: Waiting to quiesce") }

    private companion object {
        const val JOIN_MS = 30_000L
        const val FRAME_TIME = 1_000_000_000L
    }
}
