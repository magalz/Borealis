package io.github.magalz.borealis.discovery

import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNsdManager

/**
 * The stock Robolectric shadow of `NsdManager` confirms a discovery at once, inside the call that
 * asks for it, and confirms a stop inside the call that asks for that. This one keeps the start
 * confirmation until the test calls [confirmStarts], and can keep the stop confirmation until
 * [confirmStops] (when [holdStops] is set), so that a test can see an agent whose request the
 * platform has not answered yet, as on a device where both are asynchronous. It also remembers the
 * protocol of the discovery and the executor of each service info callback.
 *
 * The listeners that `getDiscoveryListeners` returns wrap the ones of the agent and pass every call
 * on to them. Use it with `@Config(shadows = [HoldingNsdManager::class])`.
 */
@Implements(NsdManager::class)
class HoldingNsdManager : ShadowNsdManager() {
    private val heldStarts = ArrayDeque<Runnable>()
    private val heldStops = ArrayDeque<Runnable>()
    private val wrappers =
        IdentityHashMap<NsdManager.DiscoveryListener, NsdManager.DiscoveryListener>()

    /** Whether the confirmation of a stop waits for [confirmStops]. */
    var holdStops = false

    /** The protocol of the last discovery that was asked for. */
    var lastProtocol: Int? = null
        private set

    /** The executor of each service info callback that was registered, in order. */
    val serviceInfoExecutors = mutableListOf<Executor>()

    @Implementation
    override fun discoverServices(
        serviceType: String,
        protocolType: Int,
        listener: NsdManager.DiscoveryListener,
    ) {
        lastProtocol = protocolType
        val wrapper = StopHoldingListener(listener)
        wrappers[listener] = wrapper
        discoverServices(
            serviceType,
            protocolType,
            null as Network?,
            Executor { heldStarts.add(it) },
            wrapper,
        )
    }

    @Implementation
    override fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {
        super.stopServiceDiscovery(wrappers[listener] ?: listener)
    }

    @Implementation
    override fun registerServiceInfoCallback(
        serviceInfo: NsdServiceInfo,
        executor: Executor,
        callback: NsdManager.ServiceInfoCallback,
    ) {
        serviceInfoExecutors += executor
        super.registerServiceInfoCallback(serviceInfo, executor, callback)
    }

    /** Delivers `onDiscoveryStarted` for every discovery that is waiting for it. */
    fun confirmStarts() {
        while (heldStarts.isNotEmpty()) {
            heldStarts.removeFirst().run()
        }
    }

    /** Delivers `onDiscoveryStopped` for every stop that is waiting for it. */
    fun confirmStops() {
        while (heldStops.isNotEmpty()) {
            heldStops.removeFirst().run()
        }
    }

    private inner class StopHoldingListener(private val agent: NsdManager.DiscoveryListener) :
        NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) =
            agent.onStartDiscoveryFailed(serviceType, errorCode)

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) =
            agent.onStopDiscoveryFailed(serviceType, errorCode)

        override fun onDiscoveryStarted(serviceType: String?) = agent.onDiscoveryStarted(serviceType)

        override fun onDiscoveryStopped(serviceType: String?) {
            if (holdStops) {
                heldStops.add { agent.onDiscoveryStopped(serviceType) }
            } else {
                agent.onDiscoveryStopped(serviceType)
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) = agent.onServiceFound(serviceInfo)

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = agent.onServiceLost(serviceInfo)
    }
}
