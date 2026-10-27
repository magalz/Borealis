package com.limelight.binding.video

import android.media.MediaCodecInfo.CodecProfileLevel
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Characterizes the failure that a renderer cannot recover from and reports: when the crash
 * listener hears of it, what it is told, and the text of the `RendererException` that carries the
 * state of the renderer to the crash report. The failure that these tests cause is the simplest one
 * to make: a decode unit that does not fit in an input buffer of the codec.
 */
class RendererCrashTest : RendererTestBase() {
    private val inputBufferSize = 64
    private val tooBig = DecodeUnit(ByteArray(100) { 1 }, frameNumber = 1)

    /** A renderer for [format] whose codecs have input buffers of 64 bytes. */
    private fun small(
        format: Int = MoonBridge.VIDEO_FORMAT_H264,
        formatOption: FormatOption = FormatOption.AUTO,
        crashCount: Int = 0,
    ): MediaCodecDecoderRenderer {
        val decoders =
            listOf(
                codec("vendor.test.avc.decoder") { mime = "video/avc" },
                codec("c2.test.hevc.decoder") { mime = "video/hevc" },
                codec("vendor.test.av1.decoder") { mime = "video/av01" },
            )
        install(*decoders.toTypedArray())
        decoders.forEach { ShadowRecordingVideoCodec.register(it.name, inputBufferSize) }
        val renderer = renderer(prefs { videoFormat = formatOption }, crashCount)
        assertThat(renderer.prepare(format)).isEqualTo(0)
        return renderer
    }

    private fun crash(renderer: MediaCodecDecoderRenderer, unit: DecodeUnit = tooBig): Throwable =
        assertThrows(RuntimeException::class.java) { renderer.submit(unit) }

    private fun Throwable.lines(): List<String> = toString().split("\n")

    /** Tells the renderer of one PPS, so that the error is no longer taken for one before any SPS. */
    private fun MediaCodecDecoderRenderer.gotPps() {
        assertThat(submit(h264Pps())).isEqualTo(MoonBridge.DR_OK)
    }

    // Who is told

    @Test
    fun `a decode unit that does not fit in the input buffer crashes the renderer`() {
        val renderer = small()

        val thrown = crash(renderer)

        assertThat(thrown.javaClass.name)
            .isEqualTo("com.limelight.binding.video.MediaCodecDecoderRenderer\$RendererException")
        assertThat(thrown.lines().last())
            .isEqualTo("java.lang.IllegalArgumentException: Decode unit length 100 too large for input buffer 64")
    }

