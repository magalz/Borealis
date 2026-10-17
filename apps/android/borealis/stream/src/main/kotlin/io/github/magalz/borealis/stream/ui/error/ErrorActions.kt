package io.github.magalz.borealis.stream.ui.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents

private val BUTTON_GAP = 10.dp

/**
 * The buttons of the error screen: Try again (with the initial focus), the state action of the
 * failure kind if it has one, Diagnostics and Close. A TV or a handheld has them in one row. A
 * phone has Try again and the state action in one row and Diagnostics and Close in a row below, so
 * the way forward stands apart from the ways out; the focus order is the visual order either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ErrorActions(stateAction: StateAction?, intents: StreamIntents) {
    val tryAgainFocus = remember { FocusRequester() }
    if (LocalFormFactor.current == FormFactor.Phone) {
        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            ButtonRow { PrimaryActions(stateAction, intents, tryAgainFocus) }
            ButtonRow { SecondaryActions(intents) }
        }
    } else {
        ButtonRow {
            PrimaryActions(stateAction, intents, tryAgainFocus)
            SecondaryActions(intents)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        content = content,
    )
}

/** Try again, which has the initial focus, and the state action of the failure kind. */
@Composable
private fun PrimaryActions(
    stateAction: StateAction?,
    intents: StreamIntents,
    tryAgainFocus: FocusRequester,
) {
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
}

/** Diagnostics and Close. */
@Composable
private fun SecondaryActions(intents: StreamIntents) {
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
