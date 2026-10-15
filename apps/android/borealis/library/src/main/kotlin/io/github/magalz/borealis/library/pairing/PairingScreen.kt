package io.github.magalz.borealis.library.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** The pairing screen. */
@Composable
fun PairingScreen(
    state: PairingUiState,
    formFactor: FormFactor,
    onGetNewPin: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hostName: String? = null,
) = Unit
