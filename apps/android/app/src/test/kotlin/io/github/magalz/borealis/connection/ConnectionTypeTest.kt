package io.github.magalz.borealis.connection

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.StreamConfiguration
import java.net.InetAddress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * A stream that asks for the connection type to be detected (`STREAM_CFG_AUTO`) is local, remote or
 * left to moonlight-common-c, from what the active network and its routes say about the address of
 * the host, which is 127.0.0.1 here. A remote stream uses the packet size of 1024 bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class ConnectionTypeTest {
    @get:Rule val rig = ConnectionRig()

    private val connectivity =
        ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(ConnectivityManager::class.java)

    private val shadow = shadowOf(connectivity)

    /** The default network of Robolectric: cellular, which is no VPN. */
    private val network: Network = checkNotNull(connectivity.activeNetwork)

    private fun capabilities(
        transport: Int,
        vpn: Boolean = false,
    ): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also {
            val capabilities = shadowOf(it)
            capabilities.addTransportType(transport)
            if (vpn) capabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }

    private fun link(vararg routes: RouteInfo): LinkProperties =
        LinkProperties().also { properties ->
            // A route is added to the interface of the link, which is hidden API.
            ReflectionHelpers.callInstanceMethod<Any>(
                properties,
                "setInterfaceName",
                ClassParameter.from(String::class.java, INTERFACE),
            )
            routes.forEach { properties.addRoute(it) }
        }

    /** A route to [address]/[length] through [gateway], or on-link without one. */
    private fun route(address: String, length: Int, gateway: String? = null): RouteInfo {
        val prefixClass = Class.forName("android.net.IpPrefix")
        val prefix =
            ReflectionHelpers.callConstructor(
                prefixClass,
                ClassParameter.from(InetAddress::class.java, InetAddress.getByName(address)),
                ClassParameter.from(Int::class.javaPrimitiveType, length),
            )
        return ReflectionHelpers.callConstructor(
            RouteInfo::class.java,
            ClassParameter.from(prefixClass, prefix),
            ClassParameter.from(InetAddress::class.java, gateway?.let(InetAddress::getByName)),
            ClassParameter.from(String::class.java, INTERFACE),
            ClassParameter.from(Int::class.javaPrimitiveType, RouteInfo.RTN_UNICAST),
        )
    }

    private fun onWifi(vararg routes: RouteInfo) {
        shadow.setNetworkCapabilities(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI))
        shadow.setLinkProperties(network, link(*routes))
    }

    /** The connection type and the packet size that reached the native connection. */
    private fun negotiated(remote: Int = StreamConfiguration.STREAM_CFG_AUTO): Pair<Int, Int> {
        val config = rig.config {
            setRemoteConfiguration(remote)
            setMaxPacketSize(CONFIGURED_PACKET_SIZE)
        }
        rig.start(rig.connection(config))
        val args = ShadowRecordingBridge.await("startConnection").args
        rig.listener.awaitEnded()
        return args[REMOTE_ARG] as Int to args[PACKET_SIZE_ARG] as Int
    }

    @Test
    fun `a local connection type that the configuration gives is kept, with its packet size`() {
        assertThat(negotiated(StreamConfiguration.STREAM_CFG_LOCAL))
            .isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL to CONFIGURED_PACKET_SIZE)
    }

    @Test
    fun `a remote connection type that the configuration gives is kept, with its packet size`() {
        assertThat(negotiated(StreamConfiguration.STREAM_CFG_REMOTE))
            .isEqualTo(StreamConfiguration.STREAM_CFG_REMOTE to CONFIGURED_PACKET_SIZE)
    }

    @Test
    fun `cellular is remote, and a remote stream has the small packet size`() {
        assertThat(negotiated())
            .isEqualTo(StreamConfiguration.STREAM_CFG_REMOTE to REMOTE_PACKET_SIZE)
    }

    @Test
    fun `a VPN transport is remote`() {
        shadow.setNetworkCapabilities(
            network,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI).also {
                shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            },
        )
        shadow.setLinkProperties(network, link(route("127.0.0.0", 8)))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_REMOTE)
    }

    @Test
    fun `a network that is not marked as no VPN is remote`() {
        shadow.setNetworkCapabilities(
            network,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, vpn = true),
        )
        shadow.setLinkProperties(network, link(route("127.0.0.0", 8)))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_REMOTE)
    }

    @Test
    fun `a route to the host without a gateway is local, with the packet size of the configuration`() {
        onWifi(route("127.0.0.0", 8))

        assertThat(negotiated())
            .isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL to CONFIGURED_PACKET_SIZE)
    }

    @Test
    fun `a route to the host through a gateway leaves the decision to moonlight-common-c`() {
        onWifi(route("127.0.0.0", 8, gateway = "10.0.0.1"))

        assertThat(negotiated())
            .isEqualTo(StreamConfiguration.STREAM_CFG_AUTO to CONFIGURED_PACKET_SIZE)
    }

    @Test
    fun `a route without a gateway that follows a route with one still makes the host local`() {
        onWifi(route("0.0.0.0", 0, gateway = "10.0.0.1"), route("127.0.0.0", 8))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    @Test
    fun `no route to the host leaves the decision to moonlight-common-c`() {
        onWifi(route("192.168.0.0", 16))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
    }

    @Test
    fun `without a network the decision is left to moonlight-common-c`() {
        shadow.setActiveNetworkInfo(null)

        assertThat(negotiated())
            .isEqualTo(StreamConfiguration.STREAM_CFG_AUTO to CONFIGURED_PACKET_SIZE)
    }

    @Test
    fun `a network without capabilities is told by its routes`() {
        shadow.setNetworkCapabilities(network, null)
        shadow.setLinkProperties(network, link(route("127.0.0.0", 8)))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    @Test
    fun `a network without link properties leaves the decision to moonlight-common-c`() {
        shadow.setNetworkCapabilities(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI))

        assertThat(negotiated().first).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
    }

    private companion object {
        const val INTERFACE = "wlan0"
        const val CONFIGURED_PACKET_SIZE = 1392
        const val REMOTE_PACKET_SIZE = 1024
        const val REMOTE_ARG = 10
        const val PACKET_SIZE_ARG = 9
    }
}
