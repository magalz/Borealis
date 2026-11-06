package com.limelight.nvstream.mdns

import com.limelight.LimeLog
import java.net.Inet4Address
import java.net.Inet6Address

abstract class MdnsDiscoveryAgent(@JvmField protected val listener: MdnsDiscoveryListener) {
    @JvmField protected val computers = HashSet<MdnsComputer>()

    abstract fun startDiscovery(discoveryIntervalMs: Int)

    abstract fun stopDiscovery()

    // The end of the agent, when its owner is done with it. The default is to stop discovery.
    open fun close() = stopDiscovery()

    protected fun reportNewComputer(
        name: String?,
        port: Int,
        v4Addrs: Array<Inet4Address>,
        v6Addrs: Array<Inet6Address>,
    ) {
        LimeLog.info("mDNS: $name has ${v4Addrs.size} IPv4 addresses")
        LimeLog.info("mDNS: $name has ${v6Addrs.size} IPv6 addresses")

        val v6GlobalAddr = getBestIpv6Address(v6Addrs)

        // Add a computer object for each IPv4 address reported by the PC
        for (v4Addr in v4Addrs) {
            addAndNotify(MdnsComputer(name, v4Addr, v6GlobalAddr, port))
        }

        // If there were no IPv4 addresses, use IPv6 for registration
        if (v4Addrs.isEmpty()) {
            val v6LocalAddr = getLocalAddress(v6Addrs)

            if (v6LocalAddr != null || v6GlobalAddr != null) {
                addAndNotify(MdnsComputer(name, v6LocalAddr, v6GlobalAddr, port))
            }
        }
    }

    // The set is guarded by its own lock, and the listener is told after the lock is released, so
    // that a listener that reads the set or calls back into the agent never holds it.
    private fun addAndNotify(computer: MdnsComputer) {
        val isNew = synchronized(computers) { computers.add(computer) }
        if (isNew) {
            listener.notifyComputerAdded(computer)
        }
    }

    fun getComputerSet(): List<MdnsComputer> = synchronized(computers) { ArrayList(computers) }

    // The listener is told of a computer once, so a computer that is dropped here is told of again
    // when it is next reported. The same lock as the adds guards the set.
    fun forget(computer: MdnsComputer) {
        synchronized(computers) { computers.remove(computer) }
    }

    private fun getLocalAddress(addresses: Array<Inet6Address>): Inet6Address? {
        for (addr in addresses) {
            if (addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
                return addr
            }
            // fc00::/7 - ULAs
            else if ((addr.address[0].toInt() and ULA_MASK) == ULA_PREFIX) {
                return addr
            }
        }

        return null
    }

    private fun getLinkLocalAddress(addresses: Array<Inet6Address>): Inet6Address? {
        for (addr in addresses) {
            if (addr.isLinkLocalAddress) {
                LimeLog.info("Found link-local address: ${addr.hostAddress}")
                return addr
            }
        }

        return null
    }

    private fun getBestIpv6Address(addresses: Array<Inet6Address>): Inet6Address? {
        // First try to find a link local address, so we can match the interface identifier
        // with a global address (this will work for SLAAC but not DHCPv6).
        val linkLocalAddr = getLinkLocalAddress(addresses)

        // We will try once to match a SLAAC interface suffix, then
        // pick the first matching address
        return (0 until BEST_ADDRESS_TRIES).firstNotNullOfOrNull { tries ->
            // We assume the addresses are already sorted in descending order
            // of preference from Bonjour.
            addresses.firstOrNull { addr ->
                isGlobalAddress(addr) &&
                    (tries > 0 ||
                        linkLocalAddr == null ||
                        sharesInterfaceIdentifier(linkLocalAddr, addr))
            }
        }
    }

    private companion object {
        private const val IPV6_ADDRESS_BYTES = 16
        private const val INTERFACE_IDENTIFIER_START = 8

        // The first byte of fc00::/7, once the last bit is masked off
        private const val ULA_MASK = 0xfe
        private const val ULA_PREFIX = 0xfc

        // The first byte of 2002::/16 (6to4) and of 2001::/32 (Teredo), and the second of each
        private const val TUNNEL_FIRST_BYTE = 0x20
        private const val SIX_TO_FOUR_SECOND_BYTE = 0x02
        private const val TEREDO_SECOND_BYTE = 0x01

        // The 32-bit prefix of Teredo ends at this byte
        private const val TEREDO_LAST_PREFIX_INDEX = 3

        // One try to match a SLAAC interface suffix, then one to pick the first matching address
        private const val BEST_ADDRESS_TRIES = 2

        // Logs the reason when the address is not one to connect to from outside the network
        private fun isGlobalAddress(addr: Inet6Address): Boolean {
            val addrBytes = addr.address
            val reason =
                when {
                    // Link-local, site-local, and loopback aren't global
                    addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isLoopbackAddress ->
                        "non-global address"
                    // 6to4 has horrible performance
                    isSixToFour(addrBytes) -> "6to4 address"
                    // Teredo also has horrible performance
                    isTeredo(addrBytes) -> "Teredo address"
                    // ULAs aren't global
                    isUniqueLocal(addrBytes) -> "ULA"
                    else -> null
                }

            if (reason != null) {
                LimeLog.info("Ignoring $reason: ${addr.hostAddress}")
            }
            return reason == null
        }

        // 2002::/16
        private fun isSixToFour(addrBytes: ByteArray): Boolean =
            addrBytes[0].toInt() == TUNNEL_FIRST_BYTE &&
                addrBytes[1].toInt() == SIX_TO_FOUR_SECOND_BYTE

        // 2001::/32
        private fun isTeredo(addrBytes: ByteArray): Boolean {
            val inTeredoBlock =
                addrBytes[0].toInt() == TUNNEL_FIRST_BYTE &&
                    addrBytes[1].toInt() == TEREDO_SECOND_BYTE
            return inTeredoBlock &&
                addrBytes[2].toInt() == 0x00 &&
                addrBytes[TEREDO_LAST_PREFIX_INDEX].toInt() == 0x00
        }

        // fc00::/7
        private fun isUniqueLocal(addrBytes: ByteArray): Boolean =
            (addrBytes[0].toInt() and ULA_MASK) == ULA_PREFIX

        // Compare the final 64-bit interface identifier and skip the address
        // if it doesn't match our link-local address.
        private fun sharesInterfaceIdentifier(
            linkLocalAddr: Inet6Address,
            addr: Inet6Address,
        ): Boolean {
            val linkLocalBytes = linkLocalAddr.address
            val addrBytes = addr.address
            val matched =
                (INTERFACE_IDENTIFIER_START until IPV6_ADDRESS_BYTES).all {
                    linkLocalBytes[it] == addrBytes[it]
                }

            if (!matched) {
                LimeLog.info("Ignoring non-matching global address: ${addr.hostAddress}")
            }
            return matched
        }
    }
}
