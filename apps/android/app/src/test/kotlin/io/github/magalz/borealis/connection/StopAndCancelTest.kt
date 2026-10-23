package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import io.github.magalz.borealis.library.RecordingLog
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

    @Test
    fun `stop interrupts the connection first, then stops and cleans up holding the monitor`() {
        val connection = rig.connection(config = null)

        connection.stop()

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.locked })
            .containsExactly(
                "interruptConnection" to false,
                "stopConnection" to true,
                "cleanupBridge" to true,
            )
            .inOrder()
    }

    /** A connection that never took the place still gives one back: the places can pile up. */
    @Test
    fun `stop gives a place back even to a connection that never took one`() {
        rig.connection(config = null).stop()

        assertThat(rig.permits()).isEqualTo(2)
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

        assertThat(ShadowRecordingBridge.calls.map { it.name to it.locked })
            .containsExactly(
                "setupBridge" to true,
                "startConnection" to true,
                "interruptConnection" to false,
                "stopConnection" to true,
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

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 10L
    }
}
