package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.settings.ShadowMoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a [StreamConfiguration] holds right after construction, and what each `Builder` setter
 * changes. The configuration is built here with its real constructor, which needs
 * [ShadowMoonBridge]: the defaults read the audio configuration of `MoonBridge`.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class StreamConfigurationCharacterizationTest {
    private class Case(
        val setter: String,
        val apply: (StreamConfiguration.Builder) -> Unit,
        val changes: Map<String, Any?>,
    )

    private fun builder() = StreamConfiguration.Builder()

    /** Every getter, by name. The app is reduced to its name and id: `NvApp` has no `equals`. */
    private fun snapshot(config: StreamConfiguration): Map<String, Any?> =
        mapOf(
            "app" to config.app?.let { it.appName to it.appId },
            "width" to config.width,
            "height" to config.height,
            "refreshRate" to config.refreshRate,
            "launchRefreshRate" to config.launchRefreshRate,
            "clientRefreshRateX100" to config.clientRefreshRateX100,
            "bitrate" to config.bitrate,
            "sops" to config.sops,
            "adaptiveResolutionEnabled" to config.adaptiveResolutionEnabled,
            "playLocalAudio" to config.playLocalAudio,
            "maxPacketSize" to config.maxPacketSize,
            "remote" to config.remote,
            "audioConfiguration" to config.audioConfiguration,
            "supportedVideoFormats" to config.supportedVideoFormats,
            "attachedGamepadMask" to config.attachedGamepadMask,
            "persistGamepadsAfterDisconnect" to config.persistGamepadsAfterDisconnect,
            "colorRange" to config.colorRange,
            "colorSpace" to config.colorSpace,
        )

    @Test
    fun `a fresh configuration is a 720p60 H264 stream of the Steam app`() {
        val config = builder().build()

        assertThat(config.app?.appName).isEqualTo("Steam")
        assertThat(config.app?.appId).isEqualTo(0)
        assertThat(config.width).isEqualTo(1280)
        assertThat(config.height).isEqualTo(720)
        assertThat(config.refreshRate).isEqualTo(60)
        assertThat(config.launchRefreshRate).isEqualTo(60)
        assertThat(config.clientRefreshRateX100).isEqualTo(0)
        assertThat(config.bitrate).isEqualTo(10_000)
        assertThat(config.maxPacketSize).isEqualTo(1024)
        assertThat(config.remote).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
        assertThat(config.sops).isTrue()
        assertThat(config.adaptiveResolutionEnabled).isFalse()
        assertThat(config.playLocalAudio).isFalse()
        assertThat(config.audioConfiguration).isSameInstanceAs(MoonBridge.AUDIO_CONFIGURATION_STEREO)
        assertThat(config.supportedVideoFormats).isEqualTo(MoonBridge.VIDEO_FORMAT_H264)
        assertThat(config.attachedGamepadMask).isEqualTo(0)
        assertThat(config.persistGamepadsAfterDisconnect).isFalse()
        assertThat(config.colorRange).isEqualTo(0)
        assertThat(config.colorSpace).isEqualTo(0)
    }

    @Test
    fun `every builder setter returns the same builder`() {
        val b = builder()

        val returned =
            listOf(
                b.setApp(NvApp("A")),
                b.setRemoteConfiguration(1),
                b.setResolution(1, 2),
                b.setRefreshRate(3),
                b.setLaunchRefreshRate(4),
                b.setBitrate(5),
                b.setEnableSops(false),
                b.enableAdaptiveResolution(true),
                b.enableLocalAudioPlayback(true),
                b.setMaxPacketSize(6),
                b.setAttachedGamepadMask(7),
                b.setAttachedGamepadMaskByCount(2),
                b.setPersistGamepadsAfterDisconnect(true),
                b.setClientRefreshRateX100(8),
                b.setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO),
                b.setSupportedVideoFormats(9),
                b.setColorRange(10),
                b.setColorSpace(11),
            )

        for (result in returned) {
            assertThat(result).isSameInstanceAs(b)
        }
    }

    @Test
    fun `the builder hands out one configuration and later setters change it`() {
        val b = builder()
        val first = b.build()

        b.setBitrate(25_000)

        assertThat(b.build()).isSameInstanceAs(first)
        assertThat(first.bitrate).isEqualTo(25_000)
    }

    private val surround = MoonBridge.AudioConfiguration(6, 0x3F)

    private val cases =
        listOf(
            Case(
                "setApp",
                { it.setApp(NvApp("Desktop", 881448767, false)) },
                mapOf("app" to ("Desktop" to 881448767)),
            ),
            Case(
                "setRemoteConfiguration",
                { it.setRemoteConfiguration(StreamConfiguration.STREAM_CFG_REMOTE) },
                mapOf("remote" to StreamConfiguration.STREAM_CFG_REMOTE),
            ),
            Case(
                "setResolution",
                { it.setResolution(3840, 2160) },
                mapOf("width" to 3840, "height" to 2160),
            ),
            Case("setRefreshRate", { it.setRefreshRate(120) }, mapOf("refreshRate" to 120)),
            Case(
                "setLaunchRefreshRate",
                { it.setLaunchRefreshRate(90) },
                mapOf("launchRefreshRate" to 90),
            ),
            Case("setBitrate", { it.setBitrate(80_000) }, mapOf("bitrate" to 80_000)),
            Case("setEnableSops", { it.setEnableSops(false) }, mapOf("sops" to false)),
            Case(
                "enableAdaptiveResolution",
                { it.enableAdaptiveResolution(true) },
                mapOf("adaptiveResolutionEnabled" to true),
            ),
            Case(
                "enableLocalAudioPlayback",
                { it.enableLocalAudioPlayback(true) },
                mapOf("playLocalAudio" to true),
            ),
            Case(
                "setMaxPacketSize",
                { it.setMaxPacketSize(1392) },
                mapOf("maxPacketSize" to 1392),
            ),
            Case(
                "setAttachedGamepadMask",
                { it.setAttachedGamepadMask(0b0101) },
                mapOf("attachedGamepadMask" to 0b0101),
            ),
            Case(
                "setAttachedGamepadMaskByCount",
                { it.setAttachedGamepadMaskByCount(3) },
                mapOf("attachedGamepadMask" to 0b0111),
            ),
            Case(
                "setPersistGamepadsAfterDisconnect",
                { it.setPersistGamepadsAfterDisconnect(true) },
                mapOf("persistGamepadsAfterDisconnect" to true),
            ),
            Case(
                "setClientRefreshRateX100",
                { it.setClientRefreshRateX100(11_999) },
                mapOf("clientRefreshRateX100" to 11_999),
            ),
            Case(
                "setAudioConfiguration",
                { it.setAudioConfiguration(surround) },
                mapOf("audioConfiguration" to surround),
            ),
            Case(
                "setSupportedVideoFormats",
                { it.setSupportedVideoFormats(MoonBridge.VIDEO_FORMAT_H265) },
                mapOf("supportedVideoFormats" to MoonBridge.VIDEO_FORMAT_H265),
            ),
            Case("setColorRange", { it.setColorRange(1) }, mapOf("colorRange" to 1)),
            Case("setColorSpace", { it.setColorSpace(2) }, mapOf("colorSpace" to 2)),
        )

    @Test
    fun `each setter changes its own getter and no other`() {
        val fresh = snapshot(builder().build())

        for (case in cases) {
            val b = builder()
            case.apply(b)

            assertWithMessage(case.setter).that(snapshot(b.build())).isEqualTo(fresh + case.changes)
        }
    }

    @Test
    fun `the attached gamepad mask by count sets one bit per pad, up to four`() {
        val masks =
            listOf(-1, 0, 1, 2, 3, 4, 5, 100).associateWith {
                builder().setAttachedGamepadMaskByCount(it).build().attachedGamepadMask
            }

        assertThat(masks)
            .containsExactly(
                -1,
                0,
                0,
                0,
                1,
                0b0001,
                2,
                0b0011,
                3,
                0b0111,
                4,
                0b1111,
                5,
                0b1111,
                100,
                0b1111,
            )
    }

    @Test
    fun `the attached gamepad mask by count replaces a mask that was set before`() {
        val config =
            builder().setAttachedGamepadMask(0b1000_0001).setAttachedGamepadMaskByCount(1).build()

        assertThat(config.attachedGamepadMask).isEqualTo(0b0001)
    }

    @Test
    fun `the app and the audio configuration are stored as given, null included`() {
        val app = NvApp("Desktop", 881448767, false)
        val audio = MoonBridge.AudioConfiguration(8, 0x63F)

        val given = builder().setApp(app).setAudioConfiguration(audio).build()
        val none = builder().setApp(null).setAudioConfiguration(null).build()

        assertThat(given.app).isSameInstanceAs(app)
        assertThat(given.audioConfiguration).isSameInstanceAs(audio)
        assertThat(none.app).isNull()
        assertThat(none.audioConfiguration).isNull()
    }
}
