package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * What the activity tells the session when it asks the host for the app: a resume joins an app that
 * ran already, which decides what a 599 answer to a Quit means (see `StreamSession`): the host
 * keeps the app for the device that started it. A launch starts the app, so the 599 means nothing
 * more than a failed Quit.
 */
internal class GameJoinedAppTest : GameTestBase() {
    private val shared = QuitResult.Failed("Not yours", notTheStarter = true)

    /** The connection thread asks the host for the app, [resume] or not, and the user quits. */
    private fun startedThenQuit(resume: Boolean): SharedQuitRig {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared, joined = false)
        onConnectionThread { rig.game.launchStarting(resume) }
        ShadowLooper.idleMainLooper()
        quit.session.quitApp()
        return quit
    }

    @Test
    fun `a stream that resumes the app holds the 599 answer to its Quit, as a joined app does`() {
        val quit = startedThenQuit(resume = true)

        assertThat(quit.scheduled.map { it.first }).containsExactly(SHARED_QUIT_WINDOW_MS)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(quit.session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    @Test
    fun `a stream that launches the app shows the 599 answer to its Quit at once`() {
        val quit = startedThenQuit(resume = false)

        assertThat(quit.scheduled).isEmpty()
        assertThat(ShadowToast.showedToast("Not yours")).isTrue()
        assertThat(quit.session.state.value.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    @Test
    fun `the last request counts, so a launch after a resume is no joined app`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, shared, joined = false)
        onConnectionThread {
            rig.game.launchStarting(true)
            rig.game.launchStarting(false)
        }
        ShadowLooper.idleMainLooper()

        quit.session.quitApp()

        assertThat(quit.scheduled).isEmpty()
        assertThat(ShadowToast.showedToast("Not yours")).isTrue()
    }
}
