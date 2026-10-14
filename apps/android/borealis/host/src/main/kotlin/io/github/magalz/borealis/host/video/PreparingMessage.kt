package io.github.magalz.borealis.host.video

/** What the preparing message needs from the screen: show it, and take it away again. */
interface StartupOverlay {
    fun showPreparing()

    fun clearPreparing()
}

/** Stub, replaced by the implementation commit. */
class PreparingMessage(private val overlay: StartupOverlay) {
    fun show() = Unit

    fun hide() = Unit

    fun onWarning() = Unit

    fun onWarningCleared() = Unit
}
