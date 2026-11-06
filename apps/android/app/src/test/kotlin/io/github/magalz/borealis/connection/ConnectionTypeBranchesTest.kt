package io.github.magalz.borealis.connection

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.crypto.providerOf
import io.github.magalz.borealis.testing.ANDROID_12
import io.github.magalz.borealis.testing.ANDROID_9
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
 * The branches of the connection type detection that `ConnectionTypeTest` leaves out: the gates of
 * the API level (a route that is not unicast, and the gateway of a route before Android 10), a host
 * that does not resolve, and the NAT64 prefix. A start cannot reach the last two: the host answers
 * on an IPv4 address, and a name that does not resolve fails the `/serverinfo` before the
 * detection, so those two call the detection of a connection directly.
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
class ConnectionTypeBranchesTest {
    @get:Rule val rig = ConnectionRig()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val shadow = shadowOf(connectivity)

    /** The default network of Robolectric: cellular, which is no VPN. */
    private val network: Network = checkNotNull(connectivity.activeNetwork)

    private fun link(vararg routes: RouteInfo, nat64Prefix: Any? = null): LinkProperties =
        LinkProperties().also { properties ->
            // A route is added to the interface of the link, which is hidden API.
            ReflectionHelpers.callInstanceMethod<Any>(
                properties,
                "setInterfaceName",
                ClassParameter.from(String::class.java, INTERFACE),
            )
            nat64Prefix?.let {
                ReflectionHelpers.callInstanceMethod<Any>(
                    properties,
                    "setNat64Prefix",
                    ClassParameter.from(ipPrefixClass, it),
                )
            }
            routes.forEach { properties.addRoute(it) }
        }

    private val ipPrefixClass = Class.forName("android.net.IpPrefix")

    private fun prefix(address: String, length: Int): Any =
        ReflectionHelpers.callConstructor(
            ipPrefixClass,
            ClassParameter.from(InetAddress::class.java, InetAddress.getByName(address)),
            ClassParameter.from(Int::class.javaPrimitiveType, length),
        )

    /** A route to [address]/[length] through [gateway], or on-link without one. */
    private fun route(
        address: String,
        length: Int,
        gateway: String? = null,
        type: Int = RouteInfo.RTN_UNICAST,
    ): RouteInfo =
        ReflectionHelpers.callConstructor(
            RouteInfo::class.java,
            ClassParameter.from(ipPrefixClass, prefix(address, length)),
            ClassParameter.from(InetAddress::class.java, gateway?.let(InetAddress::getByName)),
            ClassParameter.from(String::class.java, INTERFACE),
            ClassParameter.from(Int::class.javaPrimitiveType, type),
        )

    private fun onWifi(link: LinkProperties) {
        shadow.setNetworkCapabilities(
            network,
            ShadowNetworkCapabilities.newInstance().also {
                shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            },
        )
        shadow.setLinkProperties(network, link)
    }

    /** The connection type that reached the native connection of a start that asks to detect it. */
    private fun negotiatedType(): Int {
        val config = rig.config { setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO) }
        rig.start(rig.connection(config))
        val args = ShadowRecordingBridge.await("startConnection").args
        rig.listener.awaitEnded()
        return args[REMOTE_ARG] as Int
    }

    /** The detection of a connection to [host], called as the start calls it. */
    private fun detectedFor(host: String): Int {
        val config = rig.config { setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO) }
        val connection =
            NvConnection(
                application,
                ComputerDetails.AddressTuple(host, PORT),
                HTTPS_PORT,
                "id",
                config,
                providerOf(TestIdentities.clientRsa),
                TestIdentities.hostRsa.certificate,
            )
        return ReflectionHelpers.callInstanceMethod(connection, "detectServerConnectionType")
    }

    @Test
    fun `a route to the host that is not a unicast route is skipped from Android 13`() {
        onWifi(link(route("127.0.0.0", 8, type = RouteInfo.RTN_UNREACHABLE)))

        assertThat(negotiatedType()).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `a route to the host that is not a unicast route still counts before Android 13`() {
        onWifi(link(route("127.0.0.0", 8, type = RouteInfo.RTN_UNREACHABLE)))

        assertThat(negotiatedType()).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    // The framework of Android 9 gives a route without a gateway the unspecified address, so a
    // test passes that: the constructor would look for a field that the JVM does not have.
    @Test
    @Config(sdk = [ANDROID_9])
    fun `before Android 10 a route to the host through the unspecified address makes it local`() {
        onWifi(link(route("127.0.0.0", 8, gateway = "0.0.0.0")))

        assertThat(negotiatedType()).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    @Test
    @Config(sdk = [ANDROID_9])
    fun `before Android 10 a route to the host through a gateway leaves the decision`() {
        onWifi(link(route("127.0.0.0", 8, gateway = "10.0.0.1")))

        assertThat(negotiatedType()).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
    }

    @Test
    fun `a host that does not resolve leaves the decision to moonlight-common-c`() {
        onWifi(link(route("::", 0)))

        assertThat(detectedFor(UNRESOLVABLE_HOST)).isEqualTo(StreamConfiguration.STREAM_CFG_AUTO)
    }

    @Test
    fun `a host that resolves to an IPv6 address is told by its routes`() {
        // The route of the test above, which only a host that resolves can match.
        onWifi(link(route("::", 0)))

        assertThat(detectedFor(IPV6_HOST)).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    @Test
    fun `a host in the NAT64 prefix is remote, whatever its routes say`() {
        onWifi(link(route(NAT64_NETWORK, 96), nat64Prefix = prefix(NAT64_NETWORK, 96)))

        assertThat(detectedFor(NAT64_HOST)).isEqualTo(StreamConfiguration.STREAM_CFG_REMOTE)
    }

    @Test
    fun `a host outside the NAT64 prefix is told by its routes`() {
        onWifi(link(route(NAT64_NETWORK, 96), nat64Prefix = prefix("64:ff9c::", 96)))

        assertThat(detectedFor(NAT64_HOST)).isEqualTo(StreamConfiguration.STREAM_CFG_LOCAL)
    }

    private companion object {
        const val INTERFACE = "wlan0"
        const val PORT = 47989
        const val HTTPS_PORT = 47984
        const val REMOTE_ARG = 10

        // A host with an unclosed bracket is no IP address and no name: no lookup is made for it.
        const val UNRESOLVABLE_HOST = "[::1"
        const val IPV6_HOST = "::1"
        const val NAT64_NETWORK = "64:ff9b::"
        const val NAT64_HOST = "64:ff9b::7f00:1"
    }
}
