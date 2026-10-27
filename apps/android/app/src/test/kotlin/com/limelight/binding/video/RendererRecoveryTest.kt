package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Characterizes how the renderer recovers when the codec fails: which failures it lets go, which
 * make it restart, reset or recreate the codec, how the three threads that touch the codec wait for
 * each other before that happens, and what happens when the recovery itself fails.
 *
 * A recovery waits until the input thread (the caller of `submitDecodeUnit`), the renderer thread
 * and, with balanced frame pacing, the choreographer have all stopped using the codec, and the last
 * to arrive does the work. A failure that the input thread meets is therefore recovered by the
 * renderer thread, which comes by every 50 ms. A test that needs the recovery to run on its own
 * thread, to see what a failing recovery throws, makes the renderer thread meet the failure first.
 */
class RendererRecoveryTest : RendererTestBase() {
    private fun running(
        pacing: Int = PreferenceConfiguration.FRAME_PACING_MIN_LATENCY
    ): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer(prefs { framePacing = pacing })
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.begin()
        return renderer
    }

    private fun waiting() = log.lines().count { it.startsWith("INFO: Waiting to quiesce") }

    /** Waits until [count] threads wait for the others, so that the next arrival is the last. */
    private fun awaitWaiting(count: Int = 1) {
        await("$count threads waiting to quiesce") { waiting() >= count }
    }

    /** What the renderer thread meets first: the failure [failure], and then it waits. */
    private fun MediaCodecDecoderRenderer.metByRendererThread(failure: () -> Throwable) {
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_OUTPUT, 1, failure)
        awaitWaiting()
    }

    private fun names() =
        journal.filterNot { it is CodecEvent.Output }.map { it::class.simpleName }

    private val stateFailure = { IllegalStateException("codec state") }
    private val recoverable = { codecException(errorCode = -1, actionCode = ACTION_RECOVERABLE) }
    private val unrecoverable = { codecException(errorCode = -2, actionCode = ACTION_NONE) }
    private val transient = { codecException(errorCode = -3, actionCode = ACTION_TRANSIENT) }

    // Failures that are let go

    @Test
    fun `a transient codec error on queue costs the frame but not the codec`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        val failure = transient()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) { failure }

        val result = renderer.submit(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(log.lines()).contains("WARNING: ${failure.diagnosticInfo}")
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(events<CodecEvent.Reset>()).isEmpty()
        assertThat(waiting()).isEqualTo(0)
    }

    @Test
    fun `after a transient codec error the same input buffer is used for the next frame`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, transient)
        renderer.submit(pFrame(2))

        val result = renderer.submit(pFrame(3))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        // The frame that failed would have used buffer 1; the next frame uses it, not a new one.
        assertThat(events<CodecEvent.Queued>().map { it.index }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `a transient codec error on dequeue of an input buffer costs the frame only`() {
        val renderer = running()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT, 1, transient)

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_NEED_IDR)

        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(events<CodecEvent.Reset>()).isEmpty()
    }

    @Test
    fun `a transient codec error in the renderer thread is logged and the thread goes on`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        renderer.submit(pFrame(1))
        val failure = transient()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_OUTPUT) { failure }

        renderer.begin()
        await("the frame to be shown") { events<CodecEvent.Output>().isNotEmpty() }

        assertThat(log.lines()).contains("WARNING: ${failure.diagnosticInfo}")
        assertThat(waiting()).isEqualTo(0)
    }

    // Failures that are recovered

    @Test
    fun `a recoverable codec error restarts the codec and asks for an IDR frame`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        val failure = recoverable()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) { failure }

        val result = renderer.submit(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        val first = events<CodecEvent.Created>().single().codec
        assertThat(names().takeLast(4))
            .containsExactly("Stopped", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(events<CodecEvent.Stopped>().single().codec).isEqualTo(first)
        assertThat(events<CodecEvent.Started>().map { it.codec }).containsExactly(first, first)
        assertThat(configuredFormats.last()).isEqualTo(configuredFormats.first())
        assertThat(log.lines())
            .containsAtLeast(
                "SEVERE: ${failure.diagnosticInfo}",
                "INFO: Decoder requires restart for recoverable CodecException",
                "INFO: Codec recovery attempt: 1",
                "WARNING: Trying to restart decoder after CodecException",
            )
    }

    @Test
    fun `a codec error that cannot be recovered resets the codec`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, unrecoverable)

        val result = renderer.submit(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().takeLast(4))
            .containsExactly("Reset", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Decoder requires reset for non-recoverable CodecException",
                "INFO: Codec recovery attempt: 1",
                "WARNING: Trying to reset decoder after CodecException",
            )
    }

    @Test
    fun `an illegal state of the codec resets it`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)

        val result = renderer.submit(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().takeLast(4))
            .containsExactly("Reset", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(log.lines()).contains("INFO: Decoder requires reset for IllegalStateException")
    }

    @Test
    fun `the stream goes on after a recovery, with buffers of the restarted codec`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, recoverable)
        renderer.submit(pFrame(2))

        val result = renderer.submit(pFrame(3))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        // The failed frame was never queued. The buffers of the restarted codec begin again at 0.
        assertThat(events<CodecEvent.Queued>().map { it.index }).containsExactly(0, 0).inOrder()
    }

    @Test
    fun `a failing input buffer dequeue is recovered like a failing queue`() {
        val renderer = running()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT, 1, recoverable)

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().takeLast(4))
            .containsExactly("Stopped", "Configured", "Scaling", "Started")
            .inOrder()
    }

    @Test
    fun `a failure of the renderer thread is recovered when the input thread arrives`() {
        val renderer = running()
        renderer.metByRendererThread(recoverable)

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().takeLast(4))
            .containsExactly("Stopped", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
    }

    @Test
    fun `a failure to release a frame in the renderer thread is recovered`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        renderer.submit(pFrame(1))
        ShadowRecordingVideoCodec.failNext(CodecPoint.RELEASE_OUTPUT, 1, stateFailure)
        renderer.begin()
        awaitWaiting()

        val result = renderer.submit(pFrame(2))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Reset>()).hasSize(1)
    }

    // Failures that are let go for other reasons

    @Test
    fun `a failure met while the renderer is stopping is let go`() {
        val renderer = running()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT) {
            renderer.prepareForStop()
            IllegalStateException("too late to matter")
        }

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(waiting()).isEqualTo(0)
        assertThat(events<CodecEvent.Reset>()).isEmpty()
        assertThat(crashes).isEmpty()
    }

    @Test
    fun `an input buffer that the codec answers null for is dropped and the next one is used`() {
        val renderer = running()
        ShadowRecordingVideoCodec.nullInputBuffers.set(1)

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)

        // Buffer 0 was the one that came back null: the frame is in the next.
        assertThat(events<CodecEvent.Queued>().map { it.index }).containsExactly(1)
    }

    // Failures in the middle of an IDR frame

    private fun MediaCodecDecoderRenderer.sendParameterSets(number: Int = 1) {
        assertThat(submit(parameterSet(MoonBridge.BUFFER_TYPE_SPS, hex(H264Sps().nal()), number)))
            .isEqualTo(MoonBridge.DR_OK)
        assertThat(submit(h264Pps(number))).isEqualTo(MoonBridge.DR_OK)
    }

    @Test
    fun `an IDR frame that cannot get an input buffer asks for another`() {
        val renderer = running()
        renderer.sendParameterSets()
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_INPUT, 1, transient)

        val result = renderer.submit(idrSlice())

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `an IDR frame whose config buffer cannot be queued asks for another`() {
        val renderer = running()
        renderer.sendParameterSets()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, transient)

        val result = renderer.submit(idrSlice())

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `an IDR frame that is not queued after its config buffer asks for another`() {
        val renderer = running()
        renderer.sendParameterSets()
        ShadowRecordingVideoCodec.failAfter(CodecPoint.QUEUE_INPUT, 1, transient)

        val result = renderer.submit(idrSlice())

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Queued>().map { it.flags }).containsExactly(CODEC_CONFIG)
    }

    @Test
    fun `a replay of the SPS that cannot be queued asks for another IDR frame`() {
        install(codec("omx.intel.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        renderer.begin()
        renderer.sendParameterSets()
        ShadowRecordingVideoCodec.failAfter(CodecPoint.QUEUE_INPUT, 1, transient)

        val result = renderer.submit(idrSlice())

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        // The baseline SPS went, the replay did not, and the frame did not follow.
        assertThat(events<CodecEvent.Queued>().map { it.flags }).containsExactly(CODEC_CONFIG)
        assertThat(log.lines()).doesNotContain("INFO: SPS replay complete")
    }

    @Test
    fun `an IDR frame that meets a recovery that is not for HDR is not kept`() {
        val renderer = running()
        renderer.metByRendererThread(recoverable)
        renderer.sendParameterSets()

        val result = renderer.submit(idrSlice())

        // The restart forgot the parameter sets, and nothing restores them: a new IDR is needed.
        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Stopped>()).hasSize(1)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    // What a failure does to a recovery that is waiting

    @Test
    fun `a non recoverable error turns a pending restart into a reset`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, unrecoverable)

        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(log.lines())
            .contains("INFO: Decoder restart promoted to reset for non-recoverable CodecException")
    }

    @Test
    fun `an illegal state turns a pending restart into a reset`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)

        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(log.lines())
            .contains("INFO: Decoder restart promoted to reset for IllegalStateException")
    }

    @Test
    fun `a recoverable error leaves a pending restart as it is`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, recoverable)

        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Stopped>()).hasSize(1)
        assertThat(events<CodecEvent.Reset>()).isEmpty()
        assertThat(log.lines().filter { it.contains("promoted") }).isEmpty()
    }

    @Test
    fun `a recoverable error does not turn a pending reset into a restart`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        renderer.metByRendererThread(stateFailure)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, recoverable)

        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
    }

    @Test
    fun `a non recoverable error leaves a pending reset as it is`() {
        val renderer = running()
        renderer.submit(pFrame(1))
        renderer.metByRendererThread(stateFailure)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, unrecoverable)

        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(log.lines().filter { it.contains("promoted") }).isEmpty()
    }

    // A recovery that fails

    @Test
    fun `a restart that the codec refuses is replaced by a reset`() {
        val renderer = running()
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 1) {
            IllegalStateException("refused")
        }

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().takeLast(6))
            .containsExactly("Stopped", "Configured", "Reset", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(events<CodecEvent.Created>()).hasSize(1)
        assertThat(log.lines().filter { it.startsWith("INFO: Codec recovery attempt") })
            .containsExactly("INFO: Codec recovery attempt: 1")
    }

    @Test
    fun `a restart and a reset that the codec refuses are followed by a new codec`() {
        val renderer = running()
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 2) {
            IllegalStateException("refused")
        }

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        val (first, second) = events<CodecEvent.Created>()
        assertThat(names().takeLast(9))
            .containsExactly(
                "Stopped",
                "Configured",
                "Reset",
                "Configured",
                "Released",
                "Created",
                "Configured",
                "Scaling",
                "Started",
            )
            .inOrder()
        assertThat(events<CodecEvent.Released>().first().codec).isEqualTo(first.codec)
        assertThat(events<CodecEvent.Started>().last().codec).isEqualTo(second.codec)
        assertThat(log.lines()).contains("WARNING: Trying to recreate decoder after CodecException")
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
    }

    @Test
    fun `a restart that fails on an illegal argument stops the renderer`() {
        val renderer = running()
        renderer.metByRendererThread(recoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 1) {
            IllegalArgumentException("surface")
        }

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Reset>()).isEmpty()
        assertThat(crashes).isEmpty()
        // The renderer is stopping: it takes frames and does nothing with them.
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
        await("the renderer thread to end") { threadNamed("Video - Renderer (MediaCodec)") == null }
    }

    @Test
    fun `a reset that fails on an illegal argument stops the renderer`() {
        val renderer = running()
        renderer.metByRendererThread(unrecoverable)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 1) {
            IllegalArgumentException("surface")
        }

        renderer.submit(pFrame(1))

        assertThat(events<CodecEvent.Released>()).isEmpty()
        assertThat(crashes).isEmpty()
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `a new codec that fails on an illegal argument stops the renderer`() {
        val renderer = running()
        renderer.metByRendererThread(stateFailure)
        // The reset is refused, then all four tries of the new codec, the last on the surface.
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 4) {
            IllegalStateException("refused")
        }
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 1) {
            IllegalArgumentException("surface")
        }

        val result = renderer.submit(pFrame(1))

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(crashes).isEmpty()
        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `a new codec that cannot be configured crashes the renderer`() {
        val renderer = running()
        renderer.metByRendererThread(stateFailure)
        // The reset is refused, then all four tries of the new codec.
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 5) {
            IllegalStateException("refused for good")
        }

        val thrown = assertThrows(RuntimeException::class.java) { renderer.submit(pFrame(1)) }

        assertThat(thrown.javaClass.simpleName).isEqualTo("RendererException")
        assertThat(thrown.toString().split("\n").first()).isEqualTo("PreSPSErrorFormat: 1")
        assertThat(thrown.toString().split("\n").last())
            .isEqualTo("java.lang.IllegalStateException: refused for good")
        assertThat(crashes).hasSize(1)
        assertThat(crashes.single()).isInstanceOf(IllegalStateException::class.java)
        assertThat(crashes.single()).hasMessageThat().isEqualTo("refused for good")
        assertThat(log.lines()).contains("WARNING: Trying to recreate decoder after CodecException")
    }

    @Test
    fun `a new codec that cannot be started crashes the renderer`() {
        val renderer = running()
        renderer.metByRendererThread(stateFailure)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 1) {
            IllegalStateException("reset refused")
        }
        ShadowRecordingVideoCodec.failNext(CodecPoint.START, 4) {
            IllegalStateException("no start")
        }

        val thrown = assertThrows(RuntimeException::class.java) { renderer.submit(pFrame(1)) }

        assertThat(thrown.javaClass.simpleName).isEqualTo("RendererException")
        assertThat(crashes).hasSize(1)
    }

    // Too many recoveries

    @Test
    fun `after ten recoveries a failure is not recovered but held back for three seconds`() {
        val renderer = running()
        for (number in 1..10) {
            ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
            assertThat(renderer.submit(pFrame(number))).isEqualTo(MoonBridge.DR_NEED_IDR)
        }
        assertThat(events<CodecEvent.Reset>()).hasSize(10)

        // The eleventh failure is not recovered, and the frame asks for an IDR frame as before.
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
        assertThat(renderer.submit(pFrame(11))).isEqualTo(MoonBridge.DR_NEED_IDR)
        advanceClock(2_999)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
        assertThat(renderer.submit(pFrame(12))).isEqualTo(MoonBridge.DR_NEED_IDR)

        assertThat(events<CodecEvent.Reset>()).hasSize(10)
        assertThat(crashes).isEmpty()
    }

    @Test
    fun `three seconds after the first failure that is not recovered the renderer crashes with it`() {
        val renderer = running()
        for (number in 1..10) {
            ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
            renderer.submit(pFrame(number))
        }
        val failure = codecException(errorCode = -38, actionCode = ACTION_NONE, message = "dead")
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) { failure }
        renderer.submit(pFrame(11))
        advanceClock(3_000)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)

        val thrown = assertThrows(RuntimeException::class.java) { renderer.submit(pFrame(12)) }

        // The exception thrown is the one made at the first failure, with that failure in it.
        assertThat(thrown.javaClass.simpleName).isEqualTo("RendererException")
        assertThat(thrown.toString().split("\n").takeLast(5))
            .containsExactly(
                "Diagnostic Info: android.media.MediaCodec.error_neg_38",
                "Recoverable: false",
                "Transient: false",
                "Codec Error Code: -38",
                "android.media.MediaCodec\$CodecException: dead",
            )
            .inOrder()
        assertThat(crashes).hasSize(1)
        assertThat(crashes.single()).isSameInstanceAs(thrown)
    }

    @Test
    fun `a renderer that crashed throws the same exception again and tells nobody twice`() {
        val renderer = running()
        for (number in 1..10) {
            ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
            renderer.submit(pFrame(number))
        }
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
        renderer.submit(pFrame(11))
        advanceClock(3_000)
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 2, stateFailure)
        val first = assertThrows(RuntimeException::class.java) { renderer.submit(pFrame(12)) }

        val second = assertThrows(RuntimeException::class.java) { renderer.submit(pFrame(13)) }

        assertThat(second).isSameInstanceAs(first)
        assertThat(crashes).hasSize(1)
    }

    @Test
    fun `the recovery attempts count the restarts and resets, not the transient errors`() {
        val renderer = running()
        for (number in 1..12) {
            ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, transient)
            assertThat(renderer.submit(pFrame(number))).isEqualTo(MoonBridge.DR_NEED_IDR)
        }

        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT, 1, stateFailure)
        assertThat(renderer.submit(pFrame(13))).isEqualTo(MoonBridge.DR_NEED_IDR)

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(log.lines()).contains("INFO: Codec recovery attempt: 1")
    }
}
