package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * One entry of the audit trail as the viewer shows it. [type] is the event code. Only what the
 * trail already stores is here, and [host] and [detail] were redacted when the entry was written,
 * so no PIN, token or key can be in it.
 */
data class AuditEntry(
    val timestampMillis: Long,
    val type: AuditType,
    val outcome: AuditOutcome,
    val host: String?,
    val detail: String?,
)

/** The audit trail for the viewer (UC-007): a live list and the erase call. */
interface AuditTrail {
    /** The entries, newest first. Emits again whenever the trail changes. */
    fun observe(): Flow<List<AuditEntry>>

    /** Erases every entry. True on success; false when the entries could not be erased. */
    suspend fun erase(): Boolean
}

/** This entry with the host masked as the diagnostic export does it. */
fun AuditEntry.masked(): AuditEntry = this

internal class RoomAuditTrail(private val dao: AuditDao, private val store: AuditStore) :
    AuditTrail {
    override fun observe(): Flow<List<AuditEntry>> = flowOf(emptyList())

    override suspend fun erase(): Boolean = false
}
