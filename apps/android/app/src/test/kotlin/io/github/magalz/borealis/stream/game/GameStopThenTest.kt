package io.github.magalz.borealis.stream.game

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamControls
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun `the callback runs at once when no connection runs`() {
        rig.create()
        var ran = false

        rig.game.stopConnectionThen { ran = true }

        assertThat(ran).isTrue()
        assertThat(ShadowRecordingBridge.named("stopConnection")).isEmpty()
    }

    private companion object {
        const val WAIT_MS = 30_000L
    }
}
