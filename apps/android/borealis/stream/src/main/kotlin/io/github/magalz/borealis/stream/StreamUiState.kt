package io.github.magalz.borealis.stream

import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.nativecore.LaunchCategory

/**
 * What the stream chrome shows: the app and the host it streams, the phase of the session and
 * whether the activity is in picture-in-picture, which hides the chrome and leaves the phase alone.
 * [visuals] are what the stream started with, and [decoderHeld] says whether a decoder of the
 * stream is, or is about to be, configured.
 */
data class StreamUiState(
    val appName: String,
    val hostName: String,
    val phase: StreamPhase,
    val inPip: Boolean = false,
    val visuals: StreamVisuals = StreamVisuals(),
    val decoderHeld: Boolean = false,
)

/**
 * What the stream draws besides the video, read once when the stream starts (ADR 0061): the glass
 * of the in-game menu, how the background behaves on the screens outside the game, and the style of
 * that background, the one the viewer chose in Settings. Its intensity is the one of the look.
 */
data class StreamVisuals(
    val menuGlass: MenuGlass = MenuGlass.BLUR,
    val background: BackgroundMode = BackgroundMode.MOVING,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.Default,
)

/** The glass of the in-game menu in a stream. */
enum class MenuGlass {
    /** A live copy of the video, blurred. */
    BLUR,

    /** A solid surface. */
    SOLID,
}

/** How the background behaves on the screens outside the game. */
enum class BackgroundMode {
    /** The style as it moves. */
    MOVING,

    /** One still frame of the style. */
    STILL,

    /** The plain page colour. */
    OFF,
}

/** The phase of a stream session, from the first connection step to the stream or a failure. */
sealed interface StreamPhase {
    /** The stream is starting; [retry] is set while the host asks Borealis to try again. */
    data class Starting(val stage: StartStage, val retry: LaunchRetry? = null) : StreamPhase

    /**
     * Another app is running on the host and the user must choose what to do. Cancelling leaves the
     * phase here; the activity finishes itself.
     */
    data class Conflict(
        val runningAppName: String?,
        val requestedAppName: String,
        val sameApp: Boolean,
    ) : StreamPhase

    /**
     * The stream could not start or ended with an error. Final: `retry()` starts the stream again
     * in a new activity and a new session.
     */
    data class Failed(val failure: StreamFailure) : StreamPhase

    /**
     * The user asked to quit the app and the host has not answered yet. [resumeTo] is the phase the
     * user was in, which a quit that fails returns to while the stream still runs; [ended] is how
     * the stream ended when the host ended it before it answered, and a quit that fails after that
     * turns this into [Failed] with [ended]. The screen is quiet; the activity finishes when the
     * quit answers.
     */
    data class Quitting(val resumeTo: StreamPhase, val ended: StreamFailure? = null) : StreamPhase

    /**
     * The user disconnected: the connection ends and the app keeps running on the PC. The screen
     * says so until the connection has stopped, and the activity finishes after it.
     */
    data object Disconnecting : StreamPhase

    /**
     * The first frame is on screen. [touchMode] is null when the device has no touchscreen and
     * [gamepadMouse] is null when there is no gamepad-as-mouse choice to offer. [pointerSpeed] is
     * the speed of the gamepad mouse for this session. [dockedKeyboard] says whether the keyboard
     * docks under the stream (the stream keeps the keys) or covers it (the chrome takes them, and a
     * pad can use it). It is decided when the keyboard opens and means nothing while none is open.
     */
    data class Streaming(
        val panel: Panel = Panel.NONE,
        val overlay: OverlayMode,
        val touchMode: TouchMode?,
        val gamepadMouse: Boolean?,
        val info: SessionInfo,
        val showMenuHint: Boolean,
        val pointerSpeed: PointerSpeed = PointerSpeed.NORMAL,
        val dockedKeyboard: Boolean = false,
    ) : StreamPhase
}

/** The steps of starting a stream, in order. */
enum class StartStage {
    CONNECTING,
    STARTING_APP,
    PREPARING_DISPLAY,
    FIRST_FRAME,
}

/** The panel open over the stream. */
enum class Panel {
    NONE,
    MENU,
    KEYBOARD,
}

/** How much the performance overlay shows. */
enum class OverlayMode {
    OFF,
    COMPACT,
    DETAILED,
}

/** How the touchscreen drives the pointer. */
enum class TouchMode {
    TRACKPAD,
    DIRECT,
    OFF,
}

/**
 * How fast the gamepad mouse moves in this session, as a [factor] of the speed in the settings. It
 * is the session's own choice and does not change the setting.
 */
enum class PointerSpeed(val factor: Float) {
    SLOW(0.5f),
    NORMAL(1f),
    FAST(2f),
}

/** What the chrome asks of the input layer: who gets the touches and the keys. */
enum class ChromeMode {
    /** Nothing is open and the overlay is off. */
    IDLE,

    /** Only the performance overlay is visible; the stream keeps the input. */
    OVERLAY,

    /** A panel is docked and the stream stays usable above it. */
    DOCKED,

    /** The chrome takes the input. */
    MODAL,

    /** The chrome is hidden, for example in picture-in-picture. */
    HIDDEN,
}

/** A retry that Borealis performs because the host asked for it: [attempt] of [maxAttempts]. */
data class LaunchRetry(
    val category: LaunchCategory?,
    val attempt: Int,
    val maxAttempts: Int,
    val delayMs: Int,
)

/** The negotiated stream, for the session info and the overlay. */
data class SessionInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val codec: String,
    val hdr: Boolean,
    val decoder: String?,
    val bitrateLimitKbps: Int,
)
