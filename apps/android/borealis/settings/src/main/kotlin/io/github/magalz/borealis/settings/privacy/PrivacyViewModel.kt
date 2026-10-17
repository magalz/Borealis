package io.github.magalz.borealis.settings.privacy

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** The Privacy and diagnostics pane (UC-006, UC-007). */
@Suppress("UnusedPrivateProperty", "TooManyFunctions")
@HiltViewModel
class PrivacyViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val export: DiagnosticsExport,
    private val audit: AuditTrail,
) : ViewModel() {
    val uiState: StateFlow<PrivacyUiState> = MutableStateFlow(PrivacyUiState())

    val effects: Flow<PrivacyEffect> = emptyFlow()

    fun onVerboseLoggingChange(enabled: Boolean) = Unit

    fun onMaskAddressesChange(enabled: Boolean) = Unit

    fun onExport() = Unit

    fun onEraseRequested() = Unit

    fun onEraseConfirmed() = Unit

    fun onEraseCancelled() = Unit

    fun onDialogDismissed() = Unit
}
