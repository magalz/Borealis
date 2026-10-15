package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.BorealisAudit
import java.io.OutputStream
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The audit trail as `audit.jsonl` in the diagnostic bundle: one JSON object per line, oldest
 * first, UTC timestamps. Every entry is redacted again with the current "Mask addresses" setting
 * ([redact]), so an entry written while masking was off is masked in a later export.
 */
class AuditSection(
    private val store: AuditStore,
    private val redact: (AuditEvent) -> AuditEvent = BorealisAudit::redactForExport,
) : DiagnosticSection {
    override val fileName = "audit.jsonl"

    @Serializable
    private data class Line(
        val timestamp: String,
        val type: String,
        val outcome: String,
        val host: String?,
        val detail: String?,
        val appVersion: String,
    )

    override fun writeTo(out: OutputStream) {
        for (stored in store.entries()) {
            val event = redact(stored)
            val line =
                Line(
                    timestamp = Instant.ofEpochMilli(event.timestampMillis).toString(),
                    type = event.type.name,
                    outcome = event.outcome.name,
                    host = event.host,
                    detail = event.detail,
                    appVersion = event.appVersion,
                )
            // JSON escapes line breaks, so one entry is always exactly one line.
            out.write((Json.encodeToString(line) + "\n").toByteArray(Charsets.UTF_8))
        }
    }
}
