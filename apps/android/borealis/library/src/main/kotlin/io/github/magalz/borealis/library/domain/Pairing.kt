package io.github.magalz.borealis.library.domain

/** What the pairing screen shows while the user enters the PIN in the host's Web UI. */
data class PairingInstructions(
    val pin: String,
    /** The LuminalShine Web UI address where the PIN is entered, or null when it is unknown. */
    val webUiUrl: String?,
    /** The name this device pairs under. It never contains "xbox" or "webos". */
    val deviceName: String,
)

sealed interface PairingOutcome {
    data object Paired : PairingOutcome

    data object PinWrong : PairingOutcome

    data object AlreadyInProgress : PairingOutcome

    data object HostOffline : PairingOutcome

    /** The host dropped its pairing session. The caller pairs again once, with a new PIN. */
    data object RestartNeeded : PairingOutcome

    /** Any other failure, with the localized text to show, or null for the generic text. */
    data class Failed(val message: String?) : PairingOutcome
}

/** Pairs this device with a host. The app module implements it over PairingManager. */
interface PairingService {
    /** Generates a new PIN for [hostId]; null when the host is unknown. No network request. */
    suspend fun prepare(hostId: String): PairingInstructions?

    /**
     * Pairs with [pin]. Cancelling the calling coroutine aborts the pending host request and stores
     * nothing. [restarted] is true for the automatic second attempt after
     * [PairingOutcome.RestartNeeded].
     */
    suspend fun pair(hostId: String, pin: String, restarted: Boolean): PairingOutcome

    /** Records in the audit trail that the PIN expired before the host accepted it. */
    fun recordExpired(hostId: String)
}
