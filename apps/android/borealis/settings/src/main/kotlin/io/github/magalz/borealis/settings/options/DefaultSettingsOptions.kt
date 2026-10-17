package io.github.magalz.borealis.settings.options

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/** The [SettingsOptions] of the app: the rules of the inherited `StreamSettings`, in Kotlin. */
class DefaultSettingsOptions : SettingsOptions {
    override fun resolutionChoices(facts: DeviceFacts): List<ResolutionChoice> = emptyList()

    override fun frameRateChoices(
        facts: DeviceFacts,
        unlockAllFrameRates: Boolean,
    ): List<FrameRateChoice> = emptyList()

    override fun rowState(
        setting: Setting<*>,
        facts: DeviceFacts,
        snapshot: SettingsSnapshot,
    ): RowState = RowState.Enabled

    override fun onScreenControlsGroupState(facts: DeviceFacts): RowState = RowState.Enabled

    override fun resetLayoutState(facts: DeviceFacts): RowState = RowState.Enabled
}
