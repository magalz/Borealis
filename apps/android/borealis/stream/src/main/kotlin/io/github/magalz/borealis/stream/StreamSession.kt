package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.keys.KeyChord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a stream starts with: the user's settings when the first frame arrives. */
data class StreamDefaults(
    val overlay: OverlayMode,
    val touchMode: TouchMode?,
    val gamepadMouse: Boolean?,
    val showMenuHint: Boolean,
)

/**
 * The state of one stream session, for the chrome. The stream activity reports what happens with
 * the `on...` events, from any thread; the screens read [state] and [perf] and call the
 * [StreamIntents].
 */
@Suppress("UnusedParameter", "EmptyFunctionBlock", "TooManyFunctions")
class StreamSession(
    appName: String,
    hostName: String,
    defaults: StreamDefaults,
    dockedKeyboard: Boolean,
    actions: StreamActions,
) : StreamIntents {
    private val mutableState =
        MutableStateFlow(
            StreamUiState(appName, hostName, StreamPhase.Starting(StartStage.CONNECTING))
        )
    private val mutablePerf = MutableStateFlow<PerfSnapshot?>(null)

    val state: StateFlow<StreamUiState> = mutableState

    val perf: StateFlow<PerfSnapshot?> = mutablePerf

    val chromeMode: ChromeMode
        get() = ChromeMode.IDLE

    fun onStage(stage: StartStage) {}

    fun onLaunchRetry(retry: LaunchRetry) {}

    fun onConflict(
        runningAppName: String?,
        requestedAppName: String,
        sameApp: Boolean,
        answer: ConflictAnswer,
    ) {}

    fun onConnectionStarted(info: SessionInfo) {}

    fun onFirstFrame() {}

    fun onFailed(failure: StreamFailure) {}

    fun onPerf(snapshot: PerfSnapshot) {}

    fun onPip(inPip: Boolean) {}

    override fun openMenu() {}

    override fun openKeyboard() {}

    override fun closePanel() {}

    override fun dismissMenuHint() {}

    override fun send(chord: KeyChord) {}

    override fun sendText(text: String) {}

    override fun openSystemKeyboard() {}

    override fun requestKeyframe() {}

    override fun setTouchMode(mode: TouchMode) {}

    override fun setGamepadMouse(enabled: Boolean) {}

    override fun setOverlay(mode: OverlayMode) {}

    override fun disconnect() {}

    override fun quitApp() {}

    override fun leave() {}

    override fun retry() {}

    override fun wakeHost() {}

    override fun pairAgain() {}

    override fun openDiagnostics() {}

    override fun answerConflict(choice: ConflictChoice) {}
}
