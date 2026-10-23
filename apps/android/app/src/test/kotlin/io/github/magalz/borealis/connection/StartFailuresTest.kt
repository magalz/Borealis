package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.nvhttp.HostReply
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A start that fails before the app is launched: what the listener is told (the step, the ports and
 * the status), what the connection remembers about the failure for the error screen, and what goes
 * to the log. `StartFailureTest` has the texts of the screen.
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
class StartFailuresTest {
    @get:Rule val rig = ConnectionRig()
    @get:Rule val log = RecordingLog()

    private val listener
        get() = rig.listener

    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val died = CountDownLatch(1)
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun catchUncaught() {
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            uncaught += error
            died.countDown()
        }
    }

    @After
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    private fun startAndFail(connection: NvConnection = rig.connection()): NvConnection {
        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()
        return connection
    }

    private fun refusedPort(): Int = ServerSocket(0).use { it.localPort }

    private fun startLogged(): List<String> =
        log.records
            .filter { it.message.startsWith("start failed") }
            .map { "${it.level} ${it.message} ${it.fields}" }

    @Test
    fun `a host that does not answer fails the serverinfo with the two ports of the pairing`() {
        val connection = startAndFail(rig.connection(serverCert = null, port = refusedPort()))

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "stageFailed serverinfo ports=3 error=0",
            )
            .inOrder()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.UNREACHABLE)
        assertThat(connection.lastHttpCode()).isEqualTo(0)
        assertThat(connection.launchRetriesExhausted()).isFalse()
        assertThat(connection.endedByResumeCollision()).isFalse()
    }

    @Test
    fun `a host that answers with a status fails the serverinfo with that status`() {
        rig.script.serverInfoReplies += HostReply.failure("500", "boom")

        startAndFail()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "stageFailed serverinfo ports=0 error=500",
            )
            .inOrder()
    }

    @Test
    fun `a host that answers with a status is remembered as an HTTP failure with that status`() {
        rig.script.serverInfoReplies += HostReply.failure("500", "boom")

        val connection = startAndFail()

        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
        assertThat(connection.lastHttpCode()).isEqualTo(500)
    }

    @Test
    fun `an answer that is not XML fails the serverinfo with the two ports of the pairing`() {
        rig.script.serverInfoReplies += HostReply.xml("""<root status_code="200"><appversion>""")

        val connection = startAndFail()

        assertThat(listener.events.last()).isEqualTo("stageFailed serverinfo ports=3 error=0")
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(connection.lastHttpCode()).isEqualTo(0)
    }

    @Test
    fun `an answer without the version of the host fails the serverinfo with the two ports of the pairing`() {
        rig.script.serverInfoReplies += HostReply.root("<hostname>HOST</hostname>")

        startAndFail()

        assertThat(listener.events.last()).isEqualTo("stageFailed serverinfo ports=3 error=0")
    }

    @Test
    fun `a certificate that is not the pinned one is a mismatch, whatever the fallback says`() {
        rig.host.identity = TestIdentities.otherHostRsa

        val connection = startAndFail()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "stageFailed serverinfo ports=3 error=0",
            )
            .inOrder()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.CERT_MISMATCH)
        assertThat(rig.requests()).containsExactly("http /serverinfo")
    }

    @Test
    fun `a certificate that is not the pinned one is a mismatch even when the fallback fails too`() {
        rig.host.identity = TestIdentities.otherHostRsa
        rig.script.serverInfoReplies += HostReply.status(500, "Server Error", ByteArray(0))

        val connection = startAndFail()

        assertThat(listener.events.last()).isEqualTo("stageFailed serverinfo ports=3 error=0")
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.CERT_MISMATCH)
        assertThat(connection.lastHttpCode()).isEqualTo(0)
    }

    @Test
    fun `a PC that is not paired with this device ends the start at the serverinfo`() {
        rig.script.paired = false

        val connection = startAndFail()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "message Device not paired with computer",
                "stageFailed serverinfo ports=0 error=0",
            )
            .inOrder()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(rig.paths()).containsExactly("/serverinfo")
    }

    @Test
    fun `a failed start is logged at warning level with the step, and the listener hears no exception text`() {
        rig.script.serverInfoReplies += HostReply.failure("500", "boom")

        startAndFail()

        assertThat(startLogged())
            .containsExactly(
                "WARN start failed: HostHttpResponseException: " +
                    "Host PC returned error: boom (Error code: 500) [(step, serverinfo)]"
            )
        assertThat(listener.events("message")).isEmpty()
    }

    @Test
    fun `a second start of a connection forgets the failure of the first`() {
        rig.script.serverInfoReplies += HostReply.failure("500", "boom")
        val connection = startAndFail()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
        assertThat(connection.lastHttpCode()).isEqualTo(500)

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(connection.lastHttpCode()).isEqualTo(0)
    }

    @Test
    fun `a second start of a connection forgets that the first one retried`() {
        repeat(6) { rig.script.launchReplies += HostReply.failure("409", "pending") }
        val connection = startAndFail()
        assertThat(connection.launchRetriesExhausted()).isTrue()

        // A GFE host is never asked again, so this start retries nothing.
        rig.script.state = HostScript.GFE_FREE
        rig.script.launchReplies += HostReply.failure("409", "pending")
        rig.start(connection)
        listener.awaitCount("stageFailed", 2)
        listener.awaitEnded()

        assertThat(connection.launchRetriesExhausted()).isFalse()
    }

    @Test
    fun `a stream configuration without an app ends the thread of the start before it says anything`() {
        rig.start(rig.connection(config = rig.config(app = null)))

        assertThat(died.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(uncaught.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(listener.events).isEmpty()
        assertThat(rig.paths()).isEmpty()
    }

    @Test
    fun `no stream configuration ends the thread of the start before it says anything`() {
        rig.start(rig.connection(config = null))

        assertThat(died.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(uncaught.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(listener.events).isEmpty()
        assertThat(rig.paths()).isEmpty()
    }

    /**
     * Starts through reflection: a Java caller may pass null for the renderer and the listener, and
     * the test must compile whatever the Kotlin parameter types are.
     */
    private fun startWithNull(
        connection: NvConnection,
        decoder: VideoDecoderRenderer?,
        connectionListener: NvConnectionListener?,
    ) {
        NvConnection::class
            .java
            .getMethod(
                "start",
                AudioRenderer::class.java,
                VideoDecoderRenderer::class.java,
                NvConnectionListener::class.java,
            )
            .invoke(connection, StubAudio(), decoder, connectionListener)
    }

    @Test
    fun `a start without a decoder renderer fails in its own thread before it says anything`() {
        startWithNull(rig.connection(), null, listener)

        assertThat(died.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(uncaught.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(listener.events).isEmpty()
        assertThat(rig.paths()).isEmpty()
    }

    @Test
    fun `a start without a listener fails in its own thread before it asks the host anything`() {
        startWithNull(rig.connection(), StubDecoder(), null)

        assertThat(died.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(uncaught.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(rig.paths()).isEmpty()
    }

    @Test
    fun `a stream configuration without an audio configuration ends the thread of the start at the launch`() {
        rig.start(rig.connection(config = rig.config(audio = null)))

        assertThat(died.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(uncaught.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", "launchStarting resume=false")
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    private companion object {
        const val WAIT_SECONDS = 30L
    }
}
