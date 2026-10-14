package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.nativecore.LaunchCategory

/**
 * Audit events of `/launch` and `/resume` (ADR 0050). Status codes, categories and ids only: the
 * host's message text and app names are not recorded.
 */
internal object LaunchAudit {
    /** The final result of one launch or resume request. */
    fun attempt(host: String?, verb: String, outcome: LaunchOutcome) {
        val (result, audited) =
            when (outcome) {
                LaunchOutcome.SUCCEEDED -> "succeeded" to AuditOutcome.SUCCESS
                LaunchOutcome.FAILED -> "refused" to AuditOutcome.FAILURE
                LaunchOutcome.CANCELLED -> "cancelled" to AuditOutcome.CANCELLED
                LaunchOutcome.FALLBACK_TO_LAUNCH -> "no_app_to_resume" to AuditOutcome.FAILURE
                LaunchOutcome.CONFLICT -> "app_running" to AuditOutcome.FAILURE
            }
        BorealisAudit.record(AuditType.LAUNCH_ATTEMPT, audited, host, "verb=$verb result=$result")
    }

    /** A request that failed for good; [error] is the host's answer when there was one. */
    fun failed(host: String?, verb: String, error: HostError?, failure: Throwable) {
        val reason = error?.let { "status=${it.code}" } ?: "error=${failure.javaClass.simpleName}"
        BorealisAudit.record(
            AuditType.LAUNCH_ATTEMPT,
            AuditOutcome.FAILURE,
            host,
            "verb=$verb result=failed $reason",
        )
    }

    /** The host asked the client to try again. */
    @Suppress("LongParameterList")
    fun retry(
        host: String?,
        verb: String,
        category: LaunchCategory,
        attempt: Int,
        max: Int,
        delayMs: Int,
    ) {
        BorealisAudit.record(
            AuditType.LAUNCH_RETRY,
            AuditOutcome.FAILURE,
            host,
            "verb=$verb category=$category attempt=$attempt/$max delay_ms=$delayMs",
        )
    }

    /** What the user decided about the app that is already running. */
    fun conflict(host: String?, choice: ConflictChoice, runningAppId: Int) {
        val (name, outcome) =
            when (choice) {
                ConflictChoice.JOIN -> "join" to AuditOutcome.SUCCESS
                ConflictChoice.QUIT_AND_RELAUNCH -> "quit" to AuditOutcome.SUCCESS
                ConflictChoice.CANCEL -> "cancel" to AuditOutcome.CANCELLED
            }
        BorealisAudit.record(
            AuditType.LAUNCH_CONFLICT,
            outcome,
            host,
            "choice=$name running_app=$runningAppId",
        )
    }
}
