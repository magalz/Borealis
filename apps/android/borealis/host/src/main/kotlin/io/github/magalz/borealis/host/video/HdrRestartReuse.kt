package io.github.magalz.borealis.host.video

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets the decoder keep the IDR frame it is handed when it restarts to apply HDR metadata.
 *
 * The host announces HDR mode over the control stream, usually just before the first video packet,
 * after the decoder was configured without the HDR static info. The restart then happens while the
 * first IDR is being submitted, and without this the frame is dropped and a new IDR requested from
 * the host, which costs seconds. The restart is a deliberate reconfiguration, so the stored CSD is
 * intact and the same IDR can go to the restarted decoder.
 *
 * [requested] runs on the control-stream thread, [retryAfterRecovery] on the decoder thread; the
 * single atomic flag is the only shared state.
 */
class HdrRestartReuse {
    private val pending = AtomicBoolean(false)

    /** The decoder is about to restart for new HDR metadata. */
    fun requested() {
        pending.set(true)
        BorealisLog.i(LogTag.VIDEO, "Restarting the decoder to apply HDR metadata")
    }

    /**
     * Whether the input buffer fetch that just failed because of a decoder recovery may be retried
     * once for the same frame. True at most once per [requested], and only for an IDR. Any call
     * uses the request up: after a non-IDR frame the decoder is already restarted, and the next IDR
     * lands on it normally, so a later unrelated recovery must not be mistaken for the HDR one.
     */
    fun retryAfterRecovery(isIdr: Boolean): Boolean = pending.getAndSet(false) && isIdr
}
