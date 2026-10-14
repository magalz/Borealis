package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditType

/**
 * Keeps the same event from filling the trail: the same type, host and detail is stored at most
 * once per [windowMillis] (ten minutes; `/serverinfo` is polled every 1.5 s). The window lives in
 * memory, so a process restart forgets it and the first event after the restart is stored again. It
 * is anchored on the event that was stored, not on the repeats that were dropped.
 */
class AuditDeduplicator(private val windowMillis: Long = WINDOW_MILLIS) {
    private data class Key(val type: AuditType, val host: String?, val detail: String?)

    private val lastStored = HashMap<Key, Long>()

    /** Number of events the window currently remembers. */
    val size: Int
        @Synchronized get() = lastStored.size

    /** True when [event] is not a repeat; the caller must then store it. */
    @Synchronized
    fun shouldStore(event: AuditEvent): Boolean {
        val now = event.timestampMillis
        lastStored.values.removeAll { now - it >= windowMillis }
        val key = Key(event.type, event.host, event.detail)
        val last = lastStored[key]
        // A clock that went backwards (now < last) is not a repeat either.
        if (last != null && now >= last) return false
        // A flood of different events must not grow the window without bound.
        if (lastStored.size >= MAX_KEYS) lastStored.clear()
        lastStored[key] = now
        return true
    }

    fun markStored(event: AuditEvent) {}

    @Synchronized
    fun clear() {
        lastStored.clear()
    }

    companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val MAX_KEYS = 1_024
    }
}
