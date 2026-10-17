package io.github.magalz.borealis.settings.audit

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Audit trail screen (UC-007): the entries newest first, masked, and the opened entry. */
@HiltViewModel
class AuditViewModel
@Inject
constructor(
    trail: AuditTrail,
    settings: SettingsRepository,
    formatter: AuditTimeFormatter,
) : ViewModel() {
    val uiState: StateFlow<AuditUiState> = MutableStateFlow(AuditUiState())

    /** Opens the entry at [index] of the list. */
    fun onEntrySelected(index: Int) = Unit

    /** Closes the opened entry. */
    fun onDetailDismissed() = Unit
}
