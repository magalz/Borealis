package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.shell.QuitNotice
import io.github.magalz.borealis.stream.keys.KeyChord
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Records what reaches the connection, as the sequence a host would see. */
class RecordingInput : InputSink {
    val events = mutableListOf<String>()

    override fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte, flags: Byte) {
        val key = "%04X".format(Locale.ROOT, keyMap.toInt() and 0xFFFF)
        val action = if (direction == DOWN) "down" else "up"
        events +=
            "$action $key mods %02X flags %d".format(Locale.ROOT, modifiers.toInt(), flags.toInt())
    }

    override fun sendUtf8Text(text: String) {
        events += "text $text"
    }

    override fun sendMouseButtonUp(button: Byte) {
        events += "mouse up $button"
    }

    override fun requestKeyframe() {
        events += "keyframe"
    }

    private companion object {
        const val DOWN: Byte = 0x03
    }
}

class FakeApps(var result: QuitResult = QuitResult.Quit) : AppRepository {
    val quits = mutableListOf<String>()

    /** When set, [quit] waits for it, as a request that is still under way. */
    var gate: CompletableDeferred<Unit>? = null

    override fun apps(hostId: String): Flow<AppsSnapshot> = emptyFlow()

    override suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean) = Unit

    override suspend fun quit(hostId: String): QuitResult {
        quits += hostId
        gate?.await()
        return result
    }
}

class FakeHosts(var result: WakeResult = WakeResult.SENT) : HostRepository {
    val wakes = mutableListOf<String>()

    override val hosts: Flow<List<Host>> = emptyFlow()

    override suspend fun addHost(address: HostAddress): AddHostResult = AddHostResult.Unreachable

    override suspend fun wake(hostId: String): WakeResult {
        wakes += hostId
        return result
    }

    override suspend fun forget(hostId: String) = Unit

    override suspend fun details(hostId: String): String? = null
}

class FakeEntryPoint(
    val apps: FakeApps = FakeApps(),
    val hosts: FakeHosts = FakeHosts(),
    val notice: QuitNotice = QuitNotice(),
) : StreamEntryPoint {
    override fun appRepository(): AppRepository = apps

    override fun hostRepository(): HostRepository = hosts

    override fun quitNotice(): QuitNotice = notice
}

/** Records what the session asks of the activity, by name. */
class RecordingStreamActions : StreamActions {
    val calls = mutableListOf<String>()

    override fun send(chord: KeyChord) {
        calls += "send"
    }

    override fun sendText(text: String) {
        calls += "text $text"
    }

    override fun openSystemKeyboard() {
        calls += "systemKeyboard"
    }

    override fun requestKeyframe() {
        calls += "keyframe"
    }

    override fun setTouchMode(mode: TouchMode) {
        calls += "touch $mode"
    }

    override fun setGamepadMouse(enabled: Boolean) {
        calls += "gamepadMouse $enabled"
    }

    override fun setPointerSpeed(speed: PointerSpeed) {
        calls += "pointerSpeed $speed"
    }

    override fun setOverlay(mode: OverlayMode) {
        calls += "overlay $mode"
    }

    override fun menuHintShown() {
        calls += "menuHintShown"
    }

    override fun disconnect(appName: String) {
        calls += "disconnect $appName"
    }

    override fun quitApp(onFailed: () -> Unit) {
        calls += "quit"
    }

    override fun leave() {
        calls += "leave"
    }

    override fun retry() {
        calls += "retry"
    }

    override fun wakeHost() {
        calls += "wake"
    }

    override fun pairAgain() {
        calls += "pair"
    }

    override fun openDiagnostics() {
        calls += "diagnostics"
    }

    override fun answerConflict(choice: ConflictChoice) {
        calls += "answer $choice"
    }
}

/** A session that is streaming, with the given starting state. */
fun streamingSession(
    overlay: OverlayMode = OverlayMode.OFF,
    gamepadMouse: Boolean? = false,
    actions: StreamActions = RecordingStreamActions(),
): StreamSession {
    val session =
        StreamSession(
            appName = "Desktop",
            hostName = "Den PC",
            defaults =
                StreamDefaults(overlay, TouchMode.TRACKPAD, gamepadMouse, showMenuHint = false),
            dockKeyboard = { false },
            actions = actions,
        )
    session.onConnectionStarted(SessionInfo(1920, 1080, 60, "HEVC", false, null, 20_000))
    session.onFirstFrame()
    return session
}
