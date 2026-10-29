package io.github.magalz.borealis.library.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.domain.WakeResult
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HostsViewModel @Inject constructor(private val repository: HostRepository) : ViewModel() {
    private val details = MutableStateFlow<String?>(null)
    private var detailsHostId: String? = null
    private val effectChannel = Channel<HostsEffect>(Channel.BUFFERED)

    /** Collecting it starts host polling; it stops five seconds after the last collector leaves. */
    val uiState: StateFlow<HostsUiState> =
        combine(repository.hosts, details) { hosts, details -> hosts.toUiState(details) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                HostsUiState(loading = true, searching = false),
            )

    val effects: Flow<HostsEffect> = effectChannel.receiveAsFlow()

    fun onHostSelected(hostId: String) {
        val item = uiState.value.items.firstOrNull { it.id == hostId } ?: return
        when (item.action) {
            HostAction.OPEN -> send(HostsEffect.OpenApps(hostId))
            HostAction.PAIR -> send(HostsEffect.OpenPairing(hostId))
            HostAction.WAKE -> onWake(hostId)
            HostAction.NONE -> send(HostsEffect.ShowMessage(item.reachability.noActionMessage()))
        }
    }

    fun onContinue(hostId: String) {
        val item = uiState.value.continueItems.firstOrNull { it.hostId == hostId } ?: return
        // The adapter decides HDR from the app and the device when it starts the stream. Continue
        // is the user's choice to resume the app that the row shows running.
        send(
            HostsEffect.StartStream(
                StreamRequest(
                    hostId,
                    item.appId,
                    item.appName,
                    hdrSupported = false,
                    resumeChosen = true,
                )
            )
        )
    }

    fun onWake(hostId: String) {
        viewModelScope.launch { send(HostsEffect.ShowMessage(repository.wake(hostId).toMessage())) }
    }

    fun onForget(hostId: String) {
        if (detailsHostId == hostId) onDetailsDismissed()
        viewModelScope.launch { repository.forget(hostId) }
    }

    fun onDetails(hostId: String) {
        viewModelScope.launch {
            details.value = repository.details(hostId)
            detailsHostId = hostId
        }
    }

    fun onDetailsDismissed() {
        details.value = null
        detailsHostId = null
    }

    fun onAddHost() = send(HostsEffect.OpenAddHost)

    fun onSettings() = send(HostsEffect.OpenSettings)

    private fun send(effect: HostsEffect) {
        effectChannel.trySend(effect)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun List<Host>.toUiState(details: String?): HostsUiState {
    val hosts = sortedBy { it.name.lowercase() }
    return HostsUiState(
        loading = false,
        searching = hosts.isEmpty(),
        items = hosts.map { it.toItem() },
        continueItems =
            hosts.mapNotNull { host ->
                val app = host.runningApp
                if (host.reachability == Reachability.ONLINE && host.paired && app != null) {
                    ContinueItemUiState(host.id, host.name, app.id, app.name)
                } else {
                    null
                }
            },
        details = details,
    )
}

private fun Host.toItem() =
    HostItemUiState(
        id = id,
        name = name,
        reachability = reachability,
        paired = paired,
        runningApp = runningApp,
        action = primaryAction(),
        address = address,
        foundOnNetwork = reachability == Reachability.ONLINE && !paired,
        tags = tags(),
    )

/**
 * The tags of the card, at most three, in this order: the best video codec (10-bit beats 8-bit, and
 * AV1 shows only when there is no HEVC 10-bit), HDR, then 4:4:4. An offline host shows none: what
 * it could do is not what it does now.
 */
private fun Host.tags(): List<HostCapability> =
    if (reachability == Reachability.OFFLINE) {
        emptyList()
    } else {
        listOfNotNull(
            CODECS_BY_PREFERENCE.firstOrNull { it in capabilities },
            HostCapability.HDR.takeIf { it in capabilities },
            HostCapability.YUV_444.takeIf { it in capabilities },
        )
    }

private val CODECS_BY_PREFERENCE =
    listOf(
        HostCapability.HEVC_10_BIT,
        HostCapability.AV1_10_BIT,
        HostCapability.HEVC,
        HostCapability.AV1,
    )

private fun Host.primaryAction(): HostAction =
    when {
        reachability == Reachability.ONLINE && paired -> HostAction.OPEN
        reachability == Reachability.ONLINE -> HostAction.PAIR
        reachability == Reachability.OFFLINE && canWake -> HostAction.WAKE
        else -> HostAction.NONE
    }

private fun WakeResult.toMessage(): HostsMessage =
    when (this) {
        WakeResult.SENT -> HostsMessage.WAKE_SENT
        WakeResult.ALREADY_ONLINE -> HostsMessage.ALREADY_ONLINE
        WakeResult.NO_MAC_ADDRESS -> HostsMessage.NO_MAC_ADDRESS
        WakeResult.FAILED -> HostsMessage.WAKE_FAILED
    }

/** Why tapping a host with no action does nothing: it is not polled yet, or it cannot be woken. */
private fun Reachability.noActionMessage(): HostsMessage =
    if (this == Reachability.UNKNOWN) HostsMessage.HOST_CHECKING else HostsMessage.HOST_UNAVAILABLE
