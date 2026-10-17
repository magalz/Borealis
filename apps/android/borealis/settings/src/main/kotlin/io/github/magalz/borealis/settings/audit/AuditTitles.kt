package io.github.magalz.borealis.settings.audit

import androidx.annotation.StringRes
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.R

/** The title of [this] event in plain words; the event code stays beside it for support. */
@StringRes
internal fun AuditType.titleRes(): Int =
    when (this) {
        AuditType.PAIRING_START -> R.string.audit_ui_title_pairing_start
        AuditType.PAIRING_SUCCESS -> R.string.audit_ui_title_pairing_success
        AuditType.PAIRING_FAILURE -> R.string.audit_ui_title_pairing_failure
        AuditType.PAIRING_RESTART -> R.string.audit_ui_title_pairing_restart
        AuditType.UNPAIR_CONFIRMED -> R.string.audit_ui_title_unpair_confirmed
        AuditType.UNPAIR_NOT_CONFIRMED -> R.string.audit_ui_title_unpair_not_confirmed
        AuditType.CERTIFICATE_CHANGED -> R.string.audit_ui_title_certificate_changed
        AuditType.TLS_FAILURE -> R.string.audit_ui_title_tls_failure
        AuditType.LAUNCH_ATTEMPT -> R.string.audit_ui_title_launch_attempt
        AuditType.LAUNCH_RETRY -> R.string.audit_ui_title_launch_retry
        AuditType.LAUNCH_CONFLICT -> R.string.audit_ui_title_launch_conflict
        AuditType.SESSION_START -> R.string.audit_ui_title_session_start
        AuditType.SESSION_END -> R.string.audit_ui_title_session_end
    }

/** The result of an event in plain words. */
@StringRes
internal fun AuditOutcome.labelRes(): Int =
    when (this) {
        AuditOutcome.SUCCESS -> R.string.audit_ui_outcome_success
        AuditOutcome.FAILURE -> R.string.audit_ui_outcome_failure
        AuditOutcome.CANCELLED -> R.string.audit_ui_outcome_cancelled
    }
