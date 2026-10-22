package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.ActionSheet
import io.github.magalz.borealis.designsystem.components.ActionSheetAction
import io.github.magalz.borealis.designsystem.components.ActionStyle
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.DialogWidth
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
        DetailsDialog(details, onDetailsDismissed)
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

/** Asks before a host is forgotten. Focus starts on Cancel, so a stray OK does not forget it. */
@Composable
private fun ForgetDialog(hostName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BorealisDialog(
        title = stringResource(R.string.hosts_forget_title),
        onDismissRequest = onDismiss,
        confirm =
            DialogAction(
                stringResource(R.string.hosts_forget_confirm),
                BorealisButtonStyle.Danger,
                onConfirm,
            ),
        dismiss = DialogAction(stringResource(R.string.hosts_forget_cancel), onClick = onDismiss),
        focusDismiss = true,
    ) {
        DialogMessage(stringResource(R.string.hosts_forget_message, hostName))
    }
}

/** What the host reports about itself, in a dialog that Close dismisses. */
@Composable
private fun DetailsDialog(details: String, onDismiss: () -> Unit) {
    BorealisDialog(
        title = stringResource(R.string.hosts_details_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.hosts_details_close), onClick = onDismiss),
        width = DialogWidth.Standard,
    ) {
        DialogMessage(details)
    }
}

@Composable
internal fun DialogMessage(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalBorealisColors.current.ink2,
    )
}
