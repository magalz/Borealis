package io.github.magalz.borealis.settings.privacy

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
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
    DialogFrame(
        title = stringResource(R.string.borealis_privacy_erase_dialog_title),
        onDismiss = actions.onEraseCancelled,
        confirm =
            DialogButton(
                stringResource(R.string.borealis_privacy_erase_confirm),
                actions.onEraseConfirmed,
                BorealisButtonStyle.Danger,
            ),
        dismiss =
            DialogButton(
                stringResource(R.string.borealis_privacy_erase_cancel),
                actions.onEraseCancelled,
                BorealisButtonStyle.Text,
            ),
        focusOnDismiss = true,
    ) {
        Text(stringResource(R.string.borealis_privacy_erase_dialog_message))
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
            DialogButton(
                stringResource(R.string.borealis_privacy_saved_send_to_phone),
                { actions.onSendToPhone(dialog.bundle, dialog.adbPullCommand) },
                BorealisButtonStyle.Secondary,
            )
        }
    DialogFrame(
        title = stringResource(R.string.borealis_privacy_saved_title),
        onDismiss = actions.onDialogDismissed,
        confirm =
            DialogButton(
                stringResource(R.string.borealis_privacy_saved_close),
                actions.onDialogDismissed,
            ),
        dismiss = sendToPhone,
        focusOnDismiss = false,
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

/** A button of a [DialogFrame]. */
private class DialogButton(
    val label: String,
    val onClick: () -> Unit,
    val style: BorealisButtonStyle = BorealisButtonStyle.Primary,
)

/**
 * A dialog in the Borealis look with a [title], a scrollable body, a [confirm] button and
 * optionally a [dismiss] button. [focusOnDismiss] says which of the two has the focus when the
 * dialog opens, so a D-pad or a gamepad works at once; with no [dismiss] button, [confirm] has it.
 * Back calls [onDismiss].
 */
@Composable
private fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    confirm: DialogButton,
    dismiss: DialogButton?,
    focusOnDismiss: Boolean,
    body: @Composable () -> Unit,
) {
    val colors = LocalBorealisColors.current
    val focus = remember { FocusRequester() }
    val confirmFocus = dismiss == null || !focusOnDismiss
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, Modifier.semantics { heading() }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { body() } },
        confirmButton = {
            BorealisButton(
                confirm.label,
                confirm.onClick,
                if (confirmFocus) Modifier.initialFocus(focus) else Modifier,
                style = confirm.style,
            )
        },
        dismissButton =
            dismiss?.let { button ->
                {
                    BorealisButton(
                        button.label,
                        button.onClick,
                        if (confirmFocus) Modifier else Modifier.initialFocus(focus),
                        style = button.style,
                    )
                }
            },
    )
}
