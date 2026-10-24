package io.github.magalz.borealis.computers

import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.discovery.LimeLogRecords
import io.github.magalz.borealis.nvhttp.HostReply
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The reference to the host list database that every poll takes and gives back. The service keeps
 * the database open while a reference is out and closes it when the last one is given back, which
 * `onDestroy` does for the first one. A reference that a poll keeps would leave the database open
 * after the service is destroyed: a host that answers is then polled a second time and saved, as
 * if the service were alive, while with every reference given back the second poll never starts
 * (see [destroyAndProbe]). Each test ends a poll on one of the ways out of `runPoll` and
 * `removeComputer`.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerDatabaseReferenceCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogRecords()
    @get:Rule(order = 1) val rig = ManagerRule()

    /** Waits until the polling thread has ended its poll and sleeps until the next one. */
    private fun awaitAsleep() =
        awaitUntil("the polling thread to sleep") {
            liveThreads(POLLING_THREAD).singleOrNull()?.state == Thread.State.TIMED_WAITING
        }

    /** Stops the polling and waits for its threads, so that no poll is left when it is destroyed. */
    private fun stopPollingAndWait() {
        rig.binder.stopPolling()
        awaitUntil("the polling thread to end") { liveThreads(POLLING_THREAD).isEmpty() }
    }

    /**
     * Destroys the service, then adds a host that answers. With the database closed the service
     * polls it once and neither polls it a second time nor saves it; with a reference still out it
     * does both.
     */
    private fun destroyAndProbe() {
        val probe = rig.reachable(PROBE_UUID, "PROBE-PC")
        rig.destroy()
        val details = ComputerDetails().also { it.manualAddress = probe.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isTrue()
        assertThat(probe.serverInfoRequests()).isEqualTo(1)
        assertThat(rig.savedRows().map { it.uuid }).doesNotContain(PROBE_UUID)
    }

    @Test
    fun `a poll that failed below the threshold gives its reference back`() {
        rig.seed(saved(UUID_1, "ONE-PC", manual = rig.gone()))
        rig.start()
        rig.binder.startPolling(Updates())
        awaitUntil("a failed poll") {
            log.lines().contains("WARNING: ONE-PC is offline (try 0)")
        }
        awaitAsleep()
        stopPollingAndWait()

        destroyAndProbe()
    }

    @Test
    fun `a poll that is interrupted gives its reference back`() {
        val host = rig.host()
        val answerNow = rig.gate()
        host.handler = { _ ->
            answerNow.await()
            HostReply.xml(hostInfo(UUID_1))
        }
        rig.seed(saved(UUID_1, "ONE-PC", manual = host.address))
        rig.start()
        rig.binder.startPolling(Updates())
        awaitUntil("a poll in flight") { host.serverInfoRequests() > 0 }

        stopPollingAndWait()

        destroyAndProbe()
    }

    @Test
    fun `a poll that finds the host gone from the saved list gives its reference back`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        val db = ComputerDatabaseManager(rig.app)
        try {
            db.deleteComputer(saved(UUID_1))
        } finally {
            db.close()
        }
        awaitUntil("a poll that finds the row gone") {
            log.lines().contains("WARNING: DEN-PC is offline (try 0)")
        }
        awaitAsleep()
        stopPollingAndWait()

        destroyAndProbe()
    }

    @Test
    fun `a poll that answered gives its reference back`() {
        val host = rig.reachable(UUID_1, "DEN-PC")
        rig.seed(saved(UUID_1, "DEN-PC", manual = host.address))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        updates.awaitOtherThan(Thread.currentThread(), ComputerDetails.State.ONLINE)
        awaitAsleep()
        stopPollingAndWait()

        destroyAndProbe()
    }

    @Test
    fun `removing a host gives its reference back`() {
        rig.seed(saved(UUID_1, "ONE-PC"))
        rig.start()

        rig.binder.removeComputer(saved(UUID_1))

        destroyAndProbe()
    }

    @Test
    fun `creating the service gives back the reference that it took to read the saved hosts`() {
        rig.seed(saved(UUID_1, "ONE-PC"))
        rig.start()

        destroyAndProbe()
    }

    private companion object {
        const val UUID_1 = "00000000-0000-0000-0000-0000000000e1"
        const val PROBE_UUID = "00000000-0000-0000-0000-0000000000e9"
    }
}
