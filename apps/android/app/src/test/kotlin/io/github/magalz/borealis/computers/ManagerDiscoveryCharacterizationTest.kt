package io.github.magalz.borealis.computers

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import io.github.magalz.borealis.computers.ShadowFailingConnectivityManager.Call
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * What the inherited `ComputerManagerService` does with a host that mDNS finds: it adds it, and on
 * an IPv4 address it asks a STUN server for the external address of the device, with the process
 * bound to the network under a VPN. The platform is Robolectric; the STUN request is a shadow of
 * `MoonBridge` and no test reaches a host that is not a fake on the loopback interface.
 *
 * Characterization, except for the thread of the add: the Java class added the host on the thread
 * of the discovery agent, and the service now adds it on an executor of its own (S12-18), which the
 * tests wait for with `awaitAdds`.
 */
@Suppress("DEPRECATION")
@Config(shadows = [ShadowStunMoonBridge::class, ShadowFailingConnectivityManager::class])
@RunWith(RobolectricTestRunner::class)
class ManagerDiscoveryCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    private val connectivity = shadowOf(rig.app.getSystemService(ConnectivityManager::class.java))
    private val platform: ShadowFailingConnectivityManager =
        Shadow.extract(rig.app.getSystemService(ConnectivityManager::class.java))

    @Before
    fun resetStunAndNetworks() {
        ShadowStunMoonBridge.reset()
        // The platform starts with a Wi-Fi network and a cellular one: each test sets its own.
        for (id in listOf(WIFI_ID, CELLULAR_ID)) {
            connectivity.removeNetwork(ShadowNetwork.newInstance(id))
        }
    }

    /** Adds a network of the platform. */
    private fun network(
        netId: Int,
        type: Int,
        transports: List<Int>,
        capabilities: List<Int>,
        active: Boolean = false,
    ): Network {
        val info =
            ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, true)
        val network = ShadowNetwork.newInstance(netId)
        connectivity.addNetwork(network, info)
        if (active) {
            connectivity.setActiveNetworkInfo(info)
            connectivity.setDefaultNetworkActive(true)
        }
        val netCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(netCaps).clearCapabilities()
        for (transport in transports) shadowOf(netCaps).addTransportType(transport)
        for (capability in capabilities) shadowOf(netCaps).addCapability(capability)
        connectivity.setNetworkCapabilities(network, netCaps)
        return network
    }

    /** The device is on a VPN (its active network) that runs over the networks [under]. */
    private fun vpn(vararg under: Pair<Int, Int>): List<Network> {
        network(
            VPN_ID,
            ConnectivityManager.TYPE_VPN,
            listOf(NetworkCapabilities.TRANSPORT_VPN),
            emptyList(),
            active = true,
        )
        return under.map { (id, transport) ->
            network(
                id,
                id,
                listOf(transport),
                listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            )
        }
    }

    @Test
    fun `a host that mDNS finds is added with its local address and the listener hears it from the adding thread`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
        rig.awaitAdds()

        val computer = checkNotNull(rig.binder.getComputer(UUID_1))
        assertThat(computer.localAddress).isEqualTo(host.address)
        assertThat(computer.activeAddress).isEqualTo(host.address)
        assertThat(computer.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(rig.savedRows().single().localAddress).isEqualTo(host.address)
        assertThat(updates.on(caller)).isEmpty()
        assertThat(updates.named(ADD_THREAD).map { it.state })
            .containsExactly(ComputerDetails.State.ONLINE)
        assertThat(log.lines()).contains("INFO: New PC (DEN-PC) is UUID $UUID_1")
    }

    @Test
    fun `a host that mDNS finds and that does not answer is logged and not added`() {
        rig.start()
        rig.binder.startPolling(Updates())
        val gone = rig.gone()

        rig.findAndResolve("Gone PC", "127.0.0.1", gone.port)
        rig.awaitAdds()

        val template = ComputerDetails().also { it.localAddress = gone }
        assertThat(log.lines()).contains("WARNING: Auto-discovered PC failed to respond: $template")
        assertThat(rig.savedRows()).isEmpty()
    }

    /**
     * The platform resolves a host on a thread of its own, so that a blocked add cannot hang a
     * test.
     */
    private fun resolveOnThreadOfItsOwn(name: String, host: NvFakeHost): Thread =
        thread(isDaemon = true) {
            runCatching { rig.findAndResolve(name, "127.0.0.1", host.httpPort) }
        }

    /** A host that answers `/serverinfo` as [uuid] once [answerNow] is open. */
    private fun slowHost(uuid: String, answerNow: CountDownLatch): NvFakeHost =
        rig.host().also { host ->
            host.handler = { _ ->
                answerNow.await()
                HostReply.xml(hostInfo(uuid, httpsPort = host.httpsPort))
            }
        }

    @Test
    fun `the callback of the discovery returns while the poll of the host is still going`() {
        val host = slowHost(UUID_1, rig.gate())
        rig.start()
        rig.binder.startPolling(Updates())
        val finder = resolveOnThreadOfItsOwn("Den PC", host)
        awaitUntil("the poll in flight") { host.serverInfoRequests() > 0 }

        finder.join(PROMPT_MS)

        assertThat(finder.isAlive).isFalse()
    }

    @Test
    fun `an add that is in flight ends when the service is destroyed, quietly and without a host`() {
        val host = slowHost(UUID_1, rig.gate())
        rig.start()
        rig.binder.startPolling(Updates())
        val finder = resolveOnThreadOfItsOwn("Den PC", host)
        awaitUntil("the poll in flight") { host.serverInfoRequests() > 0 }
        finder.join(PROMPT_MS)
        assertThat(liveThreads(ADD_THREAD)).hasSize(1)
        val printed = ByteArrayOutputStream()
        val standardError = System.err
        System.setErr(PrintStream(printed, true))

        try {
            rig.destroy()
            // The executor is shut down: its thread ends, and the add with it.
            awaitUntil("the add thread to end") { liveThreads(ADD_THREAD).isEmpty() }
        } finally {
            System.setErr(standardError)
        }

        assertThat(rig.binder.getComputer(UUID_1)).isNull()
        assertThat(log.lines().filter { it.contains("failed to respond") }).isEmpty()
        assertThat(printed.toString()).doesNotContain("InterruptedException")
    }

    @Test
    fun `a host that mDNS finds after the service is destroyed is dropped without an error`() {
        val host = rig.reachable(UUID_1)
        rig.start()
        rig.binder.startPolling(Updates())
        rig.destroy()

        // The discovery agent goes on calling until it is stopped: it must hear no exception.
        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)

        assertThat(host.serverInfoRequests()).isEqualTo(0)
        assertThat(liveThreads(ADD_THREAD)).isEmpty()
    }

    @Test
    fun `an add that throws is logged without the id, and the next host is still added`() {
        val unreadable = rig.reachable(UUID_1)
        val other = rig.reachable(UUID_2, "OTHER-PC")
        // A saved host whose addresses cannot be read: looking it up throws.
        rig.seedRow(UUID_1, "Den PC", "not addresses")
        rig.start()
        rig.binder.startPolling(Updates())

        val heard = runCatching {
            rig.findAndResolve("Den PC", "127.0.0.1", unreadable.httpPort)
        }
            .exceptionOrNull()
        rig.awaitAdds()
        rig.findAndResolve("Other PC", "127.0.0.1", other.httpPort)
        rig.awaitAdds()

        assertThat(heard).isNull()
        assertThat(log.lines())
            .contains("SEVERE: Could not add an auto-discovered PC: RuntimeException")
        assertThat(log.lines().filter { it.contains(UUID_1) }).isEmpty()
        assertThat(rig.binder.getComputer(UUID_1)).isNull()
        assertThat(checkNotNull(rig.binder.getComputer(UUID_2)).name).isEqualTo("OTHER-PC")
    }

    @Test
    fun `adds that arrive together run one after the other, in the order they arrived`() {
        val answerNow = rig.gate()
        val first = slowHost(UUID_1, answerNow)
        val second = rig.reachable(UUID_2, "OTHER-PC")
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        resolveOnThreadOfItsOwn("Den PC", first).join(PROMPT_MS)
        awaitUntil("the poll of the first host") { first.serverInfoRequests() > 0 }
        resolveOnThreadOfItsOwn("Other PC", second).join(PROMPT_MS)

        // The second host waits for the first, whose poll has not ended.
        Thread.sleep(SETTLE_MS)
        assertThat(second.serverInfoRequests()).isEqualTo(0)
        answerNow.countDown()
        awaitUntil("both hosts") {
            rig.binder.getComputer(UUID_1) != null && rig.binder.getComputer(UUID_2) != null
        }

        assertThat(updates.named(ADD_THREAD).map { it.name })
            .containsExactly("DEN-PC", "OTHER-PC")
            .inOrder()
    }

    @Test
    fun `a failure of the discovery is logged`() {
        rig.start()
        rig.binder.startPolling(Updates())
        val found =
            android.net.nsd.NsdServiceInfo().apply {
                serviceName = "Den PC"
                serviceType = "_nvstream._tcp"
            }
        rig.discoveries().single().onServiceFound(found)

        shadowOf(rig.app.getSystemService(android.net.nsd.NsdManager::class.java))
            .getServiceInfoCallbacks(found)
            .orEmpty()
            .single()
            .onServiceInfoCallbackRegistrationFailed(5)

        assertThat(log.lines()).contains("SEVERE: mDNS discovery failed")
    }

    @Test
    fun `an IPv4 host on a device that is not on a VPN is added without a STUN request`() {
        val host = rig.reachable(UUID_1)
        network(
            WIFI_ID,
            ConnectivityManager.TYPE_WIFI,
            listOf(NetworkCapabilities.TRANSPORT_WIFI),
            listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            active = true,
        )
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        rig.binder.startPolling(Updates())

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
        rig.awaitAdds()

        assertThat(ShadowStunMoonBridge.requests).isEmpty()
        assertThat(checkNotNull(rig.binder.getComputer(UUID_1)).remoteAddress).isNull()
    }

    @Test
    fun `under a VPN the process is bound to the network under it for the STUN request, then freed`() {
        val host = rig.reachable(UUID_1)
        val under = vpn(WIFI_ID to NetworkCapabilities.TRANSPORT_WIFI).single()
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        rig.binder.startPolling(Updates())

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
        rig.awaitAdds()

        val request = ShadowStunMoonBridge.requests.single()
        assertThat(request.host).isEqualTo("stun.moonlight-stream.org")
        assertThat(request.port).isEqualTo(STUN_PORT)
        assertThat(request.boundNetwork).isSameInstanceAs(under)
        assertThat(rig.app.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess)
            .isNull()
        // The port is a guess: the port of the address that mDNS gave.
        val computer = checkNotNull(rig.binder.getComputer(UUID_1))
        assertThat(computer.remoteAddress).isEqualTo(AddressTuple(STUN_ANSWER, host.httpPort))
        assertThat(rig.savedRows().single().remoteAddress)
            .isEqualTo(AddressTuple(STUN_ANSWER, host.httpPort))
    }

    @Test
    fun `under a VPN with no network under it the host is added without a STUN request`() {
        val host = rig.reachable(UUID_1)
        vpn(CELLULAR_ID to NetworkCapabilities.TRANSPORT_CELLULAR)
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        rig.binder.startPolling(Updates())

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
        rig.awaitAdds()

        assertThat(ShadowStunMoonBridge.requests).isEmpty()
        assertThat(rig.app.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess)
            .isNull()
        assertThat(checkNotNull(rig.binder.getComputer(UUID_1)).remoteAddress).isNull()
    }

    @Test
    fun `a STUN server that does not answer leaves the host without an external address`() {
        val host = rig.reachable(UUID_1)
        vpn(WIFI_ID to NetworkCapabilities.TRANSPORT_WIFI)
        ShadowStunMoonBridge.answer = null
        rig.start()
        rig.binder.startPolling(Updates())

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
        rig.awaitAdds()

        assertThat(ShadowStunMoonBridge.requests).hasSize(1)
        assertThat(checkNotNull(rig.binder.getComputer(UUID_1)).remoteAddress).isNull()
        assertThat(rig.app.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess)
            .isNull()
    }

    /**
     * A host that mDNS finds on a device under a VPN, while [call] of the platform throws (none
     * does when it is null). The error ends the add: how it ends does not matter here, only what
     * the add leaves behind.
     */
    private fun resolveWhileFailing(call: Call?, host: NvFakeHost) {
        vpn(WIFI_ID to NetworkCapabilities.TRANSPORT_WIFI)
        ShadowStunMoonBridge.answer = STUN_ANSWER
        platform.failing = call
        rig.start()
        rig.binder.startPolling(Updates())

        runCatching { rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort) }
        rig.awaitAdds()
    }

    @Test
    fun `the network lock is released when the list of networks cannot be read`() {
        resolveWhileFailing(Call.ALL_NETWORKS, rig.reachable(UUID_1))

        assertThat(platform.thrown).isEqualTo(1)
        assertThat(rig.defaultNetworkLockIsHeld()).isFalse()
    }

    @Test
    fun `the network lock is released when the process cannot be bound to a network`() {
        resolveWhileFailing(Call.BIND_TO_NETWORK, rig.reachable(UUID_1))

        assertThat(platform.thrown).isEqualTo(1)
        assertThat(rig.defaultNetworkLockIsHeld()).isFalse()
        assertThat(boundNetwork()).isNull()
    }

    @Test
    fun `the network lock is released when the process cannot be freed from its network`() {
        resolveWhileFailing(Call.UNBIND, rig.reachable(UUID_1))

        assertThat(ShadowStunMoonBridge.requests).hasSize(1)
        assertThat(platform.thrown).isEqualTo(1)
        assertThat(rig.defaultNetworkLockIsHeld()).isFalse()
    }

    @Test
    fun `the process is freed from its network and the lock is released when the STUN request throws`() {
        ShadowStunMoonBridge.failure = IllegalStateException("The STUN request failed")
        resolveWhileFailing(null, rig.reachable(UUID_1))

        assertThat(ShadowStunMoonBridge.requests).hasSize(1)
        assertThat(boundNetwork()).isNull()
        assertThat(rig.defaultNetworkLockIsHeld()).isFalse()
    }

    private fun boundNetwork(): Network? =
        rig.app.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000c1"
        const val UUID_2 = "00000000-0000-0000-0000-0000000000c2"
        const val SETTLE_MS = 500L
        const val PROMPT_MS = 1_000L
        const val VPN_ID = 17
        const val WIFI_ID = 1
        const val CELLULAR_ID = 0

        // Loopback, so that polling it reaches no host.
        const val STUN_ANSWER = "127.0.0.2"
        const val STUN_PORT = 3478
    }
}