    @Test
    fun `the crash listener is told what went wrong, once`() {
        val renderer = small()

        crash(renderer)

        assertThat(crashes).hasSize(1)
        assertThat(crashes.single()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(crashes.single())
            .hasMessageThat()
            .isEqualTo("Decode unit length 100 too large for input buffer 64")
    }

    @Test
    fun `a second crash of the same renderer is thrown but not reported again`() {
        val renderer = small()
        crash(renderer)

        val second = crash(renderer)

        assertThat(second.javaClass.simpleName).isEqualTo("RendererException")
        assertThat(crashes).hasSize(1)
    }

    @Test
    fun `a unit that fits exactly is queued`() {
        val renderer = small()

        val result = renderer.submit(DecodeUnit(ByteArray(inputBufferSize) { 1 }, frameNumber = 1))

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(crashes).isEmpty()
    }

    @Test
    fun `a unit one byte too long is a crash`() {
        val renderer = small()

        crash(renderer, DecodeUnit(ByteArray(inputBufferSize + 1) { 1 }, frameNumber = 1))

        assertThat(crashes).hasSize(1)
    }

    @Test
    fun `the exception has no message of its own, only its text`() {
        val renderer = small()

        val thrown = crash(renderer)

        assertThat(thrown).hasMessageThat().isNull()
    }

    // The first line: how far the stream had got

    @Test
    fun `before any parameter set the error is a pre SPS error`() {
        val renderer = small()

        assertThat(crash(renderer).lines().first()).isEqualTo("PreSPSErrorFormat: 1")
    }

    @Test
    fun `a frame that came before any parameter set does not change that`() {
        val renderer = small()
        renderer.submit(pFrame(1))

        assertThat(crash(renderer, tooBig.again(2)).lines().first()).isEqualTo("PreSPSErrorFormat: 1")
    }

    @Test
    fun `after an SPS but before a PPS the error is a pre PPS error`() {
        val renderer = small()
        renderer.submit(parameterSet(MoonBridge.BUFFER_TYPE_SPS, hex(H264Sps().nal())))

        assertThat(crash(renderer).lines().first()).isEqualTo("PrePPSErrorFormat: 1")
    }

    @Test
    fun `a VPS alone is neither an SPS nor a PPS, and takes the stream past the first checks`() {
        val renderer = small(MoonBridge.VIDEO_FORMAT_H265)
        renderer.submit(parameterSet(MoonBridge.BUFFER_TYPE_VPS, "00000001 4001"))

        assertThat(crash(renderer).lines().first()).isEqualTo("PreOutputConfigErrorFormat: 100")
    }

    @Test
    fun `after a frame was sent but before the output format is known it is a pre output config error`() {
        val renderer = small()
        renderer.gotPps()
        renderer.submit(pFrame(1))

        assertThat(crash(renderer, tooBig.again(2)).lines().first())
            .isEqualTo("PreOutputConfigErrorFormat: 1")
    }

    @Test
    fun `after the output format is known but before any output it is a pre output error`() {
        val renderer = small()
        renderer.gotPps()
        renderer.begin()
        await("the output format") { log.lines().any { it == "INFO: Output format changed" } }

        assertThat(crash(renderer, tooBig.again(1)).lines().first())
            .isEqualTo("PreOutputErrorFormat: 1")
    }

    @Test
    fun `after a few frames came out it is an early output error`() {
        val renderer = small()
        renderer.gotPps()
        renderer.begin()
        renderer.submit(pFrame(1))
        await("the first frame to come out") { events<CodecEvent.Output>().isNotEmpty() }

        assertThat(crash(renderer, tooBig.again(2)).lines().first())
            .isEqualTo("EarlyOutputErrorFormat: 1")
    }

    @Test
    fun `after more than 30 seconds of frames at the setup frame rate it is an error while streaming`() {
        val renderer = small()
        renderer.gotPps()
        // One frame a second: 30 seconds are 30 frames.
        renderer.setup(MoonBridge.VIDEO_FORMAT_H264, 1920, 1080, 1)
        renderer.begin()
        for (number in 1..31) renderer.submit(pFrame(number))
        await("31 frames to come out") { events<CodecEvent.Output>().size >= 31 }

        assertThat(crash(renderer, tooBig.again(32)).lines().first())
            .isEqualTo("ErrorWhileStreamingFormat: 1")
    }

    @Test
    fun `exactly 30 seconds of frames at the setup frame rate is still an early output error`() {
        val renderer = small()
        renderer.gotPps()
        renderer.setup(MoonBridge.VIDEO_FORMAT_H264, 1920, 1080, 1)
        renderer.begin()
        for (number in 1..30) renderer.submit(pFrame(number))
        await("30 frames to come out") { events<CodecEvent.Output>().size >= 30 }

        assertThat(crash(renderer, tooBig.again(31)).lines().first())
            .isEqualTo("EarlyOutputErrorFormat: 1")
    }

    private fun DecodeUnit.again(number: Int) =
        DecodeUnit(data, type, number, frameType, times)

    // The text

    @Test
    fun `the text lists the decoders, the formats, the device and the counters`() {
        device(DeviceBuild(sdk = Build.VERSION_CODES.S, performanceClass = Build.VERSION_CODES.S))
        deviceSoc("Qualcomm", "SM8250")
        val renderer = small(crashCount = 2)

        val lines = crash(renderer).lines()

        assertThat(lines.filterNot { it.startsWith("Configured format") || it.startsWith("Input format") })
            .containsExactly(
                "PreSPSErrorFormat: 1",
                "AVC Decoder: vendor.test.avc.decoder",
                "HEVC Decoder: c2.test.hevc.decoder",
                "AV1 Decoder: (none)",
                "AVC supported width range: [64, 4096]",
                "AVC achievable FPS range: null",
                "HEVC supported width range: [64, 4096]",
                "HEVC achievable FPS range: null",
                "Output format: null",
                "Adaptive playback: false",
                "GL Renderer: Adreno (TM) 640",
                "SOC: Qualcomm - SM8250",
                "Performance class: 31",
                "Consecutive crashes: 2",
                "RFI active: false",
                "Using modern SPS patching: true",
                "Fused IDR frames: false",
                "Video dimensions: 1920x1080",
                "FPS target: 60",
                "Bitrate: 20000 Kbps",
                "CSD stats: 0, 0, 0",
                "Frames in-out: 1, 0",
                "Total frames received: 0",
                "Total frames rendered: 0",
                "Frame losses: 0 in 0 loss events",
                "Average end-to-end client latency: 0ms",
                "Average hardware decoder latency: 0ms",
                "Frame pacing mode: 0",
                "java.lang.IllegalArgumentException: Decode unit length 100 too large for input buffer 64",
            )
            .inOrder()
    }

    @Test
    fun `the configured format is in the text as the codec was given it`() {
        val renderer = small()

        val lines = crash(renderer).lines()

        val expected = configuredFormats.single()
        for (prefix in listOf("Configured format: ", "Input format: ")) {
            val line = lines.single { it.startsWith(prefix) }.removePrefix(prefix)
            assertThat(line).startsWith("{")
            assertThat(line).endsWith("}")
            val entries = line.removeSurrounding("{", "}").split(", ")
            assertThat(entries)
                .containsExactlyElementsIn(expected.map { (key, value) -> "$key=$value" })
        }
    }

    @Test
    fun `the lines before Android 12 have no SoC`() {
        device(DeviceBuild(sdk = Build.VERSION_CODES.R))
        val renderer = small()

        val lines = crash(renderer).lines()

        assertThat(lines.filter { it.startsWith("SOC") || it.startsWith("Performance") }).isEmpty()
        assertThat(lines).contains("GL Renderer: Adreno (TM) 640")
    }

    @Test
    fun `the GL renderer is null when the activity did not give one`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        ShadowRecordingVideoCodec.register("vendor.test.avc.decoder", inputBufferSize)
        val renderer = renderer(glRenderer = null)
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(crash(renderer).lines()).contains("GL Renderer: null")
    }

