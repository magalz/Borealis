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
     * did not happen, with the [QuitFailure] that says why and lets the session choose what the
     * activity does about it: nothing is shown or left until the session says so.
     */
    fun quitApp(onFailed: (QuitFailure) -> Unit)

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

/**
 * A quit that did not happen, as [StreamActions.quitApp] reports it, with the two things the
 * activity can do about it. The session calls one of them, on the main thread, when it has judged
 * the failure ([StreamSession]): the activity does neither on its own.
 */
interface QuitFailure {
    /**
     * True when the host answered that another device started the session of the app (the synthetic
     * 599 of `NvHTTP.quitApp`). A host that keeps the app for that device answers it to the Quit of
     * a device that joined, and ends the stream of the one that asked.
     */
    val notTheStarter: Boolean

    /** Tells the viewer why the quit did not happen; the activity stays where it is. */
    fun show()

    /**
     * The Quit of a joined app only ended this stream, and [appName] (blank when not known) keeps
     * running for the device that started it: leaves the Library the notice that says so, and
     * finishes the activity.
     */
    fun leaveKeepingApp(appName: String)
}
