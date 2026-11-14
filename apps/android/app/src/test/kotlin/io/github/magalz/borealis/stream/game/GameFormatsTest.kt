package io.github.magalz.borealis.stream.game

import android.media.MediaCodecInfo.CodecProfileLevel
import android.view.Display
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.video.codec
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.robolectric.shadows.ShadowToast

/** The video formats that `Game.onCreate` offers the host, and the warnings it shows. */
internal class GameFormatsTest : GameTestBase() {
    private val hevc = codec("c2.test.hevc.decoder") { mime = "video/hevc" }
    private val softwareHevc = codec("c2.test.hevc.sw.decoder") { mime = "video/hevc" }
    private val hevcMain10 =
        codec("c2.test.hevc.main10.decoder") {
            mime = "video/hevc"
            profiles = listOf(CodecProfileLevel.HEVCProfileMain10HDR10)
        }
    private val softwareHevcMain10 =
        codec("c2.test.hevc.sw.main10.decoder") {
            mime = "video/hevc"
            profiles = listOf(CodecProfileLevel.HEVCProfileMain10HDR10)
        }
    private val av1 = codec("c2.test.av1.decoder") { mime = "video/av01" }
    private val av1Main10 =
        codec("c2.test.av1.main10.decoder") {
            mime = "video/av01"
            profiles = listOf(CodecProfileLevel.AV1ProfileMain10HDR10)
        }
    private val avc = codec(AVC_DECODER)

    private val offered: Int
        get() = rig.streamConfiguration.supportedVideoFormats

    @Test
    fun `H264 is always offered, and HEVC too when a hardware decoder for it exists`() {
        rig.decoders(avc, hevc)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
    }

    @Test
    fun `HEVC is not offered on a software decoder, unless the setting forces it`() {
        rig.decoders(avc, softwareHevc)
        rig.create()
        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
    }

    @Test
    fun `forcing HEVC offers it even on a software decoder`() {
        rig.preference("video_format", "forceh265")
        rig.decoders(avc, softwareHevc)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts()
    }

    @Test
    fun `forcing HEVC without a decoder warns that there is none`() {
        rig.preference("video_format", "forceh265")

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        assertToasts("No HEVC decoder found")
    }

    @Test
    fun `never using HEVC offers H264 alone`() {
        rig.preference("video_format", "neverh265")
        rig.decoders(avc, hevc)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
    }

