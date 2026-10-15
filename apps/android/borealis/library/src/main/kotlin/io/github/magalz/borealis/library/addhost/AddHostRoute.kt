package io.github.magalz.borealis.library.addhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor

/**
 * The Add host screen wired to its [AddHostViewModel]. [onAdded] gets the name the host reported
 * (UC-008 step 5): the caller closes the screen and says so on Hosts. [onClose] is Cancel and Back.
 */
@Composable
fun AddHostRoute(
    onClose: () -> Unit,
    onAdded: (name: String) -> Unit,
    viewModel: AddHostViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnAdded by rememberUpdatedState(onAdded)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is AddHostEffect.Added -> currentOnAdded(effect.name)
                    AddHostEffect.Close -> currentOnClose()
                }
            }
        }
    }

    AddHostScreen(
        state = state,
        formFactor = rememberFormFactor(),
        onAddressChanged = viewModel::onAddressChanged,
        onSubmit = viewModel::onSubmit,
        onCancel = viewModel::onCancel,
    )
}
