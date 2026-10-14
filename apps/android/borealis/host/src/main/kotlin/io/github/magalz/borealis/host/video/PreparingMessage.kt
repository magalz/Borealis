package io.github.magalz.borealis.host.video

/** What the preparing message needs from the screen: show it, and take it away again. */
interface StartupOverlay {
    fun showPreparing()

    fun clearPreparing()
}

/**
 * The non-modal "host is preparing the display" message, shown from the moment the connection
 * starts until the first frame is rendered. It lives on the notification overlay next to the
 * connection warnings, so it must give way to them.
 *
 * - A connection warning takes precedence ([onWarning]); the message comes back if the warning
 *   clears while the stream is still starting ([onWarningCleared]).
 * - [hide] ends the message for good (first frame, timeout, cancel, stop). It removes the message
 *   only while the message is on the overlay: a warning that is up stays, so the overlay then
 *   reflects the last connection status alone.
 *
 * Main thread only. [show] never reaches the overlay again after [hide].
 */
class PreparingMessage(private val overlay: StartupOverlay) {
    private var wanted = false
    private var ended = false
    private var warning = false
    private var displayed = false

    fun show() {
        if (ended || wanted) return
        wanted = true
        display()
    }

    fun hide() {
        ended = true
        wanted = false
        if (displayed) {
            displayed = false
            overlay.clearPreparing()
        }
    }

    fun onWarning() {
        warning = true
        displayed = false
    }

    fun onWarningCleared() {
        warning = false
        display()
    }

    private fun display() {
        if (!wanted || warning || displayed) return
        displayed = true
        overlay.showPreparing()
    }
}
