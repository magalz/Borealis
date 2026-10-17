package io.github.magalz.borealis.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Privacy and diagnostics pane (UC-006, UC-007): the two switches, which write the settings
 * repository, the export of the diagnostic bundle and the erase of the audit trail.
 */
@HiltViewModel
class PrivacyViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val export: DiagnosticsExport,
    private val audit: AuditTrail,
) : ViewModel() {
    /** What the pane itself holds; the switches come from the repository. */
    private data class Local(val exporting: Boolean = false, val dialog: PrivacyDialog? = null)

    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<PrivacyEffect>(Channel.BUFFERED)

    val uiState: StateFlow<PrivacyUiState> =
        combine(
                settings.observe(Settings.VERBOSE_LOGGING),
                settings.observe(Settings.MASK_ADDRESSES),
                local,
            ) { verbose, mask, local ->
                PrivacyUiState(verbose, mask, local.exporting, local.dialog)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PrivacyUiState(
                    verboseLogging = settings.snapshot.value[Settings.VERBOSE_LOGGING],
                    maskAddresses = settings.snapshot.value[Settings.MASK_ADDRESSES],
                ),
            )

    /** One-off events: the share sheet to start and the messages to say. */
    val effects: Flow<PrivacyEffect> = effectChannel.receiveAsFlow()

    fun onVerboseLoggingChange(enabled: Boolean) {
        viewModelScope.launch { settings.set(Settings.VERBOSE_LOGGING, enabled) }
    }

    fun onMaskAddressesChange(enabled: Boolean) {
        viewModelScope.launch { settings.set(Settings.MASK_ADDRESSES, enabled) }
    }

    /** Builds the bundle, then shares it or opens the Saved dialog. Does nothing while it runs. */
    fun onExport() {
        if (local.value.exporting) return
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                when (val result = export.export()) {
                    is ExportResult.Share -> send(PrivacyEffect.StartShare(result.chooser))
                    is ExportResult.Saved -> local.update { it.copy(dialog = result.toDialog()) }
                    ExportResult.Failed ->
                        send(PrivacyEffect.ShowMessage(PrivacyMessage.ExportFailed))
                }
            } finally {
                local.update { it.copy(exporting = false) }
            }
        }
    }

    fun onEraseRequested() {
        local.update { it.copy(dialog = PrivacyDialog.EraseConfirmation) }
    }

    fun onEraseCancelled() {
        local.update {
            if (it.dialog is PrivacyDialog.EraseConfirmation) it.copy(dialog = null) else it
        }
    }

    /** Erases the trail, but only after [onEraseRequested] opened the confirmation. */
    fun onEraseConfirmed() {
        if (local.value.dialog !is PrivacyDialog.EraseConfirmation) return
        local.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val message =
                if (audit.erase()) PrivacyMessage.EraseDone else PrivacyMessage.EraseFailed
            send(PrivacyEffect.ShowMessage(message))
        }
    }

    /** Back or Close on the Saved dialog. */
    fun onDialogDismissed() {
        local.update { it.copy(dialog = null) }
    }

    private fun send(effect: PrivacyEffect) {
        effectChannel.trySend(effect)
    }

    private fun ExportResult.Saved.toDialog() =
        PrivacyDialog.Saved(location, adbPullCommand, bundle, fileName, sizeBytes)
}
