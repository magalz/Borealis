package io.github.magalz.borealis.computers

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.testing.LimeLogCapture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The reference to the host list database that every poll takes and gives back. The service keeps
 * the database open while a reference is out and closes it when the last one is given back, which
 * `onDestroy` does for the first one. A reference that a poll keeps would leave the database open
 * after the service is destroyed: a host that answers is then polled a second time and saved, as if
 * the service were alive, while with every reference given back the second poll never starts (see
 * [destroyAndProbe]). Each test ends a poll on one of the ways out of `runPoll` and
 * `removeComputer`.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion, except the two
 * that throw out of a poll (S12-13) and the two that throw out of `removeComputer` (S12-16), which
 * pin the fixed behaviour.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerDatabaseReferenceCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    /** Waits until the polling thread has ended its poll and sleeps until the next one. */
    private fun awaitAsleep() =
        awaitUntil("the polling thread to sleep") {
            liveThreads(POLLING_THREAD).singleOrNull()?.state == Thread.State.TIMED_WAITING
        }

    /**
     * Stops the polling and waits for its threads, so that no poll is left when it is destroyed.
     */
    private fun stopPollingAndWait() {
        rig.binder.stopPolling()
        awaitUntil("the polling thread to end") { liveThreads(POLLING_THREAD).isEmpty() }
    }

    /**
     * Adds a host that answers and checks that it is saved. A poll that gave its reference back
     * twice would leave the service without the database, and the host would not be saved.
     */
    private fun saveAnotherHost() {
        val other = rig.reachable(UUID_2, "TWO-PC")

        val added =
            rig.binder.addComputerBlocking(
                ComputerDetails().also { it.manualAddress = other.address }
            )

        assertThat(added).isTrue()
        assertThat(rig.savedRows().map { it.uuid }).contains(UUID_2)
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
    fun `a poll whose lookup of the saved host throws gives its reference back`() {
        rig.seedRow(BAD_UUID, "BAD-PC", "not json")
        rig.start()
        val bad = rig.reachable(BAD_UUID, "BAD-PC")

        val failure = runCatching {
            rig.binder.addComputerBlocking(
                ComputerDetails().also { it.manualAddress = bad.address }
            )
        }
            .exceptionOrNull()

        // The host answers and the lookup of its saved row, which cannot be read, throws.
        assertThat(failure).isInstanceOf(RuntimeException::class.java)
        saveAnotherHost()
        destroyAndProbe()
    }

    @Test
    fun `a poll whose listener throws gives its reference back`() {
        rig.start()
        val boom = IllegalStateException("the listener failed")
        rig.binder.startPolling { throw boom }
        val host = rig.reachable(UUID_1, "ONE-PC")

        val failure = runCatching {
            rig.binder.addComputerBlocking(
                ComputerDetails().also { it.manualAddress = host.address }
            )
        }
            .exceptionOrNull()
        rig.binder.stopPolling()

        assertThat(failure).isSameInstanceAs(boom)
        saveAnotherHost()
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
    fun `removing a host that was saved without an id deletes it and gives its reference back`() {
        rig.seedRow(null, "NO-ID-PC", "{}")
        rig.start()

        val failure = runCatching { rig.binder.removeComputer(ComputerDetails()) }.exceptionOrNull()

        assertThat(failure).isNull()
        assertThat(rig.savedRows()).isEmpty()
        destroyAndProbe()
    }

    @Test
    fun `removing a host whose row cannot be deleted gives its reference back`() {
        rig.seed(saved(UUID_1, "ONE-PC"))
        rig.refuseDeletes()
        rig.start()

        val failure = runCatching { rig.binder.removeComputer(saved(UUID_1)) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteException::class.java)
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
        const val UUID_2 = "00000000-0000-0000-0000-0000000000e2"
        const val BAD_UUID = "00000000-0000-0000-0000-0000000000e3"
        const val PROBE_UUID = "00000000-0000-0000-0000-0000000000e9"
    }
}
