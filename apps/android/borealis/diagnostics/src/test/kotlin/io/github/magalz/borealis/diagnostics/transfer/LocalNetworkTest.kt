package io.github.magalz.borealis.diagnostics.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class LocalNetworkTest {
    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun `the first site-local IPv4 address is chosen`() {
        val picked =
            privateIpv4(
                listOf(ip("fe80::1"), ip("203.0.113.9"), ip("192.168.1.23"), ip("10.0.0.4"))
            )

        assertThat(picked!!.hostAddress).isEqualTo("192.168.1.23")
    }

    @Test
    fun `the three private ranges count and nothing else does`() {
        assertThat(privateIpv4(listOf(ip("10.1.2.3")))).isNotNull()
        assertThat(privateIpv4(listOf(ip("172.16.5.5")))).isNotNull()
        assertThat(privateIpv4(listOf(ip("192.168.0.9")))).isNotNull()
        assertThat(privateIpv4(listOf(ip("127.0.0.1")))).isNull()
        assertThat(privateIpv4(listOf(ip("169.254.10.10")))).isNull()
        assertThat(privateIpv4(listOf(ip("8.8.8.8")))).isNull()
        assertThat(privateIpv4(listOf(ip("fd00::5")))).isNull()
        assertThat(privateIpv4(emptyList())).isNull()
    }

    private fun connectivity(
        transport: Int?,
        vararg addresses: String,
        active: Boolean = true,
    ): ConnectivityManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(manager)
        if (active) {
            val network = manager.activeNetwork!!
            shadow.setLinkProperties(
                network,
                LinkProperties().apply {
                    addresses.forEach { addAddress(this, ip(it)) }
                },
            )
            shadow.setNetworkCapabilities(
                network,
                ShadowNetworkCapabilities.newInstance().also {
                    if (transport != null) shadowOf(it).addTransportType(transport)
                },
            )
        } else {
            shadow.setDefaultNetworkActive(false)
            shadow.setActiveNetworkInfo(null)
        }
        return manager
    }

    @Test
    fun `a Wi-Fi network gives its private address`() {
        val manager = connectivity(NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23")

        assertThat(activeLocalIpv4(manager)!!.hostAddress).isEqualTo("192.168.1.23")
    }

    @Test
    fun `an Ethernet network gives its private address`() {
        val manager = connectivity(NetworkCapabilities.TRANSPORT_ETHERNET, "10.0.0.8")

        assertThat(activeLocalIpv4(manager)!!.hostAddress).isEqualTo("10.0.0.8")
    }

    @Test
    fun `a cellular network is not a local network`() {
        val manager = connectivity(NetworkCapabilities.TRANSPORT_CELLULAR, "10.20.30.40")

        assertThat(activeLocalIpv4(manager)).isNull()
    }

    @Test
    fun `a Wi-Fi network with a public address only gives nothing`() {
        val manager = connectivity(NetworkCapabilities.TRANSPORT_WIFI, "203.0.113.9")

        assertThat(activeLocalIpv4(manager)).isNull()
    }

    @Test
    fun `an active network with unknown capabilities gives nothing`() {
        val manager = connectivity(NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23")
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork!!, null)

        assertThat(activeLocalIpv4(manager)).isNull()
    }

    @Test
    fun `no active network gives nothing`() {
        val manager = connectivity(null, active = false)

        assertThat(activeLocalIpv4(manager)).isNull()
    }
}

/** `addLinkAddress` and the public `LinkAddress` constructor are system API: reach them by name. */
private fun addAddress(properties: LinkProperties, address: InetAddress) {
    val link =
        LinkAddress::class
            .java
            .getConstructor(InetAddress::class.java, Int::class.java)
            .newInstance(address, 24)
    LinkProperties::class
        .java
        .getMethod("addLinkAddress", LinkAddress::class.java)
        .invoke(properties, link)
}
