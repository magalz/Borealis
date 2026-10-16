package io.github.magalz.borealis.library.hosts

import io.github.magalz.borealis.library.domain.HostCapability
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
        version = "LuminalShine 26.9.1",
        address = "192.168.1.20",
        tags = listOf(HostCapability.HEVC_10_BIT, HostCapability.HDR),
    )
internal val livingRoomPc =
    HostItemUiState(
        "living",
        "Living Room PC",
        Reachability.OFFLINE,
        true,
        null,
        HostAction.WAKE,
        version = "LuminalShine 26.9.1",
        address = "192.168.1.31",
    )
internal val studio =
    HostItemUiState(
        "studio",
        "Studio",
        Reachability.ONLINE,
        false,
        null,
        HostAction.PAIR,
        version = "LuminalShine 26.9.1",
        address = "192.168.1.40",
        foundOnNetwork = true,
    )
internal val checkingPc =
    HostItemUiState("new", "Basement", Reachability.UNKNOWN, true, null, HostAction.NONE)

internal val continueDesktop = ContinueItemUiState("desk", "Desk PC", 1, "Desktop")

internal val withHosts =
    HostsUiState(
        loading = false,
        searching = false,
        items = listOf(deskPc, livingRoomPc, studio),
        continueItems = listOf(continueDesktop),
    )

internal val withHostsNoContinue = withHosts.copy(continueItems = emptyList())

internal val searching = HostsUiState(loading = false, searching = true)

/** Before the repository reported the hosts for the first time. */
internal val loadingHosts = HostsUiState(loading = true, searching = false)

/** A paired host that runs an app whose name the app list did not give yet. */
internal val unnamedAppPc =
    HostItemUiState(
        "unnamed",
        "Den PC",
        Reachability.ONLINE,
        true,
        RunningApp(7, null),
        HostAction.OPEN,
    )
