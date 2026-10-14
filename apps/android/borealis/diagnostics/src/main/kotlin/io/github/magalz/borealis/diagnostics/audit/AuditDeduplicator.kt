package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent

class AuditDeduplicator(private val windowMillis: Long = WINDOW_MILLIS) {
    val size: Int
        get() = 0

    fun shouldStore(event: AuditEvent): Boolean = true

    fun clear() {}

    companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val MAX_KEYS = 1_024
    }
}
