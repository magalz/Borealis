package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * Characterizes `setHdrMode`: the HDR static info that goes into the format of the codec, the
 * changes that make the renderer restart the codec to pick it up, and how the restart treats the
 * frames that are being sent.
 */
class RendererHdrTest : RendererTestBase() {
    private val hdrKey = "hdr-static-info"

    /** The 24 bytes the host sends: twelve little endian shorts, 1001, 2002, and so on. */
    private fun metadata(offset: Int = 0): ByteArray =
        ByteBuffer.allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { for (i in 1..12) putShort((i * 1000 + i + offset).toShort()) }
            .array()

    /**
     * What the codec must get for [metadata]: a zero metadata type, then the same twelve shorts.
     */
    private fun staticInfo(metadata: ByteArray) = "bytes:00" + hex(metadata)

    private fun ready(): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        return renderer().also { assertThat(it.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0) }
    }

    private fun MediaCodecDecoderRenderer.sendIdr(number: Int = 1): Int {
        submit(parameterSet(MoonBridge.BUFFER_TYPE_PPS, "00000001 68ce3c80", number))
        return submit(idrSlice(number))
    }

    // The releases of frames belong to the renderer thread, which a test does not time.
    private fun names() = journal.filterNot { it is CodecEvent.Output }.map { it::class.simpleName }

    // The format

    @Test
    fun `HDR metadata set before setup goes into the format as a static info of 25 bytes`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.setHdrMode(true, metadata())

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(configuredFormats.single()[hdrKey]).isEqualTo(staticInfo(metadata()))
    }

    @Test
    fun `HDR metadata set before setup restarts nothing`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        renderer.setHdrMode(true, metadata())
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        renderer.begin()

        // The codec was made with the static info, so the first frame meets no restart.
        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
    }

    @Test
    fun `new metadata after the renderer was told to stop restarts nothing`() {
        val renderer = ready()
        renderer.prepareForStop()

        renderer.setHdrMode(true, metadata())

        // A restart marked now would never be done: the threads that meet it would wait for it.
        val recovery = ReflectionHelpers.getField<AtomicInteger>(renderer, "codecRecoveryType")
        assertThat(recovery.get()).isEqualTo(0)
    }

    @Test
    fun `without HDR metadata the format has no static info`() {
        val renderer = ready()

        assertThat(configuredFormats.single().keys).doesNotContain(hdrKey)
        renderer.setHdrMode(false, null)
    }

    // Changes that restart nothing

    @Test
    fun `turning HDR off when it was never on restarts nothing`() {
        val renderer = ready()
        renderer.begin()

        renderer.setHdrMode(false, null)
        renderer.setHdrMode(false, metadata())

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
    }

    @Test
    fun `turning HDR on without metadata restarts nothing`() {
        val renderer = ready()
        renderer.begin()

        renderer.setHdrMode(true, null)

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
    }

    @Test
    fun `the same metadata again restarts nothing`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())
        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_NEED_IDR)
        val restarts = events<CodecEvent.Stopped>().size

        renderer.setHdrMode(true, metadata())

        assertThat(renderer.submit(pFrame(2))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Stopped>()).hasSize(restarts)
    }

    // Changes that restart the codec

    @Test
    fun `new metadata restarts the codec at the next frame with the static info in its format`() {
        val renderer = ready()
        renderer.begin()
        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)

        renderer.setHdrMode(true, metadata())
        val result = renderer.submit(pFrame(2))

        // A restart wants an IDR frame, so the call that met it asks for one.
        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(names().drop(names().indexOf("Stopped")))
            .containsExactly("Stopped", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(configuredFormats.last()[hdrKey]).isEqualTo(staticInfo(metadata()))
        assertThat(events<CodecEvent.Created>()).hasSize(1)
    }

    @Test
    fun `the frame that meets the restart is queued to the codec just before it is restarted`() {
        val renderer = ready()
        renderer.begin()
        renderer.submit(pFrame(1))

        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(2))

        // The buffer for the frame was fetched earlier, so the restart is only met after the queue.
        assertThat(names().takeLast(5))
            .containsExactly("Queued", "Stopped", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(events<CodecEvent.Queued>()).hasSize(2)
    }

    @Test
    fun `the codec is restarted again for different metadata`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(1))

        renderer.setHdrMode(true, metadata(offset = 7))
        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Stopped>()).hasSize(2)
        assertThat(configuredFormats.last()[hdrKey]).isEqualTo(staticInfo(metadata(offset = 7)))
    }

    @Test
    fun `turning HDR off restarts the codec without the static info`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(1))

        renderer.setHdrMode(false, metadata())
        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Stopped>()).hasSize(2)
        assertThat(configuredFormats.last().keys).doesNotContain(hdrKey)
    }

    @Test
    fun `HDR on with no metadata after it was on also restarts without the static info`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(1))

        renderer.setHdrMode(true, null)
        renderer.submit(pFrame(2))

        assertThat(events<CodecEvent.Stopped>()).hasSize(2)
        assertThat(configuredFormats.last().keys).doesNotContain(hdrKey)
    }

    @Test
    fun `the IDR frame that meets the restart is kept and queued after it`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())

        val result = renderer.sendIdr()

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(names().drop(names().indexOf("Stopped")))
            .containsExactly("Stopped", "Configured", "Scaling", "Started", "Queued", "Queued")
            .inOrder()
        val (config, frame) = events<CodecEvent.Queued>()
        assertThat(config.flags).isEqualTo(CODEC_CONFIG)
        assertThat(config.hex).isEqualTo(hex(bytes("00000001 68ce3c80")))
        assertThat(frame.flags).isEqualTo(SYNC_FRAME)
        assertThat(frame.hex).isEqualTo(hex(bytes("00000001 6588 8400 33ff")))
    }

    @Test
    fun `an IDR frame with no buffer after the restart asks for another IDR frame`() {
        val renderer = ready()
        renderer.begin()
        renderer.setHdrMode(true, metadata())
        // Both fetches get no buffer: the first one also meets the restart, and the second comes
        // after the parameter sets are restored.
        ShadowRecordingVideoCodec.nullInputBuffers.set(2)

        val result = renderer.sendIdr()

        assertThat(result).isEqualTo(MoonBridge.DR_NEED_IDR)
        assertThat(events<CodecEvent.Stopped>()).hasSize(1)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
        // The next IDR frame has its own parameter sets and goes through.
        assertThat(renderer.sendIdr(number = 2)).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>().map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
    }

    @Test
    fun `a restart that the IDR frame met after its fetch is still there for the next failed fetch`() {
        val renderer = ready()
        renderer.begin()
        // The buffer for this frame is held already, so the IDR frame meets the restart when it is
        // queued, not when it is fetched.
        renderer.submit(pFrame(1))
        renderer.setHdrMode(true, metadata())
        assertThat(renderer.sendIdr(number = 2)).isEqualTo(MoonBridge.DR_NEED_IDR)
        ShadowRecordingVideoCodec.nullInputBuffers.set(1)

        val result = renderer.sendIdr(number = 3)

        // The request is not used up yet, so the failed fetch is retried with the parameter sets.
        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>().takeLast(2).map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
    }

    @Test
    fun `the restart for HDR does not count against the recovery attempts`() {
        val renderer = ready()
        renderer.begin()
        ShadowRecordingVideoCodec.failNext(CodecPoint.QUEUE_INPUT) { IllegalStateException("boom") }
        renderer.submit(pFrame(1))

        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(2))

        // Both recoveries are the first attempt: the HDR restart cleared the count.
        assertThat(log.lines().filter { it.startsWith("INFO: Codec recovery attempt") })
            .containsExactly("INFO: Codec recovery attempt: 1", "INFO: Codec recovery attempt: 1")
            .inOrder()
    }

    @Test
    fun `a reset that is waiting is not turned into a restart by new metadata`() {
        val renderer = ready()
        // The renderer thread meets the failure first and waits for the input thread.
        ShadowRecordingVideoCodec.failNext(CodecPoint.DEQUEUE_OUTPUT) {
            IllegalStateException("boom")
        }
        renderer.begin()
        awaitQuiesced(RENDERER_THREAD)

        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(1))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(configuredFormats.last()[hdrKey]).isEqualTo(staticInfo(metadata()))
    }
}
