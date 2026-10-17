@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.settings.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/** A button of a [SettingsDialog]: its [label], what it does and its [style]. */
internal class DialogButton(
    val label: String,
    val onClick: () -> Unit,
    val style: BorealisButtonStyle = BorealisButtonStyle.Primary,
)

private fun DialogButton.toAction() = DialogAction(label, style, onClick)

/**
 * A [BorealisDialog] of the standard width with a [title], [content], a [confirm] button and
 * optionally a [dismiss] button. With [focusDismiss] the [dismiss] button has focus when the dialog
 * opens, so the safe choice is the one under OK; otherwise [confirm] has it. Back and a tap outside
 * call [onDismiss].
 */
@Composable
internal fun SettingsDialog(
    title: String,
    confirm: DialogButton,
    onDismiss: () -> Unit,
    dismiss: DialogButton? = null,
    focusDismiss: Boolean = dismiss != null,
    content: @Composable () -> Unit,
) {
    BorealisDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirm = confirm.toAction(),
        width = DialogWidth.Standard,
        dismiss = dismiss?.toAction(),
        focusDismiss = focusDismiss,
    ) {
        content()
    }
}

/** A [SettingsDialog] whose content is a [message]; the dialog scrolls it. */
@Composable
internal fun SettingsMessageDialog(
    title: String,
    message: String,
    confirm: DialogButton,
    onDismiss: () -> Unit,
    dismiss: DialogButton? = null,
    focusDismiss: Boolean = dismiss != null,
) {
    SettingsDialog(title, confirm, onDismiss, dismiss, focusDismiss) {
        Text(message, color = LocalBorealisColors.current.ink2)
    }
}
