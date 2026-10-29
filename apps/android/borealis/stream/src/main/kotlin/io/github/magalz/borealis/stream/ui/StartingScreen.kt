package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Kicker
import io.github.magalz.borealis.designsystem.components.StepList
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.host.launch.LaunchProgressText
import io.github.magalz.borealis.stream.LaunchRetry
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.ui.common.appLabel
import io.github.magalz.borealis.stream.ui.common.onGamepadB

/** Marks the column of the starting screen, so a test can measure it. */
internal const val STARTING_CONTENT_TAG = "starting_content"

/** Marks the glass panel of the steps of the starting screen, so a test can measure it. */
internal const val STARTING_PANEL_TAG = "starting_panel"

private const val CONTENT_WIDTH_FRACTION = 0.86f
private val CONTENT_GAP = 16.dp
private val TEXT_GAP = 4.dp

/**
 * The stages of starting a stream (UC-002) over the black stream: the title, the steps with the
 * current one marked, a note while the host builds the display, and Cancel. While the host asks
 * Borealis to try again, a Host busy line shows the same text the spinner of Sprint 2 showed.
 * Cancel has the initial focus on a TV or handheld, and the gamepad B button cancels too.
 */
@Composable
fun StartingScreen(
    appName: String,
    hostName: String,
    phase: StreamPhase.Starting,
    intents: StreamIntents,
) {
    val cancelFocus = remember { FocusRequester() }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(LocalBorealisColors.current.page)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGamepadB(intents::leave),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.testTag(STARTING_CONTENT_TAG)
                .width(minOf(maxWidth * CONTENT_WIDTH_FRACTION, BorealisDimens.contentMaxWidth))
                .verticalScroll(rememberScrollState())
                .padding(LocalBorealisSpacing.current.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            Heading(appName, hostName)
            StepList(
                steps(appName, hostName),
                current = phase.stage.ordinal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (phase.stage.showsDisplayNote()) {
                Text(
                    stringResource(R.string.stream_starting_display_note),
                    Modifier.fillMaxWidth(),
                    color = LocalBorealisColors.current.ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            phase.retry?.let { HostBusy(it) }
            BorealisButton(
                stringResource(R.string.stream_starting_cancel),
                onClick = intents::leave,
                modifier = Modifier.initialFocus(cancelFocus),
                style = BorealisButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun Heading(appName: String, hostName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.stream_starting_title, appLabel(appName)),
            Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            hostName,
            Modifier.padding(top = TEXT_GAP),
            color = LocalBorealisColors.current.ink3,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun steps(appName: String, hostName: String): List<String> =
    listOf(
        stringResource(R.string.stream_starting_step_connecting, hostName),
        stringResource(R.string.stream_starting_step_app, appLabel(appName)),
        stringResource(R.string.stream_starting_step_display),
        stringResource(R.string.stream_starting_step_first_frame),
    )

private fun StartStage.showsDisplayNote() =
    this == StartStage.STARTING_APP || this == StartStage.PREPARING_DISPLAY

/** The retry the host asked for, announced to a screen reader as it changes. */
@Composable
private fun HostBusy(retry: LaunchRetry) {
    val text =
        LaunchProgressText.format(
            LocalContext.current,
            retry.category?.ordinal ?: -1,
            retry.attempt,
            retry.maxAttempts,
            retry.delayMs,
        )
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(TEXT_GAP),
    ) {
        Kicker(stringResource(R.string.stream_starting_host_busy))
        Text(
            text,
            color = LocalBorealisColors.current.warningInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
