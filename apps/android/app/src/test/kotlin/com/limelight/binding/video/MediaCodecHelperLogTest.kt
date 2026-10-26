package com.limelight.binding.video

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.Build
import io.github.magalz.borealis.crypto.LimeLogCapture
import java.util.logging.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes what `MediaCodecHelper` logs through `LimeLog`: the text and the level of each
 * line, in order, for the decisions that log. The log of a decision is what the field reports show,
 * so a conversion keeps every line.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperLogTest : MediaCodecHelperTestBase() {
    private val info = Level.INFO

    /** What [action] logs, as the level and the text of each record. */
    private fun logged(action: () -> Unit): List<Pair<Level, String>> =
        LimeLogCapture().use { capture ->
            action()
            capture.records.toList()
        }

    private fun infos(vararg messages: String) = messages.map { info to it }

    private fun logOfInitialize(gl: GlFacts, fireTv: Boolean = false): List<Pair<Level, String>> {
        val loaded = fresh()
        setGl(gl, fireTv)
        return logged { loaded.initialize(context, gl.renderer) }
    }

    @Test
    fun `initialize logs the OpenGL ES version and what it adds`() {
        val rows = Rows()
        val nvidia = "Added omx.nvidia/c2.nvidia to reference frame invalidation support list"
        val qualcomm = "Added omx.qcom/c2.qti to reference frame invalidation support list"
        val adreno = "Found Adreno GPU: 640"

        rows.check(
            "Adreno 640",
            infos(
                "OpenGL ES version: 196610",
                adreno,
                adreno,
                nvidia,
                qualcomm,
                adreno,
                "Added omx.qcom/c2.qti to HEVC decoders based on GLES 3.1+ support",
            ),
        ) {
            logOfInitialize(GlFacts(GlFacts.GL_3_2, "Adreno (TM) 640"))
        }
        rows.check(
            "OpenGL ES 2.0 and Adreno 640",
            infos(
                "OpenGL ES version: 131072",
                adreno,
                adreno,
                adreno,
                "Added omx.qcom/c2.qti to HEVC decoders based on GLES 3.1+ support",
            ),
        ) {
            logOfInitialize(GlFacts(GlFacts.GL_2_0, "Adreno (TM) 640"))
        }
        rows.verify()
    }

    @Test
    fun `initialize logs what it adds for the other GPUs`() {
        val nvidia = "Added omx.nvidia/c2.nvidia to reference frame invalidation support list"
        val qualcomm = "Added omx.qcom/c2.qti to reference frame invalidation support list"
        val rows = Rows()

        rows.check("Mali", infos("OpenGL ES version: 196610", nvidia, qualcomm)) {
            logOfInitialize(GlFacts(GlFacts.GL_3_2, "Mali-G78 MP14"))
        }
        rows.check(
            "PowerVR GX6250",
            infos(
                "OpenGL ES version: 196610",
                nvidia,
                qualcomm,
                "Added omx.mtk to HEVC decoders based on PowerVR GPU",
                "Added omx.mtk/c2.mtk to RFI list for HEVC",
            ),
        ) {
            logOfInitialize(GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GX6250"))
        }
        rows.check(
            "PowerVR GE8320",
            infos(
                "OpenGL ES version: 196610",
                nvidia,
                qualcomm,
                "Added omx.mtk to HEVC decoders based on PowerVR GPU",
            ),
        ) {
            logOfInitialize(GlFacts(GlFacts.GL_3_2, "PowerVR Rogue GE8320"))
        }
        rows.check("no OpenGL ES version", emptyList()) {
            logOfInitialize(GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"))
        }
        rows.check("Fire TV logs nothing of its own", emptyList()) {
            logOfInitialize(GlFacts(GlFacts.UNDEFINED, "Adreno (TM) 640"), fireTv = true)
        }

        rows.verify()
    }

    @Test
    fun `the HEVC whitelist logs why it says no or yes`() {
        val hevc = "video/hevc"
        val rows = Rows()

        rows.check(
            "sw in the name",
            infos("Disallowing HEVC on software decoder: OMX.SEC.hevc.sw.dec"),
        ) {
            val helper = helper()
            logged { helper.whitelistedForHevc(codec("OMX.SEC.hevc.sw.dec") { mime = hevc }) }
        }
        rows.check(
            "a decoder that is not hardware accelerated",
            infos("Disallowing HEVC on software decoder: c2.vendor.hevc"),
        ) {
            val helper = helper()
            logged {
                helper.whitelistedForHevc(
                    codec("c2.vendor.hevc") {
                        mime = hevc
                        hardwareAccelerated = false
                    }
                )
            }
        }
        rows.check(
            "the media performance class",
            infos("Media performance class: 33", "Allowing HEVC based on media performance class"),
        ) {
            val helper = helper(DeviceBuild(performanceClass = Build.VERSION_CODES.TIRAMISU))
            logged { helper.whitelistedForHevc(codec("c2.vendor.hevc") { mime = hevc }) }
        }
        rows.check(
            "FEATURE_LowLatency",
            infos(
                "Media performance class: 0",
                "Low latency decoding mode supported (FEATURE_LowLatency)",
                "Allowing HEVC based on FEATURE_LowLatency support",
            ),
        ) {
            val helper = helper()
            logged {
                helper.whitelistedForHevc(
                    codec("c2.vendor.hevc") {
                        mime = hevc
                        lowLatency = true
                    }
                )
            }
        }
        rows.check("the list on Android 12", infos("Media performance class: 0")) {
            val helper = helper()
            logged { helper.whitelistedForHevc(codec("OMX.Exynos.HEVC.Decoder") { mime = hevc }) }
        }
        rows.check("the list on Android 11", emptyList()) {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.R))
            logged { helper.whitelistedForHevc(codec("OMX.Exynos.HEVC.Decoder") { mime = hevc }) }
        }

        rows.verify()
    }

    @Test
    fun `the AV1 whitelist logs why it says no`() {
        val av1 = "video/av01"
        val rows = Rows()

        rows.check(
            "sw in the name",
            infos("Disallowing AV1 on software decoder: c2.vendor.sw.av1"),
        ) {
            val helper = helper()
            logged { helper.whitelistedForAv1(codec("c2.vendor.sw.av1") { mime = av1 }) }
        }
        rows.check("software only", infos("Disallowing AV1 on software decoder: c2.vendor.av1")) {
            val helper = helper()
            logged {
                helper.whitelistedForAv1(
                    codec("c2.vendor.av1") {
                        mime = av1
                        softwareOnly = true
                    }
                )
            }
        }
        rows.check("a hardware decoder", emptyList()) {
            val helper = helper()
            logged { helper.whitelistedForAv1(codec("c2.vendor.av1") { mime = av1 }) }
        }

        rows.verify()
    }

    @Test
    fun `the feature decisions log what they found`() {
        val rows = Rows()

        rows.check(
            "adaptive playback",
            infos("Adaptive playback supported (FEATURE_AdaptivePlayback)"),
        ) {
            val helper = helper()
            logged {
                helper.adaptivePlayback(
                    codec("c2.vendor.avc") { adaptivePlayback = true },
                    "video/avc",
                )
            }
        }
        rows.check(
            "adaptive playback of a blacklisted decoder",
            infos("Decoder blacklisted for adaptive playback"),
        ) {
            val helper = helper()
            logged {
                helper.adaptivePlayback(
                    codec("OMX.Intel.hw_vd.h264") { adaptivePlayback = true },
                    "video/avc",
                )
            }
        }
        rows.check(
            "fused IDR frames",
            infos("Decoder supports fused IDR frames (FEATURE_AdaptivePlayback)"),
        ) {
            val helper = helper()
            logged {
                helper.fusedIdrFrame(
                    codec("c2.vendor.avc") { adaptivePlayback = true },
                    "video/avc",
                )
            }
        }
        rows.verify()
    }

    @Test
    fun `the reference frame decisions log the low latency option that they found`() {
        val hevc = "video/hevc"
        val av1 = "video/av01"
        val lowLatencyLog = "Low latency decoding mode supported (FEATURE_LowLatency)"
        val rows = Rows()

        rows.check(
            "HEVC reference frame invalidation by FEATURE_LowLatency",
            infos(lowLatencyLog, "Enabling HEVC RFI based on low latency option support"),
        ) {
            val helper = helper()
            logged {
                helper.refFrameInvalidationHevc(
                    codec("c2.vendor.hevc") {
                        mime = hevc
                        lowLatency = true
                    }
                )
            }
        }
        rows.check(
            "AV1 reference frame invalidation by FEATURE_LowLatency",
            infos(lowLatencyLog, "Enabling AV1 RFI based on low latency option support"),
        ) {
            val helper = helper()
            logged {
                helper.refFrameInvalidationAv1(
                    codec("c2.vendor.av1") {
                        mime = av1
                        lowLatency = true
                    }
                )
            }
        }
        rows.check("HEVC reference frame invalidation by the list", emptyList()) {
            val helper = helper()
            logged { helper.refFrameInvalidationHevc(codec("c2.exynos.hevc") { mime = hevc }) }
        }

        rows.verify()
    }

    @Test
    fun `the low latency options log nothing unless the feature ends the first try`() {
        val rows = Rows()
        val format = { MediaFormat.createVideoFormat("video/avc", 1920, 1080) }

        rows.check(
            "FEATURE_LowLatency",
            infos("Low latency decoding mode supported (FEATURE_LowLatency)"),
        ) {
            val helper = helper()
            logged {
                helper.lowLatencyOptions(format(), codec("c2.vendor.avc") { lowLatency = true }, 0)
            }
        }
        rows.check("the other tries", emptyList()) {
            val helper = helper()
            logged {
                (1..5).forEach { helper.lowLatencyOptions(format(), codec("c2.qti.avc"), it) }
            }
        }

        rows.verify()
    }

    @Test
    fun `the first decoder logs what it skips and what it takes`() {
        val rows = Rows()

        rows.check(
            "a blacklisted decoder and a software only decoder",
            infos(
                "Skipping blacklisted decoder: OMX.google.h264.decoder",
                "Skipping software-only decoder: c2.android.avc.decoder",
                "First decoder choice is c2.vendor.avc",
            ),
        ) {
            val helper = helper()
            installCodecs(
                codec("OMX.google.h264.decoder"),
                codec("c2.android.avc.decoder") { softwareOnly = true },
                codec("c2.vendor.avc"),
            )
            logged { helper.findFirstDecoder("video/avc") }
        }
        rows.check("no decoder", emptyList()) {
            val helper = helper()
            installCodecs()
            logged { helper.findFirstDecoder("video/avc") }
        }

        rows.verify()
    }

    @Test
    fun `the probable safe decoder logs each round`() {
        val rows = Rows()

        rows.check(
            "the second decoder has FEATURE_LowLatency",
            infos(
                "Examining decoder capabilities of c2.vendor.plain (round 1)",
                "Skipping decoder that lacks FEATURE_LowLatency for round 1",
                "Examining decoder capabilities of c2.vendor.low (round 1)",
                "Low latency decoding mode supported (FEATURE_LowLatency)",
            ),
        ) {
            val helper = helper()
            installCodecs(codec("c2.vendor.plain"), codec("c2.vendor.low") { lowLatency = true })
            logged { helper.findProbableSafeDecoder("video/avc", -1) }
        }
        rows.check(
            "no decoder has it",
            infos(
                "Examining decoder capabilities of c2.vendor.plain (round 1)",
                "Skipping decoder that lacks FEATURE_LowLatency for round 1",
                "Examining decoder capabilities of c2.vendor.plain (round 2)",
            ),
        ) {
            val helper = helper()
            installCodecs(codec("c2.vendor.plain"))
            logged { helper.findProbableSafeDecoder("video/avc", -1) }
        }
        rows.check(
            "a blacklisted decoder",
            infos(
                "Examining decoder capabilities of OMX.google.h264.decoder (round 1)",
                "Skipping blacklisted decoder: OMX.google.h264.decoder",
                "Examining decoder capabilities of OMX.google.h264.decoder (round 2)",
                "Skipping blacklisted decoder: OMX.google.h264.decoder",
            ),
        ) {
            val helper = helper()
            installCodecs(codec("OMX.google.h264.decoder"))
            logged { helper.findProbableSafeDecoder("video/avc", -1) }
        }
        rows.verify()
    }

    @Test
    fun `the probable safe decoder logs the required profile and a throwing decoder`() {
        val high = CodecProfileLevel.AVCProfileHigh
        val baseline = CodecProfileLevel.AVCProfileBaseline
        val rows = Rows()

        rows.check(
            "the required profile",
            infos(
                "Examining decoder capabilities of c2.vendor.baseline (round 1)",
                "Skipping decoder that lacks FEATURE_LowLatency for round 1",
                "Examining decoder capabilities of c2.vendor.high (round 1)",
                "Skipping decoder that lacks FEATURE_LowLatency for round 1",
                "Examining decoder capabilities of c2.vendor.baseline (round 2)",
                "Decoder c2.vendor.baseline does NOT support required profile",
                "Examining decoder capabilities of c2.vendor.high (round 2)",
                "Decoder c2.vendor.high supports required profile",
            ),
        ) {
            val helper = helper()
            installCodecs(
                codec("c2.vendor.baseline") { profiles = listOf(baseline) },
                codec("c2.vendor.high") { profiles = listOf(high) },
            )
            logged { helper.findProbableSafeDecoder("video/avc", high) }
        }
        rows.check(
            "a decoder that throws falls back to the first decoder",
            infos(
                "Examining decoder capabilities of c2.vendor.buggy (round 1)",
                "First decoder choice is c2.vendor.buggy",
            ),
        ) {
            val helper = helper()
            installCodecs(codec("c2.vendor.buggy") { brokenCapabilities = true })
            logged { helper.findProbableSafeDecoder("video/avc", -1) }
        }

        rows.verify()
    }
}
