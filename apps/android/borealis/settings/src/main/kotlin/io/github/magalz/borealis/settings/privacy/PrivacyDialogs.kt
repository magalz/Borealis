package io.github.magalz.borealis.settings.privacy

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.settings.R

private val FIELD_GAP = 12.dp
private val LABEL_GAP = 2.dp

/** The dialog that [dialog] names, if any. Back and the Close or Cancel button dismiss it. */
@Composable
internal fun PrivacyDialogs(dialog: PrivacyDialog?, actions: PrivacyActions) {
    when (dialog) {
        null -> Unit
        PrivacyDialog.EraseConfirmation -> EraseDialog(actions)
        is PrivacyDialog.Saved -> SavedDialog(dialog, actions)
    }
}

/** Cancel has the focus, so a stray OK changes nothing. */
@Composable
private fun EraseDialog(actions: PrivacyActions) {
    BorealisDialog(
        title = stringResource(R.string.borealis_privacy_erase_dialog_title),
        onDismissRequest = actions.onEraseCancelled,
        confirm =
            DialogAction(
                stringResource(R.string.borealis_privacy_erase_confirm),
                BorealisButtonStyle.Danger,
                actions.onEraseConfirmed,
            ),
        width = DialogWidth.Compact,
        dismiss =
            DialogAction(
                stringResource(R.string.borealis_privacy_erase_cancel),
                onClick = actions.onEraseCancelled,
            ),
        focusDismiss = true,
    ) {
        Text(
            stringResource(R.string.borealis_privacy_erase_dialog_message),
            color = LocalBorealisColors.current.ink2,
        )
    }
}

/**
 * Where the bundle was saved. A TV also offers Send to phone, which leaves this dialog open under
 * the caller's own; a phone or handheld gets here only when no app can receive the share, so it has
 * nothing to offer but Close. Close has the focus.
 */
@Composable
private fun SavedDialog(dialog: PrivacyDialog.Saved, actions: PrivacyActions) {
    val size = Formatter.formatShortFileSize(LocalContext.current, dialog.sizeBytes)
    val sendToPhone =
        if (LocalFormFactor.current != FormFactor.Tv) {
            null
        } else {
            DialogAction(stringResource(R.string.borealis_privacy_saved_send_to_phone)) {
                actions.onSendToPhone(dialog.bundle, dialog.adbPullCommand)
            }
        }
    BorealisDialog(
        title = stringResource(R.string.borealis_privacy_saved_title),
        onDismissRequest = actions.onDialogDismissed,
        confirm =
            DialogAction(
                stringResource(R.string.borealis_privacy_saved_close),
                onClick = actions.onDialogDismissed,
            ),
        width = DialogWidth.Standard,
        dismiss = sendToPhone,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
            Field(
                stringResource(R.string.borealis_privacy_saved_file),
                "${dialog.fileName} ($size)",
            )
            Field(stringResource(R.string.borealis_privacy_saved_location), dialog.location)
            Field(
                stringResource(R.string.borealis_privacy_saved_adb),
                dialog.adbPullCommand,
                monospace = true,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Text(
            label,
            color = LocalBorealisColors.current.ink3,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            value,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
