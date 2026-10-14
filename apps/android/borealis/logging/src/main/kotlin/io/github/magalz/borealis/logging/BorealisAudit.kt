package io.github.magalz.borealis.logging

/** Process-wide audit facade. Until [install] is called, events are dropped. */
object BorealisAudit {
    fun install(
        sink: AuditSink,
        redactor: Redactor,
        appVersion: String,
        clock: () -> Long = System::currentTimeMillis,
    ) {}

    fun uninstall() {}

    fun record(
        type: AuditType,
        outcome: AuditOutcome,
        host: String? = null,
        detail: String? = null,
    ) {}

    fun redactForExport(event: AuditEvent): AuditEvent = event

    @JvmStatic fun hostLabel(name: String?, address: String?): String? = null
}
