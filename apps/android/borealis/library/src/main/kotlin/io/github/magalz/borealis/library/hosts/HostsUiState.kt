package io.github.magalz.borealis.library.hosts

import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest

/** What tapping a host does. */
enum class HostAction {
    /** The host is online and paired: open its apps. */
    OPEN,

    /** The host is online and not paired: pair it. */
    PAIR,

    /** The host is offline and a Wake-on-LAN packet can be sent. */
    WAKE,

    /** Nothing to do: the host is offline without a MAC address, or not polled yet. */
    NONE,
}

data class HostItemUiState(
    val id: String,
    val name: String,
    val reachability: Reachability,
    val paired: Boolean,
    val runningApp: RunningApp?,
    val action: HostAction,
    /** The address to show in the detail line, or null when none is known. */
    val address: String? = null,
    /** True for an online host that is not paired: the line says it was found on the network. */
    val foundOnNetwork: Boolean = false,
    /** The capability tags to show, in display order; none for an offline host. */
    val tags: List<HostCapability> = emptyList(),
)

/** An app that is running on an online, paired host, for the Continue row. */
data class ContinueItemUiState(
    val hostId: String,
    val hostName: String,
    val appId: Int,
    /** Null while the host does not report the name of the app. */
    val appName: String?,
)

data class HostsUiState(
    /** True until the repository reports the known hosts for the first time. */
    val loading: Boolean = true,
    /** True once loaded with no host known: the screen explains the next step (ADR 0052, D8). */
    val searching: Boolean = false,
    /** The hosts, sorted by name. */
    val items: List<HostItemUiState> = emptyList(),
    /** The hosts with a running app, in host name order. */
    val continueItems: List<ContinueItemUiState> = emptyList(),
    /** The plain-text host details to show in a dialog, or null when no dialog is open. */
    val details: String? = null,
)

enum class HostsMessage {
    HOST_UNAVAILABLE,
    WAKE_SENT,
    ALREADY_ONLINE,
    NO_MAC_ADDRESS,
    WAKE_FAILED,
    HOST_CHECKING,
}

sealed interface HostsEffect {
    data class OpenApps(val hostId: String) : HostsEffect

    data class OpenPairing(val hostId: String) : HostsEffect

    data object OpenAddHost : HostsEffect

    data object OpenSettings : HostsEffect

    /** The screen hands [request] and its Activity to the stream launcher. */
    data class StartStream(val request: StreamRequest) : HostsEffect

    data class ShowMessage(val message: HostsMessage) : HostsEffect
}
