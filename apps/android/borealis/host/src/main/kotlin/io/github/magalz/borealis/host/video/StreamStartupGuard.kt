package io.github.magalz.borealis.host.video

import kotlin.concurrent.thread

/** What the guard needs from the stream screen. */
interface StreamStartupHost : StartupOverlay {
    /** Runs [action] on the UI thread. */
    fun runOnUi(action: Runnable)

    /**
     * Ends the stream through the normal failure path with "no video frame" (-101). That path does
     * network I/O, so the guard calls it off the main thread. The screen's own once-only dialog
     * guard keeps a late native termination from showing a second dialog.
     */
    fun terminateNoVideoFrame()
}

/**
 * Covers the time between "connection started" and the first rendered frame, which can be long on
 * LuminalShine (about 18 s): it shows the [PreparingMessage] and runs the [FirstFrameWatchdog] as
 * the last safety net behind the native -100/-101 deadlines.
 *
 * [onFirstFrame] may be called from any thread (the decoder's render path); everything else runs on
 * the UI thread, as do the watchdog timeout and the message updates.
 */
class StreamStartupGuard
@JvmOverloads
constructor(
    private val host: StreamStartupHost,
    scheduler: WatchdogScheduler,
    timeoutMillis: Long = FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS,
) {
    private val message = PreparingMessage(host)
    private val watchdog = FirstFrameWatchdog(scheduler, timeoutMillis) { onTimeout() }

    fun onConnectionStarted() {
        watchdog.arm()
        // Not armed means a frame already arrived or the guard was cancelled: keep the message
        // away.
        if (watchdog.state == FirstFrameWatchdog.State.ARMED) message.show()
    }

    fun onFirstFrame() {
        watchdog.onFirstFrame()
        host.runOnUi { message.hide() }
    }

    fun onWarning() = message.onWarning()

    fun onWarningCleared() = message.onWarningCleared()

    fun cancel() {
        watchdog.cancel()
        message.hide()
    }

    private fun onTimeout() {
        message.hide()
        thread(name = "Borealis first-frame timeout") { host.terminateNoVideoFrame() }
    }
}
