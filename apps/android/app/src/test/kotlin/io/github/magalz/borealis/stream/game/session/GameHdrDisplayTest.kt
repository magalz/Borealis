package io.github.magalz.borealis.stream.game.session

import android.media.MediaCodecInfo.CodecProfileLevel
import android.view.Display
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.video.codec
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.stream.game.AVC_DECODER
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.assertToasts
import io.github.magalz.borealis.stream.game.streamConfiguration
import org.junit.Test

/**
 * The checks of the HDR setting against the display, in `Game.onCreate`: the display must list
 * HDR10 among the HDR types it supports, wherever in the list; the video formats that the stream
 * offers follow.
 */
internal class GameHdrDisplayTest : GameTestBase() {
    private val hevcMain10 =
        codec("c2.test.hevc.main10.decoder") {
            mime = "video/hevc"
            profiles = listOf(CodecProfileLevel.HEVCProfileMain10HDR10)
        }

    private val withHdr10 =
        MoonBridge.VIDEO_FORMAT_H264 or
            MoonBridge.VIDEO_FORMAT_H265 or
            MoonBridge.VIDEO_FORMAT_H265_MAIN10

    private fun streamWithHdr(vararg displayTypes: Int) {
        rig.preference("checkbox_enable_hdr", true)
        rig.displayHdr(*displayTypes)
        rig.decoders(codec(AVC_DECODER), hevcMain10)
        rig.create()
    }

    @Test
    fun `a display that lists HDR10 after other types supports it`() {
        streamWithHdr(
            Display.HdrCapabilities.HDR_TYPE_HLG,
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
            Display.HdrCapabilities.HDR_TYPE_HDR10,
        )

        assertThat(rig.streamConfiguration.supportedVideoFormats).isEqualTo(withHdr10)
        assertToasts()
    }

    @Test
    fun `a display that lists HDR10 first supports it`() {
        streamWithHdr(
            Display.HdrCapabilities.HDR_TYPE_HDR10,
            Display.HdrCapabilities.HDR_TYPE_HLG,
        )

        assertThat(rig.streamConfiguration.supportedVideoFormats).isEqualTo(withHdr10)
        assertToasts()
    }

    @Test
    fun `a display with other HDR types and no HDR10 does not support it`() {
        streamWithHdr(
            Display.HdrCapabilities.HDR_TYPE_HLG,
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
        )

        assertThat(rig.streamConfiguration.supportedVideoFormats)
            .isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts("Display does not support HDR10")
    }

    @Test
    fun `without the HDR setting the display is not asked about HDR at all`() {
        rig.displayHdr(Display.HdrCapabilities.HDR_TYPE_HDR10)
        rig.decoders(codec(AVC_DECODER), hevcMain10)

        rig.create()

        assertThat(rig.streamConfiguration.supportedVideoFormats)
            .isEqualTo(MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265)
        assertToasts()
    }
}
