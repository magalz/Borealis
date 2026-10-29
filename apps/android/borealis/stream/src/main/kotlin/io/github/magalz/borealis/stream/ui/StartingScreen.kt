package io.github.magalz.borealis.stream.ui

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
import io.github.magalz.borealis.designsystem.components.BackdropInk
import io.github.magalz.borealis.designsystem.components.BackdropText
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Kicker
import io.github.magalz.borealis.designsystem.components.StepList
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.glass.GlassTextColors
import io.github.magalz.borealis.designsystem.glass.glassSurface
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
private val PANEL_PADDING = 16.dp
private val TEXT_GAP = 4.dp

/**
 * The stages of starting a stream (UC-002) over the background of the screens outside the game: the
 * title, the steps with the current one marked on a glass panel, a note while the host builds the
 * display, and Cancel. While the host asks Borealis to try again, a Host busy line shows the same
 * text the spinner of Sprint 2 showed, on the panel of the steps. The title and the note stand on
 * the background, so they have its halo. The screen paints no page of its own: the background
 * behind it does (`StreamChromeRoot`). Cancel has the initial focus on a TV or handheld, and the
 * gamepad B button cancels too.
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
            StepsPanel(steps(appName, hostName), phase)
            if (phase.stage.showsDisplayNote()) {
                BackdropText(
                    stringResource(R.string.stream_starting_display_note),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    ink = BackdropInk.Ink3,
                )
            }
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
        BackdropText(
            stringResource(R.string.stream_starting_title, appLabel(appName)),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        BackdropText(
            hostName,
            Modifier.padding(top = TEXT_GAP),
            style = MaterialTheme.typography.bodySmall,
            ink = BackdropInk.Ink3,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The steps, and the retry that the host asked for under them, on a glass panel: the glass keeps
 * their text at 4.5:1 over the background, as it does the text of the Library's cards.
 */
@Composable
private fun StepsPanel(steps: List<String>, phase: StreamPhase.Starting) {
    GlassTextColors {
        Column(
            Modifier.fillMaxWidth()
                .testTag(STARTING_PANEL_TAG)
                .glassSurface(MaterialTheme.shapes.large)
                .padding(PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            StepList(steps, current = phase.stage.ordinal, modifier = Modifier.fillMaxWidth())
            phase.retry?.let { HostBusy(it) }
        }
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
