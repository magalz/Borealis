package io.github.magalz.borealis.settings.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.diagnostics.audit.AuditEntry
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.diagnostics.audit.masked
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsRepository
import java.util.Objects
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The Audit trail screen (UC-007): the entries newest first, redacted the way the export does it,
 * and the entry that the user opened. Collecting [uiState] reads the trail; it stops five seconds
 * after the last collector leaves.
 *
 * Redacting and formatting up to 2,000 entries is not light work, so it runs on [mappingDispatcher]
 * and not on the main thread.
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
    mappingDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        trail: AuditTrail,
        settings: SettingsRepository,
        formatter: AuditTimeFormatter,
    ) : this(trail, settings, formatter, Dispatchers.Default)

    /** The [AuditRow.key] of the opened entry; the entry itself may leave the trail. */
    private val openedKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AuditUiState> =
        combine(
                trail.observe(),
                // Only the change matters: `masked` reads the redactor, not this value.
                settings.observe(Settings.MASK_ADDRESSES),
                openedKey,
            ) { entries, _, opened ->
                val keys = keysOf(entries)
                AuditUiState(
                    loading = false,
                    entries =
                        entries.mapIndexed { index, entry -> entry.masked().toRow(keys[index]) },
                    detail =
                        opened
                            ?.let { keys.indexOf(it) }
                            ?.takeIf { it >= 0 }
                            ?.let { entries[it].masked().toDetail() },
                )
            }
            .flowOn(mappingDispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AuditUiState(),
            )

    /** Opens the entry with [key]; a key that is not in the list is ignored. */
    fun onEntrySelected(key: String) {
        openedKey.value = key
    }

    /** Closes the opened entry. */
    fun onDetailDismissed() {
        openedKey.value = null
    }

    /**
     * One key per entry that does not depend on its position, so a newer entry at the head does not
     * change the keys of the others: the time, the type and a hash of the rest, and the count of
     * entries before it that are the same. The keys are from the stored entries, so masking does
     * not change them.
     */
    private fun keysOf(entries: List<AuditEntry>): List<String> {
        val seen = HashMap<String, Int>()
        return entries.map { entry ->
            val content = Objects.hash(entry.outcome, entry.host, entry.detail)
            val base = "${entry.timestampMillis}:${entry.type.name}:$content"
            val before = seen[base] ?: 0
            seen[base] = before + 1
            "$base:$before"
        }
    }

    private fun AuditEntry.toRow(key: String) =
        AuditRow(
            key,
            type,
            formatter.short(timestampMillis),
            listOfNotNull(host, detail)
                .filter { it.isNotBlank() }
                .joinToString(SEPARATOR)
                .ifEmpty { null },
        )

    private fun AuditEntry.toDetail() =
        AuditDetail(type, formatter.full(timestampMillis), outcome, host, detail)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEPARATOR = " · "
    }
}
