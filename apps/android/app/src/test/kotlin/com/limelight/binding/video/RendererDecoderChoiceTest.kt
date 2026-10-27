package com.limelight.binding.video

import android.media.MediaCodecInfo.CodecProfileLevel
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import org.junit.Test

/**
 * Characterizes which decoders the `MediaCodecDecoderRenderer` constructor picks for H.264, HEVC
 * and AV1, from what the device lists, the format the user prefers and the HDR request. The
 * decisions about one decoder (whitelists, blacklists, capabilities) belong to `MediaCodecHelper`
 * and its tests; these tests pin what the renderer does with them.
 */
class RendererDecoderChoiceTest : RendererTestBase() {
    private val avc = codec("c2.test.avc.decoder") { mime = "video/avc" }
    private val hevc = codec("c2.test.hevc.decoder") { mime = "video/hevc" }

    // A name that no whitelist knows, so that HEVC needs a reason to be used.
    private val strangeHevc = codec("vendor.test.hevc.decoder") { mime = "video/hevc" }
    private val av1 = codec("c2.test.av1.decoder") { mime = "video/av01" }

    private fun nameAfterSetup(renderer: MediaCodecDecoderRenderer, format: Int): String? {
        renderer.setRenderTarget(holder)
        renderer.setup(format, 1920, 1080, 60)
        return renderer.getDecoderName()
    }

    // H.264

