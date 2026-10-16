package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.keys.KeyChord

/** What the stream screens call. [StreamSession] implements it. */
@Suppress("TooManyFunctions") // One call per thing a stream screen can do.
interface StreamIntents {
    fun openMenu()

    fun openKeyboard()

    fun closePanel()

    /** The user dismissed the menu hint. */
    fun dismissMenuHint()

    fun send(chord: KeyChord)

    fun sendText(text: String)

    fun openSystemKeyboard()

    fun requestKeyframe()

    fun setTouchMode(mode: TouchMode)

    fun setGamepadMouse(enabled: Boolean)

    fun setOverlay(mode: OverlayMode)

    fun disconnect()

    fun quitApp()

    fun leave()

    fun retry()

    fun wakeHost()

    fun pairAgain()

    fun openDiagnostics()

    fun answerConflict(choice: ConflictChoice)
}
