package com.limelight.binding.video

import android.media.MediaCodec
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Characterizes `setup` and `initializeDecoder`: the format that the codec is configured with for
 * each video format, the low latency options that are tried until one is accepted, and what each
 * way of failing returns or throws.
 */
class RendererSetupTest : RendererTestBase() {
    private fun avc(name: String = "vendor.test.avc.decoder", change: CodecChange = {}) =
        codec(name) {
            mime = "video/avc"
            change()
        }

    private fun hevc(name: String = "c2.test.hevc.decoder") = codec(name) { mime = "video/hevc" }

    private fun av1(name: String = "vendor.test.av1.decoder") = codec(name) { mime = "video/av01" }

    /** What a decoder with no vendor options is configured with on the first try. */
    private fun firstTry(mime: String, vararg more: Pair<String, Any>): Map<String, Any> =
        (mapOf(
                "mime" to mime,
                "width" to 1920,
                "height" to 1080,
                "frame-rate" to 60,
                "color-range" to LIMITED_RANGE,
                "color-transfer" to SDR_TRANSFER,
                "color-standard" to BT709,
                "low-latency" to 1,
                "vdec-lowlatency" to 1,
                "priority" to 0,
            ) + more)
            .toSortedMap()

    // The format, per video format

    @Test
    fun `H264 is configured with the colour keys and the low latency options of the first try`() {
        install(avc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)

        assertThat(configuredFormats).containsExactly(firstTry("video/avc"))
        assertThat(events<CodecEvent.Configured>().single().surface).isSameInstanceAs(surface)
    }

    @Test
    fun `the first codec is configured, scaled to fit, then started, in that order`() {
        install(avc())
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(journal.map { it::class.simpleName })
            .containsExactly("Created", "Configured", "Scaling", "Started")
            .inOrder()
        assertThat(events<CodecEvent.Scaling>().single().mode)
            .isEqualTo(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
    }

    @Test
    fun `the frame rate of the setup goes into the format`() {
        install(avc())
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, fps = 120)

        assertThat(configuredFormats.single()["frame-rate"]).isEqualTo(120)
    }

    @Test
    fun `the size of the setup goes into the format`() {
        install(avc())
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, width = 1280, height = 720)

