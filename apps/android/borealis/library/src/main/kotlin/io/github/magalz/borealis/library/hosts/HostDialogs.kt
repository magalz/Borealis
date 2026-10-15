@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.library.hosts

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.ActionSheet
import io.github.magalz.borealis.designsystem.components.ActionSheetAction
import io.github.magalz.borealis.designsystem.components.ActionStyle
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.library.R

internal enum class HostOption {
    Details,
    Wake,
    Forget,
}

/** The options of a host, the Forget confirmation and the details: what opens over the screen. */
@Composable
internal fun HostsDialogs(
    state: HostsUiState,
    optionsHostId: String?,
    forgetHostId: String?,
    onOptionsHostChange: (String?) -> Unit,
    onForgetHostChange: (String?) -> Unit,
    onDetails: (String) -> Unit,
    onWake: (String) -> Unit,
    onForget: (String) -> Unit,
    onDetailsDismissed: () -> Unit,
) {
    state.items
        .firstOrNull { it.id == optionsHostId }
        ?.let { host ->
            HostOptions(
                host = host,
                onChosen = { option ->
                    onOptionsHostChange(null)
                    when (option) {
                        HostOption.Details -> onDetails(host.id)
                        HostOption.Wake -> onWake(host.id)
                        HostOption.Forget -> onForgetHostChange(host.id)
                    }
                },
                onDismiss = { onOptionsHostChange(null) },
            )
        }
    state.items
        .firstOrNull { it.id == forgetHostId }
        ?.let { host ->
            ForgetDialog(
                hostName = host.name,
                onConfirm = {
                    onForgetHostChange(null)
                    onForget(host.id)
                },
                onDismiss = { onForgetHostChange(null) },
            )
        }
    state.details?.let { details ->
        HostDialog(
            title = stringResource(R.string.hosts_details_title),
            message = details,
            confirm =
                DialogButton(stringResource(R.string.hosts_details_close), onDetailsDismissed),
            onDismiss = onDetailsDismissed,
        )
    }
}

@Composable
private fun HostOptions(
    host: HostItemUiState,
    onChosen: (HostOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val details =
        ActionSheetAction(
            HostOption.Details,
            stringResource(R.string.hosts_option_details),
            stringResource(R.string.hosts_option_details_consequence),
        )
    // Wake is for a host that is offline and has an address to wake.
    val wake =
        ActionSheetAction(
                HostOption.Wake,
                stringResource(R.string.hosts_option_wake),
                stringResource(R.string.hosts_option_wake_consequence),
            )
            .takeIf { host.action == HostAction.WAKE }
    val forget =
        ActionSheetAction(
            HostOption.Forget,
            stringResource(R.string.hosts_option_forget),
            stringResource(R.string.hosts_option_forget_consequence),
            ActionStyle.Danger,
        )
    ActionSheet(
        title = host.name,
        actions = listOfNotNull(details, wake, forget),
        onAction = { onChosen(it.key) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ForgetDialog(hostName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    HostDialog(
        title = stringResource(R.string.hosts_forget_title),
        message = stringResource(R.string.hosts_forget_message, hostName),
        confirm =
            DialogButton(
                stringResource(R.string.hosts_forget_confirm),
                onConfirm,
                BorealisButtonStyle.Danger,
            ),
        onDismiss = onDismiss,
        dismiss =
            DialogButton(
                stringResource(R.string.hosts_forget_cancel),
                onDismiss,
                BorealisButtonStyle.Text,
            ),
    )
}

/** A button of a [HostDialog]: its [label], [style] and what it does. */
internal class DialogButton(
    val label: String,
    val onClick: () -> Unit,
    val style: BorealisButtonStyle = BorealisButtonStyle.Primary,
)

/**
 * A dialog in the Borealis look with a [title], a scrollable [message], a [confirm] button and
 * optionally a [dismiss] button. The [dismiss] button, when there is one, has focus when the dialog
 * opens, so a D-pad or a gamepad works at once and the safe choice is the one under OK; with only
 * [confirm], that one has focus. Back dismisses.
 */
@Composable
internal fun HostDialog(
    title: String,
    message: String,
    confirm: DialogButton,
    onDismiss: () -> Unit,
    dismiss: DialogButton? = null,
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
        text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            BorealisButton(
                confirm.label,
                confirm.onClick,
                if (dismiss == null) Modifier.initialFocus(focus) else Modifier,
                style = confirm.style,
            )
        },
        dismissButton =
            dismiss?.let { button ->
                {
                    BorealisButton(
                        button.label,
                        button.onClick,
                        Modifier.initialFocus(focus),
                        style = button.style,
                    )
                }
            },
    )
}
