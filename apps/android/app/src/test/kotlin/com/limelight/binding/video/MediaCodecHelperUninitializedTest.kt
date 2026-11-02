package com.limelight.binding.video

import android.media.MediaFormat
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins what `MediaCodecHelper` does before `initialize` has run: every public decision throws
 * `IllegalStateException` first, also the ones that could answer without a prefix list. The rows
 * are the public entry points, one by one.
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
        rows.throwsNotInitialized("slices per frame") { helper.slicesPerFrame("omx.qcom.video") }
        rows.throwsNotInitialized("AVC reference frame invalidation") {
            helper.refFrameInvalidationAvc("omx.qcom.video", 1080)
        }
        rows.throwsNotInitialized("even for a short name") { helper.canDirectSubmit("") }

        rows.verify()
    }

    @Test
    fun `AVC reference frame invalidation throws before initialize for the b3 and b5 devices too`() {
        val rows = Rows()

        listOf("b3", "b5").forEach { device ->
            rows.throwsNotInitialized(device) {
                fresh(DeviceBuild(device = device)).refFrameInvalidationAvc("omx.qcom.video", 1080)
            }
        }

        rows.verify()
    }

    @Test
    fun `the decisions on a decoder throw before initialize`() {
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
        rows.throwsNotInitialized("HEVC whitelist of a software decoder") {
            helper.whitelistedForHevc(codec("OMX.SEC.hevc.sw.dec") { mime = "video/hevc" })
        }
        rows.throwsNotInitialized("AV1 reference frame invalidation") {
            helper.refFrameInvalidationAv1(codec("c2.vendor.av1") { mime = "video/av01" })
        }
        rows.throwsNotInitialized("AV1 whitelist") {
            helper.whitelistedForAv1(codec("c2.vendor.av1") { mime = "video/av01" })
        }
        rows.throwsNotInitialized("fused IDR frames") {
            helper.fusedIdrFrame(codec("c2.vendor.avc") { adaptivePlayback = true }, "video/avc")
        }

        rows.verify()
    }

    @Test
    fun `a decoder that could answer without a list throws before initialize`() {
        val rows = Rows()
        val hevc = "video/hevc"

        rows.throwsNotInitialized("the HEVC whitelist by the media performance class") {
            fresh(DeviceBuild(performanceClass = Build.VERSION_CODES.TIRAMISU))
                .whitelistedForHevc(codec("OMX.vendor.hevc.decoder") { mime = hevc })
        }
        rows.throwsNotInitialized("the HEVC whitelist by FEATURE_LowLatency") {
            fresh()
                .whitelistedForHevc(
                    codec("OMX.vendor.hevc.decoder") {
                        mime = hevc
                        lowLatency = true
                    }
                )
        }
        rows.throwsNotInitialized("HEVC reference frame invalidation by FEATURE_LowLatency") {
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
    fun `the low latency options throw before initialize, also when the first try ends on FEATURE_LowLatency`() {
        val helper = fresh()
        val rows = Rows()
        val format = { MediaFormat.createVideoFormat("video/avc", 1920, 1080) }

        rows.throwsNotInitialized("try 0 with the feature") {
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
    fun `finding the first decoder throws before initialize even with no decoder`() {
        val helper = fresh()
        val rows = Rows()

        rows.throwsNotInitialized("no decoder") {
            installCodecs()
            helper.findFirstDecoder("video/avc")
        }
        rows.throwsNotInitialized("no decoder of the type") {
            installCodecs(codec("c2.vendor.hevc") { mime = "video/hevc" })
            helper.findFirstDecoder("video/avc")
        }
        rows.throwsNotInitialized("a software only decoder") {
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
