package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import java.lang.management.ManagementFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interrupt flag of the native library is process-global: `LiStartConnection` clears it when a
 * start begins and `LiInterruptConnection` sets it (`Connection.c`). An interrupt that a stop
 * decided for its own connection must therefore be made before the next connection can begin its
 * native start, or it aborts that start.
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
class StaleInterruptTest {
    @get:Rule val rig = ConnectionRig()

    /** The interrupt flag of the native library. */
    private val nativeFlag = AtomicBoolean(false)

    private val firstInterrupt = AtomicBoolean(true)
    private val firstStartBegan = CountDownLatch(1)
    private val interruptEntered = CountDownLatch(1)
    private val interruptGate = CountDownLatch(1)
    private val firstStartGate = CountDownLatch(1)
    private val secondStartBegan = CountDownLatch(1)
    private val secondStartGate = CountDownLatch(1)

    /** What each native start answered, in order. */
    private val nativeResults = CopyOnWriteArrayList<Int>()

    /**
     * The native start of the first connection waits at [firstStartGate] and then fails by itself,
     * as an RTSP handshake that the host refuses does. The one of the second waits at
     * [secondStartGate] and is aborted when the flag was set meanwhile, as `LiStartConnection` ends
     * at its next check. The first interrupt is slow: its thread waits at [interruptGate] after it
     * decided to interrupt, and sets the flag when it is let go.
     */
    private fun fakeNative() {
        ShadowRecordingBridge.onStartConnection = {
            nativeFlag.set(false)
            val first = ShadowRecordingBridge.named("startConnection").size == 1
            val gate = if (first) firstStartGate else secondStartGate
            (if (first) firstStartBegan else secondStartBegan).countDown()
            check(gate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "a native start was not released" }
            val result = if (first || nativeFlag.get()) -1 else 0
            nativeResults += result
            result
        }
        ShadowRecordingBridge.onInterrupt = {
            if (firstInterrupt.getAndSet(false)) {
                interruptEntered.countDown()
                check(interruptGate.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "never let go" }
            }
            nativeFlag.set(true)
        }
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < limit) {
                "Timed out waiting for $what; calls: ${ShadowRecordingBridge.names}"
            }
            Thread.sleep(POLL_MS)
        }
    }

    /** Whether [waiting] waits for a monitor that [owner] holds. */
    private fun waitsFor(waiting: Thread, owner: Thread): Boolean =
        ManagementFactory.getThreadMXBean().getThreadInfo(waiting.id)?.lockOwnerId == owner.id

    /**
     * A stop has read that the bridge is set up and is about to interrupt. Meanwhile the native
     * start of its connection fails by itself, which gives the bridge and the place back, and the
     * next connection begins its native start. The interrupt must not reach that start: either it
     * is made before the place goes back, or the stop does not interrupt at all.
     */
    @Test
    fun `an interrupt that a stop decided for a start that then failed does not abort the next start`() {
        fakeNative()
        val first = rig.connection()
        val second = rig.connection()
        val secondListener = rig.newListener()
        rig.start(first)
        check(firstStartBegan.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "no native start began" }
        val stop = Thread { first.stop() }
        stop.start()
        check(interruptEntered.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "no interrupt was made" }

        firstStartGate.countDown()
        rig.start(second, listener = secondListener)
        // Either the second connection reached its native start, or the first one waits for the
        // stop, which holds what orders the interrupt and the end of the first start.
        awaitUntil("the second start to begin or the first one to wait for the stop") {
            secondStartBegan.count == 0L || waitsFor(rig.listener.threads.single(), stop)
        }
        interruptGate.countDown()
        awaitUntil("the interrupt to be made") { nativeFlag.get() }
        secondStartGate.countDown()
        stop.join(WAIT_MS)
        secondListener.awaitEnded()
        rig.listener.awaitEnded()

        assertThat(nativeResults).containsExactly(-1, 0).inOrder()
        second.stop()
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 10L
    }
}
