package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.nativecore.LaunchCategory
import org.junit.Test

class StreamSessionFactoryTest {
    private fun prefs(
        perfOverlay: Boolean = false,
        trackpad: Boolean = true,
        mouseEmulation: Boolean = false,
    ) =
        PreferenceConfiguration().apply {
            enablePerfOverlay = perfOverlay
            touchscreenTrackpad = trackpad
            this.mouseEmulation = mouseEmulation
        }

    @Test
    fun `the performance overlay starts detailed when the user turned it on, off otherwise`() {
        assertThat(StreamSessionFactory.defaults(prefs(perfOverlay = true), true, true).overlay)
            .isEqualTo(OverlayMode.DETAILED)
        assertThat(StreamSessionFactory.defaults(prefs(perfOverlay = false), true, true).overlay)
            .isEqualTo(OverlayMode.OFF)
    }

    @Test
    fun `the touch mode follows the trackpad preference, and is absent without a touch screen`() {
        assertThat(StreamSessionFactory.defaults(prefs(trackpad = true), true, true).touchMode)
            .isEqualTo(TouchMode.TRACKPAD)
        assertThat(StreamSessionFactory.defaults(prefs(trackpad = false), true, true).touchMode)
            .isEqualTo(TouchMode.DIRECT)
        assertThat(StreamSessionFactory.defaults(prefs(), false, true).touchMode).isNull()
    }

    @Test
    fun `the gamepad as a mouse follows the mouse emulation preference`() {
        assertThat(StreamSessionFactory.defaults(prefs(mouseEmulation = true), true, true).gamepadMouse)
            .isTrue()
        assertThat(StreamSessionFactory.defaults(prefs(mouseEmulation = false), true, true).gamepadMouse)
            .isFalse()
    }

    @Test
    fun `the menu hint is what the caller says`() {
        assertThat(StreamSessionFactory.defaults(prefs(), true, true).showMenuHint).isTrue()
        assertThat(StreamSessionFactory.defaults(prefs(), true, false).showMenuHint).isFalse()
    }

    @Test
    fun `the session info names the negotiated codec and tells HDR`() {
        val h264 = StreamSessionFactory.sessionInfo(1920, 1080, 60, MoonBridge.VIDEO_FORMAT_H264, 20_000)
        val hevcHdr =
            StreamSessionFactory.sessionInfo(3840, 2160, 120, MoonBridge.VIDEO_FORMAT_H265_MAIN10, 80_000)
        val av1 = StreamSessionFactory.sessionInfo(1280, 720, 30, MoonBridge.VIDEO_FORMAT_AV1_MAIN8, 5_000)

        assertThat(h264)
            .isEqualTo(SessionInfo(1920, 1080, 60, "H.264", hdr = false, null, 20_000))
        assertThat(hevcHdr)
            .isEqualTo(SessionInfo(3840, 2160, 120, "HEVC", hdr = true, null, 80_000))
        assertThat(av1.codec).isEqualTo("AV1")
        assertThat(av1.hdr).isFalse()
    }

    @Test
    fun `a failure carries the error code and stage as its raw code`() {
        assertThat(StreamSessionFactory.failure("RTSP handshake", -1, null))
            .isEqualTo(StreamFailure(FailureKind.GENERIC, "Error -1 · RTSP handshake", null))
    }

    @Test
    fun `a failure without a stage has the error code only, large codes in hex`() {
        assertThat(StreamSessionFactory.failure(null, 5, "UDP 47998").rawCode).isEqualTo("Error 5")
        assertThat(StreamSessionFactory.failure(null, 5, "UDP 47998").portHint).isEqualTo("UDP 47998")
        assertThat(StreamSessionFactory.failure(null, 0x4A5B, null).rawCode).isEqualTo("Error 4a5b")
    }

    @Test
    fun `an empty port text is no port hint`() {
        assertThat(StreamSessionFactory.failure("Control", -1, "").portHint).isNull()
    }

    @Test
    fun `a launch retry names the category the stack reported by ordinal`() {
        assertThat(StreamSessionFactory.launchRetry(LaunchCategory.PENDING.ordinal, 1, 3, 2000))
            .isEqualTo(LaunchRetry(LaunchCategory.PENDING, 1, 3, 2000))
        assertThat(StreamSessionFactory.launchRetry(99, 2, 3, 500).category).isNull()
    }
}
