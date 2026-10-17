package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** What the settings screen reports to its ViewModel. */
class SettingsActions(
    val onSwitchChanged: (SettingsItem, Boolean) -> Unit,
    val onChoiceSelected: (SettingsItem, String) -> Unit,
    val onSliderChanged: (SettingsItem, Int) -> Unit,
    val onResetLayout: () -> Unit,
)

/** The settings screen, stateless. */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    formFactor: FormFactor,
    actions: SettingsActions,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    privacyPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    about: AboutSource = rememberAboutSource(),
) = Unit
