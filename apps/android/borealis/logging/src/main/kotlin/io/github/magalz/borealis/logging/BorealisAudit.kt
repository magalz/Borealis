package io.github.magalz.borealis.logging

/**
 * Process-wide audit facade (ADR 0021, 0050). Events are redacted with the shared [Redactor] and
 * then handed to the installed [AuditSink]. Until [install] is called, events are dropped.
 * Recording never throws and does no I/O itself.
 *
 * What goes in: event types and outcomes, the host label, and a short detail such as a status code
 * or a category. What never goes in: PINs, keys, certificates, tokens. The [Redactor] is a second
 * line of defence for that, not a licence to pass them.
 */
object BorealisAudit {
    private const val MAX_DETAIL_LENGTH = 512
    private const val HOST_FIELD = "host"

    private class State(
        val sink: AuditSink,
        val redactor: Redactor,
        val appVersion: String,
        val clock: () -> Long,
    )

    @Volatile private var state: State? = null

    /** Replaces any earlier installation. [redactor] is the one that [BorealisLog] uses. */
    fun install(
        sink: AuditSink,
        redactor: Redactor,
        appVersion: String,
        clock: () -> Long = System::currentTimeMillis,
    ) {
        state = State(sink, redactor, appVersion, clock)
    }

    /** Goes back to dropping events. */
    fun uninstall() {
        state = null
    }

    /**
     * Records one event. [host] is redacted as a host field, so it follows the "Mask addresses"
     * switch as it is at this moment; [detail] is redacted as free text and cut to a bounded
     * length.
     */
    fun record(
        type: AuditType,
        outcome: AuditOutcome,
        host: String? = null,
        detail: String? = null,
    ) {
        val current = state ?: return
        runCatching {
            val redactor = current.redactor
            current.sink.write(
                AuditEvent(
                    timestampMillis = current.clock(),
                    type = type,
                    outcome = outcome,
                    host = host?.let { redactor.redactField(HOST_FIELD, it) },
                    detail = detail?.let { redactor.redact(it, MAX_DETAIL_LENGTH) },
                    appVersion = current.appVersion,
                )
            )
        }
    }

    /**
     * Applies the current redaction again to a stored event, so an entry written while "Mask
     * addresses" was off is masked in an export made after it was switched on.
     */
    fun redactForExport(event: AuditEvent): AuditEvent {
        val redactor = state?.redactor ?: Redactor()
        return event.copy(
            host = event.host?.let { redactor.redactField(HOST_FIELD, it) },
            detail = event.detail?.let { redactor.redact(it, MAX_DETAIL_LENGTH) },
        )
    }

    /** The host as the audit trail names it: `name (address)`, or whichever of the two is known. */
    @JvmStatic
    fun hostLabel(name: String?, address: String?): String? {
        val n = name?.trim().orEmpty()
        val a = address?.trim().orEmpty()
        return when {
            n.isEmpty() && a.isEmpty() -> null
            n.isEmpty() -> a
            a.isEmpty() || a == n -> n
            else -> "$n ($a)"
        }
    }
}
