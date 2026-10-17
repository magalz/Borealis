package io.github.magalz.borealis.settings.ui

/** What the settings screen reports to its ViewModel. */
class SettingsActions(
    val onSwitchChanged: (SettingsItem, Boolean) -> Unit,
    val onChoiceSelected: (SettingsItem, String) -> Unit,
    val onSliderChanged: (SettingsItem, Int) -> Unit,
    val onResetLayout: () -> Unit,
)
