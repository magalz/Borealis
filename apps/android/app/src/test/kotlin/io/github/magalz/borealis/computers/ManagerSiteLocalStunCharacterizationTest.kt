package io.github.magalz.borealis.computers

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import org.junit.After
import org.junit.Assume.assumeNotNull
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
 * The poll that saves a new host in the inherited `ComputerManagerService`: when the host is
 * reached at a site-local address (RFC 1918) and has no external address yet, the service asks a
 * STUN server for the external address of the device, with the process bound to the network under a
 * VPN, before it saves the host. A host that is saved already, one that has an external address and
 * one at another kind of address get no such request.
 *
 * A fake host on the loopback interface cannot be at a site-local address, so the tests that need
 * one serve `/serverinfo` from a site-local IPv4 address of this machine, which a poll reaches
 * without leaving it, and are skipped on a machine that has none. The STUN request is a shadow of
 * `MoonBridge`: no test reaches a host outside this machine.
 *
 * Characterization: these tests pass on the code before the refactor of the poll (BORE-76).
 */
@Suppress("DEPRECATION")
@Config(shadows = [ShadowStunMoonBridge::class])
@RunWith(RobolectricTestRunner::class)
class ManagerSiteLocalStunCharacterizationTest {
    @get:Rule val rig = ManagerRule()

    private val connectivity = shadowOf(rig.app.getSystemService(ConnectivityManager::class.java))
    private val hosts = mutableListOf<Closeable>()

    @Before
    fun resetStunAndNetworks() {
        ShadowStunMoonBridge.reset()
        // The platform starts with a Wi-Fi network and a cellular one: each test sets its own.
        for (id in listOf(WIFI_ID, CELLULAR_ID)) {
            connectivity.removeNetwork(ShadowNetwork.newInstance(id))
        }
    }

    @After
    fun closeHosts() {
        hosts.forEach { it.close() }
    }

    /** The device is on a VPN (its active network) that runs over a Wi-Fi network. */
    private fun vpnOverWifi() {
        network(
            VPN_ID,
            ConnectivityManager.TYPE_VPN,
            listOf(NetworkCapabilities.TRANSPORT_VPN),
            emptyList(),
            active = true,
        )
        network(
            WIFI_ID,
            WIFI_ID,
            listOf(NetworkCapabilities.TRANSPORT_WIFI),
            listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        )
    }

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

    /**
     * A host that answers `/serverinfo` as [UUID_1] from the site-local [address] of this machine.
     */
    private class SiteLocalHost(address: InetAddress) : Closeable {
        private val server = HttpServer.create(InetSocketAddress(address, 0), 0)
        val address = AddressTuple(address.hostAddress, server.address.port)

        init {
            server.createContext("/serverinfo") { exchange ->
                val body = hostInfo(UUID_1).toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/xml")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        override fun close() = server.stop(0)
    }

    /** The first IPv4 address of this machine that is site-local, or null when it has none. */
    private fun siteLocalAddress(): InetAddress? =
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }

    private fun siteLocalHost(): SiteLocalHost {
        val address = siteLocalAddress()
        assumeNotNull("a site-local IPv4 address on this machine", address)
        return SiteLocalHost(checkNotNull(address)).also { hosts += it }
    }

    @Test
    fun `a new host at a site-local address gets its external address from STUN before it is saved`() {
        val host = siteLocalHost()
        vpnOverWifi()
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        val request = ShadowStunMoonBridge.requests.single()
        assertThat(request.host).isEqualTo("stun.moonlight-stream.org")
        // The port is a guess: the port of the address that answered.
        val external = AddressTuple(STUN_ANSWER, host.address.port)
        assertThat(details.remoteAddress).isEqualTo(external)
        assertThat(rig.savedRows().single().remoteAddress).isEqualTo(external)
    }

    @Test
    fun `a new host at a site-local address is saved without an external address when STUN has none`() {
        val host = siteLocalHost()
        vpnOverWifi()
        ShadowStunMoonBridge.answer = null
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(ShadowStunMoonBridge.requests).hasSize(1)
        assertThat(details.remoteAddress).isNull()
        assertThat(rig.savedRows().single().uuid).isEqualTo(UUID_1)
    }

    @Test
    fun `a new host that has an external address already gets no STUN request`() {
        val host = siteLocalHost()
        vpnOverWifi()
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        // The same address in two fields is polled once.
        val details =
            ComputerDetails().also {
                it.manualAddress = host.address
                it.remoteAddress = host.address
            }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(ShadowStunMoonBridge.requests).isEmpty()
        assertThat(rig.savedRows().single().remoteAddress).isEqualTo(host.address)
    }

    @Test
    fun `a host that is saved already gets no STUN request`() {
        val host = siteLocalHost()
        vpnOverWifi()
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.seed(saved(UUID_1, "OLD-NAME", manual = host.address))
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(ShadowStunMoonBridge.requests).isEmpty()
        assertThat(rig.savedRows().single().remoteAddress).isNull()
    }

    @Test
    fun `a new host that is not at a site-local address gets no STUN request`() {
        val host = rig.reachable(UUID_1)
        vpnOverWifi()
        ShadowStunMoonBridge.answer = STUN_ANSWER
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(ShadowStunMoonBridge.requests).isEmpty()
        assertThat(rig.savedRows().single().remoteAddress).isNull()
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000e1"
        const val VPN_ID = 17
        const val WIFI_ID = 1
        const val CELLULAR_ID = 0

        // Loopback, so that polling it reaches no host.
        const val STUN_ANSWER = "127.0.0.2"
    }
}
