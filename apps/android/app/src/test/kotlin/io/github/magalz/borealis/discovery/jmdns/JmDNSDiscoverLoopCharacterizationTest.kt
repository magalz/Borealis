package io.github.magalz.borealis.discovery.jmdns

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import io.github.magalz.borealis.discovery.FakeJmDns
import io.github.magalz.borealis.discovery.FakeServiceEvent
import io.github.magalz.borealis.discovery.RecordingDiscoveryListener
import io.github.magalz.borealis.discovery.holdUnresolvedService
import io.github.magalz.borealis.discovery.ip4
import io.github.magalz.borealis.discovery.serviceInfo
import io.github.magalz.borealis.testing.LimeLogCapture
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceInfo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The loop of the discovery thread of the jmDNS agent: what it asks the resolver and in which
 * order, how long it waits between two passes, how it retries a service that did not resolve, and
 * what ends it. The resolver is a [ScriptedResolver] that records every call, so that a pass can be
 * read call by call.
 *
 * Characterization: these tests pass on the loop as it was converted from the Java, and they are
 * the contract of its refactor (Sprint 18a, BORE-96). The thread, the resolver reference and the
 * sleep that the interrupt cuts are covered by `JmDNSDiscoveryAgentCharacterizationTest` too, but
 * on the real resolver, which gives no interface to listen on and so no call to read.
 */
@RunWith(RobolectricTestRunner::class)
class JmDNSDiscoverLoopCharacterizationTest {
    @get:Rule(order = 0) val resolver = ScriptedResolver()
    @get:Rule(order = 1) val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val listener = RecordingDiscoveryListener()
    private val agent = JmDNSDiscoveryAgent(app, listener)
    private val others = mutableListOf<JmDNSDiscoveryAgent>()

    @Before
    fun theResolverIsNotReferenced() {
        val field = JmDNSDiscoveryAgent::class.java.getDeclaredField("resolverRefCount")
        field.isAccessible = true
        check(field.getInt(null) == 0) { "an earlier test left a reference to the resolver" }
    }

    @After
    fun stopEveryAgent() {
        agent.stopDiscovery()
        others.forEach { it.stopDiscovery() }
        // A thread that no agent holds any more is ended too: left alone it keeps its reference to
        // the resolver, and the next test finds the resolver open.
        discoveryThreads().forEach { it.interrupt() }
        awaitThreads(0)
    }

    private fun awaitThreads(count: Int): List<Thread> {
        awaitUntil("$count discovery threads") { discoveryThreads().size == count }
        return discoveryThreads()
    }

    /** Waits until the thread has ended after it let go of the resolver. */
    private fun awaitEnd() {
        awaitUntil("the resolver to be closed") { resolver.callsOf(CLOSE).isNotEmpty() }
        awaitThreads(0)
    }

    private fun awaitSleeping(threads: List<Thread>) =
        awaitUntil("the threads to sleep") { threads.all { it.isSleeping() } }

    /** Waits until the loop has sent its [count]th query. */
    private fun awaitQueries(count: Int) =
        awaitUntil("$count queries") { resolver.callsOf(QUERY).size >= count }

    private fun resolves(name: String, vararg addresses: String) =
        addresses.map { serviceInfo(name, PORT, listOf(ip4(it))) }.toTypedArray()

    @Test
    fun `a pass asks the resolver for the nvstream services after the listener is registered`() {
        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitSleeping(awaitThreads(1))

        assertThat(resolver.calls.map { it.signature }).containsExactly(LISTENER, QUERY).inOrder()
        assertThat(resolver.calls[0].args.first()).isEqualTo(SERVICE_TYPE)
        // No name, so that the query is for every service of the type, and the interval is its
        // timeout.
        assertThat(resolver.calls[1].args)
            .containsExactly(SERVICE_TYPE, null, IDLE_INTERVAL_MS.toLong())
            .inOrder()
    }

    @Test
    fun `the thread waits one interval between two passes and asks again with the same timeout`() {
        agent.startDiscovery(FAST_INTERVAL_MS)

        awaitQueries(3)

        val queries = resolver.callsOf(QUERY).take(3)
        assertThat(queries.map { it.args })
            .isEqualTo(List(3) { listOf(SERVICE_TYPE, null, FAST_INTERVAL_MS.toLong()) })
        // A sleep does not end before its time, give or take the clock of the machine.
        val gaps = queries.zipWithNext { first, second -> second.at - first.at }
        val atLeast = TimeUnit.MILLISECONDS.toNanos(FAST_INTERVAL_MS / 2L)
        gaps.forEach { assertThat(it).isAtLeast(atLeast) }
        // And the listener is registered once, with the first pass.
        assertThat(resolver.callsOf(LISTENER)).hasSize(1)
    }

