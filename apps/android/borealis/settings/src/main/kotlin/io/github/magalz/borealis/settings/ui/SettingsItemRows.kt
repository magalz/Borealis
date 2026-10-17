@file:Suppress("MatchingDeclarationName")

package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.settings.SettingsRowTone
import io.github.magalz.borealis.designsystem.components.settings.SettingsSliderRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsSwitchRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import kotlin.math.roundToInt

/** What a row can ask the screen to do besides changing a value: open its list or its question. */
internal class RowRequests(val onOpenChoice: (SettingsItem) -> Unit, val onResetLayout: () -> Unit)

/** The row of [itemState], drawn for its kind from the values in [state]. */
@Composable
internal fun SettingsItemRow(
    itemState: SettingsItemState,
    state: SettingsUiState,
    actions: SettingsActions,
    requests: RowRequests,
    modifier: Modifier = Modifier,
) {
    val item = itemState.item
    val title = stringResource(item.titleRes)
    val summary = item.summaryRes?.let { stringResource(it) }
    val enabled = itemState.disabled == null
    val note = disabledNote(itemState.disabled)
    when (item.kind) {
        SettingsItemKind.SWITCH ->
            SettingsSwitchRow(
                title = title,
                checked = state.snapshot[item.setting as BooleanSetting],
                onCheckedChange = { actions.onSwitchChanged(item, it) },
                modifier = modifier,
                summary = summary,
                enabled = enabled,
                disabledNote = note,
            )
        SettingsItemKind.CHOICE ->
            SettingsValueRow(
                title = title,
                value = choiceValue(item, state),
                onClick = { requests.onOpenChoice(item) },
                modifier = modifier,
                summary = summary,
                enabled = enabled,
                disabledNote = note,
                onClickLabel = stringResource(R.string.settings_choose),
            )
        SettingsItemKind.SLIDER ->
            SliderItemRow(item, state, actions, modifier, title, summary, enabled, note)
        SettingsItemKind.ACTION ->
            SettingsValueRow(
                title = title,
                value = null,
                onClick = requests.onResetLayout,
                modifier = modifier,
                summary = summary,
                enabled = enabled,
                disabledNote = note,
                tone = SettingsRowTone.Danger,
            )
    }
}

@Composable
private fun SliderItemRow(
    item: SettingsItem,
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier,
    title: String,
    summary: String?,
    enabled: Boolean,
    note: String?,
) {
    val spec = requireNotNull(item.slider) { "$item has no slider" }
    val isBitrate = item == SettingsItem.BITRATE
    val value = if (isBitrate) state.bitrateKbps else state.snapshot[item.setting as IntSetting]
    val label =
        if (isBitrate) {
            bitrateLabel(state.bitrateKbps, state.bitrateAutomatic)
        } else {
            stringResource(R.string.settings_value_percent, value)
        }
    SettingsSliderRow(
        title = title,
        value = value.coerceIn(spec.min, spec.max).toFloat(),
        valueRange = spec.min.toFloat()..spec.max.toFloat(),
        step = spec.step.toFloat(),
        valueLabel = label,
        onValueChange = { actions.onSliderChanged(item, it.roundToInt()) },
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        disabledNote = note,
        keyStep = spec.keyStep.toFloat(),
    )
}
