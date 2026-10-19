package io.github.magalz.borealis.library.domain

import kotlinx.coroutines.flow.Flow

/** A known or discovered host, as the Library screens show it. */
data class Host(
    /** Stable host id (the GameStream unique id). */
    val id: String,
    val name: String,
    val reachability: Reachability,
    val paired: Boolean,
    /** The app the host reports as running, or null when nothing runs. */
    val runningApp: RunningApp?,
    /** True when a Wake-on-LAN packet can be sent, because a MAC address is known. */
    val canWake: Boolean,
    /** The address the client reaches the host at, without the port; null when none is known. */
    val address: String?,
    /** What the host can stream; empty when its capabilities are unknown. */
    val capabilities: Set<HostCapability>,
)

/** A streaming capability of a host, as its card lists it. */
enum class HostCapability {
    HEVC,
    HEVC_10_BIT,
    AV1,
    AV1_10_BIT,
    YUV_444,
    HDR,
}

enum class Reachability {
    ONLINE,
    OFFLINE,
    /** Not polled yet. */
    UNKNOWN,
}

/**
 * An app that is running on a host. [name] is null while the host's app list does not know it.
 * [isDesktop] is true for the Desktop app, whose Quit ends the stream and closes no program; the
 * app adapter sets it, because the Library has no rule of its own to tell Desktop from a game.
 */
data class RunningApp(val id: Int, val name: String?, val isDesktop: Boolean = false)

/** A user-entered host after parsing: a host name or an IP literal, and the HTTP port. */
data class HostAddress(val host: String, val port: Int = DEFAULT_PORT) {
    companion object {
        /** The LuminalShine HTTP port when the user enters none (host contract, section 2). */
        const val DEFAULT_PORT = 47989
    }
}

sealed interface AddHostResult {
    /** The host answered and was added; [name] is the name it reported (UC-008 step 5). */
    data class Added(val hostId: String, val name: String) : AddHostResult

    /** The address cannot be resolved or is not a valid host. */
    data object InvalidAddress : AddHostResult

    /** A private address on a different subnet than this device. */
    data object WrongSubnet : AddHostResult

    /** The host did not answer. */
    data object Unreachable : AddHostResult
}

enum class WakeResult {
    SENT,
    ALREADY_ONLINE,
    NO_MAC_ADDRESS,
    FAILED,
}

/** Known and discovered hosts. The app module implements it over ComputerManagerService. */
interface HostRepository {
    /**
     * Every known or discovered host. Collecting starts host polling and discovery; when the last
     * collector stops, polling stops.
     */
    val hosts: Flow<List<Host>>

    suspend fun addHost(address: HostAddress): AddHostResult

    suspend fun wake(hostId: String): WakeResult

    /** Removes the host from this device and unpairs it on a best-effort basis. */
    suspend fun forget(hostId: String)

    /** Plain-text details for the host options (name, addresses, capabilities), or null. */
    suspend fun details(hostId: String): String?
}
