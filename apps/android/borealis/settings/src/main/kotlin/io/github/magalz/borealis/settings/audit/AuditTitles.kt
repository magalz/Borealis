package io.github.magalz.borealis.settings.audit

import androidx.annotation.StringRes
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.R

/** The title of [this] event in plain words; the event code stays beside it for support. */
@StringRes internal fun AuditType.titleRes(): Int = R.string.audit_ui_screen_title
