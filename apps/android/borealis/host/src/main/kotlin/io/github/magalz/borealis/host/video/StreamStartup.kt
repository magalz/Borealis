package io.github.magalz.borealis.host.video

import android.view.View
import io.github.magalz.borealis.host.R
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntConsumer

/**
 * Builds the [StreamStartupGuard] for the stream screen from plain plumbing, so the screen carries
 * no logic of its own. The screen owns the notification overlay; this object decides what to put on
 * it.
 */
object StreamStartup {
    /**
     * @param runOnUi runs an action on the UI thread.
     * @param setOverlayText sets the overlay text from a string resource id.
     * @param setRequestedVisibility records the visibility the overlay should have (the screen
     *   restores it when picture-in-picture ends).
     * @param applyVisibility sets the overlay view's visibility.
     * @param isHidingOverlays true while overlays are hidden (picture-in-picture); the view is then
     *   left alone.
     * @param terminateNoVideoFrame ends the stream through the screen's normal failure path with
     *   the "no video frame" error. Called on a background thread.
     * @param scheduler the watchdog clock; the main looper unless a test passes a fake.
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("LongParameterList") // One lambda per screen capability, plain plumbing from Game.
    fun create(
        runOnUi: Consumer<Runnable>,
        setOverlayText: IntConsumer,
        setRequestedVisibility: IntConsumer,
        applyVisibility: IntConsumer,
        isHidingOverlays: BooleanSupplier,
        terminateNoVideoFrame: Runnable,
        scheduler: WatchdogScheduler = HandlerWatchdogScheduler(),
    ): StreamStartupGuard {
        fun request(visibility: Int) {
            setRequestedVisibility.accept(visibility)
            if (!isHidingOverlays.asBoolean) applyVisibility.accept(visibility)
        }
        val host =
            object : StreamStartupHost {
                override fun showPreparing() {
                    setOverlayText.accept(R.string.stream_preparing_display)
                    request(View.VISIBLE)
                }

                override fun clearPreparing() = request(View.GONE)

                override fun runOnUi(action: Runnable) = runOnUi.accept(action)

                override fun terminateNoVideoFrame() = terminateNoVideoFrame.run()
            }
        return StreamStartupGuard(host, scheduler)
    }
}
