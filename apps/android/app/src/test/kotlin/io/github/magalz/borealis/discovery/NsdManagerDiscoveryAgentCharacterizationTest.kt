package io.github.magalz.borealis.discovery

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import io.github.magalz.borealis.testing.LimeLogCapture
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The agent that discovers hosts with the Android `NsdManager` (Android 14 and later): which
 * discovery it asks for, how it follows the start, the failures and the stop of that discovery, and
 * which computers it reports from the services that the platform resolves. [HoldingNsdManager]
 * stands in for the platform and lets the test confirm a start when it chooses.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [HoldingNsdManager::class])
class NsdManagerDiscoveryAgentCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val platform =
        shadowOf(app.getSystemService(NsdManager::class.java)) as HoldingNsdManager
    private val listener = RecordingDiscoveryListener()
    private val agent = NsdManagerDiscoveryAgent(app, listener)

    /** The discoveries that the agent asked the platform for and did not stop. */
    private fun discoveries(): List<NsdManager.DiscoveryListener> =
        platform.getDiscoveryListeners(SERVICE_TYPE).orEmpty()

    /** The service info callbacks that the agent registered for [info] and did not unregister. */
    private fun callbacks(info: NsdServiceInfo): List<NsdManager.ServiceInfoCallback> =
        platform.getServiceInfoCallbacks(info).orEmpty()

    private fun service(name: String) =
        NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
        }

    private fun resolved(name: String, port: Int, vararg addresses: String) =
        service(name).apply {
            this.port = port
            hostAddresses = addresses.map { InetAddress.getByName(it) }
        }

    /** Starts discovery and has the platform confirm it. */
    private fun startAndConfirm(): NsdManager.DiscoveryListener {
        agent.startDiscovery(INTERVAL_MS)
        platform.confirmStarts()
        return discoveries().single()
    }

    @Test
    fun `discovery asks the platform for the nvstream service type over DNS-SD`() {
        agent.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
        assertThat(platform.getDiscoveryListenerServiceType(discoveries().single()))
            .isEqualTo("_nvstream._tcp")
        assertThat(platform.lastProtocol).isEqualTo(NsdManager.PROTOCOL_DNS_SD)
    }

    @Test
    fun `discovery that is starting is not asked for a second time`() {
        agent.startDiscovery(INTERVAL_MS)
        agent.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `discovery that is running is not asked for a second time`() {
        startAndConfirm()

        agent.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `a confirmed start is logged`() {
        startAndConfirm()

        assertThat(log.messages(Level.INFO)).contains("NSD: Service discovery started")
    }

    @Test
    fun `a failed start tells the listener, logs the error code and allows a new start`() {
        agent.startDiscovery(INTERVAL_MS)

        discoveries().single().onStartDiscoveryFailed(SERVICE_TYPE, 3)

        assertThat(listener.failures.map { it.message })
            .containsExactly("onStartDiscoveryFailed(): 3")
        assertThat(listener.failures.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(log.messages(Level.SEVERE))
            .containsExactly("NSD: Service discovery start failed: 3")

        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a failed start of a discovery that is no longer wanted is only logged`() {
        agent.startDiscovery(INTERVAL_MS)
        val stale = discoveries().single()
        agent.stopDiscovery()

        stale.onStartDiscoveryFailed(SERVICE_TYPE, 3)

        assertThat(listener.failures).isEmpty()
        assertThat(log.messages(Level.SEVERE))
            .containsExactly("NSD: Service discovery start failed: 3")
    }

    @Test
    fun `a start that the platform confirms after the stop is stopped at once`() {
        agent.startDiscovery(INTERVAL_MS)
        val stale = discoveries().single()
        agent.stopDiscovery()

        platform.confirmStarts()

        assertThat(discoveries()).isEmpty()
        val found = service("Den PC")
        stale.onServiceFound(found)
        assertThat(callbacks(found)).isEmpty()

        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `a service found before the start is confirmed is ignored`() {
        agent.startDiscovery(INTERVAL_MS)
        val found = service("Den PC")

        discoveries().single().onServiceFound(found)

        assertThat(callbacks(found)).isEmpty()
        assertThat(log.messages(Level.INFO)).doesNotContain("NSD: Machine appeared: Den PC")
    }

    @Test
    fun `a service found while running is watched until it is resolved`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")

        discovery.onServiceFound(found)

        assertThat(callbacks(found)).hasSize(1)
        assertThat(log.messages(Level.INFO)).contains("NSD: Machine appeared: Den PC")
        assertThat(listener.added).isEmpty()
    }

    @Test
    fun `a resolved service is reported once for each IPv4 address with the global IPv6 address`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)

        callbacks(found)
            .single()
            .onServiceUpdated(
                resolved("Den PC", 47989, "fe80::1", "192.168.1.5", "2001:db8::1", "10.0.0.2")
            )

        assertThat(listener.added)
            .containsExactly(
                MdnsComputer("Den PC", ip4("192.168.1.5"), ip6("2001:db8::1"), 47989),
                MdnsComputer("Den PC", ip4("10.0.0.2"), ip6("2001:db8::1"), 47989),
            )
            .inOrder()
        assertThat(log.messages(Level.INFO)).contains("NSD: Machine resolved: Den PC")
    }

    @Test
    fun `a resolved service with only IPv6 addresses is reported through them`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)

        callbacks(found)
            .single()
            .onServiceUpdated(resolved("Den PC", 47989, "fe80::1", "2001:db8::1"))

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip6("fe80::1"), ip6("2001:db8::1"), 47989))
    }

    @Test
    fun `a service that is resolved again is not reported again`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)
        val callback = callbacks(found).single()

        callback.onServiceUpdated(resolved("Den PC", 47989, "192.168.1.5"))
        callback.onServiceUpdated(resolved("Den PC", 47989, "192.168.1.5"))

        assertThat(listener.added).hasSize(1)
        assertThat(agent.getComputerSet()).hasSize(1)
    }

    @Test
    fun `a failed registration of the service callback tells the listener and logs the code`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)

        callbacks(found).single().onServiceInfoCallbackRegistrationFailed(5)

        assertThat(listener.failures.map { it.message })
            .containsExactly("onServiceInfoCallbackRegistrationFailed(): 5")
        assertThat(listener.failures.single()).isInstanceOf(RuntimeException::class.java)
        assertThat(log.messages(Level.SEVERE))
            .containsExactly("NSD: Service info callback registration failed: 5")
    }

    @Test
    fun `a service callback that is lost or unregistered reports nothing`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)

        callbacks(found).single().onServiceLost()
        callbacks(found).single().onServiceInfoCallbackUnregistered()

        assertThat(listener.added).isEmpty()
        assertThat(listener.failures).isEmpty()
    }

    @Test
    fun `a lost service has its callback unregistered and is logged`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)

        discovery.onServiceLost(found)

        assertThat(callbacks(found)).isEmpty()
        assertThat(log.messages(Level.INFO)).contains("NSD: Machine lost: Den PC")
    }

    @Test
    fun `a lost service that was never found is only logged`() {
        val discovery = startAndConfirm()

        discovery.onServiceLost(service("Den PC"))

        assertThat(log.messages(Level.INFO)).contains("NSD: Machine lost: Den PC")
    }

    @Test
    fun `a service that is lost after the stop is ignored`() {
        val discovery = startAndConfirm()

        agent.stopDiscovery()
        discovery.onServiceLost(service("Den PC"))

        assertThat(log.messages(Level.INFO)).doesNotContain("NSD: Machine lost: Den PC")
    }

    @Test
    fun `stopping discovery stops the running discovery and unregisters the service callbacks`() {
        val discovery = startAndConfirm()
        val denPc = service("Den PC")
        val laptop = service("Laptop")
        discovery.onServiceFound(denPc)
        discovery.onServiceFound(laptop)

        agent.stopDiscovery()

        assertThat(discoveries()).isEmpty()
        assertThat(callbacks(denPc)).isEmpty()
        assertThat(callbacks(laptop)).isEmpty()
        assertThat(log.messages(Level.INFO)).contains("NSD: Service discovery stopped")
    }

    @Test
    fun `a service found after the stop is ignored`() {
        val discovery = startAndConfirm()
        agent.stopDiscovery()
        val found = service("Den PC")

        discovery.onServiceFound(found)

        assertThat(callbacks(found)).isEmpty()
    }

    @Test
    fun `discovery can start again as soon as it was stopped`() {
        startAndConfirm()
        agent.stopDiscovery()

        agent.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `stopping discovery that never started does nothing`() {
        agent.stopDiscovery()

        assertThat(discoveries()).isEmpty()
        assertThat(listener.failures).isEmpty()
    }

    @Test
    fun `a failed stop is logged and lets discovery start again`() {
        val discovery = startAndConfirm()

        discovery.onStopDiscoveryFailed(SERVICE_TYPE, 4)

        assertThat(log.messages(Level.SEVERE))
            .containsExactly("NSD: Service discovery stop failed: 4")
        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a failed stop of a discovery that is no longer the running one changes nothing`() {
        val first = startAndConfirm()
        agent.stopDiscovery()
        startAndConfirm()

        first.onStopDiscoveryFailed(SERVICE_TYPE, 4)
        agent.startDiscovery(INTERVAL_MS)

        // The second discovery is still the running one, so no third was asked for.
        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `a discovery that the platform stopped lets discovery start again`() {
        val discovery = startAndConfirm()

        discovery.onDiscoveryStopped(SERVICE_TYPE)

        assertThat(log.messages(Level.INFO)).contains("NSD: Service discovery stopped")
        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a lost service is forgotten, so stopping later does not unregister it again`() {
        // The platform refuses a callback that is not registered, so a second unregistration
        // throws.
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)
        discovery.onServiceLost(found)

        agent.stopDiscovery()

        assertThat(discoveries()).isEmpty()
    }

    @Test
    fun `the callbacks of a stopped discovery are forgotten, so a later stop does not unregister them again`() {
        val first = startAndConfirm()
        first.onServiceFound(service("Den PC"))
        agent.stopDiscovery()
        startAndConfirm()

        agent.stopDiscovery()

        assertThat(discoveries()).isEmpty()
    }

    @Test
    fun `discovery can start again at once, before the platform confirms the stop`() {
        startAndConfirm()
        platform.holdStops = true
        agent.stopDiscovery()

        agent.startDiscovery(INTERVAL_MS)
        platform.confirmStarts()
        assertThat(discoveries()).hasSize(1)

        // The late confirmation of the first stop does not touch the discovery that runs now.
        platform.confirmStops()
        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `every service callback runs on the one executor of the agent, which has a single thread`() {
        val discovery = startAndConfirm()
        discovery.onServiceFound(service("Den PC"))
        discovery.onServiceFound(service("Laptop"))

        val executors = platform.serviceInfoExecutors
        assertThat(executors).hasSize(2)
        assertThat(executors[0]).isSameInstanceAs(executors[1])
        val pool = executors[0] as ThreadPoolExecutor
        assertThat(pool.corePoolSize).isEqualTo(1)
        assertThat(pool.maximumPoolSize).isEqualTo(1)
        assertThat(pool.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(0)
        assertThat(pool.queue).isInstanceOf(LinkedBlockingQueue::class.java)
        val threads = (1..2).map { pool.submit<Thread> { Thread.currentThread() }.get() }
        assertThat(threads[0]).isSameInstanceAs(threads[1])
        pool.shutdown()
    }

    @Test
    fun `a service found again leaves its first callback registered after the stop`() {
        // Inherited quirk: the callbacks are kept by name, so the second one replaces the first in
        // the map and only the second is unregistered. A fix belongs to the backlog.
        val discovery = startAndConfirm()
        val found = service("Den PC")
        discovery.onServiceFound(found)
        discovery.onServiceFound(found)
        assertThat(callbacks(found)).hasSize(2)

        agent.stopDiscovery()

        assertThat(callbacks(found)).hasSize(1)
    }

    private companion object {
        const val SERVICE_TYPE = "_nvstream._tcp"
        const val INTERVAL_MS = 1_000
    }
}
