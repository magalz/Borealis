package io.github.magalz.borealis.settings.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.audit.AuditEntry
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.diagnostics.audit.masked
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The Audit trail screen (UC-007): the entries newest first, redacted the way the export does it,
 * and the entry that the user opened. Collecting [uiState] reads the trail; it stops five seconds
 * after the last collector leaves.
 *
 * [AuditTrail.observe] does not emit again when "Mask addresses in logs and diagnostics" changes,
 * so the entries are redacted again whenever that setting changes. The redactor that
 * [AuditEntry.masked] follows has to be switched before the setting is stored.
 */
@HiltViewModel
class AuditViewModel
internal constructor(
    trail: AuditTrail,
    settings: SettingsRepository,
    private val formatter: AuditTimeFormatter,
    @Suppress("UnusedPrivateProperty") private val mappingDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        trail: AuditTrail,
        settings: SettingsRepository,
        formatter: AuditTimeFormatter,
    ) : this(trail, settings, formatter, Dispatchers.Default)

    private val opened = MutableStateFlow<AuditEntry?>(null)

    /** The entries of the last report, as stored, for [onEntrySelected]. */
    private var latest: List<AuditEntry> = emptyList()

    val uiState: StateFlow<AuditUiState> =
        combine(
                trail.observe().onEach { latest = it },
                // Only the change matters: `masked` reads the redactor, not this value.
                settings.observe(Settings.MASK_ADDRESSES),
                opened,
            ) { entries, _, selected ->
                AuditUiState(
                    loading = false,
                    entries =
                        entries.mapIndexed { index, entry ->
                            entry.masked().toRow(index.toString())
                        },
                    detail = selected?.takeIf { it in entries }?.masked()?.toDetail(),
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AuditUiState(),
            )

    /** Opens the entry at [index] of the list; a position that is not in the list is ignored. */
    fun onEntrySelected(key: String) {
        key.toIntOrNull()?.let { latest.getOrNull(it) }?.let { opened.value = it }
    }

    /** Closes the opened entry. */
    fun onDetailDismissed() {
        opened.value = null
    }

    private fun AuditEntry.toRow(key: String) =
        AuditRow(
            key,
            type,
            formatter.short(timestampMillis),
            listOfNotNull(host, detail)
                .filter { it.isNotBlank() }
                .joinToString(SEPARATOR)
                .ifEmpty {
                    null
                },
        )

    private fun AuditEntry.toDetail() =
        AuditDetail(type, formatter.full(timestampMillis), outcome, host, detail)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEPARATOR = " · "
    }
}
