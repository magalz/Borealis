package io.github.magalz.borealis.library.hosts

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
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
 * The Hosts screen wired to its [HostsViewModel]. State is collected while the screen is started,
 * which is what keeps host polling running only while it is on screen. Each effect becomes the
 * matching callback, or a snackbar for a message; starting the stream and opening Settings need the
 * Activity, so the caller does them through [onStartStream] and [onOpenSettings].
 */
@Composable
fun HostsRoute(
    onOpenApps: (String) -> Unit,
    onOpenPairing: (hostId: String, hostName: String?) -> Unit,
    onOpenAddHost: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartStream: (StreamRequest) -> Unit,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: HostsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenApps by rememberUpdatedState(onOpenApps)
    val currentOnOpenPairing by rememberUpdatedState(onOpenPairing)
    val currentOnOpenAddHost by rememberUpdatedState(onOpenAddHost)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnStartStream by rememberUpdatedState(onStartStream)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is HostsEffect.OpenApps -> currentOnOpenApps(effect.hostId)
                    is HostsEffect.OpenPairing -> currentOnOpenPairing(effect.hostId, null)
                    HostsEffect.OpenAddHost -> currentOnOpenAddHost()
                    HostsEffect.OpenSettings -> currentOnOpenSettings()
                    is HostsEffect.StartStream -> currentOnStartStream(effect.request)
                    // A snackbar waits until it goes away; the next effect must not wait with it.
                    is HostsEffect.ShowMessage ->
                        launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(effect.message.textRes())
                            )
                        }
                }
            }
        }
    }

    HostsScreen(
        state = state,
        formFactor = rememberFormFactor(),
        snackbarHostState = snackbarHostState,
        onHostSelected = viewModel::onHostSelected,
        onContinue = viewModel::onContinue,
        onWake = viewModel::onWake,
        onForget = viewModel::onForget,
        onDetails = viewModel::onDetails,
        onDetailsDismissed = viewModel::onDetailsDismissed,
        onAddHost = viewModel::onAddHost,
        onSettings = viewModel::onSettings,
    )
}

@StringRes
private fun HostsMessage.textRes(): Int =
    when (this) {
        HostsMessage.HOST_UNAVAILABLE -> R.string.hosts_message_unavailable
        HostsMessage.WAKE_SENT -> R.string.hosts_message_wake_sent
        HostsMessage.ALREADY_ONLINE -> R.string.hosts_message_already_online
        HostsMessage.NO_MAC_ADDRESS -> R.string.hosts_message_no_mac
        HostsMessage.WAKE_FAILED -> R.string.hosts_message_wake_failed
        HostsMessage.HOST_CHECKING -> R.string.hosts_message_checking
    }
