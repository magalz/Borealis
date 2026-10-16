package io.github.magalz.borealis.stream.ui.starting

import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord

/**
 * What a screen called, in order. A real session answers a conflict only while one is pending,
 * which a screen test has no use for.
 */
@Suppress("TooManyFunctions")
internal class RecordingIntents : StreamIntents {
    val calls = mutableListOf<String>()

    override fun openMenu() {
        calls += "openMenu"
    }

    override fun openKeyboard() {
        calls += "openKeyboard"
    }

    override fun closePanel() {
        calls += "closePanel"
    }

    override fun dismissMenuHint() {
        calls += "dismissMenuHint"
    }

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

    override fun setOverlay(mode: OverlayMode) {
        calls += "setOverlay $mode"
    }

    override fun disconnect() {
        calls += "disconnect"
    }

    override fun quitApp() {
        calls += "quitApp"
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
    }
}
