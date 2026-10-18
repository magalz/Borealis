package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

/**
 * This entry with the host and the detail redacted as the diagnostic export does it
 * ([BorealisAudit.redactForExport]). Call it for every entry the viewer shows: it follows the
 * redactor installed in [BorealisAudit], so it masks addresses only while "Mask addresses in logs
 * and diagnostics" is on, and it always redacts secrets in the detail. [AuditTrail.observe] does
 * not emit again when that setting changes, so a viewer that shows masked entries maps them again
 * when the setting changes.
 */
fun AuditEntry.masked(): AuditEntry {
    val redacted =
        BorealisAudit.redactForExport(
            AuditEvent(timestampMillis, type, outcome, host, detail, appVersion = "")
        )
    return copy(host = redacted.host, detail = redacted.detail)
}

internal class RoomAuditTrail(private val dao: AuditDao, private val store: AuditStore) :
    AuditTrail {
    override fun observe(): Flow<List<AuditEntry>> =
        dao.observeNewestFirst().map { rows -> rows.mapNotNull { it.toEvent()?.toEntry() } }

    // Room can throw several unrelated types, and the audit trail must never crash the app.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun erase(): Boolean =
        try {
            store.erase()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BorealisLog.w(LogTag.APP, "audit erase failed", "error" to e.javaClass.simpleName)
            false
        }

    private fun AuditEvent.toEntry() = AuditEntry(timestampMillis, type, outcome, host, detail)
}