    @Test
    fun `the only H264 decoder is used and named`() {
        install(avc)

        val renderer = renderer()

        assertThat(renderer.isAvcSupported()).isTrue()
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_H264))
            .isEqualTo("c2.test.avc.decoder")
        assertThat(log.lines()).contains("INFO: Selected AVC decoder: c2.test.avc.decoder")
    }

    @Test
    fun `a decoder with the low latency feature wins over one listed before it`() {
        val plain = codec("c2.plain.avc.decoder") { mime = "video/avc" }
        val fast =
            codec("c2.fast.avc.decoder") {
                mime = "video/avc"
                lowLatency = true
            }
        install(plain, fast)

        renderer()

        assertThat(log.lines()).contains("INFO: Selected AVC decoder: c2.fast.avc.decoder")
    }

    @Test
    fun `a decoder without the High profile is used when nothing better is listed`() {
        val baseline =
            codec("c2.baseline.avc.decoder") {
                mime = "video/avc"
                profiles = listOf(CodecProfileLevel.AVCProfileBaseline)
            }
        install(baseline)

        val renderer = renderer()

        assertThat(renderer.isAvcSupported()).isTrue()
        assertThat(log.lines()).contains("INFO: Selected AVC decoder: c2.baseline.avc.decoder")
    }

    @Test
    fun `a decoder with the High profile wins over one listed before it without`() {
        val baseline =
            codec("c2.baseline.avc.decoder") {
                mime = "video/avc"
                profiles = listOf(CodecProfileLevel.AVCProfileBaseline)
            }
        install(baseline, avc)

        renderer()

        assertThat(log.lines()).contains("INFO: Selected AVC decoder: c2.test.avc.decoder")
    }

    @Test
    fun `without an H264 decoder H264 is unsupported and the renderer says so`() {
        install(hevc)

        val renderer = renderer()

        assertThat(renderer.isAvcSupported()).isFalse()
        assertThat(log.lines()).contains("WARNING: No AVC decoder found")
    }

    // HEVC

    @Test
    fun `a whitelisted HEVC decoder is used with the automatic format`() {
        install(avc, hevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_H265))
            .isEqualTo("c2.test.hevc.decoder")
        assertThat(log.lines()).contains("INFO: Selected HEVC decoder: c2.test.hevc.decoder")
    }

    @Test
    fun `forcing H264 leaves HEVC out even when the decoder is whitelisted`() {
        install(avc, hevc)

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_H264 })

        assertThat(renderer.isHevcSupported()).isFalse()
        assertThat(log.lines()).contains("INFO: No HEVC decoder found")
    }

    @Test
    fun `a decoder that is not whitelisted is not used on its own`() {
        install(avc, strangeHevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Found HEVC decoder, but it's not whitelisted - vendor.test.hevc.decoder",
                "INFO: No HEVC decoder found",
            )
    }

    @Test
    fun `forcing HEVC uses a decoder that is not whitelisted`() {
        install(avc, strangeHevc)

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_HEVC })

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Forcing HEVC enabled despite non-whitelisted decoder",
                "INFO: Selected HEVC decoder: vendor.test.hevc.decoder",
            )
    }

    @Test
    fun `an HDR request uses a decoder that is not whitelisted`() {
        install(avc, strangeHevc)

        val renderer = renderer(hdr = true)

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(log.lines()).contains("INFO: Forcing HEVC enabled for HDR streaming")
    }

    @Test
    fun `a stream wider than 4096 uses a decoder that is not whitelisted`() {
        install(avc, strangeHevc)

        val renderer = renderer(prefs { width = 4097 })

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(log.lines()).contains("INFO: Forcing HEVC enabled for over 4K streaming")
    }

    @Test
    fun `a stream taller than 4096 uses a decoder that is not whitelisted`() {
        install(avc, strangeHevc)

        val renderer = renderer(prefs { height = 4097 })

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(log.lines()).contains("INFO: Forcing HEVC enabled for over 4K streaming")
    }

    @Test
    fun `a stream of exactly 4096 by 4096 is not over 4K`() {
        install(avc, strangeHevc)

        val renderer =
            renderer(
                prefs {
                    width = 4096
                    height = 4096
                }
            )

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `without an H264 decoder a decoder that is not whitelisted is not used`() {
        install(strangeHevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    // The performance point test: HEVC is used when H264 cannot reach the frame rate and it can.

    private val slowAvc =
        limitedCodec("vendor.slow.avc.decoder", "video/avc") { blocksPerSecond = "1-100000" }
    private val fastHevc =
        limitedCodec("vendor.fast.hevc.decoder", "video/hevc") {
            profiles = listOf(CodecProfileLevel.HEVCProfileMain)
        }

    @Test
    fun `HEVC that is not whitelisted is used when H264 cannot reach the frame rate and it can`() {
        install(slowAvc, fastHevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
        assertThat(log.lines())
            .contains("INFO: Using non-whitelisted HEVC decoder to meet performance point")
    }

    @Test
    fun `HEVC that is not whitelisted stays out when H264 reaches the frame rate`() {
        val fastAvc = limitedCodec("vendor.fast.avc.decoder", "video/avc")
        install(fastAvc, fastHevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `HEVC that is not whitelisted stays out when it cannot reach the frame rate either`() {
        val slowHevc =
            limitedCodec("vendor.slow.hevc.decoder", "video/hevc") {
                blocksPerSecond = "1-100000"
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(slowAvc, slowHevc)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `performance points decide when the decoder lists them`() {
        val avcPoints =
            limitedCodec("vendor.points.avc.decoder", "video/avc") {
                performancePoints = mapOf("1920x1080" to "30-30")
            }
        val hevcPoints =
            limitedCodec("vendor.points.hevc.decoder", "video/hevc") {
                performancePoints = mapOf("1920x1080" to "60-120")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(avcPoints, hevcPoints)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
    }

    @Test
    fun `performance points that do not cover the stream keep HEVC out`() {
        val avcPoints =
            limitedCodec("vendor.points.avc.decoder", "video/avc") {
                performancePoints = mapOf("1920x1080" to "30-30")
            }
        val hevcPoints =
            limitedCodec("vendor.points.hevc.decoder", "video/hevc") {
                performancePoints = mapOf("1920x1080" to "30-120")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(avcPoints, hevcPoints)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `measured frame rates decide when the decoder lists no performance points`() {
        val slow =
            limitedCodec("vendor.measured.avc.decoder", "video/avc") {
                measuredRates = mapOf("1920x1080" to "30-45")
            }
        val fast =
            limitedCodec("vendor.measured.hevc.decoder", "video/hevc") {
                measuredRates = mapOf("1920x1080" to "60-90")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(slow, fast)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
    }

    @Test
    fun `measured frame rates that stay under the target keep HEVC out`() {
        val slow =
            limitedCodec("vendor.measured.avc.decoder", "video/avc") {
                measuredRates = mapOf("1920x1080" to "30-45")
            }
        val alsoSlow =
            limitedCodec("vendor.measured.hevc.decoder", "video/hevc") {
                measuredRates = mapOf("1920x1080" to "30-59")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(slow, alsoSlow)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `a measured frame rate equal to the target meets it`() {
        val slow =
            limitedCodec("vendor.measured.avc.decoder", "video/avc") {
                measuredRates = mapOf("1920x1080" to "30-45")
            }
        val exact =
            limitedCodec("vendor.measured.hevc.decoder", "video/hevc") {
                measuredRates = mapOf("1920x1080" to "30-60")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(slow, exact)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
    }

    @Test
    fun `a size that H264 does not support at any frame rate does not meet the target`() {
        val wide =
            limitedCodec("vendor.wide.hevc.decoder", "video/hevc") {
                sizeRange = "64x64-8192x4608"
                blockCount = "1-1000000"
                blocksPerSecond = "1-100000000"
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(avc, wide)

        val renderer =
            renderer(
                prefs {
                    width = 4096
                    height = 3000
                }
            )

        assertThat(renderer.isHevcSupported()).isTrue()
    }

    @Test
    fun `a size that neither decoder supports keeps HEVC out`() {
        install(avc, strangeHevc)

        val renderer =
            renderer(
                prefs {
                    width = 4096
                    height = 3000
                }
            )

        assertThat(renderer.isHevcSupported()).isFalse()
    }

    @Test
    fun `before Android 10 the performance points are not read`() {
        device(DeviceBuild(sdk = 28))
        val slow =
            limitedCodec("vendor.old.avc.decoder", "video/avc") {
                performancePoints = mapOf("1920x1080" to "60-60")
                measuredRates = mapOf("1920x1080" to "30-45")
            }
        val fast =
            limitedCodec("vendor.old.hevc.decoder", "video/hevc") {
                performancePoints = mapOf("1920x1080" to "30-30")
                measuredRates = mapOf("1920x1080" to "60-90")
                profiles = listOf(CodecProfileLevel.HEVCProfileMain)
            }
        install(slow, fast)

        val renderer = renderer()

        assertThat(renderer.isHevcSupported()).isTrue()
    }

    // AV1

    @Test
    fun `AV1 is not used unless it is forced`() {
        install(avc, av1)

        for (format in
            listOf(FormatOption.AUTO, FormatOption.FORCE_H264, FormatOption.FORCE_HEVC)) {
            val renderer = renderer(prefs { videoFormat = format })

            assertThat(renderer.isAv1Supported()).isFalse()
        }
    }

    @Test
    fun `forcing AV1 uses an AV1 decoder even though none is whitelisted`() {
        install(avc, av1)

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.isAv1Supported()).isTrue()
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_AV1_MAIN8))
            .isEqualTo("c2.test.av1.decoder")
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Found AV1 decoder, but it's not whitelisted - c2.test.av1.decoder",
                "INFO: Forcing AV1 enabled despite non-whitelisted decoder",
                "INFO: Selected AV1 decoder: c2.test.av1.decoder",
            )
    }

    @Test
    fun `forcing AV1 without an AV1 decoder leaves AV1 unsupported`() {
        install(avc)

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.isAv1Supported()).isFalse()
        assertThat(log.lines()).contains("INFO: No AV1 decoder found")
    }

    // The name of the decoder of the active format

    @Test
    fun `the decoder name follows the format of the setup`() {
        install(avc, hevc, av1)
        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.getDecoderName()).isNull()
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_H264))
            .isEqualTo("c2.test.avc.decoder")
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_H265))
            .isEqualTo("c2.test.hevc.decoder")
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_H265_MAIN10))
            .isEqualTo("c2.test.hevc.decoder")
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_AV1_MAIN8))
            .isEqualTo("c2.test.av1.decoder")
        assertThat(nameAfterSetup(renderer, MoonBridge.VIDEO_FORMAT_AV1_MAIN10))
            .isEqualTo("c2.test.av1.decoder")
        assertThat(renderer.getActiveVideoFormat()).isEqualTo(MoonBridge.VIDEO_FORMAT_AV1_MAIN10)
    }

    @Test
    fun `the decoder name is null for a format the renderer does not know`() {
        install(avc)
        val renderer = renderer()

        assertThat(nameAfterSetup(renderer, 0x10)).isNull()
    }
}
