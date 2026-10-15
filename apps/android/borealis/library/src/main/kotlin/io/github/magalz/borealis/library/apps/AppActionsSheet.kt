package io.github.magalz.borealis.library.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magalz.borealis.designsystem.components.ActionSheet
import io.github.magalz.borealis.designsystem.components.ActionSheetAction
import io.github.magalz.borealis.designsystem.components.ActionStyle
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.library.R

private val DIALOG_MAX_WIDTH = 420.dp
private val DIALOG_MARGIN = 16.dp
private val DIALOG_PADDING = 20.dp
private val DIALOG_GAP = 16.dp
private val BUTTON_GAP = 12.dp

/** The actions that end an app: they ask first, because unsaved work in it is lost. */
private fun AppAction.losesWork() = this == AppAction.QUIT || this == AppAction.QUIT_AND_START

/**
 * The actions of one app, each with its one-line consequence (ADR 0052, D6). Choosing an action
 * calls [onChosen], except Quit and Quit and start, which ask for a confirmation first; Back or
 * tapping outside calls [onDismiss].
 */
@Composable
internal fun AppActionsSheet(
    actions: AppActionsUiState,
    onChosen: (AppAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf<AppAction?>(null) }
    val running = actions.runningAppName ?: stringResource(R.string.apps_running_app_fallback)
    val app = actions.app.name
    val confirm = confirming
    if (confirm == null) {
        ActionSheet(
            title = app,
            actions = actions.choices.map { sheetAction(it, app, running) },
            onAction = { choice ->
                if (choice.key.losesWork()) confirming = choice.key else onChosen(choice.key)
            },
            onDismiss = onDismiss,
        )
    } else {
        // One window at a time: the sheet comes back when the confirmation is cancelled.
        ConfirmQuitDialog(
            appName = if (confirm == AppAction.QUIT_AND_START) running else app,
            onConfirm = {
                confirming = null
                onChosen(confirm)
            },
            onCancel = { confirming = null },
        )
    }
}

@Composable
private fun sheetAction(
    action: AppAction,
    app: String,
    running: String,
): ActionSheetAction<AppAction> =
    when (action) {
        AppAction.RESUME ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_resume),
                stringResource(R.string.apps_action_resume_consequence, app),
            )
        AppAction.START ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_start),
                stringResource(R.string.apps_action_start_consequence, app),
            )
        AppAction.QUIT ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_quit, app),
                stringResource(R.string.apps_action_quit_consequence, app),
                ActionStyle.Danger,
            )
        AppAction.QUIT_AND_START ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_quit_and_start, running, app),
                stringResource(R.string.apps_action_quit_and_start_consequence, running),
                ActionStyle.Danger,
            )
        AppAction.DETAILS ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_details),
                stringResource(R.string.apps_action_details_consequence),
            )
        AppAction.HIDE ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_hide),
                stringResource(R.string.apps_action_hide_consequence),
            )
        AppAction.UNHIDE ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_unhide),
                stringResource(R.string.apps_action_unhide_consequence),
            )
    }

/** Asks before an app ends. Focus starts on Cancel, so a stray OK does not lose work. */
@Composable
private fun ConfirmQuitDialog(appName: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppsDialog(onDismiss = onCancel) {
        Text(
            stringResource(R.string.apps_confirm_quit, appName),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.End),
        ) {
            BorealisButton(
                stringResource(R.string.apps_confirm_cancel),
                onCancel,
                modifier = Modifier.initialFocus(remember { FocusRequester() }),
                style = BorealisButtonStyle.Secondary,
            )
            BorealisButton(
                stringResource(R.string.apps_confirm_quit_button),
                onConfirm,
                style = BorealisButtonStyle.Danger,
            )
        }
    }
}

/** The app id and whether the app supports HDR. */
@Composable
internal fun AppDetailsDialog(app: AppItemUiState, onDismiss: () -> Unit) {
    AppsDialog(onDismiss = onDismiss) {
        Text(app.name, style = MaterialTheme.typography.titleLarge)
        Detail(stringResource(R.string.apps_details_id), app.id.toString(), mono = true)
        Detail(
            stringResource(R.string.apps_details_hdr),
            stringResource(
                if (app.hdrSupported) {
                    R.string.apps_details_hdr_supported
                } else {
                    R.string.apps_details_hdr_unsupported
                }
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BorealisButton(
                stringResource(R.string.apps_details_close),
                onDismiss,
                modifier = Modifier.initialFocus(remember { FocusRequester() }),
                style = BorealisButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false) {
    val colors = LocalBorealisColors.current
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (mono) BorealisFonts.Mono else null,
        )
    }
}

/** A dialog in the Borealis look: a surface with a hairline, as wide as the content needs. */
@Composable
private fun AppsDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.extraLarge
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.padding(horizontal = DIALOG_MARGIN)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.line, shape)
                .clip(shape)
                .padding(DIALOG_PADDING),
            verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
            content = content,
        )
    }
}
