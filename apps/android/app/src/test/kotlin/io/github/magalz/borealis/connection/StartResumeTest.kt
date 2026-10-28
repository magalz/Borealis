package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A start of the app that already runs on the host: it is resumed, and the failures of `/resume`
 * have their own texts. Includes the second device that joins while the host still builds the
 * display of the first (a failed RTSP handshake right after an accepted `/resume`).
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
class StartResumeTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    private fun startAndFail(connection: NvConnection = rig.connection()): NvConnection {
        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()
        return connection
    }

    /**
     * What the native handshake does when the RTSP stage fails with [code], then the start fails.
     */
    private fun rtspFails(code: Int = -1, ports: Int = 5): (BridgeCall) -> Int = {
        checkNotNull(ShadowRecordingBridge.listener)
            .stageFailed(
                ShadowRecordingBridge.getStageName(ShadowRecordingBridge.RTSP_STAGE),
                ports,
                code,
            )
        -1
    }

    @Test
    fun `an app that already runs is resumed, not launched`() {
        rig.script.running(DESKTOP_ID)
        val connection = rig.connection()

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/resume").inOrder()
        assertThat(rig.request("/resume").value("appid")).isEqualTo("$DESKTOP_ID")
        assertThat(ShadowRecordingBridge.named("startConnection").single().args[3])
            .isEqualTo("rtsp://127.0.0.1:48010")
    }

    @Test
    fun `a resume is logged`() {
        rig.script.running(DESKTOP_ID)

        LimeLogCapture().start().use { log ->
            rig.start(rig.connection())
            ShadowRecordingBridge.await("startConnection")
            listener.awaitEnded()

            assertThat(log.entries).contains(Level.INFO to "Resumed existing game session")
        }
    }

    @Test
    fun `a resume that the host does not take is a failed resume`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.root("<resume>0</resume>")

        startAndFail()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                "message Failed to resume existing session",
                "stageFailed resume ports=0 error=0",
            )
            .inOrder()
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a resume of a session that another device started says so, with the code`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("470", "denied")

        val connection = startAndFail()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "message This session wasn't started by this device, so it cannot be resumed. " +
                    "End streaming on the original device or the PC itself and try again. " +
                    "(Error code: 470)",
                "stageFailed resume ports=0 error=0",
            )
            .inOrder()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
    }

    @Test
    fun `a resume of a minimized application says so`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("525", "minimized")

        startAndFail()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "message The application is minimized. Resume it on the PC manually or quit the " +
                    "session and start streaming again.",
                "stageFailed resume ports=0 error=0",
            )
            .inOrder()
    }

    @Test
    fun `a resume that fails with another status fails at the resume step with that status`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("500", "boom")

        startAndFail()

        assertThat(listener.events.drop(2)).containsExactly("stageFailed resume ports=0 error=500")
    }

    @Test
    fun `a resume that fails with another status is remembered as an HTTP failure with that status`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("500", "boom")

        val connection = startAndFail()

        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
        assertThat(connection.lastHttpCode()).isEqualTo(500)
    }

    @Test
    fun `a resume with no running app to resume launches the app instead`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("503", "No running app to resume")

        rig.start(rig.connection())
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/resume", "/launch").inOrder()
    }

    @Test
    fun `a failed handshake of a GFE host after a resume is reported as it is`() {
        rig.script.running(DESKTOP_ID, HostScript.GFE_BUSY)
        ShadowRecordingBridge.onStartConnection = rtspFails()
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed RTSP handshake ports=5 error=-1")
        assertThat(connection.endedByResumeCollision()).isFalse()
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
    }

    @Test
    fun `another failure of the handshake after a resume is reported as it is`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = rtspFails(code = -2)

        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed RTSP handshake ports=5 error=-2")
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
    }

    @Test
    fun `a failed handshake after a launch is reported as it is`() {
        ShadowRecordingBridge.onStartConnection = rtspFails()

        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed RTSP handshake ports=5 error=-1")
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
    }

    @Test
    fun `a stage other than the handshake that fails after a resume is reported as it is`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = {
            checkNotNull(ShadowRecordingBridge.listener).stageFailed("stage 3", 9, -1)
            -1
        }

        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed stage 3 ports=9 error=-1")
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
    }

    @Test
    fun `a failed handshake right after a resume is held, and the whole start is restarted once`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = { call ->
            if (ShadowRecordingBridge.named("startConnection").size == 1) rtspFails()(call) else 0
        }
        val connection = rig.connection()

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection", count = 2)
        listener.await("stageComplete")
        waitForEvents("stageComplete", 2)

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
                "stageStarting $DESKTOP_NAME",
                "launchRetrying category=1 attempt=1 max=1 delay=3000",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths())
            .containsExactly("/serverinfo", "/resume", "/serverinfo", "/resume")
            .inOrder()
        assertThat(ShadowRecordingBridge.names)
            .containsExactly("setupBridge", "startConnection", "setupBridge", "startConnection")
            .inOrder()
        assertThat(connection.endedByResumeCollision()).isFalse()
    }

    @Test
    fun `a restart that is cancelled while it waits for the host ends without a word`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = rtspFails()
        val connection = rig.connection()
        listener.onEvent = { if (it.startsWith("launchRetrying")) connection.cancelLaunch() }

        rig.start(connection)
        listener.await("launchRetrying")
        waitForEvents("stageStarting", 2)

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
                "stageStarting $DESKTOP_NAME",
                "launchRetrying category=1 attempt=1 max=1 delay=3000",
            )
            .inOrder()
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
    }

    @Test
    fun `a handshake that fails again after the restart is reported without the ports, as a collision`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = rtspFails()
        val connection = rig.connection()

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection", count = 2)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.filter { it.startsWith("stageFailed") })
            .containsExactly("stageFailed RTSP handshake ports=0 error=-1")
        assertThat(connection.endedByResumeCollision()).isTrue()
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(2)
    }

    @Test
    fun `a restart that finds another app running does not quit it, and ends as a collision`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = { call ->
            // The other device took over: the host runs another app when the restart looks.
            rig.script.running(OTHER_ID)
            rtspFails()(call)
        }
        val connection = rig.connection()

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")
        listener.await("stageFailed")
        waitForEvents("stageStarting", 2)
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed serverinfo ports=0 error=0")
        assertThat(connection.endedByResumeCollision()).isTrue()
        assertThat(rig.paths()).doesNotContain("/cancel")
        assertThat(listener.events("launchConflict")).isEmpty()
    }

    /** Waits until [count] events start with [prefix], for a start that goes on in a new thread. */
    private fun waitForEvents(prefix: String, count: Int) {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (listener.events(prefix).size < count) {
            check(System.currentTimeMillis() < limit) {
                "Timed out waiting for $count x $prefix; events: ${listener.events}"
            }
            Thread.sleep(POLL_MS)
        }
        listener.threads.forEach { it.join(WAIT_MS) }
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 10L
    }
}
