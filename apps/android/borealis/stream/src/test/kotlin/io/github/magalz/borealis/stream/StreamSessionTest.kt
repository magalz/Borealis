package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import org.junit.Test

class StreamSessionTest {
    private val actions = RecordingActions()
    private val session = aSession(actions)

    private val phase: StreamPhase
        get() = session.state.value.phase

    private fun startStreaming(defaults: StreamDefaults = aDefaults): StreamSession =
        aSession(actions, defaults).also {
            it.onConnectionStarted(aSessionInfo)
            it.onFirstFrame()
        }

    private fun conflict(answer: ConflictAnswer = ConflictAnswer()): ConflictAnswer {
        session.onConflict("Steam", "Desktop", sameApp = false, answer = answer)
        return answer
    }

    @Test
    fun `starts connecting with the names it was given`() {
        assertThat(session.state.value)
            .isEqualTo(
                StreamUiState("Desktop", "Gaming PC", StreamPhase.Starting(StartStage.CONNECTING))
            )
        assertThat(session.perf.value).isNull()
    }

    @Test
    fun `a stage replaces the stage and clears the retry`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 3, delayMs = 2000)
        session.onLaunchRetry(retry)

        session.onStage(StartStage.PREPARING_DISPLAY)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `a retry keeps the stage and shows the retry`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 3, delayMs = 2000)
        session.onStage(StartStage.STARTING_APP)

        session.onLaunchRetry(retry)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP, retry))
    }

    @Test
    fun `a conflict shows the question`() {
        conflict()

        assertThat(phase).isEqualTo(StreamPhase.Conflict("Steam", "Desktop", sameApp = false))
    }

    @Test
    fun `the connection starting moves to the first frame`() {
        session.onConnectionStarted(aSessionInfo)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.FIRST_FRAME))
    }

    @Test
    fun `the first frame starts streaming with the defaults`() {
        val defaults =
            StreamDefaults(
                overlay = OverlayMode.COMPACT,
                touchMode = null,
                gamepadMouse = true,
                showMenuHint = false,
            )

        val started = startStreaming(defaults)

        assertThat(started.state.value.phase)
            .isEqualTo(
                StreamPhase.Streaming(
                    panel = Panel.NONE,
                    overlay = OverlayMode.COMPACT,
                    touchMode = null,
                    gamepadMouse = true,
                    info = aSessionInfo,
                    showMenuHint = false,
                )
            )
    }

    @Test
    fun `the first frame without a connection is ignored`() {
        session.onFirstFrame()

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))
    }

    @Test
    fun `a late stage, retry or connection does not undo streaming`() {
        val started = startStreaming()
        val before = started.state.value

        started.onStage(StartStage.CONNECTING)
        started.onLaunchRetry(LaunchRetry(null, 1, 1, 0))
        started.onConnectionStarted(aSessionInfo.copy(width = 1280))
        started.onConflict("Steam", "Desktop", false, ConflictAnswer())

        assertThat(started.state.value).isEqualTo(before)
    }

    @Test
    fun `a failure shows the error`() {
        session.onFailed(aFailure)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `a stream that ends with an error shows the error`() {
        val started = startStreaming()

        started.onFailed(aFailure)

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `no event changes a failure once it is shown`() {
        session.onFailed(aFailure)
        val failed = session.state.value

        session.onStage(StartStage.STARTING_APP)
        session.onLaunchRetry(LaunchRetry(LaunchCategory.PENDING, 1, 3, 1000))
        session.onConflict("Steam", "Desktop", true, ConflictAnswer())
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        session.onFailed(StreamFailure(FailureKind.HOST_OFFLINE, "other", null))
        session.openMenu()
        session.openKeyboard()

        assertThat(session.state.value).isEqualTo(failed)
    }

    @Test
    fun `perf snapshots update the perf flow only`() {
        val before = session.state.value

        session.onPerf(aPerf(totalFps = 61f))

        assertThat(session.perf.value).isEqualTo(aPerf(totalFps = 61f))
        assertThat(session.state.value).isEqualTo(before)
    }

    @Test
    fun `answering a conflict chooses once, forwards it and goes back to starting the app`() {
        val answer = conflict()

        session.answerConflict(ConflictChoice.QUIT_AND_RELAUNCH)

        assertThat(answer.choose(ConflictChoice.CANCEL)).isFalse()
        assertThat(actions.calls).containsExactly("answerConflict QUIT_AND_RELAUNCH")
        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `joining goes back to starting the app`() {
        conflict()

        session.answerConflict(ConflictChoice.JOIN)

        assertThat(actions.calls).containsExactly("answerConflict JOIN")
        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `cancelling a conflict is forwarded and leaves the dialog for the activity to close`() {
        val answer = conflict()

        session.answerConflict(ConflictChoice.CANCEL)

        assertThat(answer.choose(ConflictChoice.JOIN)).isFalse()
        assertThat(actions.calls).containsExactly("answerConflict CANCEL")
        assertThat(phase).isEqualTo(StreamPhase.Conflict("Steam", "Desktop", sameApp = false))
    }

    @Test
    fun `a second answer is ignored`() {
        conflict()
        session.answerConflict(ConflictChoice.JOIN)

        session.answerConflict(ConflictChoice.QUIT_AND_RELAUNCH)

        assertThat(actions.calls).containsExactly("answerConflict JOIN")
    }

    @Test
    fun `an answer that lost to an earlier choice is not forwarded`() {
        val answer = conflict()
        answer.choose(ConflictChoice.CANCEL)

        session.answerConflict(ConflictChoice.JOIN)

        assertThat(actions.calls).isEmpty()
        assertThat(phase).isEqualTo(StreamPhase.Conflict("Steam", "Desktop", sameApp = false))
    }

    @Test
    fun `an answer without a question is ignored`() {
        session.answerConflict(ConflictChoice.JOIN)

        assertThat(actions.calls).isEmpty()
        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))
    }

    @Test
    fun `the menu and the keyboard open only while streaming`() {
        session.openMenu()
        session.openKeyboard()
        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))

        conflict()
        session.openMenu()
        assertThat(phase).isInstanceOf(StreamPhase.Conflict::class.java)

        val started = startStreaming()
        started.openMenu()
        assertThat((started.state.value.phase as StreamPhase.Streaming).panel).isEqualTo(Panel.MENU)
        started.openKeyboard()
        assertThat((started.state.value.phase as StreamPhase.Streaming).panel)
            .isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `closing the panel returns to the stream`() {
        val started = startStreaming()
        started.openMenu()

        started.closePanel()

        assertThat((started.state.value.phase as StreamPhase.Streaming).panel).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the overlay, touch and gamepad mouse choices update the state and are forwarded`() {
        val started = startStreaming()

        started.setOverlay(OverlayMode.DETAILED)
        started.setTouchMode(TouchMode.DIRECT)
        started.setGamepadMouse(true)

        val now = started.state.value.phase as StreamPhase.Streaming
        assertThat(now.overlay).isEqualTo(OverlayMode.DETAILED)
        assertThat(now.touchMode).isEqualTo(TouchMode.DIRECT)
        assertThat(now.gamepadMouse).isTrue()
        assertThat(actions.calls)
            .containsExactly("setOverlay DETAILED", "setTouchMode DIRECT", "setGamepadMouse true")
            .inOrder()
    }

    @Test
    fun `dismissing the menu hint hides it and tells the activity`() {
        val started = startStreaming()

        started.dismissMenuHint()

        assertThat((started.state.value.phase as StreamPhase.Streaming).showMenuHint).isFalse()
        assertThat(actions.calls).containsExactly("menuHintShown")
    }

    @Test
    fun `every other intent is forwarded`() {
        val chord = KeyChord(0x09, setOf(KeyModifier.ALT))

        session.send(chord)
        session.sendText("hi")
        session.openSystemKeyboard()
        session.requestKeyframe()
        session.disconnect()
        session.quitApp()
        session.leave()
        session.retry()
        session.wakeHost()
        session.pairAgain()
        session.openDiagnostics()

        assertThat(actions.calls)
            .containsExactly(
                "send 9",
                "sendText hi",
                "openSystemKeyboard",
                "requestKeyframe",
                "disconnect",
                "quitApp",
                "leave",
                "retry",
                "wakeHost",
                "pairAgain",
                "openDiagnostics",
            )
            .inOrder()
    }
}
