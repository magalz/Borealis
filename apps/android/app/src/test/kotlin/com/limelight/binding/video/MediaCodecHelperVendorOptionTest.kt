package com.limelight.binding.video

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes how `MediaCodecHelper` probes the vendor parameters of a decoder from Android 12:
 * it creates the codec by name, looks for one of the four known low latency options in any case,
 * and releases the codec. A known option counts as low latency support for HEVC and AV1 reference
 * frame invalidation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowVendorParametersMediaCodec::class])
class MediaCodecHelperVendorOptionTest : MediaCodecHelperTestBase() {
    private val hevc = "video/hevc"

    // Robolectric does not call the resetter of a shadow that a test adds with @Config.
    @Before fun resetShadow() = ShadowVendorParametersMediaCodec.forgetEverything()

    private val av1 = "video/av01"

    private fun unlistedHevc() = codec("OMX.vendor.hevc.decoder") { mime = hevc }

    private fun unlistedAv1() = codec("c2.vendor.av1.decoder") { mime = av1 }

    @Test
    fun `a known vendor option counts as low latency support`() {
        val helper = helper()
        val rows = Rows()

        listOf(
                "vendor.qti-ext-dec-low-latency.enable",
                "vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req",
                "vendor.rtc-ext-dec-low-latency.enable",
                "vendor.low-latency.enable",
            )
            .forEach { option ->
                rows.check("HEVC with $option", true) {
                    ShadowVendorParametersMediaCodec.parameters = listOf(option)
                    helper.refFrameInvalidationHevc(unlistedHevc())
                }
                rows.check("AV1 with $option", true) {
                    ShadowVendorParametersMediaCodec.parameters = listOf(option)
                    helper.refFrameInvalidationAv1(unlistedAv1())
                }
            }

        rows.verify()
    }

    @Test
    fun `the vendor options are compared without the case and among others`() {
        val helper = helper()
        val rows = Rows()

        rows.check("the case", true) {
            ShadowVendorParametersMediaCodec.parameters = listOf("VENDOR.Low-Latency.ENABLE")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("the known option is not the first", true) {
            ShadowVendorParametersMediaCodec.parameters =
                listOf(
                    "vendor.qti-ext-dec-picture-order.enable",
                    "vendor.other.option",
                    "vendor.low-latency.enable",
                )
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("another option", false) {
            ShadowVendorParametersMediaCodec.parameters =
                listOf("vendor.qti-ext-dec-picture-order.enable")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("a longer name is another option", false) {
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.low-latency.enable.extra")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("no option", false) {
            ShadowVendorParametersMediaCodec.parameters = emptyList()
            helper.refFrameInvalidationAv1(unlistedAv1())
        }

        rows.verify()
    }

    @Test
    fun `without a known option HEVC falls back to the list`() {
        val helper = helper()
        val rows = Rows()

        rows.check("an unlisted decoder", false) {
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.other.option")
            helper.refFrameInvalidationHevc(unlistedHevc())
        }
        rows.check("a listed decoder", true) {
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.other.option")
            helper.refFrameInvalidationHevc(codec("OMX.Exynos.HEVC.Decoder") { mime = hevc })
        }

        rows.verify()
    }

    @Test
    fun `the vendor parameters are probed from Android 12 only`() {
        val rows = Rows()

        rows.check("Android 11", false) {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.R))
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.low-latency.enable")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("Android 12", true) {
            val helper = helper(DeviceBuild(sdk = Build.VERSION_CODES.S))
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.low-latency.enable")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }

        rows.verify()
    }

    @Test
    fun `a probed codec is released and a codec that throws is tolerated`() {
        val helper = helper()
        val rows = Rows()

        rows.check("a codec that lists its parameters is released", 1) {
            ShadowVendorParametersMediaCodec.parameters = listOf("vendor.low-latency.enable")
            helper.refFrameInvalidationAv1(unlistedAv1())
            ShadowVendorParametersMediaCodec.released
        }
        rows.check("a codec without a known option is released", 2) {
            ShadowVendorParametersMediaCodec.parameters = emptyList()
            helper.refFrameInvalidationAv1(unlistedAv1())
            ShadowVendorParametersMediaCodec.released
        }
        rows.check("a codec that throws is released and counts as having no option", false) {
            ShadowVendorParametersMediaCodec.failure = IllegalStateException("buggy codec")
            helper.refFrameInvalidationAv1(unlistedAv1())
        }
        rows.check("the codec that threw is released", 3) {
            ShadowVendorParametersMediaCodec.released
        }
        rows.check("HEVC falls back to the list when the codec throws", true) {
            helper.refFrameInvalidationHevc(codec("c2.exynos.hevc.decoder") { mime = hevc })
        }

        rows.verify()
    }

    @Test
    fun `FEATURE_LowLatency answers before any codec is created`() {
        val helper = helper()
        val rows = Rows()

        rows.check("HEVC with the feature", true) {
            ShadowVendorParametersMediaCodec.parameters = emptyList()
            helper.refFrameInvalidationHevc(
                codec("OMX.vendor.hevc.decoder") {
                    mime = hevc
                    lowLatency = true
                }
            )
        }
        rows.check("AV1 with the feature", true) {
            helper.refFrameInvalidationAv1(
                codec("c2.vendor.av1.decoder") {
                    mime = av1
                    lowLatency = true
                }
            )
        }
        rows.check("no codec was created", 0) { ShadowVendorParametersMediaCodec.released }

        rows.verify()
    }

    @Test
    fun `a known vendor option is logged with the spelling of the codec`() {
        val helper = helper()
        ShadowVendorParametersMediaCodec.parameters =
            listOf("vendor.other.option", "VENDOR.Low-Latency.ENABLE")

        val records =
            LimeLogCapture().start().use { capture ->
                helper.refFrameInvalidationAv1(unlistedAv1())
                capture.entries.toList()
            }

        assertThat(records)
            .containsExactly(
                Level.INFO to
                    "c2.vendor.av1.decoder supports known low latency option: VENDOR.Low-Latency.ENABLE",
                Level.INFO to "Enabling AV1 RFI based on low latency option support",
            )
            .inOrder()
    }
}
