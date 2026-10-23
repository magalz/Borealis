package io.github.magalz.borealis.discovery

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.discovery.DiscoveryService
import com.limelight.nvstream.mdns.MdnsComputer
import java.net.InetAddress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The service that `ComputerManagerService` (Java) binds to find hosts on the network: the binder
 * it hands out, which agent it runs for each Android version, and what reaches the listener that
 * the client bound. The platform is Robolectric's `NsdManager`, which confirms a discovery at once.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class DiscoveryServiceCharacterizationTest {
    @get:Rule(order = 0) val network = NoMdnsNetwork()
    @get:Rule(order = 1) val log = LimeLogRecords()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val platform = shadowOf(app.getSystemService(NsdManager::class.java))
    private val service = Robolectric.buildService(DiscoveryService::class.java).create().get()
    private val binder = service.onBind(Intent()) as DiscoveryService.DiscoveryBinder
    private val listener = RecordingDiscoveryListener()

    private fun discoveries() = platform.getDiscoveryListeners(SERVICE_TYPE).orEmpty()

    private fun discoveryThreads() =
        Thread.getAllStackTraces().keys.filter { it.name == THREAD_NAME && it.isAlive }

    private fun awaitThreads(count: Int): List<Thread> {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (discoveryThreads().size != count && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
        }
        return discoveryThreads()
    }

    private fun foundService(name: String) =
        NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
        }

    /** Has the platform find [name] and resolve it to the IPv4 address [address]. */
    private fun findAndResolve(name: String, address: String) {
        val found = foundService(name)
        discoveries().single().onServiceFound(found)
        val resolved =
            NsdServiceInfo().apply {
                serviceName = name
                port = PORT
                hostAddresses = listOf(InetAddress.getByName(address))
            }
        platform.getServiceInfoCallbacks(found).orEmpty().single().onServiceUpdated(resolved)
    }

    @Test
    fun `the service hands out one binder for every bind`() {
        val again = service.onBind(Intent())

        assertThat(again).isSameInstanceAs(binder)
        assertThat(binder).isInstanceOf(Binder::class.java)
    }

    @Test
    fun `the manifest declares the service, kept to the app`() {
        val info =
            app.packageManager.getServiceInfo(ComponentName(app, DiscoveryService::class.java), 0)

        assertThat(info.name).isEqualTo("com.limelight.discovery.DiscoveryService")
        assertThat(info.exported).isFalse()
        assertThat(info.nonLocalizedLabel.toString()).isEqualTo("mDNS PC Auto-Discovery Service")
    }

    @Test
    fun `a service that nothing found yet has no computers`() {
        assertThat(binder.getComputerSet()).isEmpty()
    }

    @Test
    fun `starting discovery through the binder asks the platform for the nvstream service`() {
        binder.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
    }

    @Test
    fun `stopping discovery through the binder stops it on the platform`() {
        binder.startDiscovery(INTERVAL_MS)

        binder.stopDiscovery()

        assertThat(discoveries()).isEmpty()
    }

    @Test
    fun `a computer that the platform resolves reaches the bound listener and the computer set`() {
        binder.setListener(listener)
        binder.startDiscovery(INTERVAL_MS)

        findAndResolve("Den PC", "192.168.1.5")

        val expected = MdnsComputer("Den PC", ip4("192.168.1.5"), null, PORT)
        assertThat(listener.added).containsExactly(expected)
        assertThat(binder.getComputerSet()).containsExactly(expected)
    }

    @Test
    fun `a failure of the discovery reaches the bound listener`() {
        binder.setListener(listener)
        binder.startDiscovery(INTERVAL_MS)
        val found = foundService("Den PC")
        discoveries().single().onServiceFound(found)

        platform
            .getServiceInfoCallbacks(found)
            .orEmpty()
            .single()
            .onServiceInfoCallbackRegistrationFailed(5)

        assertThat(listener.failures.map { it.message })
            .containsExactly("onServiceInfoCallbackRegistrationFailed(): 5")
    }

    @Test
    fun `a computer found while no listener is bound is kept in the computer set`() {
        binder.startDiscovery(INTERVAL_MS)

        findAndResolve("Den PC", "192.168.1.5")

        assertThat(binder.getComputerSet())
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), null, PORT))
    }

    @Test
    fun `only the listener that was bound last hears the computers`() {
        val first = RecordingDiscoveryListener()
        binder.setListener(first)
        binder.setListener(listener)
        binder.startDiscovery(INTERVAL_MS)

        findAndResolve("Den PC", "192.168.1.5")

        assertThat(first.added).isEmpty()
        assertThat(listener.added).hasSize(1)
    }

    @Test
    fun `unbinding stops discovery, forgets the listener and does not ask to be rebound`() {
        binder.setListener(listener)
        binder.startDiscovery(INTERVAL_MS)

        val rebind = service.onUnbind(Intent())

        assertThat(rebind).isFalse()
        assertThat(discoveries()).isEmpty()
        binder.startDiscovery(INTERVAL_MS)
        findAndResolve("Den PC", "192.168.1.5")
        assertThat(listener.added).isEmpty()
        assertThat(binder.getComputerSet()).hasSize(1)
    }

    @Test
    @Config(sdk = [ANDROID_14])
    fun `on Android 14 the service discovers with the platform`() {
        binder.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).hasSize(1)
        assertThat(discoveryThreads()).isEmpty()
    }

    @Test
    @Config(sdk = [ANDROID_13])
    fun `before Android 14 the service discovers with jmDNS and leaves the platform alone`() {
        binder.startDiscovery(INTERVAL_MS)

        assertThat(discoveries()).isEmpty()
        assertThat(awaitThreads(1)).hasSize(1)

        service.onUnbind(Intent())

        assertThat(awaitThreads(0)).isEmpty()
    }

    private companion object {
        const val SERVICE_TYPE = "_nvstream._tcp"
        const val THREAD_NAME = "mDNS Discovery Thread"
        const val INTERVAL_MS = 60_000
        const val PORT = 47989
        const val ANDROID_13 = 33
        const val ANDROID_14 = 34
        const val POLL_MS = 10L
        const val WAIT_NANOS = 5_000_000_000L
    }
}
