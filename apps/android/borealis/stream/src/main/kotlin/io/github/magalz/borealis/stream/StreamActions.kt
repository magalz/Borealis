package io.github.magalz.borealis.stream

import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.keys.KeyChord

/** What the stream activity does for the chrome. Every call arrives on the main thread. */
@Suppress("TooManyFunctions") // One call per thing the chrome can ask of the stream.
interface StreamActions {
    /** Sends [chord]: its modifiers go down, then the key, then everything is released. */
    fun send(chord: KeyChord)

    /** Types [text] on the host. */
    fun sendText(text: String)

    /** Opens the Android soft keyboard. */
    fun openSystemKeyboard()

    /** Asks the host for a keyframe, to repair a smeared picture. */
    fun requestKeyframe()

    fun setTouchMode(mode: TouchMode)

    fun setGamepadMouse(enabled: Boolean)

    fun setPointerSpeed(speed: PointerSpeed)

    fun setOverlay(mode: OverlayMode)

    /** The user has seen the menu hint, so it is not shown again. */
    fun menuHintShown()

    /**
     * Ends the connection; the app named [appName] (blank when the name is not known) keeps running
     * on the PC.
     */
    fun disconnect(appName: String)

    /**
     * Ends the app on the PC and the connection. [onFailed] runs on the main thread when the quit
     * did not happen.
     */
    fun quitApp(onFailed: () -> Unit)

    /** Leaves the stream screen without ending anything. */
    fun leave()

    /** Starts the connection again. */
    fun retry()

    /** Sends Wake-on-LAN to the host. */
    fun wakeHost()

    /** Starts pairing again, after the host identity changed. */
    fun pairAgain()

    /** Opens the privacy and diagnostics screen. */
    fun openDiagnostics()

    /** Applies the user's choice for the app that is already running on the host. */
    fun answerConflict(choice: ConflictChoice)
}
