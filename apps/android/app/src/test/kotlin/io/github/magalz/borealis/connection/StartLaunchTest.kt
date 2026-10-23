package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.crypto.LimeLogCapture
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.nvhttp.HostReply
import java.nio.ByteBuffer
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A start of an app that is not running: what the host is asked, what the listener is told, and
 * which arguments reach the native connection.
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
class StartLaunchTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    /** Starts [connection] and waits until it reached the native connection. */
    private fun startAndConnect(connection: NvConnection, decoder: StubDecoder = StubDecoder()) {
        rig.start(connection, decoder = decoder)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()
    }

    private fun startConnectionArgs(): List<Any?> =
        ShadowRecordingBridge.named("startConnection").single().args

    @Test
    fun `an app that is not running is launched, and the listener hears the stages in order`() {
        startAndConnect(rig.connection())

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/launch").inOrder()
    }

    @Test
    fun `the launch asks for the app by id, with the id and the key of this connection`() {
        startAndConnect(rig.connection())

        val launch = rig.request("/launch")
        assertThat(launch.scheme).isEqualTo("https")
        assertThat(launch.value("appid")).isEqualTo("$DESKTOP_ID")
        assertThat(launch.value("mode")).isEqualTo("1920x1080x60")
        assertThat(launch.value("rikey")).hasLength(32)
        assertThat(launch.value("rikeyid")).isNotEmpty()
    }

    @Test
    fun `every connection has a key and a key id of its own`() {
        val first = rig.connection()
        startAndConnect(first)
        first.stop()
        val second = rig.connection()
        ShadowRecordingBridge.reset()
        startAndConnect(second)

        val launches = rig.host.requests.filter { it.path == "/launch" }
        assertThat(launches).hasSize(2)
        assertThat(launches.map { it.value("rikey") }.toSet()).hasSize(2)
        assertThat(launches.map { it.value("rikeyid") }.toSet()).hasSize(2)
    }

    @Test
    fun `the thread of a start has no name of its own`() {
        startAndConnect(rig.connection())

        assertThat(listener.threads).hasSize(1)
        assertThat(ConnectionRig.isUnnamed(listener.threads.single())).isTrue()
    }

    @Test
    fun `the native connection gets the negotiated stream and the host that the start found out`() {
        rig.script.gfeVersion = "3.28.0.417"
        rig.script.codecSupport = "259"
        val config =
            rig.config(
                maxPacketSize = 1392,
                formats = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265,
                clientRefreshRateX100 = 5994,
                refreshRate = 90,
                launchRefreshRate = 30,
            )
        startAndConnect(rig.connection(config))

        assertThat(startConnectionArgs().subList(0, 14))
            .containsExactly(
                "127.0.0.1", // the address of the host
                "7.1.431.-1", // the version of the server app
                "3.28.0.417", // the GFE version
                "rtsp://127.0.0.1:48010", // the session of /launch
                259, // the codec support of the host
                1920,
                1080,
                90, // the refresh rate of the stream, not the one of the launch
                20_000, // the bitrate
                1392, // the packet size of the configuration
                StreamConfiguration.STREAM_CFG_LOCAL,
                MoonBridge.AUDIO_CONFIGURATION_STEREO.toInt(),
                MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265,
                5994,
            )
            .inOrder()
    }

    @Test
    fun `the native connection gets the key of the launch, and the id of the key as the vector`() {
        startAndConnect(rig.connection())

        val launch = rig.request("/launch")
        val keyId = checkNotNull(launch.value("rikeyid")).toInt()
        val key = checkNotNull(launch.value("rikey")).chunked(2).map { it.toInt(16).toByte() }
        assertThat((startConnectionArgs()[14] as ByteArray).toList()).isEqualTo(key)
        assertThat(startConnectionArgs()[15] as ByteArray)
            .isEqualTo(ByteBuffer.allocate(16).putInt(keyId).array())
    }

    @Test
    fun `the native connection gets the capabilities of the decoder and the color settings`() {
        val config =
            rig.config(
                colorSpace = MoonBridge.COLORSPACE_REC_2020,
                colorRange = MoonBridge.COLOR_RANGE_FULL,
            )
        startAndConnect(rig.connection(config), decoder = StubDecoder(capabilities = 0x42))

        assertThat(startConnectionArgs().subList(16, 19))
            .containsExactly(
                0x42, // the capabilities of the decoder
                MoonBridge.COLORSPACE_REC_2020,
                MoonBridge.COLOR_RANGE_FULL,
            )
            .inOrder()
    }

    @Test
    fun `a codec support above the int range is narrowed to an int`() {
        rig.script.codecSupport = "4294967295"

        startAndConnect(rig.connection())

        assertThat(startConnectionArgs()[4]).isEqualTo(-1)
    }

    @Test
    fun `a host without a GFE version or a codec support sends nulls and zero`() {
        startAndConnect(rig.connection())

        assertThat(startConnectionArgs()[2]).isNull()
        assertThat(startConnectionArgs()[4]).isEqualTo(0)
    }

    @Test
    fun `the bridge is set up and the connection started with the monitor of the bridge held`() {
        val decoder = StubDecoder()
        val audio = StubAudio()
        val connection = rig.connection()
        rig.start(connection, decoder = decoder, audio = audio)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(ShadowRecordingBridge.names)
            .containsExactly("setupBridge", "startConnection")
            .inOrder()
        assertThat(ShadowRecordingBridge.calls.map { it.locked }).containsExactly(true, true)
        val setup = ShadowRecordingBridge.named("setupBridge").single().args
        assertThat(setup[0]).isSameInstanceAs(decoder)
        assertThat(setup[1]).isSameInstanceAs(audio)
    }

    @Test
    fun `the listener of the bridge forwards every callback to the listener of the start`() {
        startAndConnect(rig.connection())

        val bridgeListener = checkNotNull(ShadowRecordingBridge.listener)
        assertThat(bridgeListener).isNotSameInstanceAs(listener)
        assertThat(bridgeListener.javaClass.name)
            .isEqualTo("io.github.magalz.borealis.HeldStageFailures")
        bridgeListener.connectionStarted()
        bridgeListener.connectionTerminated(-100)
        bridgeListener.stageFailed("RTSP handshake", 7, -1)

        assertThat(listener.events.drop(3))
            .containsExactly(
                "connectionStarted",
                "connectionTerminated -100",
                "stageFailed RTSP handshake ports=7 error=-1",
            )
            .inOrder()
    }

    @Test
    fun `a native connection that starts lets the stream ask for keyframes`() {
        val connection = rig.connection()
        startAndConnect(connection)

        connection.requestKeyframe()

        assertThat(ShadowRecordingBridge.named("requestIdrFrame")).hasSize(1)
    }

    @Test
    fun `a native connection that does not start lets no keyframe through`() {
        ShadowRecordingBridge.onStartConnection = { -1 }
        val connection = rig.connection()
        startAndConnect(connection)

        connection.requestKeyframe()

        assertThat(ShadowRecordingBridge.named("requestIdrFrame")).isEmpty()
    }

    @Test
    fun `a native connection that does not start frees the place for the next start`() {
        ShadowRecordingBridge.onStartConnection = { -1 }
        startAndConnect(rig.connection())

        assertThat(rig.permits()).isEqualTo(1)
        assertThat(listener.events.last()).isEqualTo("stageComplete $DESKTOP_NAME")
        assertThat(ShadowRecordingBridge.names)
            .containsExactly("setupBridge", "startConnection")
            .inOrder()
    }

    @Test
    fun `a native connection that starts keeps the place until the stop`() {
        startAndConnect(rig.connection())

        assertThat(rig.permits()).isEqualTo(0)
    }

    @Test
    fun `a stream that does not ask for HDR says nothing about it`() {
        startAndConnect(rig.connection())

        assertThat(listener.events("transient")).isEmpty()
        assertThat(rig.request("/launch").value("hdrMode")).isNull()
    }

    @Test
    fun `a PC that cannot stream HDR is told so, and the stream is SDR`() {
        rig.script.codecSupport = "1"
        val config =
            rig.config(
                formats = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            )

        startAndConnect(rig.connection(config))

        assertThat(listener.events("transient"))
            .containsExactly(
                "transient Your PC GPU does not support streaming HDR. The stream will be SDR."
            )
        assertThat(rig.request("/launch").value("hdrMode")).isNull()
    }

    @Test
    fun `a PC with HEVC Main10 gets the HDR launch`() {
        assertHdrLaunchWithSupport("512")
    }

    @Test
    fun `a PC with AV1 Main10 gets the HDR launch`() {
        assertHdrLaunchWithSupport("131072")
    }

    private fun assertHdrLaunchWithSupport(support: String) {
        rig.script.codecSupport = support
        val config =
            rig.config(
                formats = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            )

        startAndConnect(rig.connection(config))

        assertThat(listener.events("transient")).isEmpty()
        assertThat(rig.request("/launch").value("hdrMode")).isEqualTo("1")
    }

    @Test
    fun `4K on a host that is not GFE 3 is 1080p, and the listener is told`() {
        val config = rig.config(width = 3840, height = 2160)

        startAndConnect(rig.connection(config))

        assertThat(listener.events("transient"))
            .containsExactly(
                "transient You must update GeForce Experience to stream in 4K. The stream will be 1080p."
            )
        assertThat(startConnectionArgs().subList(5, 7)).containsExactly(1920, 1080).inOrder()
        assertThat(rig.request("/launch").value("mode")).isEqualTo("1920x1080x60")
    }

    @Test
    fun `4K on GFE 2 is 1080p too`() {
        rig.script.gfeVersion = "2.8.1"
        val config = rig.config(width = 3840, height = 2160)

        startAndConnect(rig.connection(config))

        assertThat(startConnectionArgs().subList(5, 7)).containsExactly(1920, 1080).inOrder()
    }

    @Test
    fun `4K on GFE 3 is 4K`() {
        rig.script.gfeVersion = "3.20.0"
        val config = rig.config(width = 3840, height = 2160)

        startAndConnect(rig.connection(config))

        assertThat(listener.events("transient")).isEmpty()
        assertThat(startConnectionArgs().subList(5, 7)).containsExactly(3840, 2160).inOrder()
        assertThat(rig.request("/launch").value("mode")).isEqualTo("3840x2160x60")
    }

    @Test
    fun `above 4K needs HEVC or AV1 on the device, and the start fails`() {
        val config = rig.config(width = 5120, height = 2880)

        rig.start(rig.connection(config))
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "message Your streaming device must support HEVC or AV1 to stream at resolutions above 4K.",
                "stageFailed serverinfo ports=0 error=0",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo")
    }

    @Test
    fun `above 4K needs the host to support it when the host is GFE`() {
        rig.script.state = HostScript.GFE_FREE
        val config =
            rig.config(
                width = 5120,
                height = 2880,
                formats = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265,
            )

        rig.start(rig.connection(config))
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "message Your host PC does not support streaming at resolutions above 4K.",
                "stageFailed serverinfo ports=0 error=0",
            )
            .inOrder()
    }

    @Test
    fun `above 4K is streamed as asked when HEVC is there and the host is GFE 3`() {
        rig.script.gfeVersion = "3.20.0"
        rig.script.codecSupport = "512"
        val config =
            rig.config(
                width = 5120,
                height = 2880,
                formats = MoonBridge.VIDEO_FORMAT_H264 or MoonBridge.VIDEO_FORMAT_H265,
            )

        startAndConnect(rig.connection(config))

        assertThat(startConnectionArgs().subList(5, 7)).containsExactly(5120, 2880).inOrder()
    }

    @Test
    fun `an app without an id is looked up by name in the app list, without regard to case`() {
        val config = rig.config(app = NvApp("desktop"))

        startAndConnect(rig.connection(config))

        assertThat(rig.paths()).containsExactly("/serverinfo", "/applist", "/launch").inOrder()
        assertThat(listener.events.first()).isEqualTo("stageStarting desktop")
    }

    /**
     * The id that was looked up decides whether the app is running, but a launch still asks for the
     * id of the configuration: zero, for an app that came without one.
     */
    @Test
    fun `an app that was looked up by name is launched with the id of the configuration`() {
        val config = rig.config(app = NvApp("Desktop"))

        startAndConnect(rig.connection(config))

        assertThat(rig.request("/launch").value("appid")).isEqualTo("0")
    }

    @Test
    fun `an app that is not in the app list fails the start with the name of the app`() {
        val config = rig.config(app = NvApp("Missing"))

        rig.start(rig.connection(config))
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting Missing",
                "message The app Missing is not in GFE app list",
                "stageFailed serverinfo ports=0 error=0",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/applist").inOrder()
    }

    @Test
    fun `the lookup by name and the launch are logged`() {
        LimeLogCapture().use { log ->
            startAndConnect(rig.connection(rig.config(app = NvApp("Desktop"))))

            assertThat(log.records)
                .containsAtLeast(
                    Level.INFO to DEPRECATED_LOOKUP,
                    Level.INFO to "Launched new game session",
                )
        }
    }

    @Test
    fun `a launch that the host does not take is a failed application`() {
        rig.script.launchReplies += HostReply.root("<gamesession>0</gamesession>")

        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "message Failed to launch application",
                "stageFailed launch ports=0 error=0",
            )
            .inOrder()
    }

    @Test
    fun `a launch that fails with a status of the host fails at the launch step with the status`() {
        rig.script.launchReplies += HostReply.failure("500", "boom")

        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed launch ports=0 error=500")
        assertThat(listener.events("message")).isEmpty()
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a launch that fails with a status of the host is remembered as that status`() {
        rig.script.launchReplies += HostReply.failure("500", "boom")
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(connection.lastHttpCode()).isEqualTo(500)
    }

    @Test
    fun `a launch that fails with a status of the host is remembered as an HTTP failure`() {
        rig.script.launchReplies += HostReply.failure("500", "boom")
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
        assertThat(connection.launchRetriesExhausted()).isFalse()
        assertThat(connection.endedByResumeCollision()).isFalse()
    }

    @Test
    fun `a launch that works leaves no failure to tell`() {
        val connection = rig.connection()
        startAndConnect(connection)

        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(connection.lastHttpCode()).isEqualTo(0)
        assertThat(connection.launchRetriesExhausted()).isFalse()
    }

    private companion object {
        const val DEPRECATED_LOOKUP =
            "Using deprecated app lookup method - " +
                "Please specify an app ID in your StreamConfiguration instead"
    }
}
