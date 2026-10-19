package io.github.magalz.borealis.host.video

import kotlin.concurrent.thread

/** What the guard needs from the stream screen. */
interface StreamStartupHost {
    /**
     * Ends the stream through the normal failure path with "no video frame" (-101). That path does
     * network I/O, so the guard calls it off the main thread. The screen's own once-only dialog
     * guard keeps a late native termination from showing a second dialog.
     */
    fun terminateNoVideoFrame()
}

/**
 * Runs the [FirstFrameWatchdog] from "connection started" to the first rendered frame, which can be
 * long on LuminalShine (about 18 s). It is the last safety net behind the native -100/-101
 * deadlines; the Starting screen is what the user sees during the wait.
 *
 * [onFirstFrame] may be called from any thread (the decoder's render path). The watchdog timeout
 * runs on the scheduler's thread, the main looper in production.
 */
class StreamStartupGuard
@JvmOverloads
constructor(
    private val host: StreamStartupHost,
    scheduler: WatchdogScheduler,
    timeoutMillis: Long = FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS,
) {
    private val watchdog = FirstFrameWatchdog(scheduler, timeoutMillis) { onTimeout() }

    fun onConnectionStarted() = watchdog.arm()

    fun onFirstFrame() = watchdog.onFirstFrame()

    fun cancel() = watchdog.cancel()

    private fun onTimeout() {
        thread(name = "Borealis first-frame timeout") { host.terminateNoVideoFrame() }
    }
}
