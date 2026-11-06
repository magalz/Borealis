package io.github.magalz.borealis.computers

import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerManagerService.ApplistPoller
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.NvFakeHost
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two edges of the app list poller of the inherited `ComputerManagerService` that the tests of
 * `ManagerAppListCharacterizationTest` leave out: a poller that runs while nobody listens, and an
 * answer to the request for the app list that is not a document (an answer with no body ends in a
 * parse error, so the poller never reaches its log line for an app list that has no text).
 *
 * Characterization: these tests pass on the code before the refactor of the poller (BORE-76).
 */
@RunWith(RobolectricTestRunner::class)
class ManagerAppListEdgesCharacterizationTest {
    @get:Rule val rig = ManagerRule()

    private fun host(reply: HostReply): NvFakeHost =
        rig.reachable(UUID_1).also { it.answer("/applist", reply, "https") }

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

    private fun appListRequests(host: NvFakeHost) = host.requests.count { it.path == "/applist" }

    @Test
    fun `a poller that runs after polling stopped keeps the app list and tells nobody`() {
        val list =
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>1</ID></App>"
        val host = host(HostReply.root(list))
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        // The listener is forgotten.
        rig.binder.stopPolling()

        rig.appListPoller(computer).start()

        awaitUntil("the app list") { computer.rawAppList != null }
        assertThat(computer.rawAppList).isEqualTo("""<root status_code="200">$list</root>""")
        assertThat(cacheFile().readText()).isEqualTo(computer.rawAppList)
        assertThat(updates.events).isEmpty()
    }

    @Test
    fun `an answer that is not a document is no app list and does not count as an empty list`() {
        val host = host(HostReply.xml(""))
        val mode = AtomicReference("no body")
        host.handler = { request ->
            when {
                request.path != "/applist" -> HostReply.xml(hostInfo(UUID_1))
                mode.get() == "no body" -> HostReply.xml("")
                else -> HostReply.root("")
            }
        }
        val computer = paired(host)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val poller = rig.appListPoller(computer)

        // An answer with no body ends in a parse error, as many times as it comes.
        poller.start()
        awaitUntil("request 1 for the app list") { appListRequests(host) == 1 }
        for (request in 2..EMPTY_LIST_THRESHOLD + 1) askAgain(poller, host, request)
        awaitIdle()
        assertThat(computer.rawAppList).isNull()
        assertThat(cacheFile().exists()).isFalse()
        assertThat(updates.events).isEmpty()

        // Those answers did not count: an empty list is believed at the third, not before.
        mode.set("empty")
        askAgain(poller, host, EMPTY_LIST_THRESHOLD + 2)
        askAgain(poller, host, EMPTY_LIST_THRESHOLD + 3)
        awaitIdle()
        assertThat(computer.rawAppList).isNull()
        assertThat(updates.events).isEmpty()
        poller.nudge()
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        assertThat(appListRequests(host)).isEqualTo(EMPTY_LIST_THRESHOLD + 4)
        assertThat(computer.rawAppList).isEqualTo("""<root status_code="200"></root>""")
        assertThat(cacheFile().readText()).isEqualTo(computer.rawAppList)
    }

    private fun cacheFile() = File(rig.app.cacheDir, "applist/$UUID_1")

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

    /** Wakes the poller and waits for its request number [request]. */
    private fun askAgain(poller: ApplistPoller, host: NvFakeHost, request: Int) {
        poller.nudge()
        awaitUntil("request $request for the app list") { appListRequests(host) == request }
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000e2"

        // The number of empty lists in a row after which the poller believes an empty list.
        const val EMPTY_LIST_THRESHOLD = 3
    }
}
