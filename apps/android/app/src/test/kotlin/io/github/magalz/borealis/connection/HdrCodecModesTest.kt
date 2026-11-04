package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The codec modes of the host that let a stream be HDR (`HDR_CODEC_MODES` of `NvConnection`): HEVC
 * Main10 and AV1 Main10 only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class HdrCodecModesTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    /**
     * A pin of the current behaviour, not a fix. The client never offers a 4:4:4 format (`Game`
     * picks HEVC Main10 and AV1 Main10 only), so a host that supports HDR only in 4:4:4 (HEVC
     * REXT10 4:4:4, `0x100000`, and AV1 High10 4:4:4, `0x400000`) would find no format that
     * matches: HDR must stay off for it, with the message that says so, and the launch asks for no
     * HDR. Adding these two bits to `HDR_CODEC_MODES` would turn HDR on for such a host and the
     * stream would fail.
     */
    @Test
    fun `a PC that supports HDR only in 4 4 4 gets an SDR stream, the client offers no 4 4 4 format`() {
        rig.script.codecSupport = (0x100000 or 0x400000).toString()
        val config = rig.config {
            setSupportedVideoFormats(
                MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            )
        }

        rig.start(rig.connection(config))
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(listener.events("transient"))
            .containsExactly(
                "transient Your PC GPU does not support streaming HDR. The stream will be SDR."
            )
        assertThat(rig.request("/launch").value("hdrMode")).isNull()
    }
}
