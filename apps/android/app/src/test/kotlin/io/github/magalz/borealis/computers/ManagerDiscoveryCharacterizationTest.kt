package io.github.magalz.borealis.computers

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * What the inherited `ComputerManagerService` does with a host that mDNS finds: it adds it, and on
 * an IPv4 address it asks a STUN server for the external address of the device, with the process
 * bound to the network under a VPN. The platform is Robolectric; the STUN request is a shadow of
 * `MoonBridge` and no test reaches a host that is not a fake on the loopback interface.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@Suppress("DEPRECATION")
@Config(shadows = [ShadowStunMoonBridge::class])
@RunWith(RobolectricTestRunner::class)
class ManagerDiscoveryCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    private val connectivity = shadowOf(rig.app.getSystemService(ConnectivityManager::class.java))

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
    fun `a host that mDNS finds and that answers is added with its local address, and the listener hears it`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()

        rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)

        val computer = checkNotNull(rig.binder.getComputer(UUID_1))
        assertThat(computer.localAddress).isEqualTo(host.address)
        assertThat(computer.activeAddress).isEqualTo(host.address)
        assertThat(computer.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(rig.savedRows().single().localAddress).isEqualTo(host.address)
        assertThat(updates.on(caller).map { it.state })
            .containsExactly(ComputerDetails.State.ONLINE)
        assertThat(log.lines()).contains("INFO: New PC (DEN-PC) is UUID $UUID_1")
    }

    @Test
    fun `a host that mDNS finds and that does not answer is logged and not added`() {
        rig.start()
        rig.binder.startPolling(Updates())
        val gone = rig.gone()

        rig.findAndResolve("Gone PC", "127.0.0.1", gone.port)

        val template = ComputerDetails().also { it.localAddress = gone }
        assertThat(log.lines()).contains("WARNING: Auto-discovered PC failed to respond: $template")
        assertThat(rig.savedRows()).isEmpty()
    }

    @Test
    fun `a host that mDNS found is not added when its poll is interrupted, and the interrupt stays`() {
        val host = rig.host()
        val answerNow = rig.gate()
        host.handler = { _ ->
            answerNow.await()
            HostReply.xml(hostInfo(UUID_1, httpsPort = host.httpsPort))
        }
        rig.start()
        rig.binder.startPolling(Updates())
        val interrupted = AtomicBoolean(false)
        val finder =
            thread(isDaemon = true) {
                rig.findAndResolve("Den PC", "127.0.0.1", host.httpPort)
                interrupted.set(Thread.currentThread().isInterrupted)
            }
        awaitUntil("the poll in flight") { host.serverInfoRequests() > 0 }

        finder.interrupt()
        finder.join(WAIT_MS)

        assertThat(finder.isAlive).isFalse()
        assertThat(interrupted.get()).isTrue()
        assertThat(rig.binder.getComputer(UUID_1)).isNull()
        assertThat(log.lines().filter { it.contains("failed to respond") }).isEmpty()
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

        assertThat(ShadowStunMoonBridge.requests).hasSize(1)
        assertThat(checkNotNull(rig.binder.getComputer(UUID_1)).remoteAddress).isNull()
        assertThat(rig.app.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess)
            .isNull()
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000c1"
        const val VPN_ID = 17
        const val WIFI_ID = 1
        const val CELLULAR_ID = 0

        // Loopback, so that polling it reaches no host.
        const val STUN_ANSWER = "127.0.0.2"
        const val STUN_PORT = 3478
    }
}
