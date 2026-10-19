package io.github.magalz.borealis.host.caps

import android.content.Context
import io.github.magalz.borealis.host.getFormattedString

/** Whether the client could reach a host at its last check. */
enum class HostState {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

/**
 * What the host details dialog says about a host, as plain values. An address is the text to show,
 * port included; a null or blank value is unknown and leaves its line out.
 */
data class HostDetailsFacts(
    val name: String?,
    val state: HostState,
    /** The address in use. */
    val address: String?,
    /** The other addresses of the host; the one in use and repeats are left out of the text. */
    val otherAddresses: List<String>,
    /** Null while the pairing of the host is unknown. */
    val paired: Boolean?,
    val macAddress: String?,
    /** The name of the app that runs on the host. */
    val runningApp: String?,
)

/** The facts block of the host details dialog. */
object HostDetailsText {
    /** One line per known fact, each ending in a newline, so that a block can follow it. */
    fun build(context: Context, facts: HostDetailsFacts): String =
        build(context::getFormattedString, facts)

    internal fun build(getString: (Int, List<Any>) -> String, facts: HostDetailsFacts): String = ""
}
