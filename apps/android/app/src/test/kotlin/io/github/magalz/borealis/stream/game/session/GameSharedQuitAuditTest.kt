package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.game.GameTestBase
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * How the stream is audited when the user's Quit ends it while the host keeps the app for another
 * device: the host answers 599, and closes the stream with the quit's ENet disconnect, or with the
 * graceful termination when it sends that before it ends a cancelled session. The two can come in
 * either order, and the session takes both for a clean leave, whether the stream launched the app
 * or joined it. The audit says the same: the stream ended with a quit. Any other quit that failed,
 * and any other end, is audited as it always was.
 */
internal class GameSharedQuitAuditTest : GameTestBase() {
    @get:Rule val borealisLog = RecordingLog()

    private val shared = QuitResult.Failed("No", notTheStarter = true)

    private fun leftKeepingTheApp() =
        borealisLog.records.any { it.message == "quit of a shared app is a leave" }

    private fun sessionEnd() = rig.audit.single { it.type == AuditType.SESSION_END }

    private fun assertQuit() {
        assertThat(sessionEnd().outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(sessionEnd().detail).startsWith("reason=quit duration_s=")
    }

    private fun assertError(code: Int) {
        assertThat(sessionEnd().outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(sessionEnd().detail).startsWith("reason=error code=$code duration_s=")
    }

    private fun streamEnds(code: Int = ENET_DISCONNECT) {
        rig.game.connectionTerminated(code)
        rig.awaitStop()
    }

    @Test
    fun `the host answers 599 before the stream ends, and the clean leave is audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds()

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
    }

    @Test
    fun `the stream ends before the host answers 599, and the clean leave is audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        streamEnds()
        answer.complete(Unit)

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
    }

    @Test
    fun `another end in the window after the answer of 599 is audited as an error`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds(code = -100)

        assertError(-100)
    }

    @Test
    fun `an end of the stream after the window is audited as an error`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        quit.session.quitApp()
        quit.now = SHARED_QUIT_WINDOW_MS + 1

        streamEnds()

        assertError(ENET_DISCONNECT)
    }

    @Test
    fun `a session that launched the app is a clean leave too, audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        onConnectionThread { rig.game.launchStarting(false) }
        ShadowLooper.idleMainLooper()
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds()

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
        assertThat(leftKeepingTheApp()).isTrue()
    }

    @Test
    fun `another error of the quit does not make the end of the stream a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, QuitResult.Failed("Boom"))
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds()

        assertError(ENET_DISCONNECT)
    }

    @Test
    fun `a quit that is under way and ends the stream is audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, QuitResult.Quit)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        streamEnds()

        assertQuit()
    }

    // The graceful termination as the end of the stream: the activity finishes with no failure to
    // report, and the session has to know that the end was the quit's.

    @Test
    fun `the host answers 599 before the graceful end, and the clean leave is audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds(code = GRACEFUL)
        ShadowLooper.idleMainLooper()
        quit.runScheduled()

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
        assertThat(leftKeepingTheApp()).isTrue()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `the graceful end comes before the host answers 599, and the clean leave shows no toast`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        streamEnds(code = GRACEFUL)
        answer.complete(Unit)
        ShadowLooper.idleMainLooper()
        quit.runScheduled()

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
        assertThat(leftKeepingTheApp()).isTrue()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `a graceful end of a quit that is under way is audited as a quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, QuitResult.Quit)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        streamEnds(code = GRACEFUL)

        assertThat(rig.game.isFinishing).isTrue()
        assertQuit()
    }

    @Test
    fun `a graceful end after the window is audited as the host ending it, as it was`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        quit.session.quitApp()
        quit.now = SHARED_QUIT_WINDOW_MS + 1

        streamEnds(code = GRACEFUL)

        assertThat(rig.game.isFinishing).isTrue()
        assertThat(sessionEnd().outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(sessionEnd().detail).startsWith("reason=host_ended duration_s=")
        assertThat(leftKeepingTheApp()).isFalse()
    }
}

private const val GRACEFUL = MoonBridge.ML_ERROR_GRACEFUL_TERMINATION
