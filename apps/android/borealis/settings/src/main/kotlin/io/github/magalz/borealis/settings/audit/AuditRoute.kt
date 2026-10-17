package io.github.magalz.borealis.settings.audit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor

/**
 * The Audit trail (UC-007), wired to its ViewModel. [onBack] is called when the user leaves the
 * screen: Back and the arrow of the phone bar close an opened entry first.
 */
@Composable
fun AuditRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AuditScreen(
        state = state,
        formFactor = rememberFormFactor(),
        onEntrySelected = viewModel::onEntrySelected,
        onDetailDismissed = viewModel::onDetailDismissed,
        onBack = onBack,
        modifier = modifier,
    )
}
