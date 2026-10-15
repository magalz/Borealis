package io.github.magalz.borealis.library.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.magalz.borealis.library.domain.StreamRequest

/** The apps screen of one host, wired to its ViewModel. */
@Composable
fun AppsRoute(
    hostId: String,
    onClose: (message: String?) -> Unit,
    onStartStream: (StreamRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel<AppsViewModel, AppsViewModel.Factory> { it.create(hostId) },
) = Unit
