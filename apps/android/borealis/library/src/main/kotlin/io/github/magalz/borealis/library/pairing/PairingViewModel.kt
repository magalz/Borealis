package io.github.magalz.borealis.library.pairing

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.PairingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@HiltViewModel(assistedFactory = PairingViewModel.Factory::class)
class PairingViewModel
@AssistedInject
constructor(@Assisted private val hostId: String, private val service: PairingService) :
    ViewModel() {
    val uiState: StateFlow<PairingUiState> = MutableStateFlow(PairingUiState.Preparing)
    val effects: Flow<PairingEffect> = emptyFlow()

    fun onGetNewPin() = Unit

    fun onCancel() = Unit

    @AssistedFactory
    interface Factory {
        fun create(hostId: String): PairingViewModel
    }
}
