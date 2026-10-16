package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.ui.error.StateAction
import io.github.magalz.borealis.stream.ui.error.errorCopy

private val CONTENT_MAX_WIDTH = 460.dp
private val CONTENT_GAP = 14.dp
private val SUPPORT_LINE_GAP = 2.dp
private val BUTTON_GAP = 8.dp

/**
 * What happened to the connection, what the host was doing, and the next step (ADR 0052, UC-002).
 * The raw code and the port hint stay visible for support. Gamepad B leaves; Back is handled by the
 * host.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionErrorScreen(
    appName: String,
    hostName: String,
    failure: StreamFailure,
    intents: StreamIntents,
) {
    val colors = LocalBorealisColors.current
    val copy = errorCopy(failure.kind)
    // The title of a generic failure names the app; every other kind names the host.
    val subject = if (failure.kind == FailureKind.GENERIC) appName else hostName
    val tryAgainFocus = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize()
            .background(colors.page)
            .onPreviewKeyEvent { event ->
                val isB = event.key == Key.ButtonB
                if (isB && event.type == KeyEventType.KeyUp) intents.leave()
                isB
            }
            .padding(LocalBorealisSpacing.current.gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            Modifier.widthIn(max = CONTENT_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            Text(stringResource(copy.title, subject), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(copy.body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            SupportLines(failure)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                BorealisButton(
                    stringResource(R.string.stream_error_try_again),
                    intents::retry,
                    Modifier.initialFocus(tryAgainFocus),
                )
                when (copy.action) {
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
    }
}

/** The raw code and, when the classifier has one, the port hint, in monospace for support. */
@Composable
private fun SupportLines(failure: StreamFailure) {
    val style =
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = BorealisFonts.Mono,
            color = LocalBorealisColors.current.ink3,
        )
    Column(verticalArrangement = Arrangement.spacedBy(SUPPORT_LINE_GAP)) {
        Text(failure.rawCode, style = style)
        failure.portHint?.let { Text(it, style = style) }
    }
}
