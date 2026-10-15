package io.github.magalz.borealis.library

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells a host that is unreachable because it is on another private network from one that is not.
 */
@Singleton
internal class SubnetCheck @Inject constructor(private val networks: LocalNetworks) {
    /**
     * True when [host] is a private IPv4 address that no local interface shares a subnet with.
     * False for anything else, and when the interfaces cannot be listed.
     */
    // Some devices throw a NullPointerException from the interface listing, as upstream notes.
    @Suppress("TooGenericExceptionCaught")
    fun isWrongSiteLocalAddress(host: String): Boolean =
        try {
            val target = InetAddress.getByName(host)
            target is Inet4Address &&
                target.isSiteLocalAddress &&
                networks.siteLocalIpv4().none { sameSubnet(target.address, it) }
        } catch (e: Exception) {
            BorealisLog.d(LogTag.HOST, "subnet check skipped", "error" to e.javaClass.simpleName)
            false
        }

    /** The first [LocalIpv4.prefixLength] bits of both addresses, from the most significant one. */
    private fun sameSubnet(target: ByteArray, local: LocalIpv4): Boolean {
        val mine = local.address.address
        val prefix = local.prefixLength.coerceIn(0, BITS)
        val wholeBytes = prefix / BITS_PER_BYTE
        val rest = prefix % BITS_PER_BYTE
        if ((0 until wholeBytes).any { target[it] != mine[it] }) return false
        if (rest == 0) return true
        val mask = (BYTE_MASK shl (BITS_PER_BYTE - rest)) and BYTE_MASK
        return (target[wholeBytes].toInt() and mask) == (mine[wholeBytes].toInt() and mask)
    }

    private companion object {
        const val BITS = 32
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFF
    }
}
