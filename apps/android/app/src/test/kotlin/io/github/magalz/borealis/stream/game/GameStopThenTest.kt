package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamControls
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * What a Disconnect waits for: `stopConnectionThen` stops the connection, which talks to the host
 * on a thread of its own, and runs its callback from that thread once the stop is done.
 */
internal class GameStopThenTest : GameTestBase() {
    @Test
    fun `the callback runs after the connection stopped, on the thread that stopped it`() {
        rig.streaming()
        val seen = CopyOnWriteArrayList<List<String>>()
        val threads = CopyOnWriteArrayList<Thread>()

        rig.game.stopConnectionThen {
            threads += Thread.currentThread()
            seen += ShadowRecordingBridge.names
        }
        rig.awaitStop()

        assertThat(threads.single()).isNotSameInstanceAs(Thread.currentThread())
        assertThat(seen.single())
            .containsAtLeast("interruptConnection", "stopConnection", "cleanupBridge")
            .inOrder()
    }

    @Test
    fun `the actions of the activity stop the connection through the activity`() {
        rig.streaming()
        val controls =
            ReflectionHelpers.getField<StreamControls>(rig.field<Any>("streamActions"), "controls")
        val done = CountDownLatch(1)

        controls.stopConnection(Runnable { done.countDown() })

        assertThat(done.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
    }

    @Test
    fun `the session learns that the connection stopped, on the main thread, when the stop ends`() {
        rig.streaming()
        assertThat(rig.session.state.value.decoderHeld).isTrue()

        rig.game.stopConnectionThen {}
        rig.awaitStop()

        // The thread of the stop only posts it.
        assertThat(rig.session.state.value.decoderHeld).isTrue()
        ShadowLooper.idleMainLooper()
        assertThat(rig.session.state.value.decoderHeld).isFalse()
    }

    @Test
    fun `the session learns it too when the activity stops the connection`() {
        rig.streaming()

        rig.stop()
        ShadowLooper.idleMainLooper()

        assertThat(rig.session.state.value.decoderHeld).isFalse()
    }

    @Test
    fun `a start that failed after the decoder stage lets go of the decoder when it is stopped`() {
        rig.create()
        // The decoder is held from this stage on, and the connection never started.
        rig.session.onStage(StartStage.PREPARING_DISPLAY)
        assertThat(rig.session.state.value.decoderHeld).isTrue()

        rig.game.stopConnectionThen {}
        rig.awaitStop()
        ShadowLooper.idleMainLooper()

        assertThat(rig.session.state.value.decoderHeld).isFalse()
    }

    @Test
    fun `the stop of an activity whose start failed lets go of the decoder too`() {
        rig.create()
        rig.session.onStage(StartStage.PREPARING_DISPLAY)

        rig.stop()
        ShadowLooper.idleMainLooper()

        assertThat(rig.session.state.value.decoderHeld).isFalse()
    }

    @Test
    fun `the callback runs after the stop of a connection that never connected, on its thread`() {
        rig.create()
        val seen = CopyOnWriteArrayList<List<String>>()
        val cancelled = CopyOnWriteArrayList<Boolean>()
        val threads = CopyOnWriteArrayList<Thread>()

        rig.game.stopConnectionThen {
            threads += Thread.currentThread()
            seen += ShadowRecordingBridge.names
            // The stop of the connection cancels its start, which nothing else did on this path.
            cancelled += ReflectionHelpers.getField<Boolean>(rig.connection, "launchCancelled")
        }
        rig.awaitStop()

        assertThat(threads.single()).isNotSameInstanceAs(Thread.currentThread())
        assertThat(cancelled.single()).isTrue()
        // The connection never set its bridge up, so the stop makes no native call.
        assertThat(seen.single()).isEmpty()
    }

    @Test
    fun `the callback of a connection with its bridge set up waits for the stop`() {
        rig.create()
        ReflectionHelpers.setField(rig.connection, "bridgeSetUp", true)
        val seen = CopyOnWriteArrayList<List<String>>()

        rig.game.stopConnectionThen { seen += ShadowRecordingBridge.names }
        rig.awaitStop()

        assertThat(seen.single())
            .containsAtLeast("interruptConnection", "stopConnection", "cleanupBridge")
            .inOrder()
    }

    @Test
    fun `the callback of a stop that another stop preceded runs all the same`() {
        rig.streaming()
        // The stop of onStop, as the one of a surface that went away or of an end of the stream.
        rig.stop()
        val done = CountDownLatch(1)

        rig.game.stopConnectionThen { done.countDown() }

        assertThat(done.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    fun `the callbacks of two stops that overlap run when the stop under way ends`() {
        rig.streaming()
        val order = CopyOnWriteArrayList<String>()
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        // The stop waits for the native library, which this thread holds.
        val holder = thread {
            synchronized(MoonBridge::class.java) {
                held.countDown()
                release.await(WAIT_MS, TimeUnit.MILLISECONDS)
            }
        }
        assertThat(held.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()

        rig.game.stopConnectionThen { order += "first" }
        rig.game.stopConnectionThen { order += "second" }
        assertThat(order).isEmpty()
        release.countDown()
        holder.join(WAIT_MS)
        rig.awaitStop()

        assertThat(order).containsExactly("first", "second")
    }

    private companion object {
        const val WAIT_MS = 30_000L
    }
}
