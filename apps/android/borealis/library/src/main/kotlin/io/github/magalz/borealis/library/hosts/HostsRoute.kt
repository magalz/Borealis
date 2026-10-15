package io.github.magalz.borealis.library.hosts

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.library.domain.StreamRequest

@Composable
@Suppress("UNUSED_PARAMETER")
fun HostsRoute(
    onOpenApps: (String) -> Unit,
    onOpenPairing: (String) -> Unit,
    onOpenAddHost: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartStream: (StreamRequest) -> Unit,
    viewModel: HostsViewModel,
) = Unit
