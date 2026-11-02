package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.game.GameTestBase
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test

/**
 * How the stream is audited when the user's Quit of an app that another device started ends it: the
 * host keeps the app and answers 599, and closes the stream with the quit's ENet disconnect. The two
 * can come in either order, and the session takes both for a clean leave. The audit says the same:
 * the stream ended with a quit. Any other quit that failed, and any other end, is audited as it
 * always was.
 */
internal class GameSharedQuitAuditTest : GameTestBase() {
    private val shared = QuitResult.Failed("No", notTheStarter = true)

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
    fun `a session that launched the app has no clean leave, and its end after the 599 is an error`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared, joined = false)
        quit.session.quitApp()
        quit.now = 1_000L

        streamEnds()

        assertError(ENET_DISCONNECT)
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
}
