package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Test

class StreamSessionFactoryTest {
    private fun prefs(
        trackpad: Boolean = true,
        mouseEmulation: Boolean = false,
        mouseNavButtons: Boolean = false,
    ) =
        PreferenceConfiguration().apply {
            touchscreenTrackpad = trackpad
            this.mouseEmulation = mouseEmulation
            this.mouseNavButtons = mouseNavButtons
        }

    @Test
    fun `the performance overlay starts as the three-state setting says`() {
        fun overlay(mode: String) = StreamSessionFactory.defaults(prefs(), mode, true, true).overlay

        assertThat(overlay(Settings.PERF_OVERLAY_OFF)).isEqualTo(OverlayMode.OFF)
        assertThat(overlay(Settings.PERF_OVERLAY_COMPACT)).isEqualTo(OverlayMode.COMPACT)
        assertThat(overlay(Settings.PERF_OVERLAY_DETAILED)).isEqualTo(OverlayMode.DETAILED)
    }

    @Test
    fun `an unknown performance overlay value starts with the overlay off`() {
        assertThat(StreamSessionFactory.defaults(prefs(), "loud", true, true).overlay)
            .isEqualTo(OverlayMode.OFF)
    }

    @Test
    fun `the touch mode follows the trackpad preference, and is absent without a touch screen`() {
        assertThat(
                StreamSessionFactory.defaults(prefs(trackpad = true), "off", true, true).touchMode
            )
            .isEqualTo(TouchMode.TRACKPAD)
        assertThat(
                StreamSessionFactory.defaults(prefs(trackpad = false), "off", true, true).touchMode
            )
            .isEqualTo(TouchMode.DIRECT)
        assertThat(StreamSessionFactory.defaults(prefs(), "off", false, true).touchMode).isNull()
    }

    @Test
    fun `the gamepad as a mouse follows the mouse emulation preference`() {
        assertThat(
                StreamSessionFactory.defaults(prefs(mouseEmulation = true), "off", true, true)
                    .gamepadMouse
            )
            .isTrue()
        assertThat(
                StreamSessionFactory.defaults(prefs(mouseEmulation = false), "off", true, true)
                    .gamepadMouse
            )
            .isFalse()
    }

    @Test
    fun `the mouse navigation buttons follow their preference, which is off by default`() {
        assertThat(StreamSessionFactory.defaults(prefs(), "off", true, true).mouseNavButtons)
            .isFalse()
        assertThat(
                StreamSessionFactory.defaults(prefs(mouseNavButtons = true), "off", true, true)
                    .mouseNavButtons
            )
            .isTrue()
    }

    @Test
    fun `the menu hint is what the caller says`() {
        assertThat(StreamSessionFactory.defaults(prefs(), "off", true, true).showMenuHint).isTrue()
        assertThat(StreamSessionFactory.defaults(prefs(), "off", true, false).showMenuHint)
            .isFalse()
    }

    private fun sized(width: Int, height: Int, bitrate: Int) =
        prefs().apply {
            this.width = width
            this.height = height
            this.bitrate = bitrate
        }

    @Test
    fun `the session info names the negotiated codec and tells HDR`() {
        val h264 =
            StreamSessionFactory.sessionInfo(
                sized(1920, 1080, 20_000),
                60,
                MoonBridge.VIDEO_FORMAT_H264,
                null,
            )
        val hevcHdr =
            StreamSessionFactory.sessionInfo(
                sized(3840, 2160, 80_000),
                120,
                MoonBridge.VIDEO_FORMAT_H265_MAIN10,
                null,
            )
        val av1 =
            StreamSessionFactory.sessionInfo(
                sized(1280, 720, 5_000),
                30,
                MoonBridge.VIDEO_FORMAT_AV1_MAIN8,
                null,
            )

        assertThat(h264).isEqualTo(SessionInfo(1920, 1080, 60, "H.264", hdr = false, null, 20_000))
        assertThat(hevcHdr)
            .isEqualTo(SessionInfo(3840, 2160, 120, "HEVC", hdr = true, null, 80_000))
        assertThat(av1.codec).isEqualTo("AV1")
        assertThat(av1.hdr).isFalse()
    }

    @Test
    fun `the session info names the decoder the renderer picked`() {
        val info =
            StreamSessionFactory.sessionInfo(
                sized(1920, 1080, 20_000),
                60,
                MoonBridge.VIDEO_FORMAT_H265,
                "c2.qti.hevc.decoder",
            )

        assertThat(info.decoder).isEqualTo("c2.qti.hevc.decoder")
    }

    @Test
    fun `a decoder that is not known leaves the name out`() {
        val info =
            StreamSessionFactory.sessionInfo(
                sized(1920, 1080, 20_000),
                60,
                MoonBridge.VIDEO_FORMAT_H264,
                null,
            )

        assertThat(info.decoder).isNull()
    }

    @Test
    fun `a launch retry names the category the stack reported by ordinal`() {
        assertThat(StreamSessionFactory.launchRetry(LaunchCategory.PENDING.ordinal, 1, 3, 2000))
            .isEqualTo(LaunchRetry(LaunchCategory.PENDING, 1, 3, 2000))
        assertThat(StreamSessionFactory.launchRetry(99, 2, 3, 500).category).isNull()
    }

    @Test
    fun `the stage that names the app is still connecting, the stream stages prepare the display`() {
        // Starting the app is the stage of launchStarting, when /launch or /resume is sent.
        assertThat(StreamSessionFactory.startStage("Desktop", "Desktop"))
            .isEqualTo(StartStage.CONNECTING)
        assertThat(StreamSessionFactory.startStage("RTSP handshake", "Desktop"))
            .isEqualTo(StartStage.PREPARING_DISPLAY)
        assertThat(StreamSessionFactory.startStage("video stream", "Desktop"))
            .isEqualTo(StartStage.PREPARING_DISPLAY)
    }
}
