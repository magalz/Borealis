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
 * about forgetting a computer and the ones about a stop or a restart while the old thread is slow
 * to let go of the resolver (Sprint 17b, BORE-6).
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
        while (threads.any { !it.isSleeping() } && System.nanoTime() < deadline) {
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

    /** Whether the thread is in the sleep of its loop, and not in a wait for the old thread. */
    private fun Thread.isSleeping() =
        state == Thread.State.TIMED_WAITING && stackTrace.any { it.methodName.startsWith("sleep") }

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
        agent.holdUnresolvedService()
        withRetryStall { stall ->
            agent.startDiscovery(INTERVAL_MS)
            stall.awaitReached()
            val old = discoveryThreads().single()
            val before = JmmDNS.Factory.getInstance()

            val restart = callUntilItEndsOrWaits { agent.startDiscovery(INTERVAL_MS) }
            // The old thread lets go when the stall ends. The new thread takes its reference after
            // that, and not before: a reference taken while the old one is held leaves the
            // resolver open and its cache unreported.
            stall.release()
            restart.join(WAIT_MS)

            assertThat(restart.isAlive).isFalse()
            val current = awaitThreads(1).single()
            assertThat(current).isNotSameInstanceAs(old)
            awaitSleeping(listOf(current))
            assertThat(JmmDNS.Factory.getInstance()).isNotSameInstanceAs(before)
        }
    }

    @Test
    fun `a rebind right after an unbind reopens the resolver, however slowly the old thread lets go`() {
        agent.holdUnresolvedService()
        withRetryStall { stall ->
            agent.startDiscovery(INTERVAL_MS)
            stall.awaitReached()
            val old = discoveryThreads().single()
            val before = JmmDNS.Factory.getInstance()

            // Neither call waits for the old thread: the stall holds it until they both returned.
            agent.stopDiscovery()
            agent.startDiscovery(INTERVAL_MS)
            // The new thread either waits for the old one to let go, or it took its reference.
            awaitUntil("the new thread to wait for the old one, or to sleep") {
                discoveryThreads().any { it !== old && it.state == Thread.State.TIMED_WAITING }
            }
            stall.release()

            val current = awaitThreads(1).single()
            assertThat(current).isNotSameInstanceAs(old)
            awaitSleeping(listOf(current))
            assertWithMessage("the resolver was closed and opened again")
                .that(JmmDNS.Factory.getInstance())
                .isNotSameInstanceAs(before)
        }
    }

    @Test
    fun `stopping discovery does not wait for a thread that is slow to let go`() {
        agent.holdUnresolvedService()
        withRetryStall {
            agent.startDiscovery(INTERVAL_MS)
            it.awaitReached()
            val old = discoveryThreads().single()

            val stop = callUntilItEndsOrWaits { agent.stopDiscovery() }

            // The stall holds the old thread, so it is alive: the stop ended without it.
            assertThat(stop.state).isEqualTo(Thread.State.TERMINATED)
            assertThat(old.isAlive).isTrue()
        }
    }

    @Test
    fun `a restart gives up on a thread that does not let go`() {
        agent.holdUnresolvedService()
        withRetryStall {
            agent.startDiscovery(INTERVAL_MS)
            it.awaitReached()
            val old = discoveryThreads().single()

            agent.startDiscovery(INTERVAL_MS)

            // The stall is never released: the new thread goes on without the old one's release.
            awaitUntil("the new thread to give up on the old one and to sleep") {
                discoveryThreads().any { it !== old && it.isSleeping() }
            }
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
        const val INTERVAL_MS = 60_000
        const val POLL_MS = 10L
        const val WAIT_MS = 5_000L
        const val WAIT_NANOS = 5_000_000_000L
        const val SETTLE_MS = 200L
    }
}

/** The logger of `LimeLog`, which the discovery thread writes to. */
private const val LIME_LOG = "com.limelight.LimeLog"

/** What the discovery thread logs when it retries "Den PC", a service that did not resolve yet. */
private const val RETRY_LINE = "mDNS: Retrying service resolution for machine: Den PC"

private const val STALL_WAIT_SECONDS = 5L
private const val CALL_POLL_MS = 10L

/** How many samples in a row show a call waiting before it counts as waiting. */
private const val WAITING_SAMPLES = 3

/** Makes the agent hold "Den PC" as a service that did not resolve yet: its thread retries it. */
internal fun JmDNSDiscoveryAgent.holdUnresolvedService() =
    serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Den PC", 47989)))

/**
 * Holds the first discovery thread that logs a retry of a service, with its reference to the
 * resolver taken, until [release]: a thread that is slow to let go after it is interrupted. The
 * interrupt that comes while it is held is kept for the thread to see when it goes on, as the
 * discovery thread does with the one that stops it.
 */
internal class RetryStall : Handler() {
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
        assertThat(reached.await(STALL_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
    }

    fun release() = released.countDown()

    override fun flush() = Unit

    override fun close() = release()
}

/** Runs [block] with a [RetryStall] on the log, and lets the thread go when it ends. */
internal fun <T> withRetryStall(block: (RetryStall) -> T): T {
    val stall = RetryStall()
    val logger = Logger.getLogger(LIME_LOG)
    logger.addHandler(stall)
    try {
        return block(stall)
    } finally {
        stall.release()
        logger.removeHandler(stall)
    }
}

/**
 * Runs [call] on a thread of its own, as the main thread or an IO thread of the app would, and
 * waits until that thread has ended or waits (parked, or blocked on a lock). A call that waits for
 * another thread is seen waiting, so a test does not depend on how long the wait lasts. A wait that
 * a call has for a moment, on a lock or a class that it loads, is not taken for the wait for
 * another thread: the thread has to be seen waiting a few samples in a row. Returns the thread.
 */
internal fun callUntilItEndsOrWaits(call: () -> Unit): Thread {
    val caller = thread { call() }
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STALL_WAIT_SECONDS)
    var waiting = 0
    while (caller.isAlive && waiting < WAITING_SAMPLES && System.nanoTime() < deadline) {
        Thread.sleep(CALL_POLL_MS)
        waiting = if (caller.state == Thread.State.RUNNABLE) 0 else waiting + 1
    }
    return caller
}