    @Test
    fun `an HEVC decoder and an AV1 decoder are described as the H264 one is`() {
        device(DeviceBuild(sdk = Build.VERSION_CODES.R))
        val renderer =
            small(MoonBridge.VIDEO_FORMAT_AV1_MAIN8, FormatOption.FORCE_AV1)

        val lines = crash(renderer).lines()

        assertThat(lines)
            .containsAtLeast(
                "PreSPSErrorFormat: 1000",
                "HEVC Decoder: c2.test.hevc.decoder",
                "AV1 Decoder: vendor.test.av1.decoder",
                "AV1 supported width range: [64, 4096]",
                "AV1 achievable FPS range: null",
            )
    }

    @Test
    fun `a size that a decoder does not support at any frame rate is reported as unsupported`() {
        device(DeviceBuild(sdk = Build.VERSION_CODES.R))
        val wide =
            listOf(
                codec("vendor.test.avc.decoder") { mime = "video/avc" },
                codec("c2.test.hevc.decoder") { mime = "video/hevc" },
                codec("vendor.test.av1.decoder") { mime = "video/av01" },
            )
        install(*wide.toTypedArray())
        wide.forEach { ShadowRecordingVideoCodec.register(it.name, inputBufferSize) }
        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })
        // 4096 by 4096 is a size that H264 may have, and that the decoders of the fake do not.
        renderer.setRenderTarget(holder)
        assertThat(renderer.setup(MoonBridge.VIDEO_FORMAT_H264, 4096, 4096, 60)).isEqualTo(0)

        val lines = crash(renderer).lines()

        assertThat(lines)
            .containsAtLeast(
                "AVC achievable FPS range: UNSUPPORTED!",
                "HEVC achievable FPS range: UNSUPPORTED!",
                "AV1 achievable FPS range: UNSUPPORTED!",
            )
    }

    @Test
    fun `the frame rate range of a decoder that was measured is printed`() {
        install(
            limitedCodec("vendor.test.avc.decoder", "video/avc") {
                measuredRates = mapOf("1920x1080" to "30-45")
            }
        )
        ShadowRecordingVideoCodec.register("vendor.test.avc.decoder", inputBufferSize)
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        val line = crash(renderer).lines().single { it.startsWith("AVC achievable FPS range") }

        assertThat(line).isEqualTo("AVC achievable FPS range: [30.0, 45.0]")
    }

    @Test
    fun `the counters reflect the frames the renderer saw`() {
        val renderer = small(MoonBridge.VIDEO_FORMAT_H265)
        for (type in listOf(MoonBridge.BUFFER_TYPE_VPS, MoonBridge.BUFFER_TYPE_SPS, MoonBridge.BUFFER_TYPE_PPS)) {
            renderer.submit(parameterSet(type, "00000001 4001"))
        }
        renderer.submit(parameterSet(MoonBridge.BUFFER_TYPE_SPS, "00000001 4201", number = 1))

        val lines = crash(renderer).lines()

        assertThat(lines).contains("CSD stats: 1, 2, 1")
    }

    @Test
    fun `an H264 stream with a decoder that fuses IDR frames says so`() {
        install(
            codec("vendor.test.avc.decoder") {
                mime = "video/avc"
                adaptivePlayback = true
            }
        )
        ShadowRecordingVideoCodec.register("vendor.test.avc.decoder", inputBufferSize)
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        val lines = crash(renderer).lines()

        assertThat(lines).containsAtLeast("Adaptive playback: true", "Fused IDR frames: true")
    }

    @Test
    fun `a decoder that invalidates reference frames says so`() {
        install(codec("c2.qti.avc.decoder") { mime = "video/avc" })
        ShadowRecordingVideoCodec.register("c2.qti.avc.decoder", inputBufferSize)
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)

        assertThat(crash(renderer).lines()).contains("RFI active: true")
    }

    @Test
    fun `the HEVC main 10 format is printed in hexadecimal`() {
        install(
            codec("vendor.test.avc.decoder") { mime = "video/avc" },
            codec("c2.test.hevc.decoder") {
                mime = "video/hevc"
                profiles = listOf(CodecProfileLevel.HEVCProfileMain10HDR10)
            },
        )
        ShadowRecordingVideoCodec.register("c2.test.hevc.decoder", inputBufferSize)
        val renderer = renderer()
        renderer.prepare(MoonBridge.VIDEO_FORMAT_H265_MAIN10)

        assertThat(crash(renderer).lines().first()).isEqualTo("PreSPSErrorFormat: 200")
    }
}
