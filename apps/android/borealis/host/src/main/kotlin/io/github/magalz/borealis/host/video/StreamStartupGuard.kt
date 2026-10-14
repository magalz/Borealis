package io.github.magalz.borealis.host.video

/** What the guard needs from the stream screen. */
interface StreamStartupHost : StartupOverlay {
    /** Runs [action] on the UI thread. */
    fun runOnUi(action: Runnable)

    /** Ends the stream through the normal failure path with "no video frame". Called off the main thread. */
    fun terminateNoVideoFrame()
}

/** Stub, replaced by the implementation commit. */
class StreamStartupGuard(
    private val host: StreamStartupHost,
    private val scheduler: WatchdogScheduler,
    private val timeoutMillis: Long = FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS,
) {
    fun onConnectionStarted() = Unit

    fun onFirstFrame() = Unit

    fun onWarning() = Unit

    fun onWarningCleared() = Unit

    fun cancel() = Unit
}
