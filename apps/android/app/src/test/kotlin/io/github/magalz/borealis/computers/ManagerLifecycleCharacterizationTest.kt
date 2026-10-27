package io.github.magalz.borealis.computers

import android.content.Intent
import android.net.ConnectivityManager
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerManagerService.ComputerManagerBinder
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.computerstore.DATABASE_NAME
import io.github.magalz.borealis.computerstore.FIXTURE_HOSTS
import io.github.magalz.borealis.computerstore.FIXTURE_UNIQUE_ID
import io.github.magalz.borealis.computerstore.UNIQUE_ID_FILE
import io.github.magalz.borealis.computerstore.computerDatabase
import io.github.magalz.borealis.computerstore.installFixture
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * The inherited `ComputerManagerService` as Android runs it: what it does when it is created,
 * bound, unbound and destroyed, and what its network callback does. The platform is Robolectric; no
 * test starts a request to a host that is not a fake on the loopback interface.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerLifecycleCharacterizationTest {
    @get:Rule(order = 0) val log = LimeLogCapture()
    @get:Rule(order = 1) val rig = ManagerRule()

    private val connectivity = shadowOf(rig.app.getSystemService(ConnectivityManager::class.java))

    @Test
    fun `every bind gets the one binder of the service, and an unbind does not ask to be rebound`() {
        rig.start()

        val first = rig.service.onBind(Intent())
        val second = rig.service.onBind(Intent())

        assertThat(first).isSameInstanceAs(second)
        assertThat(first).isInstanceOf(ComputerManagerBinder::class.java)
        assertThat(rig.service.onUnbind(Intent())).isFalse()
    }

    @Test
    fun `the client id is the one in the file that is there`() {
        val idFile = File(rig.app.filesDir, UNIQUE_ID_FILE)
        installFixture(UNIQUE_ID_FILE, idFile)
        rig.start()

        assertThat(rig.binder.uniqueId).isEqualTo(FIXTURE_UNIQUE_ID)
    }

    @Test
    fun `a service without a client id file generates and keeps one`() {
        rig.start()

        val id = rig.binder.uniqueId

        assertThat(id).matches("[0-9a-f]{16}")
        assertThat(File(rig.app.filesDir, UNIQUE_ID_FILE).readText()).isEqualTo(id)
        assertThat(log.lines()).contains("INFO: UID is now: $id")
    }

    @Test
    fun `creating the service loads the saved hosts into the list as unknown, and polls none`() {
        installFixture(DATABASE_NAME, rig.app.computerDatabase())
        rig.start()

        for (fixture in FIXTURE_HOSTS) {
            val computer = checkNotNull(rig.binder.getComputer(fixture.uuid))
            assertThat(computer.name).isEqualTo(fixture.name)
            assertThat(computer.manualAddress).isEqualTo(fixture.manual)
            assertThat(computer.macAddress).isEqualTo(fixture.mac)
            assertThat(computer.state).isEqualTo(ComputerDetails.State.UNKNOWN)
            assertThat(rig.binder.getComputer(fixture.uuid)).isSameInstanceAs(computer)
        }
        assertThat(rig.binder.getComputer("not-a-saved-host")).isNull()
        assertThat(liveThreads(POLLING_THREAD)).isEmpty()
    }

    @Test
    fun `creating the service registers one network callback and destroying it removes it`() {
        rig.start()

        assertThat(connectivity.networkCallbacks).hasSize(1)

        rig.destroy()

        assertThat(connectivity.networkCallbacks).isEmpty()
    }

    @Test
    fun `waiting for the service blocks while the discovery service is disconnected`() {
        rig.start()
        rig.binder.waitForReady()
        rig.disconnectDiscovery()
        val ready = AtomicBoolean(false)
        val waiter =
            thread(isDaemon = true) {
                rig.binder.waitForReady()
                ready.set(true)
            }

        awaitUntil("the wait to start") { waiter.state == Thread.State.TIMED_WAITING }
        Thread.sleep(SETTLE_MS)
        assertThat(ready.get()).isFalse()

        // The connection wakes the waiter; its own check comes only once a second.
        rig.connectDiscovery()
        waiter.join(PROMPT_MS)

        assertThat(ready.get()).isTrue()
    }

    @Test
    fun `destroying the service unbinds the discovery service when it was connected`() {
        rig.start()

        rig.destroy()

        assertThat(shadowOf(rig.app).unboundServiceConnections).hasSize(1)
    }

    @Test
    fun `destroying the service leaves a discovery service that is disconnected alone`() {
        rig.start()
        rig.disconnectDiscovery()

        rig.destroy()

        assertThat(shadowOf(rig.app).unboundServiceConnections).isEmpty()
    }

    @Test
    fun `after the service is destroyed the list stays and a host can no longer be removed`() {
        rig.seed(saved("host-1"))
        rig.start()

        rig.destroy()
        rig.binder.removeComputer(saved("host-1"))

        assertThat(rig.binder.getComputer("host-1")).isNotNull()
        assertThat(rig.savedRows().map { it.uuid }).containsExactly("host-1")
    }

    @Test
    fun `after the service is destroyed a host that answers is listed but not saved`() {
        val host = rig.reachable("host-9")
        rig.start()
        rig.destroy()
        val details = ComputerDetails().also { it.manualAddress = host.address }

        val added = rig.binder.addComputerBlocking(details)

        assertThat(added).isTrue()
        assertThat(rig.binder.getComputer("host-9")).isSameInstanceAs(details)
        assertThat(host.serverInfoRequests()).isEqualTo(1)
        assertThat(rig.savedRows()).isEmpty()
    }

    @Test
    fun `a wait for the discovery service that is interrupted returns and keeps the interrupt`() {
        rig.start()
        rig.disconnectDiscovery()
        val interrupted = AtomicBoolean(false)
        val waiter =
            thread(isDaemon = true) {
                rig.binder.waitForReady()
                interrupted.set(Thread.currentThread().isInterrupted)
            }
        awaitUntil("the wait to start") { waiter.state == Thread.State.TIMED_WAITING }

        waiter.interrupt()
        waiter.join(WAIT_MS)

        assertThat(waiter.isAlive).isFalse()
        assertThat(interrupted.get()).isTrue()
    }

    @Test
    fun `a new network makes every host unknown again and the listener hears each one`() {
        rig.seed(saved("host-1", "ONE"), saved("host-2", "TWO"))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()
        for (uuid in listOf("host-1", "host-2")) {
            checkNotNull(rig.binder.getComputer(uuid)).state = ComputerDetails.State.ONLINE
        }
        val before = updates.on(caller).size

        connectivity.networkCallbacks.single().onAvailable(ShadowNetwork.newInstance(1))

        val heard = updates.on(caller).drop(before)
        assertThat(heard.map { it.name }).containsExactly("ONE", "TWO").inOrder()
        assertThat(heard.map { it.state }).containsExactly(UNKNOWN, UNKNOWN)
        assertThat(log.lines()).contains("INFO: Resetting PC state for new available network")
    }

    @Test
    fun `losing the network makes every host offline and the listener hears each one`() {
        rig.seed(saved("host-1", "ONE"), saved("host-2", "TWO"))
        rig.start()
        val updates = Updates()
        rig.binder.startPolling(updates)
        val caller = Thread.currentThread()
        val before = updates.on(caller).size

        connectivity.networkCallbacks.single().onLost(ShadowNetwork.newInstance(1))

        val heard = updates.on(caller).drop(before)
        assertThat(heard.map { it.name }).containsExactly("ONE", "TWO").inOrder()
        assertThat(heard.map { it.state }).containsExactly(OFFLINE, OFFLINE)
        assertThat(log.lines()).contains("INFO: Offlining PCs due to network loss")
    }

    @Test
    fun `a network change with nobody listening still changes the state of the hosts`() {
        rig.seed(saved("host-1", "ONE"))
        rig.start()
        val computer = checkNotNull(rig.binder.getComputer("host-1"))

        connectivity.networkCallbacks.single().onLost(ShadowNetwork.newInstance(1))
        assertThat(computer.state).isEqualTo(OFFLINE)

        computer.state = ComputerDetails.State.ONLINE
        connectivity.networkCallbacks.single().onAvailable(ShadowNetwork.newInstance(1))
        assertThat(computer.state).isEqualTo(UNKNOWN)
    }

    private companion object {
        val UNKNOWN = ComputerDetails.State.UNKNOWN
        val OFFLINE = ComputerDetails.State.OFFLINE
        const val SETTLE_MS = 100L
        const val PROMPT_MS = 300L
    }
}
