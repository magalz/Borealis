package io.github.magalz.borealis.stream

import android.os.Handler
import android.os.Looper
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.stream.keys.KeyChord
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What a stream starts with: the user's settings when the first frame arrives, and the [visuals] of
 * the stream, which it keeps from the start. [mouseNavButtons] says whether the Back button of the
 * mouse is a button of its own (the setting of that name) and so can open the menu.
 */
data class StreamDefaults(
    val overlay: OverlayMode,
    val touchMode: TouchMode?,
    val gamepadMouse: Boolean?,
    val showMenuHint: Boolean,
    val visuals: StreamVisuals = StreamVisuals(),
    val mouseNavButtons: Boolean = false,
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
 * ends the stream, until the quit answers. A disconnect puts it at [StreamPhase.Disconnecting] at
 * once, and nothing that happens later changes it: the activity finishes itself.
 *
 * A host that keeps the app for another device cancels only the session of the one that asks, so
 * its answer to a quit can be 599 ([QuitFailure.notTheStarter]) and it ends only this stream, with
 * the quit's ENet disconnect or the graceful termination ([endedByQuit]). It does that whoever
 * started the app, so the session does not tell a stream that launched the app from one that joined
 * it. That pair, in either order, is a clean leave and no failure: the session asks the
 * [QuitFailure] to leave, and the activity finishes with a notice for the Library. The graceful
 * termination reports no failure, so the activity says that it ended the stream with
 * [onGracefulEnd] instead of [onFailed]. With the 599 answered first the stream still runs, and the
 * answer is held for [SHARED_QUIT_WINDOW_MS] before it is shown, which gives the end of the stream
 * the time to come. Anything else, another error of the quit or another end of the stream, goes the
 * way it always went. A held answer is dropped when the user leaves, with a disconnect or a leave,
 * and when the connection stops: nothing is left to wait for, no end of the stream is the quit's
 * any more, and no toast may follow the activity.
 *
 * The keyboard is docked or covering as [dockKeyboard] says at the moment it opens, and keeps that
 * type until it closes (see [StreamPhase.Streaming.dockedKeyboard]): a pad that connects or
 * disconnects while it is open does not change it.
 *
 * A notice ([notify]) is no part of the phase and changes neither it nor the input: the card that
 * shows it takes no touch and no key. The notices wait in a queue of their own, one [notice] at a
 * time.
 */
@Suppress(
    "TooManyFunctions", // One method per stream event and per StreamIntents call.
    "LongParameterList", // The clock and the scheduler are the seams of the tests.
)
class StreamSession(
    appName: String,
    hostName: String,
    private val defaults: StreamDefaults,
    private val dockKeyboard: () -> Boolean,
    private val actions: StreamActions,
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
    private val schedule: (Long, () -> Unit) -> Unit = ::postToMainThread,
) : StreamIntents {
    private val mutableState =
        MutableStateFlow(
            StreamUiState(
                appName,
                hostName,
                StreamPhase.Starting(StartStage.CONNECTING),
                visuals = defaults.visuals,
            )
        )
    private val mutablePerf = MutableStateFlow<PerfSnapshot?>(null)

    // Not part of the phase: the first frame and the connection report from different threads and
    // in either order, and the answer slot belongs to the connection thread that waits for it.
    private val sessionInfo = AtomicReference<SessionInfo?>(null)
    private val firstFrameSeen = AtomicBoolean(false)
    private val pendingAnswer = AtomicReference<ConflictAnswer?>(null)

    // When the quit was accepted, for the time that the end of the stream is logged with.
    @Volatile private var quitAcceptedAtMs = 0L

    // True once endedByQuit took the end of the stream for the quit's, from the connection thread.
    @Volatile private var endedByTheQuit = false

    // The 599 answer to a quit while the stream still runs: held until the window ends, so the end
    // of the stream that follows is still seen as the quit's.
    private val heldQuit = AtomicReference<HeldQuit?>(null)

    private class HeldQuit(val failure: QuitFailure, val answeredAtMs: Long)

    val state: StateFlow<StreamUiState> = mutableState

    val perf: StateFlow<PerfSnapshot?> = mutablePerf

    // The notice on show, and the ones that wait behind it, which the lock on the queue guards for
    // both: the audio and the connection give notices from threads of their own.
    private val mutableNotice = MutableStateFlow<StreamNotice?>(null)
    private val waitingNotices = ArrayDeque<StreamNotice>()

    /**
     * The notice that the card shows now, or null. Notices come one at a time, in the order they
     * were given: when the card has shown this one, it calls [noticeShown] and the next one, if
     * any, is here.
     */
    val notice: StateFlow<StreamNotice?> = mutableNotice

    /**
     * Tells the viewer something once, in the card that goes by itself, which shows [notice] when
     * it is its turn. Safe to call from any thread. A notice that is the one on show, or the last
     * one in line, is dropped: it says nothing new, and two equal notices in a row would leave the
     * card on the second with nothing to tell it that its time starts again.
     */
    fun notify(notice: StreamNotice) {
        synchronized(waitingNotices) {
            val showing = mutableNotice.value
            if (showing == null) {
                mutableNotice.value = notice
            } else if (notice != showing && notice != waitingNotices.lastOrNull()) {
                waitingNotices.addLast(notice)
            }
        }
    }

    /**
     * The card has shown [notice] for its time, so the next notice takes its place. Only the notice
     * on show ends: a late call for another one changes nothing.
     */
    fun noticeShown(notice: StreamNotice) {
        synchronized(waitingNotices) {
            if (mutableNotice.value == notice) {
                mutableNotice.value = waitingNotices.removeFirstOrNull()
            }
        }
    }

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
     *
     * The first step after the app is requested is the first stage of the connection itself, which
     * comes before the stream's decoder is configured (at the stage that establishes the video
     * stream). A decoder is held from that step on (see [StreamUiState.decoderHeld]), so the clip
     * of the background is released a few stages before the decoder is configured, and no handshake
     * with the connection thread is needed.
     */
    fun onStage(stage: StartStage) = mutableState.update {
        val phase = it.phase
        when {
            phase !is StreamPhase.Starting || stage < phase.stage -> it
            else ->
                it.copy(
                    phase = StreamPhase.Starting(stage),
                    decoderHeld = it.decoderHeld || stage >= StartStage.PREPARING_DISPLAY,
                )
        }
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
        holdDecoder()
        if (firstFrameSeen.get()) {
            startStreaming(info)
        } else {
            whileStarting { StreamPhase.Starting(StartStage.FIRST_FRAME) }
        }
    }

    /** The first frame is on screen; streams once [onConnectionStarted] has reported the stream. */
    fun onFirstFrame() {
        firstFrameSeen.set(true)
        holdDecoder()
        sessionInfo.get()?.let(::startStreaming)
    }

    private fun startStreaming(info: SessionInfo) = whileStarting {
        StreamPhase.Streaming(
            overlay = defaults.overlay,
            touchMode = defaults.touchMode,
            gamepadMouse = defaults.gamepadMouse,
            info = info,
            showMenuHint = defaults.showMenuHint,
            mouseNavButtons = defaults.mouseNavButtons,
        )
    }

    /**
     * The start failed or the stream ended with an error. The first failure stays. While the phase
     * is [StreamPhase.Quitting] the end is the quit, not a failure: the host ends the stream before
     * it answers the cancel, so the phase stays and keeps how the stream ended. The end that a held
     * 599 waits for (see [endedByQuit]) is the quit's and no failure either: the session leaves.
     */
    fun onFailed(failure: StreamFailure) {
        if (leaveForHeldQuit()) return
        mutableState.update {
            val phase = it.phase
            when {
                phase is StreamPhase.Failed || phase is StreamPhase.Disconnecting -> it
                phase is StreamPhase.Quitting ->
                    if (phase.ended == null) it.copy(phase = phase.copy(ended = failure)) else it
                else -> it.copy(phase = StreamPhase.Failed(failure))
            }
        }
    }

    // The quit did not happen: a stream that already ended with it ended by failing, and one that
    // still runs goes on as it was. Unless the host keeps the app for another device (see the
    // class), which is a clean leave when the stream ended with the quit.
    private fun onQuitFailed(failure: QuitFailure) {
        val sharedApp = failure.notTheStarter
        if (sharedApp && endedByTheQuit) {
            leaveKeepingApp(failure)
            return
        }
        // Held before the phase leaves Quitting, so an end that comes between is still the quit's.
        val held = if (sharedApp) HeldQuit(failure, clockMs()).also(heldQuit::set) else null
        var stillRuns = false
        mutableState.update {
            val phase = it.phase
            stillRuns = phase is StreamPhase.Quitting && phase.ended == null
            if (phase is StreamPhase.Quitting) {
                it.copy(phase = phase.ended?.let(StreamPhase::Failed) ?: phase.resumeTo)
            } else {
                it
            }
        }
        if (held != null && stillRuns) {
            schedule(SHARED_QUIT_WINDOW_MS) {
                if (heldQuit.compareAndSet(held, null)) failure.show()
            }
        } else {
            if (held != null) heldQuit.compareAndSet(held, null)
            failure.show()
        }
    }

    // The 599 answer came first, and the end of the stream that it holds for is here: leaves.
    private fun leaveForHeldQuit(): Boolean {
        val held = heldQuit.get()
        if (held == null || !endedByTheQuit || !heldQuit.compareAndSet(held, null)) return false
        leaveKeepingApp(held.failure)
        return true
    }

    private fun leaveKeepingApp(failure: QuitFailure) {
        BorealisLog.i(
            LogTag.APP,
            "quit of a shared app is a leave",
            "since_quit_ms" to clockMs() - quitAcceptedAtMs,
        )
        failure.leaveKeepingApp(mutableState.value.appName)
    }

    /**
     * The stream ended with [code], reported from the connection thread. True when that is the end
     * of the quit the user asked for, which the log then says with the time since the quit: the
     * host closes the stream when it gets the cancel, and the control stream reports that as the
     * ENet disconnect, [ENET_DISCONNECT], or as the graceful termination, [GRACEFUL_TERMINATION],
     * when the host sends that before it closes a cancelled session. Any other code while a quit is
     * pending is a network drop and goes the way of a failure.
     *
     * A quit whose 599 answer came first is still waiting for this end: it counts for
     * [SHARED_QUIT_WINDOW_MS] from the answer. The host closes the stream when it gets the cancel,
     * before it answers, but a stream that goes on after the answer can end at any later time, by
     * the network or by the host: only an end within the window is taken for the quit's.
     */
    fun endedByQuit(code: Int): Boolean {
        val quitting = mutableState.value.phase is StreamPhase.Quitting
        val quitEnd = code == ENET_DISCONNECT || code == GRACEFUL_TERMINATION
        if (!quitEnd || !(quitting || heldQuitOpen())) return false
        endedByTheQuit = true
        BorealisLog.i(
            LogTag.APP,
            "quit stream ended",
            "code" to code,
            "since_quit_ms" to clockMs() - quitAcceptedAtMs,
        )
        return true
    }

    /**
     * The activity finishes after the graceful termination that [endedByQuit] took for the quit's.
     * It reports no failure for that end, so [onFailed] does not come: the 599 answer that was held
     * for it leaves here, as it does there. Called on the main thread; a graceful end that was not
     * the quit's changes nothing.
     */
    fun onGracefulEnd() {
        leaveForHeldQuit()
    }

    private fun heldQuitOpen(): Boolean {
        val held = heldQuit.get() ?: return false
        return clockMs() - held.answeredAtMs <= SHARED_QUIT_WINDOW_MS
    }

    /**
     * The connection has stopped, or none ever ran, and the decoder of the stream is released: a
     * clip of the background plays again. The activity calls it on the main thread: posted from the
     * thread that stopped the connection once the stop ends, or at once when none ran.
     */
    fun onConnectionStopped() {
        heldQuit.set(null)
        mutableState.update { it.copy(decoderHeld = false) }
    }

    // The stream is up, so its decoder is: whatever stage events came before.
    private fun holdDecoder() = mutableState.update { it.copy(decoderHeld = true) }

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

    override fun disconnect() {
        if (!startLeaving { StreamPhase.Disconnecting }) return
        heldQuit.set(null)
        BorealisLog.i(LogTag.APP, "disconnect accepted")
        actions.disconnect(mutableState.value.appName)
    }

    override fun quitApp() {
        if (!startLeaving(StreamPhase::Quitting)) return
        // A new quit takes the answer of an older one with it: this one has its own to give.
        heldQuit.set(null)
        quitAcceptedAtMs = clockMs()
        BorealisLog.i(LogTag.APP, "quit accepted")
        actions.quitApp(::onQuitFailed)
    }

    // Moves to the phase that [leaving] makes from the current one, unless the session failed or
    // is leaving already; true when it moved. A quit and a disconnect shut each other out.
    private inline fun startLeaving(leaving: (StreamPhase) -> StreamPhase): Boolean {
        var started = false
        mutableState.update {
            val phase = it.phase
            started =
                phase !is StreamPhase.Failed &&
                    phase !is StreamPhase.Quitting &&
                    phase !is StreamPhase.Disconnecting
            if (started) it.copy(phase = leaving(phase)) else it
        }
        return started
    }

    override fun leave() {
        heldQuit.set(null)
        actions.leave()
    }

    override fun retry() = actions.retry()

    override fun wakeHost() = actions.wakeHost()

    override fun pairAgain() = actions.pairAgain()

    override fun openDiagnostics() = actions.openDiagnostics()
}

