package io.github.magalz.borealis.discovery

import android.app.Application
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.concurrent.thread
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
 * Characterization: these tests pass on the Java class and on its Kotlin conversion, except the one
 * about forgetting a computer and the ones about a restart that waits for the old thread to let go
 * of the resolver (Sprint 17b, BORE-6).
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

    /** Waits until [condition] holds, and fails if it does not within the wait. */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
        }
        assertWithMessage("never: $what").that(condition()).isTrue()
    }

    private fun Thread.isSleeping() = state == Thread.State.TIMED_WAITING

    /** Whether the thread is parked inside `stopDiscovery`, as a wait for the old thread is. */
    private fun Thread.waitsInStop() =
        (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) &&
            stackTrace.any { it.methodName == "stopDiscovery" }

    /**
     * Holds the first thread that logs a retry of a service, with its reference to the resolver
     * taken, until [release]: a thread that is slow to let go after it is interrupted. The
     * interrupt that comes while it is held is kept for the thread to see when it goes on, as the
     * discovery thread does with the one that stops it.
     */
    private class RetryStall : Handler() {
        private val taken = AtomicBoolean(false)
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        override fun publish(record: LogRecord) {
            if (record.message != RETRY_LINE || !taken.compareAndSet(false, true)) return
            reached.countDown()
            var interrupted = false
            while (released.count > 0) {
                try {
                    released.await()
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }

        fun awaitReached() {
            assertThat(reached.await(WAIT_NANOS, TimeUnit.NANOSECONDS)).isTrue()
        }

        fun release() = released.countDown()

        override fun flush() = Unit

        override fun close() = release()
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
    fun `a service that is forgotten is reported again when jmDNS announces it again`() {
        val (_, event) = announce("Den PC" to MdnsInfo(47989, listOf("192.168.1.5")))
        agent.serviceAdded(event)
        agent.serviceAdded(event)
        assertThat(listener.added).hasSize(1)

        agent.forget(MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989))
        agent.serviceAdded(event)

        assertThat(listener.added)
            .containsExactly(
                MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989),
                MdnsComputer("Den PC", ip4("192.168.1.5"), null, 47989),
            )
        assertThat(agent.getComputerSet()).hasSize(1)
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
    fun `a restart reopens the resolver, however slowly the old thread lets go`() {
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989)))
        val stall = RetryStall()
        val logger = Logger.getLogger(LIME_LOG)
        logger.addHandler(stall)
        try {
            agent.startDiscovery(INTERVAL_MS)
            stall.awaitReached()
            val old = discoveryThreads().single()
            val before = JmmDNS.Factory.getInstance()

            val restart = thread { agent.startDiscovery(INTERVAL_MS) }
            // The old thread lets go when the stall ends. The restart either waits for that, or it
            // goes on and the new thread takes its reference with the old one still held: the
            // second is the case that leaves the resolver open and its cache unreported.
            awaitUntil("a restart that waits for the old thread, or a new thread that runs") {
                restart.waitsInStop() || discoveryThreads().any { it !== old && it.isSleeping() }
            }
            stall.release()
            restart.join(WAIT_MS)

            assertThat(restart.isAlive).isFalse()
            val current = awaitThreads(1).single()
            assertThat(current).isNotSameInstanceAs(old)
            awaitSleeping(listOf(current))
            assertThat(JmmDNS.Factory.getInstance()).isNotSameInstanceAs(before)
        } finally {
            stall.release()
            logger.removeHandler(stall)
        }
    }

    @Test
    fun `stopping discovery gives up on a thread that does not let go`() {
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989)))
        val stall = RetryStall()
        val logger = Logger.getLogger(LIME_LOG)
        logger.addHandler(stall)
        try {
            agent.startDiscovery(INTERVAL_MS)
            stall.awaitReached()

            val stop = thread { agent.stopDiscovery() }
            stop.join(WAIT_MS)

            assertThat(stop.isAlive).isFalse()
        } finally {
            stall.release()
            logger.removeHandler(stall)
        }
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
        const val LIME_LOG = "com.limelight.LimeLog"
        const val RETRY_LINE = "mDNS: Retrying service resolution for machine: Den PC"
        const val INTERVAL_MS = 60_000
        const val POLL_MS = 10L
        const val WAIT_MS = 5_000L
        const val WAIT_NANOS = 5_000_000_000L
        const val SETTLE_MS = 200L
    }
}
