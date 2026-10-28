package io.github.magalz.borealis.library

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComputerManagerConnectionTest {
    private val binder = FakeCmsBinder()
    private val connector = FakeCmsConnector(binder)

    private fun TestScope.newConnection() =
        ComputerManagerConnection(connector, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `polling starts once for two collectors and stops when the last one leaves`() = runTest {
        val connection = newConnection()

        val first =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        val second =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }

        assertThat(binder.events).containsExactly("startPolling")
        first.cancelAndJoin()
        assertThat(binder.events).containsExactly("startPolling")
        second.cancelAndJoin()
        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `the service is bound once, on first use`() = runTest {
        val connection = newConnection()
        assertThat(connector.connects).isEqualTo(0)

        connection.binder()
        connection.binder()

        assertThat(connector.connects).isEqualTo(1)
        assertThat(connection.connectedBinder).isSameInstanceAs(binder)
    }

    @Test
    fun `a collector sees the hosts that the service reports`() = runTest {
        val connection = newConnection()
        val seen = mutableListOf<Set<String>>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect { seen += it.keys }
            }

        binder.emit(details(uuid = "a"))
        binder.emit(details(uuid = "b"))

        assertThat(seen.last()).containsExactly("a", "b")
        job.cancelAndJoin()
    }

    @Test
    fun `updates are copies, so changing the service's object changes nothing already emitted`() =
        runTest {
            val connection = newConnection()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    connection.hostUpdates().collect {}
                }
            val live = details(uuid = "a", name = "Before")

            binder.emit(live)
            live.name = "After"
            live.state = ComputerDetails.State.OFFLINE

            val emitted = connection.updates.value.getValue("a")
            assertThat(emitted).isNotSameInstanceAs(live)
            assertThat(emitted.name).isEqualTo("Before")
            assertThat(emitted.state).isEqualTo(ComputerDetails.State.ONLINE)
            job.cancelAndJoin()
        }

    @Test
    fun `the app list poller runs while the apps of a host are collected`() = runTest {
        val connection = newConnection()
        val live = details(uuid = "a")
        binder.computers["a"] = live

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.appListUpdates("a").collect {}
            }

        assertThat(binder.events).containsExactly("startPolling")
        val poller = binder.pollers.single()
        assertThat(poller.computer).isSameInstanceAs(live)
        assertThat(poller.started).isEqualTo(1)
        assertThat(poller.stopped).isEqualTo(0)

        job.cancelAndJoin()

        assertThat(poller.stopped).isEqualTo(1)
        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `two collectors of one host share a poller that stops with the last of them`() = runTest {
        val connection = newConnection()
        binder.computers["a"] = details(uuid = "a")
        val first =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.appListUpdates("a").collect {}
            }
        val second =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.appListUpdates("a").collect {}
            }

        val poller = binder.pollers.single()
        first.cancelAndJoin()
        assertThat(poller.stopped).isEqualTo(0)
        second.cancelAndJoin()
        assertThat(poller.stopped).isEqualTo(1)
    }

    @Test
    fun `the apps of an unknown host are reported as null and no poller is started`() = runTest {
        val connection = newConnection()

        val first =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                assertThat(connection.appListUpdates("missing").first()).isNull()
            }

        first.join()
        assertThat(binder.pollers).isEmpty()
    }

    @Test
    fun `pausing stops polling, waits, and starts it again afterwards`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }

        val result = connection.pausePolling {
            binder.record("inside")
            42
        }

        assertThat(result).isEqualTo(42)
        assertThat(binder.events)
            .containsExactly(
                "startPolling",
                "stopPolling",
                "waitForPollingStopped",
                "inside",
                "startPolling",
            )
            .inOrder()
        job.cancelAndJoin()
    }

    @Test
    fun `pausing without collectors never starts polling`() = runTest {
        val connection = newConnection()

        connection.pausePolling { binder.record("inside") }

        assertThat(binder.events).doesNotContain("startPolling")
    }

    @Test
    fun `polling resumes even when the paused block fails`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }

        try {
            connection.pausePolling<Unit> { error("boom") }
            throw AssertionError("the failure was swallowed")
        } catch (expected: IllegalStateException) {
            assertThat(expected).hasMessageThat().isEqualTo("boom")
        }

        assertThat(binder.events.last()).isEqualTo("startPolling")
        job.cancelAndJoin()
    }

    @Test
    fun `a collector that leaves during a pause does not restart polling afterwards`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }

        connection.pausePolling { job.cancelAndJoin() }

        assertThat(binder.events)
            .containsExactly("startPolling", "stopPolling", "waitForPollingStopped")
            .inOrder()
    }

    @Test
    fun `lookup answers from the last update, then from the service`() = runTest {
        val connection = newConnection()
        binder.computers["live"] = details(uuid = "live", name = "Live")
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        binder.emit(details(uuid = "seen", name = "Seen"))

        assertThat(connection.lookup("seen")?.name).isEqualTo("Seen")
        val fromService = connection.lookup("live")
        assertThat(fromService?.name).isEqualTo("Live")
        assertThat(fromService).isNotSameInstanceAs(binder.computers["live"])
        assertThat(connection.lookup("none")).isNull()
        job.cancelAndJoin()
    }

    @Test
    fun `adding a host that answers publishes it, and a host that does not answer is not published`() =
        runTest {
            val connection = newConnection()
            binder.onAdd = {
                it.uuid = "new"
                it.name = "New PC"
                true
            }

            assertThat(connection.addComputer(ComputerDetails())).isTrue()
            assertThat(connection.updates.value.getValue("new").name).isEqualTo("New PC")

            binder.onAdd = { false }
            assertThat(connection.addComputer(ComputerDetails())).isFalse()
            assertThat(connection.updates.value.keys).containsExactly("new")
        }

    @Test
    fun `removing a host removes it from the service and from the updates`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        val live = details(uuid = "a")
        binder.emit(live)

        connection.remove(live)

        assertThat(binder.removed).containsExactly(live)
        assertThat(connection.updates.value).isEmpty()
        job.cancelAndJoin()
    }

    @Test
    fun `pinning a certificate stores it on the service's object and invalidates its state`() =
        runTest {
            val connection = newConnection()
            val live = details(uuid = "a")
            binder.computers["a"] = live
            val cert = testCertificate()

            connection.pinCertificate("a", cert)

            assertThat(live.serverCert).isEqualTo(cert)
            assertThat(binder.invalidated).containsExactly("a")
        }

    @Test
    fun `a collector cancelled right after polling started still stops polling`() = runTest {
        val connection = newConnection()
        lateinit var job: Job
        binder.onStartPolling = { job.cancel() }
        job =
            launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.LAZY) {
                connection.hostUpdates().collect {}
            }

        job.start()
        job.join()

        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `an app list collector cancelled right after its poller started stops both`() = runTest {
        val connection = newConnection()
        binder.computers["a"] = details(uuid = "a")
        lateinit var job: Job
        binder.onCreatePoller = { job.cancel() }
        job =
            launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.LAZY) {
                connection.appListUpdates("a").collect {}
            }

        job.start()
        job.join()

        assertThat(binder.pollers.single().stopped).isEqualTo(1)
        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `a pause cancelled right after polling stopped lets polling resume`() = runTest {
        val connection = newConnection()
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) { connection.hostUpdates().collect {} }
        lateinit var paused: Job
        binder.onStopPolling = { paused.cancel() }
        paused =
            launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.LAZY) {
                connection.pausePolling { binder.record("inside") }
            }

        paused.start()
        paused.join()

        assertThat(binder.events).doesNotContain("inside")
        assertThat(binder.events.last()).isEqualTo("startPolling")
        collector.cancelAndJoin()
    }

    @Test
    fun `a certificate that was pinned makes the host paired and its state unknown before any poll`() =
        runTest {
            val connection = newConnection()
            binder.computers["a"] =
                details(uuid = "a")
                    .reported(
                        pairState = PairState.NOT_PAIRED,
                        state = ComputerDetails.State.ONLINE,
                    )
            val cert = testCertificate()

            connection.pinCertificate("a", cert)

            val published = connection.updates.value.getValue("a")
            assertThat(published.pairState).isEqualTo(PairState.PAIRED)
            assertThat(published.state).isEqualTo(ComputerDetails.State.UNKNOWN)
            assertThat(published.serverCert).isEqualTo(cert)
            assertThat(published).isNotSameInstanceAs(binder.computers["a"])
        }

    @Test
    fun `the app list of one host never shows up on another host`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }

        binder.emit(details(uuid = "a", rawAppList = APP_LIST))
        binder.emit(details(uuid = "b", rawAppList = null))

        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)
        assertThat(connection.updates.value.getValue("b").rawAppList).isNull()
        job.cancelAndJoin()
    }

    @Test
    fun `a host that was removed and added again has no app list until it gets one`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        val live = details(uuid = "a", rawAppList = APP_LIST)
        binder.emit(live)
        connection.remove(live)

        binder.emit(details(uuid = "a", rawAppList = null))
        assertThat(connection.updates.value.getValue("a").rawAppList).isNull()

        binder.emit(details(uuid = "a", rawAppList = APP_LIST))
        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)
        job.cancelAndJoin()
    }

    @Test
    fun `a new app list replaces the one that was kept`() = runTest {
        val connection = newConnection()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        val other = APP_LIST.replace("Desktop", "Games")

        binder.emit(details(uuid = "a", rawAppList = APP_LIST))
        binder.emit(details(uuid = "a", rawAppList = null))
        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)

        binder.emit(details(uuid = "a", rawAppList = other))

        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(other)
        job.cancelAndJoin()
    }

    @Test
    fun `pinning a certificate keeps the app list that was already received`() = runTest {
        val connection = newConnection()
        val live = details(uuid = "a", rawAppList = APP_LIST)
        binder.computers["a"] = live
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                connection.hostUpdates().collect {}
            }
        // The service's details lost the list to a host poll, but it was received before.
        live.rawAppList = null
        binder.emit(live)

        connection.pinCertificate("a", testCertificate())

        assertThat(connection.updates.value.getValue("a").rawAppList).isEqualTo(APP_LIST)
        job.cancelAndJoin()
    }

    @Test
    fun `a host that the service does not know is not published when a certificate is pinned`() =
        runTest {
            val connection = newConnection()

            connection.pinCertificate("nobody", testCertificate())

            assertThat(connection.updates.value).isEmpty()
        }
}
