@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.settings.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors

/** A button of a [SettingsDialog]: its [label], what it does and its [style]. */
internal class DialogButton(
    val label: String,
    val onClick: () -> Unit,
    val style: BorealisButtonStyle = BorealisButtonStyle.Primary,
)

/**
 * A dialog in the Borealis look with a [title], [content], a [confirm] button and optionally a
 * [dismiss] button. With [focusDismiss] the [dismiss] button has focus when the dialog opens, so
 * the safe choice is the one under OK; otherwise [confirm] has it. Back and a tap outside call
 * [onDismiss].
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
    val colors = LocalBorealisColors.current
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = content,
        confirmButton = {
            BorealisButton(
                confirm.label,
                confirm.onClick,
                if (focusDismiss) Modifier else Modifier.initialFocus(focus),
                style = confirm.style,
            )
        },
        dismissButton =
            dismiss?.let { button ->
                {
                    BorealisButton(
                        button.label,
                        button.onClick,
                        if (focusDismiss) Modifier.initialFocus(focus) else Modifier,
                        style = button.style,
                    )
                }
            },
    )
}

/** A [SettingsDialog] whose content is a scrollable [message]. */
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
        Text(message, Modifier.verticalScroll(rememberScrollState()))
    }
}
