package io.github.magalz.borealis.diagnostics.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType

/** One row of the audit trail. Enum values are stored by name. */
@Entity(tableName = "audit_events", indices = [Index("timestamp")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val outcome: String,
    val host: String?,
    val detail: String?,
    val appVersion: String,
) {
    /** Null for a row whose type or outcome this version does not know. */
    fun toEvent(): AuditEvent? {
        val type = runCatching { AuditType.valueOf(type) }.getOrNull() ?: return null
        val outcome = runCatching { AuditOutcome.valueOf(outcome) }.getOrNull() ?: return null
        return AuditEvent(timestamp, type, outcome, host, detail, appVersion)
    }

    companion object {
        fun of(event: AuditEvent) =
            AuditEntity(
                timestamp = event.timestampMillis,
                type = event.type.name,
                outcome = event.outcome.name,
                host = event.host,
                detail = event.detail,
                appVersion = event.appVersion,
            )
    }
}
