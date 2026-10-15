package io.github.magalz.borealis.host.video

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.atomic.AtomicBoolean

/** Stub: the behaviour is specified by HdrRestartReuseTest and implemented in the next commit. */
class HdrRestartReuse {
    private val pending = AtomicBoolean(false)

    fun requested() {
        pending.set(true)
        BorealisLog.i(LogTag.VIDEO, "Restarting the decoder to apply HDR metadata")
    }

    fun snapshot(vps: List<ByteArray>, sps: List<ByteArray>, pps: List<ByteArray>) = Unit

    fun restoreForRetry(
        vps: MutableList<ByteArray>,
        sps: MutableList<ByteArray>,
        pps: MutableList<ByteArray>,
    ): Boolean = false
}
