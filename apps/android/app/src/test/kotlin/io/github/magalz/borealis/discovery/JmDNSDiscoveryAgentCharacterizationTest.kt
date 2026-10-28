package io.github.magalz.borealis.discovery

import android.app.Application
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The agent that discovers hosts with jmDNS (Android 13 and earlier): how it reads a service that
 * jmDNS announces, and how its discovery thread starts, restarts and stops while other agents share
 * the one jmDNS resolver. [NoMdnsNetwork] gives jmDNS no interface, so the thread runs and no
 * packet leaves the test.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class JmDNSDiscoveryAgentCharacterizationTest {
    @get:Rule(order = 0) val network = NoMdnsNetwork()
    @get:Rule(order = 1) val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val listener = RecordingDiscoveryListener()
    private val agent = JmDNSDiscoveryAgent(app, listener)
    private val started = mutableListOf<JmDNSDiscoveryAgent>()

    @After
    fun stopEveryAgent() {
        agent.stopDiscovery()
        started.forEach { it.stopDiscovery() }
        awaitThreads(0)
    }

    private fun discoveryThreads(): List<Thread> =
        Thread.getAllStackTraces().keys.filter { it.name == THREAD_NAME && it.isAlive }

    /** Waits until the number of live discovery threads is [count], and returns the threads. */
    private fun awaitThreads(count: Int): List<Thread> {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (discoveryThreads().size != count && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
        }
        return discoveryThreads()
    }

    /** Waits until each thread is in its sleep, which is after it referenced the resolver. */
    private fun awaitSleeping(threads: List<Thread>) {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (
            threads.any { it.state != Thread.State.TIMED_WAITING } && System.nanoTime() < deadline
        ) {
            Thread.sleep(POLL_MS)
        }
        assertThat(threads.map { it.state }).doesNotContain(Thread.State.NEW)
    }

    /** Waits until [line] is logged at info level by the discovery thread. */
    private fun awaitInfo(line: String) {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (!log.messages(Level.INFO).contains(line) && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
        }
    }

    private fun startAnother(): JmDNSDiscoveryAgent {
        val other = JmDNSDiscoveryAgent(app, RecordingDiscoveryListener())
        started += other
        other.startDiscovery(INTERVAL_MS)
        return other
    }

    private fun multicastLockOf(target: JmDNSDiscoveryAgent): WifiManager.MulticastLock {
        val field = JmDNSDiscoveryAgent::class.java.getDeclaredField("multicastLock")
        field.isAccessible = true
        return field.get(target) as WifiManager.MulticastLock
    }

    /** The listener that the resolver calls for every agent: the agents share it. */
    private fun sharedResolverListener(): ServiceListener {
        val field = JmDNSDiscoveryAgent::class.java.getDeclaredField("nvstreamListener")
        field.isAccessible = true
        return field.get(null) as ServiceListener
    }

    private fun announce(vararg known: Pair<String, MdnsInfo>): Pair<FakeJmDns, ServiceEvent> {
        val dns = FakeJmDns(known.associate { (name, info) -> name to info.toServiceInfo(name) })
        return dns to FakeServiceEvent(dns, serviceInfo(known.first().first, 0))
    }

    /** What a resolved service holds. */
    private class MdnsInfo(
        val port: Int,
        val v4: List<String>,
        val v6: List<String> = emptyList(),
    ) {
        fun toServiceInfo(name: String) =
            serviceInfo(name, port, v4.map { ip4(it) }, v6.map { ip6(it) })
    }

    @Test
    fun `an announced service that resolves at once is reported with its addresses and port`() {
        val (dns, event) =
            announce("Den PC" to MdnsInfo(47989, listOf("192.168.1.5"), listOf("2001:db8::1")))

        agent.serviceAdded(event)

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), ip6("2001:db8::1"), 47989))
        assertThat(dns.requests)
            .containsExactly(InfoRequest("_nvstream._tcp.local.", "Den PC", 500))
        assertThat(log.messages(Level.INFO))
            .containsAtLeast("mDNS: Machine appeared: Den PC", "mDNS: Resolved (blocking)")
            .inOrder()
    }

    @Test
    fun `the computer takes its name and port from the resolved service, not from the event`() {
        val dns =
            FakeJmDns(
                mapOf("Event PC" to serviceInfo("Resolved PC", 5000, listOf(ip4("10.0.0.2"))))
            )
        val event = FakeServiceEvent(dns, serviceInfo("Event PC", 47989))

        agent.serviceAdded(event)

        assertThat(dns.requests.single().name).isEqualTo("Event PC")
        assertThat(listener.added)
            .containsExactly(MdnsComputer("Resolved PC", ip4("10.0.0.2"), null, 5000))
    }

    @Test
    fun `an announced service with only IPv6 addresses is reported through them`() {
        val (_, event) =
            announce("Den PC" to MdnsInfo(47989, emptyList(), listOf("fe80::1", "2001:db8::1")))

        agent.serviceAdded(event)

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip6("fe80::1"), ip6("2001:db8::1"), 47989))
    }

    @Test
    fun `an announced service that does not resolve yet is not reported`() {
        val dns = FakeJmDns()
        val event = FakeServiceEvent(dns, serviceInfo("Den PC", 47989))

        agent.serviceAdded(event)

        assertThat(listener.added).isEmpty()
        assertThat(dns.requests).hasSize(1)
        assertThat(log.messages(Level.INFO)).contains("mDNS: Machine appeared: Den PC")
        assertThat(log.messages(Level.INFO)).doesNotContain("mDNS: Resolved (blocking)")
    }

    @Test
    fun `a service that did not resolve yet is retried by the discovery thread`() {
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989)))

        agent.startDiscovery(INTERVAL_MS)
        awaitInfo("mDNS: Retrying service resolution for machine: Den PC")

        assertThat(log.messages(Level.INFO))
            .contains("mDNS: Retrying service resolution for machine: Den PC")
        assertThat(listener.added).isEmpty()
    }

    @Test
    fun `a service that resolved at once is not retried`() {
        val resolved = serviceInfo("Den PC", 47989, listOf(ip4("10.0.0.2")))
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(mapOf("Den PC" to resolved)), resolved))
        // A second service that does not resolve marks the end of the first pass of the thread.
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Laptop", 47989)))

        agent.startDiscovery(INTERVAL_MS)
        awaitInfo("mDNS: Retrying service resolution for machine: Laptop")
        awaitSleeping(awaitThreads(1))
        Thread.sleep(SETTLE_MS)

        assertThat(log.messages(Level.INFO))
            .doesNotContain("mDNS: Retrying service resolution for machine: Den PC")
        assertThat(listener.added).hasSize(1)
    }

    @Test
    fun `a service that disappears is only logged, and one that resolved is left alone`() {
        val event = FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989))

        agent.serviceRemoved(event)
        agent.serviceResolved(event)

        assertThat(listener.added).isEmpty()
        assertThat(listener.failures).isEmpty()
        assertThat(log.lines()).containsExactly("INFO: mDNS: Machine disappeared: Den PC")
    }

    @Test
    fun `starting discovery runs a thread named for it and holds the multicast lock`() {
        agent.startDiscovery(INTERVAL_MS)

        val threads = awaitThreads(1)
        assertThat(threads).hasSize(1)
        assertThat(threads.single().name).isEqualTo("mDNS Discovery Thread")
        assertThat(multicastLockOf(agent).isHeld).isTrue()
    }

    @Test
    fun `the multicast lock is not reference counted`() {
        val lock = multicastLockOf(agent)

        lock.acquire()
        lock.acquire()
        lock.release()

        assertThat(lock.isHeld).isFalse()
    }

    @Test
    fun `stopping discovery ends the thread and releases the multicast lock`() {
        agent.startDiscovery(INTERVAL_MS)
        awaitSleeping(awaitThreads(1))

        agent.stopDiscovery()

        assertThat(awaitThreads(0)).isEmpty()
        assertThat(multicastLockOf(agent).isHeld).isFalse()
    }

    @Test
    fun `stopping discovery that never started releases the lock and does nothing else`() {
        agent.stopDiscovery()

        assertThat(multicastLockOf(agent).isHeld).isFalse()
        assertThat(discoveryThreads()).isEmpty()
    }

    @Test
    fun `starting discovery again replaces the thread`() {
        agent.startDiscovery(INTERVAL_MS)
        val first = awaitThreads(1).single()
        awaitSleeping(listOf(first))

        agent.startDiscovery(INTERVAL_MS)

        val second = awaitThreads(1).single()
        assertThat(second).isNotSameInstanceAs(first)
        assertThat(first.isAlive).isFalse()
        assertThat(multicastLockOf(agent).isHeld).isTrue()
    }

    @Test
    fun `agents share one resolver until the last of them stops`() {
        agent.startDiscovery(INTERVAL_MS)
        val other = startAnother()
        awaitSleeping(awaitThreads(2))
        val shared = JmmDNS.Factory.getInstance()

        agent.stopDiscovery()
        awaitThreads(1)
        assertThat(JmmDNS.Factory.getInstance()).isSameInstanceAs(shared)

        other.stopDiscovery()
        awaitThreads(0)
        assertThat(JmmDNS.Factory.getInstance()).isNotSameInstanceAs(shared)
    }

    @Test
    fun `a service that the resolver announces reaches every started agent and no stopped one`() {
        val other = startAnother()
        agent.startDiscovery(INTERVAL_MS)
        val (_, event) = announce("Den PC" to MdnsInfo(47989, listOf("192.168.1.5")))

        sharedResolverListener().serviceAdded(event)

        assertThat(listener.added).hasSize(1)
        agent.stopDiscovery()
        sharedResolverListener().serviceAdded(event)
        assertThat(listener.added).hasSize(1)
        other.stopDiscovery()
    }

    @Test
    fun `the resolver passes on a service that disappears or resolves to every started agent`() {
        agent.startDiscovery(INTERVAL_MS)
        val event = FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989))

        sharedResolverListener().serviceRemoved(event)
        sharedResolverListener().serviceResolved(event)

        assertThat(log.lines()).containsExactly("INFO: mDNS: Machine disappeared: Den PC")
    }

    private companion object {
        const val THREAD_NAME = "mDNS Discovery Thread"
        const val INTERVAL_MS = 60_000
        const val POLL_MS = 10L
        const val WAIT_NANOS = 5_000_000_000L
        const val SETTLE_MS = 200L
    }
}
