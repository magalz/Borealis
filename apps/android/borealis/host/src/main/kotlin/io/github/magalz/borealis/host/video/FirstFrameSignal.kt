package io.github.magalz.borealis.host.video

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Raised by the video decoder when it releases its first output buffer for rendering, from either
 * render path (the Choreographer callback or the renderer thread). It notifies [listener] at most
 * once per instance, on the rendering thread, so the listener must hand off quickly.
 *
 * The render path pays one atomic read per frame after the first: no allocation, no logging.
 */
class FirstFrameSignal {
    @Volatile var listener: Runnable? = null

    private val raised = AtomicBoolean(false)

    fun frameRendered() {
        if (raised.get()) return
        // Without a listener nobody heard the frame, so the signal is not used up.
        val target = listener ?: return
        if (raised.compareAndSet(false, true)) target.run()
    }
}
