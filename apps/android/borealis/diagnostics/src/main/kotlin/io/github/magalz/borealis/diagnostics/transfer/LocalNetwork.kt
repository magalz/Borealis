package io.github.magalz.borealis.diagnostics.transfer

import android.net.ConnectivityManager
import java.net.InetAddress

/** The address of the active Wi-Fi or Ethernet network that a phone on the LAN could reach. */
internal fun activeLocalIpv4(connectivity: ConnectivityManager): InetAddress? = TODO()

internal fun privateIpv4(addresses: List<InetAddress>): InetAddress? = TODO()
