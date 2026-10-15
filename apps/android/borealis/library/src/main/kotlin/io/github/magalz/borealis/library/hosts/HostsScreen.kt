package io.github.magalz.borealis.library.hosts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

@Composable
@Suppress("UNUSED_PARAMETER")
fun HostsScreen(
    state: HostsUiState,
    formFactor: FormFactor,
    snackbarHostState: SnackbarHostState,
    onHostSelected: (String) -> Unit,
    onContinue: (String) -> Unit,
    onWake: (String) -> Unit,
    onForget: (String) -> Unit,
    onDetails: (String) -> Unit,
    onDetailsDismissed: () -> Unit,
    onAddHost: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
