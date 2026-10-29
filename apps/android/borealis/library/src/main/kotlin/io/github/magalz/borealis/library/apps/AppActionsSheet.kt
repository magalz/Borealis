package io.github.magalz.borealis.library.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.ActionSheet
import io.github.magalz.borealis.designsystem.components.ActionSheetAction
import io.github.magalz.borealis.designsystem.components.ActionStyle
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.copy.QuitCopy
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.hosts.DialogMessage

/**
 * The actions that end the running app: they ask first, because a game closes and loses its unsaved
 * progress. Desktop closes nothing, but it asks too: it ends the stream.
 */
private fun AppAction.endsApp() = this == AppAction.QUIT || this == AppAction.QUIT_AND_START

/**
 * The actions of one app, each with its consequence, one sentence per line (ADR 0052, D6, as
 * amended in Sprint 9). Choosing an action calls [onChosen], except Quit and Quit and start, which
 * ask for a confirmation first; Back or tapping outside calls [onDismiss].
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
    val desktop = actions.runningAppIsDesktop
    val confirm = confirming
    if (confirm == null) {
        ActionSheet(
            title = app,
            actions = actions.choices.map { sheetAction(it, app, running, desktop) },
            onAction = { choice ->
                if (choice.key.endsApp()) confirming = choice.key else onChosen(choice.key)
            },
            onDismiss = onDismiss,
        )
    } else {
        // One window at a time: the sheet comes back when the confirmation is cancelled.
        ConfirmQuitDialog(
            appName = if (confirm == AppAction.QUIT_AND_START) running else app,
            consequence = quitConsequence(confirm, app, running, desktop),
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
    desktop: Boolean,
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
                quitConsequence(action, app, running, desktop),
                ActionStyle.Danger,
            )
        AppAction.QUIT_AND_START ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_quit_and_start, running, app),
                quitConsequence(action, app, running, desktop),
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
        AppAction.ADD_TO_HOME_SCREEN ->
            ActionSheetAction(
                action,
                stringResource(R.string.apps_action_pin),
                stringResource(R.string.apps_action_pin_consequence, app),
            )
    }

/**
 * What an action that ends the running app costs, a sentence on each line. Desktop only ends the
 * stream; a game closes and loses its unsaved progress. [app] is the app the sheet is for and
 * [running] the app that runs, which Quit and start ends first.
 */
@Composable
private fun quitConsequence(
    action: AppAction,
    app: String,
    running: String,
    desktop: Boolean,
): String =
    when {
        action == AppAction.QUIT_AND_START && desktop ->
            stringResource(R.string.apps_action_quit_and_start_desktop_consequence)
        action == AppAction.QUIT_AND_START ->
            stringResource(R.string.apps_action_quit_and_start_consequence, running)
        else -> QuitCopy.consequence(app, desktop)
    }

/**
 * Asks before the running app ends: the question as the title and, as the body, what that costs
 * ([consequence], the one the action shows). Focus starts on Cancel, so a stray OK does not lose
 * work.
 */
@Composable
private fun ConfirmQuitDialog(
    appName: String,
    consequence: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BorealisDialog(
        title = stringResource(R.string.apps_confirm_quit, appName),
        onDismissRequest = onCancel,
        confirm =
            DialogAction(
                stringResource(R.string.apps_confirm_quit_button),
                BorealisButtonStyle.Danger,
                onConfirm,
            ),
        dismiss = DialogAction(stringResource(R.string.apps_confirm_cancel), onClick = onCancel),
        focusDismiss = true,
    ) {
        DialogMessage(consequence)
    }
}

/** The app id and whether the app supports HDR. */
@Composable
internal fun AppDetailsDialog(app: AppItemUiState, onDismiss: () -> Unit) {
    BorealisDialog(
        title = app.name,
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.apps_details_close), onClick = onDismiss),
        width = DialogWidth.Standard,
    ) {
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
    }
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false) {
    val colors = LocalBorealisColors.current
    val style = MaterialTheme.typography.bodyLarge
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
        Text(value, style = if (mono) LocalBorealisType.current.codes(style) else style)
    }
}
