package io.github.magalz.borealis.settings.ui

/**
 * What the settings screen reports to its ViewModel. [onSliderReleased] reports the slider that the
 * user let go of with the value that the gesture ended on, in the stored unit.
 */
class SettingsActions(
    val onSwitchChanged: (SettingsItem, Boolean) -> Unit,
    val onChoiceSelected: (SettingsItem, String) -> Unit,
    val onSliderChanged: (SettingsItem, Int) -> Unit,
    val onResetLayout: () -> Unit,
    val onSliderReleased: (SettingsItem, Int) -> Unit = { _, _ -> },
)
