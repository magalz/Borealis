package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Room-backed audit trail. [write] only queues the event: one writer coroutine on [io] stores
 * the events in order, applies the deduplication window and the retention (the newest [maxEntries],
 * nothing older than [maxAgeMillis]). The queue is bounded and drops its oldest event when it
 * overflows, so a burst can never block or grow the caller.
 */
class AuditStore(
    private val dao: AuditDao,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxAgeMillis: Long = MAX_AGE_MILLIS,
    private val dedup: AuditDeduplicator = AuditDeduplicator(),
    private val clock: () -> Long = System::currentTimeMillis,
) : AuditSink {
    private val queue = Channel<AuditEvent>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)
    private val lock = Any()

    init {
        scope.launch(io) {
            for (event in queue) {
                store(event)
            }
        }
    }

    override fun write(event: AuditEvent) {
        queue.trySend(event)
    }

    /** Deletes the whole trail and forgets the deduplication window. */
    suspend fun erase() {
        withContext(io) {
            synchronized(lock) {
                dao.deleteAll()
                dedup.clear()
            }
        }
    }

    /** The stored events, oldest first. Blocks on the database: call it off the main thread. */
    fun entries(): List<AuditEvent> = synchronized(lock) { dao.all() }.mapNotNull { it.toEvent() }

    @Suppress("TooGenericExceptionCaught")
    private fun store(event: AuditEvent) {
        try {
            synchronized(lock) {
                if (dedup.shouldStore(event)) {
                    // The cutoff is the event's own time, but never later than the wall clock: an
                    // event from a clock that jumped far ahead must not drop the older entries.
                    val now = minOf(event.timestampMillis, clock())
                    dao.insertAndPrune(
                        AuditEntity.of(event),
                        cutoff = now - maxAgeMillis,
                        keep = maxEntries,
                    )
                    // Only now: a failed insert above must not suppress the repeat.
                    dedup.markStored(event)
                }
            }
        } catch (e: Exception) {
            // The audit trail must never take the app down; say what failed, not what was audited.
            BorealisLog.w(LogTag.APP, "audit write failed", "error" to e.javaClass.simpleName)
        }
    }

    companion object {
        const val MAX_ENTRIES = 2_000
        const val MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000
        private const val QUEUE_CAPACITY = 256
    }
}
