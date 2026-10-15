package io.github.magalz.borealis.library.addhost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.HostAddressParser
import io.github.magalz.borealis.library.domain.HostAddressParser.ParseResult
import io.github.magalz.borealis.library.domain.HostRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddHostViewModel @Inject constructor(private val repository: HostRepository) : ViewModel() {
    private val state = MutableStateFlow(AddHostUiState())
    private val effectChannel = Channel<AddHostEffect>(Channel.BUFFERED)

    val uiState: StateFlow<AddHostUiState> = state.asStateFlow()
    val effects: Flow<AddHostEffect> = effectChannel.receiveAsFlow()

    fun onAddressChanged(address: String) {
        state.update { it.copy(address = address, error = null) }
    }

    fun onSubmit() {
        if (state.value.busy) return
        when (val parsed = HostAddressParser.parse(state.value.address)) {
            ParseResult.Empty -> state.update { it.copy(error = AddHostError.EMPTY) }
            ParseResult.Invalid -> state.update { it.copy(error = AddHostError.INVALID) }
            is ParseResult.Valid -> add(parsed)
        }
    }

    fun onCancel() {
        effectChannel.trySend(AddHostEffect.Close)
    }

    private fun add(parsed: ParseResult.Valid) {
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = repository.addHost(parsed.address)
            state.update { it.copy(busy = false, error = result.toError()) }
            if (result is AddHostResult.Added)
                effectChannel.trySend(AddHostEffect.Added(result.name))
        }
    }
}

private fun AddHostResult.toError(): AddHostError? =
    when (this) {
        is AddHostResult.Added -> null
        AddHostResult.InvalidAddress -> AddHostError.INVALID
        AddHostResult.WrongSubnet -> AddHostError.WRONG_SUBNET
        AddHostResult.Unreachable -> AddHostError.UNREACHABLE
    }