    @Test
    fun `forcing AV1 offers it with an AV1 decoder`() {
        rig.preference("video_format", "forceav1")
        rig.decoders(avc, av1)

        rig.create()

        assertThat(offered)
            .isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_AV1_MAIN8)
    }

    @Test
    fun `forcing AV1 without a decoder warns that there is none`() {
        rig.preference("video_format", "forceav1")

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        assertToasts("No AV1 decoder found")
    }

    @Test
    fun `forcing AV1 with a decoder for it warns about nothing`() {
        rig.preference("video_format", "forceav1")
        rig.decoders(avc, av1)

        rig.create()

        assertToasts()
    }

    @Test
    fun `forcing AV1 warns that there is no decoder even when one for HEVC exists`() {
        rig.preference("video_format", "forceav1")
        rig.decoders(avc, hevc)

        rig.create()

        assertToasts("No AV1 decoder found")
    }

    @Test
    fun `without the HDR setting AV1 Main10 is not offered either`() {
        rig.preference("video_format", "forceav1")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, av1Main10)

        rig.create()

        assertThat(offered)
            .isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_AV1_MAIN8)
    }

    @Test
    fun `HDR on a display without HDR10 warns and offers no 10-bit format`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceh265")
        rig.decoders(avc, hevcMain10)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts("Display does not support HDR10")
    }

    @Test
    fun `HDR on a display with HDR10 and a Main10 HEVC decoder offers HEVC Main10`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, hevcMain10)

        rig.create()

        assertThat(offered)
            .isEqualTo(
                MoonBridge.VIDEO_FORMAT_H264 or
                    MoonBridge.VIDEO_FORMAT_H265 or
                    MoonBridge.VIDEO_FORMAT_H265_MAIN10
            )
        assertToasts()
    }

    @Test
    fun `HDR with a decoder that has no HDR10 profile warns and drops the 10-bit format`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, hevc)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts("Decoder does not support HDR10 profile")
    }

    @Test
    fun `HDR with an AV1 Main10 decoder offers AV1 Main10`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceav1")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, av1Main10)

        rig.create()

        assertThat(offered)
            .isEqualTo(
                MoonBridge.VIDEO_FORMAT_H264 or
                    MoonBridge.VIDEO_FORMAT_AV1_MAIN8 or
                    MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            )
    }

    // The warnings come in one order: the display, the decoder profile of HDR, then the forced
    // format. The toasts keep no list: the last one shown tells which came last.

    @Test
    fun `the warning about the display comes before the one about a forced HEVC without decoder`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceh265")

        rig.create()

        assertToasts("Display does not support HDR10", "No HEVC decoder found")
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("No HEVC decoder found")
    }

    @Test
    fun `the warning about the HDR10 profile comes before the one about a forced HEVC`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceh265")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)

        rig.create()

        assertToasts("Decoder does not support HDR10 profile", "No HEVC decoder found")
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("No HEVC decoder found")
    }

    @Test
    fun `the warning about the HDR10 profile comes before the one about a forced AV1`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceav1")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)

        rig.create()

        assertToasts("Decoder does not support HDR10 profile", "No AV1 decoder found")
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("No AV1 decoder found")
    }

    // The decoder is built with what the display says about HDR, before the decoder is asked.

    @Test
    fun `a display with HDR10 makes the decoder look for HEVC even on a decoder that is not trusted`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, softwareHevcMain10)

        rig.create()

        assertThat(offered)
            .isEqualTo(
                MoonBridge.VIDEO_FORMAT_H264 or
                    MoonBridge.VIDEO_FORMAT_H265 or
                    MoonBridge.VIDEO_FORMAT_H265_MAIN10
            )
        assertToasts()
    }

    @Test
    fun `a display without HDR10 does not make the decoder look for HEVC on a decoder not trusted`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.decoders(avc, softwareHevcMain10)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        assertToasts("Display does not support HDR10")
    }

    @Test
    fun `the decoder was built for HDR even when it then turns out not to support the profile`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, softwareHevc)

        rig.create()

        assertThat(offered).isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts("Decoder does not support HDR10 profile")
    }

    @Test
    fun `HDR offers Main10 of AV1 only, when the HEVC decoder beside it has no Main10 profile`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceav1")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, hevc, av1Main10)

        rig.create()

        assertThat(offered)
            .isEqualTo(
                MoonBridge.VIDEO_FORMAT_H264 or
                    MoonBridge.VIDEO_FORMAT_H265 or
                    MoonBridge.VIDEO_FORMAT_AV1_MAIN8 or
                    MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            )
        assertToasts()
    }

    @Test
    fun `HDR offers Main10 of HEVC only, when the AV1 decoder beside it has no Main10 profile`() {
        rig.preference("checkbox_enable_hdr", true)
        rig.preference("video_format", "forceav1")
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(avc, hevcMain10, av1)

        rig.create()

        assertThat(offered)
            .isEqualTo(
                MoonBridge.VIDEO_FORMAT_H264 or
                    MoonBridge.VIDEO_FORMAT_H265 or
                    MoonBridge.VIDEO_FORMAT_H265_MAIN10 or
                    MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            )
        assertToasts()
    }

    @Test
    fun `a metered connection is warned about`() {
        rig.network(metered = true)

        rig.create()

        assertToasts("Warning: Your active network connection is metered!")
    }

    @Test
    fun `the setting that disables warnings silences the metered connection warning`() {
        rig.network(metered = true)
        rig.preference("checkbox_disable_warnings", true)

        rig.create()

        assertToasts()
    }
}
