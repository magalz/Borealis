package io.github.magalz.borealis.settings.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.components.BorealisSnackbarHost
import io.github.magalz.borealis.designsystem.components.settings.SettingsCard
import io.github.magalz.borealis.designsystem.components.settings.SettingsRowTone
import io.github.magalz.borealis.designsystem.components.settings.SettingsSwitchRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.settings.R
import java.io.File
import kotlinx.coroutines.launch

private val ROW_INSET = 16.dp
private val SNACKBAR_PADDING = 16.dp

/**
 * The Privacy and diagnostics rows and their dialogs, for the "Privacy and diagnostics" group of
 * Settings on a TV or handheld. It draws in the theme it is given and keeps its own snackbar.
 * [onSendToPhone] gets the saved bundle and its `adb pull` command when the user chooses Send to
 * phone; the dialog that shows the link is the caller's, and the Saved dialog stays open under it.
 * [onOpenAudit] opens the audit trail. [startShare] starts the system share sheet that a phone gets
 * for the export; by default it starts the activity from the current context.
 */
@Composable
fun PrivacyPane(
    onSendToPhone: (File, String) -> Unit,
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
    onSendToPhone: (File, String) -> Unit,
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
                dialogs =
                    PrivacyDialogActions(
                        onEraseConfirmed = viewModel::onEraseConfirmed,
                        onEraseCancelled = viewModel::onEraseCancelled,
                        onDialogDismissed = viewModel::onDialogDismissed,
                        onSendToPhone = { bundle, command -> sendToPhone(bundle, command) },
                    ),
            )
        }
    return PrivacyBinding(state, snackbarHostState, actions)
}

/**
 * False when the system refused the share sheet: nothing handles the chooser, or the system does
 * not allow it. Any other failure is a bug and is not hidden.
 */
internal fun tryShare(startShare: (Intent) -> Unit, chooser: Intent): Boolean =
    try {
        startShare(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        BorealisLog.e(LogTag.APP, "could not start the diagnostic share", e)
        false
    } catch (e: SecurityException) {
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

/**
 * Starts [chooser] from the activity behind this context, if there is one; a context with no
 * activity needs a task of its own.
 */
internal fun Context.startShareSheet(chooser: Intent) {
    if (findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * The rows of the pane, its snackbar and its dialogs. Stateless: the dialog to show is in [state].
 * On a phone the rows are one glass card; on a TV or handheld they are on the glass of the pane.
 * When the Audit trail row had the focus as it opened the viewer, Back finds the focus on it again:
 * the screen leaves the composition while the viewer shows, and a focus that is lost with it would
 * land on whatever comes first.
 */
@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    snackbarHostState: SnackbarHostState,
    actions: PrivacyActions,
    modifier: Modifier = Modifier,
) {
    val phone = LocalFormFactor.current == FormFactor.Phone
    val auditFocus = rememberAuditRowFocus(actions.onOpenAudit)
    Box(modifier) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BorealisDimens.listPadding)
        ) {
            // On a TV or handheld the rows are on the glass of the pane, which Settings draws; a
            // phone has a screen of its own, so the rows are on a glass card.
            if (phone) SettingsCard { PrivacyRows(state, actions, auditFocus, phone) }
            else PrivacyRows(state, actions, auditFocus, phone)
        }
        BorealisSnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).padding(SNACKBAR_PADDING),
        )
    }
    PrivacyDialogs(state.dialog, actions.dialogs)
}

/** The rows of Privacy and diagnostics, and on a phone the note under them. */
@Composable
private fun PrivacyRows(
    state: PrivacyUiState,
    actions: PrivacyActions,
    auditFocus: AuditRowFocus,
    phone: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap)) {
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
        ExportRow(state.exporting, LocalFormFactor.current == FormFactor.Tv, actions.onExport)
        SettingsValueRow(
            title = stringResource(R.string.borealis_privacy_audit_title),
            value = null,
            onClick = auditFocus.onClick,
            summary = stringResource(R.string.borealis_privacy_audit_summary),
            modifier = auditFocus.modifier,
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
}

/** The Audit trail row's [modifier] and [onClick], which keep the focus across the viewer. */
private class AuditRowFocus(val modifier: Modifier, val onClick: () -> Unit)

/**
 * Remembers that the Audit trail row had the focus as it opened the viewer, and takes the focus
 * back when the screen comes back. The state is saved, as the screen leaves the composition while
 * the viewer shows. The focus goes back to the row also when the rail's Settings item pops the
 * viewer, not only on Back: either way the user returns to the row that opened it.
 */
@Composable
private fun rememberAuditRowFocus(onOpenAudit: () -> Unit): AuditRowFocus {
    val row = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var returnToRow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (returnToRow) {
            returnToRow = false
            row.requestFocus()
        }
    }
    return AuditRowFocus(
        Modifier.focusRequester(row).onFocusChanged { hasFocus = it.isFocused },
        onClick = {
            returnToRow = hasFocus
            onOpenAudit()
        },
    )
}

/**
 * The export row. While the bundle is built its summary says so and a progress bar shows under it.
 * The row stays enabled so that it keeps the focus of a D-pad (a disabled row is a different
 * element); the ViewModel ignores a second request. The bar is hidden from a screen reader, which
 * reads the summary once.
 */
@Composable
private fun ExportRow(exporting: Boolean, television: Boolean, onExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap)) {
        SettingsValueRow(
            title = stringResource(R.string.borealis_privacy_export_title),
            value = null,
            onClick = onExport,
            summary =
                stringResource(
                    when {
                        exporting -> R.string.borealis_privacy_export_working
                        television -> R.string.borealis_privacy_export_summary_tv
                        else -> R.string.borealis_privacy_export_summary_phone
                    }
                ),
        )
        if (exporting) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = ROW_INSET).clearAndSetSemantics {},
                color = LocalBorealisColors.current.accent,
            )
        }
    }
}
