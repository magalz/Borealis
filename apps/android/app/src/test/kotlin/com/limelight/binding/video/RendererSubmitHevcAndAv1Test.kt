package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Test

/**
 * Characterizes the bytes that `submitDecodeUnit` queues for HEVC and AV1. HEVC parameter sets go
 * to the codec as they came, in the order VPS, SPS, PPS; the AV1 stream has none and its frames go
 * through whole.
 */
class RendererSubmitHevcAndAv1Test : RendererTestBase() {
    private val vps = "00000001 4001 0c01ffff 0160"
    private val sps = "00000001 4201 0101 6000 0003 0090"
    private val pps = "00000001 4401 c172"
    private val slice = "00000001 2601 af08 4f9d"

    private fun hevc(change: CodecBuilder.() -> Unit = {}): MediaCodecDecoderRenderer {
        install(
            codec("vendor.test.avc.decoder") { mime = "video/avc" },
            codec("c2.test.hevc.decoder") {
                mime = "video/hevc"
                change()
            },
        )
        return renderer().also { assertThat(it.prepare(MoonBridge.VIDEO_FORMAT_H265)).isEqualTo(0) }
    }

    private fun av1(): MediaCodecDecoderRenderer {
        install(
            codec("vendor.test.avc.decoder") { mime = "video/avc" },
            codec("vendor.test.av1.decoder") { mime = "video/av01" },
        )
        return renderer(prefs { videoFormat = FormatOption.FORCE_AV1 }).also {
            assertThat(it.prepare(MoonBridge.VIDEO_FORMAT_AV1_MAIN8)).isEqualTo(0)
        }
    }

    private fun MediaCodecDecoderRenderer.sendIdr(number: Int = 1, order: List<Int> = DEFAULT_ORDER) {
        val data =
            mapOf(
                MoonBridge.BUFFER_TYPE_VPS to vps,
                MoonBridge.BUFFER_TYPE_SPS to sps,
                MoonBridge.BUFFER_TYPE_PPS to pps,
            )
        for (type in order) {
            assertThat(submit(parameterSet(type, data.getValue(type), number))).isEqualTo(MoonBridge.DR_OK)
        }
        val idr =
            DecodeUnit(
                bytes(slice),
                frameNumber = number,
                frameType = MoonBridge.FRAME_TYPE_IDR,
            )
        assertThat(submit(idr)).isEqualTo(MoonBridge.DR_OK)
    }

    private fun queued() = events<CodecEvent.Queued>()

    // HEVC

    @Test
    fun `the parameter sets of an HEVC IDR frame go to the codec together as config`() {
        val renderer = hevc()

        renderer.sendIdr()

        val (config, frame) = queued()
        assertThat(config.hex).isEqualTo(hex(bytes(vps + sps + pps)))
        assertThat(config.flags).isEqualTo(CODEC_CONFIG)
        assertThat(config.ptsUs).isEqualTo(0)
        assertThat(frame.hex).isEqualTo(hex(bytes(slice)))
        assertThat(frame.flags).isEqualTo(SYNC_FRAME)
    }

    @Test
    fun `the parameter sets are sent in the order VPS, SPS, PPS whatever the order they came in`() {
        val renderer = hevc()

        renderer.sendIdr(
            order = listOf(MoonBridge.BUFFER_TYPE_PPS, MoonBridge.BUFFER_TYPE_SPS, MoonBridge.BUFFER_TYPE_VPS)
        )

        assertThat(queued().first().hex).isEqualTo(hex(bytes(vps + sps + pps)))
    }

    @Test
    fun `HEVC parameter sets are queued as they came without being rewritten`() {
        val renderer = hevc()

        renderer.sendIdr()

        assertThat(queued().first().hex).contains(hex(bytes(sps)))
        assertThat(log.lines()).doesNotContain("INFO: Patching num_ref_frames in SPS")
    }

    @Test
    fun `an HEVC decoder that is not fused gets the parameter sets again with every IDR frame`() {
        val renderer = hevc()

        renderer.sendIdr(number = 1)
        renderer.sendIdr(number = 2)

        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME, CODEC_CONFIG, SYNC_FRAME)
            .inOrder()
    }

    @Test
    fun `a fused HEVC decoder gets the parameter sets in front of the later IDR frames`() {
        val renderer = hevc { adaptivePlayback = true }

        renderer.sendIdr(number = 1)
        renderer.sendIdr(number = 2)

        assertThat(queued().map { it.flags })
            .containsExactly(CODEC_CONFIG, SYNC_FRAME, SYNC_FRAME)
            .inOrder()
        assertThat(queued()[2].hex).isEqualTo(hex(bytes(vps + sps + pps + slice)))
    }

    @Test
    fun `an HEVC predicted frame is queued as it came`() {
        val renderer = hevc()

        val result = renderer.submit(DecodeUnit(bytes("00000001 0201 d008"), frameNumber = 1))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(queued().single().hex).isEqualTo(hex(bytes("00000001 0201 d008")))
        assertThat(queued().single().flags).isEqualTo(0)
    }

    @Test
    fun `the HEVC SPS reaches the diagnostics whole, with its start code`() {
        val sink = RecordingSink()
        BorealisLog.initialize(listOf(sink), LogLevel.DEBUG)
        try {
            val renderer = hevc()

            renderer.sendIdr()

            assertThat(sink.messages)
                .contains("first HEVC SPS hex=00 00 00 01 42 01 01 01 60 00 00 03 00 90")
        } finally {
            BorealisLog.shutdown()
        }
    }

    // AV1

    @Test
    fun `an AV1 IDR frame goes to the codec whole with the sync flag and no config buffer`() {
        val renderer = av1()
        val data = bytes("12 00 0a 0b 00 00 00 24 c4 1f ff")

        val result =
            renderer.submit(
                DecodeUnit(data, frameNumber = 1, frameType = MoonBridge.FRAME_TYPE_IDR)
            )

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        val frame = queued().single()
        assertThat(frame.hex).isEqualTo(hex(data))
        assertThat(frame.flags).isEqualTo(SYNC_FRAME)
    }

    @Test
    fun `an AV1 predicted frame goes to the codec whole with no flags`() {
        val renderer = av1()
        val data = bytes("32 0a 0f 00 00 01 11")

        renderer.submit(DecodeUnit(data, frameNumber = 1))

        assertThat(queued().single().hex).isEqualTo(hex(data))
        assertThat(queued().single().flags).isEqualTo(0)
    }

    private class RecordingSink : LogSink {
        private val records = CopyOnWriteArrayList<LogRecord>()

        val messages: List<String>
            get() = records.map { it.message }

        override fun write(record: LogRecord) {
            records += record
        }
    }

    private companion object {
        val DEFAULT_ORDER =
            listOf(MoonBridge.BUFFER_TYPE_VPS, MoonBridge.BUFFER_TYPE_SPS, MoonBridge.BUFFER_TYPE_PPS)
    }
}
