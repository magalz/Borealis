package io.github.magalz.borealis.library

import android.content.Context
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.PairingManager.PairState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.host.caps.HostInfoText
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
@Suppress("LongParameterList")
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

    /** As `AddComputerManually.doAddPc`, without its dialogs and its connection test. */
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
                connection.addComputer(details) -> AddHostResult.Added(details.uuid, details.name)
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
        withContext(io) { shortcuts.hostForgotten(details) }
        withContext(io) { boxArt.delete(hostId) }
    }

    override suspend fun details(hostId: String): String? =
        connection.lookup(hostId)?.let {
            HostInfoText.appendTo(context, it.toString(), it.borealis)
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
