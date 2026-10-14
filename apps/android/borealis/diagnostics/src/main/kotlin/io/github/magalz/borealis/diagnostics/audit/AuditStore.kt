package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class AuditStore(
    private val dao: AuditDao,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxAgeMillis: Long = MAX_AGE_MILLIS,
    private val dedup: AuditDeduplicator = AuditDeduplicator(),
) : AuditSink {
    override fun write(event: AuditEvent) {}

    suspend fun erase() {}

    fun entries(): List<AuditEvent> = emptyList()

    companion object {
        const val MAX_ENTRIES = 2_000
        const val MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
