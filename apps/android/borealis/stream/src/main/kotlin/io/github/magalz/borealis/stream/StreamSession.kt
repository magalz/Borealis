package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.keys.KeyChord
import java.util.concurrent.atomic.AtomicBoolean
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
 * and call the [StreamIntents].
 *
 * Threading: the [StreamIntents] methods are called on the main thread (the gamepad menu chord
 * fires there too) and forward to [actions] on that thread.
 *
 * A failure is final: once the session is [StreamPhase.Failed], no later event changes it, and
 * `retry()` starts the stream again in a new activity and a new session. Cancelling a conflict
 * leaves the phase at [StreamPhase.Conflict]; the activity finishes itself. A quit that is asked
 * for puts the phase at [StreamPhase.Quitting] at once and keeps it there, whether or not the host
 * ends the stream, until the quit answers.
 *
 * The keyboard is docked or covering as [dockKeyboard] says at the moment it opens, and keeps that
 * type until it closes (see [StreamPhase.Streaming.dockedKeyboard]): a pad that connects or
 * disconnects while it is open does not change it.
 */
@Suppress("TooManyFunctions") // One method per stream event and per StreamIntents call.
class StreamSession(
    appName: String,
    hostName: String,
    private val defaults: StreamDefaults,
    private val dockKeyboard: () -> Boolean,
    private val actions: StreamActions,
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) : StreamIntents {
    private val mutableState =
        MutableStateFlow(
            StreamUiState(appName, hostName, StreamPhase.Starting(StartStage.CONNECTING))
        )
    private val mutablePerf = MutableStateFlow<PerfSnapshot?>(null)

    // Not part of the phase: the first frame and the connection report from different threads and
    // in either order, and the answer slot belongs to the connection thread that waits for it.
    private val sessionInfo = AtomicReference<SessionInfo?>(null)
    private val firstFrameSeen = AtomicBoolean(false)
    private val pendingAnswer = AtomicReference<ConflictAnswer?>(null)

    val state: StateFlow<StreamUiState> = mutableState

    val perf: StateFlow<PerfSnapshot?> = mutablePerf

    /** Who gets the input and what the chrome covers; one read of [state], from any thread. */
    val chromeMode: ChromeMode
        get() {
            val current = mutableState.value
            val phase = current.phase
            return when {
                current.inPip -> ChromeMode.HIDDEN
                phase !is StreamPhase.Streaming -> ChromeMode.MODAL
                else -> streamingChromeMode(phase)
            }
        }

    private fun streamingChromeMode(phase: StreamPhase.Streaming): ChromeMode =
        when (phase.panel) {
            Panel.MENU -> ChromeMode.MODAL
            Panel.KEYBOARD -> if (phase.dockedKeyboard) ChromeMode.DOCKED else ChromeMode.MODAL
            Panel.NONE ->
                if (phase.overlay != OverlayMode.OFF) ChromeMode.OVERLAY else ChromeMode.IDLE
        }

    /**
     * A step of the start is reached; clears a retry that was showing. A step before the current
     * one is ignored, so the list of steps never goes backwards whatever order the events arrive
     * in.
     */
    fun onStage(stage: StartStage) = whileStarting {
        if (stage < it.stage) it else StreamPhase.Starting(stage)
    }

    /** The host asked for a retry of the current stage. */
    fun onLaunchRetry(retry: LaunchRetry) = whileStarting { StreamPhase.Starting(it.stage, retry) }

    /** Another app runs on the host; the user answers through [answerConflict]. */
    fun onConflict(
        runningAppName: String?,
        requestedAppName: String,
        sameApp: Boolean,
        answer: ConflictAnswer,
    ) {
        // The slot is taken before the phase changes, so an answer never finds a Conflict without
        // it; an event that does not enter the Conflict phase gives the slot back.
        pendingAnswer.compareAndSet(null, answer)
        var entered = false
        mutableState.update {
            entered = it.phase is StreamPhase.Starting
            if (entered) {
                it.copy(phase = StreamPhase.Conflict(runningAppName, requestedAppName, sameApp))
            } else {
                it
            }
        }
        if (!entered) pendingAnswer.compareAndSet(answer, null)
    }

    /**
     * The connection is up and waits for the first frame; streams at once when the frame came
     * first. The two events come from different threads, so each one records itself before it looks
     * for the other: whichever runs second sees both.
     */
    fun onConnectionStarted(info: SessionInfo) {
        sessionInfo.set(info)
        if (firstFrameSeen.get()) {
            startStreaming(info)
        } else {
            whileStarting { StreamPhase.Starting(StartStage.FIRST_FRAME) }
        }
    }

    /** The first frame is on screen; streams once [onConnectionStarted] has reported the stream. */
    fun onFirstFrame() {
        firstFrameSeen.set(true)
        sessionInfo.get()?.let(::startStreaming)
    }

    private fun startStreaming(info: SessionInfo) = whileStarting {
        StreamPhase.Streaming(
            overlay = defaults.overlay,
            touchMode = defaults.touchMode,
            gamepadMouse = defaults.gamepadMouse,
            info = info,
            showMenuHint = defaults.showMenuHint,
        )
    }

    /**
     * The start failed or the stream ended with an error. The first failure stays. While the phase
     * is [StreamPhase.Quitting] the end is the quit, not a failure: the host ends the stream before
     * it answers the cancel, so the phase stays and keeps how the stream ended.
     */
    fun onFailed(failure: StreamFailure) = mutableState.update {
        val phase = it.phase
        when {
            phase is StreamPhase.Failed -> it
            phase is StreamPhase.Quitting ->
                if (phase.ended == null) it.copy(phase = phase.copy(ended = failure)) else it
            else -> it.copy(phase = StreamPhase.Failed(failure))
        }
    }

    // The quit did not happen: a stream that already ended with it ended by failing, and one that
    // still runs goes on as it was.
    private fun onQuitFailed() {
        mutableState.update {
            val phase = it.phase
            if (phase is StreamPhase.Quitting) {
                it.copy(phase = phase.ended?.let(StreamPhase::Failed) ?: phase.resumeTo)
            } else {
                it
            }
        }
    }

    /**
     * The stream ended with [code], reported from the connection thread. True when that is the end
     * of the quit the user asked for, which the log then says with the time since the quit.
     */
    fun endedByQuit(code: Int): Boolean = false

    fun onPerf(snapshot: PerfSnapshot) {
        mutablePerf.value = snapshot
    }

    /** The activity entered or left picture-in-picture; the phase is untouched. */
    fun onPip(inPip: Boolean) = mutableState.update { it.copy(inPip = inPip) }

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

    override fun openKeyboard() {
        // Asked here, not inside the update, which can run again.
        val docked = dockKeyboard()
        whileStreaming { it.copy(panel = Panel.KEYBOARD, dockedKeyboard = docked) }
    }

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

    override fun setPointerSpeed(speed: PointerSpeed) {
        whileStreaming { it.copy(pointerSpeed = speed) }
        actions.setPointerSpeed(speed)
    }

    override fun answerConflict(choice: ConflictChoice) {
        val conflict = mutableState.value.phase as? StreamPhase.Conflict ?: return
        val answer = pendingAnswer.getAndSet(null) ?: return
        // The phase leaves the conflict before the answer unblocks the connection thread: what that
        // thread reports next must find the session in Starting, not in the question it answered.
        val leaves = choice != ConflictChoice.CANCEL
        if (leaves) {
            mutableState.update {
                if (it.phase is StreamPhase.Conflict) {
                    it.copy(phase = StreamPhase.Starting(StartStage.STARTING_APP))
                } else {
                    it
                }
            }
        }
        if (!answer.choose(choice)) {
            if (leaves) {
                mutableState.update {
                    if (it.phase == StreamPhase.Starting(StartStage.STARTING_APP)) {
                        it.copy(phase = conflict)
                    } else {
                        it
                    }
                }
            }
            return
        }
        actions.answerConflict(choice)
    }

    override fun send(chord: KeyChord) = actions.send(chord)

    override fun sendText(text: String) = actions.sendText(text)

    override fun openSystemKeyboard() = actions.openSystemKeyboard()

    override fun requestKeyframe() = actions.requestKeyframe()

    override fun disconnect() = actions.disconnect()

    override fun quitApp() {
        var asked = false
        mutableState.update {
            val phase = it.phase
            asked = phase !is StreamPhase.Failed && phase !is StreamPhase.Quitting
            if (asked) it.copy(phase = StreamPhase.Quitting(phase)) else it
        }
        if (asked) actions.quitApp(::onQuitFailed)
    }

    override fun leave() = actions.leave()

    override fun retry() = actions.retry()

    override fun wakeHost() = actions.wakeHost()

    override fun pairAgain() = actions.pairAgain()

    override fun openDiagnostics() = actions.openDiagnostics()
}

private const val NANOS_PER_MS = 1_000_000L
