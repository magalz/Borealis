package io.github.magalz.borealis.stream.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.ui.StreamDialogAction

/**
 * The buttons of the error popup, in the order of the focus: Try again (with the initial focus, as
 * the first button of the popup), the state action of the failure kind if it has one, Diagnostics
 * and Close. The way forward comes first, and the two ways out are text buttons.
 */
@Composable
internal fun errorActions(
    stateAction: StateAction?,
    intents: StreamIntents,
): List<StreamDialogAction> = buildList {
    add(
        StreamDialogAction(
            stringResource(R.string.stream_error_try_again),
            BorealisButtonStyle.Primary,
            intents::retry,
        )
    )
    when (stateAction) {
        StateAction.WAKE_HOST ->
            add(
                StreamDialogAction(
                    stringResource(R.string.stream_error_wake_pc),
                    BorealisButtonStyle.Secondary,
                    intents::wakeHost,
                )
            )
        StateAction.PAIR_AGAIN ->
            add(
                StreamDialogAction(
                    stringResource(R.string.stream_error_pair_again),
                    BorealisButtonStyle.Secondary,
                    intents::pairAgain,
                )
            )
        null -> Unit
    }
    add(
        StreamDialogAction(
            stringResource(R.string.stream_error_diagnostics),
            BorealisButtonStyle.Text,
            intents::openDiagnostics,
        )
    )
    add(
        StreamDialogAction(
            stringResource(R.string.stream_error_close),
            BorealisButtonStyle.Text,
            intents::leave,
        )
    )
}
