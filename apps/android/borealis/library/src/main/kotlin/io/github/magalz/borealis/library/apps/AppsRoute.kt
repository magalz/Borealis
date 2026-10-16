package io.github.magalz.borealis.library.apps

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.domain.StreamRequest
import kotlinx.coroutines.launch

/**
 * The apps of [hostId], wired to its ViewModel. [onStartStream] gets the request to hand, with the
 * Activity, to the stream launcher. [onClose] gets the localized message to show on the previous
 * screen, or null when the user went back. [launchAppId] is an app to select as a tap does once the
 * apps are loaded, for a launch request; [onLaunchHandled] says that it was.
 */
@Composable
fun AppsRoute(
    hostId: String,
    onClose: (message: String?) -> Unit,
    onStartStream: (StreamRequest) -> Unit,
    modifier: Modifier = Modifier,
    launchAppId: Int? = null,
    onLaunchHandled: () -> Unit = {},
    viewModel: AppsViewModel =
        hiltViewModel<AppsViewModel, AppsViewModel.Factory> { it.create(hostId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val startStream by rememberUpdatedState(onStartStream)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handled by rememberUpdatedState(onLaunchHandled)
    LaunchedEffect(viewModel, launchAppId, state.loading) {
        if (launchAppId != null && !state.loading) {
            viewModel.onLaunchRequested(launchAppId)
            handled()
        }
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is AppsEffect.Close -> close(effect.message.text(context))
                    is AppsEffect.StartStream -> startStream(effect.request)
                    // A snackbar waits for its own end; the next effect must not wait with it.
                    is AppsEffect.ShowMessage ->
                        launch { snackbarHostState.showSnackbar(effect.message.text(context)) }
                }
            }
        }
    }
    AppsScreen(
        state = state,
        formFactor = rememberFormFactor(),
        snackbarHostState = snackbarHostState,
        onBack = { close(null) },
        onAppSelected = viewModel::onAppSelected,
        onActions = viewModel::onActions,
        onActionChosen = viewModel::onActionChosen,
        onActionsDismissed = viewModel::onActionsDismissed,
        onDetailsDismissed = viewModel::onDetailsDismissed,
        onToggleShowHidden = viewModel::onToggleShowHidden,
        modifier = modifier,
    )
}

private fun AppsMessage.text(context: Context): String =
    when (this) {
        AppsMessage.HostUnknown -> context.getString(R.string.apps_message_host_unknown)
        AppsMessage.HostNotPaired -> context.getString(R.string.apps_message_host_not_paired)
        AppsMessage.HostOffline -> context.getString(R.string.apps_message_host_offline)
        AppsMessage.QuitDone -> context.getString(R.string.apps_message_quit_done)
        AppsMessage.PinFailed -> context.getString(R.string.apps_message_pin_failed)
        is AppsMessage.QuitFailed -> message ?: context.getString(R.string.apps_message_quit_failed)
    }
