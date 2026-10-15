package io.github.magalz.borealis.library.hosts

import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp

/** Hosts named like the captures of the proposal; the addresses are never shown. */
internal val deskPc =
    HostItemUiState(
        "desk",
        "Desk PC",
        Reachability.ONLINE,
        true,
        RunningApp(1, "Desktop"),
        HostAction.OPEN,
    )
internal val livingRoomPc =
    HostItemUiState("living", "Living Room PC", Reachability.OFFLINE, true, null, HostAction.WAKE)
internal val studio =
    HostItemUiState("studio", "Studio", Reachability.ONLINE, false, null, HostAction.PAIR)
internal val checkingPc =
    HostItemUiState("new", "Basement", Reachability.UNKNOWN, true, null, HostAction.NONE)

internal val continueDesktop = ContinueItemUiState("desk", "Desk PC", 1, "Desktop")

internal val withHosts =
    HostsUiState(
        searching = false,
        items = listOf(deskPc, livingRoomPc, studio),
        continueItems = listOf(continueDesktop),
    )

internal val withHostsNoContinue = withHosts.copy(continueItems = emptyList())

internal val searching = HostsUiState()
