package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.ui.common.appLabel
import io.github.magalz.borealis.stream.ui.error.errorActions
import io.github.magalz.borealis.stream.ui.error.errorCopy

/** Marks the panel of the popup, so a test can measure it. */
internal const val ERROR_PANEL_TAG = "error_panel"

private val SUPPORT_LINE_GAP = 2.dp

/**
 * What happened to the connection, what the host was doing, and the next step (ADR 0052, UC-002),
 * as a popup: a [StreamDialog] over the background of the screens outside the game, or over the
 * last frame of the stream when the stream ran and there is one. The raw code and the port hint
 * stay visible for support, under the announced title and body.
 *
 * It has no Cancel: a tap outside it does nothing, and Back and the gamepad B button close it,
 * which is Close, back to the Library. Try again has the initial focus.
 */
@Composable
fun ConnectionErrorScreen(
    appName: String,
    hostName: String,
    failure: StreamFailure,
    intents: StreamIntents,
) {
    val copy = errorCopy(failure.kind)
    // The title of a generic failure and of a stopped stream names the app; every other kind names
    // the host.
    val subject =
        when (failure.kind) {
            FailureKind.GENERIC,
            FailureKind.STOPPED -> appLabel(appName)
            else -> hostName
        }
    StreamDialog(
        title = stringResource(copy.title, subject),
        onDismiss = intents::leave,
        actions = errorActions(copy.action, intents),
        modifier = Modifier.testTag(ERROR_PANEL_TAG),
        width = DialogWidth.Standard,
        dismissOnScrimTap = false,
        details = { SupportLines(failure) },
    ) {
        Text(
            stringResource(copy.body),
            color = LocalBorealisColors.current.ink2,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The raw code and, when the classifier has one, the port hint, in monospace for support. */
@Composable
private fun SupportLines(failure: StreamFailure) {
    val style = LocalBorealisType.current.codes(MaterialTheme.typography.bodySmall)
    val ink = LocalBorealisColors.current.ink3
    Column(verticalArrangement = Arrangement.spacedBy(SUPPORT_LINE_GAP)) {
        Text(failure.rawCode, color = ink, style = style)
        failure.portHint?.let { Text(it, color = ink, style = style) }
    }
}
