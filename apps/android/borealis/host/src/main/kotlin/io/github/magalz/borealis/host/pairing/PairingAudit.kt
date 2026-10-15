package io.github.magalz.borealis.host.pairing

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.nativecore.PairDecision

/**
 * Audit events of the pairing flow (ADR 0050). Only categories and status codes are recorded: never
 * the PIN, never the host's message text.
 */
object PairingAudit {
    /** A pairing attempt begins; [restarted] is true for the automatic second attempt. */
    @JvmStatic
    fun started(host: String?, restarted: Boolean) {
        BorealisAudit.record(
            AuditType.PAIRING_START,
            AuditOutcome.SUCCESS,
            host,
            "attempt=${if (restarted) 2 else 1}",
        )
    }

    /** The upstream `PairState` name of a pairing that ended without an exception. */
    @JvmStatic
    fun finished(host: String?, pairState: String) {
        when (pairState) {
            "PAIRED" -> BorealisAudit.record(AuditType.PAIRING_SUCCESS, AuditOutcome.SUCCESS, host)
            "PIN_WRONG" -> failure(host, "reason=wrong_pin")
            "FAILED" -> failure(host, "reason=refused")
            "ALREADY_IN_PROGRESS" -> failure(host, "reason=already_in_progress")
        }
    }

    /** The PIN expired before the host accepted it. */
    @JvmStatic
    fun expired(host: String?) {}

    /**
     * A pairing that ended with an exception; [decision] is null when the rules were unavailable.
     */
    internal fun failed(host: String?, status: Int?, decision: PairDecision?, restarted: Boolean) {
        val parts =
            listOfNotNull(
                status?.let { "status=$it" },
                "category=${decision?.category ?: UNCLASSIFIED}",
                decision?.let { "action=${it.action}" },
                decision?.let { "restarted=$restarted" },
            )
        failure(host, parts.joinToString(" "))
    }

    /** Pairing starts again by itself after the failure that was just recorded. */
    internal fun restarting(host: String?, decision: PairDecision?) {
        BorealisAudit.record(
            AuditType.PAIRING_RESTART,
            AuditOutcome.SUCCESS,
            host,
            "category=${decision?.category ?: UNCLASSIFIED}",
        )
    }

    private fun failure(host: String?, detail: String) {
        BorealisAudit.record(AuditType.PAIRING_FAILURE, AuditOutcome.FAILURE, host, detail)
    }

    private const val UNCLASSIFIED = "unclassified"
}
