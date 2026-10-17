package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor

/**
 * The settings screen wired to its [SettingsViewModel] (UC-009). [onBack] leaves the screen.
 * [onOpenPrivacy] opens Privacy and diagnostics from the link row of a phone. On a TV or a handheld
 * Privacy and diagnostics is a group of this screen, and [privacyPane] draws its rows.
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
    SettingsScreen(
        state = viewModel.uiState.value,
        formFactor = rememberFormFactor(),
        actions = SettingsActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}),
        onBack = onBack,
        onOpenPrivacy = onOpenPrivacy,
        privacyPane = privacyPane,
        modifier = modifier,
        about = about,
    )
}
