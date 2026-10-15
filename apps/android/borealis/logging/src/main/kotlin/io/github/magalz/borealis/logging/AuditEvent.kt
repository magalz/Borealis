package io.github.magalz.borealis.logging

/** What happened. REST and token events arrive with Phase 3. */
enum class AuditType {
    PAIRING_START,
    PAIRING_SUCCESS,
    PAIRING_FAILURE,
    PAIRING_RESTART,
    UNPAIR_CONFIRMED,
    UNPAIR_NOT_CONFIRMED,
    CERTIFICATE_CHANGED,
    TLS_FAILURE,
    LAUNCH_ATTEMPT,
    LAUNCH_RETRY,
    LAUNCH_CONFLICT,
    SESSION_START,
    SESSION_END,
}

enum class AuditOutcome {
    SUCCESS,
    FAILURE,
    CANCELLED,
}

/**
 * One entry of the audit trail. [host] and [detail] are already redacted when a sink receives the
 * event; PINs, keys, certificates and tokens never belong in either.
 */
data class AuditEvent(
    val timestampMillis: Long,
    val type: AuditType,
    val outcome: AuditOutcome,
    val host: String?,
    val detail: String?,
    val appVersion: String,
)
