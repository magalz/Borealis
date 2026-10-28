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
    fun `a stage before the current one is ignored while starting`() {
        session.onStage(StartStage.PREPARING_DISPLAY)

        session.onStage(StartStage.STARTING_APP)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `stages that come later still apply, and the same stage clears a retry`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 3, delayMs = 2000)
        session.onStage(StartStage.STARTING_APP)
        session.onStage(StartStage.PREPARING_DISPLAY)
        session.onLaunchRetry(retry)

        session.onStage(StartStage.PREPARING_DISPLAY)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `connecting stays the stage while the host asks for a retry, and sending the launch moves on`() {
        val retry =
            LaunchRetry(LaunchCategory.PENDING, attempt = 1, maxAttempts = 3, delayMs = 2000)

        session.onStage(StartStage.CONNECTING)
        session.onLaunchRetry(retry)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING, retry))

        session.onStage(StartStage.STARTING_APP)
        session.onStage(StartStage.CONNECTING)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
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
    fun `a first frame before the connection is reported starts streaming once it is`() {
        session.onFirstFrame()
        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))

        session.onConnectionStarted(aSessionInfo)

        assertThat(phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat((phase as StreamPhase.Streaming).info).isEqualTo(aSessionInfo)
    }

    @Test
    fun `a first frame that came early does not start streaming after a failure`() {
        session.onFirstFrame()
        session.onFailed(aFailure)

        session.onConnectionStarted(aSessionInfo)

        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `picture in picture is part of the state and keeps the phase`() {
        session.onFailed(aFailure)

        session.onPip(true)

        assertThat(session.state.value.inPip).isTrue()
        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))

        session.onPip(false)

        assertThat(session.state.value.inPip).isFalse()
        assertThat(phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `the phase has left the conflict when the choice is forwarded`() {
        var phaseWhenForwarded: StreamPhase? = null
        actions.whenAnswered = { phaseWhenForwarded = phase }
        conflict()

        session.answerConflict(ConflictChoice.JOIN)

        assertThat(phaseWhenForwarded).isEqualTo(StreamPhase.Starting(StartStage.STARTING_APP))
    }

    @Test
    fun `events the connection reports as soon as it is unblocked are kept`() {
        actions.whenAnswered = { session.onStage(StartStage.PREPARING_DISPLAY) }
        conflict()

        session.answerConflict(ConflictChoice.QUIT_AND_RELAUNCH)

        assertThat(phase).isEqualTo(StreamPhase.Starting(StartStage.PREPARING_DISPLAY))
    }

    @Test
    fun `a second conflict while one is open keeps the first answer slot`() {
        val first = conflict()
        val second = ConflictAnswer()
        session.onConflict("Other", "Desktop", sameApp = true, answer = second)

        session.answerConflict(ConflictChoice.JOIN)

        assertThat(first.choose(ConflictChoice.CANCEL)).isFalse()
        assertThat(second.choose(ConflictChoice.CANCEL)).isTrue()
    }

    @Test
    fun `a conflict after a failure leaves no answer behind`() {
        session.onFailed(aFailure)
        val answer = ConflictAnswer()

        session.onConflict("Steam", "Desktop", sameApp = false, answer = answer)
        session.answerConflict(ConflictChoice.JOIN)

        assertThat(answer.choose(ConflictChoice.CANCEL)).isTrue()
        assertThat(actions.calls).isEmpty()
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
    fun `a stream starts at the normal pointer speed`() {
        val started = startStreaming()

        assertThat((started.state.value.phase as StreamPhase.Streaming).pointerSpeed)
            .isEqualTo(PointerSpeed.NORMAL)
    }

    @Test
    fun `the pointer speed updates the state and is forwarded`() {
        val started = startStreaming()

        started.setPointerSpeed(PointerSpeed.FAST)

        assertThat((started.state.value.phase as StreamPhase.Streaming).pointerSpeed)
            .isEqualTo(PointerSpeed.FAST)
        assertThat(actions.calls).containsExactly("setPointerSpeed FAST")
    }

    @Test
    fun `the pointer speed is kept when the gamepad mouse is turned off and on`() {
        val started = startStreaming()
        started.setPointerSpeed(PointerSpeed.SLOW)

        started.setGamepadMouse(false)
        started.setGamepadMouse(true)

        assertThat((started.state.value.phase as StreamPhase.Streaming).pointerSpeed)
            .isEqualTo(PointerSpeed.SLOW)
    }

    @Test
    fun `dismissing the menu hint hides it and tells the activity`() {
        val started = startStreaming()

        started.dismissMenuHint()

        assertThat((started.state.value.phase as StreamPhase.Streaming).showMenuHint).isFalse()
        assertThat(actions.calls).containsExactly("menuHintShown")
    }

    @Test
    fun `an end of the stream while a quit is pending is quiet, not a failure`() {
        val started = startStreaming()
        val streaming = started.state.value.phase
        started.quitApp()

        started.onFailed(aFailure)

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Quitting(streaming, aFailure))
        assertThat(started.chromeMode).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `an end of the stream without a quit is still a failure`() {
        val started = startStreaming()

        started.onFailed(aFailure)

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `a quit that failed lets the next end be a failure`() {
        val started = startStreaming()
        started.quitApp()
        actions.quitFailed()

        started.onFailed(aFailure)

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `a quit that fails after the stream ended shows how the stream ended`() {
        val started = startStreaming()
        started.quitApp()
        started.onFailed(aFailure)

        actions.quitFailed()

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Failed(aFailure))
    }

    @Test
    fun `a second end while quitting changes nothing`() {
        val started = startStreaming()
        val streaming = started.state.value.phase
        started.quitApp()
        started.onFailed(aFailure)

        started.onFailed(StreamFailure(FailureKind.HOST_OFFLINE, "other", null))

        assertThat(started.state.value.phase).isEqualTo(StreamPhase.Quitting(streaming, aFailure))
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
