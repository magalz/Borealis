package io.github.magalz.borealis.stream.testing

import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.StreamActions
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord

/** Records every call so a test can say what the session forwarded, and in which order. */
class RecordingActions : StreamActions {
    val calls = mutableListOf<String>()

    /** Runs inside [answerConflict], as the connection thread does once the answer is chosen. */
    var whenAnswered: () -> Unit = {}

    override fun send(chord: KeyChord) {
        calls += "send ${chord.vk}"
    }

    override fun sendText(text: String) {
        calls += "sendText $text"
    }

    override fun openSystemKeyboard() {
        calls += "openSystemKeyboard"
    }

    override fun requestKeyframe() {
        calls += "requestKeyframe"
    }

    override fun setTouchMode(mode: TouchMode) {
        calls += "setTouchMode $mode"
    }

    override fun setGamepadMouse(enabled: Boolean) {
        calls += "setGamepadMouse $enabled"
    }

    override fun setPointerSpeed(speed: PointerSpeed) {
        calls += "setPointerSpeed $speed"
    }

    override fun setOverlay(mode: OverlayMode) {
        calls += "setOverlay $mode"
    }

    override fun menuHintShown() {
        calls += "menuHintShown"
    }

    override fun disconnect(appName: String) {
        calls += "disconnect $appName"
    }

    /** Called by a test to say the quit did not happen, as the activity does. */
    var quitFailed: () -> Unit = {}

    override fun quitApp(onFailed: () -> Unit) {
        calls += "quitApp"
        quitFailed = onFailed
    }

    override fun leave() {
        calls += "leave"
    }

    override fun retry() {
        calls += "retry"
    }

    override fun wakeHost() {
        calls += "wakeHost"
    }

    override fun pairAgain() {
        calls += "pairAgain"
    }

    override fun openDiagnostics() {
        calls += "openDiagnostics"
    }

    override fun answerConflict(choice: ConflictChoice) {
        calls += "answerConflict $choice"
        whenAnswered()
    }
}
