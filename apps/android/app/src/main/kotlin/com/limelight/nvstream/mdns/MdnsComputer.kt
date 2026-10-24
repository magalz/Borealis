package com.limelight.nvstream.mdns

import java.net.Inet6Address
import java.net.InetAddress
import java.util.Objects

class MdnsComputer(
    val name: String?,
    val localAddress: InetAddress?,
    val ipv6Address: Inet6Address?,
    val port: Int,
) {
    // A computer with no name throws a NullPointerException here, as the Java did.
    override fun hashCode(): Int = Objects.requireNonNull(name).hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is MdnsComputer) {
            return false
        }

        // The name of the other computer is dereferenced, so a missing one throws, as in the Java.
        if (Objects.requireNonNull(other.name) != name || other.port != port) {
            return false
        }

        return other.localAddress == localAddress && other.ipv6Address == ipv6Address
    }

    override fun toString(): String = "[$name - $localAddress - $ipv6Address]"
}
