package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.ui.starting.onGamepadB

private const val DIALOG_WIDTH_FRACTION = 0.92f
private val DIALOG_MAX_WIDTH = 420.dp
private val DIALOG_PADDING = 20.dp
private val DIALOG_GAP = 12.dp
private val BUTTON_GAP = 12.dp
private val WARNING_PADDING_HORIZONTAL = 12.dp
private val WARNING_PADDING_VERTICAL = 10.dp
private const val WARNING_TINT = 0.1f

/**
 * The choice for an app that is already running on the host (UC-003): Cancel, Join, or quit it and
 * start again. It is drawn inside the chrome, not in a platform dialog window, because the stream
 * host owns the input. Cancel has the initial focus so a stray OK changes nothing, and the gamepad
 * B button cancels.
 */
@Composable
fun ConflictDialog(phase: StreamPhase.Conflict, intents: StreamIntents) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.extraLarge
    val answer = { choice: ConflictChoice -> intents.answerConflict(choice) }
    Box(
        Modifier.fillMaxSize()
            .background(colors.scrim)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGamepadB { answer(ConflictChoice.CANCEL) },
        contentAlignment = Alignment.Center,
    ) {
        // The padding is inside the scroll area: the area clips, and a focus outline reaches
        // outside its button.
        Column(
            Modifier.widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .clip(shape)
                .verticalScroll(rememberScrollState())
                .padding(DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
        ) {
            Text(
                stringResource(R.string.stream_conflict_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(body(phase), color = colors.ink2, style = MaterialTheme.typography.bodyMedium)
            Warning()
            Buttons(answer)
        }
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

/** What quitting costs (UC-003-T6). */
@Composable
private fun Warning() {
    val colors = LocalBorealisColors.current
    Text(
        stringResource(R.string.stream_conflict_warning),
        Modifier.fillMaxWidth()
            .background(colors.warning.copy(alpha = WARNING_TINT), MaterialTheme.shapes.medium)
            .padding(WARNING_PADDING_HORIZONTAL, WARNING_PADDING_VERTICAL),
        color = colors.warningInk,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Three buttons do not fit side by side on a phone, so they stack there. */
@Composable
private fun Buttons(answer: (ConflictChoice) -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val stacked = LocalFormFactor.current == FormFactor.Phone
    val width = if (stacked) Modifier.fillMaxWidth() else Modifier
    val buttons: @Composable () -> Unit = {
        BorealisButton(
            stringResource(R.string.stream_conflict_cancel),
            { answer(ConflictChoice.CANCEL) },
            width.initialFocus(cancelFocus),
            BorealisButtonStyle.Secondary,
        )
        BorealisButton(
            stringResource(R.string.stream_conflict_join),
            { answer(ConflictChoice.JOIN) },
            width,
            BorealisButtonStyle.Primary,
        )
        BorealisButton(
            stringResource(R.string.stream_conflict_quit_and_relaunch),
            { answer(ConflictChoice.QUIT_AND_RELAUNCH) },
            width,
            BorealisButtonStyle.Danger,
        )
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) { buttons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) { buttons() }
    }
}
