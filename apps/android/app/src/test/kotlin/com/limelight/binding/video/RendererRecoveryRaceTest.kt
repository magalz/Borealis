package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.jni.MoonBridge
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
}