    @Test
    fun `a service that did not resolve is asked for again on every pass, if the answer is empty`() {
        agent.holdUnresolvedService()
        resolver.answers["Den PC"] = emptyArray()

        agent.startDiscovery(FAST_INTERVAL_MS)
        awaitUntil("two retries") { resolver.callsOf(RETRY).size >= 2 }

        assertThat(resolver.calls.take(5).map { it.signature })
            .containsExactly(LISTENER, QUERY, RETRY, QUERY, RETRY)
            .inOrder()
        assertThat(resolver.callsOf(RETRY).map { it.args }.distinct())
            .containsExactly(listOf(SERVICE_TYPE, "Den PC", RETRY_TIMEOUT_MS))
        assertThat(log.messages(Level.INFO).count { it == RETRYING_DEN_PC }).isAtLeast(2)
        assertThat(log.messages(Level.INFO).filter { it.startsWith("mDNS: Resolved (retry)") })
            .isEmpty()
        assertThat(listener.added).isEmpty()
    }

    @Test
    fun `a service that did not resolve is asked for again on every pass, if there is no answer`() {
        agent.holdUnresolvedService()

        agent.startDiscovery(FAST_INTERVAL_MS)
        awaitUntil("two retries") { resolver.callsOf(RETRY).size >= 2 }

        assertThat(resolver.calls.take(5).map { it.signature })
            .containsExactly(LISTENER, QUERY, RETRY, QUERY, RETRY)
            .inOrder()
        assertThat(log.messages(Level.INFO).filter { it.startsWith("mDNS: Resolved (retry)") })
            .isEmpty()
        assertThat(listener.added).isEmpty()
    }

    @Test
    fun `a service that resolves on a retry is reported and logged, and is not asked for again`() {
        agent.holdUnresolvedService()
        resolver.answers["Den PC"] = resolves("Den PC", "192.168.1.5")

        agent.startDiscovery(FAST_INTERVAL_MS)
        // The third query is sent after the second pass has retried what is still pending.
        awaitQueries(3)

        assertThat(listener.added)
            .containsExactly(MdnsComputer("Den PC", ip4("192.168.1.5"), null, PORT))
        assertThat(log.messages(Level.INFO))
            .containsAtLeast(RETRYING_DEN_PC, "mDNS: Resolved (retry) with 1 service entries")
            .inOrder()
        assertThat(resolver.callsOf(RETRY)).hasSize(1)
    }

    @Test
    fun `every entry of a service that resolves on a retry is reported`() {
        agent.holdUnresolvedService()
        resolver.answers["Den PC"] = resolves("Den PC", "192.168.1.5", "192.168.1.6")

        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitSleeping(awaitThreads(1))

        assertThat(listener.added)
            .containsExactly(
                MdnsComputer("Den PC", ip4("192.168.1.5"), null, PORT),
                MdnsComputer("Den PC", ip4("192.168.1.6"), null, PORT),
            )
        assertThat(log.messages(Level.INFO))
            .contains("mDNS: Resolved (retry) with 2 service entries")
    }

    @Test
    fun `every service that is pending is retried in the same pass`() {
        agent.holdUnresolvedService()
        agent.serviceAdded(FakeServiceEvent(FakeJmDns(), serviceInfo("Laptop", PORT)))
        resolver.answers["Den PC"] = resolves("Den PC", "192.168.1.5")
        resolver.answers["Laptop"] = resolves("Laptop", "192.168.1.7")

        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitSleeping(awaitThreads(1))

        // The pending names are copied before the loop, as each resolved one leaves the set.
        assertThat(resolver.callsOf(RETRY).map { it.args[1] }).containsExactly("Den PC", "Laptop")
        assertThat(listener.added.map { it.name }).containsExactly("Den PC", "Laptop")
    }

    @Test
    fun `an interrupt before the first pass sends no query and lets go of the resolver`() {
        resolver.onCall = { signature ->
            if (signature == LISTENER) Thread.currentThread().interrupt()
        }

        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitEnd()

        assertThat(resolver.calls.map { it.signature }).containsExactly(LISTENER, CLOSE).inOrder()
        assertThat(resolver.callsOf(CLOSE).single().interrupted).isFalse()
    }

    @Test
    fun `an interrupt during the query ends the pass after its retries, without a wait`() {
        agent.holdUnresolvedService()
        // The interrupt that comes while the resolver is asked is kept for the sleep to see, as
        // jmDNS keeps the one that it does not act on.
        resolver.onCall = { signature ->
            if (signature == QUERY) Thread.currentThread().interrupt()
        }

        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitEnd()

        assertThat(resolver.calls.map { it.signature })
            .containsExactly(LISTENER, QUERY, RETRY, CLOSE)
            .inOrder()
        assertThat(resolver.callsOf(CLOSE).single().interrupted).isFalse()
    }

