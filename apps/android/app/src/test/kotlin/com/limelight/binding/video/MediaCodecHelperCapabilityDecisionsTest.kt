package com.limelight.binding.video

import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes the decisions that `MediaCodecHelper` takes from a `MediaCodecInfo`: HEVC and AV1
 * whitelisting, adaptive playback, fused IDR frames and HEVC and AV1 reference frame invalidation.
 * The rows build the decoders with the Robolectric builder and set the Android version and the
 * media performance class through the `Build` fields.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperCapabilityDecisionsTest : MediaCodecHelperTestBase() {
    private val hevc = "video/hevc"

    private fun hevcDecoder(name: String, configure: CodecBuilder.() -> Unit = {}) =
        codec(name) {
            mime = hevc
            configure()
        }

    private fun api(sdk: Int, performanceClass: Int = 0) =
        DeviceBuild(sdk = sdk, performanceClass = performanceClass)

    @Test
    fun `a decoder with sw in its name is never whitelisted for HEVC`() {
        val helper = helper()
        val rows = Rows()

        rows.check("the Qualcomm software decoder", false) {
            helper.whitelistedForHevc(hevcDecoder("OMX.qcom.video.decoder.hevcswvdec"))
        }
        rows.check("the Samsung software decoder", false) {
            helper.whitelistedForHevc(hevcDecoder("OMX.SEC.hevc.sw.dec"))
        }
        rows.check("even with FEATURE_LowLatency", false) {
            helper.whitelistedForHevc(
                hevcDecoder("c2.vendor.hevc.sw.decoder") { lowLatency = true }
            )
        }
        rows.check("the match is case sensitive", true) {
            helper.whitelistedForHevc(hevcDecoder("OMX.Exynos.HEVC.SW.Decoder"))
        }

        rows.verify()
    }

    @Test
    fun `from Android 10 a decoder that is not hardware accelerated or is software only is out`() {
        val rows = Rows()

        rows.check("Android 10 not accelerated", false) {
            helper(api(Build.VERSION_CODES.Q))
                .whitelistedForHevc(
                    hevcDecoder("OMX.Exynos.HEVC.Decoder") { hardwareAccelerated = false }
                )
        }
        rows.check("Android 10 software only", false) {
            helper(api(Build.VERSION_CODES.Q))
                .whitelistedForHevc(hevcDecoder("OMX.Exynos.HEVC.Decoder") { softwareOnly = true })
        }
        rows.check("Android 10 hardware", true) {
            helper(api(Build.VERSION_CODES.Q))
                .whitelistedForHevc(hevcDecoder("OMX.Exynos.HEVC.Decoder"))
        }
        rows.check("Android 9 ignores the flags", true) {
            helper(api(Build.VERSION_CODES.P))
                .whitelistedForHevc(
                    hevcDecoder("OMX.Exynos.HEVC.Decoder") {
                        hardwareAccelerated = false
                        softwareOnly = true
                    }
                )
        }
        rows.check("the flags come before the performance class", false) {
            helper(
                    api(
                        Build.VERSION_CODES.TIRAMISU,
                        performanceClass = Build.VERSION_CODES.TIRAMISU,
                    )
                )
                .whitelistedForHevc(hevcDecoder("OMX.MTK.HEVC") { softwareOnly = true })
        }

        rows.verify()
    }

    @Test
    fun `from Android 12 media performance class 12 whitelists any hardware HEVC decoder`() {
        val unlisted = "OMX.vendor.hevc.decoder"
        val rows = Rows()

        rows.check("class 31 on Android 12", true) {
            helper(api(Build.VERSION_CODES.S, 31)).whitelistedForHevc(hevcDecoder(unlisted))
        }
        rows.check("class 33 on Android 13", true) {
            helper(api(Build.VERSION_CODES.TIRAMISU, 33)).whitelistedForHevc(hevcDecoder(unlisted))
        }
        rows.check("class 30 is below the bar", false) {
            helper(api(Build.VERSION_CODES.TIRAMISU, 30)).whitelistedForHevc(hevcDecoder(unlisted))
        }
        rows.check("no class", false) {
            helper(api(Build.VERSION_CODES.TIRAMISU, 0)).whitelistedForHevc(hevcDecoder(unlisted))
        }
        rows.check("the class is ignored before Android 12", false) {
            helper(api(Build.VERSION_CODES.R, 33)).whitelistedForHevc(hevcDecoder(unlisted))
        }

        rows.verify()
    }

    @Test
    fun `from Android 11 FEATURE_LowLatency whitelists a decoder for HEVC`() {
        val unlisted = "OMX.vendor.hevc.decoder"
        val rows = Rows()

        rows.check("Android 11 with the feature", true) {
            helper(api(Build.VERSION_CODES.R))
                .whitelistedForHevc(hevcDecoder(unlisted) { lowLatency = true })
        }
        rows.check("Android 11 without it", false) {
            helper(api(Build.VERSION_CODES.R)).whitelistedForHevc(hevcDecoder(unlisted))
        }
        rows.check("Android 10 does not look at the feature", false) {
            helper(api(Build.VERSION_CODES.Q))
                .whitelistedForHevc(hevcDecoder(unlisted) { lowLatency = true })
        }
        rows.check("a decoder that has no HEVC capabilities", false) {
            helper(api(Build.VERSION_CODES.R))
                .whitelistedForHevc(codec(unlisted) { lowLatency = true })
        }

        rows.verify()
    }

    @Test
    fun `the HEVC whitelist of known decoders applies last`() {
        val helper = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val rows = Rows()

        listOf(
                "OMX.Exynos.HEVC.Decoder",
                "c2.exynos.hevc.decoder",
                "omx.nvidia.h265.decode",
                "OMX.amlogic.hevc.decoder",
                "OMX.realtek.video.decoder.hevc",
                "c2.android.hevc.decoder",
                "C2.mediatek.hevc.decoder",
                "OMX.qcom.video.decoder.hevc",
                "c2.qti.hevc.decoder",
            )
            .forEach { name ->
                rows.check("$name is whitelisted", true) {
                    helper.whitelistedForHevc(hevcDecoder(name))
                }
            }
        listOf("OMX.MTK.VIDEO.DECODER.HEVC", "OMX.google.hevc.decoder", "OMX.rk.video_decoder.hevc")
            .forEach { name ->
                rows.check("$name is not", false) { helper.whitelistedForHevc(hevcDecoder(name)) }
            }

        rows.verify()
    }

    @Test
    fun `the HEVC whitelist follows the device and the GPU`() {
        val rows = Rows()

        rows.check("the emulator allows its software decoder", true) {
            helper(DeviceBuild(hardware = "ranchu"))
                .whitelistedForHevc(hevcDecoder("OMX.google.hevc.decoder"))
        }
        rows.check("a phone does not", false) {
            helper().whitelistedForHevc(hevcDecoder("OMX.google.hevc.decoder"))
        }
        rows.check("the Shield Tablet has no NVIDIA HEVC", false) {
            helper(DeviceBuild(device = "shieldtablet"))
                .whitelistedForHevc(hevcDecoder("OMX.Nvidia.h265.decode"))
        }
        rows.check("a Bravia allows MediaTek HEVC", true) {
            helper(DeviceBuild(device = "BRAVIA_ATV3_4K"))
                .whitelistedForHevc(hevcDecoder("OMX.MTK.VIDEO.DECODER.HEVC"))
        }
        rows.check("Sabrina has no Amlogic HEVC", false) {
            helper(DeviceBuild(device = "sabrina"))
                .whitelistedForHevc(hevcDecoder("OMX.amlogic.hevc.decoder"))
        }
        rows.check("Sabrina on Fire OS has it", true) {
            helper(DeviceBuild(device = "sabrina"), fireTv = true)
                .whitelistedForHevc(hevcDecoder("OMX.amlogic.hevc.decoder"))
        }
        rows.check("Fire TV allows MediaTek HEVC", true) {
            helper(fireTv = true).whitelistedForHevc(hevcDecoder("OMX.MTK.VIDEO.DECODER.HEVC"))
        }
        rows.check("a PowerVR GPU allows MediaTek HEVC", true) {
            helper(gl = GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GE8320"))
                .whitelistedForHevc(hevcDecoder("OMX.MTK.VIDEO.DECODER.HEVC"))
        }
        rows.check("an Adreno 3xx has no Qualcomm HEVC", false) {
            helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 306"))
                .whitelistedForHevc(hevcDecoder("OMX.qcom.video.decoder.hevc"))
        }
        rows.check("an Adreno 4xx has it", true) {
            helper(gl = GlFacts(GlFacts.GL_3_0, "Adreno (TM) 405"))
                .whitelistedForHevc(hevcDecoder("OMX.qcom.video.decoder.hevc"))
        }

        rows.verify()
    }

    @Test
    fun `no decoder is whitelisted for AV1`() {
        val av1 = "video/av01"
        val rows = Rows()

        rows.check("a hardware decoder on Android 13", false) {
            helper().whitelistedForAv1(codec("c2.vendor.av1.decoder") { mime = av1 })
        }
        rows.check("a hardware decoder with FEATURE_LowLatency", false) {
            helper()
                .whitelistedForAv1(
                    codec("c2.vendor.av1.decoder") {
                        mime = av1
                        lowLatency = true
                    }
                )
        }
        rows.check("a hardware decoder on Android 10", false) {
            helper(api(Build.VERSION_CODES.Q))
                .whitelistedForAv1(codec("c2.vendor.av1.decoder") { mime = av1 })
        }
        rows.check("a hardware decoder before Android 10", false) {
            helper(api(Build.VERSION_CODES.P))
                .whitelistedForAv1(codec("c2.vendor.av1.decoder") { mime = av1 })
        }
        rows.check("sw in the name", false) {
            helper().whitelistedForAv1(codec("c2.vendor.av1.sw.decoder") { mime = av1 })
        }
        rows.check("not hardware accelerated", false) {
            helper()
                .whitelistedForAv1(
                    codec("c2.vendor.av1.decoder") {
                        mime = av1
                        hardwareAccelerated = false
                    }
                )
        }
        rows.check("software only", false) {
            helper()
                .whitelistedForAv1(
                    codec("c2.vendor.av1.decoder") {
                        mime = av1
                        softwareOnly = true
                    }
                )
        }

        rows.verify()
    }

    @Test
    fun `adaptive playback needs the feature and is off for Intel and MediaTek decoders`() {
        val helper = helper()
        val rows = Rows()

        rows.check("a decoder with the feature", true) {
            helper.adaptivePlayback(
                codec("OMX.Exynos.AVC.Decoder") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("a decoder without it", false) {
            helper.adaptivePlayback(codec("OMX.Exynos.AVC.Decoder"), "video/avc")
        }
        rows.check("the Intel decoder is blacklisted", false) {
            helper.adaptivePlayback(
                codec("OMX.Intel.hw_vd.h264") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("the MediaTek decoder is blacklisted", false) {
            helper.adaptivePlayback(
                codec("omx.mtk.video.decoder.avc") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("the Codec2 MediaTek decoder is not", true) {
            helper.adaptivePlayback(
                codec("c2.mtk.avc.decoder") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("a mime type that the decoder does not support", false) {
            helper.adaptivePlayback(
                codec("OMX.Exynos.AVC.Decoder") { adaptivePlayback = true },
                hevc,
            )
        }
        rows.check("the feature of another mime type does not count", false) {
            helper.adaptivePlayback(
                codec("OMX.Exynos.HEVC.Decoder") {
                    mimes = listOf("video/avc", hevc)
                    adaptivePlayback = true
                },
                "video/vp9",
            )
        }

        rows.verify()
    }

    @Test
    fun `fused IDR frames need adaptive playback and no list applies`() {
        val helper = helper()
        val rows = Rows()

        rows.check("a decoder with the feature", true) {
            helper.fusedIdrFrame(
                codec("OMX.Exynos.AVC.Decoder") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("a decoder without it", false) {
            helper.fusedIdrFrame(codec("OMX.Exynos.AVC.Decoder"), "video/avc")
        }
        rows.check("the Intel decoder is not blacklisted here", true) {
            helper.fusedIdrFrame(
                codec("OMX.Intel.hw_vd.h264") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.check("a mime type that the decoder does not support", false) {
            helper.fusedIdrFrame(codec("OMX.Exynos.AVC.Decoder") { adaptivePlayback = true }, hevc)
        }
        rows.check("a decoder that throws for its capabilities", false) {
            helper.fusedIdrFrame(
                codec("OMX.Exynos.AVC.Decoder") {
                    adaptivePlayback = true
                    brokenCapabilities = true
                },
                "video/avc",
            )
        }

        rows.verify()
    }

    @Test
    fun `adaptive playback treats a decoder that throws for its capabilities as without it`() {
        val helper = helper()

        Rows().apply {
            check("a decoder that throws", false) {
                helper.adaptivePlayback(
                    codec("OMX.Exynos.AVC.Decoder") {
                        adaptivePlayback = true
                        brokenCapabilities = true
                    },
                    "video/avc",
                )
            }
            verify()
        }
    }

    @Test
    fun `HEVC reference frame invalidation follows the lists and the low latency options`() {
        val modern = helper(gl = GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        val rows = Rows()

        listOf(
                "OMX.Exynos.HEVC.Decoder",
                "c2.exynos.hevc.decoder",
                "OMX.Nvidia.h265.decode",
                "c2.nvidia.hevc.decoder",
                "OMX.qcom.video.decoder.hevc",
                "c2.qti.hevc.decoder",
            )
            .forEach { name ->
                rows.check("$name can invalidate", true) {
                    modern.refFrameInvalidationHevc(hevcDecoder(name))
                }
            }
        listOf("OMX.MTK.VIDEO.DECODER.HEVC", "OMX.amlogic.hevc.decoder", "OMX.google.hevc.decoder")
            .forEach { name ->
                rows.check("$name cannot", false) {
                    modern.refFrameInvalidationHevc(hevcDecoder(name))
                }
            }

        rows.verify()
    }

    @Test
    fun `HEVC reference frame invalidation of the NVIDIA decoder needs Android 11 and not the Pixel C`() {
        val rows = Rows()

        rows.check("Android 10", false) {
            helper(api(Build.VERSION_CODES.Q))
                .refFrameInvalidationHevc(hevcDecoder("OMX.Nvidia.h265.decode"))
        }
        rows.check("Android 10 for the Codec2 decoder", true) {
            helper(api(Build.VERSION_CODES.Q))
                .refFrameInvalidationHevc(hevcDecoder("c2.nvidia.hevc.decoder"))
        }
        rows.check("Android 11", true) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationHevc(hevcDecoder("OMX.Nvidia.h265.decode"))
        }
        rows.check("the Pixel C", false) {
            helper(DeviceBuild(device = "dragon"))
                .refFrameInvalidationHevc(hevcDecoder("OMX.Nvidia.h265.decode"))
        }

        rows.verify()
    }

    @Test
    fun `HEVC reference frame invalidation follows the device GPU and Amazon`() {
        val rows = Rows()

        rows.check("without OpenGL ES the Exynos decoder keeps its entry", true) {
            helper(gl = GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"))
                .refFrameInvalidationHevc(hevcDecoder("OMX.Exynos.HEVC.Decoder"))
        }
        rows.check("without OpenGL ES the Qualcomm decoder has none", false) {
            helper(gl = GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"))
                .refFrameInvalidationHevc(hevcDecoder("OMX.qcom.video.decoder.hevc"))
        }
        rows.check("a PowerVR Series6XT GPU allows the MediaTek decoder", true) {
            helper(gl = GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GX6250"))
                .refFrameInvalidationHevc(hevcDecoder("OMX.MTK.VIDEO.DECODER.HEVC"))
        }
        rows.check("the Codec2 MediaTek decoder too", true) {
            helper(gl = GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GX6250"))
                .refFrameInvalidationHevc(hevcDecoder("c2.mtk.hevc.decoder"))
        }
        rows.check("a newer PowerVR GPU does not", false) {
            helper(gl = GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GE8320"))
                .refFrameInvalidationHevc(hevcDecoder("OMX.MTK.VIDEO.DECODER.HEVC"))
        }
        listOf(
                "OMX.MTK.VIDEO.DECODER.HEVC",
                "c2.mtk.hevc.decoder",
                "OMX.amlogic.hevc.decoder",
                "c2.amlogic.hevc.decoder",
            )
            .forEach { name ->
                rows.check("Fire TV allows $name", true) {
                    helper(fireTv = true).refFrameInvalidationHevc(hevcDecoder(name))
                }
            }

        rows.verify()
    }

    @Test
    fun `FEATURE_LowLatency from Android 11 is enough for HEVC reference frame invalidation`() {
        val unlisted = "OMX.vendor.hevc.decoder"
        val rows = Rows()

        rows.check("Android 11 with the feature", true) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationHevc(hevcDecoder(unlisted) { lowLatency = true })
        }
        rows.check("Android 11 without it", false) {
            helper(api(Build.VERSION_CODES.R)).refFrameInvalidationHevc(hevcDecoder(unlisted))
        }
        rows.check("Android 10 ignores the feature", false) {
            helper(api(Build.VERSION_CODES.Q))
                .refFrameInvalidationHevc(hevcDecoder(unlisted) { lowLatency = true })
        }
        rows.check("Android 10 falls back to the list", true) {
            helper(api(Build.VERSION_CODES.Q))
                .refFrameInvalidationHevc(
                    hevcDecoder("OMX.Exynos.HEVC.Decoder") { lowLatency = true }
                )
        }
        rows.check("the feature of the AVC decoder does not count for HEVC", false) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationHevc(codec(unlisted) { lowLatency = true })
        }

        rows.verify()
    }

    @Test
    fun `a decoder that throws for its capabilities falls back to the list for HEVC reference frame invalidation`() {
        val rows = Rows()

        rows.check("a listed decoder", true) {
            helper()
                .refFrameInvalidationHevc(
                    hevcDecoder("OMX.Exynos.HEVC.Decoder") {
                        lowLatency = true
                        brokenCapabilities = true
                    }
                )
        }
        rows.check("an unlisted decoder", false) {
            helper()
                .refFrameInvalidationHevc(
                    hevcDecoder("OMX.vendor.hevc.decoder") {
                        lowLatency = true
                        brokenCapabilities = true
                    }
                )
        }

        rows.verify()
    }

    @Test
    fun `an Android 12 device that cannot probe vendor options falls back to the list`() {
        val rows = Rows()

        rows.check("Android 12 and a listed decoder", true) {
            helper(api(Build.VERSION_CODES.S))
                .refFrameInvalidationHevc(hevcDecoder("OMX.Exynos.HEVC.Decoder"))
        }
        rows.check("Android 12 and an unlisted decoder", false) {
            helper(api(Build.VERSION_CODES.S))
                .refFrameInvalidationHevc(hevcDecoder("OMX.vendor.hevc.decoder"))
        }
        rows.check("Android 15 and an unlisted decoder", false) {
            helper(api(Build.VERSION_CODES.VANILLA_ICE_CREAM))
                .refFrameInvalidationHevc(hevcDecoder("OMX.vendor.hevc.decoder"))
        }

        rows.verify()
    }

    @Test
    fun `AV1 reference frame invalidation needs FEATURE_LowLatency or a vendor option and no list applies`() {
        val av1 = "video/av01"
        val rows = Rows()

        rows.check("Android 11 with the feature", true) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationAv1(
                    codec("c2.vendor.av1.decoder") {
                        mime = av1
                        lowLatency = true
                    }
                )
        }
        rows.check("Android 11 without it", false) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationAv1(codec("c2.vendor.av1.decoder") { mime = av1 })
        }
        rows.check("Android 10 ignores the feature", false) {
            helper(api(Build.VERSION_CODES.Q))
                .refFrameInvalidationAv1(
                    codec("c2.vendor.av1.decoder") {
                        mime = av1
                        lowLatency = true
                    }
                )
        }
        rows.check("the lists of HEVC do not apply", false) {
            helper().refFrameInvalidationAv1(codec("OMX.Exynos.AV1.Decoder") { mime = av1 })
        }
        rows.check("the feature of the HEVC decoder does not count for AV1", false) {
            helper(api(Build.VERSION_CODES.R))
                .refFrameInvalidationAv1(
                    hevcDecoder("c2.vendor.hevc.decoder") { lowLatency = true }
                )
        }
        rows.check("Android 12 cannot probe vendor options", false) {
            helper(api(Build.VERSION_CODES.S))
                .refFrameInvalidationAv1(codec("c2.vendor.av1.decoder") { mime = av1 })
        }

        rows.verify()
    }
}
