package io.github.magalz.borealis.computers

import android.net.ConnectivityManager
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import io.github.magalz.borealis.testing.LimeLogCapture
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowSystemClock

/**
 * How the inherited `ComputerManagerService` polls its hosts: the threads that it starts and stops,
 * the report of each host to the listener, the number of failed polls that make a host offline and
 * the time after which a state is forgotten. The hosts are fakes on the loopback interface. The
 * polling period is real time (one and a half seconds), so a test that needs several polls takes
 * several seconds; every wait ends on what the service does, not on a fixed delay.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerPollingCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    private val caller = Thread.currentThread()

    /** A host that answers [info] while it is up and closes the connection while it is down. */
    private class Flaky(host: NvFakeHost, info: String) {
        @Volatile var down = false

        init {
            host.handler = { if (down) HostReply.raw(ByteArray(0)) else HostReply.xml(info) }
        }
    }

    /** Waits until the polling thread has told the listener and noted the time: it sleeps. */
    private fun awaitAsleep() =
        awaitUntil("the polling thread to sleep") {
            liveThreads(POLLING_THREAD).singleOrNull()?.state == Thread.State.TIMED_WAITING
        }

    /** The warnings that say how many polls of [name] failed in a row. */
    private fun failures(name: String): List<String> =
        log.lines().filter { it.startsWith("WARNING: $name is offline (try ") }

    @Test
    fun `starting polling reports each host first, then polls it and saves what it learned`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.seed(saved(UUID_1, "OLD-NAME", manual = host.address))
        rig.start()
        val updates = Updates()

        rig.binder.startPolling(updates)

        val initial = updates.on(caller).single()
        assertThat(initial.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(initial.name).isEqualTo("OLD-NAME")
        assertThat(initial.details).isSameInstanceAs(rig.binder.getComputer(UUID_1))
        updates.awaitOtherThan(caller, ComputerDetails.State.ONLINE)
        val polled = updates.events.first { it.thread !== caller && it.state == ONLINE }
        assertThat(polled.details).isSameInstanceAs(initial.details)
        assertThat(polled.name).isEqualTo("DEN-PC")
        assertThat(polled.details.activeAddress).isEqualTo(host.address)
        assertThat(rig.savedRows().single().name).isEqualTo("DEN-PC")
        assertThat(liveThreads(POLLING_THREAD).map { it.name })
            .containsExactly("${POLLING_THREAD}OLD-NAME")
    }

    @Test
    fun `a host is polled again every one and a half seconds`() {
        val host = rig.host()
        val times = CopyOnWriteArrayList<Long>()
        host.handler = { _ ->
            times += System.nanoTime()
            HostReply.xml(hostInfo(UUID_1))
        }
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()

        rig.binder.startPolling(Updates())
        awaitUntil("three polls") { times.size >= THREE_POLLS }
        rig.binder.stopPolling()

        val gaps = times.take(THREE_POLLS).zipWithNext { a, b -> (b - a) / NANOS_PER_MS }
        assertThat(gaps).hasSize(2)
        for (gap in gaps) assertThat(gap).isAtLeast(POLL_PERIOD_MS - SLACK_MS)
    }

    @Test
    fun `polling twice keeps one thread per host and hands the events to the last listener`() {
        val host = rig.reachable(UUID_1)
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()
        val first = Updates()
        val second = Updates()

        rig.binder.startPolling(first)
        first.awaitOtherThan(caller, ONLINE)
        rig.binder.startPolling(second)

        assertThat(second.on(caller)).hasSize(1)
        assertThat(liveThreads(POLLING_THREAD)).hasSize(1)
        val seen = first.events.size
        second.awaitOtherThan(caller, ONLINE)
        assertThat(first.events.size).isEqualTo(seen)
    }

    @Test
    fun `starting polling asks the platform to discover hosts and stopping it takes that back`() {
        rig.start()
        assertThat(rig.discoveries()).isEmpty()

        rig.binder.startPolling(Updates())
        assertThat(rig.discoveries()).hasSize(1)

        rig.binder.stopPolling()
        assertThat(rig.discoveries()).isEmpty()
    }

    @Test
    fun `stopping polling ends the threads, forgets the listener and keeps the hosts`() {
        val host = rig.reachable(UUID_1)
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(caller, ONLINE)
        awaitAsleep()
        val polling = liveThreads(POLLING_THREAD).single()

        rig.binder.stopPolling()

        // The thread is woken from its sleep of one and a half seconds.
        polling.join(PROMPT_MS)
        assertThat(polling.isAlive).isFalse()
        val seen = updates.events.size
        shadowOf(rig.app.getSystemService(ConnectivityManager::class.java))
            .networkCallbacks
            .single()
            .onLost(ShadowNetwork.newInstance(1))
        assertThat(updates.events).hasSize(seen)
        assertThat(rig.binder.getComputer(UUID_1)).isNotNull()
        assertThat(rig.binder.getComputer(UUID_1)?.state).isEqualTo(OFFLINE)

        val again = Updates()
        rig.binder.startPolling(again)
        assertThat(again.on(caller)).hasSize(1)
        assertThat(liveThreads(POLLING_THREAD)).hasSize(1)
        assertThat(rig.discoveries()).hasSize(1)
    }

    @Test
    fun `a host that is added while polling is on is polled, and not before`() {
        val host = rig.reachable(UUID_1, "ONE-PC")
        val other = rig.reachable(UUID_2, "TWO-PC")
        rig.start()
        rig.binder.addComputerBlocking(ComputerDetails().also { it.manualAddress = host.address })
        assertThat(liveThreads(POLLING_THREAD)).isEmpty()

        rig.binder.startPolling(Updates())
        assertThat(liveThreads(POLLING_THREAD).map { it.name })
            .containsExactly("${POLLING_THREAD}ONE-PC")

        rig.binder.addComputerBlocking(ComputerDetails().also { it.manualAddress = other.address })
        assertThat(liveThreads(POLLING_THREAD).map { it.name })
            .containsExactly("${POLLING_THREAD}ONE-PC", "${POLLING_THREAD}TWO-PC")

        rig.binder.stopPolling()
        awaitUntil("the polling threads to end") { liveThreads(POLLING_THREAD).isEmpty() }
        val late = rig.reachable(UUID_3, "THREE-PC")
        rig.binder.addComputerBlocking(ComputerDetails().also { it.manualAddress = late.address })
        assertThat(liveThreads(POLLING_THREAD)).isEmpty()
    }

    @Test
    fun `removing a host ends its polling thread and no other`() {
        val one = rig.reachable(UUID_1, "ONE-PC")
        val two = rig.reachable(UUID_2, "TWO-PC")
        rig.seed(
            saved(UUID_1, "ONE-PC", manual = one.address),
            saved(UUID_2, "TWO-PC", manual = two.address),
        )
        rig.start()
        rig.binder.startPolling(Updates())
        awaitUntil("both threads to sleep") {
            liveThreads(POLLING_THREAD).let { threads ->
                threads.size == 2 && threads.all { it.state == Thread.State.TIMED_WAITING }
            }
        }
        val removed = liveThreads(POLLING_THREAD).single { it.name.endsWith("ONE-PC") }

        rig.binder.removeComputer(saved(UUID_1))

        // The thread is woken from its sleep of one and a half seconds.
        removed.join(PROMPT_MS)
        assertThat(removed.isAlive).isFalse()
        assertThat(liveThreads(POLLING_THREAD).map { it.name })
            .containsExactly("${POLLING_THREAD}TWO-PC")
    }

    @Test
    fun `a host that no poll answered for thirty seconds is unknown again when polling starts`() {
        val host = rig.reachable(UUID_1)
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }
        rig.binder.addComputerBlocking(details)

        // No poll has stamped the host, so it is as old as the clock.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(LONG_AGO_S))
        val first = Updates()
        rig.binder.startPolling(first)
        assertThat(first.on(caller).single().state).isEqualTo(UNKNOWN)
        assertThat(log.lines()).contains("INFO: Timing out polled state for DEN-PC")
        first.awaitOtherThan(caller, ONLINE)
        awaitAsleep()
        rig.binder.stopPolling()

        // The poll above stamped it. Thirty seconds are not too old, thirty seconds and a
        // millisecond are.
        ShadowSystemClock.advanceBy(Duration.ofMillis(TTL_MS))
        val second = Updates()
        rig.binder.startPolling(second)
        assertThat(second.on(caller).single().state).isEqualTo(ONLINE)
        second.awaitOtherThan(caller, ONLINE)
        awaitAsleep()
        rig.binder.stopPolling()

        ShadowSystemClock.advanceBy(Duration.ofMillis(TTL_MS + 1))
        val third = Updates()
        rig.binder.startPolling(third)
        assertThat(third.on(caller).single().state).isEqualTo(UNKNOWN)
        assertThat(log.lines().count { it == "INFO: Timing out polled state for DEN-PC" })
            .isEqualTo(2)
    }

    @Test
    fun `a host that was online is offline after four polls in a row that failed`() {
        val host = rig.host()
        val flaky = Flaky(host, hostInfo(UUID_1))
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }
        rig.binder.addComputerBlocking(details)
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(caller, ONLINE)

        flaky.down = true
        val since = updates.events.size
        updates.awaitState(OFFLINE, since)
        rig.binder.stopPolling()

        assertThat(failures("DEN-PC"))
            .containsExactly(
                "WARNING: DEN-PC is offline (try 0)",
                "WARNING: DEN-PC is offline (try 1)",
                "WARNING: DEN-PC is offline (try 2)",
            )
            .inOrder()
        assertThat(updates.events.drop(since).map { it.state }.filter { it != ONLINE })
            .containsExactly(OFFLINE)
        assertThat(details.state).isEqualTo(OFFLINE)
    }

    @Test
    fun `a host that no poll has answered yet is offline after three polls that failed`() {
        rig.seed(saved(UUID_1, "ONE-PC", manual = rig.gone()))
        rig.start()
        val updates = Updates()

        rig.binder.startPolling(updates)
        updates.awaitState(OFFLINE)
        rig.binder.stopPolling()

        assertThat(failures("ONE-PC"))
            .containsExactly(
                "WARNING: ONE-PC is offline (try 0)",
                "WARNING: ONE-PC is offline (try 1)",
            )
            .inOrder()
        assertThat(updates.on(caller).map { it.state }).containsExactly(UNKNOWN)
        assertThat(updates.events.filter { it.thread !== caller }.map { it.state })
            .containsExactly(OFFLINE)
    }

    @Test
    fun `a poll that answers starts the count of failed polls again`() {
        val host = rig.host()
        val flaky = Flaky(host, hostInfo(UUID_1))
        rig.start()
        rig.binder.addComputerBlocking(ComputerDetails().also { it.manualAddress = host.address })
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(caller, ONLINE)

        flaky.down = true
        awaitUntil("a failed poll") { failures("DEN-PC").size == 1 }
        flaky.down = false
        val seen = updates.events.size
        updates.awaitOtherThan(caller, ONLINE, seen)
        flaky.down = true
        awaitUntil("a second failed poll") { failures("DEN-PC").size == 2 }
        rig.binder.stopPolling()

        assertThat(failures("DEN-PC"))
            .containsExactly(
                "WARNING: DEN-PC is offline (try 0)",
                "WARNING: DEN-PC is offline (try 0)",
            )
        assertThat(updates.events.map { it.state }).doesNotContain(OFFLINE)
    }

    @Test
    fun `a host that was removed from the saved list while it is polled is neither saved nor told`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(caller, ONLINE)

        val db = ComputerDatabaseManager(rig.app)
        try {
            db.deleteComputer(saved(UUID_1))
        } finally {
            db.close()
        }
        val seen = updates.events.size
        awaitUntil("a poll that finds the row gone") { failures("DEN-PC").isNotEmpty() }
        rig.binder.stopPolling()

        assertThat(failures("DEN-PC")).contains("WARNING: DEN-PC is offline (try 0)")
        assertThat(updates.events).hasSize(seen)
        assertThat(rig.savedRows()).isEmpty()
        assertThat(rig.binder.getComputer(UUID_1)?.state).isEqualTo(ONLINE)
    }

    @Test
    fun `waiting for the polls to stop returns at once when no poll runs`() {
        rig.start()

        rig.binder.waitForPollingStopped()
    }

    @Test
    fun `a poll in flight is named, holds the wait for the polls and ends when polling stops`() {
        val host = rig.host()
        val answerNow = rig.gate()
        host.handler = { _ ->
            answerNow.await()
            HostReply.xml(hostInfo(UUID_1))
        }
        rig.seed(saved(UUID_1, "ONE-PC", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        awaitUntil("a poll in flight") { host.serverInfoRequests() > 0 }
        assertThat(liveThreads(PARALLEL_THREAD).map { it.name })
            .containsExactly("${PARALLEL_THREAD}${host.address} - ONE-PC")
        val stopped = AtomicBoolean(false)
        val waiter =
            thread(isDaemon = true) {
                rig.binder.waitForPollingStopped()
                stopped.set(true)
            }

        Thread.sleep(SETTLE_MS)
        assertThat(stopped.get()).isFalse()

        rig.binder.stopPolling()
        waiter.join(WAIT_MS)
        assertThat(stopped.get()).isTrue()
        awaitUntil("the polling thread to end") { liveThreads(POLLING_THREAD).isEmpty() }
        // The host answers now, but nobody is waiting for it any more.
        answerNow.countDown()
        awaitUntil("the poll threads to end") { liveThreads(PARALLEL_THREAD).isEmpty() }
        assertThat(updates.events.filter { it.thread !== caller }).isEmpty()
        assertThat(rig.binder.getComputer(UUID_1)?.state).isEqualTo(UNKNOWN)
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000b1"
        const val UUID_2 = "00000000-0000-0000-0000-0000000000b2"
        const val UUID_3 = "00000000-0000-0000-0000-0000000000b3"
        const val PROMPT_MS = 1_000L
        val UNKNOWN = ComputerDetails.State.UNKNOWN
        val ONLINE = ComputerDetails.State.ONLINE
        val OFFLINE = ComputerDetails.State.OFFLINE
        const val THREE_POLLS = 3
        const val POLL_PERIOD_MS = 1_500L
        const val SLACK_MS = 100L
        const val NANOS_PER_MS = 1_000_000L
        const val LONG_AGO_S = 100L
        const val TTL_MS = 30_000L
        const val SETTLE_MS = 500L
    }
}
