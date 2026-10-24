package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.nvhttp.HostReply
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Another app runs on the host: the connection waits for it to end, then asks the listener what to
 * do: Join (`/resume` it), Quit and relaunch, or Cancel. A prompt takes about three seconds, the
 * time that the connection reads `/serverinfo` again before it asks.
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
class StartConflictTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    private val conflict =
        "launchConflict running=$OTHER_ID/$OTHER_NAME requested=$DESKTOP_ID/$DESKTOP_NAME"

    /** The requests that are no `/serverinfo`, which the connection reads again and again. */
    private fun steps(): List<String> = rig.paths().filter { it != "/serverinfo" }

    private fun startAndConnect() {
        rig.start(rig.connection())
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()
    }

    private fun startAndFail() {
        rig.start(rig.connection())
        listener.await("stageFailed")
        listener.awaitEnded()
    }

    private fun raceWithOtherApp(reply: HostReply = RACE) {
        rig.script.launchReplies += reply
        rig.script.onLaunch = {
            if (rig.script.launchReplies.isNotEmpty()) rig.script.running(OTHER_ID)
        }
    }

    @Test
    fun `another app that keeps running is asked about, with its name and the name of the requested app`() {
        rig.script.running(OTHER_ID)
        listener.conflictChoice = ConflictChoice.JOIN

        startAndConnect()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                conflict,
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/serverinfo" }).isGreaterThan(1)
    }

    @Test
    fun `join resumes the app that runs, by its id`() {
        rig.script.running(OTHER_ID)
        listener.conflictChoice = ConflictChoice.JOIN

        startAndConnect()

        assertThat(steps()).containsExactly("/applist", "/resume").inOrder()
        assertThat(rig.request("/resume").value("appid")).isEqualTo("$OTHER_ID")
    }

    @Test
    fun `quit and relaunch ends the app that runs, then launches the app that was asked for`() {
        rig.script.running(OTHER_ID)
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH

        startAndConnect()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                conflict,
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/applist", "/cancel", "/launch").inOrder()
        assertThat(rig.request("/launch").value("appid")).isEqualTo("$DESKTOP_ID")
    }

    @Test
    fun `cancel ends the start without a word, and nothing is asked of the host`() {
        rig.script.running(OTHER_ID)
        listener.conflictChoice = ConflictChoice.CANCEL
        val connection = rig.connection()

        rig.start(connection)
        listener.await("launchConflict")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(steps()).containsExactly("/applist")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
        assertThat(connection.endedByResumeCollision()).isFalse()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
    }

    @Test
    fun `a start that is cancelled while it waits for the answer ends without a word`() {
        rig.script.running(OTHER_ID)
        listener.conflictChoice = null
        val connection = rig.connection()

        rig.start(connection)
        listener.await("launchConflict")
        connection.cancelLaunch()
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(steps()).containsExactly("/applist")
    }

    @Test
    fun `an app that ends while the connection waits is no conflict, and the app is launched`() {
        rig.script.running(OTHER_ID)
        rig.script.onServerInfo = { count ->
            if (count >= 2) {
                rig.script.state = HostScript.FREE
                rig.script.currentGame = 0
            }
        }

        startAndConnect()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/serverinfo", "/launch").inOrder()
    }

    @Test
    fun `a join that the host does not take says so, and fails at the resume step`() {
        rig.script.running(OTHER_ID)
        rig.script.resumeReplies += HostReply.root("<resume>0</resume>")
        listener.conflictChoice = ConflictChoice.JOIN

        startAndFail()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "launchStarting resume=true",
                "message Failed to resume existing session",
                "stageFailed resume ports=0 error=0",
            )
            .inOrder()
    }

    @Test
    fun `a join of a session that another device started says so`() {
        rig.script.running(OTHER_ID)
        rig.script.resumeReplies += HostReply.failure("470", "denied")
        listener.conflictChoice = ConflictChoice.JOIN

        startAndFail()

        assertThat(listener.events.drop(3))
            .containsExactly(
                "message This session wasn't started by this device, so it cannot be resumed. " +
                    "End streaming on the original device or the PC itself and try again. " +
                    "(Error code: 470)",
                "stageFailed resume ports=0 error=0",
            )
            .inOrder()
    }

    @Test
    fun `a join with no running app to resume launches the app instead`() {
        rig.script.running(OTHER_ID)
        rig.script.resumeReplies += HostReply.failure("503", "No running app to resume")
        listener.conflictChoice = ConflictChoice.JOIN

        startAndConnect()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "launchStarting resume=true",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/applist", "/resume", "/launch").inOrder()
    }

    @Test
    fun `a quit that the host does not take says so, and fails at the quit step`() {
        rig.script.running(OTHER_ID)
        rig.script.cancelReplies += HostReply.root("<cancel>0</cancel>")
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH

        startAndFail()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "message Failed to quit previous session! You must quit it manually",
                "stageFailed quit ports=0 error=0",
            )
            .inOrder()
        assertThat(rig.paths()).doesNotContain("/launch")
    }

    @Test
    fun `a quit of a session that another device started says so`() {
        rig.script.running(OTHER_ID)
        // The host says yes, and still runs the app: it is another device's session.
        rig.script.cancelReplies += HostReply.root("<cancel>1</cancel>")
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH

        startAndFail()

        assertThat(listener.events.drop(2))
            .containsExactly(
                "message This session wasn't started by this device, so it cannot be quit. End " +
                    "streaming on the original device or the PC itself. (Error code: 599)",
                "stageFailed quit ports=0 error=0",
            )
            .inOrder()
        assertThat(rig.paths()).doesNotContain("/launch")
    }

    @Test
    fun `a launch that the host refuses because an app runs is a conflict, and quit then launches`() {
        raceWithOtherApp()
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH

        startAndConnect()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                conflict,
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/launch", "/applist", "/cancel", "/launch").inOrder()
    }

    @Test
    fun `a launch that is refused again after the answer fails like any other, without a second question`() {
        raceWithOtherApp()
        rig.script.launchReplies += RACE
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events.last()).isEqualTo("stageFailed launch ports=0 error=400")
        assertThat(listener.events("launchConflict")).hasSize(1)
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
        assertThat(connection.lastHttpCode()).isEqualTo(400)
    }

    @Test
    fun `a launch that is refused because an app runs ends without a word when the user cancels`() {
        raceWithOtherApp()
        listener.conflictChoice = ConflictChoice.CANCEL

        rig.start(rig.connection())
        listener.await("launchConflict")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                conflict,
            )
            .inOrder()
    }

    @Test
    fun `a launch that is refused because an app ran, and it has ended, is launched again without a question`() {
        raceWithOtherApp()
        rig.script.onServerInfo = { count ->
            if (count >= 2) {
                rig.script.state = HostScript.FREE
                rig.script.currentGame = 0
            }
        }

        startAndConnect()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/launch" }).isEqualTo(2)
    }

    private companion object {
        val RACE: HostReply = HostReply.failure("400", "An app is already running on this host")
    }
}
