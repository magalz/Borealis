package com.limelight.binding.video

import android.media.MediaCodecInfo.CodecProfileLevel
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import org.junit.Test

/**
 * Characterizes what the renderer tells the native layer about itself before the stream starts: its
 * capability bits, the HDR support of its decoders, and its preferred colour space and range. All
 * of it is decided in the constructor.
 */
class RendererCapabilitiesTest : RendererTestBase() {
    private val slicesShift = 24

    private fun avc(name: String) = codec(name) { mime = "video/avc" }

    private fun hevc(name: String, profiles: List<Int>? = null) =
        codec(name) {
            mime = "video/hevc"
            if (profiles != null) this.profiles = profiles
        }

    private fun av1(name: String, lowLatency: Boolean = false, profiles: List<Int>? = null) =
        codec(name) {
            mime = "video/av01"
            this.lowLatency = lowLatency
            if (profiles != null) this.profiles = profiles
        }

    // The bits

    @Test
    fun `a decoder that wants nothing special asks for one slice and no extras`() {
        install(avc("vendor.test.avc.decoder"))

        val capabilities = renderer().getCapabilities()

        assertThat(capabilities).isEqualTo(1 shl slicesShift)
    }

    @Test
    fun `without any decoder no slice count is asked for`() {
        install()

        val renderer = renderer()

        assertThat(renderer.getCapabilities()).isEqualTo(0)
        assertThat(log.lines()).contains("INFO: Requesting 0 slices per frame")
    }

    @Test
    fun `a Codec2 decoder can be fed directly`() {
        install(avc("c2.test.avc.decoder"))

        val renderer = renderer()

        assertThat(renderer.getCapabilities())
            .isEqualTo((1 shl slicesShift) or MoonBridge.CAPABILITY_DIRECT_SUBMIT)
        assertThat(log.lines()).contains("INFO: Decoder c2.test.avc.decoder will use direct submit")
    }

    @Test
    fun `a Qualcomm decoder gets reference frame invalidation for H264`() {
        install(avc("c2.qti.avc.decoder"))

        val renderer = renderer()

        assertThat(renderer.getCapabilities())
            .isEqualTo(
                (1 shl slicesShift) or
                    MoonBridge.CAPABILITY_DIRECT_SUBMIT or
                    MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC
            )
        assertThat(log.lines())
            .contains("INFO: Decoder c2.qti.avc.decoder will use reference frame invalidation for AVC")
    }

    @Test
    fun `a Qualcomm HEVC decoder gets reference frame invalidation for HEVC`() {
        install(avc("vendor.test.avc.decoder"), hevc("c2.qti.hevc.decoder"))

        val renderer = renderer()

        assertThat(renderer.getCapabilities())
            .isEqualTo(
                (1 shl slicesShift) or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC
            )
        assertThat(log.lines())
            .contains(
                "INFO: Decoder c2.qti.hevc.decoder will use reference frame invalidation for HEVC"
            )
    }

