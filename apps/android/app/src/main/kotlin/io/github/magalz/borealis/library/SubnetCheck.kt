package io.github.magalz.borealis.library

import javax.inject.Inject
import javax.inject.Singleton

/** Tells a host that is unreachable because it is on another private network from one that is not. */
@Singleton
internal class SubnetCheck @Inject constructor(private val networks: LocalNetworks) {
    /**
     * True when [host] is a private IPv4 address that no local interface shares a subnet with. False
     * for anything else, and when the interfaces cannot be listed.
     */
    fun isWrongSiteLocalAddress(host: String): Boolean = TODO()
}
