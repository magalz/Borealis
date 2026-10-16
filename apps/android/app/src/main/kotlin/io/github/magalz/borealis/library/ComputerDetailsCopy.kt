package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple

/**
 * An exact copy of these details that shares nothing mutable with them, address tuples included:
 * the service goes on mutating its own objects in place. Unlike the upstream copy constructor it
 * keeps every field as it is.
 */
internal fun ComputerDetails.snapshot(): ComputerDetails {
    val source = this
    return ComputerDetails().apply {
        uuid = source.uuid
        name = source.name
        localAddress = source.localAddress.copied()
        remoteAddress = source.remoteAddress.copied()
        manualAddress = source.manualAddress.copied()
        ipv6Address = source.ipv6Address.copied()
        macAddress = source.macAddress
        serverCert = source.serverCert
        state = source.state
        activeAddress = source.activeAddress.copied()
        httpsPort = source.httpsPort
        externalPort = source.externalPort
        pairState = source.pairState
        runningGameId = source.runningGameId
        rawAppList = source.rawAppList
        nvidiaServer = source.nvidiaServer
        borealis = source.borealis
    }
}

/**
 * These details, or a copy of them with the app list of [previous] when they have none and
 * [previous] has one. Nothing is mutated.
 */
internal fun ComputerDetails.withListFrom(previous: ComputerDetails?): ComputerDetails {
    val list = previous?.rawAppList
    if (rawAppList != null || list == null) return this
    return snapshot().also { it.rawAppList = list }
}

/** The tuple constructor rejects a port that is not positive: such a tuple is shared as it is. */
private fun AddressTuple?.copied(): AddressTuple? =
    if (this == null || port <= 0) this else AddressTuple(address, port)

/** These details, or a copy of them with [list] when they have no app list and [list] is set. */
internal fun ComputerDetails.withListIfNone(list: String?): ComputerDetails =
    if (rawAppList != null || list == null) this else snapshot().also { it.rawAppList = list }
