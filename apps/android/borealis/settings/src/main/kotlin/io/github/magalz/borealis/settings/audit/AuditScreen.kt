package io.github.magalz.borealis.settings.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** The Audit trail. */
@Composable
fun AuditScreen(
    state: AuditUiState,
    formFactor: FormFactor,
    onEntrySelected: (index: Int) -> Unit,
    onDetailDismissed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
