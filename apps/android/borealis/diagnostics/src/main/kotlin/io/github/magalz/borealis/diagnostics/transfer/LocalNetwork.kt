package io.github.magalz.borealis.diagnostics.transfer

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The private IPv4 address of the active Wi-Fi or Ethernet network, the one a phone on the same
 * network can reach; null when there is no such network (mobile data, no connection, public address
 * only).
 */
// The app module declares ACCESS_NETWORK_STATE; this library has no other use for it.
@SuppressLint("MissingPermission")
internal fun activeLocalIpv4(connectivity: ConnectivityManager): InetAddress? {
    val network = connectivity.activeNetwork ?: return null
    val local =
        connectivity.getNetworkCapabilities(network)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
    if (!local) return null
    return connectivity.getLinkProperties(network)?.let { properties ->
        privateIpv4(properties.linkAddresses.map { it.address })
    }
}

/** The first site-local (10/8, 172.16/12, 192.168/16) IPv4 address. */
internal fun privateIpv4(addresses: List<InetAddress>): InetAddress? = addresses.firstOrNull {
    it is Inet4Address && it.isSiteLocalAddress
}
