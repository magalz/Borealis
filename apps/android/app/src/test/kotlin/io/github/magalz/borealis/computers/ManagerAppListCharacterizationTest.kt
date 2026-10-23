package io.github.magalz.borealis.computers

import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerManagerService.ApplistPoller
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.discovery.LimeLogRecords
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app list poller of the inherited `ComputerManagerService`: when it asks a host for its apps,
 * what it keeps in the cache, what it tells the listener, how it survives a host that fails and
 * how it waits for the poll of the host. The host is a fake on the loopback interface, polled
 * over TLS with its pinned certificate. A test wakes the poller with `pollNow` when it is idle,
 * so that it never waits for the two seconds of the retry period.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerAppListCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogRecords()
    @get:Rule(order = 1) val rig = ManagerRule()

    private fun apps(vararg titles: String): String =
        titles.withIndex().joinToString("") { (index, title) ->
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>$title</AppTitle>" +
                "<ID>${index + 1}</ID></App>"
        }

    private fun host(list: String): NvFakeHost =
        rig.reachable(UUID_1).also { it.answer("/applist", HostReply.root(list), "https") }

    /** A host as the library has it once it is paired: online, with its certificate. */
    private fun paired(host: NvFakeHost) =
        ComputerDetails().also {
            it.uuid = UUID_1
            it.name = "DEN-PC"
            it.state = ComputerDetails.State.ONLINE
            it.pairState = PairingManager.PairState.PAIRED
            it.activeAddress = host.address
            it.httpsPort = host.httpsPort
            it.serverCert = TestIdentities.hostRsa.certificate
        }

    private fun cacheFile() = File(rig.app.cacheDir, "applist/$UUID_1")

    private fun appListRequests(host: NvFakeHost) = host.requests.count { it.path == "/applist" }

    /** Waits until the poller is idle, waiting for its period or for a wake-up. */
    private fun awaitIdle() =
        awaitUntil("the app list thread to wait") {
            liveThreads(APP_LIST_THREAD).singleOrNull()?.state == Thread.State.TIMED_WAITING
        }

    /** Wakes the poller once it is idle. */
    private fun ApplistPoller.nudge() {
        awaitIdle()
        pollNow()
    }

    @Test
    fun `an app list is fetched over TLS, cached as it came, kept on the host and told to the listener`() {
        val list = apps("Desktop", "Steam")
        val host = host(list)
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)

        rig.appListPoller(computer).start()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)

        val raw = host.requests.first { it.path == "/applist" }
        assertThat(raw.scheme).isEqualTo("https")
        assertThat(raw.clientCertificates).isNotEmpty()
        assertThat(computer.rawAppList)
            .isEqualTo("""<root status_code="200">$list</root>""")
        assertThat(cacheFile().readText()).isEqualTo(computer.rawAppList)
        val told = updates.events.single { it.thread !== Thread.currentThread() }
        assertThat(told.details).isSameInstanceAs(computer)
        assertThat(told.rawAppList).isEqualTo(computer.rawAppList)
        assertThat(told.thread.name).isEqualTo("${APP_LIST_THREAD}DEN-PC")
    }

    @Test
    fun `a host that is not online or not paired is reported again instead of polled`() {
        val host = host(apps("Desktop"))
        val computer = paired(host).also { it.state = ComputerDetails.State.OFFLINE }
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        poller.start()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.OFFLINE)
        awaitIdle()
        // Left alone, the poller reports again only after its retry period of two seconds.
        Thread.sleep(RETRY_PERIOD_MS / 2)
        assertThat(updates.events.filter { it.thread !== Thread.currentThread() }).hasSize(1)
        // A wake-up makes the poller poll at once, not after that period.
        poller.nudge()
        awaitUntil("a second report", PROMPT_MS) {
            updates.events.count { it.thread !== Thread.currentThread() } == 2
        }
        awaitIdle()

        assertThat(updates.events.filter { it.thread !== Thread.currentThread() }).hasSize(2)
        assertThat(appListRequests(host)).isEqualTo(0)

        computer.state = ComputerDetails.State.ONLINE
        computer.pairState = PairingManager.PairState.NOT_PAIRED
        poller.nudge()
        awaitUntil("a third report", PROMPT_MS) {
            updates.events.count { it.thread !== Thread.currentThread() } == 3
        }
        assertThat(appListRequests(host)).isEqualTo(0)
    }

    @Test
    fun `stopping a poller that waits for its next poll ends its thread at once`() {
        val host = host(apps("Desktop"))
        val computer = paired(host).also { it.state = ComputerDetails.State.OFFLINE }
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)
        poller.start()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.OFFLINE)
        awaitIdle()
        val thread = liveThreads(APP_LIST_THREAD).single()

        poller.stop()

        // The thread is woken from its wait of two seconds.
        thread.join(PROMPT_MS)
        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `a host without an id or without an address is skipped and polled once it has them`() {
        val list = apps("Desktop")
        val host = host(list)
        val computer = paired(host).also { it.uuid = null }
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        poller.start()
        awaitIdle()
        assertThat(appListRequests(host)).isEqualTo(0)
        assertThat(updates.events).isEmpty()

        computer.uuid = UUID_1
        computer.activeAddress = null
        poller.nudge()
        awaitIdle()
        assertThat(appListRequests(host)).isEqualTo(0)
        assertThat(computer.rawAppList).isNull()

        computer.activeAddress = host.address
        poller.nudge()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        assertThat(appListRequests(host)).isEqualTo(1)
        assertThat(cacheFile().exists()).isTrue()
    }

    @Test
    fun `an empty app list is believed only when it comes three times in a row`() {
        val host = host("")
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        poller.start()
        awaitUntil("the first empty list") { appListRequests(host) == 1 }
        awaitIdle()
        assertThat(computer.rawAppList).isNull()
        assertThat(cacheFile().exists()).isFalse()

        poller.nudge()
        awaitUntil("the second empty list") { appListRequests(host) == 2 }
        awaitIdle()
        assertThat(computer.rawAppList).isNull()
        assertThat(cacheFile().exists()).isFalse()
        assertThat(updates.events).isEmpty()

        poller.nudge()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        assertThat(appListRequests(host)).isEqualTo(3)
        assertThat(computer.rawAppList).isEqualTo("""<root status_code="200"></root>""")
        assertThat(cacheFile().readText()).isEqualTo(computer.rawAppList)
        assertThat(log.lines().count { it == "WARNING: Empty app list received from $UUID_1" })
            .isEqualTo(3)
    }

    @Test
    fun `a cache that cannot be written does not keep the app list from the host`() {
        val list = apps("Desktop")
        val host = host(list)
        val computer = paired(host)
        // A file where the folder of the cache should be.
        File(rig.app.cacheDir, "applist").also {
            it.parentFile?.mkdirs()
            it.writeText("not a folder")
        }
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)

        rig.appListPoller(computer).start()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)

        assertThat(computer.rawAppList).isEqualTo("""<root status_code="200">$list</root>""")
        assertThat(cacheFile().exists()).isFalse()
    }

    @Test
    fun `an empty list that follows a list with apps starts the count of empty lists again`() {
        val list = apps("Desktop")
        val host = host(list)
        val mode = AtomicReference("empty")
        host.handler = { request ->
            when {
                request.path != "/applist" -> HostReply.xml(hostInfo(UUID_1))
                mode.get() == "empty" -> HostReply.root("")
                else -> HostReply.root(list)
            }
        }
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        poller.start()
        awaitUntil("the first empty list") { appListRequests(host) == 1 }
        awaitIdle()
        mode.set("apps")
        poller.nudge()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        awaitIdle()
        mode.set("empty")
        poller.nudge()
        awaitUntil("the second empty list") { appListRequests(host) == 3 }
        awaitIdle()
        poller.nudge()
        awaitUntil("the third empty list") { appListRequests(host) == 4 }
        awaitIdle()

        // Three empty lists were asked for in all, but only two in a row.
        val expected = """<root status_code="200">$list</root>"""
        assertThat(computer.rawAppList).isEqualTo(expected)
        assertThat(cacheFile().readText()).isEqualTo(expected)
    }

    @Test
    fun `a poller that has an app list waits for its long period, not for the retry period`() {
        val host = host(apps("Desktop"))
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        rig.appListPoller(computer).start()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        awaitIdle()

        // A poller that has no list yet asks again after two seconds.
        Thread.sleep(RETRY_PERIOD_MS + SETTLE_MS)

        assertThat(appListRequests(host)).isEqualTo(1)
    }

    @Test
    fun `a host that fails or answers badly does not end the poller`() {
        val list = apps("Desktop")
        val host = host(list)
        val mode = AtomicReference("close")
        host.handler = { request ->
            when {
                request.path != "/applist" -> HostReply.xml(hostInfo(UUID_1))
                mode.get() == "close" -> HostReply.raw(ByteArray(0))
                mode.get() == "garbage" -> HostReply.xml("this is not xml")
                else -> HostReply.root(list)
            }
        }
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        poller.start()
        awaitUntil("a failed request") { appListRequests(host) >= 1 }
        awaitIdle()
        val failed = appListRequests(host)
        mode.set("garbage")
        poller.nudge()
        awaitUntil("an answer that is not XML") { appListRequests(host) > failed }
        awaitIdle()
        assertThat(computer.rawAppList).isNull()
        assertThat(updates.events).isEmpty()
        mode.set("good")
        poller.nudge()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)

        assertThat(computer.rawAppList).isEqualTo("""<root status_code="200">$list</root>""")
        assertThat(cacheFile().readText()).isEqualTo(computer.rawAppList)
    }

    @Test
    fun `the request for the app list waits for the poll of the host that holds the network lock`() {
        val list = apps("Desktop")
        val host = host(list)
        val answerNow = rig.gate()
        host.handler = { request ->
            if (request.path == "/serverinfo") {
                answerNow.await()
                HostReply.xml(hostInfo(UUID_1))
            } else {
                HostReply.root(list)
            }
        }
        rig.seed(
            ComputerDetails().also {
                it.uuid = UUID_1
                it.name = "DEN-PC"
                it.manualAddress = host.address
                it.serverCert = TestIdentities.hostRsa.certificate
            }
        )
        rig.start()
        rig.binder.startPolling(Updates())
        awaitUntil("the poll of the host") { host.serverInfoRequests() == 1 }
        val live = checkNotNull(rig.binder.getComputer(UUID_1))
        live.state = ComputerDetails.State.ONLINE
        live.pairState = PairingManager.PairState.PAIRED
        live.activeAddress = host.address
        live.httpsPort = host.httpsPort

        rig.appListPoller(live).start()
        Thread.sleep(SETTLE_MS)
        assertThat(appListRequests(host)).isEqualTo(0)

        // Stopping the polling ends the poll that holds the lock.
        rig.binder.stopPolling()
        awaitUntil("the app list request") { appListRequests(host) == 1 }
        awaitUntil("the app list") { live.rawAppList != null }
    }

    @Test
    fun `stopping the poller ends its thread and a late answer tells nobody`() {
        val list = apps("Desktop")
        val host = host(list)
        val answerNow = rig.gate()
        host.handler = { request ->
            if (request.path == "/applist") answerNow.await()
            HostReply.root(list)
        }
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)
        poller.start()
        awaitUntil("the request in flight") { appListRequests(host) == 1 }
        assertThat(liveThreads(APP_LIST_THREAD)).hasSize(1)

        poller.stop()
        answerNow.countDown()

        awaitUntil("the app list thread to end") { liveThreads(APP_LIST_THREAD).isEmpty() }
        assertThat(updates.events).isEmpty()
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000d1"
        const val SETTLE_MS = 500L
        const val RETRY_PERIOD_MS = 2_000L
        const val PROMPT_MS = 1_000L
    }
}