    @Test
    fun `an AV1 decoder with the low latency feature gets reference frame invalidation for AV1`() {
        install(avc("vendor.test.avc.decoder"), av1("vendor.test.av1.decoder", lowLatency = true))

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.getCapabilities())
            .isEqualTo(
                (1 shl slicesShift) or MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1
            )
        assertThat(log.lines())
            .contains(
                "INFO: Decoder vendor.test.av1.decoder will use reference frame invalidation for AV1"
            )
    }

    @Test
    fun `an AV1 decoder without the low latency feature gets none`() {
        install(avc("vendor.test.avc.decoder"), av1("vendor.test.av1.decoder"))

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.getCapabilities()).isEqualTo(1 shl slicesShift)
    }

    @Test
    fun `the slice count is the larger of what the H264 and the HEVC decoders want`() {
        install(
            avc("vendor.test.avc.decoder"),
            // The decoders of Android's own codec2 stack want four slices for multithreading.
            hevc("c2.android.hevc.decoder"),
        )

        val renderer = renderer()

        assertThat(renderer.getCapabilities() shr slicesShift).isEqualTo(4)
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Decoder vendor.test.avc.decoder wants 1 slices per frame",
                "INFO: Decoder c2.android.hevc.decoder wants 4 slices per frame",
                "INFO: Requesting 4 slices per frame",
            )
    }

    @Test
    fun `an H264 decoder that wants four slices makes the request four`() {
        install(avc("c2.android.avc.decoder"))

        val renderer = renderer()

        assertThat(renderer.getCapabilities() shr slicesShift).isEqualTo(4)
    }

    // Reference frame invalidation on devices where it is risky

    @Test
    fun `a low end Snapdragon does not invalidate reference frames at 1080p`() {
        device(gl = GlFacts(renderer = "Adreno (TM) 405"))
        install(avc("c2.qti.avc.decoder"))

        val renderer = renderer(prefs { height = 1080 })

        assertThat(renderer.getCapabilities() and MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC)
            .isEqualTo(0)
    }

    @Test
    fun `a low end Snapdragon invalidates reference frames at 720p`() {
        device(gl = GlFacts(renderer = "Adreno (TM) 405"))
        install(avc("c2.qti.avc.decoder"))

        val renderer = renderer(prefs { height = 720 })

        assertThat(renderer.getCapabilities() and MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC)
            .isEqualTo(MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC)
    }

    @Test
    fun `an odd number of earlier crashes turns reference frame invalidation off`() {
        install(avc("c2.qti.avc.decoder"), hevc("c2.qti.hevc.decoder"))

        val renderer = renderer(crashCount = 1)

        assertThat(renderer.getCapabilities())
            .isEqualTo((1 shl slicesShift) or MoonBridge.CAPABILITY_DIRECT_SUBMIT)
        assertThat(log.lines()).contains("WARNING: Disabling RFI due to previous crash")
    }

    @Test
    fun `an even number of earlier crashes keeps reference frame invalidation`() {
        install(avc("c2.qti.avc.decoder"), hevc("c2.qti.hevc.decoder"))

        for (crashes in listOf(0, 2, 4)) {
            val capabilities = renderer(crashCount = crashes).getCapabilities()

            assertThat(capabilities and MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC)
                .isNotEqualTo(0)
            assertThat(capabilities and MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC)
                .isNotEqualTo(0)
        }
        assertThat(log.lines()).doesNotContain("WARNING: Disabling RFI due to previous crash")
    }

    @Test
    fun `earlier crashes leave reference frame invalidation for AV1 alone`() {
        install(avc("vendor.test.avc.decoder"), av1("vendor.test.av1.decoder", lowLatency = true))

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 }, crashCount = 1)

        assertThat(renderer.getCapabilities() and MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1)
            .isEqualTo(MoonBridge.CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1)
    }

    // HDR

    @Test
    fun `an HEVC decoder that lists Main10 HDR10 supports it`() {
        install(
            avc("vendor.test.avc.decoder"),
            hevc(
                "c2.test.hevc.decoder",
                listOf(CodecProfileLevel.HEVCProfileMain, CodecProfileLevel.HEVCProfileMain10HDR10),
            ),
        )

        val renderer = renderer()

        assertThat(renderer.isHevcMain10Hdr10Supported()).isTrue()
        assertThat(log.lines())
            .contains("INFO: HEVC decoder c2.test.hevc.decoder supports HEVC Main10 HDR10")
    }

    @Test
    fun `an HEVC decoder that lists only Main does not support Main10 HDR10`() {
        install(avc("vendor.test.avc.decoder"), hevc("c2.test.hevc.decoder"))

        assertThat(renderer().isHevcMain10Hdr10Supported()).isFalse()
    }

    @Test
    fun `without an HEVC decoder Main10 HDR10 is not supported`() {
        install(avc("vendor.test.avc.decoder"))

        assertThat(renderer().isHevcMain10Hdr10Supported()).isFalse()
    }

    @Test
    fun `an AV1 decoder that lists Main10 HDR10 supports it`() {
        install(
            avc("vendor.test.avc.decoder"),
            av1(
                "vendor.test.av1.decoder",
                profiles = listOf(CodecProfileLevel.AV1ProfileMain8, CodecProfileLevel.AV1ProfileMain10HDR10),
            ),
        )

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.isAv1Main10Supported()).isTrue()
        assertThat(log.lines())
            .contains("INFO: AV1 decoder vendor.test.av1.decoder supports AV1 Main 10 HDR10")
    }

    @Test
    fun `an AV1 decoder that lists only Main8 does not support Main 10`() {
        install(
            avc("vendor.test.avc.decoder"),
            av1("vendor.test.av1.decoder", profiles = listOf(CodecProfileLevel.AV1ProfileMain8)),
        )

        val renderer = renderer(prefs { videoFormat = FormatOption.FORCE_AV1 })

        assertThat(renderer.isAv1Main10Supported()).isFalse()
    }

    @Test
    fun `without an AV1 decoder Main 10 is not supported`() {
        install(avc("vendor.test.avc.decoder"))

        assertThat(renderer().isAv1Main10Supported()).isFalse()
    }

    // Colour

    @Test
    fun `the preferred colour space is Rec 709`() {
        install(avc("vendor.test.avc.decoder"))

        assertThat(renderer().getPreferredColorSpace()).isEqualTo(MoonBridge.COLORSPACE_REC_709)
    }

    @Test
    fun `the preferred colour range is limited unless the user asks for the full range`() {
        install(avc("vendor.test.avc.decoder"))

        assertThat(renderer(prefs { fullRange = false }).getPreferredColorRange())
            .isEqualTo(MoonBridge.COLOR_RANGE_LIMITED)
        assertThat(renderer(prefs { fullRange = true }).getPreferredColorRange())
            .isEqualTo(MoonBridge.COLOR_RANGE_FULL)
    }
}