    @Test
    fun `stopping discovery cuts the wait, and the resolver is kept until then`() {
        agent.startDiscovery(IDLE_INTERVAL_MS)
        awaitSleeping(awaitThreads(1))
        assertThat(resolver.callsOf(CLOSE)).isEmpty()

        agent.stopDiscovery()
        awaitEnd()

        assertThat(resolver.calls.map { it.signature })
            .containsExactly(LISTENER, QUERY, CLOSE)
            .inOrder()
        assertThat(resolver.callsOf(CLOSE).single().interrupted).isFalse()
    }

    @Test
    fun `each thread references the resolver once and the last one to end closes it`() {
        val other = JmDNSDiscoveryAgent(app, RecordingDiscoveryListener()).also { others += it }
        agent.startDiscovery(IDLE_INTERVAL_MS)
        other.startDiscovery(IDLE_INTERVAL_MS)
        awaitSleeping(awaitThreads(2))
        assertThat(resolver.callsOf(LISTENER)).hasSize(1)
        assertThat(resolver.callsOf(QUERY)).hasSize(2)

        agent.stopDiscovery()
        awaitThreads(1)
        assertThat(resolver.callsOf(CLOSE)).isEmpty()

        other.stopDiscovery()
        awaitEnd()
        assertThat(resolver.callsOf(CLOSE)).hasSize(1)
    }

    /**
     * One call to the resolver, with the time and the interrupt flag of the thread that made it.
     */
    class Call(val signature: String, val args: List<Any?>) {
        val at = System.nanoTime()
        val interrupted = Thread.currentThread().isInterrupted
    }

    /**
     * A resolver that stands in for the shared jmDNS one, which the agent reaches through
     * `JmmDNS.Factory`. It records the calls of the agent, answers `getServiceInfos` from [answers]
     * (no entry is no answer), and fails on any other call, so that a loop that starts to ask
     * something else is noticed. [onCall] runs on the calling thread, with the signature, as the
     * call starts.
     */
    class ScriptedResolver : ExternalResource() {
        val calls = CopyOnWriteArrayList<Call>()
        val answers = ConcurrentHashMap<String, Array<ServiceInfo>>()
        @Volatile var onCall: (String) -> Unit = {}

        private var original: JmmDNS.Factory.ClassDelegate? = null

        private val instance: JmmDNS =
            Proxy.newProxyInstance(JmmDNS::class.java.classLoader, arrayOf(JmmDNS::class.java)) {
                proxy,
                method,
                args ->
                val parameters = method.parameterTypes.joinToString(",") { it.simpleName }
                val signature = "${method.name}($parameters)"
                val arguments = args?.toList().orEmpty()
                when (signature) {
                    "toString()" -> "ScriptedResolver"
                    "hashCode()" -> System.identityHashCode(proxy)
                    "equals(Object)" -> proxy === arguments[0]
                    else -> {
                        calls += Call(signature, arguments)
                        onCall(signature)
                        when (signature) {
                            LISTENER,
                            QUERY,
                            CLOSE -> null
                            RETRY -> answers[arguments[1] as String]
                            else -> throw UnsupportedOperationException(signature)
                        }
                    }
                }
            } as JmmDNS

        fun callsOf(signature: String): List<Call> = calls.filter { it.signature == signature }

        override fun before() {
            // An earlier test that left the real resolver open must not stand between the agent and
            // the scripted one.
            JmmDNS.Factory.close()
            original = JmmDNS.Factory.classDelegate()
            JmmDNS.Factory.setClassDelegate { instance }
        }

        override fun after() {
            JmmDNS.Factory.close()
            JmmDNS.Factory.setClassDelegate(original)
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_nvstream._tcp.local."
        const val PORT = 47989
        const val RETRY_TIMEOUT_MS = 500L

        /** Long enough that a second pass never comes while a test runs. */
        const val IDLE_INTERVAL_MS = 60_000

        /** Short enough that a test waits for a few passes. */
        const val FAST_INTERVAL_MS = 200

        const val RETRYING_DEN_PC = "mDNS: Retrying service resolution for machine: Den PC"
    }
}

private const val LISTENER = "addServiceListener(String,ServiceListener)"
private const val QUERY = "requestServiceInfo(String,String,long)"
private const val RETRY = "getServiceInfos(String,String,long)"
private const val CLOSE = "close()"
