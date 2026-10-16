package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.keys.KeyChord
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What a stream starts with: the user's settings when the first frame arrives. */
data class StreamDefaults(
    val overlay: OverlayMode,
    val touchMode: TouchMode?,
    val gamepadMouse: Boolean?,
    val showMenuHint: Boolean,
)

/**
 * The state of one stream session, for the chrome. The stream activity reports what happens with
 * the `on...` events, which are safe to call from any thread; the screens read [state] and [perf]
 * and call the [StreamIntents], which [actions] carries out on the main thread.
 *
 * A failure is final: once the session is [StreamPhase.Failed], no later event changes it.
 */
@Suppress("TooManyFunctions")
class StreamSession(
    appName: String,
    hostName: String,
    private val defaults: StreamDefaults,
    private val dockedKeyboard: Boolean,
    private val actions: StreamActions,
) : StreamIntents {
    private val mutableState =
        MutableStateFlow(
            StreamUiState(appName, hostName, StreamPhase.Starting(StartStage.CONNECTING))
        )
    private val mutablePerf = MutableStateFlow<PerfSnapshot?>(null)

    // Not part of the phase: the first frame needs the info that the connection reported earlier,
    // and the answer slot belongs to the connection thread that is waiting for it.
    private val sessionInfo = AtomicReference<SessionInfo?>(null)
    private val pendingAnswer = AtomicReference<ConflictAnswer?>(null)

    @Volatile private var inPip = false

    val state: StateFlow<StreamUiState> = mutableState

    val perf: StateFlow<PerfSnapshot?> = mutablePerf

    /** Who gets the input and what the chrome covers; readable from any thread. */
    val chromeMode: ChromeMode
        get() {
            val phase = mutableState.value.phase
            return when {
                inPip -> ChromeMode.HIDDEN
                phase !is StreamPhase.Streaming -> ChromeMode.MODAL
                else -> streamingChromeMode(phase)
            }
        }

    private fun streamingChromeMode(phase: StreamPhase.Streaming): ChromeMode =
        when (phase.panel) {
            Panel.MENU -> ChromeMode.MODAL
            Panel.KEYBOARD -> if (dockedKeyboard) ChromeMode.DOCKED else ChromeMode.MODAL
            Panel.NONE ->
                if (phase.overlay != OverlayMode.OFF) ChromeMode.OVERLAY else ChromeMode.IDLE
        }

    /** A step of the start is reached; clears a retry that was showing. */
    fun onStage(stage: StartStage) = whileStarting { StreamPhase.Starting(stage) }

    /** The host asked for a retry of the current stage. */
    fun onLaunchRetry(retry: LaunchRetry) = whileStarting { StreamPhase.Starting(it.stage, retry) }

    /** Another app runs on the host; the user answers through [answerConflict]. */
    fun onConflict(
        runningAppName: String?,
        requestedAppName: String,
        sameApp: Boolean,
        answer: ConflictAnswer,
    ) {
        pendingAnswer.set(answer)
        whileStarting { StreamPhase.Conflict(runningAppName, requestedAppName, sameApp) }
    }

    /** The connection is up and waits for the first frame. */
    fun onConnectionStarted(info: SessionInfo) {
        sessionInfo.set(info)
        whileStarting { StreamPhase.Starting(StartStage.FIRST_FRAME) }
    }

    /** The first frame is on screen. Ignored until [onConnectionStarted] reported the stream. */
    fun onFirstFrame() {
        val info = sessionInfo.get() ?: return
        whileStarting {
            StreamPhase.Streaming(
                overlay = defaults.overlay,
                touchMode = defaults.touchMode,
                gamepadMouse = defaults.gamepadMouse,
                info = info,
                showMenuHint = defaults.showMenuHint,
            )
        }
    }

    /** The start failed or the stream ended with an error. The first failure stays. */
    fun onFailed(failure: StreamFailure) = mutableState.update {
        if (it.phase is StreamPhase.Failed) it else it.copy(phase = StreamPhase.Failed(failure))
    }

    fun onPerf(snapshot: PerfSnapshot) {
        mutablePerf.value = snapshot
    }

    fun onPip(inPip: Boolean) {
        this.inPip = inPip
    }

    /** Replaces the phase while the stream is starting; later events cannot undo a later phase. */
    private inline fun whileStarting(next: (StreamPhase.Starting) -> StreamPhase) =
        mutableState.update {
            val phase = it.phase
            if (phase is StreamPhase.Starting) it.copy(phase = next(phase)) else it
        }

    private inline fun whileStreaming(change: (StreamPhase.Streaming) -> StreamPhase.Streaming) =
        mutableState.update {
            val phase = it.phase
            if (phase is StreamPhase.Streaming) it.copy(phase = change(phase)) else it
        }

    override fun openMenu() = whileStreaming { it.copy(panel = Panel.MENU) }

    override fun openKeyboard() = whileStreaming { it.copy(panel = Panel.KEYBOARD) }

    override fun closePanel() = whileStreaming { it.copy(panel = Panel.NONE) }

    override fun dismissMenuHint() {
        whileStreaming { it.copy(showMenuHint = false) }
        actions.menuHintShown()
    }

    override fun setOverlay(mode: OverlayMode) {
        whileStreaming { it.copy(overlay = mode) }
        actions.setOverlay(mode)
    }

    override fun setTouchMode(mode: TouchMode) {
        whileStreaming { it.copy(touchMode = mode) }
        actions.setTouchMode(mode)
    }

    override fun setGamepadMouse(enabled: Boolean) {
        whileStreaming { it.copy(gamepadMouse = enabled) }
        actions.setGamepadMouse(enabled)
    }

    override fun answerConflict(choice: ConflictChoice) {
        if (mutableState.value.phase !is StreamPhase.Conflict) return
        val answer = pendingAnswer.getAndSet(null) ?: return
        if (!answer.choose(choice)) return
        actions.answerConflict(choice)
        if (choice != ConflictChoice.CANCEL) {
            mutableState.update {
                if (it.phase is StreamPhase.Conflict) {
                    it.copy(phase = StreamPhase.Starting(StartStage.STARTING_APP))
                } else {
                    it
                }
            }
        }
    }

    override fun send(chord: KeyChord) = actions.send(chord)

    override fun sendText(text: String) = actions.sendText(text)

    override fun openSystemKeyboard() = actions.openSystemKeyboard()

    override fun requestKeyframe() = actions.requestKeyframe()

    override fun disconnect() = actions.disconnect()

    override fun quitApp() = actions.quitApp()

    override fun leave() = actions.leave()

    override fun retry() = actions.retry()

    override fun wakeHost() = actions.wakeHost()

    override fun pairAgain() = actions.pairAgain()

    override fun openDiagnostics() = actions.openDiagnostics()
}
