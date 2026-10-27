package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

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

    /** What the codec must get for [metadata]: a zero metadata type, then the same twelve shorts. */
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
        await("the renderer thread to wait for the others") {
            log.lines().any { it.startsWith("INFO: Waiting to quiesce decoder threads") }
        }

        renderer.setHdrMode(true, metadata())
        renderer.submit(pFrame(1))

        assertThat(events<CodecEvent.Reset>()).hasSize(1)
        assertThat(events<CodecEvent.Stopped>()).isEmpty()
        assertThat(configuredFormats.last()[hdrKey]).isEqualTo(staticInfo(metadata()))
    }
}
