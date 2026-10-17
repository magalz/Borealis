package io.github.magalz.borealis.settings.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** The Audit trail, wired to its ViewModel. */
@Composable
fun AuditRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditViewModel = hiltViewModel(),
) = Unit
