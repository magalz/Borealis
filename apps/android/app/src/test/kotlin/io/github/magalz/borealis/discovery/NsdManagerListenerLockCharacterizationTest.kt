package io.github.magalz.borealis.discovery

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import io.github.magalz.borealis.testing.LimeLogCapture
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which callbacks of the discovery listener of `NsdManagerDiscoveryAgent` run under its
 * `listenerLock`, and which tell the agent's listener outside it (Sprint 18a, BORE-79). The lock is
 * private, so the test reads it by reflection. A callback runs on another thread while this one
 * holds the lock: one that takes the lock blocks until this thread lets it go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [HoldingNsdManager::class])
class NsdManagerListenerLockCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val nsdManager = app.getSystemService(NsdManager::class.java)
    private val platform = shadowOf(nsdManager) as HoldingNsdManager
    private val listener = LockProbingListener()
    private val agent = NsdManagerDiscoveryAgent(app, listener)
    private val lock: Any =
        NsdManagerDiscoveryAgent::class
            .java
            .getDeclaredField("listenerLock")
            .apply { isAccessible = true }
            .get(agent)!!

    /** Remembers, for each thing it is told, whether the telling thread held the lock. */
    private class LockProbingListener : MdnsDiscoveryListener {
        lateinit var lock: Any
        val failureHeldLock = CopyOnWriteArrayList<Boolean>()
        val addedHeldLock = CopyOnWriteArrayList<Boolean>()

        override fun notifyComputerAdded(computer: MdnsComputer) {
            addedHeldLock += Thread.holdsLock(lock)
        }

        override fun notifyDiscoveryFailure(e: Exception) {
            failureHeldLock += Thread.holdsLock(lock)
        }
    }

    init {
        listener.lock = lock
    }

    private fun discoveries(): List<NsdManager.DiscoveryListener> =
        platform.getDiscoveryListeners(SERVICE_TYPE).orEmpty()

    private fun callbacks(info: NsdServiceInfo): List<NsdManager.ServiceInfoCallback> =
        platform.getServiceInfoCallbacks(info).orEmpty()

    private fun service(name: String) =
        NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
        }

    private fun startAndConfirm(): NsdManager.DiscoveryListener {
        agent.startDiscovery(INTERVAL_MS)
        platform.confirmStarts()
        return discoveries().single()
    }

    /** A running discovery that has found [name]: the discovery, the service and its callback. */
    private fun runningWithService(
        name: String
    ): Triple<NsdManager.DiscoveryListener, NsdServiceInfo, NsdManager.ServiceInfoCallback> {
        val discovery = startAndConfirm()
        val found = service(name)
        discovery.onServiceFound(found)
        return Triple(discovery, found, callbacks(found).single())
    }

    /**
     * Runs [action] on another thread while this one holds the lock. It has to block on the lock,
     * and to finish when this thread lets the lock go.
     */
    private fun assertWaitsForLock(action: () -> Unit) {
        val errors = CopyOnWriteArrayList<Throwable>()
        val worker = Thread {
            try {
                action()
            } catch (e: Throwable) {
                errors += e
            }
        }

        synchronized(lock) {
            worker.start()
            // The action ends or blocks on the lock; no timing decides which.
            val deadline = System.nanoTime() + SETTLE_MS * NANOS_PER_MS
            while (
                worker.state != Thread.State.BLOCKED &&
                    worker.state != Thread.State.TERMINATED &&
                    System.nanoTime() < deadline
            ) {
                Thread.sleep(POLL_MS)
            }

            assertThat(worker.state).isEqualTo(Thread.State.BLOCKED)
        }
        worker.join(SETTLE_MS)

        assertThat(worker.isAlive).isFalse()
        assertThat(errors).isEmpty()
    }

    /** Runs [action] on another thread while this one holds the lock: it must not wait for it. */
    private fun assertRunsWithoutLock(action: () -> Unit) {
        val errors = CopyOnWriteArrayList<Throwable>()
        val worker = Thread {
            try {
                action()
            } catch (e: Throwable) {
                errors += e
            }
        }

        synchronized(lock) {
            worker.start()
            worker.join(SETTLE_MS)

            assertThat(worker.isAlive).isFalse()
        }
        assertThat(errors).isEmpty()
    }

    @Test
    fun `a failed start waits for the lock and tells the listener outside it`() {
        agent.startDiscovery(INTERVAL_MS)
        val discovery = discoveries().single()

        assertWaitsForLock { discovery.onStartDiscoveryFailed(SERVICE_TYPE, 3) }

        assertThat(listener.failureHeldLock).containsExactly(false)
        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a failed stop waits for the lock`() {
        val discovery = startAndConfirm()

        assertWaitsForLock { discovery.onStopDiscoveryFailed(SERVICE_TYPE, 3) }

        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a confirmed start waits for the lock`() {
        agent.startDiscovery(INTERVAL_MS)
        val discovery = discoveries().single()

        assertWaitsForLock { discovery.onDiscoveryStarted(SERVICE_TYPE) }

        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `a confirmed stop waits for the lock`() {
        val discovery = startAndConfirm()

        assertWaitsForLock { discovery.onDiscoveryStopped(SERVICE_TYPE) }

        agent.startDiscovery(INTERVAL_MS)
        assertThat(discoveries()).hasSize(2)
    }

    @Test
    fun `a service that is found waits for the lock`() {
        val discovery = startAndConfirm()
        val found = service("Den PC")

        assertWaitsForLock { discovery.onServiceFound(found) }

        assertThat(callbacks(found)).hasSize(1)
    }

    @Test
    fun `a service that is lost waits for the lock`() {
        val (discovery, found, _) = runningWithService("Den PC")

        assertWaitsForLock { discovery.onServiceLost(found) }

        assertThat(callbacks(found)).isEmpty()
    }

    @Test
    fun `a failed registration of a service callback waits for the lock and tells the listener outside it`() {
        val (discovery, found, callback) = runningWithService("Den PC")
        // The platform drops the callback before it reports the failure.
        nsdManager.unregisterServiceInfoCallback(callback)

        assertWaitsForLock { callback.onServiceInfoCallbackRegistrationFailed(5) }

        assertThat(listener.failureHeldLock).containsExactly(false)
        discovery.onServiceFound(found)
        assertThat(callbacks(found)).hasSize(1)
    }

    @Test
    fun `a resolved service is reported without taking the lock, and the listener is told outside it`() {
        val (_, found, callback) = runningWithService("Den PC")
        val resolved =
            service("Den PC").apply {
                port = 47989
                hostAddresses = listOf(InetAddress.getByName("192.168.1.5"))
            }

        assertRunsWithoutLock { callback.onServiceUpdated(resolved) }

        assertThat(listener.addedHeldLock).containsExactly(false)
        assertThat(callbacks(found)).hasSize(1)
    }

    private companion object {
        const val SERVICE_TYPE = "_nvstream._tcp"
        const val INTERVAL_MS = 1000
        const val SETTLE_MS = 10_000L
        const val POLL_MS = 1L
        const val NANOS_PER_MS = 1_000_000L
    }
}
