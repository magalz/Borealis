package com.limelight.nvstream.mdns

import com.limelight.LimeLog
import java.net.Inet4Address
import java.net.Inet6Address

abstract class MdnsDiscoveryAgent(@JvmField protected val listener: MdnsDiscoveryListener) {
    @JvmField protected val computers = HashSet<MdnsComputer>()

    abstract fun startDiscovery(discoveryIntervalMs: Int)

    abstract fun stopDiscovery()

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
            synchronized(computers) {
                val computer = MdnsComputer(name, v4Addr, v6GlobalAddr, port)
                if (computers.add(computer)) {
                    // This was a new entry
                    listener.notifyComputerAdded(computer)
                }
            }
        }

        // If there were no IPv4 addresses, use IPv6 for registration
        if (v4Addrs.isEmpty()) {
            val v6LocalAddr = getLocalAddress(v6Addrs)

            if (v6LocalAddr != null || v6GlobalAddr != null) {
                val computer = MdnsComputer(name, v6LocalAddr, v6GlobalAddr, port)
                if (computers.add(computer)) {
                    // This was a new entry
                    listener.notifyComputerAdded(computer)
                }
            }
        }
    }

    fun getComputerSet(): List<MdnsComputer> = synchronized(computers) { ArrayList(computers) }

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

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress(
        "LoopWithTooManyJumpStatements",
        "NestedBlockDepth",
        "CyclomaticComplexMethod",
        "ComplexCondition",
    )
    private fun getBestIpv6Address(addresses: Array<Inet6Address>): Inet6Address? {
        // First try to find a link local address, so we can match the interface identifier
        // with a global address (this will work for SLAAC but not DHCPv6).
        val linkLocalAddr = getLinkLocalAddress(addresses)

        // We will try once to match a SLAAC interface suffix, then
        // pick the first matching address
        for (tries in 0 until BEST_ADDRESS_TRIES) {
            // We assume the addresses are already sorted in descending order
            // of preference from Bonjour.
            for (addr in addresses) {
                if (addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isLoopbackAddress) {
                    // Link-local, site-local, and loopback aren't global
                    LimeLog.info("Ignoring non-global address: ${addr.hostAddress}")
                    continue
                }

                val addrBytes = addr.address

                // 2002::/16
                if (
                    addrBytes[0].toInt() == TUNNEL_FIRST_BYTE &&
                        addrBytes[1].toInt() == SIX_TO_FOUR_SECOND_BYTE
                ) {
                    // 6to4 has horrible performance
                    LimeLog.info("Ignoring 6to4 address: ${addr.hostAddress}")
                    continue
                }
                // 2001::/32
                else if (
                    addrBytes[0].toInt() == TUNNEL_FIRST_BYTE &&
                        addrBytes[1].toInt() == TEREDO_SECOND_BYTE &&
                        addrBytes[2].toInt() == 0x00 &&
                        addrBytes[TEREDO_LAST_PREFIX_INDEX].toInt() == 0x00
                ) {
                    // Teredo also has horrible performance
                    LimeLog.info("Ignoring Teredo address: ${addr.hostAddress}")
                    continue
                }
                // fc00::/7
                else if ((addrBytes[0].toInt() and ULA_MASK) == ULA_PREFIX) {
                    // ULAs aren't global
                    LimeLog.info("Ignoring ULA: ${addr.hostAddress}")
                    continue
                }

                // Compare the final 64-bit interface identifier and skip the address
                // if it doesn't match our link-local address.
                if (linkLocalAddr != null && tries == 0) {
                    var matched = true

                    for (i in INTERFACE_IDENTIFIER_START until IPV6_ADDRESS_BYTES) {
                        if (linkLocalAddr.address[i] != addr.address[i]) {
                            matched = false
                            break
                        }
                    }

                    if (!matched) {
                        LimeLog.info("Ignoring non-matching global address: ${addr.hostAddress}")
                        continue
                    }
                }

                return addr
            }
        }

        return null
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
    }
}
