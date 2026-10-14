package io.github.magalz.borealis.logging

/**
 * Destination of the audit trail. [write] is called on the thread that records the event, so an
 * implementation must return quickly and never throw: hand the event to another thread.
 */
fun interface AuditSink {
    fun write(event: AuditEvent)
}
