package com.limelight.nvstream.mdns

import android.content.Context
import android.net.wifi.WifiManager
import com.limelight.LimeLog
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.jmdns.JmmDNS
import javax.jmdns.NetworkTopologyDiscovery
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.impl.NetworkTopologyDiscoveryImpl

/**
 * The agent that discovers hosts with jmDNS, before Android 14.
 *
 * No thread that calls the agent waits for a discovery thread, directly or on a lock that a thread
 * that waits holds, so the main thread can stop discovery (an unbind or a destroy of the discovery
 * service). And no two discovery threads of the agent hold the shared resolver at once: a restart
 * has to close the resolver and open it again, which is what makes it report its cached services
 * again, so the new thread references the resolver only after the old one let go of it.
 *
 * - [startDiscovery] and [stopDiscovery] are synchronized on the agent, so [discoveryThread] is
 *   never overwritten while one runs, and neither of them waits inside.
 * - [stopDiscovery] only interrupts the thread.
 * - The wait is the new thread's own: it takes the permit of [resolverSlot] before it references
 *   the resolver, and the old thread gives the permit back after it dereferenced the resolver. The
 *   wait is bounded ([RESOLVER_RELEASE_TIMEOUT_MS]), so a thread that does not let go does not stop
 *   discovery for good: the new thread goes on without it.
 *
 * The permit orders the threads of one agent, which is what the discovery service makes: the agents
 * of a process share the resolver on purpose, and the service of a process makes one.
 */