        assertThat(configuredFormats.single()["width"]).isEqualTo(1280)
        assertThat(configuredFormats.single()["height"]).isEqualTo(720)
    }

    @Test
    fun `the full range preference selects the full colour range`() {
        install(avc())
        val renderer = renderer(prefs { fullRange = true })

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(configuredFormats.single()["color-range"]).isEqualTo(FULL_RANGE)
    }

    @Test
    fun `HEVC is configured as a video hevc stream with the colour keys`() {
        install(avc(), hevc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H265)).isEqualTo(0)

        assertThat(configuredFormats).containsExactly(firstTry("video/hevc"))
    }

    @Test
    fun `a 10 bit HEVC stream leaves the colour standard and transfer to the decoder`() {
        install(avc(), hevc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H265_MAIN10)).isEqualTo(0)

        assertThat(configuredFormats.single().keys)
            .containsNoneOf("color-transfer", "color-standard")
        assertThat(configuredFormats.single()["color-range"]).isEqualTo(LIMITED_RANGE)
    }

    @Test
    fun `AV1 is configured as a video av01 stream with the colour keys`() {
        install(avc(), av1())
        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_AV1_MAIN8)).isEqualTo(0)

        assertThat(configuredFormats).containsExactly(firstTry("video/av01"))
    }

    @Test
    fun `a 10 bit AV1 stream leaves the colour standard and transfer to the decoder`() {
        install(avc(), av1())
        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_AV1_MAIN10)).isEqualTo(0)

        assertThat(configuredFormats.single().keys)
            .containsNoneOf("color-transfer", "color-standard")
    }

    @Test
    fun `a decoder that supports adaptive playback is told the largest size`() {
        install(avc(change = { adaptivePlayback = true }))
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, width = 1280, height = 720)

        assertThat(configuredFormats.single()).containsAtLeast("max-width", 1280, "max-height", 720)
    }

    @Test
    fun `a decoder that does not support adaptive playback is told no largest size`() {
        install(avc())
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(configuredFormats.single().keys).containsNoneOf("max-width", "max-height")
    }

    @Test
    fun `a decoder with the low latency feature is asked for low latency alone`() {
        install(avc(change = { lowLatency = true }))
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(configuredFormats.single().keys)
            .containsNoneOf("vdec-lowlatency", "priority", "operating-rate")
        assertThat(configuredFormats.single()["low-latency"]).isEqualTo(1)
    }

    @Test
    fun `a Qualcomm decoder is also given its vendor options and the operating rate`() {
        install(avc("c2.qti.avc.decoder"))
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(configuredFormats)
            .containsExactly(
                firstTry("video/avc", "vendor.qti-ext-dec-picture-order.enable" to 1)
                    .minus("priority")
                    .plus(
                        mapOf(
                            "operating-rate" to Short.MAX_VALUE.toInt(),
                            "vendor.qti-ext-dec-low-latency.enable" to 1,
                        )
                    )
                    .toSortedMap()
            )
    }

    // What setup reports and logs

    @Test
    fun `setup reports each step of its search for a configuration`() {
        install(avc())
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Decoder configuration try: 0",
                "INFO: Using codec vendor.test.avc.decoder for hardware decoding video/avc",
            )
            .inOrder()
        assertThat(log.lines().filter { it.startsWith("INFO: Configuring with format: ") })
            .hasSize(1)
        assertThat(log.lines().filter { it.startsWith("INFO: Input format: ") }).hasSize(1)
    }

    @Test
    fun `a decoder that needs the baseline hack and the constrained profile is logged`() {
        install(avc("omx.intel.avc.decoder"))
        val renderer = renderer()

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Decoder omx.intel.avc.decoder needs baseline SPS hack",
                "INFO: Decoder omx.intel.avc.decoder needs constrained high profile",
            )
    }

    // The ladder of low latency options

    @Test
    fun `a rejected format is replaced by one with fewer low latency options`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 2) {
            IllegalArgumentException("rejected")
        }

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)

        val base = firstTry("video/avc")
        assertThat(configuredFormats)
            .containsExactly(
                base,
                base.minus("low-latency").toSortedMap(),
                base.minus("low-latency").minus("vdec-lowlatency").toSortedMap(),
            )
            .inOrder()
        assertThat(log.lines().filter { it.startsWith("INFO: Decoder configuration try") })
            .containsExactly(
                "INFO: Decoder configuration try: 0",
                "INFO: Decoder configuration try: 1",
                "INFO: Decoder configuration try: 2",
            )
            .inOrder()
    }

    @Test
    fun `the last try uses no low latency option at all`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 3) {
            IllegalStateException("rejected")
        }

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)

        assertThat(configuredFormats).hasSize(4)
        assertThat(configuredFormats.last().keys)
            .containsNoneOf("low-latency", "vdec-lowlatency", "priority")
    }

    @Test
    fun `each rejected codec is released and a new codec is made for the next try`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 2) {
            IllegalArgumentException("rejected")
        }

        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(journal.map { it::class.simpleName })
            .containsExactly(
                "Created",
                "Configured",
                "Released",
                "Created",
                "Configured",
                "Released",
                "Created",
                "Configured",
                "Scaling",
                "Started",
            )
            .inOrder()
        assertThat(events<CodecEvent.Created>().map { it.codec }.distinct()).hasSize(3)
    }

    @Test
    fun `setup returns minus 5 when not even the plain format is accepted`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 4) {
            IllegalArgumentException("rejected")
        }

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(-5)

        assertThat(events<CodecEvent.Created>()).hasSize(4)
        assertThat(events<CodecEvent.Released>()).hasSize(4)
        assertThat(events<CodecEvent.Started>()).isEmpty()
    }

    @Test
    fun `a codec that cannot be created counts as a rejected format`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(
            CodecPoint.CREATE,
            4,
            ShadowRecordingVideoCodec.ioFailure(),
        )

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(-5)

        assertThat(events<CodecEvent.Created>()).isEmpty()
    }

    @Test
    fun `a codec that cannot be started counts as a rejected format`() {
        install(avc())
        val renderer = renderer()
        ShadowRecordingVideoCodec.failNext(CodecPoint.START) { IllegalStateException("no start") }

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)

        assertThat(events<CodecEvent.Configured>()).hasSize(2)
        assertThat(events<CodecEvent.Released>()).hasSize(1)
    }

    @Test
    fun `initializeDecoder can be told to throw what the last try threw`() {
        install(avc())
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 4) {
            IllegalStateException("rejected at the end")
        }

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                renderer.initializeDecoder(true)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("rejected at the end")
    }

    @Test
    fun `initializeDecoder rethrows an illegal argument as it is`() {
        install(avc())
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 4) {
            IllegalArgumentException("bad surface")
        }

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                renderer.initializeDecoder(true)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("bad surface")
    }

    @Test
    fun `initializeDecoder wraps an IO failure in a runtime exception`() {
        install(avc())
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        ShadowRecordingVideoCodec.failNext(
            CodecPoint.CREATE,
            4,
            ShadowRecordingVideoCodec.ioFailure(),
        )

        val thrown = assertThrows(RuntimeException::class.java) { renderer.initializeDecoder(true) }

        assertThat(thrown.javaClass).isEqualTo(RuntimeException::class.java)
        assertThat(thrown.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `initializeDecoder with the throwing flag hides the failures of the earlier tries`() {
        install(avc())
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        ShadowRecordingVideoCodec.failNext(CodecPoint.CONFIGURE, 3) {
            IllegalArgumentException("rejected")
        }

        assertThat(renderer.initializeDecoder(true)).isEqualTo(0)
    }

    // Setup that cannot start

    @Test
    fun `setup returns minus 1 without an H264 decoder`() {
        install(hevc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(-1)

        assertThat(log.lines()).contains("SEVERE: No available AVC decoder!")
        assertThat(events<CodecEvent.Configured>()).isEmpty()
    }

    @Test
    fun `setup returns minus 1 for H264 over 4096 wide or tall`() {
        install(avc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, width = 4097)).isEqualTo(-1)
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, height = 4097)).isEqualTo(-1)

        assertThat(log.lines()).contains("SEVERE: > 4K streaming only supported on HEVC")
        assertThat(events<CodecEvent.Configured>()).isEmpty()
    }

    @Test
    fun `setup accepts H264 of exactly 4096 by 4096`() {
        install(avc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, width = 4096, height = 4096))
            .isEqualTo(0)
    }

    @Test
    fun `setup returns minus 2 without an HEVC decoder`() {
        install(avc())
        val renderer = renderer()

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H265)).isEqualTo(-2)

        assertThat(log.lines()).contains("SEVERE: No available HEVC decoder!")
    }

    @Test
    fun `setup returns minus 2 without an AV1 decoder`() {
        install(avc())
        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_AV1_MAIN8)).isEqualTo(-2)

        assertThat(log.lines()).contains("SEVERE: No available AV1 decoder!")
    }

    @Test
    fun `setup returns minus 3 for a format it does not know`() {
        install(avc())
        val renderer = renderer()

        assertThat(renderer.prepare(UNKNOWN_FORMAT)).isEqualTo(-3)

        assertThat(log.lines()).contains("SEVERE: Unknown format")
    }

    @Test
    fun `setup without a render target fails with a null pointer and releases the codec`() {
        install(avc())
        val renderer = renderer()

        assertThrows(NullPointerException::class.java) {
            renderer.setup(MoonBridge.VIDEO_FORMAT_H264, 1920, 1080, 60)
        }

        assertThat(events<CodecEvent.Created>()).hasSize(1)
        assertThat(events<CodecEvent.Released>()).hasSize(1)
        assertThat(events<CodecEvent.Started>()).isEmpty()
    }

    @Test
    fun `the active video format is the one of the last setup`() {
        install(avc(), hevc())
        val renderer = renderer()

        assertThat(renderer.getActiveVideoFormat()).isEqualTo(0)
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)
        assertThat(renderer.getActiveVideoFormat()).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H265)
        assertThat(renderer.getActiveVideoFormat()).isEqualTo(MoonBridge.VIDEO_FORMAT_H265)
    }

    private companion object {
        const val LIMITED_RANGE = 2
        const val FULL_RANGE = 1
        const val SDR_TRANSFER = 3
        const val BT709 = 1

        // Outside the masks of H264 (0xF), HEVC (0xF00) and AV1 (0xF000).
        const val UNKNOWN_FORMAT = 0x10
    }
}

private typealias CodecChange = CodecBuilder.() -> Unit
