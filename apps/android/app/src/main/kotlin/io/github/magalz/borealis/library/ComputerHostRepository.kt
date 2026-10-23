package io.github.magalz.borealis.library

import android.content.Context
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.host.caps.HostDetailsFacts
import io.github.magalz.borealis.host.caps.HostDetailsText
import io.github.magalz.borealis.host.caps.HostInfoText
import io.github.magalz.borealis.host.caps.HostState
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [HostRepository] over [ComputerManagerConnection]. */
@Singleton
@Suppress("LongParameterList") // Hilt injects one collaborator per parameter.
internal class ComputerHostRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    private val waker: WakeSender,
    private val subnet: SubnetCheck,
    private val hiddenApps: HiddenApps,
    private val boxArt: BoxArtCache,
    private val shortcuts: ShortcutCleanup,
    @IoDispatcher private val io: CoroutineDispatcher,
) : HostRepository {
    override val hosts: Flow<List<Host>> =
        connection
            .hostUpdates()
            .map { known -> known.values.map { it.toHost() }.sortedWith(BY_NAME) }
            .distinctUntilChanged()

    /** As the manual add of 12.2, without its dialogs and its connection test. */
    override suspend fun addHost(address: HostAddress): AddHostResult {
        val host = address.host.trim()
        val manual =
            if (host.isEmpty()) null
            else runCatching { AddressTuple(host, address.port) }.getOrNull()
        if (manual == null) return AddHostResult.InvalidAddress
        return add(ComputerDetails().also { it.manualAddress = manual }, host)
    }

    private suspend fun add(details: ComputerDetails, host: String): AddHostResult =
        try {
            when {
                connection.addComputer(details) ->
                    AddHostResult.Added(checkNotNull(details.uuid), checkNotNull(details.name))
                withContext(io) { subnet.isWrongSiteLocalAddress(host) } ->
                    AddHostResult.WrongSubnet
                else -> AddHostResult.Unreachable
            }
        } catch (e: IllegalArgumentException) {
            // OkHttp throws this when the host name does not canonicalize to a valid name.
            BorealisLog.w(LogTag.HOST, "invalid host address", "error" to e.javaClass.simpleName)
            AddHostResult.InvalidAddress
        } catch (e: IOException) {
            // The service could not be bound: the host cannot be reached through it.
            BorealisLog.w(LogTag.HOST, "host not added", "error" to e.javaClass.simpleName)
            AddHostResult.Unreachable
        }

    override suspend fun wake(hostId: String): WakeResult {
        val details = connection.lookup(hostId) ?: return WakeResult.FAILED
        return when {
            details.state == ComputerDetails.State.ONLINE -> WakeResult.ALREADY_ONLINE
            details.macAddress == null -> WakeResult.NO_MAC_ADDRESS
            else ->
                withContext(io) {
                    try {
                        waker.send(details)
                        WakeResult.SENT
                    } catch (e: IOException) {
                        BorealisLog.w(LogTag.HOST, "wake failed", "error" to e.javaClass.simpleName)
                        WakeResult.FAILED
                    }
                }
        }
    }

    override suspend fun forget(hostId: String) {
        val details = connection.lookup(hostId) ?: return
        if (
            details.state == ComputerDetails.State.ONLINE && details.pairState == PairState.PAIRED
        ) {
            unpairBestEffort(details)
        }
        connection.remove(details)
        hiddenApps.clear(hostId)
        withContext(io) {
            try {
                shortcuts.hostForgotten(details)
            } finally {
                boxArt.delete(hostId)
            }
        }
    }

    /** The host in plain words, then its capabilities; no line is left over at the end. */
    override suspend fun details(hostId: String): String? =
        connection.lookup(hostId)?.let {
            val facts = HostDetailsText.build(context, it.toDetailsFacts())
            HostInfoText.appendTo(context, facts, it.borealis).trimEnd()
        }

    /** The host may not answer or confirm; the host is removed from this device either way. */
    private suspend fun unpairBestEffort(details: ComputerDetails) {
        val uniqueId = connection.binder().uniqueId
        withContext(io) {
            try {
                clients.create(details, uniqueId).unpair()
            } catch (e: IOException) {
                BorealisLog.w(LogTag.PAIR, "unpair not sent", "error" to e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        val BY_NAME: Comparator<Host> =
            compareBy(String.CASE_INSENSITIVE_ORDER) { host: Host -> host.name }.thenBy { it.id }
    }
}

/**
 * What the details dialog says about this host. The running app is named as the Hosts screen names
 * it; the UUID and the HTTPS port are not part of it (the capabilities block lists the ports).
 */
private fun ComputerDetails.toDetailsFacts() =
    HostDetailsFacts(
        name = name,
        state =
            when (state) {
                ComputerDetails.State.ONLINE -> HostState.ONLINE
                ComputerDetails.State.OFFLINE -> HostState.OFFLINE
                else -> HostState.UNKNOWN
            },
        address = activeAddress?.shown(),
        otherAddresses =
            listOfNotNull(localAddress, remoteAddress, manualAddress, ipv6Address).map {
                it.shown()
            },
        paired = pairState?.let { it == PairState.PAIRED },
        macAddress = macAddress,
        runningApp = toHost().runningApp?.name,
    )

/**
 * The address as the dialog shows it: the host alone on the default HTTP port, else `host:port` (an
 * IPv6 address in brackets then).
 */
private fun AddressTuple.shown(): String =
    if (port == NvHTTP.DEFAULT_HTTP_PORT) address else toString()
