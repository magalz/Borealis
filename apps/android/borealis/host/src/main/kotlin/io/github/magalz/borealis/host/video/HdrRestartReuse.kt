package io.github.magalz.borealis.host.video

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Lets the decoder keep the IDR frame it is handed when it restarts to apply HDR metadata.
 *
 * The host announces HDR mode over the control stream, usually just before the first video packet,
 * after the decoder was configured without the HDR static info. The restart then happens while the
 * first IDR is being submitted, and without this the frame is dropped and a new IDR requested from
 * the host, which costs seconds. The restart is a deliberate reconfiguration, so the same IDR can
 * go to the restarted decoder, but configuring the decoder clears the stored VPS, SPS and PPS, so
 * the caller keeps a [snapshot] of them before the buffer fetch that may restart the decoder and
 * puts it back with [restoreForRetry].
 *
 * [requested] runs on the control-stream thread; [snapshot] and [restoreForRetry] run on the
 * decoder thread. The pending flag and the snapshot slot are atomics, the only shared state.
 *
 * A snapshot belongs to the IDR it was taken for and is dropped when restored, so a request that is
 * never consumed is harmless: [snapshot] copies again for the next IDR and [restoreForRetry] only
 * ever returns parameter sets taken from the frame being submitted.
 */
class HdrRestartReuse {
    private class Snapshot(
        val vps: List<ByteArray>,
        val sps: List<ByteArray>,
        val pps: List<ByteArray>,
    )

    private val pending = AtomicBoolean(false)
    private val saved = AtomicReference<Snapshot?>(null)

    /** The decoder is about to restart for new HDR metadata. */
    fun requested() {
        pending.set(true)
        BorealisLog.i(LogTag.VIDEO, "Restarting the decoder to apply HDR metadata")
    }

    /**
     * Keeps the parameter sets of the IDR being submitted, only while a restart is pending (a
     * single atomic read otherwise). Only the lists are copied: the arrays in them are never
     * modified after they are added, so sharing them is safe.
     */
    fun snapshot(vps: List<ByteArray>, sps: List<ByteArray>, pps: List<ByteArray>) {
        if (!pending.get()) return
        saved.set(Snapshot(vps.toList(), sps.toList(), pps.toList()))
    }

    /**
     * Called when the input buffer fetch of an IDR failed. Uses the pending request up; when it was
     * pending and a [snapshot] exists, replaces the contents of the (cleared) lists with it and
     * returns true: the caller may fetch once more and submit the same IDR on the restarted
     * decoder. Otherwise returns false and leaves the lists alone.
     */
    fun restoreForRetry(
        vps: MutableList<ByteArray>,
        sps: MutableList<ByteArray>,
        pps: MutableList<ByteArray>,
    ): Boolean {
        if (!pending.getAndSet(false)) return false
        val snapshot = saved.getAndSet(null) ?: return false
        vps.setTo(snapshot.vps)
        sps.setTo(snapshot.sps)
        pps.setTo(snapshot.pps)
        return true
    }

    private fun MutableList<ByteArray>.setTo(items: List<ByteArray>) {
        clear()
        addAll(items)
    }
}
