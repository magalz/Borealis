package io.github.magalz.borealis.host.video

/**
 * Builds the [StreamStartupGuard] for the stream screen from plain plumbing, so the screen carries
 * no logic of its own.
 */
object StreamStartup {
    /**
     * @param terminateNoVideoFrame ends the stream through the screen's normal failure path with
     *   the "no video frame" error. Called on a background thread.
     * @param scheduler the watchdog clock; the main looper unless a test passes a fake.
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        terminateNoVideoFrame: Runnable,
        scheduler: WatchdogScheduler = HandlerWatchdogScheduler(),
    ): StreamStartupGuard =
        StreamStartupGuard(
            object : StreamStartupHost {
                override fun terminateNoVideoFrame() = terminateNoVideoFrame.run()
            },
            scheduler,
        )
}
