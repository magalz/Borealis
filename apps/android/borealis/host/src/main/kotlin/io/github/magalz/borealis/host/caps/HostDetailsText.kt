package io.github.magalz.borealis.host.caps

import android.content.Context
import io.github.magalz.borealis.host.R
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

    internal fun build(getString: (Int, List<Any>) -> String, facts: HostDetailsFacts): String {
        fun line(id: Int, value: String?) =
            value?.takeIf { it.isNotBlank() }?.let { getString(id, listOf(it)) }

        val status =
            when (facts.state) {
                HostState.ONLINE -> R.string.host_details_status_online
                HostState.OFFLINE -> R.string.host_details_status_offline
                HostState.UNKNOWN -> R.string.host_details_status_checking
            }
        val paired =
            facts.paired?.let {
                if (it) R.string.host_details_paired_yes else R.string.host_details_paired_no
            }
        val others =
            facts.otherAddresses.filter { it.isNotBlank() && it != facts.address }.distinct()
        val lines =
            listOfNotNull(
                line(R.string.host_details_name, facts.name),
                line(R.string.host_details_status, getString(status, emptyList())),
                line(R.string.host_details_address, facts.address),
                line(R.string.host_details_other_addresses, others.joinToString(", ")),
                line(R.string.host_details_paired, paired?.let { getString(it, emptyList()) }),
                line(R.string.host_details_mac_address, facts.macAddress.takeUnless(::isNoMac)),
                line(R.string.host_details_running, facts.runningApp),
            )
        return lines.joinToString("") { it + "\n" }
    }

    /** A host that does not know its MAC address reports all zeros, as the client's update does. */
    private fun isNoMac(mac: String?): Boolean =
        mac.isNullOrBlank() || mac.all { it == '0' || it == ':' || it == '-' }
}
