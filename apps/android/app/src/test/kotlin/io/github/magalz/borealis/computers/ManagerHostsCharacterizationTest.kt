package io.github.magalz.borealis.computers

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.PairingManager
import io.github.magalz.borealis.crypto.TestIdentities
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hosts that the inherited `ComputerManagerService` keeps: how one is added (it is polled
 * twice, and kept only when it answers), which of its addresses is used, how a known host is
 * updated, how a host is removed and what happens to its state. The hosts are fakes on the loopback
 * interface.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerHostsCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    private fun reachable(uuid: String, name: String = "DEN-PC") = rig.reachable(uuid, name)

    private fun gone(): AddressTuple = rig.gone()

    @Test
    fun `a host that answers is added, filled in, saved and listed`() {
        val host = reachable(UUID_1)
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isTrue()
        assertThat(details.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(details.uuid).isEqualTo(UUID_1)
        assertThat(details.name).isEqualTo("DEN-PC")
        assertThat(details.activeAddress).isEqualTo(host.address)
        assertThat(details.httpsPort).isEqualTo(host.httpsPort)
        assertThat(details.macAddress).isEqualTo("02:00:00:00:00:01")
        assertThat(details.pairState).isEqualTo(PairingManager.PairState.NOT_PAIRED)
        assertThat(rig.binder.getComputer(UUID_1)).isSameInstanceAs(details)
        val row = rig.savedRows().single()
        assertThat(row.uuid).isEqualTo(UUID_1)
        assertThat(row.name).isEqualTo("DEN-PC")
        assertThat(row.manualAddress).isEqualTo(host.address)
        assertThat(row.macAddress).isEqualTo("02:00:00:00:00:01")
        assertThat(liveThreads(POLLING_THREAD)).isEmpty()
    }

    @Test
    fun `a new host is polled twice, the second time to save it, and the log says so`() {
        val host = reachable(UUID_1)
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        rig.binder.addComputerBlocking(details)

        assertThat(host.serverInfoRequests()).isEqualTo(2)
        assertThat(log.lines())
            .containsAtLeast(
                "INFO: Starting parallel poll for null (null, null, ${host.address}, null)",
                "INFO: Parallel poll for DEN-PC returned address: ${host.address}",
                "INFO: New PC (DEN-PC) is UUID $UUID_1",
            )
    }

    @Test
    fun `a host that does not answer is not added and nothing is saved`() {
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = gone() }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isFalse()
        assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(rig.savedRows()).isEmpty()
        assertThat(log.lines()).contains("INFO: Parallel poll for null returned address: null")
    }

    @Test
    fun `a host that answers with something that is not a host is not added`() {
        val notXml = rig.host().also { it.answer("/serverinfo", HostReply.xml("this is not xml")) }
        val noAnswer = rig.host().also { it.answer("/serverinfo", HostReply.raw(ByteArray(0))) }
        val refused =
            rig.host().also { it.answer("/serverinfo", HostReply.failure("401", "Unauthorized")) }
        rig.start()

        for (host in listOf(notXml, noAnswer, refused)) {
            val details = ComputerDetails().also { it.manualAddress = host.address }

            assertThat(rig.binder.addComputerBlocking(details)).isFalse()
            assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        }
        assertThat(rig.savedRows()).isEmpty()
    }

    @Test
    fun `a host that answers the first poll and not the second is not added and nobody is told`() {
        val host = rig.host()
        val calls = AtomicInteger()
        host.handler = { _ ->
            if (calls.getAndIncrement() == 0) HostReply.xml(hostInfo(UUID_1))
            else HostReply.raw(ByteArray(0))
        }
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val details = ComputerDetails().also { it.manualAddress = host.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isFalse()
        assertThat(details.state).isEqualTo(ComputerDetails.State.OFFLINE)
        assertThat(rig.binder.getComputer(UUID_1)).isNull()
        assertThat(rig.savedRows()).isEmpty()
        assertThat(updates.on(Thread.currentThread())).isEmpty()
    }

    @Test
    fun `the listener hears a new host once when it answers, and nothing when it does not`() {
        val host = reachable(UUID_1)
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()

        rig.binder.addComputerBlocking(ComputerDetails().also { it.manualAddress = gone() })
        assertThat(updates.on(caller)).isEmpty()

        val details = ComputerDetails().also { it.manualAddress = host.address }
        rig.binder.addComputerBlocking(details)

        val heard = updates.on(caller)
        assertThat(heard).hasSize(1)
        assertThat(heard.single().details).isSameInstanceAs(details)
        assertThat(heard.single().state).isEqualTo(ComputerDetails.State.ONLINE)
    }

    @Test
    fun `a host that is already known is updated in place and its saved row is merged`() {
        val host = reachable(UUID_1)
        rig.seed(
            saved(UUID_1, "OLD-NAME", manual = host.address).also {
                it.localAddress = AddressTuple("192.0.2.77", 47989)
                it.macAddress = "02:00:00:00:00:99"
            }
        )
        rig.start()
        val known = checkNotNull(rig.binder.getComputer(UUID_1))
        val details = ComputerDetails().also { it.manualAddress = host.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isTrue()
        assertThat(rig.binder.getComputer(UUID_1)).isSameInstanceAs(known)
        assertThat(known.name).isEqualTo("DEN-PC")
        assertThat(known.state).isEqualTo(ComputerDetails.State.ONLINE)
        val row = rig.savedRows().single()
        assertThat(row.name).isEqualTo("DEN-PC")
        assertThat(row.macAddress).isEqualTo("02:00:00:00:00:01")
        assertThat(row.manualAddress).isEqualTo(host.address)
        assertThat(row.localAddress).isEqualTo(AddressTuple("192.0.2.77", 47989))
    }

    @Test
    fun `a known host that has a pinned certificate is polled the second time over TLS with it`() {
        val host = rig.host()
        host.serves(hostInfo(UUID_1, httpsPort = host.httpsPort, paired = true))
        val certificate = TestIdentities.hostRsa.certificate
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address, cert = certificate))
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isTrue()
        assertThat(host.requests.map { it.scheme }).containsExactly("http", "https").inOrder()
        assertThat(host.requests.last().clientCertificates).isNotEmpty()
        assertThat(details.serverCert).isEqualTo(certificate)
        assertThat(details.pairState).isEqualTo(PairingManager.PairState.PAIRED)
    }

    @Test
    fun `the first address that answers wins, in the order local, manual, remote and IPv6`() {
        val local = reachable(UUID_1)
        val manual = reachable(UUID_1)
        val remote = reachable(UUID_1)
        val ipv6 = reachable(UUID_1)
        rig.start()

        fun details() =
            ComputerDetails().also {
                it.localAddress = local.address
                it.manualAddress = manual.address
                it.remoteAddress = remote.address
                it.ipv6Address = ipv6.address
            }

        val all = details()
        assertThat(rig.binder.addComputerBlocking(all)).isTrue()
        assertThat(all.activeAddress).isSameInstanceAs(all.localAddress)

        local.close()
        val withoutLocal = details()
        assertThat(rig.binder.addComputerBlocking(withoutLocal)).isTrue()
        assertThat(withoutLocal.activeAddress).isEqualTo(manual.address)

        manual.close()
        val withoutManual = details()
        assertThat(rig.binder.addComputerBlocking(withoutManual)).isTrue()
        assertThat(withoutManual.activeAddress).isEqualTo(remote.address)

        remote.close()
        val onlyIpv6 = details()
        assertThat(rig.binder.addComputerBlocking(onlyIpv6)).isTrue()
        assertThat(onlyIpv6.activeAddress).isEqualTo(ipv6.address)

        ipv6.close()
        val none = details()
        assertThat(rig.binder.addComputerBlocking(none)).isFalse()
    }

    @Test
    fun `an address that two fields share is polled once for each poll`() {
        val host = reachable(UUID_1)
        rig.start()
        val details =
            ComputerDetails().also {
                it.localAddress = host.address
                it.manualAddress = host.address
                it.remoteAddress = host.address
            }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(host.serverInfoRequests()).isEqualTo(2)
        assertThat(details.activeAddress).isSameInstanceAs(details.localAddress)
    }

    @Test
    fun `an address that answers as another host than the expected one is passed over`() {
        val wrong = reachable(UUID_2, "WRONG-PC")
        val right = reachable(UUID_1, "RIGHT-PC")
        rig.start()
        val details =
            ComputerDetails().also {
                it.uuid = UUID_1
                it.localAddress = wrong.address
                it.manualAddress = right.address
            }

        assertThat(rig.binder.addComputerBlocking(details)).isTrue()

        assertThat(details.activeAddress).isEqualTo(right.address)
        assertThat(details.name).isEqualTo("RIGHT-PC")
        assertThat(log.lines()).contains("INFO: Polling returned the wrong PC!")
    }

    @Test
    fun `removing a host deletes its row and drops it from the list, by its id`() {
        rig.seed(saved(UUID_1, "ONE"), saved(UUID_2, "TWO"))
        rig.start()

        rig.binder.removeComputer(saved(UUID_1, "anything else"))

        assertThat(rig.binder.getComputer(UUID_1)).isNull()
        assertThat(rig.binder.getComputer(UUID_2)).isNotNull()
        assertThat(rig.savedRows().map { it.uuid }).containsExactly(UUID_2)
    }

    @Test
    fun `removing a host that is not in the list still deletes its row`() {
        rig.start()
        rig.seed(saved(UUID_1, "ONE"))
        assertThat(rig.binder.getComputer(UUID_1)).isNull()

        rig.binder.removeComputer(saved(UUID_1))

        assertThat(rig.savedRows()).isEmpty()
    }

    @Test
    fun `removing a host tells nobody and a removed host can be added again`() {
        val host = reachable(UUID_1)
        rig.seed(saved(UUID_1, "ONE", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()
        val before = updates.on(caller).size

        rig.binder.removeComputer(saved(UUID_1))

        assertThat(updates.on(caller)).hasSize(before)
        val details = ComputerDetails().also { it.manualAddress = host.address }
        assertThat(rig.binder.addComputerBlocking(details)).isTrue()
        assertThat(rig.binder.getComputer(UUID_1)).isSameInstanceAs(details)
    }

    @Test
    fun `invalidating a host makes its state unknown and tells nobody`() {
        val host = reachable(UUID_1)
        val other = reachable(UUID_2)
        rig.start()
        val updates = Updates()
        val caller = Thread.currentThread()
        rig.binder.startPolling(updates)
        val one = ComputerDetails().also { it.manualAddress = host.address }
        val two = ComputerDetails().also { it.manualAddress = other.address }
        rig.binder.addComputerBlocking(one)
        rig.binder.addComputerBlocking(two)
        val before = updates.on(caller).size

        rig.binder.invalidateStateForComputer(UUID_1)

        assertThat(one.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(two.state).isEqualTo(ComputerDetails.State.ONLINE)
        assertThat(updates.on(caller)).hasSize(before)
        rig.binder.invalidateStateForComputer("not-a-host")
    }

    @Test
    fun `invalidating a host waits for the poll that is running, so the poll cannot undo it`() {
        val host = rig.host()
        host.serves(hostInfo(UUID_1))
        rig.start()
        val details = ComputerDetails().also { it.manualAddress = host.address }
        rig.binder.addComputerBlocking(details)
        val answerNow = rig.gate()
        val polls = host.serverInfoRequests()
        host.handler = { _ ->
            answerNow.await()
            HostReply.xml(hostInfo(UUID_1))
        }
        val updates = Updates()
        rig.binder.startPolling(updates)
        awaitUntil("a poll in flight") { host.serverInfoRequests() > polls }
        val invalidated = AtomicBoolean(false)
        val waiter =
            thread(isDaemon = true) {
                rig.binder.invalidateStateForComputer(UUID_1)
                invalidated.set(true)
            }

        Thread.sleep(SETTLE_MS)
        assertThat(invalidated.get()).isFalse()

        answerNow.countDown()
        waiter.join(WAIT_MS)
        rig.binder.stopPolling()

        assertThat(invalidated.get()).isTrue()
        assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000a1"
        const val UUID_2 = "00000000-0000-0000-0000-0000000000a2"
        const val SETTLE_MS = 500L
    }
}
