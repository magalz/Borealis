package com.limelight.nvstream.mdns

import android.annotation.TargetApi
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.limelight.LimeLog
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class NsdManagerDiscoveryAgent(context: Context, listener: MdnsDiscoveryListener) :
    MdnsDiscoveryAgent(listener) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val listenerLock = Any()
    private var pendingListener: NsdManager.DiscoveryListener? = null
    private var activeListener: NsdManager.DiscoveryListener? = null
    private val serviceCallbacks = HashMap<String?, NsdManager.ServiceInfoCallback>()
    private val executor =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongMethod")
    private fun createDiscoveryListener(): NsdManager.DiscoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                LimeLog.severe("NSD: Service discovery start failed: $errorCode")

                // This listener is no longer pending after this failure
                synchronized(listenerLock) {
                    if (pendingListener !== this) {
                        return
                    }

                    pendingListener = null
                }

                listener.notifyDiscoveryFailure(
                    RuntimeException("onStartDiscoveryFailed(): $errorCode")
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                LimeLog.severe("NSD: Service discovery stop failed: $errorCode")

                // This listener is no longer active after this failure
                synchronized(listenerLock) {
                    if (activeListener !== this) {
                        return
                    }

                    activeListener = null
                }
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                LimeLog.info("NSD: Service discovery started")

                synchronized(listenerLock) {
                    if (pendingListener !== this) {
                        // If we registered another discovery listener in the meantime, stop this
                        // one
                        nsdManager.stopServiceDiscovery(this)
                        return
                    }

                    pendingListener = null
                    activeListener = this
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                LimeLog.info("NSD: Service discovery stopped")

                synchronized(listenerLock) {
                    if (activeListener !== this) {
                        return
                    }

                    activeListener = null
                }
            }

            override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
                // Protect against racing stopDiscovery() call
                synchronized(listenerLock) {
                    // Ignore callbacks if we're not the active listener
                    if (activeListener !== this) {
                        return
                    }

                    LimeLog.info("NSD: Machine appeared: " + nsdServiceInfo.serviceName)

                    val serviceInfoCallback =
                        object : NsdManager.ServiceInfoCallback {
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                LimeLog.severe(
                                    "NSD: Service info callback registration failed: $errorCode"
                                )
                                listener.notifyDiscoveryFailure(
                                    RuntimeException(
                                        "onServiceInfoCallbackRegistrationFailed(): $errorCode"
                                    )
                                )
                            }

                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                LimeLog.info("NSD: Machine resolved: " + serviceInfo.serviceName)
                                reportNewComputer(
                                    serviceInfo.serviceName,
                                    serviceInfo.port,
                                    serviceInfo.hostAddresses
                                        .filterIsInstance<Inet4Address>()
                                        .toTypedArray(),
                                    serviceInfo.hostAddresses
                                        .filterIsInstance<Inet6Address>()
                                        .toTypedArray(),
                                )
                            }

                            override fun onServiceLost() = Unit

                            override fun onServiceInfoCallbackUnregistered() = Unit
                        }

                    nsdManager.registerServiceInfoCallback(
                        nsdServiceInfo,
                        executor,
                        serviceInfoCallback,
                    )
                    serviceCallbacks[nsdServiceInfo.serviceName] = serviceInfoCallback
                }
            }

            override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
                // Protect against racing stopDiscovery() call
                synchronized(listenerLock) {
                    // Ignore callbacks if we're not the active listener
                    if (activeListener !== this) {
                        return
                    }

                    LimeLog.info("NSD: Machine lost: " + nsdServiceInfo.serviceName)

                    val serviceInfoCallback = serviceCallbacks.remove(nsdServiceInfo.serviceName)
                    if (serviceInfoCallback != null) {
                        nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
                    }
                }
            }
        }

    override fun startDiscovery(discoveryIntervalMs: Int) {
        synchronized(listenerLock) {
            // Register a new service discovery listener if there's not already one starting or
            // running
            if (pendingListener == null && activeListener == null) {
                val discoveryListener = createDiscoveryListener()
                pendingListener = discoveryListener
                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            }
        }
    }

    override fun stopDiscovery() {
        // Protect against racing ServiceInfoCallback and DiscoveryListener callbacks
        synchronized(listenerLock) {
            // Clear any pending listener to ensure the discoverStarted() callback
            // will realize it's gone and stop itself.
            pendingListener = null

            // Unregister the service discovery listener
            val running = activeListener
            if (running != null) {
                nsdManager.stopServiceDiscovery(running)

                // Even though listener stoppage is asynchronous, the listener is gone as far as
                // we're concerned. We null this right now to ensure pending callbacks know it's
                // stopped and startDiscovery() can immediately create a new listener. If we left
                // it until onDiscoveryStopped() was called, startDiscovery() would get confused
                // and assume a listener was already running, even though it's stopping.
                activeListener = null
            }

            // Unregister all service info callbacks
            for (callback in serviceCallbacks.values) {
                nsdManager.unregisterServiceInfoCallback(callback)
            }
            serviceCallbacks.clear()
        }
    }

    private companion object {
        private const val SERVICE_TYPE = "_nvstream._tcp"
    }
}
