package io.github.magalz.borealis.library.addhost

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

@Composable
@Suppress("UNUSED_PARAMETER")
fun AddHostScreen(
    state: AddHostUiState,
    formFactor: FormFactor,
    onAddressChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
