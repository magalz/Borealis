package io.github.magalz.borealis.settings.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.io.File

/**
 * Privacy and diagnostics as a screen of its own, for a phone: a top bar with Back, then the
 * [PrivacyPane]. [onBack] leaves the screen.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onSendToPhone: (File) -> Unit,
    onOpenAudit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    Box(modifier)
}

/** The stateless phone screen. */
@Composable
internal fun PrivacyScreenContent(
    state: PrivacyUiState,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    actions: PrivacyActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier)
}
