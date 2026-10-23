package com.limelight.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetHelper {
    @JvmStatic
    fun isActiveNetworkVpn(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netCaps = connMgr.activeNetwork?.let { connMgr.getNetworkCapabilities(it) }
        return netCaps != null &&
            (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
    }
}
