package io.github.magalz.borealis.host.pairing

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import java.security.MessageDigest
import java.security.cert.Certificate

/**
 * Audit events of TLS and pinning failures (ADR 0050). A certificate is recorded only as a short
 * SHA-256 fingerprint prefix, which the privacy policy allows; an exception only by its class,
 * because its message can quote certificate data.
 */
object TlsAudit {
    private const val FINGERPRINT_BYTES = 8
    private const val UNAVAILABLE = "unavailable"

    /** The host presented [presented] although [pinned] was pinned when the PC was paired. */
    @JvmStatic
    fun certificateChanged(host: String?, presented: Certificate, pinned: Certificate) {
        BorealisAudit.record(
            AuditType.CERTIFICATE_CHANGED,
            AuditOutcome.FAILURE,
            host,
            "presented=${fingerprint(presented)} pinned=${fingerprint(pinned)}",
        )
    }

    /** Any other TLS failure while talking to [host]. */
    @JvmStatic
    fun failure(host: String?, error: Throwable) {
        BorealisAudit.record(
            AuditType.TLS_FAILURE,
            AuditOutcome.FAILURE,
            host,
            "error=${error.javaClass.simpleName}",
        )
    }

    private fun fingerprint(certificate: Certificate): String = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .take(FINGERPRINT_BYTES)
            .joinToString("") { "%02x".format(it) }
    }
        .getOrDefault(UNAVAILABLE)
}
