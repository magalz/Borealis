package com.limelight.discovery

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent

class DiscoveryService : Service() {
    private lateinit var discoveryAgent: MdnsDiscoveryAgent
    private var boundListener: MdnsDiscoveryListener? = null

    inner class DiscoveryBinder : Binder() {
        fun setListener(listener: MdnsDiscoveryListener?) {
            boundListener = listener
        }

        fun startDiscovery(queryIntervalMs: Int) {
            discoveryAgent.startDiscovery(queryIntervalMs)
        }

        fun stopDiscovery() {
            discoveryAgent.stopDiscovery()
        }

        fun getComputerSet(): List<MdnsComputer> = discoveryAgent.getComputerSet()

        // Drops the computers of a host that was removed, so that discovery announces them again.
        // The agent knows a computer by its service name, which is not the name that the host
        // reports, so a computer belongs to the host when one of its addresses is one of the
        // host's.
        fun forget(host: ComputerDetails) {
            val addresses =
                listOfNotNull(
                        host.localAddress,
                        host.ipv6Address,
                        host.manualAddress,
                        host.remoteAddress,
                    )
                    .map { it.address }
            for (computer in discoveryAgent.getComputerSet()) {
                val discovered = listOfNotNull(computer.localAddress, computer.ipv6Address)
                if (discovered.any { it.hostAddress in addresses }) {
                    discoveryAgent.forget(computer)
                }
            }
        }
    }

    override fun onCreate() {
        val listener =
            object : MdnsDiscoveryListener {
                override fun notifyComputerAdded(computer: MdnsComputer) {
                    boundListener?.notifyComputerAdded(computer)
                }

                override fun notifyDiscoveryFailure(e: Exception) {
                    boundListener?.notifyDiscoveryFailure(e)
                }
            }

        // Prior to Android 14, NsdManager doesn't provide all the capabilities needed for parity
        // with jmDNS (specifically handling multiple addresses for a single service). There are
        // also documented reliability bugs early in the Android 4.x series shortly after it was
        // introduced. The benefit of using NsdManager over jmDNS is that it works correctly in
        // environments where mDNS proxying is required, like ChromeOS, WSA, and the emulator.
        //
        // As such, we use the jmDNS-based MdnsDiscoveryAgent prior to Android 14 and NsdManager
        // on Android 14 and above.
        discoveryAgent =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                JmDNSDiscoveryAgent(applicationContext, listener)
            } else {
                NsdManagerDiscoveryAgent(applicationContext, listener)
            }
    }

    private val binder = DiscoveryBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        // Stop any discovery session
        discoveryAgent.stopDiscovery()

        // Unbind the listener
        boundListener = null
        return false
    }

    override fun onDestroy() {
        // The agent ends for good, whatever it held (a NsdManager agent has an executor)
        discoveryAgent.close()
        super.onDestroy()
    }
}
