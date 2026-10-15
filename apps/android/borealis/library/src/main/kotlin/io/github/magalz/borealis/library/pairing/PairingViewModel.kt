package io.github.magalz.borealis.library.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the host accepts the PIN. */
private val PIN_LIFETIME = 5.minutes

/**
 * Pairs with one host. Cancelling the pair job, by [onCancel], by expiry or when the screen goes
 * away, aborts the pending host request; the pairing service stores nothing then.
 */
@HiltViewModel(assistedFactory = PairingViewModel.Factory::class)
class PairingViewModel
@AssistedInject
constructor(@Assisted private val hostId: String, private val service: PairingService) :
    ViewModel() {
    private val state = MutableStateFlow<PairingUiState>(PairingUiState.Preparing)
    private val effectChannel = Channel<PairingEffect>(Channel.BUFFERED)
    private var pairJob: Job? = null
    private var cancelled = false

    val uiState: StateFlow<PairingUiState> = state.asStateFlow()
    val effects: Flow<PairingEffect> = effectChannel.receiveAsFlow()

    init {
        start()
    }

    /** Starts again with a new PIN after the PIN expired or pairing failed. */
    fun onGetNewPin() {
        if (state.value is PairingUiState.Expired || state.value is PairingUiState.Failed) start()
    }

    /** Closes once; ignored after the host accepted the PIN, when the screen opens the apps. */
    fun onCancel() {
        if (cancelled || state.value is PairingUiState.Paired) return
        cancelled = true
        pairJob?.cancel()
        effectChannel.trySend(PairingEffect.Close)
    }

    private fun start() {
        pairJob?.cancel()
        state.value = PairingUiState.Preparing
        pairJob = viewModelScope.launch { attempt(restarted = false) }
    }

    private suspend fun attempt(restarted: Boolean) {
        val instructions = service.prepare(hostId)
        if (instructions == null) {
            state.value = PairingUiState.HostUnknown
            return
        }
        state.value =
            PairingUiState.Waiting(
                instructions.pin,
                instructions.webUiUrl,
                instructions.deviceName,
                restarted,
            )
        val outcome =
            withTimeoutOrNull(PIN_LIFETIME) { service.pair(hostId, instructions.pin, restarted) }
        if (outcome == null) {
            service.recordExpired(hostId)
            state.value = PairingUiState.Expired
        } else {
            handle(outcome, restarted)
        }
    }

    private suspend fun handle(outcome: PairingOutcome, restarted: Boolean) {
        when (outcome) {
            PairingOutcome.Paired -> {
                state.value = PairingUiState.Paired
                effectChannel.trySend(PairingEffect.OpenApps(hostId))
            }
            PairingOutcome.RestartNeeded ->
                if (restarted) fail(PairingFailure.OTHER, null) else attempt(restarted = true)
            PairingOutcome.PinWrong -> fail(PairingFailure.PIN_WRONG, null)
            PairingOutcome.AlreadyInProgress -> fail(PairingFailure.ALREADY_IN_PROGRESS, null)
            PairingOutcome.HostOffline -> fail(PairingFailure.HOST_OFFLINE, null)
            is PairingOutcome.Failed -> fail(PairingFailure.OTHER, outcome.message)
        }
    }

    private fun fail(reason: PairingFailure, message: String?) {
        state.value = PairingUiState.Failed(reason, message)
    }

    @AssistedFactory
    interface Factory {
        fun create(hostId: String): PairingViewModel
    }
}
