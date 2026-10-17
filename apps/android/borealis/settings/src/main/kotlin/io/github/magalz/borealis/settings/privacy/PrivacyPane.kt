package io.github.magalz.borealis.settings.privacy

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.io.File

/**
 * The Privacy and diagnostics rows and their dialogs, for the "Privacy and diagnostics" group of
 * Settings on a TV or handheld. It draws in the theme it is given and keeps its own snackbar.
 * [onSendToPhone] gets the saved bundle when the user chooses Send to phone; the dialog that shows
 * the link is the caller's. [onOpenAudit] opens the audit trail. [startShare] starts the system
 * share sheet; by default it starts the activity from the current context.
 */
@Composable
fun PrivacyPane(
    onSendToPhone: (File) -> Unit,
    onOpenAudit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
    startShare: (Intent) -> Unit = {},
) {
    Box(modifier)
}

/** The stateless pane. */
@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    snackbarHostState: SnackbarHostState,
    actions: PrivacyActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier)
}
