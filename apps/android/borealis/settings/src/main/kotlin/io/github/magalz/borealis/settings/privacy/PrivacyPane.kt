package io.github.magalz.borealis.settings.privacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.components.settings.SettingsRowTone
import io.github.magalz.borealis.designsystem.components.settings.SettingsSwitchRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.R
import java.io.File
import kotlinx.coroutines.launch

/** The outline of a focused row reaches 5dp outside it: 8dp around the list, 10dp between rows. */
private val LIST_PADDING = 8.dp
private val ROW_GAP = 10.dp
private val ROW_INSET = 16.dp
private val SNACKBAR_PADDING = 16.dp

/**
 * The Privacy and diagnostics rows and their dialogs, for the "Privacy and diagnostics" group of
 * Settings on a TV or handheld. It draws in the theme it is given and keeps its own snackbar.
 * [onSendToPhone] gets the saved bundle when the user chooses Send to phone; the dialog that shows
 * the link is the caller's, and the Saved dialog stays open under it. [onOpenAudit] opens the audit
 * trail. [startShare] starts the system share sheet that a phone gets for the export; by default it
 * starts the activity from the current context.
 */
@Composable
fun PrivacyPane(
    onSendToPhone: (File) -> Unit,
    onOpenAudit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
    startShare: (Intent) -> Unit = rememberShareStarter(),
) {
    val binding = rememberPrivacyBinding(viewModel, onSendToPhone, onOpenAudit, startShare)
    PrivacyContent(binding.state, binding.snackbarHostState, binding.actions, modifier)
}

/** What a stateful entry point passes to its stateless content. */
internal class PrivacyBinding(
    val state: PrivacyUiState,
    val snackbarHostState: SnackbarHostState,
    val actions: PrivacyActions,
)

/**
 * Connects [viewModel] to the content: its state, a snackbar for its messages and its effects. The
 * share sheet is started through [startShare], and a share that the system refuses says that the
 * export failed.
 */
@Composable
internal fun rememberPrivacyBinding(
    viewModel: PrivacyViewModel,
    onSendToPhone: (File) -> Unit,
    onOpenAudit: () -> Unit,
    startShare: (Intent) -> Unit,
): PrivacyBinding {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val share by rememberUpdatedState(startShare)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                val message =
                    when (effect) {
                        is PrivacyEffect.ShowMessage -> effect.message
                        is PrivacyEffect.StartShare ->
                            PrivacyMessage.ExportFailed.takeIf { !tryShare(share, effect.chooser) }
                    }
                // A snackbar waits for its own end; the next effect must not wait with it.
                if (message != null) {
                    launch { snackbarHostState.showSnackbar(resources.getString(message.text())) }
                }
            }
        }
    }
    val openAudit by rememberUpdatedState(onOpenAudit)
    val sendToPhone by rememberUpdatedState(onSendToPhone)
    val actions =
        remember(viewModel) {
            PrivacyActions(
                onVerboseLoggingChange = viewModel::onVerboseLoggingChange,
                onMaskAddressesChange = viewModel::onMaskAddressesChange,
                onExport = viewModel::onExport,
                onOpenAudit = { openAudit() },
                onEraseRequested = viewModel::onEraseRequested,
                onEraseConfirmed = viewModel::onEraseConfirmed,
                onEraseCancelled = viewModel::onEraseCancelled,
                onDialogDismissed = viewModel::onDialogDismissed,
                onSendToPhone = { sendToPhone(it) },
            )
        }
    return PrivacyBinding(state, snackbarHostState, actions)
}

/** False when the system refused the share sheet. */
@Suppress("TooGenericExceptionCaught")
private fun tryShare(startShare: (Intent) -> Unit, chooser: Intent): Boolean =
    try {
        startShare(chooser)
        true
    } catch (e: RuntimeException) {
        BorealisLog.e(LogTag.APP, "could not start the diagnostic share", e)
        false
    }

private fun PrivacyMessage.text(): Int =
    when (this) {
        PrivacyMessage.ExportFailed -> R.string.borealis_privacy_export_failed
        PrivacyMessage.EraseDone -> R.string.borealis_privacy_erased
        PrivacyMessage.EraseFailed -> R.string.borealis_privacy_erase_failed
    }

/** Starts the share sheet from the current context. */
@Composable
internal fun rememberShareStarter(): (Intent) -> Unit {
    val context = LocalContext.current
    return remember(context) { { chooser -> context.startShareSheet(chooser) } }
}

/** Starts [chooser]; a context that is not an activity needs a task of its own. */
internal fun Context.startShareSheet(chooser: Intent) {
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

/**
 * The rows of the pane, its snackbar and its dialogs. Stateless: the dialog to show is in [state].
 */
@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    snackbarHostState: SnackbarHostState,
    actions: PrivacyActions,
    modifier: Modifier = Modifier,
) {
    val phone = LocalFormFactor.current == FormFactor.Phone
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LIST_PADDING),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.borealis_privacy_verbose_title),
                checked = state.verboseLogging,
                onCheckedChange = actions.onVerboseLoggingChange,
                summary = stringResource(R.string.borealis_privacy_verbose_summary),
            )
            SettingsSwitchRow(
                title = stringResource(R.string.borealis_privacy_mask_title),
                checked = state.maskAddresses,
                onCheckedChange = actions.onMaskAddressesChange,
                summary = stringResource(R.string.borealis_privacy_mask_summary),
            )
            ExportRow(state.exporting, phone, actions.onExport)
            SettingsValueRow(
                title = stringResource(R.string.borealis_privacy_audit_title),
                value = null,
                onClick = actions.onOpenAudit,
                summary = stringResource(R.string.borealis_privacy_audit_summary),
            )
            SettingsValueRow(
                title = stringResource(R.string.borealis_privacy_erase_title),
                value = null,
                onClick = actions.onEraseRequested,
                summary = stringResource(R.string.borealis_privacy_erase_summary),
                tone = SettingsRowTone.Danger,
            )
            if (phone) {
                Text(
                    stringResource(R.string.borealis_privacy_footer),
                    Modifier.padding(horizontal = ROW_INSET),
                    color = LocalBorealisColors.current.ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).padding(SNACKBAR_PADDING),
        )
    }
    PrivacyDialogs(state.dialog, actions)
}

/** The export row, and under it a progress bar while the bundle is being built. */
@Composable
private fun ExportRow(exporting: Boolean, phone: Boolean, onExport: () -> Unit) {
    val working = stringResource(R.string.borealis_privacy_export_working)
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        SettingsValueRow(
            title = stringResource(R.string.borealis_privacy_export_title),
            value = null,
            onClick = onExport,
            summary =
                stringResource(
                    if (phone) R.string.borealis_privacy_export_summary_phone
                    else R.string.borealis_privacy_export_summary_tv
                ),
            enabled = !exporting,
            disabledNote = working,
        )
        if (exporting) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = ROW_INSET).semantics {
                    contentDescription = working
                },
                color = LocalBorealisColors.current.accent,
            )
        }
    }
}
