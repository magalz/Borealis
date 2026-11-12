package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.concurrent.thread
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * The end of one codec recovery and the start of the next (BORE-121). The thread that does a
 * recovery wakes the threads that wait for it, and a waiter must take the monitor again before it
 * looks at the recovery type. When the next recovery is asked for in that window, the waiter has to
 * count itself in again: with its mark cleared by the first recovery, the second one never sees all
 * the threads, and every thread waits for ever.
 */
class RendererRecoveryRaceTest : RendererTestBase() {
    private val stateFailure = { IllegalStateException("codec state") }

    /** A renderer whose renderer thread makes a pass only when the test steps it. */
    private fun stepped(): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.beginStepped()
        return renderer
    }

    @Test
    fun `a thread woken after a recovery that finds the next one asked for counts itself in again`() {
        val renderer = stepped()
        val monitor = ReflectionHelpers.getField<Any>(renderer, "codecRecoveryMonitor")
        // The renderer thread meets the first failure and waits for the input thread (the
        // renderer has no choreographer, so its bit comes with the first arrival).
        threadLog.forgetWaits()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_OUTPUT, 1, stateFailure)
        ShadowRecordingVideoCodec.stepOutput()
        threadLog.awaitWaiting(RENDERER_THREAD or CHOREOGRAPHER)

        // The input thread holds the monitor from the first recovery, which it does as the last
        // thread, to the wait of the second: the renderer thread, woken at the end of the first,
        // cannot take the monitor before the second is asked for by a failure of the input.
        val input = InputCall {
            synchronized(monitor) {
                val first = renderer.submit(pFrame(1))
                ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
                first to renderer.submit(pFrame(2))
            }
        }
        try {
            await("the second recovery, or a thread that waits without its own mark") {
                input.isFinished || threadLog.waitsWithoutOwnBit().isNotEmpty()
            }
        } finally {
            // Without the fix both threads wait for ever: let them go, so that none is left behind.
            renderer.prepareForStop()
        }

        assertWithMessage("threads that waited to quiesce without their own bit")
            .that(threadLog.waitsWithoutOwnBit())
            .isEmpty()
        assertThat(input.result()).isEqualTo(MoonBridge.DR_NEED_IDR to MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Reset>()).hasSize(2)
        assertThat(log.lines()).contains("INFO: Codec recovery attempt: 2")
    }

    // How the log knows which role a thread plays, which decides whether a wait is the race

    @Test
    fun `the test thread that submits is the input thread for the log`() {
        val renderer = stepped()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
        threadLog.forgetWaits()
        // The renderer thread comes once the test thread, which is the input thread here, waits.
        thread(name = "stepper", isDaemon = true) {
            threadLog.awaitWaiting(INPUT_THREAD or CHOREOGRAPHER)
            ShadowRecordingVideoCodec.stepOutput()
        }

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_NEED_IDR)

        val waits =
            threadLog.lines.filter {
                it.thread == Thread.currentThread().name &&
                    it.message.startsWith("Waiting to quiesce decoder threads: ")
            }
        assertThat(waits).isNotEmpty()
        assertThat(waits.map { it.bit }.distinct()).containsExactly(INPUT_THREAD)
    }

    private fun fetchNextInputBuffer(action: () -> Unit) = action()

    private fun doFrame(action: () -> Unit) = action()

    /** What a log that is not the rig's makes of the wait [flags] of a thread that [role] names. */
    private fun waitsOf(threadName: String, flags: Int, role: ((() -> Unit) -> Unit)? = null) =
        ThreadedLog()
            .also { fresh ->
                thread(name = threadName) {
                        val wait = {
                            fresh.publish(
                                LogRecord(Level.INFO, "Waiting to quiesce decoder threads: $flags")
                            )
                        }
                        if (role == null) wait() else role(wait)
                    }
                    .join()
            }
            .waitsWithoutOwnBit()

    @Test
    fun `a thread in the frame callback is the choreographer, whatever its name`() {
        assertThat(waitsOf("test thread", CHOREOGRAPHER, ::doFrame)).isEmpty()
        assertThat(waitsOf("test thread", INPUT_THREAD or RENDERER_THREAD, ::doFrame)).hasSize(1)
    }

    @Test
    fun `a thread in the input buffer functions is the input thread, whatever its name`() {
        assertThat(waitsOf("test thread", INPUT_THREAD, ::fetchNextInputBuffer)).isEmpty()
        assertThat(waitsOf("test thread", RENDERER_THREAD or CHOREOGRAPHER, ::fetchNextInputBuffer))
            .hasSize(1)
    }

    @Test
    fun `a thread named like the input thread is judged by the function it is in first`() {
        assertThat(waitsOf(INPUT_CALL_THREAD, CHOREOGRAPHER, ::doFrame)).isEmpty()
        assertThat(waitsOf(INPUT_CALL_THREAD, INPUT_THREAD, ::doFrame)).hasSize(1)
    }

    @Test
    fun `the choreographer thread is known by its name`() {
        assertThat(waitsOf(CHOREOGRAPHER_THREAD_NAME, CHOREOGRAPHER)).isEmpty()
        assertThat(waitsOf(CHOREOGRAPHER_THREAD_NAME, INPUT_THREAD or RENDERER_THREAD)).hasSize(1)
    }

    @Test
    fun `a thread that is not named and in none of those functions is never judged`() {
        assertThat(waitsOf("somebody", INPUT_THREAD)).isEmpty()
    }
}
