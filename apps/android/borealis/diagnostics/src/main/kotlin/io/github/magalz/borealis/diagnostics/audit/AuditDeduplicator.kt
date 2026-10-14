package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditType

/**
 * Keeps the events that repeat on every `/serverinfo` poll (every 1.5 s) from filling the trail: a
 * changed host certificate or a TLS failure is stored at most once per [windowMillis] (a day) for
 * the same type, host and detail. Every other type is user-driven history and is always stored.
 *
 * The window lives in memory, so a process restart forgets it and the first such event after the
 * restart is stored again. It is anchored on the event that was stored, not on the repeats that
 * were dropped, and an event counts only once the caller reports it with [markStored]: a failed
 * insert must not suppress the repeat.
 */
class AuditDeduplicator(private val windowMillis: Long = WINDOW_MILLIS) {
    private data class Key(val type: AuditType, val host: String?, val detail: String?)

    private val lastStored = HashMap<Key, Long>()

    /** Number of events the window currently remembers. */
    val size: Int
        @Synchronized get() = lastStored.size

    /** True when [event] is not a repeat; the caller must then store it and call [markStored]. */
    @Synchronized
    fun shouldStore(event: AuditEvent): Boolean {
        if (event.type !in DEDUPLICATED_TYPES) return true
        val now = event.timestampMillis
        lastStored.values.removeAll { now - it >= windowMillis }
        val last = lastStored[key(event)]
        // A clock that went backwards (now < last) is not a repeat either.
        return last == null || now < last
    }

    /** Remembers that [event] was stored, so its repeats are dropped for a day. */
    @Synchronized
    fun markStored(event: AuditEvent) {
        if (event.type !in DEDUPLICATED_TYPES) return
        // A flood of different events must not grow the window without bound.
        if (lastStored.size >= MAX_KEYS) lastStored.clear()
        lastStored[key(event)] = event.timestampMillis
    }

    @Synchronized
    fun clear() {
        lastStored.clear()
    }

    private fun key(event: AuditEvent) = Key(event.type, event.host, event.detail)

    companion object {
        const val WINDOW_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX_KEYS = 1_024

        /** The types raised again on every poll while the condition lasts. */
        val DEDUPLICATED_TYPES = setOf(AuditType.CERTIFICATE_CHANGED, AuditType.TLS_FAILURE)
    }
}
