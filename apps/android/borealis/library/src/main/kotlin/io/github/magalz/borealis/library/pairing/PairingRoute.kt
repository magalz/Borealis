package io.github.magalz.borealis.library.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor

/**
 * The pairing screen of [hostId], wired to its ViewModel: [onOpenApps] when the host accepted the
 * PIN, [onClose] when the user cancelled or the host is gone. [hostName] is for the title when the
 * caller knows it.
 */
@Composable
fun PairingRoute(
    hostId: String,
    onOpenApps: (hostId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hostName: String? = null,
    viewModel: PairingViewModel =
        hiltViewModel<PairingViewModel, PairingViewModel.Factory> { it.create(hostId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openApps by rememberUpdatedState(onOpenApps)
    val close by rememberUpdatedState(onClose)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is PairingEffect.OpenApps -> openApps(effect.hostId)
                    PairingEffect.Close -> close()
                }
            }
        }
    }
    PairingScreen(
        state = state,
        formFactor = rememberFormFactor(),
        onGetNewPin = viewModel::onGetNewPin,
        onCancel = viewModel::onCancel,
        modifier = modifier,
        hostName = hostName,
    )
}
