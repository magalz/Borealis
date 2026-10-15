package io.github.magalz.borealis.library.pairing

enum class PairingFailure {
    PIN_WRONG,
    ALREADY_IN_PROGRESS,
    HOST_OFFLINE,

    /** Any other failure; the adapter's localized text, when there is one, is in the message. */
    OTHER,
}

sealed interface PairingUiState {
    /** Waiting for the PIN from the pairing service. */
    data object Preparing : PairingUiState

    /** The host is not known (any more). */
    data object HostUnknown : PairingUiState

    /** The user enters [pin] in the host's Web UI at [webUiUrl]. */
    data class Waiting(
        val pin: String,
        val webUiUrl: String?,
        val deviceName: String,
        /** True for the automatic second attempt, after the host dropped its pairing session. */
        val restarted: Boolean,
    ) : PairingUiState {
        /** Keeps the PIN out of logs and crash reports. */
        override fun toString() =
            "Waiting(pin=****, webUiUrl=$webUiUrl, deviceName=$deviceName, restarted=$restarted)"
    }

    data object Paired : PairingUiState

    data class Failed(val reason: PairingFailure, val message: String?) : PairingUiState

    /** The PIN was not accepted in time. */
    data object Expired : PairingUiState
}

sealed interface PairingEffect {
    data class OpenApps(val hostId: String) : PairingEffect

    data object Close : PairingEffect
}
