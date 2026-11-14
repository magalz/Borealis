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
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [HostRepository] over [ComputerManagerConnection]. */
@Singleton
@Suppress("LongParameterList") // Kept (BORE-92): Hilt injects one collaborator per parameter.
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
        return add(manual, host)
    }

    private suspend fun add(manual: AddressTuple, host: String): AddHostResult {
        val details = ComputerDetails().also { it.manualAddress = manual }
        // The hosts the device knew before this add: the add publishes the host that it reaches.
        val known = connection.updates.value
        val knownAtAddress = known.alreadyAddedAt(manual)
        return try {
            when {
                connection.addComputer(details) -> {
                    val id = checkNotNull(details.uuid)
                    val name = checkNotNull(details.name)
                    // The id says which host answered, whatever address it answered at; the poll
                    // that just answered says whether it is paired, a new host as well as a known
                    // one (a forgotten host still trusts this device).
                    val paired = details.pairState == PairState.PAIRED
                    if (id in known) AddHostResult.AlreadyAdded(id, name, paired)
                    else AddHostResult.Added(id, name, paired)
                }
                // A known host that does not answer is still a host that was added.
                knownAtAddress != null -> knownAtAddress
                else -> withContext(io) { unanswered(host) }
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
    }

    /**
     * What a host that did not answer is. The service swallows the failed lookup of its poll, so
     * the name is looked up here, only now that the add failed; a name that does not resolve is a
     * typo, not a closed port. The subnet check gets the address that the lookup found, so the name
     * is looked up once. Blocks: call it off the main thread.
     */
    private fun unanswered(host: String): AddHostResult {
        val target =
            try {
                InetAddress.getByName(host)
            } catch (e: UnknownHostException) {
                BorealisLog.w(
                    LogTag.HOST,
                    "host name not resolved",
                    "error" to e.javaClass.simpleName,
                )
                return AddHostResult.InvalidAddress
            }
        return if (subnet.isWrongSiteLocalAddress(target.hostAddress)) {
            AddHostResult.WrongSubnet
        } else {
            AddHostResult.Unreachable
        }
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

/** The known host that has been reached at [address], as an added host; null when none has. */
private fun Map<String, ComputerDetails>.alreadyAddedAt(
    address: AddressTuple
): AddHostResult.AlreadyAdded? =
    entries
        .firstOrNull { (_, host) ->
            address in
                listOfNotNull(
                    host.activeAddress,
                    host.localAddress,
                    host.remoteAddress,
                    host.manualAddress,
                    host.ipv6Address,
                )
        }
        ?.let { (id, host) ->
            AddHostResult.AlreadyAdded(id, host.name.orEmpty(), host.pairState == PairState.PAIRED)
        }

/**
 * The address as the dialog shows it: the host alone on the default HTTP port, else `host:port` (an
 * IPv6 address in brackets then).
 */
private fun AddressTuple.shown(): String =
    if (port == NvHTTP.DEFAULT_HTTP_PORT) address else toString()
