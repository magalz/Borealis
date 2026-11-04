package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.shell.LeaveNotice
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * What the request that starts the stream means for its Quit: nothing. A host that keeps the app
 * for another device (the one that started it, or one that joined) answers the Quit of a stream
 * that launched the app as it does the Quit of one that resumed it, with 599. The answer is held
 * for the window, and a stream that ends with the quit's end in it leaves cleanly, with the notice
 * for the Library that the app keeps running for another device, and no toast.
 */
internal class GameLaunchRequestQuitTest : GameTestBase() {
    private val shared = QuitResult.Failed("Not yours", notTheStarter = true)

    /** The connection thread asks the host for the app, [resume] or not. */
    private fun requested(resume: Boolean): SharedQuitRig {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared)
        onConnectionThread { rig.game.launchStarting(resume) }
        ShadowLooper.idleMainLooper()
        return quit
    }

    private fun noticeOrNull(quit: SharedQuitRig): LeaveNotice? {
        var notice: LeaveNotice? = null
        runTest { notice = withTimeoutOrNull(1) { quit.notice.notices.first() } }
        return notice
    }

    private fun assertLeftKeepingTheApp(quit: SharedQuitRig) {
        assertThat(rig.game.isFinishing).isTrue()
        assertThat(noticeOrNull(quit)).isEqualTo(LeaveNotice.KeepsRunning("Desktop"))
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        val end = rig.audit.single { it.type == AuditType.SESSION_END }
        assertThat(end.detail).startsWith("reason=quit duration_s=")
    }

    @Test
    fun `a stream that launches the app holds the 599 answer to its Quit`() {
        val quit = requested(resume = false)

        quit.session.quitApp()

        assertThat(quit.scheduled.map { it.first }).containsExactly(SHARED_QUIT_WINDOW_MS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(quit.session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    @Test
    fun `a stream that resumes the app holds the 599 answer to its Quit`() {
        val quit = requested(resume = true)

        quit.session.quitApp()

        assertThat(quit.scheduled.map { it.first }).containsExactly(SHARED_QUIT_WINDOW_MS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(quit.session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    @Test
    fun `a stream that launches the app and goes on shows the 599 answer after the window`() {
        val quit = requested(resume = false)
        quit.session.quitApp()

        quit.now = SHARED_QUIT_WINDOW_MS
        quit.runScheduled()

        assertThat(ShadowToast.showedToast("Not yours")).isTrue()
        assertThat(rig.game.isFinishing).isFalse()
    }

    @Test
    fun `a stream that launches the app leaves the Library the notice when the end follows the 599`() {
        val quit = requested(resume = false)
        quit.session.quitApp()
        quit.now = 1_000L

        rig.game.connectionTerminated(ENET_DISCONNECT)
        rig.awaitStop()

        assertLeftKeepingTheApp(quit)
    }

    @Test
    fun `a stream that launches the app leaves the Library the notice when the graceful end comes first`() {
        val quit = requested(resume = false)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()
        answer.complete(Unit)
        ShadowLooper.idleMainLooper()
        quit.runScheduled()

        assertLeftKeepingTheApp(quit)
    }

    @Test
    fun `a stream that resumes the app leaves the Library the same notice`() {
        val quit = requested(resume = true)
        quit.session.quitApp()
        quit.now = 1_000L

        rig.game.connectionTerminated(ENET_DISCONNECT)
        rig.awaitStop()

        assertLeftKeepingTheApp(quit)
    }
}
