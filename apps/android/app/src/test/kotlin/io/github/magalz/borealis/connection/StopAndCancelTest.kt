package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.nvhttp.HostReply
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `stop` and `cancelLaunch` while a start goes on, and the one place that lets a single connection
 * be started or stopped at a time. Each test makes the order deterministic with the threads that it
 * waits for, never with a pause that has to be long enough.
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
class StopAndCancelTest {
    @get:Rule val rig = ConnectionRig()
    @get:Rule val log = RecordingLog()

    private val listener
        get() = rig.listener

    private fun startAndConnect(connection: NvConnection, count: Int = 1) {
        rig.start(connection)
        ShadowRecordingBridge.await("startConnection", count)
    }

    /** Waits until [thread] waits for something: here, for the place of the other connection. */
    private fun awaitParked(thread: Thread) {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (thread.state != Thread.State.WAITING) {
            check(System.currentTimeMillis() < limit) {
                "The thread did not wait; it is ${thread.state}; calls: ${ShadowRecordingBridge.names}"
            }
            Thread.sleep(POLL_MS)
        }
    }

    /** Waits until [thread] waits to enter the monitor of the bridge, which the test holds. */
    private fun awaitBlockedOnBridge(thread: Thread) {
        val bridge = System.identityHashCode(MoonBridge::class.java)
        val threads = ManagementFactory.getThreadMXBean()
        val limit = System.currentTimeMillis() + WAIT_MS
        while (threads.getThreadInfo(thread.id)?.lockInfo?.identityHashCode != bridge) {
            check(System.currentTimeMillis() < limit) {
                "The thread did not wait for the bridge; it is ${thread.state}; " +
                    "calls: ${ShadowRecordingBridge.names}"
            }
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * The native calls are process-global: a connection that never set the bridge up must not
     * interrupt, stop or clean up the stream of another one.
     */
    @Test
    fun `the stop of a connection that never set the bridge up makes no native call`() {
        val connection = rig.connection(config = null)

        connection.stop()

        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `the stop of a connection that never started leaves the stream that runs alone`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val before = ShadowRecordingBridge.calls.size

        rig.connection(config = null).stop()

        assertThat(ShadowRecordingBridge.names.drop(before)).isEmpty()
        assertThat(ShadowRecordingBridge.listener).isNotNull()
        assertThat(rig.permits()).isEqualTo(0)
    }

    @Test
    fun `the stop of a stream that runs interrupts it first, then stops and cleans up holding the monitor`() {
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        val before = ShadowRecordingBridge.calls.size

        connection.stop()

        assertThat(ShadowRecordingBridge.calls.drop(before).map { it.name to it.locked })
            .containsExactly(
                "interruptConnection" to false,
                "stopConnection" to true,
                "cleanupBridge" to true,
            )
            .inOrder()
    }

    /** By the second stop another connection may own the bridge: the first one made its calls. */
    @Test
    fun `a second stop of a stream that ran makes no native call`() {
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        connection.stop()
        val before = ShadowRecordingBridge.calls.size

        connection.stop()

        assertThat(ShadowRecordingBridge.names.drop(before)).isEmpty()
    }

    @Test
    fun `a late stop of a stopped connection leaves the stream of the next one alone`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        first.stop()
        val secondListener = rig.newListener()
        rig.start(rig.connection(), listener = secondListener)
        ShadowRecordingBridge.await("startConnection", count = 2)
        secondListener.awaitEnded()
        val before = ShadowRecordingBridge.calls.size

        first.stop()

        assertThat(ShadowRecordingBridge.names.drop(before)).isEmpty()
        assertThat(ShadowRecordingBridge.listener).isNotNull()
    }

    /**
     * `LiStartConnection` undoes what it did when it fails (`Connection.c`, `Cleanup:`), so the
     * start gives the bridge back itself and nobody has to stop it.
     */
    @Test
    fun `a native connection that failed to start cleans the bridge up itself, holding the monitor`() {
        ShadowRecordingBridge.onStartConnection = { -1 }
        val connection = rig.connection()

        startAndConnect(connection)
        listener.awaitEnded()

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.locked })
            .containsExactly(
                "setupBridge" to true,
                "startConnection" to true,
                "cleanupBridge" to true,
            )
            .inOrder()
        assertThat(ShadowRecordingBridge.listener).isNull()
    }

    @Test
    fun `the stop of a start whose native connection failed makes no native call`() {
        ShadowRecordingBridge.onStartConnection = { -1 }
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        val before = ShadowRecordingBridge.calls.size

        connection.stop()

        assertThat(ShadowRecordingBridge.names.drop(before)).isEmpty()
    }

    /** A connection that never took the place gives none back: the places must not pile up. */
    @Test
    fun `stop gives no place back to a connection that never took one`() {
        rig.connection(config = null).stop()

        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `the stop of a stream that runs gives its place back`() {
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        assertThat(rig.permits()).isEqualTo(0)

        connection.stop()

        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `a second stop of a stream that runs gives no second place back`() {
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()

        connection.stop()
        connection.stop()

        assertThat(rig.permits()).isEqualTo(1)
    }

    /**
     * The start gave its place back when the native connection failed: the stop has none to give.
     */
    @Test
    fun `the stop of a start whose native connection failed gives no place back`() {
        ShadowRecordingBridge.onStartConnection = { -1 }
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        assertThat(rig.permits()).isEqualTo(1)

        connection.stop()

        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `a stop that comes while the native connection fails to start leaves one place`() {
        val gate = CountDownLatch(1)
        ShadowRecordingBridge.onStartConnection = {
            check(gate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "never interrupted" }
            -1
        }
        ShadowRecordingBridge.onInterrupt = { gate.countDown() }
        val connection = rig.connection()
        startAndConnect(connection)

        connection.stop()
        listener.awaitEnded()

        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `a stream that was stopped asks for no more keyframes`() {
        val connection = rig.connection()
        startAndConnect(connection)
        listener.awaitEnded()
        connection.requestKeyframe()
        assertThat(ShadowRecordingBridge.named("requestIdrFrame")).hasSize(1)

        connection.stop()
        connection.requestKeyframe()

        assertThat(ShadowRecordingBridge.named("requestIdrFrame")).hasSize(1)
    }

    @Test
    fun `a connection that was not started asks for no keyframes`() {
        rig.connection(config = null).requestKeyframe()

        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a stop while the native connection starts interrupts it first, then waits for the monitor`() {
        val gate = CountDownLatch(1)
        ShadowRecordingBridge.onStartConnection = {
            check(gate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "never interrupted" }
            -1
        }
        ShadowRecordingBridge.onInterrupt = { gate.countDown() }
        val connection = rig.connection()
        startAndConnect(connection)

        connection.stop()
        listener.awaitEnded()

        // The start failed, so it cleaned the bridge up itself: the stop has nothing left to stop.
        assertThat(ShadowRecordingBridge.calls.map { it.name to it.locked })
            .containsExactly(
                "setupBridge" to true,
                "startConnection" to true,
                "interruptConnection" to false,
                "cleanupBridge" to true,
            )
            .inOrder()
    }

    @Test
    fun `a native connection that started in spite of the interrupt is not left open by the stop`() {
        val gate = CountDownLatch(1)
        ShadowRecordingBridge.onStartConnection = {
            check(gate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "never interrupted" }
            0
        }
        ShadowRecordingBridge.onInterrupt = { gate.countDown() }
        val connection = rig.connection()
        startAndConnect(connection)

        connection.stop()
        listener.awaitEnded()
        connection.requestKeyframe()

        assertThat(ShadowRecordingBridge.named("requestIdrFrame")).isEmpty()
    }

    @Test
    fun `a second start waits for the place of the stream that runs until that stream is stopped`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val secondListener = rig.newListener()
        val second = rig.connection()

        rig.start(second, listener = secondListener)
        secondListener.await("stageComplete")
        awaitParked(secondListener.threads.single())
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
        first.stop()
        ShadowRecordingBridge.await("startConnection", count = 2)
        secondListener.awaitEnded()

        assertThat(ShadowRecordingBridge.names)
            .containsExactly(
                "setupBridge",
                "startConnection",
                "interruptConnection",
                "stopConnection",
                "cleanupBridge",
                "setupBridge",
                "startConnection",
            )
            .inOrder()
    }

    @Test
    fun `a start that is interrupted while it waits for the place fails at its step`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val secondListener = rig.newListener()

        rig.start(rig.connection(), listener = secondListener)
        secondListener.await("stageComplete")
        val thread = secondListener.threads.single()
        awaitParked(thread)
        thread.interrupt()
        secondListener.await("stageFailed")
        secondListener.awaitEnded()

        assertThat(secondListener.events.last()).isEqualTo("stageFailed launch ports=0 error=0")
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
        assertThat(
                log.records.filter { it.message.startsWith("start failed: InterruptedException") }
            )
            .hasSize(1)
    }

    @Test
    fun `a start that is cancelled while it waits for the place gives the place back and tells nothing`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val secondListener = rig.newListener()
        val second = rig.connection()

        rig.start(second, listener = secondListener)
        secondListener.await("stageComplete")
        awaitParked(secondListener.threads.single())
        second.cancelLaunch()
        first.stop()
        secondListener.awaitEnded()

        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
        assertThat(secondListener.events("stageFailed")).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    /**
     * The place that lets it through is the one of the stream that runs, not one that a stop adds:
     * the stop does not free the waiting thread, and the start that gets the place later ends
     * without a stream and without a failure, with no `cancelLaunch` to tell it. The stop comes
     * before the thread has the place, as a stop between its `acquire` and its cancel check does.
     */
    @Test
    fun `the stop of a start that waits for the place ends it when it gets the place, without a stream`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val secondListener = rig.newListener()
        val second = rig.connection()
        rig.start(second, listener = secondListener)
        secondListener.await("stageComplete")
        awaitParked(secondListener.threads.single())

        second.stop()
        first.stop()
        secondListener.awaitEnded()

        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
        assertThat(secondListener.events("stageFailed")).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    /**
     * The test holds the monitor of the bridge, so the start thread passes its last cancel check
     * and waits to enter it, and a whole stop, which runs under the same monitor by the same
     * thread, comes first. The stop alone, with no `cancelLaunch`, must keep that start from
     * beginning a stream whose place the stop already gave back.
     */
    @Test
    fun `a stop that completes before the start enters the native connection leaves no stream`() {
        val connection = rig.connection()

        synchronized(MoonBridge::class.java) {
            rig.start(connection)
            listener.await("stageComplete")
            awaitBlockedOnBridge(listener.threads.single())
            connection.stop()
        }
        listener.awaitEnded()

        // The stop came before the start set the bridge up: it made no native call, and the start
        // saw the cancel and never began.
        assertThat(ShadowRecordingBridge.names).isEmpty()
        assertThat(listener.events("stageFailed")).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    /**
     * The user left while the host answered `/serverinfo` with an error: nobody is left to tell.
     */
    @Test
    fun `a start that is cancelled while the host answers with an error tells nothing`() {
        val connection = rig.connection()
        rig.script.serverInfoReplies += HostReply.failure("500", "boom")
        rig.script.onServerInfo = { connection.cancelLaunch() }

        rig.start(connection)
        listener.await("stageStarting")
        listener.awaitEnded()

        assertThat(listener.events).containsExactly("stageStarting $DESKTOP_NAME")
    }

    @Test
    fun `a start that is cancelled when it is complete ends at once, without waiting for the stream that runs`() {
        val first = rig.connection()
        startAndConnect(first)
        listener.awaitEnded()
        val secondListener = rig.newListener()
        val second = rig.connection()
        secondListener.onEvent = { if (it.startsWith("stageComplete")) second.cancelLaunch() }

        try {
            rig.start(second, listener = secondListener)
            secondListener.await("stageComplete")
            val thread = secondListener.threads.single()
            thread.join(END_MS)

            assertThat(thread.isAlive).isFalse()
            assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(1)
            assertThat(rig.permits()).isEqualTo(0)
        } finally {
            // Frees the place for a thread that did not end, so that it cannot hold a later test.
            first.stop()
        }
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val END_MS = 10_000L
        const val POLL_MS = 10L
    }
}
