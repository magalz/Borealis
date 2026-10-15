package io.github.magalz.borealis.library

import com.limelight.computers.ComputerManagerListener

/**
 * Decides when the service polls: while somebody collects and nobody has paused it. Not thread
 * safe; [ComputerManagerConnection] calls it with its lock held.
 */
internal class PollingGate(private val listener: ComputerManagerListener) {
    private var collectors = 0
    private var pauses = 0
    private var polling = false

    /** Counts a collector and starts polling if it is the first one. */
    fun addCollector(binder: CmsBinder) {
        collectors++
        var started = false
        try {
            reconcile(binder)
            started = true
        } finally {
            if (!started) collectors--
        }
    }

    fun removeCollector(binder: CmsBinder) {
        collectors--
        reconcile(binder)
    }

    fun pause(binder: CmsBinder) {
        pauses++
        reconcile(binder)
    }

    fun resume(binder: CmsBinder) {
        pauses--
        reconcile(binder)
    }

    private fun reconcile(binder: CmsBinder) {
        val wanted = collectors > 0 && pauses == 0
        if (wanted && !polling) {
            binder.startPolling(listener)
            polling = true
        } else if (!wanted && polling) {
            binder.stopPolling()
            polling = false
        }
    }
}
