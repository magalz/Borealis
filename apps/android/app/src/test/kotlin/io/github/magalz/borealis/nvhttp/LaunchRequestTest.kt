package io.github.magalz.borealis.nvhttp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.crypto.LimeLogCapture
import io.github.magalz.borealis.crypto.TestIdentities
import java.io.Closeable
import java.util.logging.Level
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParserException

/**
 * `launchApp`, the resume that shares its request, and `quitApp`: the request that starts, joins or
 * ends a stream, and how the answer of the host is read. They go over HTTPS to the pinned host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowNvMoonBridge::class])
class LaunchRequestTest {
    private val closeables = mutableListOf<Closeable>()

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private val keyBytes =
        byteArrayOf(
            0x00, 0x01, 0x0F, 0x10, 0x7F, 0x80.toByte(), 0x81.toByte(), 0xA5.toByte(),
            0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x09, 0x90.toByte(),
            0x5A,
        )

    private fun host(): NvFakeHost = NvFakeHost().also { closeables += it }

    private fun client(host: NvFakeHost): NvHTTP =
        clientOf(host, serverCert = TestIdentities.hostRsa.certificate)

    private fun answerLaunch(host: NvFakeHost, verb: String = "launch", body: String = SESSION) =
        host.answer("/$verb", HostReply.root(body), "https")

    private fun context(
        gfe: Boolean = false,
        width: Int = 1920,
        height: Int = 1080,
        refreshRate: Int = 60,
        launchRefreshRate: Int = 60,
        sops: Boolean = true,
        playLocalAudio: Boolean = false,
        persistGamepads: Boolean = false,
        gamepadMask: Int = 0,
        audio: MoonBridge.AudioConfiguration? = MoonBridge.AUDIO_CONFIGURATION_STEREO,
        riKeyId: Int = -5,
    ): ConnectionContext =
        ConnectionContext().also {
            it.isNvidiaServerSoftware = gfe
            it.negotiatedWidth = width
            it.negotiatedHeight = height
            it.riKey = SecretKeySpec(keyBytes, "AES")
            it.riKeyId = riKeyId
            it.streamConfig =
                StreamConfiguration.Builder()
                    .setRefreshRate(refreshRate)
                    .setLaunchRefreshRate(launchRefreshRate)
                    .setEnableSops(sops)
                    .enableLocalAudioPlayback(playLocalAudio)
                    .setPersistGamepadsAfterDisconnect(persistGamepads)
                    .setAttachedGamepadMask(gamepadMask)
                    .setAudioConfiguration(audio)
                    .build()
        }

    /** The target of the one request that the host saw, without the two ids at the end. */
    private fun targetOf(host: NvFakeHost): String {
        val request = host.requests.single { it.path != "/serverinfo" }
        assertThat(request.scheme).isEqualTo("https")
        assertThat(request.names.takeLast(2)).containsExactly("uniqueid", "uuid").inOrder()
        assertThat(request.value("uniqueid")).isEqualTo(UNIQUE_ID)
        return request.target.substringBefore("&uniqueid=")
    }

    @Test
    fun `a launch asks for the app, the mode, the key and the audio in this order`() {
        val host = host()
        answerLaunch(host)

        val started = client(host).launchApp(context(), "launch", 12, false)

        assertThat(started).isTrue()
        assertThat(targetOf(host))
            .isEqualTo(
                "/launch?appid=12&mode=1920x1080x60&additionalStates=1&sops=1" +
                    "&rikey=00010F107F8081A5ABCDEFFEFF09905A&rikeyid=-5&localAudioPlayMode=0" +
                    "&surroundAudioInfo=196610&remoteControllersBitmap=0&gcmap=0&gcpersist=0&corever=1"
            )
    }

    @Test
    fun `the key goes out as upper case hex of every byte`() {
        val host = host()
        answerLaunch(host)

        client(host).launchApp(context(), "launch", 1, false)

        val rikey = host.requests.single().value("rikey")
        assertThat(rikey).isEqualTo("00010F107F8081A5ABCDEFFEFF09905A")
        assertThat(rikey).hasLength(keyBytes.size * 2)
    }

    @Test
    fun `HDR adds its parameters between the key id and the audio mode`() {
        val host = host()
        answerLaunch(host)

        client(host).launchApp(context(), "launch", 3, true)

        assertThat(targetOf(host))
            .isEqualTo(
                "/launch?appid=3&mode=1920x1080x60&additionalStates=1&sops=1" +
                    "&rikey=00010F107F8081A5ABCDEFFEFF09905A&rikeyid=-5" +
                    "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0" +
                    "&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1" +
                    "&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0" +
                    "&localAudioPlayMode=0&surroundAudioInfo=196610&remoteControllersBitmap=0" +
                    "&gcmap=0&gcpersist=0&corever=1"
            )
    }

    @Test
    fun `the audio, the gamepads and the key id come from the stream configuration`() {
        val host = host()
        answerLaunch(host)

        client(host)
            .launchApp(
                context(
                    playLocalAudio = true,
                    persistGamepads = true,
                    gamepadMask = 15,
                    audio = MoonBridge.AUDIO_CONFIGURATION_51_SURROUND,
                    riKeyId = 1234567,
                ),
                "launch",
                7,
                false,
            )

        assertThat(targetOf(host))
            .isEqualTo(
                "/launch?appid=7&mode=1920x1080x60&additionalStates=1&sops=1" +
                    "&rikey=00010F107F8081A5ABCDEFFEFF09905A&rikeyid=1234567&localAudioPlayMode=1" +
                    "&surroundAudioInfo=4128774&remoteControllersBitmap=15&gcmap=15&gcpersist=1" +
                    "&corever=1"
            )
    }

    @Test
    fun `the mode is the negotiated size and the launch frame rate`() {
        val host = host()
        answerLaunch(host)

        client(host).launchApp(context(width = 1280, height = 720, launchRefreshRate = 90, refreshRate = 90), "launch", 1, false)

        assertThat(host.requests.single().value("mode")).isEqualTo("1280x720x90")
    }

    @Test
    fun `a GFE host is not given a frame rate above 60`() {
        val host = host()
        answerLaunch(host)
        val http = client(host)

        http.launchApp(context(gfe = true, launchRefreshRate = 120, refreshRate = 120), "launch", 1, false)
        http.launchApp(context(gfe = true, launchRefreshRate = 60, refreshRate = 60), "launch", 1, false)
        http.launchApp(context(gfe = true, launchRefreshRate = 61, refreshRate = 61), "launch", 1, false)

        assertThat(host.requests.map { it.value("mode") })
            .containsExactly("1920x1080x0", "1920x1080x60", "1920x1080x0")
            .inOrder()
    }

    @Test
    fun `a GFE host keeps SOPS only at 720p, 1080p, 4K or below 720p`() {
        val resolutions =
            listOf(
                Triple(1280, 720, true),
                Triple(1920, 1080, true),
                Triple(3840, 2160, true),
                Triple(640, 360, true),
                Triple(1024, 768, true),
                Triple(1280, 800, false),
                Triple(2560, 1440, false),
                Triple(1920, 1200, false),
                Triple(3840, 1600, false),
            )
        for ((width, height, keepsSops) in resolutions) {
            val host = host()
            answerLaunch(host)

            client(host).launchApp(context(gfe = true, width = width, height = height), "launch", 1, false)

            assertWithMessage("${width}x$height")
                .that(host.requests.single().value("sops"))
                .isEqualTo(if (keepsSops) "1" else "0")
        }
    }

    @Test
    fun `the SOPS that GFE loses is logged with the resolution`() {
        LimeLogCapture().use { log ->
            val host = host()
            answerLaunch(host)

            client(host).launchApp(context(gfe = true, width = 2560, height = 1440), "launch", 1, false)

            assertThat(log.records)
                .contains(Level.INFO to "Disabling SOPS due to non-standard resolution: 2560x1440")
        }
    }

    @Test
    fun `another host than GFE keeps SOPS at any resolution and the user can turn it off`() {
        val host = host()
        answerLaunch(host)
        val http = client(host)

        http.launchApp(context(gfe = false, width = 2560, height = 1440), "launch", 1, false)
        http.launchApp(context(gfe = false, sops = false), "launch", 1, false)
        http.launchApp(context(gfe = true, sops = false), "launch", 1, false)

        assertThat(host.requests.map { it.value("sops") }).containsExactly("1", "0", "0").inOrder()
    }

    @Test
    fun `a launch that the host accepts keeps the address of the RTSP session`() {
        val host = host()
        answerLaunch(host, body = "<gamesession>1</gamesession><sessionUrl0>rtsp://192.0.2.10:48010</sessionUrl0>")
        val context = context()

        assertThat(client(host).launchApp(context, "launch", 1, false)).isTrue()

        assertThat(context.rtspSessionUrl).isEqualTo("rtsp://192.0.2.10:48010")
    }

    @Test
    fun `an older host without a session url leaves the address empty, also when one was set`() {
        val host = host()
        answerLaunch(host, body = "<gamesession>1</gamesession>")
        val context = context().also { it.rtspSessionUrl = "rtsp://old" }

        assertThat(client(host).launchApp(context, "launch", 1, false)).isTrue()

        assertThat(context.rtspSessionUrl).isNull()
    }

    @Test
    fun `a launch that the host does not start is false and leaves the context alone`() {
        val host = host()
        answerLaunch(host, body = "<gamesession>0</gamesession><sessionUrl0>rtsp://x</sessionUrl0>")
        val context = context().also { it.rtspSessionUrl = "rtsp://old" }

        assertThat(client(host).launchApp(context, "launch", 1, false)).isFalse()

        assertThat(context.rtspSessionUrl).isEqualTo("rtsp://old")
    }

    @Test
    fun `any gamesession other than 0 is a started launch`() {
        for (value in listOf("1", "2", "00", "")) {
            val host = host()
            answerLaunch(host, body = "<gamesession>$value</gamesession><sessionUrl0>u</sessionUrl0>")

            val result =
                runCatching { client(host).launchApp(context(), "launch", 1, false) }

            if (value.isEmpty()) {
                // An empty tag has no text: the field is missing.
                assertThat(result.exceptionOrNull()).isInstanceOf(XmlPullParserException::class.java)
            } else {
                assertWithMessage("'$value'").that(result.getOrNull()).isTrue()
            }
        }
    }

    @Test
    fun `a resume is the same request on another path and reads the resume tag`() {
        val host = host()
        answerLaunch(
            host,
            verb = "resume",
            body = "<resume>1</resume><sessionUrl0>rtsp://192.0.2.10:48010</sessionUrl0>",
        )
        val context = context()

        assertThat(client(host).launchApp(context, "resume", 99, false)).isTrue()

        assertThat(targetOf(host))
            .isEqualTo(
                "/resume?appid=99&mode=1920x1080x60&additionalStates=1&sops=1" +
                    "&rikey=00010F107F8081A5ABCDEFFEFF09905A&rikeyid=-5&localAudioPlayMode=0" +
                    "&surroundAudioInfo=196610&remoteControllersBitmap=0&gcmap=0&gcpersist=0&corever=1"
            )
        assertThat(context.rtspSessionUrl).isEqualTo("rtsp://192.0.2.10:48010")
    }

    @Test
    fun `a resume that the host refuses is false`() {
        val host = host()
        answerLaunch(host, verb = "resume", body = "<resume>0</resume>")

        assertThat(client(host).launchApp(context(), "resume", 1, false)).isFalse()
    }

    @Test
    fun `a launch reads the gamesession and a resume reads the resume tag, and only these`() {
        val launchHost = host()
        answerLaunch(launchHost, body = "<resume>1</resume>")
        val resumeHost = host()
        answerLaunch(resumeHost, verb = "resume", body = "<gamesession>1</gamesession>")

        assertThat(
                assertThrows(XmlPullParserException::class.java) {
                        client(launchHost).launchApp(context(), "launch", 1, false)
                    }
                    .message
            )
            .contains("gamesession")
        assertThat(
                assertThrows(XmlPullParserException::class.java) {
                        client(resumeHost).launchApp(context(), "resume", 1, false)
                    }
                    .message
            )
            .contains("resume")
    }

    @Test
    fun `another verb is sent as it is and is never a started session`() {
        val host = host()
        answerLaunch(host, verb = "other", body = "<gamesession>1</gamesession><resume>1</resume>")

        assertThat(client(host).launchApp(context(), "other", 1, false)).isFalse()

        assertThat(host.requests.single().path).isEqualTo("/other")
    }

    @Test
    fun `a launch the host answers with a failure raises its error`() {
        val host = host()
        host.answer(
            "/launch",
            HostReply.failure("400", "An app is already running on this host"),
            "https",
        )

        val error =
            assertThrows(HostHttpResponseException::class.java) {
                client(host).launchApp(context(), "launch", 1, false)
            }

        assertThat(error.errorCode).isEqualTo(400)
        assertThat(error.errorMessage).isEqualTo("An app is already running on this host")
    }

    @Test
    fun `a launch reads the HTTPS port of the host first when it does not know it`() {
        val host = host()
        host.answer("/serverinfo", HostReply.xml(serverInfoBody(httpsPort = host.httpsPort)), "http")
        answerLaunch(host)
        val http = clientOf(host, serverCert = TestIdentities.hostRsa.certificate, httpsKnown = false)

        assertThat(http.launchApp(context(), "launch", 1, false)).isTrue()

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("http /serverinfo", "https /launch")
            .inOrder()
    }

    @Test
    fun `a context without the pieces of a launch ends before any launch request`() {
        val without =
            listOf<(ConnectionContext) -> Unit>(
                { it.streamConfig = null },
                { it.riKey = null },
                {
                    it.streamConfig =
                        StreamConfiguration.Builder().setAudioConfiguration(null).build()
                },
            )
        for (remove in without) {
            val host = host()
            answerLaunch(host)
            val context = context().also(remove)

            // A NullPointerException in Java, an IllegalStateException in Kotlin.
            assertThrows(RuntimeException::class.java) {
                client(host).launchApp(context, "launch", 1, false)
            }

            assertThat(host.requests.map { it.path }).doesNotContain("/launch")
        }
    }

    @Test
    fun `quitting asks the host to cancel and is true when no app of another client is left`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer("/serverinfo", HostReply.xml(serverInfoBody()), "https")

        assertThat(client(host).quitApp()).isTrue()

        assertThat(host.requests.map { "${it.scheme} ${it.path}" })
            .containsExactly("https /cancel", "https /serverinfo")
            .inOrder()
        val cancel = host.requests.first()
        assertThat(cancel.names).containsExactly("uniqueid", "uuid").inOrder()
        assertThat(cancel.value("uniqueid")).isEqualTo(UNIQUE_ID)
    }

    @Test
    fun `a cancel that the host answers with 0 is false without a second request`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>0</cancel>"), "https")

        assertThat(client(host).quitApp()).isFalse()

        assertThat(host.requests.map { it.path }).containsExactly("/cancel")
    }

    @Test
    fun `a cancel that leaves an app running is the synthetic error 599`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<cancel>1</cancel>"), "https")
        host.answer(
            "/serverinfo",
            HostReply.xml(serverInfoBody("SUNSHINE_SERVER_BUSY", currentGame = 9)),
            "https",
        )

        val error = assertThrows(HostHttpResponseException::class.java) { client(host).quitApp() }

        assertThat(error.errorCode).isEqualTo(599)
        assertThat(error.errorMessage).isEmpty()
    }

    @Test
    fun `a cancel answer without the cancel tag is a parser exception`() {
        val host = host()
        host.answer("/cancel", HostReply.root("<other>1</other>"), "https")

        assertThrows(XmlPullParserException::class.java) { client(host).quitApp() }
    }

    private companion object {
        const val SESSION = "<gamesession>1</gamesession><sessionUrl0>rtsp://192.0.2.10:48010</sessionUrl0>"
    }
}
