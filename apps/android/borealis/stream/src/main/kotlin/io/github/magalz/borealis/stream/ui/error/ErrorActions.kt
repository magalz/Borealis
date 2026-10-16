package io.github.magalz.borealis.stream.ui.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents

private val BUTTON_GAP = 8.dp

/**
 * The buttons of the error screen: Try again (with the initial focus), the state action of the
 * failure kind if it has one, Diagnostics and Close.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ErrorActions(stateAction: StateAction?, intents: StreamIntents) {
    val tryAgainFocus = remember { FocusRequester() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
        BorealisButton(
            stringResource(R.string.stream_error_try_again),
            intents::retry,
            Modifier.initialFocus(tryAgainFocus),
        )
        when (stateAction) {
            StateAction.WAKE_HOST ->
                BorealisButton(
                    stringResource(R.string.stream_error_wake_pc),
                    intents::wakeHost,
                    style = BorealisButtonStyle.Secondary,
                )
            StateAction.PAIR_AGAIN ->
                BorealisButton(
                    stringResource(R.string.stream_error_pair_again),
                    intents::pairAgain,
                    style = BorealisButtonStyle.Secondary,
                )
            null -> Unit
        }
        BorealisButton(
            stringResource(R.string.stream_error_diagnostics),
            intents::openDiagnostics,
            style = BorealisButtonStyle.Text,
        )
        BorealisButton(
            stringResource(R.string.stream_error_close),
            intents::leave,
            style = BorealisButtonStyle.Text,
        )
    }
}
