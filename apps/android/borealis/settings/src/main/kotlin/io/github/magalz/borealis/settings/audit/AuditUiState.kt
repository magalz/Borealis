package io.github.magalz.borealis.settings.audit

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType

/**
 * What the Audit trail screen shows. [loading] is true until the trail reported once, so that the
 * screen does not say "No events recorded yet." while it is still reading. [detail] is the entry
 * that the user opened, if any.
 */
data class AuditUiState(
    val loading: Boolean = true,
    val entries: List<AuditRow> = emptyList(),
    val detail: AuditDetail? = null,
)

/** One entry of the list. [time] is formatted and [summary] is redacted: host and detail. */
data class AuditRow(val key: String, val type: AuditType, val time: String, val summary: String?)

/** The opened entry in full. [time] has the date; [host] and [detail] are redacted. */
data class AuditDetail(
    val type: AuditType,
    val time: String,
    val outcome: AuditOutcome,
    val host: String?,
    val detail: String?,
)
