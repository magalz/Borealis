package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameIntent
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.assertToasts
import io.github.magalz.borealis.stream.game.phase
import org.junit.Test
import org.robolectric.shadows.ShadowLooper

/**
 * The callbacks of the connection that tell how the start goes: the stages, the launch of the app,
 * the question about another running app, the retries and the messages. The native library and the
 * connection thread call them, so what they do to the session reaches it through the main looper.
 */
internal class GameStageCallbacksTest : GameTestBase() {
    private val connecting = StreamPhase.Starting(StartStage.CONNECTING)

    // The stages

    @Test
    fun `the stage that names the app is the start itself and leaves the session connecting`() {
        rig.create()

        rig.game.stageStarting(GameIntent.APP_NAME)

        assertThat(rig.phase).isEqualTo(connecting)
    }

    @Test
    fun `a stage that does not name the app is the display being prepared`() {
        rig.create()

        rig.game.stageStarting("RTSP handshake")

        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `an app without a name is called app, and so is the stage that names it`() {
        rig.create(GameIntent(appName = null))

        rig.game.stageStarting("app")
        assertThat(rig.phase).isEqualTo(connecting)

        rig.game.stageStarting(GameIntent.APP_NAME)
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `a stage that the connection thread reports reaches the session on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.stageStarting("RTSP handshake") }
        assertThat(rig.phase).isEqualTo(connecting)

        ShadowLooper.idleMainLooper()
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `a stage that completes changes nothing`() {
        rig.create()
        rig.game.stageStarting("RTSP handshake")

        rig.game.stageComplete("RTSP handshake")

        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
        assertToasts()
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    // The launch of the app

    @Test
    fun `asking the host for the app is a stage of its own`() {
        rig.create()

        rig.game.launchStarting(false)

        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `resuming an app is the same stage as launching it`() {
        rig.create()

        rig.game.launchStarting(true)

        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `the launch that the connection thread reports reaches the session on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.launchStarting(false) }
        assertThat(rig.phase).isEqualTo(connecting)

        ShadowLooper.idleMainLooper()
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `a retry of the launch is shown on the stage that the start is in`() {
        rig.create()
        rig.game.launchStarting(false)

        rig.game.launchRetrying(LaunchCategory.PENDING.ordinal, 2, 5, 3000)

        assertThat(rig.phase)
            .isEqualTo(
                StreamPhase.Starting(
                    StartStage.STARTING_APP,
                    LaunchRetry(LaunchCategory.PENDING, 2, 5, 3000),
                )
            )
    }

    @Test
    fun `a retry for a category that this build does not know has no category`() {
        rig.create()

        rig.game.launchRetrying(LaunchCategory.entries.size, 1, 3, 500)

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Starting(StartStage.CONNECTING, LaunchRetry(null, 1, 3, 500)))
    }

    @Test
    fun `a retry that the connection thread reports reaches the session on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.launchRetrying(0, 1, 3, 500) }
        assertThat(rig.phase).isEqualTo(connecting)

        ShadowLooper.idleMainLooper()
        assertThat(rig.phase)
            .isEqualTo(
                StreamPhase.Starting(
                    StartStage.CONNECTING,
                    LaunchRetry(LaunchCategory.PERMANENT, 1, 3, 500),
                )
            )
    }

    // Another app is running on the host

    @Test
    fun `another running app is a conflict that names both apps`() {
        rig.create()

        rig.game.launchConflict(10, "Other App", 20, "Other Request", ConflictAnswer())

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Conflict("Other App", "Other Request", sameApp = false))
    }

    @Test
    fun `the same app that runs on the host is the same app of the conflict`() {
        rig.create()

        rig.game.launchConflict(
            GameIntent.APP_ID,
            "Desktop",
            GameIntent.APP_ID,
            "Desktop",
            ConflictAnswer(),
        )

        assertThat(rig.phase).isEqualTo(StreamPhase.Conflict("Desktop", "Desktop", sameApp = true))
    }

    @Test
    fun `a conflict without the name of the running app has none`() {
        rig.create()

        rig.game.launchConflict(10, null, 20, "Other Request", ConflictAnswer())

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Conflict(null, "Other Request", sameApp = false))
    }

    @Test
    fun `a conflict without the name of the requested app asks for the app of the intent`() {
        rig.create()

        rig.game.launchConflict(10, "Other App", 20, null, ConflictAnswer())

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Conflict("Other App", GameIntent.APP_NAME, sameApp = false))
    }

    @Test
    fun `a conflict with no name for the requested app at all asks for an empty name`() {
        rig.create(GameIntent(appName = null))

        rig.game.launchConflict(10, "Other App", 20, null, ConflictAnswer())

        assertThat(rig.phase).isEqualTo(StreamPhase.Conflict("Other App", "", sameApp = false))
    }

    @Test
    fun `the conflict that the connection thread reports reaches the session on the main thread`() {
        rig.create()

        onConnectionThread {
            rig.game.launchConflict(10, "Other App", 20, "Other Request", ConflictAnswer())
        }
        assertThat(rig.phase).isEqualTo(connecting)

        ShadowLooper.idleMainLooper()
        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Conflict("Other App", "Other Request", sameApp = false))
    }

    // The messages

    @Test
    fun `a message of the start is no toast, it waits for the error screen`() {
        rig.create()

        rig.game.displayMessage("This device is not paired with the host.")

        assertToasts()
        assertThat(rig.phase).isEqualTo(connecting)
    }

    @Test
    fun `a message of the start is shown under the code when the start fails`() {
        rig.create()
        rig.game.displayMessage("This device is not paired with the host.")

        rig.game.stageFailed("Launch", 0, 7)

        assertThat(rig.phase)
            .isEqualTo(
                StreamPhase.Failed(
                    StreamFailure(
                        FailureKind.GENERIC,
                        "Error 7 · Launch",
                        "This device is not paired with the host.",
                    )
                )
            )
    }

    @Test
    fun `the last message of the start is the one that the error screen shows`() {
        rig.create()
        rig.game.displayMessage("The first note.")
        rig.game.displayMessage("The last note.")

        rig.game.stageFailed("Launch", 0, 7)

        val failed = rig.phase as StreamPhase.Failed
        assertThat(failed.failure.portHint).isEqualTo("The last note.")
    }

    @Test
    fun `a warning during the stream is a toast`() {
        rig.create()

        rig.game.displayTransientMessage("The host changed the display.")

        assertToasts("The host changed the display.")
    }

    @Test
    fun `the setting that disables warnings silences the toast of a warning`() {
        rig.preference("checkbox_disable_warnings", true)
        rig.create()

        rig.game.displayTransientMessage("The host changed the display.")

        assertToasts()
    }

    @Test
    fun `the warning that the connection thread reports is a toast once the main thread runs it`() {
        rig.create()

        onConnectionThread { rig.game.displayTransientMessage("The host changed the display.") }
        assertToasts()

        ShadowLooper.idleMainLooper()
        assertToasts("The host changed the display.")
    }
}
