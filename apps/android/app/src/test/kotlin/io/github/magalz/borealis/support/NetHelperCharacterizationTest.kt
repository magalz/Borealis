package io.github.magalz.borealis.support

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.utils.NetHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Whether the network that the device uses is a VPN: the computer manager checks it before polling.
 */
@RunWith(RobolectricTestRunner::class)
class NetHelperCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shadow = shadowOf(context.getSystemService(ConnectivityManager::class.java))

    /**
     * Makes a network the active one. Its capabilities are exactly [capabilities] and its
     * transports [transports]; null leaves the platform without any capabilities for it.
     */
    private fun activeNetwork(
        transports: List<Int>? = null,
        capabilities: List<Int> = emptyList(),
    ) {
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            )
        // The shadow finds the active network by the type of the info.
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        shadow.addNetwork(network, info)
        shadow.setActiveNetworkInfo(info)
        shadow.setDefaultNetworkActive(true)
        if (transports != null) {
            val netCaps = ShadowNetworkCapabilities.newInstance()
            shadowOf(netCaps).clearCapabilities()
            for (transport in transports) shadowOf(netCaps).addTransportType(transport)
            for (capability in capabilities) shadowOf(netCaps).addCapability(capability)
            shadow.setNetworkCapabilities(network, netCaps)
        }
    }

    @Test
    fun `no active network is not a VPN`() {
        shadow.setActiveNetworkInfo(null)

        assertThat(NetHelper.isActiveNetworkVpn(context)).isFalse()
    }

    @Test
    fun `an active network without known capabilities is not a VPN`() {
        activeNetwork(transports = null)

        assertThat(NetHelper.isActiveNetworkVpn(context)).isFalse()
    }

    @Test
    fun `a network with the VPN transport is a VPN`() {
        activeNetwork(
            transports = listOf(NetworkCapabilities.TRANSPORT_VPN),
            capabilities = listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        )

        assertThat(NetHelper.isActiveNetworkVpn(context)).isTrue()
    }

    @Test
    fun `a network without the not-VPN capability is a VPN, whatever its transport`() {
        activeNetwork(transports = listOf(NetworkCapabilities.TRANSPORT_WIFI))

        assertThat(NetHelper.isActiveNetworkVpn(context)).isTrue()
    }

    @Test
    fun `a network with no transport and no capability is a VPN`() {
        activeNetwork(transports = emptyList())

        assertThat(NetHelper.isActiveNetworkVpn(context)).isTrue()
    }

    @Test
    fun `a Wi-Fi network with the not-VPN capability is not a VPN`() {
        activeNetwork(
            transports = listOf(NetworkCapabilities.TRANSPORT_WIFI),
            capabilities = listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        )

        assertThat(NetHelper.isActiveNetworkVpn(context)).isFalse()
    }

    @Test
    fun `a cellular network with the not-VPN capability is not a VPN`() {
        activeNetwork(
            transports = listOf(NetworkCapabilities.TRANSPORT_CELLULAR),
            capabilities = listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        )

        assertThat(NetHelper.isActiveNetworkVpn(context)).isFalse()
    }

    @Test
    fun `a network that is on a VPN transport and over Wi-Fi is a VPN`() {
        activeNetwork(
            transports =
                listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_VPN),
            capabilities = listOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
        )

        assertThat(NetHelper.isActiveNetworkVpn(context)).isTrue()
    }
}
