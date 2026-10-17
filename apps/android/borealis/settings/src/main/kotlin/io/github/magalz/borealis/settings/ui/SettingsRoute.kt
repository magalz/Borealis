package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor

/**
 * The settings screen wired to its [SettingsViewModel] (UC-009). [onBack] leaves the screen.
 * [onOpenPrivacy] opens Privacy and diagnostics from the link row of a phone. On a TV or a handheld
 * Privacy and diagnostics is a group of this screen, and [privacyPane] draws its rows.
 *
 * The device facts are asked again when the configuration changes, because the display can change
 * with it.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    privacyPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    about: AboutSource = rememberAboutSource(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    LaunchedEffect(viewModel, configuration) { viewModel.refreshDeviceFacts() }
    val actions =
        remember(viewModel) {
            SettingsActions(
                onSwitchChanged = viewModel::onSwitchChanged,
                onChoiceSelected = viewModel::onChoiceSelected,
                onSliderChanged = viewModel::onSliderChanged,
                onResetLayout = viewModel::onResetLayout,
            )
        }
    SettingsScreen(
        state = state,
        formFactor = rememberFormFactor(),
        actions = actions,
        onBack = onBack,
        onOpenPrivacy = onOpenPrivacy,
        privacyPane = privacyPane,
        modifier = modifier,
        about = about,
    )
}
