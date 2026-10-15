package io.github.magalz.borealis.library.apps

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** The apps of one host. */
@Composable
fun AppsScreen(
    state: AppsUiState,
    formFactor: FormFactor,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAppSelected: (appId: Int) -> Unit,
    onActions: (appId: Int) -> Unit,
    onActionChosen: (AppAction) -> Unit,
    onActionsDismissed: () -> Unit,
    onDetailsDismissed: () -> Unit,
    onToggleShowHidden: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
