package io.github.magalz.borealis.diagnostics.audit

import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.BorealisAudit
import java.io.OutputStream

class AuditSection(
    private val store: AuditStore,
    private val redact: (AuditEvent) -> AuditEvent = BorealisAudit::redactForExport,
) : DiagnosticSection {
    override val fileName = "audit.jsonl"

    override fun writeTo(out: OutputStream) {}
}
