package io.github.magalz.borealis.settings.privacy

import java.io.File

/** What the pane asks of its caller; the pane itself keeps no state. */
internal class PrivacyActions(
    val onVerboseLoggingChange: (Boolean) -> Unit,
    val onMaskAddressesChange: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onOpenAudit: () -> Unit,
    val onEraseRequested: () -> Unit,
    val onEraseConfirmed: () -> Unit,
    val onEraseCancelled: () -> Unit,
    val onDialogDismissed: () -> Unit,
    val onSendToPhone: (File, String) -> Unit,
)
