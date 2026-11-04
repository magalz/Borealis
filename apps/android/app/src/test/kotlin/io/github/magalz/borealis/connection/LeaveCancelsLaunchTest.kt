package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.nvhttp.HostReply
import io.github.magalz.borealis.nvhttp.SeenRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user leaves while a start is under way. A `/launch` that the host accepted has started an app
 * that nobody streams, so the connection asks the host to `/cancel` it, once, when the start ends
 * without a stream. It never does after a `/resume` (the app may belong to another device), after a
 * launch that the host refused (an app runs, or the launch of another client is pending), on a GFE
 * host, or once another connection of the process began its own `/launch` or `/resume`: every
 * connection sends the same unique id, and the host ends the session of whoever asks. Each test
 * holds a request with a latch, so the order never depends on a pause.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class LeaveCancelsLaunchTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    private val held = CountDownLatch(1)
    private val release = CountDownLatch(1)

    /** The requests that are no `/serverinfo`, which the connection reads again and again. */
    private fun steps(): List<String> = rig.paths().filter { it != "/serverinfo" }

    private fun hold() {
        held.countDown()
        check(release.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "the request was never released" }
    }

    /** The host keeps the `/launch` until the test releases it. */
    private fun holdLaunch() {
        rig.script.onLaunch = ::hold
    }

    /** The host keeps the first `/launch` until the test releases it; the later ones go through. */
    private fun holdFirstLaunch() {
        val launches = AtomicInteger()
        rig.script.onLaunch = { if (launches.incrementAndGet() == 1) hold() }
    }

    /** The host keeps the `/resume` until the test releases it. */
    private fun holdResume() {
        val answer = rig.host.handler
        rig.host.handler = { request: SeenRequest ->
            if (request.path == "/resume") hold()
            answer(request)
        }
    }

    /** The host refuses the first `/launch` because another app runs. */
    private fun refuseLaunchBecauseAnAppRuns() {
        rig.script.launchReplies += REFUSED
        rig.script.onLaunch = {
            if (rig.script.launchReplies.isNotEmpty()) rig.script.running(OTHER_ID)
        }
    }

    private fun awaitHeld() {
        check(held.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "the host never saw the request" }
    }

    /** Waits until the host has seen [count] requests to [path]. */
    private fun awaitRequests(path: String, count: Int) {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (rig.paths().count { it == path } < count) {
            check(System.currentTimeMillis() < limit) { "the host never saw $count of $path" }
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * The user leaves while the host holds the `/launch` of [first], and starts [second] at once.
     * The host answers the held launch only after it has seen the [secondPath] request of [second],
     * so the start of [first] ends with the next stream already on its way. [hostChanges] runs
     * between the leave and the second start.
     */
    private fun leaveAndStartAgain(
        first: NvConnection,
        second: NvConnection,
        secondPath: String,
        hostChanges: () -> Unit = {},
    ) {
        rig.start(first)
        awaitHeld()
        first.cancelLaunch()
        hostChanges()
        val secondListener = rig.newListener()
        rig.start(second, listener = secondListener)
        awaitRequests(secondPath, if (secondPath == "/launch") 2 else 1)
        release.countDown()
        listener.awaitEnded()
        secondListener.awaitEnded()
        second.stop()
    }

    /** Leaves while the host holds a request, then lets the host answer it. */
    private fun startAndLeave(connection: NvConnection, leave: () -> Unit) {
        rig.start(connection)
        awaitHeld()
        leave()
        release.countDown()
        listener.awaitEnded()
    }

    @Test
    fun `a leave through cancelLaunch while the launch is held sends one cancel after the answer`() {
        val connection = rig.connection()
        holdLaunch()

        startAndLeave(connection) { connection.cancelLaunch() }

        assertThat(steps()).containsExactly("/launch", "/cancel").inOrder()
        // The leave asks for the cancel and checks nothing else, unlike a quit.
        assertThat(rig.paths().last()).isEqualTo("/cancel")
        assertThat(listener.events("stageFailed")).isEmpty()
        assertThat(ShadowRecordingBridge.calls).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    /**
     * The host still works on the launch when the stop comes: a cancel now would find nothing to
     * cancel, and the host would start the app and roll it back later.
     */
    @Test
    fun `a leave through stop while the launch is held sends the cancel only after the answer`() {
        val connection = rig.connection()
        holdLaunch()
        rig.start(connection)
        awaitHeld()

        connection.stop()

        assertThat(steps()).containsExactly("/launch")
        release.countDown()
        listener.awaitEnded()
        assertThat(steps()).containsExactly("/launch", "/cancel").inOrder()
        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `a leave while the native connection starts sends one cancel when that start ends`() {
        val gate = CountDownLatch(1)
        ShadowRecordingBridge.onStartConnection = {
            check(gate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "never interrupted" }
            -1
        }
        ShadowRecordingBridge.onInterrupt = { gate.countDown() }
        val connection = rig.connection()
        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")

        connection.stop()
        listener.awaitEnded()

        assertThat(steps()).containsExactly("/launch", "/cancel").inOrder()
    }

    @Test
    fun `a cancel that the host refuses does not fail the leave`() {
        rig.script.cancelReplies += HostReply.failure("500", "boom")
        val connection = rig.connection()
        holdLaunch()

        startAndLeave(connection) { connection.cancelLaunch() }

        assertThat(steps()).containsExactly("/launch", "/cancel").inOrder()
        assertThat(listener.events("stageFailed")).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    /** The stream is the session: stopping it later leaves the app to the user. */
    @Test
    fun `a stream that connected and is then stopped sends no cancel`() {
        val connection = rig.connection()
        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        connection.stop()

        assertThat(steps()).containsExactly("/launch")
    }

    @Test
    fun `a leave while the resume is held sends no cancel, the app may belong to another device`() {
        rig.script.running(DESKTOP_ID)
        val connection = rig.resumingConnection()
        holdResume()

        startAndLeave(connection) { connection.cancelLaunch() }

        assertThat(steps()).containsExactly("/resume")
    }

    @Test
    fun `a launch that the host refused and a user who cancels the question send no cancel`() {
        refuseLaunchBecauseAnAppRuns()
        listener.conflictChoice = ConflictChoice.CANCEL

        rig.start(rig.connection())
        listener.await("launchConflict")
        listener.awaitEnded()

        assertThat(steps()).containsExactly("/launch", "/applist").inOrder()
    }

    /**
     * The launch that the host refused started nothing, and the app that runs is the one of another
     * device: leaving while the join of it is held must not end it.
     */
    @Test
    fun `a leave while the join of an app that blocked the launch is held sends no cancel`() {
        refuseLaunchBecauseAnAppRuns()
        listener.conflictChoice = ConflictChoice.JOIN
        val connection = rig.connection()
        holdResume()

        startAndLeave(connection) { connection.cancelLaunch() }

        assertThat(steps()).containsExactly("/launch", "/applist", "/resume").inOrder()
    }

    @Test
    fun `a leave while a GFE host holds the launch sends no cancel`() {
        rig.script.state = HostScript.GFE_FREE
        val connection = rig.connection()
        holdLaunch()

        startAndLeave(connection) { connection.cancelLaunch() }

        assertThat(steps()).containsExactly("/launch")
    }

    /**
     * The late cancel would end the session that the second connection is starting: the host cannot
     * tell the two apart.
     */
    @Test
    fun `a leave sends no cancel once the next connection began its own launch`() {
        val first = rig.connection()
        val second = rig.connection()
        holdFirstLaunch()

        leaveAndStartAgain(first, second, "/launch")

        assertThat(steps()).containsExactly("/launch", "/launch").inOrder()
    }

    @Test
    fun `a leave sends no cancel once the next connection began its own resume`() {
        val first = rig.connection()
        val second = rig.resumingConnection()
        holdFirstLaunch()

        leaveAndStartAgain(first, second, "/resume") { rig.script.running(DESKTOP_ID) }

        assertThat(steps()).containsExactly("/launch", "/resume").inOrder()
    }

    private companion object {
        val REFUSED: HostReply = HostReply.failure("400", "An app is already running on this host")
        const val WAIT_MS = 30_000L
        const val POLL_MS = 10L
    }
}
