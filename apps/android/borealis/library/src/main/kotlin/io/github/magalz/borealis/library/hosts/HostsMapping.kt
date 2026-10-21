package io.github.magalz.borealis.library.hosts

import androidx.annotation.StringRes
import io.github.magalz.borealis.designsystem.components.DotStatus
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.domain.Reachability

/** An online host that is not paired needs the user, so its dot is the warning one. */
internal fun HostItemUiState.dotStatus(): DotStatus =
    when (reachability) {
        Reachability.ONLINE -> if (paired) DotStatus.Online else DotStatus.Attention
        Reachability.OFFLINE -> DotStatus.Offline
        Reachability.UNKNOWN -> DotStatus.Unknown
    }

@StringRes
internal fun HostItemUiState.statusTextRes(): Int =
    when (reachability) {
        Reachability.ONLINE ->
            if (paired) R.string.hosts_status_online else R.string.hosts_status_not_paired
        Reachability.OFFLINE -> R.string.hosts_status_offline
        Reachability.UNKNOWN -> R.string.hosts_status_checking
    }

@StringRes
internal fun HostAction.labelRes(): Int? =
    when (this) {
        HostAction.OPEN -> R.string.hosts_action_open
        HostAction.PAIR -> R.string.hosts_action_pair
        HostAction.WAKE -> R.string.hosts_action_wake
        HostAction.NONE -> null
    }
