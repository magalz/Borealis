package io.github.magalz.borealis.stream.game

import android.media.MediaCodecInfo.CodecProfileLevel
import android.view.Display
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.video.codec
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test

/** The video formats that `Game.onCreate` offers the host, and the warnings it shows. */
internal class GameFormatsTest : GameTestBase() {
    private val hevc = codec("c2.test.hevc.decoder") { mime = "video/hevc" }
    private val softwareHevc = codec("c2.test.hevc.sw.decoder") { mime = "video/hevc" }
    private val hevcMain10 =
        codec("c2.test.hevc.main10.decoder") {
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
