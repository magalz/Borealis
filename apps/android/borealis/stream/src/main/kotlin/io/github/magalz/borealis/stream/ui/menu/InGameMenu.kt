package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.MenuRow
import io.github.magalz.borealis.designsystem.components.MenuRowStyle
import io.github.magalz.borealis.designsystem.components.SidePanel
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
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

/**
 * The in-game menu (UC-004): a side panel over the stream with the header "<app> · <host>" (the
 * host alone when the app is not known) and the approved rows. Back to stream has the initial
 * focus. A tap on the scrim, the gamepad B button and Back close it, from any row or chip; B and
 * Back close the Quit popup first (it takes them itself). The panel takes Back itself so that
 * Compose does not use it to move the focus out of a chip; the host's back callback is for when
 * nothing in the panel has the focus.
 *
 * The rows have no lateral movement: Left and Right do nothing, Up and Down walk every focusable in
 * one vertical order, and OK on an option row selects its next option (see [menuKeys]). The
 * segments of an option row are for a tap only.
 *
 * Quit asks first, in a popup centred over the menu in the chrome layer, because it ends the stream
 * for everyone and closes a game (its texts say which). The menu stays drawn behind the popup but
 * is out of reach of the focus, the touch and the screen reader. Disconnect does not ask: the app
 * keeps running.
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
    // The menu comes back after the popup: the focus goes to where it was, on Quit.
    LaunchedEffect(backFromConfirmation) {
        if (backFromConfirmation) {
            quitFocus.requestFocus()
            backFromConfirmation = false
        }
    }
    val header =
        if (appName.isBlank()) hostName
        else stringResource(R.string.stream_menu_header, appName, hostName)
    Box(Modifier.fillMaxSize()) {
        SidePanel(
            onDismiss = intents::closePanel,
            modifier =
                Modifier.onGamepadB(intents::closePanel)
                    .onBackKey(intents::closePanel)
                    .inertWhen(confirmingQuit),
        ) {
            Column(
                Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Vertical + WindowInsetsSides.End
                        )
                    )
                    .semantics { paneTitle = header }
            ) {
                Text(
                    header,
                    Modifier.padding(
                            start = HEADER_PADDING_HORIZONTAL,
                            top = HEADER_PADDING_TOP,
                            end = HEADER_PADDING_HORIZONTAL,
                            bottom = HEADER_PADDING_BOTTOM,
                        )
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                // The rows stay composed under the popup, out of reach, so what the user
                // opened in them is still open when the popup goes.
                MenuRows(
                    appName,
                    phase,
                    perf,
                    intents,
                    quitFocus,
                    onQuit = { confirmingQuit = true },
                )
            }
        }
        if (confirmingQuit) {
            QuitConfirmation(appName, onCancel = cancelQuit, onQuit = intents::quitApp)
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
    onQuit: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        // The focus outline of a row reaches BorealisDimens.outlineReach outside it,
        // thickOutlineReach when it is the thicker one: the padding and the gap of a list hold it.
        Modifier.menuKeys(focusManager).padding(BorealisDimens.listPadding),
        verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap),
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
 * The keys of the rows: Left and Right do nothing, and Up and Down walk the focus through every
 * focusable of the rows in the order they are drawn, one after the other, stopping at the first and
 * the last. All four are taken here, so none is left for the host to give to the game or the PC,
 * and the focus never moves sideways (issue #98). The focus search of Compose is not used for Up
 * and Down because it would skip some of the chips of Send keys, which wrap over several lines.
 */
private fun Modifier.menuKeys(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    when (event.key) {
        Key.DirectionLeft,
        Key.DirectionRight -> true
        Key.DirectionDown -> moveFocusOnKeyDown(event, focusManager, FocusDirection.Next)
        Key.DirectionUp -> moveFocusOnKeyDown(event, focusManager, FocusDirection.Previous)
        else -> false
    }
}
    // The search wraps around from the last row to the first and back: the focus stays instead.
    // Anything else that takes the focus out, as the Quit confirmation does, goes on.
    .focusProperties {
        onExit = {
            if (
                requestedFocusDirection == FocusDirection.Next ||
                    requestedFocusDirection == FocusDirection.Previous
            ) {
                cancelFocusChange()
            }
        }
    }
    .focusGroup()

private fun moveFocusOnKeyDown(
    event: KeyEvent,
    focusManager: FocusManager,
    direction: FocusDirection,
): Boolean {
    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
    return true
}

/**
 * Takes no focus from outside and is not announced, while [inert]; it stays drawn and composed and
 * keeps its state. The popup over it has the touch, with its scrim. A search for the focus that
 * would enter it is cancelled: `canFocus = false` on a group does not reach what is inside.
 */
private fun Modifier.inertWhen(inert: Boolean): Modifier = focusProperties {
    onEnter = { if (inert) cancelFocusChange() }
}
    .focusGroup()
    .then(if (inert) Modifier.clearAndSetSemantics {} else Modifier)

/** A hairline between what changes the stream and what ends the session. */
@Composable
private fun Divider() {
    Column(Modifier.fillMaxWidth().height(1.dp).background(LocalBorealisColors.current.line)) {}
}
