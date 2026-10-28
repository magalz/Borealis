package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test

/** The stream that `Game.onCreate` asks the connection for, from the settings and the devices. */
internal class GameConfigurationTest : GameTestBase() {
    private val config: StreamConfiguration
        get() = rig.streamConfiguration

    @Test
    fun `the default settings ask the host for 720p at 60 frames`() {
        rig.create()

        assertThat(config.width).isEqualTo(1280)
        assertThat(config.height).isEqualTo(720)
        assertThat(config.refreshRate).isEqualTo(60)
        assertThat(config.launchRefreshRate).isEqualTo(60)
        assertThat(config.clientRefreshRateX100).isEqualTo(6000)
        assertThat(config.bitrate).isEqualTo(10000)
        assertThat(config.sops).isTrue()
        assertThat(config.playLocalAudio).isFalse()
        assertThat(config.maxPacketSize).isEqualTo(1392)
        assertThat(config.remote).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        assertThat(config.supportedVideoFormats).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        assertThat(config.colorSpace).isEqualTo(MoonBridge.COLORSPACE_REC_709)
        assertThat(config.colorRange).isEqualTo(MoonBridge.COLOR_RANGE_LIMITED)
        assertThat(config.adaptiveResolutionEnabled).isFalse()
    }

    @Test
    fun `the resolution, the frame rate, the bitrate and the host options come from the settings`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "30")
        rig.preference("seekbar_bitrate_kbps", 15000)
        rig.preference("checkbox_enable_sops", false)
        rig.preference("checkbox_host_audio", true)
        rig.preference("list_audio_config", "51")

        rig.create()

        assertThat(config.width).isEqualTo(1920)
        assertThat(config.height).isEqualTo(1080)
        assertThat(config.refreshRate).isEqualTo(30)
        assertThat(config.launchRefreshRate).isEqualTo(30)
        assertThat(config.bitrate).isEqualTo(15000)
        assertThat(config.sops).isFalse()
        assertThat(config.playLocalAudio).isTrue()
        assertThat(config.audioConfiguration)
            .isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
    }

    @Test
    fun `the full range setting asks for the full color range`() {
        rig.preference("checkbox_full_range", true)

        rig.create()

        assertThat(config.colorRange).isEqualTo(MoonBridge.COLOR_RANGE_FULL)
    }

    @Test
    fun `with no pad attached and multi-controller on, no gamepad is announced`() {
        rig.create()
        assertThat(config.attachedGamepadMask).isEqualTo(0)
        assertThat(config.persistGamepadsAfterDisconnect).isFalse()
    }

    @Test
    fun `without multi-controller the first gamepad is always present and stays after a disconnect`() {
        rig.preference("checkbox_multi_controller", false)

        rig.create()

        assertThat(config.attachedGamepadMask).isEqualTo(1)
        assertThat(config.persistGamepadsAfterDisconnect).isTrue()
    }

    @Test
    fun `the on-screen controller announces the first gamepad`() {
        rig.preference("checkbox_show_onscreen_controls", true)

        rig.create()

        assertThat(config.attachedGamepadMask).isEqualTo(1)
        assertThat(config.persistGamepadsAfterDisconnect).isFalse()
    }

    @Test
    fun `the negotiated frame rate is what the stream session and the audit report`() {
        rig.preference("list_fps", "90")

        rig.streaming()

        assertThat(rig.field<Int>("streamFrameRate")).isEqualTo(90)
        assertThat(rig.audit.single().detail).contains("fps=90")
    }

    @Test
    fun `capping the frame rate asks for one frame less than the display shows`() {
        rig.preference("frame_pacing", "cap-fps")

        rig.create()

        assertThat(config.launchRefreshRate).isEqualTo(60)
        assertThat(config.refreshRate).isEqualTo(59)
        assertThat(rig.field<Int>("streamFrameRate")).isEqualTo(59)
        assertThat(rig.field<PreferenceConfiguration>("prefConfig").framePacing)
            .isEqualTo(PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `capping a frame rate that is far above the display drops frames instead`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.preference("list_fps", "90")

        rig.create()

        assertThat(config.refreshRate).isEqualTo(90)
        assertThat(rig.field<PreferenceConfiguration>("prefConfig").framePacing)
            .isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
    }

    @Test
    fun `capping a frame rate within three of the display still caps it`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.preference("list_fps", "63")

        rig.create()

        assertThat(config.launchRefreshRate).isEqualTo(63)
        assertThat(config.refreshRate).isEqualTo(59)
        assertThat(rig.field<PreferenceConfiguration>("prefConfig").framePacing)
            .isEqualTo(PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `capping a frame rate below the display leaves it alone`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.preference("list_fps", "30")

        rig.create()

        assertThat(config.refreshRate).isEqualTo(30)
        assertThat(rig.field<PreferenceConfiguration>("prefConfig").framePacing)
            .isEqualTo(PreferenceConfiguration.FRAME_PACING_CAP_FPS)
    }

    @Test
    fun `capping on a display that shows less than 50 frames falls back to balanced pacing`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.preference("list_fps", "40")
        rig.displayModes(DisplayMode(0, 1280, 720, 40f))

        rig.create()

        assertThat(config.refreshRate).isEqualTo(40)
        assertThat(rig.field<PreferenceConfiguration>("prefConfig").framePacing)
            .isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
    }
}
