package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.settings.SettingsRowTone
import io.github.magalz.borealis.designsystem.components.settings.SettingsSliderRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsSwitchRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.catalog.BooleanSetting
import io.github.magalz.borealis.settings.catalog.IntSetting
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import kotlin.math.roundToInt

/**
 * What a row draws of the [SettingsUiState]: the part of it that the row reads, and only that. A
 * row is skipped unless this changes, so a change of one setting recomposes the row of that setting
 * and not every row of every pane.
 *
 * @property checked a switch: whether it is on.
 * @property choiceKey a list: the key of the entry that it shows as chosen.
 * @property resolutionChoices the entries of the Resolution list; empty for any other row.
 * @property frameRateChoices the entries of the Frame rate list; empty for any other row.
 * @property number a slider: its value, which for Bitrate is the bitrate that a stream would use.
 * @property bitrateAutomatic Bitrate: whether [number] is computed, not stored.
 */
@Immutable
internal data class RowValue(
    val checked: Boolean = false,
    val choiceKey: String = "",
    val resolutionChoices: List<ResolutionChoice> = emptyList(),
    val frameRateChoices: List<FrameRateChoice> = emptyList(),
    val number: Int = 0,
    val bitrateAutomatic: Boolean = false,
)

/** The part of this state that the row of [item] reads. */
internal fun SettingsUiState.rowValue(item: SettingsItem): RowValue =
    when (item.kind) {
        SettingsItemKind.SWITCH -> RowValue(checked = snapshot[item.setting as BooleanSetting])
        SettingsItemKind.CHOICE ->
            RowValue(
                choiceKey = item.selectedChoiceKey(snapshot),
                resolutionChoices =
                    if (item == SettingsItem.RESOLUTION) resolutionChoices else emptyList(),
                frameRateChoices =
                    if (item == SettingsItem.FRAME_RATE) frameRateChoices else emptyList(),
            )
        SettingsItemKind.SLIDER ->
            if (item == SettingsItem.BITRATE) {
                RowValue(number = bitrateKbps, bitrateAutomatic = bitrateAutomatic)
            } else {
                RowValue(number = snapshot[item.setting as IntSetting])
            }
        SettingsItemKind.ACTION -> RowValue()
    }

/** What a row can ask the screen to do besides changing a value: open its list or its question. */
internal class RowRequests(val onOpenChoice: (SettingsItem) -> Unit, val onResetLayout: () -> Unit)

/** The row of [itemState], drawn for its kind from its [value]. */
@Composable
internal fun SettingsItemRow(
    itemState: SettingsItemState,
    value: RowValue,
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
                // A firmware that crashes with HDR cannot use it, whatever is stored.
                checked =
                    value.checked &&
                        itemState.disabled?.reason != DisabledReason.HDR_BROKEN_ON_FIRMWARE,
                onCheckedChange = { actions.onSwitchChanged(item, it) },
                modifier = modifier,
                summary = summary,
                enabled = enabled,
                disabledNote = note,
            )
        SettingsItemKind.CHOICE ->
            SettingsValueRow(
                title = title,
                value = choiceValue(item, value),
                onClick = { requests.onOpenChoice(item) },
                modifier = modifier,
                summary = summary,
                enabled = enabled,
                disabledNote = note,
                onClickLabel = stringResource(R.string.settings_choose),
            )
        SettingsItemKind.SLIDER ->
            SliderItemRow(item, value, actions, modifier, title, summary, enabled, note)
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
    value: RowValue,
    actions: SettingsActions,
    modifier: Modifier,
    title: String,
    summary: String?,
    enabled: Boolean,
    note: String?,
) {
    val spec = requireNotNull(item.input.slider) { "$item has no slider" }
    val label =
        if (item == SettingsItem.BITRATE) {
            bitrateLabel(value.number, value.bitrateAutomatic)
        } else {
            stringResource(R.string.settings_value_percent, value.number)
        }
    SettingsSliderRow(
        title = title,
        value = value.number.coerceIn(spec.min, spec.max).toFloat(),
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
