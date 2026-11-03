package io.github.magalz.borealis.library.addhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.library.R

/**
 * The Add host screen wired to its [AddHostViewModel]. [onAdded] gets what to say about the host
 * once it is added (UC-008 step 5): its name, and whether it is new, already added or already
 * paired; the caller closes the screen and says so on Hosts. [onClose] is Cancel and Back.
 */
@Composable
fun AddHostRoute(
    onClose: () -> Unit,
    onAdded: (message: String) -> Unit,
    viewModel: AddHostViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val resources by rememberUpdatedState(LocalResources.current)
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnAdded by rememberUpdatedState(onAdded)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is AddHostEffect.Added ->
                        currentOnAdded(resources.getString(R.string.shell_host_added, effect.name))
                    is AddHostEffect.AlreadyAdded ->
                        currentOnAdded(
                            resources.getString(
                                if (effect.paired) R.string.shell_host_already_added_and_paired
                                else R.string.shell_host_already_added,
                                effect.name,
                            )
                        )
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
