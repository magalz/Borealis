package io.github.magalz.borealis.library.hosts

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.HostRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@HiltViewModel
class HostsViewModel @Inject constructor(private val repository: HostRepository) : ViewModel() {
    val uiState: StateFlow<HostsUiState> = MutableStateFlow(HostsUiState())
    val effects: Flow<HostsEffect> = emptyFlow()

    fun onHostSelected(hostId: String) = Unit

    fun onContinue(hostId: String) = Unit

    fun onWake(hostId: String) = Unit

    fun onForget(hostId: String) = Unit

    fun onDetails(hostId: String) = Unit

    fun onDetailsDismissed() = Unit

    fun onAddHost() = Unit

    fun onSettings() = Unit
}
