package io.github.magalz.borealis.settings.ui

import io.github.magalz.borealis.settings.DefaultBitrate
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.options.SettingsOptions
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/**
 * Builds the [SettingsUiState] for a snapshot of the settings and the facts of the device: which
 * groups and rows the device uses, which of them another setting blocks, and the lists that the
 * [options] offer.
 */
internal class SettingsStateBuilder(private val options: SettingsOptions) {
    /** The state of the screen for [snapshot] on a device with [deviceFacts]. */
    fun build(snapshot: SettingsSnapshot, deviceFacts: DeviceFacts): SettingsUiState =
        SettingsUiState(
            snapshot = snapshot,
            groups = SettingsGroupId.entries.mapNotNull { groupState(it, snapshot, deviceFacts) },
            resolutionChoices = options.resolutionChoices(deviceFacts),
            frameRateChoices = options.frameRateChoices(deviceFacts, snapshot[Settings.UNLOCK_FPS]),
            bitrateKbps = effectiveBitrateKbps(snapshot),
            bitrateAutomatic = snapshot.isBitrateAutomatic,
        )

    private fun groupState(
        group: SettingsGroupId,
        snapshot: SettingsSnapshot,
        deviceFacts: DeviceFacts,
    ): SettingsGroupState? {
        val hidden =
            group == SettingsGroupId.ON_SCREEN_CONTROLS &&
                options.onScreenControlsGroupState(deviceFacts) is RowState.Hidden
        if (hidden) return null
        val items =
            SettingsItem.entries
                .filter { it.group == group }
                .mapNotNull { itemState(it, snapshot, deviceFacts) }
        return items.takeIf { it.isNotEmpty() }?.let { SettingsGroupState(group, it) }
    }

    private fun itemState(
        item: SettingsItem,
        snapshot: SettingsSnapshot,
        deviceFacts: DeviceFacts,
    ): SettingsItemState? {
        val setting = item.setting
        val state =
            if (setting == null) {
                options.resetLayoutState(deviceFacts)
            } else {
                options.rowState(setting, deviceFacts, snapshot)
            }
        return when (state) {
            RowState.Hidden -> null
            RowState.Enabled -> SettingsItemState(item)
            is RowState.Disabled -> SettingsItemState(item, state)
        }
    }

    /** The bitrate of a stream; a stored resolution that cannot be read counts as the default. */
    private fun effectiveBitrateKbps(snapshot: SettingsSnapshot): Int = runCatching {
        snapshot.effectiveBitrateKbps()
    }
        .getOrElse {
            DefaultBitrate.kbps(Settings.RESOLUTION.default, Settings.FRAME_RATE.default)
        }
}
