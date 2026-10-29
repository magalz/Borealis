package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.copy.QuitCopy
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.host.apps.isDesktop
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase

/** Marks the panel of the dialog, so a test can measure it. */
internal const val CONFLICT_PANEL_TAG = "conflict_panel"

private val WARNING_PADDING_HORIZONTAL = 12.dp
private val WARNING_PADDING_VERTICAL = 10.dp
private const val WARNING_TINT = 0.1f

/**
 * The choice for an app that is already running on the host (UC-003): Cancel, Join, or quit it and
 * start again. It is a [StreamDialog], inside the chrome and not a window of its own: see there for
 * why, and for the focus on Cancel, which a stray OK cannot make costly, and for the gamepad B
 * button and Back, which cancel. It stands over the background of the screens outside the game, and
 * its panel is the solid surface of the dialog (the glass of a dialog is the stream's business,
 * with the dialogs of the stream layer).
 */
@Composable
fun ConflictDialog(phase: StreamPhase.Conflict, intents: StreamIntents) {
    val answer = { choice: ConflictChoice -> intents.answerConflict(choice) }
    StreamDialog(
        title = stringResource(R.string.stream_conflict_title),
        onDismiss = { answer(ConflictChoice.CANCEL) },
        actions =
            listOf(
                StreamDialogAction(
                    stringResource(R.string.stream_conflict_cancel),
                    BorealisButtonStyle.Secondary,
                ) {
                    answer(ConflictChoice.CANCEL)
                },
                StreamDialogAction(
                    stringResource(R.string.stream_conflict_join),
                    BorealisButtonStyle.Primary,
                ) {
                    answer(ConflictChoice.JOIN)
                },
                StreamDialogAction(
                    // The answer is the same; the label says what happens to the app the user
                    // chose.
                    stringResource(
                        if (phase.sameApp && phase.runningAppName != null) {
                            R.string.stream_conflict_quit_and_relaunch
                        } else {
                            R.string.stream_conflict_quit_and_start
                        }
                    ),
                    BorealisButtonStyle.Danger,
                ) {
                    answer(ConflictChoice.QUIT_AND_RELAUNCH)
                },
            ),
        modifier = Modifier.testTag(CONFLICT_PANEL_TAG),
    ) {
        val colors = LocalBorealisColors.current
        Text(body(phase), color = colors.ink2, style = MaterialTheme.typography.bodyMedium)
        Warning(phase.runningAppName)
    }
}

@Composable
private fun body(phase: StreamPhase.Conflict): String {
    val running = phase.runningAppName ?: stringResource(R.string.stream_conflict_another_app)
    return if (phase.sameApp) {
        stringResource(R.string.stream_conflict_body_same, running)
    } else {
        stringResource(R.string.stream_conflict_body_other, running, phase.requestedAppName)
    }
}

/**
 * What quitting costs (UC-003-T6). Quitting Desktop only ends the stream on the other device; any
 * other app, also one whose name is not known, closes and loses its unsaved progress.
 */
@Composable
private fun Warning(runningAppName: String?) {
    val colors = LocalBorealisColors.current
    Text(
        QuitCopy.conflictWarning(isDesktop(runningAppName)),
        Modifier.fillMaxWidth()
            .background(colors.warning.copy(alpha = WARNING_TINT), MaterialTheme.shapes.medium)
            .padding(WARNING_PADDING_HORIZONTAL, WARNING_PADDING_VERTICAL),
        color = colors.warningInk,
        style = MaterialTheme.typography.bodySmall,
    )
}