/**
 * What the stream tells the viewer once, in a card that goes by itself ([StreamSession.notify]).
 */
sealed interface StreamNotice {
    /** The sound goes out over Bluetooth, which can make it lag behind the picture. */
    data object BluetoothOutput : StreamNotice

    /** The sound output has two channels, so the stream is stereo where [asked] was requested. */
    data class StereoFallback(val asked: SurroundLayout) : StreamNotice

    /** The host takes no touch events, so the touch mode went from Native to Direct. */
    data object NativeTouchRefused : StreamNotice
}

/** The surround layouts that a stream can ask for, which a stereo output cannot play. */
enum class SurroundLayout {
    SURROUND_5_1,
    SURROUND_7_1,
}

private const val NANOS_PER_MS = 1_000_000L

private fun postToMainThread(delayMs: Long, action: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed({ action() }, delayMs)
}

/**
 * How long after a 599 answer to a quit the end of the stream is still taken for the quit's
 * ([StreamSession.endedByQuit]), and how long that answer is held before it is shown.
 */
private const val SHARED_QUIT_WINDOW_MS = 5_000L

/**
 * The code that the control stream of moonlight-common-c reports when its ENet peer disconnects
 * (`ControlStream.c`, `ENET_EVENT_TYPE_DISCONNECT`), which the host causes by closing the stream.
 */
private const val ENET_DISCONNECT = -1

/**
 * The code that the control stream reports when the host sends the graceful termination
 * (`ML_ERROR_GRACEFUL_TERMINATION`). A host that sends it before it disconnects a cancelled session
 * (NortheBridge/luminalshine#232) ends the user's Quit with it, and not with [ENET_DISCONNECT].
 */
private const val GRACEFUL_TERMINATION = 0
