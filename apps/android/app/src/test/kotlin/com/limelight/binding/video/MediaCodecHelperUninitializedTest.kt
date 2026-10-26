package com.limelight.binding.video

import android.media.MediaFormat
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Characterizes what `MediaCodecHelper` does before `initialize` has run: every decision that reads
 * a prefix list throws `IllegalStateException`, and the ones that can answer without a list answer
 * first. The rows are the public entry points, one by one.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCodecHelperUninitializedTest : MediaCodecHelperTestBase() {
    private val notInitialized = HelperHandle.NOT_INITIALIZED

    private fun Rows.throwsNotInitialized(label: String, call: () -> Unit) =
        checkThrows(label, IllegalStateException::class.java, notInitialized, call)

    @Test
    fun `the decisions on a name throw before initialize`() {
        val helper = fresh()
        val rows = Rows()

        rows.throwsNotInitialized("direct submit") { helper.canDirectSubmit("omx.qcom.video") }
        rows.throwsNotInitialized("constrained high profile") {
            helper.needsConstrainedHighProfile("omx.intel")
        }
        rows.throwsNotInitialized("baseline SPS hack") { helper.needsBaselineSpsHack("omx.intel") }
        rows.throwsNotInitialized("SPS bitstream restrictions") {
            helper.needsSpsBitstreamRestrictions("omx.qcom.video")
        }
        rows.throwsNotInitialized("slices per frame") { helper.slicesPerFrame("omx.qcom.video") }
        rows.throwsNotInitialized("AVC reference frame invalidation") {
            helper.refFrameInvalidationAvc("omx.qcom.video", 1080)
        }
        rows.throwsNotInitialized("even for a short name") { helper.canDirectSubmit("") }

        rows.verify()
    }

    @Test
    fun `AVC reference frame invalidation says no for the b3 and b5 devices before initialize`() {
        val rows = Rows()

        listOf("b3", "b5").forEach { device ->
            rows.check("$device", false) {
                fresh(DeviceBuild(device = device)).refFrameInvalidationAvc("omx.qcom.video", 1080)
            }
        }

        rows.verify()
    }

    @Test
    fun `the decisions on a decoder throw before initialize unless they answer first`() {
        val helper = fresh()
        val rows = Rows()
        val hevc = codec("OMX.vendor.hevc.decoder") { mime = "video/hevc" }

        rows.throwsNotInitialized("adaptive playback") {
            helper.adaptivePlayback(
                codec("OMX.vendor.avc.decoder") { adaptivePlayback = true },
                "video/avc",
            )
        }
        rows.throwsNotInitialized("HEVC reference frame invalidation") {
            helper.refFrameInvalidationHevc(hevc)
        }
        rows.throwsNotInitialized("HEVC whitelist") { helper.whitelistedForHevc(hevc) }
        rows.check("HEVC whitelist of a software decoder", false) {
            helper.whitelistedForHevc(codec("OMX.SEC.hevc.sw.dec") { mime = "video/hevc" })
        }
        rows.check("AV1 reference frame invalidation needs no list", false) {
            helper.refFrameInvalidationAv1(codec("c2.vendor.av1") { mime = "video/av01" })
        }
        rows.check("AV1 whitelist needs no list", false) {
            helper.whitelistedForAv1(codec("c2.vendor.av1") { mime = "video/av01" })
        }
        rows.check("fused IDR frames need no list", true) {
            helper.fusedIdrFrame(codec("c2.vendor.avc") { adaptivePlayback = true }, "video/avc")
        }

        rows.verify()
    }

    @Test
    fun `a decoder that answers without a list also answers before initialize`() {
        val rows = Rows()
        val hevc = "video/hevc"

        rows.check("the HEVC whitelist by the media performance class", true) {
            fresh(DeviceBuild(performanceClass = Build.VERSION_CODES.TIRAMISU))
                .whitelistedForHevc(codec("OMX.vendor.hevc.decoder") { mime = hevc })
        }
        rows.check("the HEVC whitelist by FEATURE_LowLatency", true) {
            fresh()
                .whitelistedForHevc(
                    codec("OMX.vendor.hevc.decoder") {
                        mime = hevc
                        lowLatency = true
                    }
                )
        }
        rows.check("HEVC reference frame invalidation by FEATURE_LowLatency", true) {
            fresh()
                .refFrameInvalidationHevc(
                    codec("OMX.vendor.hevc.decoder") {
                        mime = hevc
                        lowLatency = true
                    }
                )
        }
        rows.throwsNotInitialized("HEVC reference frame invalidation before Android 11") {
            fresh(DeviceBuild(sdk = Build.VERSION_CODES.Q))
                .refFrameInvalidationHevc(
                    codec("OMX.vendor.hevc.decoder") {
                        mime = hevc
                        lowLatency = true
                    }
                )
        }

        rows.verify()
    }

    @Test
    fun `the low latency options throw before initialize unless the first try ends on FEATURE_LowLatency`() {
        val helper = fresh()
        val rows = Rows()
        val format = { MediaFormat.createVideoFormat("video/avc", 1920, 1080) }

        rows.check("try 0 with the feature", true) {
            helper.lowLatencyOptions(format(), codec("c2.vendor.avc") { lowLatency = true }, 0)
        }
        rows.throwsNotInitialized("try 0 without it") {
            helper.lowLatencyOptions(format(), codec("c2.vendor.avc"), 0)
        }
        rows.throwsNotInitialized("try 1 with the feature") {
            helper.lowLatencyOptions(format(), codec("c2.vendor.avc") { lowLatency = true }, 1)
        }
        rows.throwsNotInitialized("try 5") {
            helper.lowLatencyOptions(format(), codec("c2.vendor.avc"), 5)
        }

        rows.verify()
    }

    @Test
    fun `finding the first decoder throws before initialize only when a decoder is up for the blacklist`() {
        val helper = fresh()
        val rows = Rows()

        rows.check("no decoder", null) {
            installCodecs()
            helper.findFirstDecoder("video/avc")
        }
        rows.check("no decoder of the type", null) {
            installCodecs(codec("c2.vendor.hevc") { mime = "video/hevc" })
            helper.findFirstDecoder("video/avc")
        }
        rows.check("a software only decoder is blacklisted before the list", null) {
            installCodecs(codec("c2.android.avc.decoder") { softwareOnly = true })
            helper.findFirstDecoder("video/avc")
        }
        rows.throwsNotInitialized("a hardware decoder") {
            installCodecs(codec("c2.vendor.avc"))
            helper.findFirstDecoder("video/avc")
        }

        rows.verify()
    }

    @Test
    fun `finding the probable safe decoder throws before initialize even with no decoder`() {
        val helper = fresh()
        val rows = Rows()

        rows.throwsNotInitialized("no decoder") {
            installCodecs()
            helper.findProbableSafeDecoder("video/avc", -1)
        }
        rows.throwsNotInitialized("a decoder with FEATURE_LowLatency") {
            installCodecs(codec("c2.vendor.avc") { lowLatency = true })
            helper.findProbableSafeDecoder("video/avc", -1)
        }
        rows.throwsNotInitialized("a profile that no decoder has") {
            installCodecs(codec("c2.vendor.avc"))
            helper.findProbableSafeDecoder("video/hevc", 1)
        }

        rows.verify()
    }
}
