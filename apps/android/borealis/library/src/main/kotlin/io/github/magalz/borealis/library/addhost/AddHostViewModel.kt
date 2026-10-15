package io.github.magalz.borealis.library.addhost

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.HostRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@HiltViewModel
class AddHostViewModel @Inject constructor(private val repository: HostRepository) : ViewModel() {
    val uiState: StateFlow<AddHostUiState> = MutableStateFlow(AddHostUiState())
    val effects: Flow<AddHostEffect> = emptyFlow()

    fun onAddressChanged(address: String) = Unit

    fun onSubmit() = Unit

    fun onCancel() = Unit
}
