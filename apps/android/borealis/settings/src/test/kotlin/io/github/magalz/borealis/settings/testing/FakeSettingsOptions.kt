package io.github.magalz.borealis.settings.testing

import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.options.SettingsOptions
import io.github.magalz.borealis.settings.repository.SettingsSnapshot

/**
 * A [SettingsOptions] that answers what the test set, whatever the facts and the snapshot are: the
 * 720p resolution only, the 30 and 60 fps rates, and every row enabled.
 *
 * Set [resolutions], [frameRates], [unlockedFrameRates] (answered while Unlock all frame rates is
 * on), [rowStates] (a setting that is not in the map is [RowState.Enabled]),
 * [onScreenControlsGroup] and [resetLayout] to change the answers.
 */
class FakeSettingsOptions : SettingsOptions {
    var resolutions: List<ResolutionChoice> =
        listOf(ResolutionChoice("1280x720", 1280, 720, ResolutionKind.STANDARD))
    var frameRates: List<FrameRateChoice> =
        listOf(FrameRateChoice(30, isNative = false), FrameRateChoice(60, isNative = false))
    var unlockedFrameRates: List<FrameRateChoice> =
        frameRates +
            listOf(FrameRateChoice(90, isNative = false), FrameRateChoice(120, isNative = false))
    val rowStates: MutableMap<Setting<*>, RowState> = mutableMapOf()
    var onScreenControlsGroup: RowState = RowState.Enabled
    var resetLayout: RowState = RowState.Enabled

    override fun resolutionChoices(facts: DeviceFacts): List<ResolutionChoice> = resolutions

    override fun frameRateChoices(
        facts: DeviceFacts,
        unlockAllFrameRates: Boolean,
    ): List<FrameRateChoice> = if (unlockAllFrameRates) unlockedFrameRates else frameRates

    override fun rowState(
        setting: Setting<*>,
        facts: DeviceFacts,
        snapshot: SettingsSnapshot,
    ): RowState = rowStates[setting] ?: RowState.Enabled

    override fun onScreenControlsGroupState(facts: DeviceFacts): RowState = onScreenControlsGroup

    override fun resetLayoutState(facts: DeviceFacts): RowState = resetLayout
}
