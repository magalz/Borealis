package io.github.magalz.borealis.host.video

import kotlin.concurrent.thread

/**
 * Runs the [FirstFrameWatchdog] from "connection started" to the first rendered frame, which can be
 * long on LuminalShine (about 18 s). It is the last safety net behind the native -100/-101
 * deadlines; the Starting screen is what the user sees during the wait.
 *
 * [onFirstFrame] may be called from any thread (the decoder's render path). The watchdog timeout
 * runs on the scheduler's thread, the main looper in production.
 *
 * @param terminate ends the stream through the screen's normal failure path with "no video frame"
 *   (-101). That path does network I/O, so the guard runs it on a background thread. The screen's
 *   own once-only dialog guard keeps a late native termination from showing a second dialog.
 * @param scheduler the watchdog clock; the main looper unless a test passes a fake.
 */
class StreamStartupGuard
@JvmOverloads
constructor(
    private val terminate: Runnable,
    scheduler: WatchdogScheduler = HandlerWatchdogScheduler(),
    timeoutMillis: Long = FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS,
) {
    private val watchdog = FirstFrameWatchdog(scheduler, timeoutMillis) { onTimeout() }

    fun onConnectionStarted() = watchdog.arm()

    fun onFirstFrame() = watchdog.onFirstFrame()

    fun cancel() = watchdog.cancel()

    private fun onTimeout() {
        thread(name = "Borealis first-frame timeout") { terminate.run() }
    }
}