class JmDNSDiscoveryAgent(context: Context, listener: MdnsDiscoveryListener) :
    MdnsDiscoveryAgent(listener), ServiceListener {
    private val multicastLock: WifiManager.MulticastLock
    private var discoveryThread: Thread? = null
    private val pendingResolution = HashSet<String>()

    // Held by the discovery thread that holds the resolver, from before it references it to after
    // it let go of it. Fair, so that the threads that wait take it in the order they came.
    private val resolverSlot = Semaphore(1, true)

    class MyNetworkTopologyDiscovery : NetworkTopologyDiscoveryImpl() {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        override fun useInetAddress(
            networkInterface: NetworkInterface?,
            interfaceAddress: InetAddress?,
        ): Boolean {
            // This is an copy of jmDNS's implementation, except we omit the multicast check, since
            // it seems at least some devices lie about interfaces not supporting multicast when
            // they really do.
            //
            // Any failure of the check, including a missing interface, means that the interface is
            // not used, as in the Java.
            return try {
                val candidate = checkNotNull(networkInterface)
                candidate.isUp && !candidate.isLoopback
            } catch (exception: Exception) {
                false
            }
        }
    }

    init {
        // Create the multicast lock required to receive mDNS traffic
        val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiMgr.createMulticastLock("Limelight mDNS")
        multicastLock.setReferenceCounted(false)
    }

    private fun handleResolvedServiceInfo(info: ServiceInfo) {
        synchronized(pendingResolution) { pendingResolution.remove(info.name) }

        handleServiceInfo(info)
    }

    private fun handleServiceInfo(info: ServiceInfo) {
        reportNewComputer(info.name, info.port, info.inet4Addresses, info.inet6Addresses)
    }

    @Synchronized
    override fun startDiscovery(discoveryIntervalMs: Int) {
        // Kill any existing discovery before starting a new one
        stopDiscovery()

        // Acquire the multicast lock to start receiving mDNS traffic
        multicastLock.acquire()

        // Add our listener to the set
        synchronized(listeners) { listeners.add(this) }

        val thread =
            object : Thread() {
                override fun run() {
                    // Behind the thread that was stopped before this one started: it has the
                    // permit until it let go of the resolver. A stop that comes while this waits
                    // ends it before it took a reference.
                    val holdsSlot =
                        try {
                            resolverSlot.tryAcquire(
                                RESOLVER_RELEASE_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS,
                            )
                        } catch (e: InterruptedException) {
                            return
                        }

                    try {
                        discover(discoveryIntervalMs)
                    } finally {
                        // The next thread may reference the resolver now
                        if (holdsSlot) resolverSlot.release()
                    }
                }
            }
        discoveryThread = thread
        thread.name = "mDNS Discovery Thread"
        thread.start()
    }

    // The loop of the discovery thread, which holds a reference to the resolver for as long as it
    // runs. Converted as is from the inherited Java; refactor in a later sprint (Sprint 12
    // backlog).
    @Suppress("NestedBlockDepth")
    private fun discover(discoveryIntervalMs: Int) {
        // This may result in listener callbacks so we must register
        // our listener first.
        val resolver = referenceResolver()

        try {
            while (!Thread.interrupted()) {
                // Start an mDNS request
                resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs.toLong())

                // Run service resolution again for pending machines
                val pendingNames = synchronized(pendingResolution) { ArrayList(pendingResolution) }
                for (name in pendingNames) {
                    LimeLog.info("mDNS: Retrying service resolution for machine: $name")
                    val infos =
                        resolver.getServiceInfos(SERVICE_TYPE, name, SERVICE_INFO_TIMEOUT_MS)
                    if (infos != null && infos.isNotEmpty()) {
                        LimeLog.info("mDNS: Resolved (retry) with ${infos.size} service entries")
                        for (svcinfo in infos) {
                            handleResolvedServiceInfo(svcinfo)
                        }
                    }
                }

                // Wait for the next polling interval
                try {
                    Thread.sleep(discoveryIntervalMs.toLong())
                } catch (e: InterruptedException) {
                    break
                }
            }
        } finally {
            // Dereference the resolver
            dereferenceResolver()
        }
    }

    @Synchronized
    override fun stopDiscovery() {
        // Release the multicast lock to stop receiving mDNS traffic
        multicastLock.release()

        // Remove our listener from the set
        synchronized(listeners) { listeners.remove(this) }

        // If there's already a running thread, interrupt it. The thread drops its reference to the
        // shared resolver as it ends, and a start that follows waits for that in its own thread:
        // this returns at once, because it runs on the main thread when the service is unbound.
        discoveryThread?.interrupt()
        discoveryThread = null
    }

    override fun serviceAdded(event: ServiceEvent) {
        LimeLog.info("mDNS: Machine appeared: " + event.info.name)

        val info = event.dns.getServiceInfo(SERVICE_TYPE, event.info.name, SERVICE_INFO_TIMEOUT_MS)
        if (info == null) {
            // This machine is pending resolution
            synchronized(pendingResolution) { pendingResolution.add(event.info.name) }
            return
        }

        LimeLog.info("mDNS: Resolved (blocking)")
        handleResolvedServiceInfo(info)
    }

    override fun serviceRemoved(event: ServiceEvent) {
        LimeLog.info("mDNS: Machine disappeared: " + event.info.name)
    }

    override fun serviceResolved(event: ServiceEvent) {
        // We handle this synchronously
    }

    private companion object {
        private const val SERVICE_TYPE = "_nvstream._tcp.local."
        private const val SERVICE_INFO_TIMEOUT_MS = 500L

        // The longest that a discovery thread waits for the one before it to let go of the
        // resolver. That one ends as soon as the interrupt cuts its sleep or its resolver call, as
        // jmDNS keeps the interrupt, so this only bounds a thread that something holds up. No
        // caller sees the wait.
        private const val RESOLVER_RELEASE_TIMEOUT_MS = 2_000L

        // The resolver factory's instance member has a static lifetime which
        // means our ref count and listener must be static also.
        var resolverRefCount = 0
        val listeners = HashSet<ServiceListener>()
        val nvstreamListener =
            object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    // Copy the listener set into a new set so we can invoke
                    // the callbacks without holding the listeners monitor the
                    // whole time.
                    val localListeners = synchronized(listeners) { HashSet(listeners) }

                    for (listener in localListeners) {
                        listener.serviceAdded(event)
                    }
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    // Copy the listener set into a new set so we can invoke
                    // the callbacks without holding the listeners monitor the
                    // whole time.
                    val localListeners = synchronized(listeners) { HashSet(listeners) }

                    for (listener in localListeners) {
                        listener.serviceRemoved(event)
                    }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    // Copy the listener set into a new set so we can invoke
                    // the callbacks without holding the listeners monitor the
                    // whole time.
                    val localListeners = synchronized(listeners) { HashSet(listeners) }

                    for (listener in localListeners) {
                        listener.serviceResolved(event)
                    }
                }
            }

        init {
            // Override jmDNS's default topology discovery class with ours
            NetworkTopologyDiscovery.Factory.setClassDelegate {
                MyNetworkTopologyDiscovery()
            }
        }

        fun referenceResolver(): JmmDNS =
            synchronized(JmDNSDiscoveryAgent::class.java) {
                val instance = JmmDNS.Factory.getInstance()
                if (++resolverRefCount == 1) {
                    // This will cause the listener to be invoked for known hosts immediately.
                    // JmDNS only supports one listener per service, so we have to do this here
                    // with a static listener.
                    instance.addServiceListener(SERVICE_TYPE, nvstreamListener)
                }
                instance
            }

        fun dereferenceResolver() {
            synchronized(JmDNSDiscoveryAgent::class.java) {
                if (--resolverRefCount == 0) {
                    try {
                        JmmDNS.Factory.close()
                    } catch (ignored: IOException) {
                        // Nothing to do when the resolver fails to close.
                    }
                }
            }
        }
    }
}
