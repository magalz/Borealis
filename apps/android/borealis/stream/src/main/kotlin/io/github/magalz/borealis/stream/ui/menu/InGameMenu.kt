package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.MenuRow
import io.github.magalz.borealis.designsystem.components.MenuRowStyle
import io.github.magalz.borealis.designsystem.components.SidePanel
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.ui.common.appLabel
import io.github.magalz.borealis.stream.ui.common.onBackKey
import io.github.magalz.borealis.stream.ui.common.onGamepadB

private val HEADER_PADDING_HORIZONTAL = 16.dp
private val HEADER_PADDING_TOP = 14.dp
private val HEADER_PADDING_BOTTOM = 8.dp
// The focus outline of a row reaches 5dp outside it: 8dp around the rows and 10dp between them.
private val ROWS_PADDING = 8.dp
private val ROWS_GAP = 10.dp

/**
 * The in-game menu (UC-004): a side panel over the stream with the header "<app> · <host>" (the
 * host alone when the app is not known) and the approved rows. Back to stream has the initial
 * focus. A tap on the scrim, the gamepad B button and Back close it, from any row or segment; B and
 * Back close the quit confirmation first. The panel takes Back itself so that Compose does not use
 * it to move the focus out of a segmented control; the host's back callback is for when nothing in
 * the panel has the focus.
 *
 * Quit asks first, inside the panel, because it ends the stream for everyone and closes a game (its
 * texts say which). Disconnect does not: the app keeps running.
 */
@Composable
internal fun InGameMenu(
    appName: String,
    hostName: String,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot?,
    intents: StreamIntents,
) {
    var confirmingQuit by remember { mutableStateOf(false) }
    val quitFocus = remember { FocusRequester() }
    var backFromConfirmation by remember { mutableStateOf(false) }
    val cancelQuit = {
        confirmingQuit = false
        backFromConfirmation = true
    }
    // The rows come back after the confirmation: the focus goes to where it was, on Quit.
    LaunchedEffect(backFromConfirmation) {
        if (backFromConfirmation) {
            quitFocus.requestFocus()
            backFromConfirmation = false
        }
    }
    val back = { if (confirmingQuit) cancelQuit() else intents.closePanel() }
    SidePanel(
        onDismiss = intents::closePanel,
        modifier = Modifier.onGamepadB(back).onBackKey(back),
    ) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            )
        ) {
            Text(
                if (appName.isBlank()) hostName
                else stringResource(R.string.stream_menu_header, appName, hostName),
                Modifier.padding(
                        start = HEADER_PADDING_HORIZONTAL,
                        top = HEADER_PADDING_TOP,
                        end = HEADER_PADDING_HORIZONTAL,
                        bottom = HEADER_PADDING_BOTTOM,
                    )
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            if (confirmingQuit) {
                QuitConfirmation(appName, onCancel = cancelQuit, onQuit = intents::quitApp)
            }
            // The rows stay composed under the confirmation, hidden, so that what the user opened
            // in them is still open when the confirmation goes.
            MenuRows(
                appName,
                phase,
                perf,
                intents,
                quitFocus,
                hidden = confirmingQuit,
                onQuit = { confirmingQuit = true },
            )
        }
    }
}

@Composable
private fun MenuRows(
    appName: String,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot?,
    intents: StreamIntents,
    quitFocus: FocusRequester,
    hidden: Boolean,
    onQuit: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    Column(
        Modifier.hiddenWhen(hidden).padding(ROWS_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROWS_GAP),
    ) {
        MenuRow(
            stringResource(R.string.stream_menu_back_to_stream),
            intents::closePanel,
            Modifier.initialFocus(firstFocus),
        )
        MenuRow(stringResource(R.string.stream_menu_keyboard), intents::openKeyboard)
        SendKeysRow(intents)
        PointerRow(phase, intents)
        OverlayRow(phase.overlay, intents)
        MenuRow(
            stringResource(R.string.stream_menu_refresh),
            {
                intents.requestKeyframe()
                intents.closePanel()
            },
            consequence = stringResource(R.string.stream_menu_refresh_note),
        )
        SessionInfoRow(phase.info, perf)
        MenuRow(
            stringResource(R.string.stream_menu_display),
            onClick = {},
            enabled = false,
            note = stringResource(R.string.stream_menu_display_note),
        )
        Divider()
        MenuRow(
            stringResource(R.string.stream_menu_disconnect),
            intents::disconnect,
            consequence =
                if (appName.isBlank()) {
                    stringResource(R.string.stream_menu_disconnect_consequence_unknown)
                } else {
                    stringResource(R.string.stream_menu_disconnect_consequence, appName)
                },
        )
        MenuRow(
            stringResource(R.string.stream_menu_quit, appLabel(appName)),
            onQuit,
            Modifier.focusRequester(quitFocus),
            consequence = quitConsequence(appName),
            style = MenuRowStyle.Danger,
        )
    }
}

/**
 * Takes no space, draws nothing, takes no touch and no focus, and is not announced, while [hidden];
 * what is inside stays composed and keeps its state.
 */
private fun Modifier.hiddenWhen(hidden: Boolean): Modifier =
    if (!hidden) {
        this
    } else {
        clearAndSetSemantics {}
            .focusProperties { canFocus = false }
            .layout { measurable, constraints ->
                measurable.measure(constraints)
                layout(0, 0) {}
            }
    }

/** A hairline between what changes the stream and what ends the session. */
@Composable
private fun Divider() {
    Column(Modifier.fillMaxWidth().height(1.dp).background(LocalBorealisColors.current.line)) {}
}
