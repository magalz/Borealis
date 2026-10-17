package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Immutable
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/**
 * What the settings screen shows: the [groups] that this device uses, with their visible rows, and
 * the values and choices that the rows need.
 *
 * @property snapshot every setting now; a row reads its value from it.
 * @property groups the groups with at least one visible row, in the order of UC-009. Privacy and
 *   diagnostics and About have no rows here.
 * @property resolutionChoices the entries of the Resolution list.
 * @property frameRateChoices the entries of the Frame rate list; they follow Unlock all frame rates
 *   at once.
 * @property bitrateKbps the bitrate that a stream would use now, in kbps.
 * @property bitrateAutomatic whether [bitrateKbps] is computed from the resolution and the frame
 *   rate, not stored.
 */
@Immutable
data class SettingsUiState(
    val snapshot: SettingsSnapshot,
    val groups: List<SettingsGroupState>,
    val resolutionChoices: List<ResolutionChoice>,
    val frameRateChoices: List<FrameRateChoice>,
    val bitrateKbps: Int,
    val bitrateAutomatic: Boolean,
)

/** A group of the screen and its visible rows, in order. */
@Immutable
data class SettingsGroupState(val group: SettingsGroupId, val items: List<SettingsItemState>)

/**
 * A visible row. It is shown enabled when [disabled] is `null`; otherwise it is shown, focusable
 * and unchangeable, with a note that says why.
 */
@Immutable
data class SettingsItemState(val item: SettingsItem, val disabled: RowState.Disabled? = null)
