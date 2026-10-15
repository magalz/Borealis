package io.github.magalz.borealis.library.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** The pairing screen of one host, wired to its ViewModel. */
@Composable
fun PairingRoute(
    hostId: String,
    onOpenApps: (hostId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hostName: String? = null,
    viewModel: PairingViewModel =
        hiltViewModel<PairingViewModel, PairingViewModel.Factory> { it.create(hostId) },
) = Unit
